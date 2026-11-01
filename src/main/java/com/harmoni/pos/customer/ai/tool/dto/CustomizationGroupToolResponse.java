package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * One customization group assigned to a product, as returned to the LLM.
 * <p>
 * The rules are the effective ones for this product (a per-product override wins over
 * the customization master), so the assistant can ask exactly what the Menu Service
 * will later validate: {@code SINGLE} takes one option, {@code MULTIPLE} takes up to
 * {@code maxSelection}, and a required group needs at least {@code minSelection} options.
 *
 * @param customizationId authoritative group id (used when adding to the cart)
 * @param name            group name, e.g. "Size" or "Milk"
 * @param selectionType   SINGLE or MULTIPLE
 * @param required        whether the customer must choose in this group
 * @param allowQuantity   whether a chosen option may carry a quantity greater than one
 * @param minSelection    minimum number of distinct options the group needs
 * @param maxSelection    maximum number of distinct options the group allows
 * @param options         the options the customer may choose from
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomizationGroupToolResponse(
        Long customizationId,
        String name,
        String selectionType,
        Boolean required,
        Boolean allowQuantity,
        Integer minSelection,
        Integer maxSelection,
        List<CustomizationOptionToolResponse> options
) {}
