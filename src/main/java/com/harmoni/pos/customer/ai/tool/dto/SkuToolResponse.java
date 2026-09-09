package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

/**
 * A single SKU/variant of a product, as returned to the LLM.
 * <p>
 * The {@code skuId} is the authoritative database identifier the LLM must use
 * for add-to-cart/order operations. Prices come straight from the Menu Service.
 *
 * @param skuId    authoritative SKU database id (from the Menu Service)
 * @param name     SKU/variant name, e.g. "Regular" or "Large"
 * @param price    unit price in IDR from the Menu Service
 * @param variant  variant descriptor when the Menu Service provides one
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SkuToolResponse(
        Long skuId,
        String name,
        BigDecimal price,
        String variant
) {}