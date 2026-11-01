package com.harmoni.pos.customer.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.ai.tool.ToolConstants;
import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.cart.dto.CartItemResponse;
import com.harmoni.pos.customer.cart.dto.CartResponse;
import com.harmoni.pos.customer.cart.dto.CustomizationGroupSelection;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import com.harmoni.pos.customer.domain.exception.CustomerSessionNotFoundException;
import com.harmoni.pos.customer.domain.exception.InvalidCustomerMessageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.*;
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
            new CartItemResponse(1L, 101L, "Coffee Latte", new BigDecimal("18000"), 0),
            new CartItemResponse(2L, 102L, "Americano", new BigDecimal("12000"), 0),
            new CartItemResponse(3L, 103L, "Cappuccino", new BigDecimal("16000"), 0),
            new CartItemResponse(4L, 104L, "Espresso", new BigDecimal("10000"), 0),
            new CartItemResponse(5L, 105L, "Mocha", new BigDecimal("20000"), 0)
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
        return addToCart(sessionId, productId, skuId, quantity, java.util.List.of());
    }

    public CartResponse addToCart(long sessionId, Long productId, Long skuId, int quantity, List<CustomizationGroupSelection> customizations) {
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

        List<CustomizationGroupSelection> customizationsToUse = customizations == null ? List.of() : customizations;
        // The chosen options carry their own per-unit charge, so the line has to be priced
        // as (SKU price + option surcharge) x quantity. Pricing on the SKU alone silently
        // drops what the customer actually agreed to pay.
        BigDecimal surcharge = resolveSurcharge(productId, customizationsToUse);

        List<CartItemResponse> items = new ArrayList<>(carts.getOrDefault(sessionId, new ArrayList<>()));
        int idx = -1;
        // Match on (productId, skuId, chosen options) so different variants AND different
        // customization answers stay separate lines. Matching on the product and SKU alone
        // would merge a plain coffee into an extra-shot one and price both the same way.
        String lineKey = lineKey(resolvedSkuId, customizationsToUse);
        for (int i = 0; i < items.size(); i++) {
            CartItemResponse it = items.get(i);
            if (it.productId().equals(productId)
                    && Objects.equals(it.skuId(), resolvedSkuId)
                    && lineKey(resolvedSkuId, it.customizations()).equals(lineKey)) { idx = i; break; }
        }
        if (idx >= 0) {
            CartItemResponse existing = items.get(idx);
            int newQty = existing.quantity() + quantity;
            // Keep whichever choices the line already carries; the surcharge must follow
            // them, not the choices on this particular add, or the total drifts on re-add.
            List<CustomizationGroupSelection> effectiveCustomizations =
                    existing.customizations() == null || existing.customizations().isEmpty()
                            ? customizationsToUse
                            : existing.customizations();
            BigDecimal effectiveSurcharge = effectiveCustomizations == customizationsToUse
                    ? surcharge
                    : resolveSurcharge(productId, effectiveCustomizations);
            if (newQty <= 0) items.remove(idx);
            else items.set(idx, CartItemResponse.priced(productId, resolvedSkuId, product.name(),
                    product.price(), newQty, effectiveSurcharge, effectiveCustomizations));
        } else if (quantity > 0) {
            items.add(CartItemResponse.priced(productId, resolvedSkuId, product.name(),
                    product.price(), quantity, surcharge, customizationsToUse));
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
                .map(p -> new CartItemResponse(p.productId(), p.skuId(), p.name(), p.price(), 0, BigDecimal.ZERO, java.util.List.of()))
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
     * Identity of a cart line: the SKU plus the options chosen on it, with each option
     * repeated once per unit and sorted, so the same choices in a different order resolve
     * to the same line.
     *
     * @param skuId          the SKU on the line
     * @param customizations the choices on the line
     * @return the line's identity
     */
    private static String lineKey(Long skuId, List<CustomizationGroupSelection> customizations) {
        List<Long> optionIds = new ArrayList<>();
        if (customizations != null) {
            for (CustomizationGroupSelection group : customizations) {
                if (group == null || group.options() == null) {
                    continue;
                }
                for (var option : group.options()) {
                    if (option == null || option.customizationOptionId() == null) {
                        continue;
                    }
                    int units = option.quantity() == null ? 1 : Math.max(1, option.quantity());
                    for (int unit = 0; unit < units; unit++) {
                        optionIds.add(option.customizationOptionId());
                    }
                }
            }
        }
        Collections.sort(optionIds);
        return skuId + "|" + optionIds;
    }

    /**
     * Sums the per-unit charge of the chosen customization options.
     * <p>
     * Each option is charged its price once per unit chosen, so two extra shots declared
     * as one option with quantity 2 cost twice the option's price. The prices come from
     * the Menu Service; an option that cannot be priced contributes nothing rather than
     * failing the add, and the Order Service re-prices the line at confirmation anyway.
     *
     * @param customizations the groups and option selections on the line
     * @return the total per-unit surcharge, never {@code null}
     */
    private BigDecimal resolveSurcharge(Long productId, List<CustomizationGroupSelection> customizations) {
        if (customizations == null || customizations.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, BigDecimal> prices = fetchOptionPrices(productId, customizations);
        BigDecimal total = BigDecimal.ZERO;
        for (CustomizationGroupSelection group : customizations) {
            if (group == null || group.options() == null) {
                continue;
            }
            for (var option : group.options()) {
                if (option == null || option.customizationOptionId() == null) {
                    continue;
                }
                BigDecimal price = prices.get(option.customizationOptionId());
                if (price == null) {
                    continue;
                }
                int units = option.quantity() == null ? 1 : Math.max(1, option.quantity());
                total = total.add(price.multiply(BigDecimal.valueOf(units)));
            }
        }
        return total;
    }

    /**
     * Reads the unit price of each chosen option from the Menu Service.
     * <p>
     * The product payload already carries {@code customizations[].options[].tierPrices},
     * so one product read covers every option on the line. Options that are absent from
     * the catalogue are left out, which makes them free rather than an error.
     *
     * @param customizations the selections to price
     * @return the unit price per option id
     */
    private Map<Long, BigDecimal> fetchOptionPrices(Long productId, List<CustomizationGroupSelection> customizations) {
        Set<Long> optionIds = new LinkedHashSet<>();
        for (CustomizationGroupSelection group : customizations) {
            if (group == null || group.options() == null) {
                continue;
            }
            for (var option : group.options()) {
                if (option != null && option.customizationOptionId() != null) {
                    optionIds.add(option.customizationOptionId());
                }
            }
        }
        if (optionIds.isEmpty() || productId == null) {
            return Map.of();
        }
        Map<Long, BigDecimal> prices = new HashMap<>();
        try {
            JsonNode product = fetchProductNode(productId);
            if (product != null) {
                collectOptionPrices(product, optionIds, prices);
            }
        } catch (Exception e) {
            log.warn("resolveSurcharge failed to read option prices: {}", e.getMessage());
        }
        return prices;
    }

    /**
     * Collects prices for the wanted options from one product payload.
     *
     * @param product   the Menu Service product node
     * @param optionIds the option ids being priced
     * @param prices    the map to fill
     */
    private void collectOptionPrices(JsonNode product, Set<Long> optionIds, Map<Long, BigDecimal> prices) {
        JsonNode groups = product.get("customizations");
        if (groups == null || !groups.isArray()) {
            return;
        }
        for (JsonNode group : groups) {
            JsonNode options = group.has("options") ? group.get("options")
                    : group.has("customizationOptions") ? group.get("customizationOptions") : null;
            if (options == null || !options.isArray()) {
                continue;
            }
            for (JsonNode option : options) {
                long optionId = option.path("id").asLong(0);
                if (optionId == 0 || !optionIds.contains(optionId)) {
                    continue;
                }
                BigDecimal price = cheapestOptionTierPrice(option);
                if (price != null) {
                    prices.put(optionId, price);
                }
            }
        }
    }

    /**
     * The cheapest live tier price of a customization option.
     * <p>
     * The cart has no operator, so the price tier cannot be matched here. The lowest
     * live row stands in, exactly as the iPad client does, and the Order Service
     * re-resolves the real amount when it prices the line.
     *
     * @param option the option node
     * @return the price, or {@code null} when no tier row carries one
     */
    private BigDecimal cheapestOptionTierPrice(JsonNode option) {
        JsonNode tiers = option.has("tierPrices") ? option.get("tierPrices")
                : option.has("skuTierPrices") ? option.get("skuTierPrices") : null;
        if (tiers != null && tiers.isArray()) {
            BigDecimal cheapest = null;
            for (JsonNode tier : tiers) {
                if (tier.path("deleted").asBoolean(false)) {
                    continue;
                }
                String raw = tier.path("price").asText(null);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                BigDecimal candidate = new BigDecimal(raw);
                if (cheapest == null || candidate.compareTo(cheapest) < 0) {
                    cheapest = candidate;
                }
            }
            if (cheapest != null) {
                return cheapest;
            }
        }
        if (option.has("price")) {
            String raw = option.path("price").asText(null);
            if (raw != null && !raw.isBlank()) {
                return new BigDecimal(raw);
            }
        }
        return null;
    }

    /**
     * Reads one product from the Menu Service as a JSON node.
     *
     * @param productId the product id
     * @return the product node, or {@code null} when unavailable
     */
    private JsonNode fetchProductNode(Long productId) {
        try {
            String endpoint = menuProps.getEndpoints().getProductById();
            String json = menuRestClient.get()
                    .uri(endpoint, productId)
                    .header(ToolConstants.HEADER_X_USERNAME, ToolConstants.DEFAULT_USERNAME)
                    .retrieve().body(String.class);
            if (json == null) return null;
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data != null && data.isObject() && data.has("data") && data.get("data").isObject()) {
                data = data.get("data");
            }
            return data != null && data.isObject() ? data : null;
        } catch (Exception e) {
            log.warn("fetchProductNode failed productId={}: {}", productId, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches a product from the Menu Service, resolving the first SKU's price.
     *
     * @param productId product id to fetch
     * @return the product as a cart item, or empty when not found/unreachable
     */
    private Optional<CartItemResponse> fetchProduct(Long productId) {
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
    private Optional<CartItemResponse> fetchProduct(Long productId, Long preferredSkuId) {
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
            if (data == null || data.isNull() || (data.isObject() && data.isEmpty())) return Optional.empty();
            if (data.isObject() && data.has("data") && data.get("data").isObject()) data = data.get("data");
            // data is product object
            long id = data.path("id").asLong(0);
            if (id == 0) return Optional.empty();
            String name = data.path("name").asText(null);
            if (name == null || name.isBlank()) return Optional.empty();
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
            return Optional.of(new CartItemResponse(id, skuId, name, price, 0, BigDecimal.ZERO));
        } catch (Exception e) {
            log.warn("fetchProduct failed productId={}: {}", productId, e.getMessage());
            return Optional.empty();
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
