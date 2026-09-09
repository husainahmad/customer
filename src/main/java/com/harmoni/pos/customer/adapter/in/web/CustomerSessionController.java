package com.harmoni.pos.customer.adapter.in.web;

import com.harmoni.pos.customer.adapter.in.web.dto.CreateCustomerMessageRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CreateCustomerSessionRequest;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerMessageResponse;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerSessionResponse;
import com.harmoni.pos.customer.adapter.in.web.dto.PageResponse;
import com.harmoni.pos.customer.application.port.in.AddCustomerMessageCommand;
import com.harmoni.pos.customer.application.port.in.AddCustomerMessageUseCase;
import com.harmoni.pos.customer.application.port.in.CloseCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionCommand;
import com.harmoni.pos.customer.application.port.in.CreateCustomerSessionUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerMessagesUseCase;
import com.harmoni.pos.customer.application.port.in.GetCustomerSessionUseCase;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerSessionException;
import com.harmoni.pos.customer.domain.model.CustomerMessage;
import com.harmoni.pos.customer.domain.model.CustomerMessageRole;
import com.harmoni.pos.customer.domain.model.CustomerSession;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Customer sessions and message history — each chat lives inside a session.
 * <p>
 * A session starts when a customer opens the app and ends when they close it
 * or the order is done. Use this alongside the AI chat endpoints for full history.
 */
@RestController
@RequestMapping("/api/v1/customer-sessions")
@RequiredArgsConstructor
@Tag(name = "Customer Sessions", description = "Create sessions and manage message history")
public class CustomerSessionController {

    private final CreateCustomerSessionUseCase createCustomerSessionUseCase;
    private final GetCustomerSessionUseCase getCustomerSessionUseCase;
    private final CloseCustomerSessionUseCase closeCustomerSessionUseCase;
    private final AddCustomerMessageUseCase addCustomerMessageUseCase;
    private final GetCustomerMessagesUseCase getCustomerMessagesUseCase;

    @Operation(summary = "Create a session", description = "Starts a new session for a customer. The source tells us where it came from (e.g. POS, AI).")
    @PostMapping
    public ResponseEntity<CustomerSessionResponse> create(
            @Valid @RequestBody CreateCustomerSessionRequest request) {
        CustomerSession session = createCustomerSessionUseCase.create(new CreateCustomerSessionCommand(
                request.customerId(), parseSource(request.source())));
        return ResponseEntity
                .created(URI.create("/api/v1/customer-sessions/" + session.getId()))
                .body(CustomerSessionResponse.from(session));
    }

    @Operation(summary = "Get session by id")
    @GetMapping("/{id}")
    public CustomerSessionResponse getById(@Parameter(description = "Session id") @PathVariable long id) {
        return CustomerSessionResponse.from(getCustomerSessionUseCase.getSession(id));
    }

    @Operation(summary = "Close a session", description = "Marks the session as closed — no more messages or cart changes allowed after this.")
    @PostMapping("/{id}/close")
    public CustomerSessionResponse close(@Parameter(description = "Session id") @PathVariable long id) {
        return CustomerSessionResponse.from(closeCustomerSessionUseCase.close(id));
    }

    @Operation(summary = "Add a message", description = "Manually add a message to the history — mostly for non-AI flows. For AI replies, use /chat instead.")
    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<CustomerMessageResponse> addMessage(
            @Parameter(description = "Session id") @PathVariable long sessionId,
            @Valid @RequestBody CreateCustomerMessageRequest request) {
        CustomerMessage message = addCustomerMessageUseCase.addMessage(new AddCustomerMessageCommand(
                sessionId, parseRole(request.role()), request.message()));
        return ResponseEntity
                .created(URI.create("/api/v1/customer-sessions/" + sessionId + "/messages"))
                .body(CustomerMessageResponse.from(message));
    }

    @Operation(summary = "List messages", description = "Returns the chat history for a session, paginated. Great for restoring a conversation on reconnect.")
    @GetMapping("/{sessionId}/messages")
    public PageResponse<CustomerMessageResponse> getMessages(
            @Parameter(description = "Session id") @PathVariable long sessionId,
            @Parameter(description = "Zero-based page") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(getCustomerMessagesUseCase.getMessages(sessionId, page, size),
                CustomerMessageResponse::from);
    }

    private static CustomerSessionSource parseSource(String raw) {
        try {
            return CustomerSessionSource.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidCustomerSessionException("Unknown session source: " + raw);
        }
    }

    private static CustomerMessageRole parseRole(String raw) {
        return CustomerMessageRole.from(raw);
    }
}
