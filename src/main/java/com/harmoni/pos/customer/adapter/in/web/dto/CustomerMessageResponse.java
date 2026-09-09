package com.harmoni.pos.customer.adapter.in.web.dto;

import com.harmoni.pos.customer.domain.model.CustomerMessage;
import com.harmoni.pos.customer.domain.model.CustomerMessageRole;

import java.time.Instant;

/**
 * Response DTO for a chat message.
 */
public record CustomerMessageResponse(
        Long id,
        Long sessionId,
        CustomerMessageRole role,
        String message,
        Instant createdAt
) {

    public static CustomerMessageResponse from(CustomerMessage message) {
        return new CustomerMessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getRole(),
                message.getMessage(),
                message.getCreatedAt());
    }
}
