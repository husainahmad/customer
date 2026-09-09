package com.harmoni.pos.customer.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.ai.tool.dto.ProductToolResponse;
import com.harmoni.pos.customer.ai.tool.dto.SkuToolResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Centralized JSON extraction logic for Menu Service responses.
 * <p>
 * Handles all supported response formats:
 * - Root array: `[{...}]`
 * - Normal response: `{"data": [{...}]}`
 * - Nested response: `{"data": {"data": [{...}]}}`
 * <p>
 * Extracts productId, skuId, name, description, price, and SKU information
 * directly from the Menu Service payload.
 */
public final class JsonNodeExtractor {

    private final JsonNode productNode;
    private final ObjectMapper objectMapper;

    private JsonNodeExtractor(JsonNode productNode, ObjectMapper objectMapper) {
        this.productNode = productNode;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an extractor for a single product/category node.
     *
     * @param productNode   the payload node to extract from
     * @param objectMapper  mapper used for further parsing
     */
    public static JsonNodeExtractor from(JsonNode productNode, ObjectMapper objectMapper) {
        return new JsonNodeExtractor(productNode, objectMapper);
    }

    /** The product's database id from the Menu Service, or null when absent. */
    public Long productId() {
        return productNode.path("id").asLong(0) != 0 ? productNode.path("id").asLong() : null;
    }

    /** The first SKU's database id, or null when the product has no SKUs. */
    public Long skuId() {
        // Try to get first SKU id
        if (productNode.has("skus") && productNode.get("skus").isArray() && !productNode.get("skus").isEmpty()) {
            long sid = productNode.get("skus").get(0).path("id").asLong(0);
            if (sid != 0) return sid;
        }
        return null;
    }

    /** The product name from the Menu Service, or null when blank/absent. */
    public String name() {
        String n = productNode.path("name").asText(null);
        return (n != null && !n.isBlank()) ? n : null;
    }

    /** The product description from the Menu Service, or null when blank/absent. */
    public String description() {
        String d = productNode.path("description").asText(null);
        return (d != null && !d.isBlank()) ? d : null;
    }

    /** The resolved unit price in IDR, or null when the payload carries no positive price. */
    public BigDecimal price() {
        BigDecimal price = extractPrice(productNode);
        return (price != null && price.compareTo(BigDecimal.ZERO) > 0) ? price : null;
    }

    /** All SKUs/variants with their own id, name, price, and variant; null when there are none. */
    public List<SkuToolResponse> skus() {
        List<SkuToolResponse> result = new ArrayList<>();
        if (productNode.has("skus") && productNode.get("skus").isArray()) {
            for (JsonNode sku : productNode.get("skus")) {
                Long sid = sku.path("id").asLong(0);
                if (sid == 0) continue;
                String skuName = sku.path("name").asText(null);
                BigDecimal skuPrice = extractPrice(sku);
                String variant = sku.path("variant").asText(null);
                result.add(new SkuToolResponse(sid, skuName, skuPrice, variant));
            }
        }
        return result.isEmpty() ? null : result;
    }

    /** Maps this node into a {@link ProductToolResponse} preserving id, price, and all SKUs. */
    public ProductToolResponse toProductToolResponse() {
        return ProductToolResponse.of(
                productId(),
                skuId(),
                name(),
                description(),
                price(),
                skus()
        );
    }

    /**
     * Extracts price from a node, supporting multiple price paths:
     * - tierPrice.price
     * - skuTierPrices[0].price
     * - tierPrices[0].price
     * - price (direct on SKU or product)
     * - first SKU's price (when the product node carries no price of its own)
     */
    private BigDecimal extractPrice(JsonNode node) {
        if (node == null || node.isNull()) return BigDecimal.ZERO;

        // 1. tierPrice.price (most common)
        if (node.has("tierPrice") && node.get("tierPrice").isObject()) {
            String raw = node.get("tierPrice").path("price").asText(null);
            if (isValidPrice(raw)) return new BigDecimal(raw);
        }

        // 2. skuTierPrices[0].price
        JsonNode tierPrices = node.has("skuTierPrices") ? node.get("skuTierPrices")
                : node.has("tierPrices") ? node.get("tierPrices") : null;
        if (tierPrices != null && tierPrices.isArray() && !tierPrices.isEmpty()) {
            String raw = tierPrices.get(0).path("price").asText(null);
            if (isValidPrice(raw)) return new BigDecimal(raw);
        }

        // 3. Direct price on SKU or product
        if (node.has("price")) {
            String raw = node.path("price").asText(null);
            if (isValidPrice(raw)) return new BigDecimal(raw);
        }

        // 4. Fallback to the first SKU's price — the Menu Service often carries
        //    the price only on the SKU (skus[].tierPrice.price).
        if (node.has("skus") && node.get("skus").isArray() && !node.get("skus").isEmpty()) {
            return extractPrice(node.get("skus").get(0));
        }

        return BigDecimal.ZERO;
    }

    private boolean isValidPrice(String raw) {
        return raw != null && !raw.isBlank() && !raw.equals("0") && !raw.equals("0.0");
    }

    /**
     * Extracts the data array from various Menu Service response formats.
     * Supports:
     * - Root array: `[...]`
     * - Normal: `{"data": [...]}`
     * - Nested: `{"data": {"data": [...]}}`
     */
    public static List<JsonNode> extractDataArray(JsonNode root) {
        if (root == null) return List.of();

        JsonNode data = root.has("data") ? root.get("data") : root;

        if (data != null && data.isObject() && data.has("data") && data.get("data").isArray()) {
            data = data.get("data");
        }

        if (data == null || !data.isArray() || data.isEmpty()) {
            return List.of();
        }

        List<JsonNode> result = new ArrayList<>();
        data.forEach(result::add);
        return result;
    }

    /**
     * Creates an extractor per product in a Menu Service response.
     * Supports root-array, {@code data}, and nested {@code data.data} formats.
     *
     * @param json          the raw Menu Service response
     * @param objectMapper  mapper used to parse the JSON
     * @return one extractor per product; empty list on parse failure
     */
    public static List<JsonNodeExtractor> extractAllProducts(String json, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return extractDataArray(root).stream()
                    .map(node -> new JsonNodeExtractor(node, objectMapper))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Creates an extractor per category in a Menu Service response.
     * Supports the same response formats as {@link #extractAllProducts}.
     *
     * @param json          the raw Menu Service response
     * @param objectMapper  mapper used to parse the JSON
     * @return one extractor per category; empty list on parse failure
     */
    public static List<JsonNodeExtractor> extractAllCategories(String json, ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(json);
            return extractDataArray(root).stream()
                    .map(node -> new JsonNodeExtractor(node, objectMapper))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}