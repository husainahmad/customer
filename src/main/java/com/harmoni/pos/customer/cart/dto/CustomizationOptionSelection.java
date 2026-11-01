package com.harmoni.pos.customer.cart.dto;

import java.util.Objects;

public record CustomizationOptionSelection(
        Long customizationOptionId,
        Integer quantity
) {
    public CustomizationOptionSelection(Long customizationOptionId, Integer quantity) {
        this.customizationOptionId = customizationOptionId;
        this.quantity = quantity == null || quantity < 1 ? 1 : quantity;
    }

    public CustomizationOptionSelection(Long customizationOptionId) {
        this(customizationOptionId, 1);
    }
}
