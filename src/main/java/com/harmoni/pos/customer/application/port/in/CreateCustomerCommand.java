package com.harmoni.pos.customer.application.port.in;

/**
 * Input data for creating a customer.
 */
public record CreateCustomerCommand(String name, String phone, String email) {
}
