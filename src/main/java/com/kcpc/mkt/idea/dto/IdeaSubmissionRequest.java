package com.kcpc.mkt.idea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IdeaSubmissionRequest(
        @NotBlank @Size(max = 200) String title,
        String referenceLink,
        String notesRemarks
) {
}
