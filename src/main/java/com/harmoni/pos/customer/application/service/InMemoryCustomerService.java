package com.harmoni.pos.customer.application.service;

import com.harmoni.pos.customer.adapter.in.web.dto.CreateCustomerRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerResponse;
import com.harmoni.pos.customer.adapter.out.http.CustomerApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * In-memory {@link CustomerService} that proxies registration to the customer
 * service (customer:8084) and caches the result per Vaadin session for the
 * duration of the browser session.
 */
@Service
public class InMemoryCustomerService implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCustomerService.class);

    private final Map<String, CustomerResponse> responseStore = new ConcurrentHashMap<>();
    private final CustomerApiClient apiClient;

    /** @param apiClient outbound adapter to the customer service */
    public InMemoryCustomerService(CustomerApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * Registers a new customer at the customer service and caches the response.
     * Customer sessions are intentionally NOT started here — they begin on the
     * first chat message (background) per requirement.
     *
     * @param sessionId the Vaadin session id
     * @param request   normalized customer details
     * @return the server-side {@link CustomerResponse}
     */
    @Override
    public CustomerResponse saveCustomer(String sessionId, CreateCustomerRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        // Normalize
        String normalizedPhone = request.phone() == null ? null : request.phone().trim();
        String normalizedName = request.name() == null ? null : request.name().trim();
        String normalizedEmail = request.email() == null ? null : request.email().trim();
        if (normalizedEmail != null && normalizedEmail.isBlank()) {
            normalizedEmail = null;
        }
        if (normalizedPhone != null && normalizedPhone.isBlank()) {
            normalizedPhone = null;
        }
        CreateCustomerRequest normalized = new CreateCustomerRequest(
                normalizedName,
                normalizedPhone,
                normalizedEmail
        );

        // Submit to remote: POST http://localhost:8084/api/v1/customers -> CustomerResponse
        // No customer-sessions call here — per requirement sessions are started
        // only on first chat message (background).
        CustomerResponse response = apiClient.createCustomer(normalized);

        // Cache locally for session continuity
        responseStore.put(sessionId, response);
        log.debug("Cached customer sessionId={} id={} name={}", sessionId, response.id(), response.name());
        return response;
    }

    /**
     * Logs in an existing customer by exact phone, or registers a new one when
     * the phone is unknown. In the login case the server's stored data wins and
     * the request's fields only fill blanks.
     *
     * @param sessionId the Vaadin session id
     * @param request   phone is required; name is required only when registering
     * @return the resolved customer
     */
    @Override
    public CustomerResponse loginOrRegister(String sessionId, CreateCustomerRequest request) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        String phone = request.phone() == null ? null : request.phone().trim();
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("phone is required for login/register");
        }
        // Try login by exact phone
        var existing = apiClient.findByPhone(phone);
        if (existing.isPresent()) {
            CustomerResponse cr = existing.get();
            // Cache with existing data (preserve server name if request name blank)
            String name = request.name() != null && !request.name().isBlank() ? request.name().trim() : cr.name();
            String email = request.email() != null && !request.email().isBlank() ? request.email().trim() : cr.email();
            CreateCustomerRequest cached = new CreateCustomerRequest(name, cr.phone(), email);
            responseStore.put(sessionId, cr);
            log.info("Login existing phone {} -> id={} sessionId={}", phone, cr.id(), sessionId);
            return cr;
        }
        // Not found -> register new (requires name)
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Nama diperlukan untuk pendaftaran. Nomor ini belum terdaftar.");
        }
        return saveCustomer(sessionId, request);
    }

    /** Looks up an existing customer by exact phone at the customer service. */
    @Override
    public Optional<CustomerResponse> findByPhone(String phone) {
        return apiClient.findByPhone(phone);
    }

    /** Returns the cached server-side customer for a session, if any. */
    @Override
    public Optional<CustomerResponse> getCustomerResponse(String sessionId) {
        return Optional.ofNullable(responseStore.get(sessionId));
    }

    /** Whether the session has completed the customer gate (server response cached). */
    @Override
    public boolean exists(String sessionId) {
        return sessionId != null && responseStore.containsKey(sessionId);
    }

    /** Drops all cached data for a session. */
    @Override
    public void remove(String sessionId) {
        responseStore.remove(sessionId);
    }
}
