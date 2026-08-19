package com.kcpc.mkt.idea.dto;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;

import java.time.Instant;
import java.util.UUID;

public record IdeaResponse(UUID ideaId, String businessIdeaCode, String title, String referenceLink,
                            String notesRemarks, String additionalNote, String submittedByName,
                            UUID submittedByUserId, Instant submittedAt, WorkflowStatus status) {
    public static IdeaResponse from(Idea idea) {
        return new IdeaResponse(idea.getId(), idea.getBusinessIdeaCode(), idea.getTitle(), idea.getReferenceLink(),
                idea.getNotesRemarks(), idea.getAdditionalNote(), idea.getSubmittedBy().getFullName(),
                idea.getSubmittedBy().getId(), idea.getSubmittedAt(), idea.getWorkflowInstance().getCurrentStatusCode());
    }
}
