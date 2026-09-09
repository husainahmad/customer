package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.harmoni.pos.customer.ai.tool.JsonNodeExtractor;

import java.math.BigDecimal;
import java.util.List;

/**
 * A product as returned to the LLM.
 * <p>
 * All identifiers ({@code productId}, {@code skuId}) and the {@code price}
 * come directly from the Menu Service — the LLM must never guess them.
 * {@code skus} preserves every variant so the LLM can pick the correct
 * {@code skuId} for ordering instead of blindly selecting the first SKU.
 *
 * @param productId    authoritative product database id
 * @param skuId        first SKU's database id (convenience; also listed in {@code skus})
 * @param name         product name as returned by the Menu Service
 * @param description  product description, when available
 * @param price        unit price in IDR from the Menu Service
 * @param skus         all SKUs/variants with their own skuId and price
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductToolResponse(
        Long productId,
        Long skuId,
        String name,
        String description,
        BigDecimal price,
        List<SkuToolResponse> skus
) {
    /**
     * Maps a {@link JsonNodeExtractor} (built from the Menu Service payload)
     * into a product tool response without losing any identifier or price.
     */
    public static ProductToolResponse of(JsonNodeExtractor extractor) {
        return new ProductToolResponse(
                extractor.productId(),
                extractor.skuId(),
                extractor.name(),
                extractor.description(),
                extractor.price(),
                extractor.skus()
        );
    }

    /**
     * Builds a product tool response from already-resolved values.
     *
     * @param productId    authoritative product database id
     * @param skuId        first SKU database id (or {@code null} when no SKUs)
     * @param name         product name
     * @param description  product description (may be {@code null})
     * @param price        price in IDR (may be {@code null} when the payload carries none)
     * @param skus         SKUs/variants (may be {@code null} or empty)
     */
    public static ProductToolResponse of(Long productId, Long skuId, String name, String description, BigDecimal price, List<SkuToolResponse> skus) {
        return new ProductToolResponse(productId, skuId, name, description, price, skus);
    }

    /**
     * Compact factory for responses that only need id, name, and price.
     */
    public static ProductToolResponse simple(Long productId, Long skuId, String name, BigDecimal price) {
        return new ProductToolResponse(productId, skuId, name, null, price, null);
    }
}