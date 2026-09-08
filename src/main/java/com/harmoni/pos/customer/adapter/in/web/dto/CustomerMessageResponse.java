package com.harmoni.pos.customer.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * A persisted chat message returned by the customer service.
 * Mirrors the customer project's {@code CustomerMessageResponse}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerMessageResponse(
        Long id,
        Long sessionId,
        CustomerMessageRole role,
        String message,
        Instant createdAt
) {
}
