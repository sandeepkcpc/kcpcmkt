package com.kcpc.mkt.planning.dto;

import java.util.List;
import java.util.UUID;

public record PublicationScopeRequest(List<UUID> publicationTargetIds) {
}
