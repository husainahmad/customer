package com.harmoni.pos.customer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Retry configuration for AI provider calls — exponential backoff on 429 rate limits.
 * <p>
 * Bind prefix: {@code spring.retry} (see application.yaml)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.retry")
public class RetryProperties {

    /** Maximum number of retry attempts */
    private int maxAttempts = 5;

    /** Initial interval between retries */
    private Duration initialInterval = Duration.ofSeconds(3);

    /** Maximum interval cap for exponential backoff */
    private Duration maxInterval = Duration.ofSeconds(30);

    /** Backoff multiplier */
    private double multiplier = 2.0;

    /** Jitter factor to prevent thundering herd (0–1) */
    private double jitterFactor = 0.2;
}
