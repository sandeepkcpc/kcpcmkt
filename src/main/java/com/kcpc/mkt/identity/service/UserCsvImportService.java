package com.kcpc.mkt.identity.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.util.RandomPasswordGenerator;
import com.kcpc.mkt.common.util.UuidV7;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.BusinessRole;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.dto.UserImportBatchResult;
import com.kcpc.mkt.identity.dto.UserImportOutcome;
import com.kcpc.mkt.identity.dto.UserImportRow;
import com.kcpc.mkt.identity.repository.BusinessRoleRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Administration -&gt; Users -&gt; Import Users: bulk-create real {@link User} rows from a CSV,
 * reusing the exact same {@link UserAdminService#createUser} path (same CEO-only authority check,
 * same audit event, same email-uniqueness rule, same bcrypt hashing) a manual one-at-a-time
 * creation already uses - this service only adds CSV parsing/validation/preview on top, never a
 * second, different way to create a user.
 *
 * <p>Governed CSV columns (the actual {@link User}/{@link BusinessRole} schema - see
 * docs/USER_CSV_IMPORT_GUIDE.md for the full spec):
 * <pre>
 * full_name,email,business_role,account_status
 * </pre>
 * No employee code (the schema has none), no Access Class (always resolved from Business Role,
 * never an independent input), no password (generated - see {@link RandomPasswordGenerator}), and
 * no operational permission of any kind - this import only ever touches {@code users}.
 */
@Service
public class UserCsvImportService {

