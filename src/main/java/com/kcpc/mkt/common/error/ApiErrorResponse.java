package com.kcpc.mkt.common.error;

import java.time.Instant;

public record ApiErrorResponse(String code, String message, Instant timestamp, String path) {

    public static ApiErrorResponse of(ErrorCode code, String message, String path) {
        return new ApiErrorResponse(code.name(), message, Instant.now(), path);
    }
}
