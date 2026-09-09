package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.CustomerSession;

/**
 * Reads a customer session by id.
 */
public interface GetCustomerSessionUseCase {

    CustomerSession getSession(long id);
}
