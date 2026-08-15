package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionScopeType;

import java.time.Instant;
import java.util.UUID;

/**
 * A consolidated, read-only row for the "who currently holds what permission" admin screen
 * (`/app/admin/permissions`) - one row per currently active {@code PermissionGrant}, across every
 * user, rather than the per-user view on the User Detail screen.
 *
 * <p>Plain class with {@code getX()} accessors, not a {@code record}: read directly by JSP EL -
 * see {@link com.kcpc.mkt.reporting.dto.KpiValue}'s class doc for why a record's no-prefix
 * accessors break classic JSP EL property resolution (ENG-031).
 */
public class PermissionGrantSummary {

    private final UUID grantId;
    private final UUID granteeUserId;
    private final String granteeName;
    private final String granteeEmail;
    private final String grantorName;
    private final OperationalPermission permission;
    private final PermissionScopeType scopeType;
    private final String scopeDetail;
    private final Instant effectiveFrom;
    private final Instant effectiveUntil;

    public PermissionGrantSummary(UUID grantId, UUID granteeUserId, String granteeName, String granteeEmail,
                                   String grantorName, OperationalPermission permission, PermissionScopeType scopeType,
                                   String scopeDetail, Instant effectiveFrom, Instant effectiveUntil) {
        this.grantId = grantId;
        this.granteeUserId = granteeUserId;
        this.granteeName = granteeName;
        this.granteeEmail = granteeEmail;
        this.grantorName = grantorName;
        this.permission = permission;
        this.scopeType = scopeType;
        this.scopeDetail = scopeDetail;
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public UUID getGranteeUserId() {
        return granteeUserId;
    }

    public String getGranteeName() {
        return granteeName;
    }

    public String getGranteeEmail() {
        return granteeEmail;
    }

    public String getGrantorName() {
        return grantorName;
    }

    public OperationalPermission getPermission() {
        return permission;
    }

    public PermissionScopeType getScopeType() {
        return scopeType;
    }

    /** Human-readable scope qualifier: empty for GLOBAL, stage list for STAGE_RESTRICTED, item count for ITEM_SPECIFIC. */
    public String getScopeDetail() {
        return scopeDetail;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveUntil() {
        return effectiveUntil;
    }
}
