package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.Customer;

/**
 * Searches customers by keyword with paging.
 */
public interface SearchCustomerUseCase {

    PageResult<Customer> search(SearchCustomersQuery query);
}
