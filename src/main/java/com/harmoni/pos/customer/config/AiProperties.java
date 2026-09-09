package com.harmoni.pos.customer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI-related configuration — replaces scattered @Value("${harmoni.ai.*}").
 * <p>
 * Bind prefix: {@code harmoni.ai}
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "harmoni.ai")
public class AiProperties {

    /** Whether AI chat is enabled globally */
    private boolean enabled = true;

    /** Whether RAG (vector store retrieval) is enabled — requires embeddings model */
    private boolean ragEnabled = false;

    /** Brand id used when ingesting the menu into the vector store (RAG) */
    private long brandId = 1;

    /** System prompt for the AI assistant */
    private String systemPrompt = "You are Harmoni AI, a friendly restaurant ordering assistant.\n\nHelp customers find menu items, check prices, manage their cart, and confirm orders.";
}