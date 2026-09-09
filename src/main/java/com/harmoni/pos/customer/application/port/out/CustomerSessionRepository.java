package com.harmoni.pos.customer.application.port.out;

import com.harmoni.pos.customer.domain.model.CustomerSession;

import java.util.Optional;

/**
 * Port out for persisting and querying customer sessions.
 */
public interface CustomerSessionRepository {

    CustomerSession save(CustomerSession session);

    Optional<CustomerSession> findById(long id);

    CustomerSession update(CustomerSession session);
}
