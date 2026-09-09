package com.harmoni.pos.customer.ai.tool;

import com.harmoni.pos.customer.cart.CustomerCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Handles the shopping cart — adding items, checking what's inside, and removing items.
 * <p>
 * This is the cart side of the AI toolbox. Everything is stored in customer:8084 via
 * {@link CustomerCartService}, so the frontend never talks to menu or order directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CartTools {

    private final CustomerCartService cartService;

    /**
     * Adds a product to the customer's cart using the authoritative identifiers
     * returned by the Menu Service's product search.
     * <p>
     * The LLM must obtain {@code productId} and {@code skuId} via
     * {@code searchProductsByName} first and pass them through unchanged — this
     * class never resolves or guesses database identifiers.
     *
     * @param sessionId customer session id
     * @param productId product id from the Menu Service
     * @param skuId     SKU id from the Menu Service (null falls back to the first SKU)
     * @param quantity  how many to add; null/&lt;=0 defaults to 1
     * @return a CART sentinel string (ITEM_ADDED or CART_ERROR)
     */
    @Tool(description = """
            Add a product to the customer's cart.

            You MUST call searchProductsByName first to get the real productId and skuId from the Menu Service.
            NEVER guess or invent productId or skuId.
            Always pass the skuId returned by searchProductsByName.

            If the product has multiple SKUs/variants, ask the customer which variant they want before adding.
            """)
    public String addToOrder(
            @ToolParam(description = "Customer session id, e.g. 42") Long sessionId,
            @ToolParam(description = "Product id from the Menu Service — call searchProductsByName first") Long productId,
            @ToolParam(description = "SKU id from the Menu Service — use the skuId returned by searchProductsByName") Long skuId,
            @ToolParam(description = "How many to add, defaults to 1") Integer quantity) {
        if (sessionId == null) return ToolConstants.cartError("sessionId is required");
        if (productId == null) return ToolConstants.cartError("productId is required, call searchProductsByName first");
        int qty = (quantity == null || quantity <= 0) ? 1 : quantity;
        try {
            var cart = cartService.addToCart(sessionId, productId, skuId, qty);
            return ToolConstants.itemAdded(productId, qty, cart.totalItems(), cart.totalPrice().toPlainString());
        } catch (Exception e) {
            log.warn("addToOrder failed sessionId={} productId={} skuId={} qty={}: {}", sessionId, productId, skuId, qty, e.getMessage());
            return ToolConstants.cartError("unable to add productId=" + productId);
        }
    }

    /**
     * Returns the current cart for a session.
     *
     * @param sessionId customer session id
     * @return the cart summary string, or CART_EMPTY when the cart has no items
     */
    @Tool(description = "Check what's currently in the cart — items and total. Returns CART_EMPTY if there's nothing yet.")
    public String getCurrentOrder(
            @ToolParam(description = "Customer session id") Long sessionId) {
        if (sessionId == null) return ToolConstants.cartError("sessionId is required");
        try {
            var cart = cartService.getCart(sessionId);
            if (cart.items().isEmpty()) return ToolConstants.cartEmpty();
            return cart.toString();
        } catch (Exception e) {
            log.warn("getCurrentOrder failed sessionId={}: {}", sessionId, e.getMessage());
            return ToolConstants.cartError("unable to read cart");
        }
    }

    /**
     * Removes an item from the cart entirely, regardless of its quantity.
     *
     * @param sessionId customer session id
     * @param productId product id already present in the cart
     * @return a CART sentinel string (ITEM_REMOVED or CART_ERROR)
     */
    @Tool(description = "Remove an item from the cart. You need the productId that's already in the cart.")
    public String removeOrderItem(
            @ToolParam(description = "Customer session id") Long sessionId,
            @ToolParam(description = "Product id to remove from the cart") Long productId) {
        if (sessionId == null) return ToolConstants.cartError("sessionId is required");
        if (productId == null) return ToolConstants.cartError("productId is required");
        try {
            var cart = cartService.removeFromCart(sessionId, productId);
            return ToolConstants.itemRemoved(productId, cart.totalItems());
        } catch (Exception e) {
            log.warn("removeOrderItem failed sessionId={} productId={}: {}", sessionId, productId, e.getMessage());
            return ToolConstants.cartError("unable to remove productId=" + productId);
        }
    }
}