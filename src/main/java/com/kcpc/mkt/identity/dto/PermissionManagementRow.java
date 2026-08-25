package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionScopeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the User Detail unified Permission Management table - one per catalogue permission
 * (never hardcoded: iterates {@code OperationalPermission.values()}), pre-resolved to exactly one
 * of 3 states so the JSP performs no derivation of its own:
 * <ul>
 *   <li><b>Not Granted</b> - zero currently-valid grants for this user+permission.</li>
 *   <li><b>Single</b> (the common/normal case) - exactly one currently-valid grant; every
 *       single-grant field below is populated and directly inline-editable.</li>
 *   <li><b>Multi</b> - 2+ simultaneously currently-valid grants (the schema and service layer both
 *       allow this - no unique constraint on grantee+permission - so it must be handled explicitly
 *       rather than silently collapsed/merged/hidden). The single-grant fields are meaningless in
 *       this state; {@link #getActiveGrants()} carries the full, individually-manageable list.</li>
 * </ul>
 *
 * <p>Plain class with {@code getX()} accessors, not a {@code record} - see
 * {@link com.kcpc.mkt.reporting.dto.KpiValue}'s class doc for why a record's no-prefix accessors
 * break classic JSP EL property resolution (ENG-031).
 */
public class PermissionManagementRow {

    private final OperationalPermission permission;
    private final String displayName;
    private final String description;
    private final boolean granted;
    private final boolean multi;

    // Meaningful only when granted && !multi.
    private final UUID grantId;
    private final PermissionScopeType scopeType;
    private final List<LifecycleStage> stages;
    private final String stageLabel;
    private final Instant effectiveFrom;
    private final Instant effectiveUntil;
    private final String reason;

    private final String statusLabel;
    private final String statusClass;

    // Meaningful only when multi.
    private final List<GrantedPermissionRow> activeGrants;

    private PermissionManagementRow(OperationalPermission permission, String displayName, String description,
                                     boolean granted, boolean multi, UUID grantId, PermissionScopeType scopeType,
                                     List<LifecycleStage> stages, String stageLabel, Instant effectiveFrom,
                                     Instant effectiveUntil, String reason, String statusLabel, String statusClass,
                                     List<GrantedPermissionRow> activeGrants) {
        this.permission = permission;
        this.displayName = displayName;
        this.description = description;
        this.granted = granted;
        this.multi = multi;
        this.grantId = grantId;
        this.scopeType = scopeType;
        this.stages = stages;
        this.stageLabel = stageLabel;
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
        this.reason = reason;
        this.statusLabel = statusLabel;
        this.statusClass = statusClass;
        this.activeGrants = activeGrants;
    }

    public static PermissionManagementRow notGranted(OperationalPermission permission, String displayName, String description) {
        return new PermissionManagementRow(permission, displayName, description, false, false, null, null,
                List.of(), "-", null, null, null, "Not Granted", "perm-status-none", List.of());
    }

    public static PermissionManagementRow single(OperationalPermission permission, String displayName, String description,
                                                   UUID grantId, PermissionScopeType scopeType, List<LifecycleStage> stages,
                                                   String stageLabel, Instant effectiveFrom, Instant effectiveUntil,
                                                   String reason) {
        boolean restricted = scopeType != PermissionScopeType.GLOBAL;
        String statusLabel = restricted ? "Active · Restricted" : "Active · Global";
        String statusClass = restricted ? "perm-status-restricted" : "perm-status-global";
        return new PermissionManagementRow(permission, displayName, description, true, false, grantId, scopeType,
                stages, stageLabel, effectiveFrom, effectiveUntil, reason, statusLabel, statusClass, List.of());
    }

    public static PermissionManagementRow multi(OperationalPermission permission, String displayName, String description,
                                                  List<GrantedPermissionRow> activeGrants) {
        String statusLabel = activeGrants.size() + " Active Grants · Multiple Scopes";
        return new PermissionManagementRow(permission, displayName, description, true, true, null, null, List.of(),
                "-", null, null, null, statusLabel, "perm-status-multi", activeGrants);
    }

    public OperationalPermission getPermission() {
        return permission;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isGranted() {
        return granted;
    }

    public boolean isMulti() {
        return multi;
    }

    /** Granted but not simple unrestricted GLOBAL coverage: for a single grant, the one active
     * grant isn't GLOBAL; for multi, none of the several active grants is GLOBAL (if even one of
     * them is GLOBAL, the user already has full coverage regardless of the others). */
    public boolean isRestricted() {
        if (!granted) {
            return false;
        }
        if (multi) {
            return activeGrants.stream().noneMatch(g -> g.getScopeType() == PermissionScopeType.GLOBAL);
        }
        return scopeType != PermissionScopeType.GLOBAL;
    }

    public UUID getGrantId() {
        return grantId;
    }

    public PermissionScopeType getScopeType() {
        return scopeType;
    }

    public List<LifecycleStage> getStages() {
        return stages;
    }

    public String getStageLabel() {
        return stageLabel;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveUntil() {
        return effectiveUntil;
    }

    public String getReason() {
        return reason;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getStatusClass() {
        return statusClass;
    }

    public List<GrantedPermissionRow> getActiveGrants() {
        return activeGrants;
    }
}
