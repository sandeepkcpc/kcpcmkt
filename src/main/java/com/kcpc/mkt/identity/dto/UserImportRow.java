package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.BusinessRole;

import java.util.ArrayList;
import java.util.List;

/**
 * One CSV data row, carried through both the Preview (validate-only, no DB write) and Import
 * (actually create the user) phases. A plain mutable class, not a record - fields are filled in
 * progressively as validation/import proceeds, and JSP EL needs plain getters.
 *
 * <p>{@code generatedPassword} is populated ONLY for a row this exact request just imported, held
 * only in this in-memory object for the single Result Summary render - never logged, never written
 * to the CSV, never persisted anywhere in plaintext (the DB only ever sees its bcrypt hash, via the
 * unchanged {@code UserAdminService#createUser}).
 */
public class UserImportRow {

    private final int rowNumber;
    private final String fullNameRaw;
    private final String emailRaw;
    private final String businessRoleNameRaw;
    private final String accountStatusRaw;

    private final List<String> errors = new ArrayList<>();
    private boolean duplicateInFile;
    private boolean duplicateInDb;
    private BusinessRole resolvedBusinessRole;
    private boolean resolvedActive = true;

    private UserImportOutcome outcome = UserImportOutcome.NOT_ATTEMPTED;
    private String failureReason;
    private String generatedPassword;

    public UserImportRow(int rowNumber, String fullNameRaw, String emailRaw, String businessRoleNameRaw,
                          String accountStatusRaw) {
        this.rowNumber = rowNumber;
        this.fullNameRaw = fullNameRaw;
        this.emailRaw = emailRaw;
        this.businessRoleNameRaw = businessRoleNameRaw;
        this.accountStatusRaw = accountStatusRaw;
    }

    public void addError(String message) {
        errors.add(message);
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getFullName() {
        return fullNameRaw;
    }

    public String getEmail() {
        return emailRaw;
    }

    /** Normalized for case-insensitive duplicate/lookup comparisons - never displayed. */
    public String normalizedEmail() {
        return emailRaw == null ? "" : emailRaw.trim().toLowerCase();
    }

    public String getBusinessRoleName() {
        return businessRoleNameRaw;
    }

    public String getAccountStatus() {
        return accountStatusRaw;
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean isDuplicateInFile() {
        return duplicateInFile;
    }

    public void markDuplicateInFile() {
        this.duplicateInFile = true;
    }

    public boolean isDuplicateInDb() {
        return duplicateInDb;
    }

    public void markDuplicateInDb() {
        this.duplicateInDb = true;
    }

    public BusinessRole getResolvedBusinessRole() {
        return resolvedBusinessRole;
    }

    public void setResolvedBusinessRole(BusinessRole resolvedBusinessRole) {
        this.resolvedBusinessRole = resolvedBusinessRole;
    }

    /** Informational only, shown on Preview so the CEO can visually confirm it before importing -
     * Access Class is never itself a CSV input, it is always resolved from the Business Role. */
    public AccessClass getResolvedAccessClass() {
        return resolvedBusinessRole == null ? null : resolvedBusinessRole.getAccessClassCode();
    }

    public boolean isResolvedActive() {
        return resolvedActive;
    }

    public void setResolvedActive(boolean resolvedActive) {
        this.resolvedActive = resolvedActive;
    }

    /** Valid = safe to import: no field errors, not a same-file duplicate, not an existing DB user. */
    public boolean isValid() {
        return errors.isEmpty() && !duplicateInFile && !duplicateInDb;
    }

    public UserImportOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(UserImportOutcome outcome) {
        this.outcome = outcome;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getGeneratedPassword() {
        return generatedPassword;
    }

    public void setGeneratedPassword(String generatedPassword) {
        this.generatedPassword = generatedPassword;
    }
}
