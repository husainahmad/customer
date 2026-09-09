package com.harmoni.pos.customer.domain.model;

import com.harmoni.pos.customer.domain.exception.InvalidCustomerMessageException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

/**
 * A single chat message exchanged within a customer session.
 * <p>
 * Immutable. Build validated instances through {@link #create(long, CustomerMessageRole, String)},
 * which trims the content and rejects blank messages or a missing role.
 */
@Getter
@RequiredArgsConstructor
public class CustomerMessage {

    private final Long id;
    private final long sessionId;
    private final CustomerMessageRole role;
    private final String message;
    private final Instant createdAt;

    /**
     * Creates a chat message without an id and timestamp (ready to be persisted).
     *
     * @param sessionId the owning customer session
     * @param role      the message role (required)
     * @param message   the message content (required, non-blank; trimmed)
     * @return a new message
     * @throws InvalidCustomerMessageException if {@code role} is null or {@code message} is blank
     */
    public static CustomerMessage create(long sessionId, CustomerMessageRole role, String message) {
        if (role == null) {
            throw new InvalidCustomerMessageException("Message role is required");
        }
        if (message == null || message.isBlank()) {
            throw new InvalidCustomerMessageException("Message content is required");
        }
        return new CustomerMessage(null, sessionId, role, message.trim(), null);
    }
}
