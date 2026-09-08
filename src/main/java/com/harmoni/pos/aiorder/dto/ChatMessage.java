package com.harmoni.pos.aiorder.dto;

/**
 * A single chat entry sent to or received from the customer service.
 *
 * @param role     who said it ({@code USER} or {@code ASSISTANT})
 * @param message  the text content
 */
public record ChatMessage(
    Role role,
    String message
) {}