package com.kcpc.mkt.reporting.dto;

import com.kcpc.mkt.marks.domain.PersonalMarkAttribution;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** SAD-ADR-008: full-graph JSON export per Content ID (SRS-REQ-080 clean Business-OS handoff readiness). */
public record ContentPlanExport(UUID contentPlanId, String contentId, WorkflowStatus status,
                                 List<OutputExport> plannedOutputs, List<MarkExport> marks,
                                 List<PublicationExport> publicationEvents) {

    public record OutputExport(UUID plannedOutputId, String outputType, String reelType) {
    }

    public record MarkExport(UUID attributionId, String recipientFullName, String roleType, String markValue,
                              Instant attributedAt) {
        public static MarkExport from(PersonalMarkAttribution a) {
            return new MarkExport(a.getId(), a.getRecipient().getFullName(), a.getRoleType().name(),
                    a.getAttributedMarkValue().toPlainString(), a.getAttributedAt());
        }
    }

    public record PublicationExport(UUID eventId, String eventType, Instant actualPublicationTimestamp,
                                     String evidenceUrl, String targetName) {
        public static PublicationExport from(ActualPublicationEvent e) {
            return new PublicationExport(e.getId(), e.getEventType().name(), e.getActualPublicationTimestamp(),
                    e.getEvidenceUrl(), e.getPublicationTarget().getTargetName());
        }
    }

}
