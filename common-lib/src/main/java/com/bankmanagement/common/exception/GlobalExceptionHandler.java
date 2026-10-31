package com.bankmanagement.common.exception;

import com.bankmanagement.common.web.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every failure into an RFC 7807 problem response. Internal details (stack traces, SQL,
 * class names) are logged, never returned to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        Map<String, String> errors = ex.getField() == null ? null : Map.of(ex.getField(), ex.getMessage());
        return build(ex.getCode(), ex.getMessage(), errors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return build(ErrorCode.VALIDATION, "One or more fields are invalid.", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return build(ErrorCode.VALIDATION, "One or more parameters are invalid.", errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex) {
        return build(ErrorCode.VALIDATION, "The request is malformed or has invalid parameters.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.VALIDATION, "The uploaded file is too large.", null);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ProblemDetail> handleMultipart(Exception ex) {
        return build(ErrorCode.VALIDATION, "Send the file as multipart/form-data in the part named 'file'.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, "You do not have permission to perform this action.", null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return build(ErrorCode.CONFLICT, "This record was changed by someone else. Reload and try again.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.CONFLICT, "The request conflicts with existing data.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethod(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed.");
        decorate(pd, ErrorCode.VALIDATION, null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(pd);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "Resource not found.", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL, "An unexpected error occurred. Quote the request id to support.", null);
    }

    public static ResponseEntity<ProblemDetail> build(ErrorCode code, String detail, Map<String, String> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), detail);
        decorate(pd, code, errors);
        return ResponseEntity.status(code.status()).body(pd);
    }

    private static void decorate(ProblemDetail pd, ErrorCode code, Map<String, String> errors) {
        pd.setTitle(code.status().getReasonPhrase());
        pd.setProperty("code", code.name());
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        if (errors != null && !errors.isEmpty()) {
            pd.setProperty("errors", errors);
        }
    }
}
