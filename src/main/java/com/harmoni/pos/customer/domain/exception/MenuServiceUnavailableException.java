package com.harmoni.pos.customer.domain.exception;

/**
 * Thrown when the Menu Service cannot be reached or fails to answer.
 * <p>
 * Raised by REST-facing gateway methods (e.g. raw category listings) so the
 * {@code GlobalExceptionHandler} can translate it into a proper HTTP response
 * instead of a silent, empty fallback.
 */
public class MenuServiceUnavailableException extends RuntimeException {

    public MenuServiceUnavailableException(String detail) {
        super("Menu service unavailable: " + detail);
    }

    public MenuServiceUnavailableException(String detail, Throwable cause) {
        super("Menu service unavailable: " + detail, cause);
    }
}