    public static final List<String> REQUIRED_HEADERS = List.of("full_name", "email", "business_role");
    public static final String OPTIONAL_STATUS_HEADER = "account_status";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final BusinessRoleRepository businessRoleRepository;
    private final UserAdminService userAdminService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public UserCsvImportService(UserRepository userRepository, BusinessRoleRepository businessRoleRepository,
                                 UserAdminService userAdminService, AuthorizationService authorizationService,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.businessRoleRepository = businessRoleRepository;
        this.userAdminService = userAdminService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /**
     * Parses and validates the CSV with no database writes at all - safe to call as many times as
     * needed while the CEO reviews the Preview screen. Row order is preserved so row numbers in
     * error messages match what the CEO sees in their spreadsheet (row 1 = the first data row,
     * i.e. immediately after the header).
     */
    public UserImportBatchResult validate(byte[] csvBytes, String filename) {
        List<String> fileLevelErrors = new ArrayList<>();
        List<UserImportRow> rows = parseRows(csvBytes, fileLevelErrors);
        if (!fileLevelErrors.isEmpty()) {
            return new UserImportBatchResult(filename, fileLevelErrors, List.of());
        }
        validateRows(rows);
        return new UserImportBatchResult(filename, fileLevelErrors, rows);
    }

    /**
     * Re-validates (state may have changed since the CEO reviewed the Preview - e.g. another admin
     * created a conflicting email in the meantime) and then imports every row that is still valid.
     * Each row is created independently (its own {@code UserAdminService#createUser} transaction,
     * which is itself {@code @Transactional}) so one row's failure can never roll back or block any
     * other row - the batch always finishes with an unambiguous per-row outcome, never a partial,
     * silently-inconsistent state.
     */
    @Transactional
    public UserImportBatchResult importValidated(User ceo, byte[] csvBytes, String filename) {
        authorizationService.requireAccessClass(ceo, AccessClass.CEO_OWNER, "User CSV import");
        UserImportBatchResult result = validate(csvBytes, filename);
        if (result.isHasFileLevelErrors()) {
            return result;
        }
        for (UserImportRow row : result.getRows()) {
            if (row.isDuplicateInDb()) {
                // validate() (called just above) already found this email on an existing user -
                // that makes the row fail isValid(), so it must be handled here BEFORE the isValid()
                // check below, or it would silently stay NOT_ATTEMPTED forever instead of being
                // reported as skipped.
                row.setOutcome(UserImportOutcome.SKIPPED_EXISTING);
                continue;
            }
            if (!row.isValid()) {
                continue; // stays NOT_ATTEMPTED - never imported, never counted as failed
            }
            if (userRepository.findByEmailIgnoreCase(row.getEmail()).isPresent()) {
                // Re-checked here even though validate() just did the same check moments earlier -
                // closes the tiny remaining race window between that check and the create() call
                // below (e.g. another admin's import committing a conflicting email in between).
                row.setOutcome(UserImportOutcome.SKIPPED_EXISTING);
                continue;
            }
            try {
                String password = RandomPasswordGenerator.generate();
                User created = userAdminService.createUser(ceo, row.getFullName(), row.getEmail(), password,
                        row.getResolvedBusinessRole().getId(),
                        "Imported via CSV: " + filename + " (row " + row.getRowNumber() + ")");
                if (!row.isResolvedActive()) {
                    userAdminService.deactivate(ceo, created.getId(), "Deactivated on CSV import: " + filename);
                }
                row.setOutcome(UserImportOutcome.IMPORTED);
                row.setGeneratedPassword(password);
            } catch (DomainException e) {
                row.setOutcome(UserImportOutcome.FAILED);
                row.setFailureReason(e.getMessage());
            }
        }
        recordBatchAudit(ceo, filename, result);
        return result;
    }

    private void recordBatchAudit(User ceo, String filename, UserImportBatchResult result) {
        String summary = "filename=" + filename + "; totalRows=" + result.getTotalRows()
                + "; imported=" + result.getImportedCount() + "; skippedExisting=" + result.getSkippedExistingCount()
                + "; failed=" + result.getFailedCount() + "; invalid=" + result.getInvalidCount();
        // No single real entity represents "this whole batch" - a fresh id is minted purely to
        // serve as this one audit row's required target_entity_id, the same way every other
        // entity id in this system is minted (UuidV7), never reused as a real foreign key anywhere.
        auditService.record(ceo, java.util.Optional.empty(), "USER_ADMIN", "USER_CSV_IMPORT_COMPLETED",
                "user_csv_import_batch", UuidV7.generate(), summary);
    }

    // ------------------------------------------------------------------ parsing

    private List<UserImportRow> parseRows(byte[] csvBytes, List<String> fileLevelErrors) {
        if (csvBytes == null || csvBytes.length == 0) {
            fileLevelErrors.add("The uploaded file is empty.");
            return List.of();
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();
        List<UserImportRow> rows = new ArrayList<>();
        try (var reader = new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            Set<String> headers = new HashSet<>();
            for (String h : parser.getHeaderNames()) {
                headers.add(h.trim().toLowerCase(Locale.ROOT));
            }
            List<String> missing = REQUIRED_HEADERS.stream().filter(h -> !headers.contains(h)).toList();
            if (!missing.isEmpty()) {
                fileLevelErrors.add("Missing required column(s): " + String.join(", ", missing));
                return List.of();
            }
            boolean hasStatusColumn = headers.contains(OPTIONAL_STATUS_HEADER);

            int rowNumber = 0;
            for (CSVRecord record : parser) {
                rowNumber++;
                String fullName = safeGet(record, "full_name");
                String email = safeGet(record, "email");
                String businessRole = safeGet(record, "business_role");
                String status = hasStatusColumn ? safeGet(record, OPTIONAL_STATUS_HEADER) : "";
                if (fullName.isBlank() && email.isBlank() && businessRole.isBlank() && status.isBlank()) {
                    continue; // a genuinely blank row (e.g. Excel trailing padding) - silently skipped, not an error
                }
                rows.add(new UserImportRow(rowNumber, fullName, email, businessRole, status));
            }
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            fileLevelErrors.add("Malformed CSV: " + e.getMessage());
            return List.of();
        }
        if (rows.isEmpty() && fileLevelErrors.isEmpty()) {
            fileLevelErrors.add("The CSV has a header row but no data rows.");
        }
        return rows;
    }

    private static String safeGet(CSVRecord record, String column) {
        try {
            String value = record.get(column);
            return value == null ? "" : value.trim();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ validation

    private void validateRows(List<UserImportRow> rows) {
        Set<String> seenEmailsInFile = new HashSet<>();
        for (UserImportRow row : rows) {
            if (row.getFullName().isBlank()) {
                row.addError("Full Name is missing");
            }
            if (row.getEmail().isBlank()) {
                row.addError("Email is missing");
            } else if (!EMAIL_PATTERN.matcher(row.getEmail()).matches()) {
                row.addError("Invalid email format");
            } else {
                String normalized = row.normalizedEmail();
                if (!seenEmailsInFile.add(normalized)) {
                    row.markDuplicateInFile();
                    row.addError("Duplicate email within this file");
                } else if (userRepository.findByEmailIgnoreCase(row.getEmail()).isPresent()) {
                    row.markDuplicateInDb();
                    row.addError("A user with this email already exists");
                }
            }
            if (row.getBusinessRoleName().isBlank()) {
                row.addError("Business Role is missing");
            } else {
                businessRoleRepository.findByRoleNameIgnoreCaseAndActiveTrue(row.getBusinessRoleName())
                        .ifPresentOrElse(row::setResolvedBusinessRole,
                                () -> row.addError("Unknown Business Role \"" + row.getBusinessRoleName() + "\""));
            }
            resolveAccountStatus(row);
        }
    }

    private void resolveAccountStatus(UserImportRow row) {
        String raw = row.getAccountStatus();
        if (raw == null || raw.isBlank()) {
            row.setResolvedActive(true); // default: ACTIVE
            return;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "ACTIVE" -> row.setResolvedActive(true);
            case "INACTIVE" -> row.setResolvedActive(false);
            default -> row.addError("Invalid Account Status \"" + raw + "\" (must be ACTIVE or INACTIVE)");
        }
    }
}
