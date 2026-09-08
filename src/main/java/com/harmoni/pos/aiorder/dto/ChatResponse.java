package com.harmoni.pos.aiorder.dto;

/**
 * A single AI answer from the customer service.
 *
 * @param message the LLM's reply text
 */
public record ChatResponse(String message) {

    /** Builds a chat response. */
    public static ChatResponse of(String message) {
        return new ChatResponse(message);
    }
}