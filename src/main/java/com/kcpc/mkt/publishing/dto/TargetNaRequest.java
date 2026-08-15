package com.kcpc.mkt.publishing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TargetNaRequest(@NotNull UUID plannedOutputId, @NotNull UUID publicationTargetId, String reason) {
}
