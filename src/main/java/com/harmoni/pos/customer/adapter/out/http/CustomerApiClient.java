package com.harmoni.pos.customer.adapter.out.http;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.adapter.in.web.dto.CreateCustomerRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Outbound adapter: POST to customer service at http://localhost:8084/api/v1/customers
 * Spec: consumes CreateCustomerRequest (name, phone, email) as JSON.
 */
@Component
public class CustomerApiClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerApiClient.class);

    private final RestClient restClient;
    private final String customersPath;
    private final int timeoutMs;

    /**
     * @param builder       Spring {@link RestClient} builder
     * @param baseUrl       customer service base URL ({@code customer.api.base-url})
     * @param customersPath customers REST path ({@code customer.api.customers-path})
     * @param timeoutMs     connect/read timeout ({@code customer.api.timeout-ms})
     */
    public CustomerApiClient(
            RestClient.Builder builder,
            @Value("${customer.api.base-url:http://localhost:8084}") String baseUrl,
            @Value("${customer.api.customers-path:/api/v1/customers}") String customersPath,
            @Value("${customer.api.timeout-ms:5000}") int timeoutMs
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
        this.customersPath = customersPath;
        this.timeoutMs = timeoutMs;
        log.info("CustomerApiClient configured baseUrl={}, path={}, timeout={}ms", baseUrl, customersPath, timeoutMs);
    }

    /**
     * Submits customer to remote service and parses CustomerResponse.
     * @return CustomerResponse with id, name, phone, email, createdAt, updatedAt
     * @throws CustomerApiException on failure (4xx/5xx / network)
     */
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        try {
            log.debug("POST {} body={}", customersPath, request);
            CustomerResponse response = restClient.post()
                    .uri(customersPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CustomerResponse.class);
            if (response == null) {
                throw new CustomerApiException("Empty response from customer service", null);
            }
            log.info("Customer created remote id={}, name={}, phone={}", response.id(), response.name(), response.phone());
            return response;
        } catch (RestClientException ex) {
            log.error("Failed to create customer remote: {}", ex.getMessage(), ex);
            throw new CustomerApiException("Gagal menyimpan customer ke server: " + ex.getMessage(), ex);
        }
    }

    /**
     * Finds customer by exact phone via GET /api/v1/customers?search=phone.
     * Returns empty if not found. Filters exact match client-side because search is fuzzy.
     */
    public Optional<CustomerResponse> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        String trimmed = phone.trim();
        try {
            String json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(customersPath)
                            .queryParam("search", trimmed)
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (json == null || json.isBlank()) return Optional.empty();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode content = root.path("content");
            if (!content.isArray() || content.isEmpty()) return Optional.empty();
            for (JsonNode node : content) {
                String foundPhone = node.path("phone").asString(null);
                if (foundPhone != null && normalizePhone(foundPhone).equals(normalizePhone(trimmed))) {
                    // Manual mapping to avoid JavaTimeModule dependency (Instant parsed as string)
                    Long id = node.path("id").asLong();
                    String name = node.path("name").asString(null);
                    String email = node.path("email").asString(null);
                    java.time.Instant createdAt = null;
                    java.time.Instant updatedAt = null;
                    try {
                        String ca = node.path("createdAt").asString(null);
                        if (ca != null) createdAt = java.time.Instant.parse(ca);
                        String ua = node.path("updatedAt").asString(null);
                        if (ua != null) updatedAt = java.time.Instant.parse(ua);
                    } catch (Exception ignored) {}
                    CustomerResponse cr = new CustomerResponse(id, name, foundPhone, email, createdAt, updatedAt);
                    log.info("Found existing customer by phone {} -> id={} name={}", trimmed, cr.id(), cr.name());
                    return Optional.of(cr);
                }
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("findByPhone failed phone={}: {}", trimmed, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Strips spaces, dashes and parentheses so phone lookups match across formatting. */
    private static String normalizePhone(String p) {
        if (p == null) return "";
        return p.replaceAll("[\\s\\-()]", "").trim();
    }

    /** Thrown when the customer service cannot be reached or rejects a request. */
    public static class CustomerApiException extends RuntimeException {
        public CustomerApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
