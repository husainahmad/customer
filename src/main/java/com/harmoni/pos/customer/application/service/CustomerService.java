package com.harmoni.pos.customer.application.service;

import com.harmoni.pos.customer.adapter.in.web.dto.CreateCustomerRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerResponse;

import java.util.Optional;

/**
 * Customer registration and lookups for the ai-order UI.
 * <p>
 * Implementations may proxy the write to the customer service and cache the
 * result per Vaadin session.
 */
public interface CustomerService {

    /**
     * Posts to the customer service and caches the returned {@link CustomerResponse}.
     *
     * @return server CustomerResponse (id, createdAt, updatedAt)
     */
    CustomerResponse saveCustomer(String sessionId, CreateCustomerRequest request);

    /**
     * Login or register by phone: an existing phone logs the customer in,
     * otherwise a new customer is created. Result cached per Vaadin sessionId.
     */
    CustomerResponse loginOrRegister(String sessionId, CreateCustomerRequest request);

    /** Looks up an existing customer by exact phone. */
    Optional<CustomerResponse> findByPhone(String phone);

    /** Returns the cached server-side customer for a session, if any. */
    Optional<CustomerResponse> getCustomerResponse(String sessionId);

    /** Whether the session has completed the customer gate. */
    boolean exists(String sessionId);

    /** Drops all cached data for a session. */
    void remove(String sessionId);
}
