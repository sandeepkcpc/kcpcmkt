package com.kcpc.mkt.planning.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignCameramanRequest(@NotNull UUID cameramanUserId) {
}
