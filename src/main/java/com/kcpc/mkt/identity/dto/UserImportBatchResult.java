package com.kcpc.mkt.identity.dto;

import java.util.List;

/**
 * Aggregate result of one CSV User import attempt - used for both the Preview render (validate
 * only, {@code rows[*].outcome} all NOT_ATTEMPTED) and the final Result Summary render (after
 * {@code UserCsvImportService#importValidated} has run). File-level errors (missing headers,
 * malformed CSV, empty file) short-circuit before any row-level parsing is even attempted.
 */
public class UserImportBatchResult {

    private final String filename;
    private final List<String> fileLevelErrors;
    private final List<UserImportRow> rows;

    public UserImportBatchResult(String filename, List<String> fileLevelErrors, List<UserImportRow> rows) {
        this.filename = filename;
        this.fileLevelErrors = fileLevelErrors;
        this.rows = rows;
    }

    public String getFilename() {
        return filename;
    }

    public List<String> getFileLevelErrors() {
        return fileLevelErrors;
    }

    public boolean isHasFileLevelErrors() {
        return !fileLevelErrors.isEmpty();
    }

    public List<UserImportRow> getRows() {
        return rows;
    }

    public int getTotalRows() {
        return rows.size();
    }

    public long getValidCount() {
        return rows.stream().filter(UserImportRow::isValid).count();
    }

    /** Excludes DB-duplicate rows - those are their own bucket ({@link #getDuplicateInDbCount()},
     * skipped rather than rejected), never double-counted here too. In-file duplicates stay
     * counted as invalid (rejected), since - unlike a DB duplicate - there is no existing user to
     * safely skip in favor of. */
    public long getInvalidCount() {
        return rows.stream().filter(r -> !r.isValid() && !r.isDuplicateInDb()).count();
    }

    public long getDuplicateInFileCount() {
        return rows.stream().filter(UserImportRow::isDuplicateInFile).count();
    }

    public long getDuplicateInDbCount() {
        return rows.stream().filter(UserImportRow::isDuplicateInDb).count();
    }

    public long getImportedCount() {
        return rows.stream().filter(r -> r.getOutcome() == UserImportOutcome.IMPORTED).count();
    }

    public long getSkippedExistingCount() {
        return rows.stream().filter(r -> r.getOutcome() == UserImportOutcome.SKIPPED_EXISTING).count();
    }

    public long getFailedCount() {
        return rows.stream().filter(r -> r.getOutcome() == UserImportOutcome.FAILED).count();
    }
}
