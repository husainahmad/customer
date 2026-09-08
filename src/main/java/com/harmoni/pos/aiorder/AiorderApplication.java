package com.harmoni.pos.aiorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI ordering BFF on :8085.
 * <p>
 * Scans both the {@code com.harmoni.pos.aiorder} UI and the
 * {@code com.harmoni.pos.customer} adapter packages; all menu/cart/chat data is
 * proxied to the customer service, never fetched directly.
 */
@SpringBootApplication(scanBasePackages = {"com.harmoni.pos.aiorder", "com.harmoni.pos.customer"})
@EnableAsync
public class AiorderApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiorderApplication.class, args);
	}

}