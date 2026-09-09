package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Envelope around a product tool response sent to the LLM.
 * <p>
 * Either {@code products} is populated (search succeeded) or {@code error}
 * holds a sentinel message such as {@code PRODUCT_NOT_FOUND: ...} or
 * {@code MENU_SERVICE_ERROR: ...}.
 *
 * @param products  matching products from the Menu Service (null on error)
 * @param error     sentinel error message (null on success)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSearchToolResponse(
        List<ProductToolResponse> products,
        String error
) {
    /** Builds a success response carrying the matched products. */
    public static ProductSearchToolResponse success(List<ProductToolResponse> products) {
        return new ProductSearchToolResponse(products, null);
    }

    /** Builds a product-not-found response; {@code context} describes what was searched. */
    public static ProductSearchToolResponse notFound(String context) {
        return new ProductSearchToolResponse(null, "PRODUCT_NOT_FOUND: " + context);
    }

    /** Builds an error response (e.g. Menu Service unreachable or invalid payload). */
    public static ProductSearchToolResponse error(String detail) {
        return new ProductSearchToolResponse(null, "MENU_SERVICE_ERROR: " + detail);
    }

    /**
     * Whether this response carries an error sentinel.
     * <p>
     * Named {@code hasError()} (not {@code isError()}) so Jackson does not
     * serialize it as a boolean property that would clobber the {@code error}
     * String field.
     */
    public boolean hasError() {
        return error != null;
    }
}