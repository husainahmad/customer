package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Envelope around a category tool response sent to the LLM.
 * <p>
 * Either {@code categories} is populated (search succeeded) or {@code error}
 * holds a sentinel message such as {@code CATEGORY_NOT_FOUND: ...} or
 * {@code MENU_SERVICE_ERROR: ...}.
 *
 * @param categories  matching categories from the Menu Service (null on error)
 * @param error       sentinel error message (null on success)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategorySearchToolResponse(
        List<CategoryToolResponse> categories,
        String error
) {
    /** Builds a success response carrying the matched categories. */
    public static CategorySearchToolResponse success(List<CategoryToolResponse> categories) {
        return new CategorySearchToolResponse(categories, null);
    }

    /** Builds a category-not-found response; {@code context} describes what was searched. */
    public static CategorySearchToolResponse notFound(String context) {
        return new CategorySearchToolResponse(null, "CATEGORY_NOT_FOUND: " + context);
    }

    /** Builds an error response (e.g. Menu Service unreachable or invalid payload). */
    public static CategorySearchToolResponse error(String detail) {
        return new CategorySearchToolResponse(null, "MENU_SERVICE_ERROR: " + detail);
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