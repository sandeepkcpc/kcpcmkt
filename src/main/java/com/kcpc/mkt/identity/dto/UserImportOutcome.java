package com.kcpc.mkt.identity.dto;

/** A CSV import row's outcome after the Import step actually runs (as opposed to the Preview
 * step's validity check, which only asks "would this row be safe to import"). */
public enum UserImportOutcome {
    NOT_ATTEMPTED,
    IMPORTED,
    SKIPPED_EXISTING,
    FAILED
}
