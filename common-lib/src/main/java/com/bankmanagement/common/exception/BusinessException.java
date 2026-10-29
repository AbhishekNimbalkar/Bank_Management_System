package com.bankmanagement.common.exception;

/** Base for all expected, client-visible failures. The message is safe to show to users. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final String field;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getField() {
        return field;
    }

    public static BusinessException notFound(String what) {
        return new BusinessException(ErrorCode.NOT_FOUND, what + " not found.");
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    public static BusinessException conflict(String message, String field) {
        return new BusinessException(ErrorCode.CONFLICT, message, field);
    }

    public static BusinessException rule(String message) {
        return new BusinessException(ErrorCode.BUSINESS_RULE, message);
    }

    public static BusinessException validation(String message, String field) {
        return new BusinessException(ErrorCode.VALIDATION, message, field);
    }
}
