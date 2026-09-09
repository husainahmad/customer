package com.harmoni.pos.customer.application.port.in;

import com.harmoni.pos.customer.domain.model.CustomerMessage;

/**
 * AI conversation use case with blocking, sync and streaming chat variants.
 */
public interface ChatWithAiUseCase {

    /**
     * Persist user message, call Ollama, persist AI response, return AI message.
     * Connection to Ollama originates from customer service (this use-case).
     */
    CustomerMessage chat(long sessionId, String userMessage);

    /**
     * Streaming variant not used in MVP; kept for future WebFlux.
     */
    String chatSync(long sessionId, String userMessage);

    /**
     * Streaming variant — persists USER immediately, streams ASSISTANT tokens via ChatClient.stream().
     * Persists ASSISTANT on completion. Used by ai-order for Vaadin incremental UI.
     */
    reactor.core.publisher.Flux<String> chatStream(long sessionId, String userMessage);
}
