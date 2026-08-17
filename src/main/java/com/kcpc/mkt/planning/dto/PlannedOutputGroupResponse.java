package com.kcpc.mkt.planning.dto;

import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.ReelType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Planning Workspace AJAX response for Add/Edit Output: the grouped row's own fields (not its
 * Publication Target chips, which the separate target endpoints already keep in sync via their
 * own AJAX round-trip) - enough for planning-outputs.js to render/update the row in place without
 * a full page reload.
 */
public record PlannedOutputGroupResponse(UUID groupId, OutputType outputType, List<ReelType> reelTypes,
                                          String titleDescription) {

    public static PlannedOutputGroupResponse of(List<PlannedOutput> groupMembers) {
        PlannedOutput first = groupMembers.get(0);
        List<ReelType> reelTypes = groupMembers.stream()
                .map(PlannedOutput::getReelType)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        return new PlannedOutputGroupResponse(first.getReelGroupId(), first.getOutputType(), reelTypes,
                first.getTitleDescription());
    }
}
