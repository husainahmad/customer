package com.harmoni.pos.customer.domain.model;

import lombok.Getter;

/**
 * Role of a chat message within a customer session.
 */
@Getter
public enum CustomerMessageRole {

    USER,
    ASSISTANT;

    public static CustomerMessageRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return valueOf(raw.trim().toUpperCase());
    }
}
