package com.kcpc.mkt.publishing.dto;

import com.kcpc.mkt.publishing.domain.PublicationEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RecordPublicationRequest(@NotNull UUID plannedOutputId, @NotNull UUID publicationTargetId,
                                        @NotNull PublicationEventType eventType,
                                        @NotNull Instant actualPublicationTimestamp,
                                        @NotBlank String evidenceUrl) {
}
