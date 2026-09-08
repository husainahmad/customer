package com.harmoni.pos.customer.adapter.in.web.dto;

import java.time.Instant;

/**
 * Mirrors the customer service's customer response:
 * {id, name, phone, email, createdAt, updatedAt}.
 */
public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String email,
        Instant createdAt,
        Instant updatedAt
) {
}
