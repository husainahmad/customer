package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.Customer;

/**
 * Creates a customer.
 */
public interface CreateCustomerUseCase {

    Customer create(CreateCustomerCommand command);
}
