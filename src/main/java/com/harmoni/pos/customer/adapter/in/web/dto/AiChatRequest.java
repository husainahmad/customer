package com.harmoni.pos.customer.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the chat endpoint - the customer's message to the AI assistant.
 */
@Schema(description = "Message to the AI assistant")
public record AiChatRequest(
        @Schema(description = "What the customer wants to say", example = "lihat menu dong") @NotBlank(message = "message is required") String message
) {}
