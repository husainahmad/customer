package com.harmoni.pos.customer.application.port.in;

/**
 * Query parameters for the customer search.
 */
public record SearchCustomersQuery(String search, Integer brandId, int page, int size) {
}
