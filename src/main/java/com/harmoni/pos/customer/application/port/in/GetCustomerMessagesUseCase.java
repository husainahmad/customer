package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.CustomerMessage;

/**
 * Reads a session's message history, paginated.
 */
public interface GetCustomerMessagesUseCase {

    PageResult<CustomerMessage> getMessages(long sessionId, int page, int size);
}
