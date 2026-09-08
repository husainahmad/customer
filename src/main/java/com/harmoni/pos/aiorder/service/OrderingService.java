package com.harmoni.pos.aiorder.service;

import com.harmoni.pos.aiorder.dto.ChatResponse;

/**
 * Natural-language ordering facade used by the Vaadin UI.
 * <p>
 * Every call resolves the customer session and proxies to the customer service;
 * ai-order never talks to the Menu or Order service directly.
 */
public interface OrderingService {

    /** Sends a chat message to the AI via the customer service. */
    ChatResponse sendMessage(String sessionId, String message);

    /** Streaming variant — emits incremental LLM tokens for the Vaadin typewriter effect. */
    reactor.core.publisher.Flux<String> streamMessage(String sessionId, String message);
}