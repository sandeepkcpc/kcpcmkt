package com.kcpc.mkt.workflow.dto;

import com.kcpc.mkt.workflow.domain.TaskStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReassignRequest(@NotNull TaskStage taskStage, @NotEmpty List<UUID> newAssigneeUserIds,
                               List<UUID> newModelUserIds, @NotBlank String reason) {
}
