package com.kcpc.mkt.identity.dto;

import com.kcpc.mkt.identity.domain.AccessClass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBusinessRoleRequest(@NotBlank String roleName, @NotNull AccessClass accessClass) {
}
