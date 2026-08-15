package com.kcpc.mkt.common.error;

import org.springframework.http.HttpStatus;

public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public DomainException(ErrorCode errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public static DomainException notFound(String message) {
        return new DomainException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public static DomainException forbidden(ErrorCode code, String message) {
        return new DomainException(code, HttpStatus.FORBIDDEN, message);
    }

    public static DomainException unauthorized(ErrorCode code, String message) {
        return new DomainException(code, HttpStatus.UNAUTHORIZED, message);
    }

    public static DomainException conflict(ErrorCode code, String message) {
        return new DomainException(code, HttpStatus.CONFLICT, message);
    }

    public static DomainException badRequest(ErrorCode code, String message) {
        return new DomainException(code, HttpStatus.BAD_REQUEST, message);
    }
}
