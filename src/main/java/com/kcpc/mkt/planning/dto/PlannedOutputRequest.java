package com.kcpc.mkt.planning.dto;

import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.ReelType;
import jakarta.validation.constraints.NotNull;

public record PlannedOutputRequest(@NotNull OutputType outputType, ReelType reelType, String titleDescription) {
}
