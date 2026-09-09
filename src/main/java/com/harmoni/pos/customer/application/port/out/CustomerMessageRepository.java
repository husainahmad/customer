package com.harmoni.pos.customer.application.port.out;

import com.harmoni.pos.customer.domain.model.CustomerMessage;

import java.util.List;

/**
 * Port out for persisting and querying chat messages.
 */
public interface CustomerMessageRepository {

    CustomerMessage save(CustomerMessage message);

    List<CustomerMessage> findBySessionId(long sessionId, int offset, int limit);

    long countBySessionId(long sessionId);
}
