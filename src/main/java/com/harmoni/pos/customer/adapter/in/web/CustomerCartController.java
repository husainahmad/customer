package com.harmoni.pos.customer.adapter.in.web;

import com.harmoni.pos.customer.cart.CustomerCartService;
import com.harmoni.pos.customer.cart.dto.AddCartItemRequest;
import com.harmoni.pos.customer.cart.dto.CartItemResponse;
import com.harmoni.pos.customer.cart.dto.CartResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Shopping cart — lives entirely in customer:8084.
 * <p>
 * ai-order only talks to customer, never to menu or order directly.
 * Prices are resolved from the Menu Service when items are added.
 */
@RestController
@RequestMapping("/api/v1/customer-sessions/{sessionId}/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "In-memory cart hosted in customer service — add, view and remove items")
public class CustomerCartController {

    private final CustomerCartService cartService;

    /**
     * Returns the current cart with items, quantities, and total price.
     *
     * @param sessionId the customer session the cart belongs to
     */
    @Operation(summary = "Get cart", description = "Returns the current cart with items, quantities and total price.")
    @GetMapping
    public CartResponse getCart(@Parameter(description = "Customer session id") @PathVariable long sessionId) {
        return cartService.getCart(sessionId);
    }

    /**
     * Adds a product SKU to the cart; an existing matching line is increased
     * rather than duplicated. Price is fetched from the Menu Service.
     *
     * @param sessionId the customer session the cart belongs to
     * @param req       product id, optional sku id, and quantity
     */
    @Operation(summary = "Add item to cart", description = "Adds a product to the cart. If the product is already there, the quantity is increased. Price is fetched from the Menu Service.")
    @PostMapping("/items")
    public CartResponse addItem(
            @Parameter(description = "Customer session id") @PathVariable long sessionId,
            @Valid @RequestBody AddCartItemRequest req) {
        return cartService.addToCart(sessionId, req.productId(), req.skuId(), req.quantity());
    }

    /**
     * Removes a product from the cart entirely, regardless of its quantity.
     *
     * @param sessionId the customer session the cart belongs to
     * @param productId product id to remove
     */
    @Operation(summary = "Remove item from cart", description = "Removes a product from the cart entirely, regardless of quantity.")
    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(
            @Parameter(description = "Customer session id") @PathVariable long sessionId,
            @Parameter(description = "Product id to remove") @PathVariable Long productId) {
        return cartService.removeFromCart(sessionId, productId);
    }

    /**
     * Lists the static fallback menu, useful when the Menu Service is offline.
     * Verifies the session is still open first.
     *
     * @param sessionId the customer session to validate
     */
    @Operation(summary = "List offline menu", description = "Fallback static menu — handy when the Menu Service is offline. Verifies the session is still open first.")
    @GetMapping("/menu")
    public List<CartItemResponse> menu(
            @Parameter(description = "Customer session id") @PathVariable long sessionId) {
        cartService.getCart(sessionId);
        return cartService.menu();
    }
}
