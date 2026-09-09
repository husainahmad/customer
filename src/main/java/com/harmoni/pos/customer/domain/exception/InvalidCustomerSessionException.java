package com.harmoni.pos.customer.domain.exception;

/**
 * Thrown when a customer session is used in an invalid way (unknown source,
 * closing an already-closed session, etc.).
 */
public class InvalidCustomerSessionException extends RuntimeException {

    public InvalidCustomerSessionException(String message) {
        super(message);
    }
}
