package com.harmoni.pos.customer.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for registering a customer at the customer service.
 *
 * @param name   customer's name (required, max 100)
 * @param phone  phone, digits plus + - ( ) and spaces (optional)
 * @param email  email address (optional)
 */
public record CreateCustomerRequest(
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
