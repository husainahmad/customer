package com.harmoni.pos.customer.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for opening a customer session.
 */
public record CreateCustomerSessionRequest(
        @Positive
        Long customerId,

        @NotBlank(message = "source is required")
        @Size(max = 30)
        String source
) {
}
