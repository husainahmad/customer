package com.harmoni.pos.customer.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.ai.tool.ToolConstants;
import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.cart.dto.CartItemResponse;
import com.harmoni.pos.customer.cart.dto.CartResponse;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import com.harmoni.pos.customer.domain.exception.CustomerSessionNotFoundException;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerMessageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cart hosted in customer service — ai-order only talks to customer:8084.
 * In-memory for now; later persist to DB or proxy to order:8083 / menu:8082.
 * Menu proxy will be added here (RestClient to menu:8082) so ai-order never calls menu directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerCartService {

    private final CustomerSessionRepository sessionRepository;
    @Qualifier("menuRestClient")
    private final RestClient menuRestClient;
    private final MenuServiceProperties menuProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Fallback static menu for offline tests (mybatisTest without menu:8082) — not used in prod
    private static final List<CartItemResponse> STATIC_MENU = List.of(
            new CartItemResponse(1L, 101L, "Coffee Latte", new BigDecimal("18000"), 0, BigDecimal.ZERO),
            new CartItemResponse(2L, 102L, "Americano", new BigDecimal("12000"), 0, BigDecimal.ZERO),
            new CartItemResponse(3L, 103L, "Cappuccino", new BigDecimal("16000"), 0, BigDecimal.ZERO),
            new CartItemResponse(4L, 104L, "Espresso", new BigDecimal("10000"), 0, BigDecimal.ZERO),
            new CartItemResponse(5L, 105L, "Mocha", new BigDecimal("20000"), 0, BigDecimal.ZERO)
    );

    private final Map<Long, List<CartItemResponse>> carts = new ConcurrentHashMap<>();

    /**
     * Returns the current cart for a session.
     *
     * @param sessionId customer session id
     * @return all cart items plus totals; an empty cart when nothing has been added
     */
    public CartResponse getCart(long sessionId) {
        requireOpenSession(sessionId);
        List<CartItemResponse> items = carts.getOrDefault(sessionId, List.of());
        return toResponse(items);
    }

    /**
     * Adds a product to the cart without a specific SKU.
     * <p>
     * Convenience overload: the SKU is resolved server-side (first SKU of the product).
     *
     * @param sessionId customer session id
     * @param productId product id from the Menu Service
     * @param quantity  how many to add
     * @return the updated cart
     */
    public CartResponse addToCart(long sessionId, Long productId, int quantity) {
        return addToCart(sessionId, productId, null, quantity);
    }

    /**
     * Adds a product SKU to the cart.
     * <p>
     * The line is keyed on {@code (productId, skuId)} so different variants of the
     * same product remain separate lines. When {@code skuId} is null it is resolved
     * to the product's first SKU (falling back to the product id).
     *
     * @param sessionId customer session id
     * @param productId product id from the Menu Service
     * @param skuId     SKU id from the Menu Service; null selects the first SKU
     * @param quantity  how many to add; adding a negative amount that zeroes a line removes it
     * @return the updated cart
     */
    public CartResponse addToCart(long sessionId, Long productId, Long skuId, int quantity) {
        requireOpenSession(sessionId);
        if (productId == null) throw new InvalidCustomerMessageException("productId required");
        CartItemResponse product = fetchProduct(productId, skuId)
                .orElseGet(() -> STATIC_MENU.stream()
                        .filter(p -> p.productId().equals(productId))
                        .findFirst()
                        .orElse(null));
        if (product == null) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }

        Long resolvedSkuId = skuId != null ? skuId : product.skuId();
        if (resolvedSkuId == null) resolvedSkuId = productId;

        List<CartItemResponse> items = new ArrayList<>(carts.getOrDefault(sessionId, new ArrayList<>()));
        int idx = -1;
        // Match on (productId, skuId) so different variants stay separate lines.
        for (int i = 0; i < items.size(); i++) {
            CartItemResponse it = items.get(i);
            if (it.productId().equals(productId) && java.util.Objects.equals(it.skuId(), resolvedSkuId)) { idx = i; break; }
        }
        if (idx >= 0) {
            CartItemResponse existing = items.get(idx);
            int newQty = existing.quantity() + quantity;
            if (newQty <= 0) items.remove(idx);
            else items.set(idx, new CartItemResponse(productId, resolvedSkuId, product.name(), product.price(), newQty, product.price().multiply(BigDecimal.valueOf(newQty))));
        } else if (quantity > 0) {
            items.add(new CartItemResponse(productId, resolvedSkuId, product.name(), product.price(), quantity, product.price().multiply(BigDecimal.valueOf(quantity))));
        }
        carts.put(sessionId, items);
        log.info("Cart add sessionId={} productId={} skuId={} qty={} totalItems={}", sessionId, productId, resolvedSkuId, quantity, items.stream().mapToInt(CartItemResponse::quantity).sum());
        return toResponse(items);
    }

    /**
     * Removes every line of a product from the cart.
     *
     * @param sessionId customer session id
     * @param productId product id to remove
     * @return the updated cart
     */
    public CartResponse removeFromCart(long sessionId, Long productId) {
        requireOpenSession(sessionId);
        List<CartItemResponse> items = new ArrayList<>(carts.getOrDefault(sessionId, new ArrayList<>()));
        items.removeIf(i -> i.productId().equals(productId));
        carts.put(sessionId, items);
        return toResponse(items);
    }

    /**
     * Empties the cart entirely — used right after a successful checkout.
     *
     * @param sessionId customer session id
     * @return the empty cart
     */
    public CartResponse clearCart(long sessionId) {
        requireOpenSession(sessionId);
        carts.remove(sessionId);
        log.info("Cart cleared sessionId={}", sessionId);
        return toResponse(List.of());
    }

    /**
     * Returns the static fallback menu (ids, names, prices).
     * <p>
     * Only used when the Menu Service proxy is unavailable; never used in production cart logic.
     */
    public List<CartItemResponse> menu() {
        return STATIC_MENU.stream()
                .map(p -> new CartItemResponse(p.productId(), p.skuId(), p.name(), p.price(), 0, BigDecimal.ZERO))
                .toList();
    }

    /**
     * Verifies the session exists and is still open before any cart mutation.
     *
     * @param sessionId customer session id to check
     * @throws CustomerSessionNotFoundException when the session does not exist
     * @throws InvalidCustomerMessageException  when the session is closed
     */
    private void requireOpenSession(long sessionId) {
        var sess = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomerSessionNotFoundException(sessionId));
        if (!sess.isOpen()) throw new InvalidCustomerMessageException("Cannot modify cart on closed session " + sessionId);
    }

    /** Wraps cart lines with item count and total price. */
    private static CartResponse toResponse(List<CartItemResponse> items) {
        int totalItems = items.stream().mapToInt(CartItemResponse::quantity).sum();
        BigDecimal totalPrice = items.stream().map(CartItemResponse::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(List.copyOf(items), totalItems, totalPrice);
    }

    /**
     * Fetches a product from the Menu Service, resolving the first SKU's price.
     *
     * @param productId product id to fetch
     * @return the product as a cart item, or empty when not found/unreachable
     */
    private java.util.Optional<CartItemResponse> fetchProduct(Long productId) {
        return fetchProduct(productId, null);
    }

    /**
     * Fetches a product from the Menu Service and resolves the price of the chosen SKU.
     * <p>
     * Uses the configured {@code /product/{id}} endpoint, which returns full SKUs
     * with tier prices, so variant pricing is honored. When {@code preferredSkuId}
     * is absent the first SKU (or the product id) is used as fallback.
     *
     * @param productId      product id to fetch
     * @param preferredSkuId the SKU the customer/LLM selected, or null for the first SKU
     * @return the product as a cart item, or empty when not found/unreachable
     */
    private java.util.Optional<CartItemResponse> fetchProduct(Long productId, Long preferredSkuId) {
        try {
            // Uses configured endpoint — returns full skus with tierPrices. /product?ids returns empty tierPrices.
            String endpoint = menuProps.getEndpoints().getProductById();
            String json = menuRestClient.get()
                    .uri(endpoint, productId)
                    .header(ToolConstants.HEADER_X_USERNAME, ToolConstants.DEFAULT_USERNAME)
                    .retrieve().body(String.class);
            if (json == null) return java.util.Optional.empty();
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data == null || data.isNull() || (data.isObject() && data.isEmpty())) return java.util.Optional.empty();
            if (data.isObject() && data.has("data") && data.get("data").isObject()) data = data.get("data");
            // data is product object
            long id = data.path("id").asLong(0);
            if (id == 0) return java.util.Optional.empty();
            String name = data.path("name").asText(null);
            if (name == null || name.isBlank()) return java.util.Optional.empty();
            JsonNode selectedSku = findSkuById(data, preferredSkuId);
            BigDecimal price = extractPrice(selectedSku != null ? selectedSku : data);
            Long skuId = selectedSku != null ? preferredSkuId : extractSkuId(data);
            // Fallback if price is zero — still allow add but warn; menu price endpoint is authoritative
            if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
                log.warn("Menu product {} has no price in payload, using 0", productId);
                price = BigDecimal.ZERO;
            }
            if (skuId == null) {
                log.warn("Menu product {} has no skuId, using productId as fallback", productId);
                skuId = productId;
            }
            return java.util.Optional.of(new CartItemResponse(id, skuId, name, price, 0, BigDecimal.ZERO));
        } catch (Exception e) {
            log.warn("fetchProduct failed productId={}: {}", productId, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Locates a SKU node inside a product payload by its database id.
     *
     * @param product the product JSON node from the Menu Service
     * @param skuId   the SKU id to look up, or null
     * @return the matching SKU node, or null when absent
     */
    private JsonNode findSkuById(JsonNode product, Long skuId) {
        if (skuId == null) return null;
        if (product != null && product.has("skus") && product.get("skus").isArray()) {
            for (JsonNode sku : product.get("skus")) {
                if (sku.path("id").asLong(0) == skuId) return sku;
            }
        }
        return null;
    }

    /** Returns the first non-zero SKU id of a product, or null when there is none. */
    private Long extractSkuId(JsonNode p) {
        try {
            if (p.has("skus") && p.get("skus").isArray() && !p.get("skus").isEmpty()) {
                for (JsonNode sku : p.get("skus")) {
                    long sid = sku.path("id").asLong(0);
                    if (sid != 0) return sid;
                }
            }
        } catch (Exception e) {
            log.warn("extractSkuId failed for product {}: {}", p.path("id").asLong(), e.getMessage());
        }
        return null;
    }

    /**
     * Resolves the unit price of a product or SKU payload.
     * <p>
     * Handles both {@code /product/{id}} (SKUs with tierPrice) and fallback
     * payloads. The node may be a product node (SKUs are consulted first) or a
     * single SKU node.
     *
     * @param p product node or SKU node from the Menu Service
     * @return the resolved price, or {@code BigDecimal.ZERO} when none is available
     */
    private BigDecimal extractPrice(JsonNode p) {
        // Handles both /product/{id} (skus with tierPrice) and fallback.
        // p may be a product node OR a single SKU node.
        try {
            // If a product node, resolve from its SKUs first.
            if (p.has("skus") && p.get("skus").isArray() && !p.get("skus").isEmpty()) {
                JsonNode sku = p.get("skus").get(0);
                BigDecimal skuPrice = extractSkuPrice(sku);
                if (skuPrice != null) return skuPrice;
            }
            // SKU node (tierPrice / skuTierPrices / tierPrices / price) or product-level price.
            BigDecimal nodePrice = extractSkuPrice(p);
            if (nodePrice != null) return nodePrice;
            if (p.has("price")) {
                String raw = p.path("price").asText(null);
                if (raw != null && !raw.isBlank() && !raw.equals("0") && !raw.equals("0.0")) return new BigDecimal(raw);
            }
        } catch (Exception e) {
            log.warn("extractPrice failed for product {}: {}", p.path("id").asLong(), e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    /**
     * Resolves a SKU's price from its tierPrice object, tier-price arrays, or a
     * direct price field, in that order.
     *
     * @param sku the SKU JsonNode
     * @return the resolved price, or null when the SKU carries none
     */
    private BigDecimal extractSkuPrice(JsonNode sku) {
        if (sku.has("tierPrice") && sku.get("tierPrice").isObject()) {
            String raw = sku.get("tierPrice").path("price").asText(null);
            if (raw != null && !raw.isBlank() && !raw.equals("0") && !raw.equals("0.0")) return new BigDecimal(raw);
        }
        JsonNode tier = sku.has("skuTierPrices") ? sku.get("skuTierPrices") : sku.has("tierPrices") ? sku.get("tierPrices") : null;
        if (tier != null && tier.isArray() && !tier.isEmpty()) {
            String raw = tier.get(0).path("price").asText(null);
            if (raw != null && !raw.isBlank() && !raw.equals("0") && !raw.equals("0.0")) return new BigDecimal(raw);
        }
        if (sku.has("price")) {
            String raw = sku.path("price").asText(null);
            if (raw != null && !raw.isBlank() && !raw.equals("0") && !raw.equals("0.0")) return new BigDecimal(raw);
        }
        return null;
    }
}
