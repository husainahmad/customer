package com.harmoni.pos.customer.adapter.in.web.dto;

import com.harmoni.pos.customer.domain.model.Customer;

import java.time.Instant;

/**
 * Response DTO for a customer.
 */
public record CustomerResponse(
        Long id,
        String name,
        String phone,
        String email,
        Instant createdAt,
        Instant updatedAt
) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
