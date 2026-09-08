package com.harmoni.pos.customer.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Response from POST /api/v1/customer-sessions.
 * Mirrors the customer project's session shape:
 * {id, customerId, sessionToken, source, status, createdAt, updatedAt}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerSessionResponse(
        Long id,
        Long customerId,
        String sessionToken,
        String source,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
}
