package com.harmoni.pos.customer.domain.model;

import lombok.Getter;

import java.time.Instant;

/**
 * Customer domain model.
 */
@Getter
public class Customer {

    private Long id;
    private Integer brandId;
    private String name;
    private String phone;
    private String email;
    private Instant createdAt;
    private Instant updatedAt;

    public Customer(Long id, Integer brandId, String name, String phone, String email,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.brandId = brandId;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Customer create(String name, String phone, String email, Integer brandId) {
        return new Customer(null, brandId, name, phone, email, null, null);
    }

    public void updateProfile(String name, String phone, String email) {
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public boolean hasId(Long otherId) {
        return id != null && id.equals(otherId);
    }
}
