package com.harmoni.pos.customer.ai.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.ai.tool.JsonNodeExtractor;
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
import java.util.stream.Collectors;

/**
 * Ingests menu:8082 products/categories into VectorStore on startup.
 * ai-order never calls menu directly — customer is the RAG hub.
 * <p>
 * Everything comes from the Menu Service at runtime: categories via
 * {@code category-by-brand}, then products via {@code product-by-category-paged}
 * for every category. Endpoint paths come from application.yaml via
 * {@link MenuServiceProperties}; nothing is hardcoded in Java.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MenuRagIngestionService {

    private static final int PRODUCTS_PAGE_SIZE = 100;

    private final VectorStore vectorStore;
    private final RestClient menuRestClient;
    private final MenuServiceProperties menuProps;
    private final AiProperties aiProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @EventListener(ApplicationReadyEvent.class)
    public void ingest() {
        if (!aiProps.isRagEnabled()) {
            log.info("RAG disabled (harmoni.ai.rag.enabled=false) — skipping ingest");
            return;
        }
        try {
            long brandId = aiProps.getBrandId();
            log.info("RAG ingest start — fetching menu from 8082 (brand {})", brandId);

            List<Document> docs = new ArrayList<>();
            List<JsonNodeExtractor> categories = fetchCategories(brandId);
            for (JsonNodeExtractor category : categories) {
                Long categoryId = category.productId();
                String categoryName = category.name();
                if (categoryId == null || categoryName == null) continue;

                docs.add(new Document(categoryDescription(brandId, categoryName, categoryId, category.description()),
                        Map.of("type", "category", "brandId", brandId)));

                List<JsonNodeExtractor> products = fetchProducts(categoryId, brandId);
                if (!products.isEmpty()) {
                    docs.add(new Document(productDescription(categoryName, categoryId, products),
                            Map.of("type", "product", "categoryId", categoryId)));
                }
            }

            if (docs.isEmpty()) {
                log.warn("RAG no docs from Menu Service — skipping VectorStore ingest (strict: no hardcoded fallback)");
                return;
            }

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
        } catch (Exception e) {
            log.error("RAG ingest failed: {}", e.getMessage(), e);
        }
    }

    /** Builds a compact text summary of one category, e.g. "Categories for brand 1: Coffee (13) — include Hot and Ice". */
    private String categoryDescription(long brandId, String name, long categoryId, String description) {
        if (description == null || description.isBlank()) {
            return "Categories for brand %d: %s (%d)".formatted(brandId, name, categoryId);
        }
        return "Categories for brand %d: %s (%d) — %s".formatted(brandId, name, categoryId, description);
    }

    /** Builds a compact text summary of a category's products, e.g. "Products in Coffee (13): Latte (60), ... — total 21 products". */
    private String productDescription(String categoryName, long categoryId, List<JsonNodeExtractor> products) {
        String names = products.stream()
                .filter(p -> p.name() != null && p.productId() != null)
                .map(p -> "%s (%d)".formatted(p.name(), p.productId()))
                .collect(Collectors.joining(", "));
        return "Products in %s category %d: %s — total %d products"
                .formatted(categoryName, categoryId, names, products.size());
    }

    /** Fetches all categories for a brand from the Menu Service; empty on failure. */
    private List<JsonNodeExtractor> fetchCategories(long brandId) {
        try {
            String endpoint = menuProps.getEndpoints().getCategoryByBrand();
            String json = menuRestClient.get()
                    .uri(endpoint, brandId)
                    .retrieve().body(String.class);
            if (json == null) return List.of();
            return JsonNodeExtractor.extractAllCategories(json, objectMapper);
        } catch (Exception e) {
            log.warn("RAG categories fetch failed brandId={}: {}", brandId, e.getMessage());
            return List.of();
        }
    }

    /** Fetches products of a category from the Menu Service; empty on failure. */
    private List<JsonNodeExtractor> fetchProducts(long categoryId, long brandId) {
        try {
            String endpoint = menuProps.getEndpoints().getProductByCategoryPaged();
            String json = menuRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path(endpoint)
                            .queryParam("size", PRODUCTS_PAGE_SIZE)
                            .build(categoryId, brandId))
                    .retrieve().body(String.class);
            if (json == null) return List.of();
            List<JsonNodeExtractor> products = JsonNodeExtractor.extractAllProducts(json, objectMapper);
            log.info("RAG fetched products categoryId={} count={}", categoryId, products.size());
            return products;
        } catch (Exception e) {
            log.warn("RAG products fetch failed categoryId={}: {}", categoryId, e.getMessage());
            return List.of();
        }
    }
}