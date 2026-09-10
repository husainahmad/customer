package com.harmoni.pos.customer.adapter.in.web.advice;

import com.harmoni.pos.customer.domain.exception.CustomerNotFoundException;
import com.harmoni.pos.customer.domain.exception.CustomerSessionNotFoundException;
import com.harmoni.pos.customer.domain.exception.DuplicateCustomerException;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerMessageException;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerSessionException;
import com.harmoni.pos.customer.domain.exception.MenuServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps domain, validation and unexpected exceptions to ApiErrorResponse bodies with stable error codes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomerNotFound(CustomerNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(CustomerSessionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSessionNotFound(CustomerSessionNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, "CUSTOMER_SESSION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateCustomer(DuplicateCustomerException e) {
        return build(HttpStatus.CONFLICT, "DUPLICATE_CUSTOMER", e.getMessage());
    }

    @ExceptionHandler(InvalidCustomerSessionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSession(InvalidCustomerSessionException e) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_CUSTOMER_SESSION", e.getMessage());
    }

    @ExceptionHandler(InvalidCustomerMessageException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidMessage(InvalidCustomerMessageException e) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_CUSTOMER_MESSAGE", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
                "Request validation failed", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(MenuServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleMenuServiceUnavailable(MenuServiceUnavailableException e) {
        log.warn("Menu service unavailable: {}", e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "MENU_SERVICE_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(Instant.now(), status.value(), code, message, null));
    }
}
