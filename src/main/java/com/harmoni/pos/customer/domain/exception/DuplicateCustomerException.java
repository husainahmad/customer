package com.harmoni.pos.customer.domain.exception;

/**
 * Thrown when a customer registration would create a duplicate (e.g. the phone is already in use).
 */
public class DuplicateCustomerException extends RuntimeException {

    public DuplicateCustomerException(String message) {
        super(message);
    }
}
