package com.harmoni.pos.customer.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.cart.CustomerCartService;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import com.harmoni.pos.customer.config.OrderServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Turns a cart into a real order — only when the customer explicitly says to.
 * <p>
 * This is the checkout side of the AI toolbox. It takes the current cart from
 * {@link CustomerCartService} and creates an order via the Order Service (8083).
 * Checkout is two API calls, both on the same path:
 * <ol>
 *   <li>POST /order — creates the order and returns its id</li>
 *   <li>PUT  /order — records the customer's chosen payment method and marks it paid</li>
 * </ol>
 * Won't create anything if the cart is empty and the payment method is unknown,
 * and it never invents store or customer IDs. All endpoint paths come from
 * {@link OrderServiceProperties} / {@link MenuServiceProperties}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTools {

    @Qualifier("orderRestClient")
    private final RestClient orderRestClient;

    @Qualifier("menuRestClient")
    private final RestClient menuRestClient;

    private final CustomerCartService cartService;
    private final CustomerSessionRepository sessionRepository;
    private final MenuServiceProperties menuProps;
    private final OrderServiceProperties orderProps;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = """
            Place the order from the current cart AND record its payment method. Only call this after the customer clearly confirms — like 'ya', 'pesan sekarang', or 'checkout'.
            FIRST ask the customer which payment method they want: Tunai/Cash, QRIS, or Kartu/Debit.
            paymentMethod is required. Valid values: 'tunai' (Cash, paymentId 1), 'qris' (QRIS, paymentId 2), 'kartu' (Kartu/Debit, paymentId 3).
            note is optional — pass any special request the customer mentioned (e.g. 'es extra', 'tanpa gula', 'pesan dibungkus').
            Creates the order (POST) then marks it paid (PUT). Will reject an empty cart.
            """)
    public String confirmOrder(
            @ToolParam(description = "Customer session id") Long sessionId,
            @ToolParam(description = "Customer name for the order, e.g. 'Budi' — defaults to 'AI Customer' if not provided") String username,
            @ToolParam(description = "Payment method chosen by the customer: tunai/cash, qris/qr, or kartu/card/debit") String paymentMethod,
            @ToolParam(description = "Optional special request/note for the order, e.g. 'es extra', 'tanpa gula' — omit when none") String note) {
        if (sessionId == null) return ToolConstants.orderError("sessionId is required");
        Integer paymentId = resolvePaymentId(paymentMethod);
        if (paymentId == null) {
            return ToolConstants.orderError("paymentMethod required — ask the customer to choose: Tunai/Cash, QRIS, or Kartu/Debit");
        }
        try {
            var session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            var cart = cartService.getCart(sessionId);
            if (cart.items().isEmpty()) return ToolConstants.cartEmpty("cannot confirm empty cart");

            String customerName = username != null && !username.isBlank() ? username : ToolConstants.DEFAULT_CUSTOMER_NAME;
            var orderDetails = cart.items().stream().map(i -> {
                Long skuId = i.skuId();
                if (skuId == null) {
                    skuId = resolveSkuId(i.productId());
                    if (skuId == null) skuId = i.productId();
                }
                return Map.of(
                        "productId", i.productId(),
                        "orderDetailSkus", java.util.List.of(Map.of(
                                "skuId", skuId,
                                "skuName", i.name(),
                                "quantity", i.quantity()
                        ))
                );
            }).toList();
            Map<String, Object> orderDto = new java.util.LinkedHashMap<>();
            orderDto.put("storeId", orderProps.getStoreId());
            orderDto.put("customerId", session.getCustomerId());
            orderDto.put("customerName", customerName);
            orderDto.put("storeServiceTypesId", orderProps.getStoreServiceTypesId());
            orderDto.put("orderDetails", orderDetails);
            if (note != null && !note.isBlank()) orderDto.put("remark", note.trim());
            String endpoint = orderProps.getEndpoints().getCreateOrder();
            String resp = orderRestClient.post()
                    .uri(endpoint)
                    .header(ToolConstants.HEADER_X_USERNAME, orderProps.getDefaultUsername())
                    .body(orderDto)
                    .retrieve().body(String.class);
            Integer orderId = extractOrderId(resp);
            if (orderId == null) {
                return ToolConstants.orderError("order was created but the response had no orderId");
            }

            Map<String, Object> paymentDto = Map.of("orderId", orderId, "paymentId", paymentId);
            String payResp = orderRestClient.put()
                    .uri(endpoint)
                    .header(ToolConstants.HEADER_X_USERNAME, orderProps.getDefaultUsername())
                    .body(paymentDto)
                    .retrieve().body(String.class);
            cartService.clearCart(sessionId);
            return ToolConstants.orderCreated(describePaidOrder(orderId, payResp, paymentId));
        } catch (Exception e) {
            log.warn("confirmOrder failed sessionId={}: {}", sessionId, e.getMessage(), e);
            return ToolConstants.orderError("unable to create or pay the order");
        }
    }

    // Maps the customer-facing payment method onto the Order Service payment ids (same mapping the POS apps use).
    private static Integer resolvePaymentId(String method) {
        if (method == null) return null;
        String m = method.trim().toLowerCase();
        if (m.matches("tunai|cash|uang|1")) return 1;
        if (m.matches("qris|qr|scan|2")) return 2;
        if (m.matches("kartu|card|debit|credit|kredit|3")) return 3;
        return null;
    }

    private static String paymentName(Integer paymentId) {
        return switch (paymentId) {
            case 1 -> "Tunai";
            case 2 -> "QRIS";
            case 3 -> "Kartu";
            default -> "paymentId=" + paymentId;
        };
    }

    // Pulls the order id out of the POST /order response, handling the RestAPIResponse data wrapper.
    private Integer extractOrderId(String json) {
        if (json == null) return null;
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data != null && data.has("data") && data.get("data").isObject()) data = data.get("data");
            long id = data != null ? data.path("id").asLong(0) : 0;
            return id == 0 ? null : (int) id;
        } catch (Exception e) {
            log.warn("extractOrderId failed: {}", e.getMessage());
            return null;
        }
    }

    // Builds a short, human-readable checkout summary from the paid-order response.
    private String describePaidOrder(Integer orderId, String payResp, Integer paymentId) {
        StringBuilder sb = new StringBuilder("orderId=" + orderId).append(" payment=").append(paymentName(paymentId));
        if (payResp != null) {
            try {
                JsonNode root = objectMapper.readTree(payResp);
                JsonNode data = root.has("data") ? root.get("data") : root;
                if (data != null && data.has("data") && data.get("data").isObject()) data = data.get("data");
                String orderNo = data != null ? data.path("orderNo").asText(null) : null;
                String grandTotal = data != null ? data.path("grandTotal").asText(null) : null;
                if (orderNo != null && !orderNo.isBlank()) sb.append(" orderNo=").append(orderNo);
                if (grandTotal != null && !grandTotal.isBlank() && !grandTotal.equals("0")) sb.append(" total=").append(grandTotal);
            } catch (Exception e) {
                log.warn("describePaidOrder failed: {}", e.getMessage());
            }
        }
        return sb.toString();
    }

    // Looks up the SKU id for a product if the cart item doesn't already have one.
    private Long resolveSkuId(Long productId) {
        String endpoint = menuProps.getEndpoints().getProductById();
        try {
            String json = menuRestClient.get()
                    .uri(endpoint, productId)
                    .header(ToolConstants.HEADER_X_USERNAME, orderProps.getDefaultUsername())
                    .retrieve().body(String.class);
            if (json == null) return null;
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data != null && data.isArray() && !data.isEmpty()) data = data.get(0);
            if (data != null && data.has("skus") && data.get("skus").isArray() && !data.get("skus").isEmpty()) {
                long sid = data.get("skus").get(0).path("id").asLong(0);
                if (sid != 0) return sid;
            }
        } catch (Exception e) {
            log.warn("resolveSkuId failed productId={}: {}", productId, e.getMessage());
        }
        return null;
    }
}