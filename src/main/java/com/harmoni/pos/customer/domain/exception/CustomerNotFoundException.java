package com.harmoni.pos.customer.domain.exception;

/**
 * Thrown when a customer cannot be found for a given id.
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(long id) {
        super("Customer not found: " + id);
    }
}
