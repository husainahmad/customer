package com.harmoni.pos.customer.domain.exception;

/**
 * Thrown when a customer session cannot be found for a given id.
 */
public class CustomerSessionNotFoundException extends RuntimeException {

    public CustomerSessionNotFoundException(long id) {
        super("Customer session not found: " + id);
    }
}
