package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GrantPermissionRequest(@NotNull UUID granteeUserId, @NotNull OperationalPermission permission,
                                      @NotNull PermissionScopeType scopeType, List<LifecycleStage> stages,
                                      List<UUID> workflowInstanceIds, Instant effectiveFrom, Instant effectiveUntil,
                                      @NotBlank String reason) {
}
