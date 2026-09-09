package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.Customer;

/**
 * Updates a customer's profile.
 */
public interface UpdateCustomerUseCase {

    Customer update(long id, UpdateCustomerCommand command);
}
