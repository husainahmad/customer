package com.harmoni.pos.customer.application.port.in;

/**
 * Query parameters for the customer search.
 */
public record SearchCustomersQuery(String search, int page, int size) {
}
