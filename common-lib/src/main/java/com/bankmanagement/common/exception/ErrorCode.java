package com.bankmanagement.common.exception;

import org.springframework.http.HttpStatus;

/** Stable, client-facing error codes. Clients should branch on the code, never on the message. */
public enum ErrorCode {
    VALIDATION(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    BUSINESS_RULE(HttpStatus.UNPROCESSABLE_ENTITY),
    QUOTA_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    DEPENDENCY_FAILURE(HttpStatus.BAD_GATEWAY),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
