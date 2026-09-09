package com.harmoni.pos.customer.adapter.in.web.advice;

import java.time.Instant;
import java.util.Map;

/**
 * Standardised error response body returned by {@link GlobalExceptionHandler}.
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> errors
) {
}
