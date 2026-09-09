package com.harmoni.pos.customer.cart.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A customer cart: its lines plus the total item count and total price.
 */
public record CartResponse(
        List<CartItemResponse> items,
        int totalItems,
        BigDecimal totalPrice
) {}
