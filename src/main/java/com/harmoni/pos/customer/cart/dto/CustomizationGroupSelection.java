package com.harmoni.pos.customer.cart.dto;

import java.util.ArrayList;
import java.util.List;

public record CustomizationGroupSelection(
        Long customizationId,
        List<CustomizationOptionSelection> options
) {
    public CustomizationGroupSelection(Long customizationId, List<CustomizationOptionSelection> options) {
        this.customizationId = customizationId;
        this.options = options == null ? new ArrayList<>() : options;
    }
}
