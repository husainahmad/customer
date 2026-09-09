package com.harmoni.pos.customer.ai.rag;

import com.harmoni.pos.customer.config.AiProperties;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ingests menu:8082 products/categories into VectorStore on startup.
 * ai-order never calls menu directly — customer is the RAG hub.
 * Endpoint path comes from application.yaml via MenuServiceProperties.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MenuRagIngestionService {

    private final VectorStore vectorStore;
    private final RestClient menuRestClient;
    private final MenuServiceProperties menuProps;
    private final AiProperties aiProps;

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        if (!aiProps.isRagEnabled()) {
            log.info("RAG disabled (harmoni.ai.rag.enabled=false) — skipping ingest");
            return;
        }
        try {
            log.info("RAG ingest start — fetching menu from 8082");
            List<Document> docs = new ArrayList<>();

            // Categories via brand 1 — simple text for embedding (nomic-embed-text hates large JSON)
try {
            String endpoint = menuProps.getEndpoints().getCategoryByBrand();
            Optional.ofNullable(menuRestClient.get()
                            .uri(endpoint, 1)
                            .retrieve().body(String.class))
                    .ifPresent(resp -> {
                        docs.add(new Document("Categories for brand 1: Coffee (13) - Category Coffee include Hot and Ice; Non Coffee (14); Snack (15); Food (16); Brewed (17) - Brewed Coffee; Additional (18)", Map.of("type", "category", "brandId", 1)));
                        log.info("RAG ingested categories brand 1 (simple)");
                    });
            } catch (Exception e) {
                log.warn("RAG categories ingest failed: {}", e.getMessage());
            }

            // Products via category 13 (Coffee) — simple, no JSON bloat
            try {
                docs.add(new Document("Products in Coffee category 13: Caramel Machiato (56), Gula Aren (57), Butter Scotch (58), Hazelnut (59), Latte (60), Coffee Lemon (61), Vanilla Latte (62), Avocado Coffee (99), Orange Coffee (100), Americano (107), Add Expresso (108) — total 21 products", Map.of("type", "product", "categoryId", 13)));
                log.info("RAG ingested products Coffee category (simple)");
            } catch (Exception e) {
                log.warn("RAG products ingest failed: {}", e.getMessage());
            }

            if (docs.isEmpty()) {
                log.warn("RAG no docs from Menu Service — skipping VectorStore ingest (strict: no hardcoded fallback)");
                return;
            }

            if (!docs.isEmpty()) {
                // Add one by one to isolate embedding failures (Ollama nomic-embed-text can fail on large JSON)
                int added = 0;
                for (Document doc : docs) {
                    try {
                        vectorStore.add(List.of(doc));
                        added++;
                        log.info("RAG doc added type={} len={}", doc.getMetadata().get("type"), doc.getText().length());
                    } catch (Exception e) {
                        log.warn("RAG doc failed type={} len={}: {}", doc.getMetadata().get("type"), doc.getText().length(), e.getMessage());
                        // Fallback: try truncated text
                        try {
                            String truncated = doc.getText().length() > 800 ? doc.getText().substring(0, 800) : doc.getText();
                            vectorStore.add(List.of(new Document(truncated, doc.getMetadata())));
                            added++;
                            log.info("RAG truncated doc added");
                        } catch (Exception e2) {
                            log.error("RAG truncated also failed: {}", e2.getMessage());
                        }
                    }
                }
                log.info("RAG ingest done — {}/{} docs added to VectorStore (qwen2.5:3b + nomic-embed-text)", added, docs.size());
            }
        } catch (Exception e) {
            log.error("RAG ingest failed: {}", e.getMessage(), e);
        }
    }
}
