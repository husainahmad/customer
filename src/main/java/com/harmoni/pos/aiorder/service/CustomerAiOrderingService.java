package com.harmoni.pos.aiorder.service;

import com.harmoni.pos.aiorder.dto.ChatResponse;
import com.harmoni.pos.customer.adapter.out.http.CustomerAiChatApiClient;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerMessageResponse;
import com.harmoni.pos.customer.application.service.CustomerSessionService;
import com.harmoni.pos.customer.application.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Natural AI ordering bridge between Vaadin and the customer backend.
 * <p>
 * The customer service at 8084 hosts the OpenAI-compatible LLM and is the single
 * source of truth for chat, cart and order data. This BFF keeps ai-order thin:
 * it resolves the customer session and forwards prompts, never calling Menu or
 * Order directly.
 */
@Service
@RequiredArgsConstructor
public class CustomerAiOrderingService implements OrderingService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAiOrderingService.class);

    private final CustomerAiChatApiClient aiChatClient;
    private final CustomerSessionService sessionService;
    private final CustomerService customerService;

    /**
     * Sends a chat message to the AI via the customer service.
     */
    @Override
    public ChatResponse sendMessage(String vaadinSessionId, String message) {
        Long customerSessionId = resolveCustomerSessionId(vaadinSessionId);
        if (customerSessionId == null) {
            throw new IllegalStateException("Customer session not found for vaadinSessionId=" + vaadinSessionId + ". Please complete the customer gate first.");
        }
        CustomerMessageResponse aiMsg = aiChatClient.chat(customerSessionId, message);
        String text = aiMsg != null && aiMsg.message() != null ? aiMsg.message() : "Maaf, tidak ada respons AI.";
        return ChatResponse.of(text);
    }

    /**
     * Streams AI tokens incrementally for a smooth typewriter effect in Vaadin.
     * Each token is forwarded from the customer service's LLM stream.
     */
    @Override
    public Flux<String> streamMessage(String vaadinSessionId, String message) {
        Long customerSessionId = resolveCustomerSessionId(vaadinSessionId);
        if (customerSessionId == null) {
            return Flux.error(new IllegalStateException("Customer session not found for vaadinSessionId=" + vaadinSessionId));
        }
        return aiChatClient.chatStream(customerSessionId, message)
                .doOnSubscribe(s -> log.info("Stream subscribe vaadin={} customerSessionId={}", vaadinSessionId, customerSessionId))
                .doOnError(e -> log.error("Stream error vaadin={}: {}", vaadinSessionId, e.getMessage(), e));
    }

    private Long resolveCustomerSessionId(String vaadinSessionId) {
        var crOpt = customerService.getCustomerResponse(vaadinSessionId);
        if (crOpt.isEmpty()) return null;
        var cr = crOpt.get();
        var sess = sessionService.getBySessionId(vaadinSessionId)
                .or(() -> sessionService.getByCustomerId(cr.id()))
                .orElse(null);
        if (sess == null) {
            // lazy start if not exists
            sess = sessionService.startSession(cr.id(), vaadinSessionId);
        }
        return sess != null ? sess.id() : null;
    }
}