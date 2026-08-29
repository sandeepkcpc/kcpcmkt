package com.kcpc.mkt.idea.dto;

import com.kcpc.mkt.planning.domain.ContentPriority;
import com.kcpc.mkt.planning.domain.PlanningMode;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Workflow redesign: Planning is no longer a separate workflow stage (PL/PLRV/PLAP) - every field
 * Planning used to collect (Category, Priority, Schedule, Drive Link, Outputs/Reel Type/Publication
 * Scope, Models/Talent, initial Shoot Team) is now supplied in the SAME "Idea Review + Planning
 * Details" action that approves the idea (see {@code IdeaService#decide}/{@code #approve}). Only
 * relevant when the decision is APPROVE; ignored for REJECT/RETAIN.
 *
 * <p>Any number of Output Type/Reel-Type-set/Publication-Scope groups can be created here (see
 * {@link PlanningOutputRequest}) - additional output groups can still also be added afterward via
 * the Publishing tab's existing Outputs management, unchanged and un-time-limited.
 */
public record PlanningApprovalRequest(
        String categoryText,
        ContentPriority contentPriority,
        String skuReference,
        boolean skuNotApplicable,
        PlanningMode planningMode,
        LocalDate plannedLiveDate,
        LocalDate shootDate,
        LocalDate editDate,
        String urgencyReason,
        String folderLink,
        List<UUID> talentUserIds,
        List<PlanningOutputRequest> outputs,
        List<UUID> camerapersonUserIds,
        UUID leadCamerapersonUserId
) {
}
