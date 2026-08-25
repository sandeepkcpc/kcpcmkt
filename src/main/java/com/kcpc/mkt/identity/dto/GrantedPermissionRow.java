package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionScopeType;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the User Detail "Current Granted Permissions" table - every {@code PermissionGrant}
 * a user has ever held (active, expired, or revoked), pre-resolved server-side (stage-scope
 * labels, currently-valid status, grant reason from the audit trail) so the JSP performs no
 * derivation of its own.
 *
 * <p>Plain class with {@code getX()} accessors, not a {@code record} - see
 * {@link com.kcpc.mkt.reporting.dto.KpiValue}'s class doc for why a record's no-prefix accessors
 * break classic JSP EL property resolution (ENG-031).
 */
public class GrantedPermissionRow {

    private final UUID grantId;
    private final OperationalPermission permission;
    private final String description;
    private final PermissionScopeType scopeType;
    private final String stageLabel;
    private final Instant effectiveFrom;
    private final Instant effectiveUntil;
    private final String reason;
    private final boolean currentlyValid;
    private final boolean revoked;

    public GrantedPermissionRow(UUID grantId, OperationalPermission permission, String description,
                                 PermissionScopeType scopeType, String stageLabel, Instant effectiveFrom,
                                 Instant effectiveUntil, String reason, boolean currentlyValid, boolean revoked) {
        this.grantId = grantId;
        this.permission = permission;
        this.description = description;
        this.scopeType = scopeType;
        this.stageLabel = stageLabel;
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
        this.reason = reason;
        this.currentlyValid = currentlyValid;
        this.revoked = revoked;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public OperationalPermission getPermission() {
        return permission;
    }

    public String getDescription() {
        return description;
    }

    public PermissionScopeType getScopeType() {
        return scopeType;
    }

    /** Comma-joined LifecycleStage names for STAGE_RESTRICTED, "N item(s)" for ITEM_SPECIFIC, "-" for GLOBAL. */
    public String getStageLabel() {
        return stageLabel;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveUntil() {
        return effectiveUntil;
    }

    /** From the PERMISSION_GRANTED audit-log row for this grant - "N/A" for a quick grant, whatever
     * the admin typed for an advanced/custom-scope grant. Never null - falls back to "-" if somehow missing. */
    public String getReason() {
        return reason;
    }

    /** {@link com.kcpc.mkt.identity.domain.PermissionGrant#isCurrentlyValid} at render time - the
     * authoritative "Active" check (unlike a naive active-flag-only check, this also excludes a
     * grant whose effectiveUntil has passed but was never explicitly revoked). */
    public boolean isCurrentlyValid() {
        return currentlyValid;
    }

    public boolean isRevoked() {
        return revoked;
    }
}
