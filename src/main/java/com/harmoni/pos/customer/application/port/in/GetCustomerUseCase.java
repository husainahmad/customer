package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.Customer;

/**
 * Reads a customer by id.
 */
public interface GetCustomerUseCase {

    Customer getCustomer(long id);
}
