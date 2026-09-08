package com.harmoni.pos.customer.adapter.out.http;

import com.harmoni.pos.customer.adapter.in.web.dto.CreateCustomerSessionRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerSessionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Outbound adapter: POST http://localhost:8084/api/v1/customer-sessions
 * Body: CreateCustomerSessionRequest(customerId, source)
 * Called in background after customer creation.
 */
@Component
public class CustomerSessionApiClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerSessionApiClient.class);

    private final RestClient restClient;
    private final String sessionsPath;

    /**
     * @param builder      Spring {@link RestClient} builder
     * @param baseUrl      customer service base URL ({@code customer.api.base-url})
     * @param sessionsPath customer sessions REST path ({@code customer.api.sessions-path})
     */
    public CustomerSessionApiClient(
            RestClient.Builder builder,
            @Value("${customer.api.base-url:http://localhost:8084}") String baseUrl,
            @Value("${customer.api.sessions-path:/api/v1/customer-sessions}") String sessionsPath
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.sessionsPath = sessionsPath;
        log.info("CustomerSessionApiClient configured baseUrl={}, path={}", baseUrl, sessionsPath);
    }

    /**
     * Fire-and-forget session start. Returns response or null on failure.
     */
    public CustomerSessionResponse createSession(Long customerId, String source) {
        CreateCustomerSessionRequest req = new CreateCustomerSessionRequest(customerId, source);
        try {
            log.debug("POST {} body={}", sessionsPath, req);
            CustomerSessionResponse resp = restClient.post()
                    .uri(sessionsPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(CustomerSessionResponse.class);
            log.info("Customer session created customerId={}, source={}, sessionId={}", customerId, source, resp != null ? resp.id() : null);
            return resp;
        } catch (RestClientException ex) {
            log.error("Failed to create customer session customerId={} source={}: {}", customerId, source, ex.getMessage(), ex);
            throw new CustomerApiClient.CustomerApiException("Gagal membuat customer session: " + ex.getMessage(), ex);
        }
    }
}
