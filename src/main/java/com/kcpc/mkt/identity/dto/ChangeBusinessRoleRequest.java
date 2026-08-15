package com.kcpc.mkt.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeBusinessRoleRequest(@NotNull UUID businessRoleId, @NotBlank String reason) {
}
