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
 * <p>
 * {@code hasCustomizations} and {@code customizations} say whether the customer must
 * answer any customization groups before the product can be added to the cart. When
 * the Menu Service payload carries them (for example the price-aware category
 * endpoint) they are extracted directly; otherwise the assistant calls
 * {@code getProductCustomizations} to check.
 *
 * @param productId         authoritative product database id
 * @param skuId             first SKU's database id (convenience; also listed in {@code skus})
 * @param name              product name as returned by the Menu Service
 * @param description       product description, when available
 * @param price             unit price in IDR from the Menu Service
 * @param skus              all SKUs/variants with their own skuId and price
 * @param hasCustomizations whether the product has customization groups to choose from
 * @param customizations    the customization groups, when the payload carries them
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductToolResponse(
        Long productId,
        Long skuId,
        String name,
        String description,
        BigDecimal price,
        List<SkuToolResponse> skus,
        Boolean hasCustomizations,
        List<CustomizationGroupToolResponse> customizations
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
                extractor.skus(),
                extractor.hasCustomizations(),
                extractor.customizations()
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
        return new ProductToolResponse(productId, skuId, name, description, price, skus, null, null);
    }

    /**
     * Compact factory for responses that only need id, name, and price.
     */
    public static ProductToolResponse simple(Long productId, Long skuId, String name, BigDecimal price) {
        return new ProductToolResponse(productId, skuId, name, null, price, null, null, null);
    }

    /** Returns a copy of this response carrying the given customization groups. */
    public ProductToolResponse withCustomizations(List<CustomizationGroupToolResponse> groups) {
        boolean has = groups != null && !groups.isEmpty();
        return new ProductToolResponse(productId, skuId, name, description, price, skus, has, groups);
    }
}
