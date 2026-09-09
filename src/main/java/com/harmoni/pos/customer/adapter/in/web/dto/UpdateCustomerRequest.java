package com.harmoni.pos.customer.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating a customer's profile.
 */
public record UpdateCustomerRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 30)
        @Pattern(regexp = "^[0-9+\\-()\\s]{5,30}$", message = "Invalid phone format")
        String phone,

        @Size(max = 150)
        @Email
        String email
) {
}
