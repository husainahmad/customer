package com.harmoni.pos.customer.adapter.out.http;

import com.harmoni.pos.customer.adapter.in.web.dto.CustomerMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Calls customer service Ollama endpoint.
 * Ollama connection originates FROM customer service (port 8084),
 * NOT from ai-order. This client just forwards user prompt.
 *
 * POST http://localhost:8084/api/v1/customer-sessions/{sessionId}/chat
 *   body: { "message": "..." }  -> CustomerMessageResponse (ASSISTANT)
 */
@Component
public class CustomerAiChatApiClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerAiChatApiClient.class);

    private final RestClient restClient;
    private final WebClient webClient;
    private final String sessionsPath;

    /**
     * @param restBuilder     Spring {@link RestClient} builder for blocking calls
     * @param customerWebClient reactive {@link WebClient} for the SSE chat stream
     * @param baseUrl         customer service base URL ({@code customer.api.base-url})
     * @param sessionsPath    customer sessions REST path ({@code customer.api.sessions-path})
     */
    public CustomerAiChatApiClient(
            RestClient.Builder restBuilder,
            WebClient customerWebClient,
            @Value("${customer.api.base-url:http://localhost:8084}") String baseUrl,
            @Value("${customer.api.sessions-path:/api/v1/customer-sessions}") String sessionsPath
    ) {
        this.restClient = restBuilder.baseUrl(baseUrl).build();
        this.webClient = customerWebClient;
        this.sessionsPath = sessionsPath;
    }

    /**
     * Sends a prompt to the customer service's AI chat endpoint and returns the
     * assistant reply.
     *
     * @param customerSessionId customer session id
     * @param message           the customer's prompt
     * @return the assistant's reply (non-null body expected)
     */
    public CustomerMessageResponse chat(long customerSessionId, String message) {
        String uri = sessionsPath + "/" + customerSessionId + "/chat";
        Map<String, String> body = Map.of("message", message);
        try {
            log.debug("POST {} body={}", uri, body);
            CustomerMessageResponse resp = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(CustomerMessageResponse.class);
            log.info("AI chat response sessionId={} id={} len={}", customerSessionId, resp != null ? resp.id() : null, resp != null && resp.message() != null ? resp.message().length() : 0);
            return resp;
        } catch (RestClientException ex) {
            log.error("AI chat failed sessionId={}: {}", customerSessionId, ex.getMessage(), ex);
            throw new CustomerApiClient.CustomerApiException("AI chat failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Streaming via WebClient — subscribes to customer SSE stream.
     * POST /api/v1/customer-sessions/{id}/chat/stream  body {message}
     * <p>
     * Decodes each Server-Sent Event using the framework's SSE reader, so the
     * emitted {@link String} is the exact event payload (multi-line {@code data}
     * is reassembled with newlines per the SSE spec, and any {@code event}/id
     * framing is already stripped). The UI appends tokens as they arrive.
     */
    public Flux<String> chatStream(long customerSessionId, String message) {
        String uri = sessionsPath + "/" + customerSessionId + "/chat/stream";
        Map<String, String> body = Map.of("message", message);
        log.info("Stream start sessionId={} uri={}", customerSessionId, uri);
        return webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .mapNotNull(ServerSentEvent::data)
                .filter(data -> !data.isBlank() && !"[DONE]".equals(data))
                .doOnError(e -> log.error("Stream failed sessionId={}: {}", customerSessionId, e.getMessage(), e));
    }
}
