package com.harmoni.pos.customer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * HTTP client beans shared by every customer-service adapter.
 * <p>
 * Exposes a {@link RestClient.Builder} for blocking calls and a
 * {@link WebClient.Builder} (plus a pre-built customer {@link WebClient}) for
 * the reactive SSE chat stream.
 */
@Configuration
public class RestClientConfig {

    /** Base {@link RestClient} builder; each adapter adds its own base URL/timeouts. */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /** Base {@link WebClient} builder for reactive calls. */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /** Reactive client pointed at the customer service base URL. */
    @Bean
    public WebClient customerWebClient(WebClient.Builder builder,
                                       @Value("${customer.api.base-url:http://localhost:8084}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
