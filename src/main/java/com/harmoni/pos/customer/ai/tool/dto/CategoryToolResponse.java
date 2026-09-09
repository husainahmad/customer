package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * A category as returned to the LLM.
 * <p>
 * {@code categoryId} is the authoritative database id from the Menu Service —
 * the LLM must use it when listing products via {@code getProductsByCategory}
 * and must never guess it.
 *
 * @param categoryId    authoritative category database id
 * @param name          category name
 * @param description   category description, when available
 * @param products      products belonging to the category (may be null/empty)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryToolResponse(
        Long categoryId,
        String name,
        String description,
        List<ProductToolResponse> products
) {
    /** Builds a category response without a product list. */
    public static CategoryToolResponse of(Long categoryId, String name, String description) {
        return new CategoryToolResponse(categoryId, name, description, null);
    }

    /** Compact factory for responses that only need id and name. */
    public static CategoryToolResponse simple(Long categoryId, String name) {
        return new CategoryToolResponse(categoryId, name, null, null);
    }
}