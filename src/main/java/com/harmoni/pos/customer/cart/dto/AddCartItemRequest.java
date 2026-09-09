package com.harmoni.pos.customer.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for adding a product, optionally a specific SKU, to a customer cart.
 */
@Schema(description = "Add a product to the cart")
public record AddCartItemRequest(
        @Schema(description = "Product id from the Menu Service", example = "60") @NotNull Long productId,
        @Schema(description = "Optional SKU id from the Menu Service; when omitted the first SKU is used", example = "107") Long skuId,
        @Schema(description = "Quantity to add", example = "2", minimum = "1") @Min(1) int quantity
) {}
