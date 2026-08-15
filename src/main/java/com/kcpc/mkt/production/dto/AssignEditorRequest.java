package com.kcpc.mkt.production.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignEditorRequest(@NotNull UUID editorUserId) {
}
