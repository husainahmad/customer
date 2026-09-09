package com.harmoni.pos.customer.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body that adds a raw chat message (role + content) to a session.
 */
public record CreateCustomerMessageRequest(
        @NotBlank
        @Size(max = 20)
        String role,

        @NotBlank
        @Size(max = 5000)
        String message
) {
}
