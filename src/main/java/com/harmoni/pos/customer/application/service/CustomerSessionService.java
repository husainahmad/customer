package com.harmoni.pos.customer.application.service;

import com.harmoni.pos.customer.adapter.out.http.CustomerSessionApiClient;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Starts customer session in background after customer creation.
 * POST /api/v1/customer-sessions {customerId, source}
 * Fire-and-forget: navigation to /order is not blocked.
 */
@Service
public class CustomerSessionService {

    private static final Logger log = LoggerFactory.getLogger(CustomerSessionService.class);

    private final CustomerSessionApiClient sessionApiClient;
    private final String source;
    private final Map<String, CustomerSessionResponse> bySessionId = new ConcurrentHashMap<>();
    private final Map<Long, CustomerSessionResponse> byCustomerId = new ConcurrentHashMap<>();

    public CustomerSessionService(
            CustomerSessionApiClient sessionApiClient,
            @Value("${customer.api.source:AI_ORDER}") String source
    ) {
        this.sessionApiClient = sessionApiClient;
        this.source = source;
    }

    /**
     * Async fire-and-forget session start. Returns the future for testing/monitoring.
     *
     * @param customerId customer to open a session for
     */
    @Async
    public CompletableFuture<CustomerSessionResponse> startSessionAsync(Long customerId) {
        return CompletableFuture.supplyAsync(() -> startSession(customerId));
    }

    /** Starts a session for a customer without linking a Vaadin session id. */
    public CustomerSessionResponse startSession(Long customerId) {
        return startSession(customerId, null);
    }

    /**
     * Starts a customer session at the customer service and caches it by both the
     * customer id and the Vaadin session id.
     *
     * @param customerId the customer to open a session for
     * @param sessionId  the Vaadin session id to link (may be null)
     * @return the created session, or null when creation failed
     */
    public CustomerSessionResponse startSession(Long customerId, String sessionId) {
        if (customerId == null) {
            log.warn("startSession skipped: customerId is null");
            return null;
        }
        try {
            log.debug("Starting customer session customerId={}, source={}, sessionId={}", customerId, source, sessionId);
            CustomerSessionResponse resp = sessionApiClient.createSession(customerId, source);
            if (resp != null) {
                byCustomerId.put(customerId, resp);
                if (sessionId != null) {
                    bySessionId.put(sessionId, resp);
                }
            }
            log.info("Customer session started id={} customerId={} sessionId={}", resp != null ? resp.id() : null, customerId, sessionId);
            return resp;
        } catch (Exception ex) {
            // Background failure should not block ordering flow; log only
            log.error("Background customer session failed customerId={}: {}", customerId, ex.getMessage(), ex);
            return null;
        }
    }

    /** Sync wrapper for callers that want fire-and-forget behavior outside of the {@code @Async} proxy. */
    public void startSessionInBackground(Long customerId) {
        startSessionInBackground(customerId, null);
    }

    /** Fire-and-forget session start on a shared thread pool, logging failures instead of throwing. */
    public void startSessionInBackground(Long customerId, String sessionId) {
        CompletableFuture.runAsync(() -> startSession(customerId, sessionId))
                .exceptionally(ex -> {
                    log.error("Async session error: {}", ex.getMessage(), ex);
                    return null;
                });
    }

    /** Finds a session cached against a Vaadin session id. */
    public Optional<CustomerSessionResponse> getBySessionId(String sessionId) {
        return Optional.ofNullable(bySessionId.get(sessionId));
    }

    /** Finds a session cached against a customer id. */
    public Optional<CustomerSessionResponse> getByCustomerId(Long customerId) {
        return Optional.ofNullable(byCustomerId.get(customerId));
    }
}
