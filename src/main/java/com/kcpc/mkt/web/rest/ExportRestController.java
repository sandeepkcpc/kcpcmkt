package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.reporting.dto.ContentPlanExport;
import com.kcpc.mkt.reporting.service.ExportService;
import com.kcpc.mkt.reporting.service.MultiFormatExportService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** SAD-ADR-008: per-Content-Plan JSON export, plus the governed multi-format export (`API-OP-062`). */
@RestController
public class ExportRestController {

    private final ExportService exportService;
    private final MultiFormatExportService multiFormatExportService;

    public ExportRestController(ExportService exportService, MultiFormatExportService multiFormatExportService) {
        this.exportService = exportService;
        this.multiFormatExportService = multiFormatExportService;
    }

    @GetMapping("/api/v1/export/content-plans/{id}")
    public ContentPlanExport export(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal) {
        return exportService.exportContentPlan(principal.user(), id);
    }

    /** API-OP-062: Execute Multi-Format Data Export. */
    @GetMapping("/api/v1/exports")
    public ResponseEntity<byte[]> multiFormatExport(@RequestParam(required = false) String format,
                                                      @RequestParam(required = false) List<String> tables,
                                                      @AuthenticationPrincipal KcpcUserPrincipal principal) {
        var result = multiFormatExportService.export(principal.user(), format, tables);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                .body(result.content());
    }
}
