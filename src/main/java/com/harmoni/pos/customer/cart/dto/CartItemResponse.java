package com.harmoni.pos.customer.cart.dto;

import java.math.BigDecimal;

/**
 * A single cart line item: product/SKU id, name, unit price, quantity and line total.
 */
public record CartItemResponse(
        Long productId,
        Long skuId,
        String name,
        BigDecimal price,
        int quantity,
        BigDecimal totalPrice
) {}
