package com.harmoni.pos.customer.application.port.in;

/**
 * Input data for updating a customer's profile.
 */
public record UpdateCustomerCommand(String name, String phone, String email) {
}
