package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.CustomerMessage;

/**
 * Adds a chat message to a session.
 */
public interface AddCustomerMessageUseCase {

    CustomerMessage addMessage(AddCustomerMessageCommand command);
}
