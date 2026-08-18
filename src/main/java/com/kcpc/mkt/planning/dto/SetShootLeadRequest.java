package com.kcpc.mkt.planning.dto;

import java.util.UUID;

/** {@code cameramanUserId == null} clears the Shoot Lead - deliberately not {@code @NotNull}. */
public record SetShootLeadRequest(UUID cameramanUserId) {
}
