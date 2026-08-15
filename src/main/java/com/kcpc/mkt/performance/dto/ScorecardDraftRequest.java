package com.kcpc.mkt.performance.dto;

import java.math.BigDecimal;

public record ScorecardDraftRequest(Integer views3sec, boolean views3secIsNa, Integer plays,
                                     BigDecimal averageWatchTimeSeconds, boolean watchTimeIsNa,
                                     BigDecimal videoLengthSeconds, boolean videoLengthIsNa,
                                     Integer linkClicks, boolean clicksIsNa, Integer impressions) {
}
