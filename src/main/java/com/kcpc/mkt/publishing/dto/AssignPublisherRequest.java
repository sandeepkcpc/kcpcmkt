package com.kcpc.mkt.publishing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignPublisherRequest(@NotNull UUID publisherUserId) {
}
