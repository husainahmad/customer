package com.harmoni.pos.customer.domain.exception;

/**
 * Thrown when a chat message is malformed (missing role, blank content, or unknown role).
 */
public class InvalidCustomerMessageException extends RuntimeException {

    public InvalidCustomerMessageException(String message) {
        super(message);
    }
}
