package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.CustomerMessageRole;

/**
 * Input data for adding a chat message to a session.
 */
public record AddCustomerMessageCommand(long sessionId, CustomerMessageRole role, String message) {
}
