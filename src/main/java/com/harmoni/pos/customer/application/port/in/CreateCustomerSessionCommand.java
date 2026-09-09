package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.CustomerSessionSource;

/**
 * Input data for opening a customer session.
 */
public record CreateCustomerSessionCommand(Long customerId, CustomerSessionSource source) {
}
