package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.CustomerSession;

/**
 * Opens a customer session.
 */
public interface CreateCustomerSessionUseCase {

    CustomerSession create(CreateCustomerSessionCommand command);
}
