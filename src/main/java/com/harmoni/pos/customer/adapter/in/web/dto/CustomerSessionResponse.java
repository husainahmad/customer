package com.harmoni.pos.customer.adapter.in.web.dto;

import com.harmoni.pos.customer.domain.model.CustomerSession;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import com.harmoni.pos.customer.domain.model.CustomerSessionStatus;

import java.time.Instant;

/**
 * Response DTO for a customer session.
 */
public record CustomerSessionResponse(
        Long id,
        Long customerId,
        String sessionToken,
        CustomerSessionSource source,
        CustomerSessionStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static CustomerSessionResponse from(CustomerSession session) {
        return new CustomerSessionResponse(
                session.getId(),
                session.getCustomerId(),
                session.getSessionToken(),
                session.getSource(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt());
    }
}
