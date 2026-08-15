package com.kcpc.mkt.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.service.AuthorizationService;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * API-OP-062 / SAD-ADR-008 / RTM-081: synchronous multi-format (JSON/CSV/XLSX) export over the
 * governed physical table union - `ERD-TBL-007`..`039`, `ERD-TBL-041`..`043` (workflow, idea,
 * planning, production, marks, publishing, performance, admin-action history; excludes identity/
 * permission tables `ERD-TBL-001`..`006`/`044` and the retired `ERD-TBL-040`). No persistent
 * export-job table - built and streamed back in the same request (ENG-030: buffered in memory
 * rather than true chunked streaming, an accepted simplification at the &lt;15-user MVP scale
 * `SAD-ADR-009`/`010` already assume).
 */
@Service
public class MultiFormatExportService {

    /** RTM-081 / API-OP-062: the exact governed exportable table union - a fixed allowlist, never
     * derived from user input, since these names are interpolated into SQL. */
    private static final Set<String> GOVERNED_TABLES = Set.of(
            "workflow_instances", "workflow_transition_history", "ideas", "content_plans", "planned_outputs",
            "predefined_role_marks", "shooting_assignments", "editing_assignments", "review_cycles",
            "personal_mark_attributions", "platforms", "company_channels", "publication_targets",
            "planned_output_publication_target_mappings", "actual_publication_events", "publication_target_na_records",
            "performance_obligations", "creative_performance_scorecards", "predefined_mark_corrections",
            "publication_evidence_corrections", "performance_metric_corrections", "reschedule_records",
            "reassignment_records", "reassignment_assignees", "cancellation_records", "reopen_records",
            "planning_preparers", "shooting_execution_participants", "editing_execution_participants",
            "content_plan_talent_entries", "work_hold_records");

    private final AuthorizationService authorizationService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MultiFormatExportService(AuthorizationService authorizationService, DataSource dataSource) {
        this.authorizationService = authorizationService;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public record ExportResult(byte[] content, String contentType, String filename) {
    }

    /** The governed table union, sorted, for the Export screen's scope checklist. */
    public List<String> governedTables() {
        return GOVERNED_TABLES.stream().sorted().toList();
    }

    @Transactional(readOnly = true)
    public ExportResult export(User requester, String format, List<String> tables) {
        // SRS-REQ-081: management-only (CEO/MM native authority; no delegated permission exists
        // for export in the 17-item catalogue).
        authorizationService.requireNativeAuthority(requester, "Data Export");

        Set<String> resolvedTables = resolveTables(tables);
        String fmt = format == null ? "JSON" : format.toUpperCase();
        String stamp = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString().replace("-", "");

        return switch (fmt) {
            case "JSON" -> new ExportResult(buildJson(resolvedTables), "application/json",
                    "KCPC_Export_" + stamp + ".json");
            case "CSV" -> {
                if (resolvedTables.size() != 1) {
                    throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "CSV export requires exactly one table (use XLSX for a multi-table export)");
                }
                String table = resolvedTables.iterator().next();
                yield new ExportResult(buildCsv(table), "text/csv", "KCPC_Export_" + table + "_" + stamp + ".csv");
            }
            case "XLSX" -> new ExportResult(buildXlsx(resolvedTables),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "KCPC_Export_" + stamp + ".xlsx");
            default -> throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "format must be one of JSON, CSV, XLSX");
        };
    }

    private Set<String> resolveTables(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return GOVERNED_TABLES;
        }
        Set<String> resolved = new LinkedHashSet<>();
        for (String t : requested) {
            String normalized = t.trim().toLowerCase();
            if (!GOVERNED_TABLES.contains(normalized)) {
                throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                        "Table not in the governed export union: " + t);
            }
            resolved.add(normalized);
        }
        return resolved;
    }

    private byte[] buildJson(Set<String> tables) {
        try {
            Map<String, Object> root = new java.util.LinkedHashMap<>();
            for (String table : tables) {
                root.put(table, jdbcTemplate.queryForList("select * from " + table));
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] buildCsv(String table) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from " + table);
        StringBuilder sb = new StringBuilder();
        if (!rows.isEmpty()) {
            List<String> columns = List.copyOf(rows.get(0).keySet());
            sb.append(String.join(",", columns)).append('\n');
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(csvEscape(row.get(columns.get(i))));
                }
                sb.append('\n');
            }
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private byte[] buildXlsx(Set<String> tables) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            for (String table : tables) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("select * from " + table);
                SXSSFSheet sheet = workbook.createSheet(table.length() > 31 ? table.substring(0, 31) : table);
                if (rows.isEmpty()) {
                    continue;
                }
                List<String> columns = List.copyOf(rows.get(0).keySet());
                var headerRow = sheet.createRow(0);
                for (int c = 0; c < columns.size(); c++) {
                    headerRow.createCell(c).setCellValue(columns.get(c));
                }
                int r = 1;
                for (Map<String, Object> row : rows) {
                    var xlsxRow = sheet.createRow(r++);
                    for (int c = 0; c < columns.size(); c++) {
                        Object value = row.get(columns.get(c));
                        xlsxRow.createCell(c).setCellValue(value == null ? "" : String.valueOf(value));
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
