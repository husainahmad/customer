package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.CustomerSession;

/**
 * Closes a customer session.
 */
public interface CloseCustomerSessionUseCase {

    CustomerSession close(long sessionId);
}
