package com.harmoni.pos.customer.adapter.in.web;

import com.harmoni.pos.customer.adapter.in.web.dto.AiChatRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerMessageResponse;
import com.harmoni.pos.customer.application.port.in.ChatWithAiUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Chat with the AI assistant — the frontend only needs to talk to customer:8084.
 * <p>
 * The AI lives here (Spring AI + Menu/Cart/Order tools), so ai-order just sends
 * the customer's message and gets back an assistant reply. Works both as a normal
 * request/response and as a streaming SSE feed for a nicer typing effect.
 */
@RestController
@RequestMapping("/api/v1/customer-sessions")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "Talk to the Harmoni AI assistant. Handles menu lookups, cart and checkout via tools behind the scenes.")
public class CustomerAiChatController {

    private final ChatWithAiUseCase chatWithAiUseCase;

    @Operation(summary = "Chat with AI", description = "Send a message and get the assistant's reply. Saves both the user message and the assistant response in the session history.")
    @ApiResponse(responseCode = "200", description = "Assistant reply saved and returned")
    @ApiResponse(responseCode = "404", description = "Session not found or closed")
    @PostMapping("/{sessionId}/chat")
    public ResponseEntity<CustomerMessageResponse> chat(
            @Parameter(description = "Customer session id", example = "42") @PathVariable long sessionId,
            @Valid @RequestBody AiChatRequest request) {
        var assistantMsg = chatWithAiUseCase.chat(sessionId, request.message());
        return ResponseEntity.ok(CustomerMessageResponse.from(assistantMsg));
    }

    @Operation(summary = "Chat with AI (alias)", description = "Same as POST /chat — kept so existing ai-order clients using /messages/ai keep working.")
    @PostMapping("/{sessionId}/messages/ai")
    public ResponseEntity<CustomerMessageResponse> chatAlias(
            @Parameter(description = "Customer session id") @PathVariable long sessionId,
            @Valid @RequestBody AiChatRequest request) {
        return chat(sessionId, request);
    }

    @Operation(summary = "Chat with AI — streaming", description = "Streams the assistant's reply as Server-Sent Events. The frontend can render tokens incrementally via UI.access() in Vaadin.")
    @ApiResponse(responseCode = "200", description = "SSE stream of assistant tokens")
    @PostMapping(value = "/{sessionId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @Parameter(description = "Customer session id") @PathVariable long sessionId,
            @Valid @RequestBody AiChatRequest request) {
        return chatWithAiUseCase.chatStream(sessionId, request.message());
    }

    @Operation(summary = "Chat streaming (alias)", description = "Same as POST /chat/stream — alias for /messages/ai/stream.")
    @PostMapping(value = "/{sessionId}/messages/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamAlias(
            @Parameter(description = "Customer session id") @PathVariable long sessionId,
            @Valid @RequestBody AiChatRequest request) {
        return chatStream(sessionId, request);
    }
}
