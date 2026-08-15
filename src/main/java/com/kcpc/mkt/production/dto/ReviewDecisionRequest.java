package com.kcpc.mkt.production.dto;

import java.util.List;
import java.util.UUID;

public record ReviewDecisionRequest(boolean approve, String reason, List<UUID> qualifyingRecipientUserIds) {
}
