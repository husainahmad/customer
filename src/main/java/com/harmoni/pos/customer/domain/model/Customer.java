package com.harmoni.pos.customer.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

/**
 * Customer domain model.
 */
@Getter
@RequiredArgsConstructor
public class Customer {

    private final Long id;
    private final String name;
    private final String phone;
    private final String email;
    private final Instant createdAt;
    private final Instant updatedAt;

    public static Customer create(String name, String phone, String email) {
        return new Customer(null, name, phone, email, null, null);
    }

    public void updateProfile(String name, String phone, String email) {
        // fields are final, so this is a placeholder — in practice the service replaces via repository
    }

    public boolean hasId(Long otherId) {
        return id != null && id.equals(otherId);
    }
}
