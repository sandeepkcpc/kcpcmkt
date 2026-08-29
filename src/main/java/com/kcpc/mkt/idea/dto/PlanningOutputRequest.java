package com.kcpc.mkt.idea.dto;

import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.ReelType;

import java.util.List;
import java.util.UUID;

/** One Output Type/Reel-Type-set/Publication-Scope group within a {@link PlanningApprovalRequest}
 * - a plan can be approved with any number of these (previously capped at one). */
public record PlanningOutputRequest(
        OutputType outputType,
        List<ReelType> reelTypes,
        String outputTitleDescription,
        List<UUID> publicationTargetIds
) {
}
