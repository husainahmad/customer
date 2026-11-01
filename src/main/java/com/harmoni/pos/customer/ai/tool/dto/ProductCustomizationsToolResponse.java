package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Envelope for the {@code getProductCustomizations} tool: whether a product carries
 * customization groups and, when it does, the groups and options the customer must
 * choose from.
 * <p>
 * Either {@code customizations} is populated (possibly empty) or {@code error} holds a
 * sentinel message such as {@code MENU_SERVICE_ERROR: ...}.
 *
 * @param productId        the product that was checked
 * @param hasCustomizations true when at least one group is assigned to the product
 * @param customizations   the product's customization groups, empty when it has none
 * @param error            sentinel error message (null on success)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductCustomizationsToolResponse(
        Long productId,
        Boolean hasCustomizations,
        List<CustomizationGroupToolResponse> customizations,
        String error
) {
    /** Builds a success response carrying the product's groups. */
    public static ProductCustomizationsToolResponse success(Long productId,
                                                            List<CustomizationGroupToolResponse> groups) {
        return new ProductCustomizationsToolResponse(productId, groups != null && !groups.isEmpty(), groups, null);
    }

    /** Builds an error response (e.g. Menu Service unreachable or invalid payload). */
    public static ProductCustomizationsToolResponse error(Long productId, String detail) {
        return new ProductCustomizationsToolResponse(productId, null, null,
                "MENU_SERVICE_ERROR: " + detail);
    }
}
