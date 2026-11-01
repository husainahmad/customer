package com.harmoni.pos.customer.ai.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;

/**
 * One option inside a product customization group, as returned to the LLM.
 *
 * @param optionId authoritative option id from the Menu Service (used for addToOrder)
 * @param name     option name, e.g. "Large" or "Oat Milk"
 * @param price    per unit surcharge in IDR, zero when the option is free
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomizationOptionToolResponse(
        Long optionId,
        String name,
        BigDecimal price
) {}
