package com.harmoni.pos.customer.cart.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A single cart line item: product/SKU id, name, unit price, quantity and line total.
 * <p>
 * The money fields separate the catalogue price from the customization surcharge, the
 * same way the iPad client does, so a caller can show "Rp20.000 + Rp4.000" rather than
 * one opaque number:
 * <ul>
 *   <li>{@code price} — the SKU's unit price from the Menu Service</li>
 *   <li>{@code surcharge} — the chosen options' per-unit extra, summed over the line</li>
 *   <li>{@code unitPrice} — {@code price + surcharge}, what one unit costs</li>
 *   <li>{@code totalPrice} — {@code unitPrice * quantity}, the line total</li>
 * </ul>
 * The surcharge is a display estimate, like the iPad client's: the operator's price tier
 * is not part of the cart, so option prices are taken from the cheapest live tier row.
 * Only option ids are ever sent onwards; the Order Service re-prices the line.
 */
public record CartItemResponse(
        Long productId,
        Long skuId,
        String name,
        BigDecimal price,
        int quantity,
        BigDecimal surcharge,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        List<CustomizationGroupSelection> customizations
) {
    public CartItemResponse {
        price = price == null ? BigDecimal.ZERO : price;
        surcharge = surcharge == null ? BigDecimal.ZERO : surcharge;
        unitPrice = unitPrice == null ? price : unitPrice;
        totalPrice = totalPrice == null ? unitPrice.multiply(BigDecimal.valueOf(quantity)) : totalPrice;
        customizations = customizations == null ? new ArrayList<>() : customizations;
    }

    /** A plain line with no customization choices. */
    public CartItemResponse(Long productId, Long skuId, String name, BigDecimal price, int quantity) {
        this(productId, skuId, name, price, quantity, BigDecimal.ZERO,
                price, price.multiply(BigDecimal.valueOf(quantity)), new ArrayList<>());
    }

    /** A line whose total is already known and carries no customization choices. */
    public CartItemResponse(Long productId, Long skuId, String name, BigDecimal price, int quantity,
                            BigDecimal totalPrice) {
        this(productId, skuId, name, price, quantity, BigDecimal.ZERO, price, totalPrice,
                new ArrayList<>());
    }

    /** A line with customization choices whose total is already known. */
    public CartItemResponse(Long productId, Long skuId, String name, BigDecimal price, int quantity,
                            BigDecimal totalPrice, List<CustomizationGroupSelection> customizations) {
        this(productId, skuId, name, price, quantity, BigDecimal.ZERO, price, totalPrice, customizations);
    }

    /**
     * Builds a line priced from its SKU and its chosen options.
     *
     * @param productId      the product
     * @param skuId          the SKU
     * @param name           the line's display name
     * @param price          the SKU's unit price
     * @param quantity       how many units
     * @param surcharge      the chosen options' per-unit extra
     * @param customizations the choices made on this line
     * @return the priced line
     */
    public static CartItemResponse priced(Long productId, Long skuId, String name, BigDecimal price,
                                          int quantity, BigDecimal surcharge,
                                          List<CustomizationGroupSelection> customizations) {
        BigDecimal extra = surcharge == null ? BigDecimal.ZERO : surcharge;
        BigDecimal unitPrice = price.add(extra);
        return new CartItemResponse(productId, skuId, name, price, quantity, extra, unitPrice,
                unitPrice.multiply(BigDecimal.valueOf(quantity)), customizations);
    }
}
