package com.kcpc.mkt.production.dto;

import java.util.UUID;

/** {@code editorUserId == null} clears the Edit Lead - deliberately not {@code @NotNull}. */
public record SetEditLeadRequest(UUID editorUserId) {
}
