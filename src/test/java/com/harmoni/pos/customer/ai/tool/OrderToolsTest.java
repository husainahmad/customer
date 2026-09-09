package com.harmoni.pos.customer.ai.tool;

import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.cart.CustomerCartService;
import com.harmoni.pos.customer.cart.dto.CartItemResponse;
import com.harmoni.pos.customer.cart.dto.CartResponse;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import com.harmoni.pos.customer.config.OrderServiceProperties;
import com.harmoni.pos.customer.domain.model.CustomerSession;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import com.harmoni.pos.customer.domain.model.CustomerSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class OrderToolsTest {

    private static final String ORDER_JSON = "{\"data\":{\"id\":7,\"orderNo\":\"ORD-7\",\"grandTotal\":14000}}";

    @Mock
    private CustomerCartService cartService;

    @Mock
    private CustomerSessionRepository sessionRepository;

    private MockRestServiceServer orderServer;
    private OrderTools orderTools;

    @BeforeEach
    void setUp() {
        OrderServiceProperties orderProps = new OrderServiceProperties();
        MenuServiceProperties menuProps = new MenuServiceProperties();
        orderProps.getEndpoints().setCreateOrder("/api/v1/order");
        RestClient.Builder builder = RestClient.builder();
        orderServer = MockRestServiceServer.bindTo(builder).build();
        orderTools = new OrderTools(builder.build(), RestClient.create(), cartService, sessionRepository, menuProps, orderProps);
    }

    private CartResponse cartWithItems() {
        return new CartResponse(
                List.of(new CartItemResponse(15L, 27L, "Kopi Tubruk", new BigDecimal("7000"), 2, new BigDecimal("14000"))),
                2, new BigDecimal("14000"));
    }

    private void stubSession(Long customerId) {
        CustomerSession session = new CustomerSession(42L, customerId, "tok-abc", CustomerSessionSource.AI_CHAT,
                CustomerSessionStatus.OPEN, Instant.now(), Instant.now());
        when(sessionRepository.findById(42L)).thenReturn(Optional.of(session));
    }

    @Test
    void confirmOrder_success_createsOrderThenPaysAndClearsCart() {
        stubSession(555L);
        when(cartService.getCart(42L)).thenReturn(cartWithItems());
        orderServer.expect(requestTo("/api/v1/order")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.customerId", org.hamcrest.Matchers.equalTo(555)))
                .andExpect(jsonPath("$.customerName", org.hamcrest.Matchers.equalTo("Budi")))
                .andExpect(jsonPath("$.storeId", org.hamcrest.Matchers.equalTo(1)))
                .andRespond(withSuccess(ORDER_JSON, MediaType.APPLICATION_JSON));
        orderServer.expect(requestTo("/api/v1/order")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(ORDER_JSON, MediaType.APPLICATION_JSON));

        String result = orderTools.confirmOrder(42L, "Budi", "qris", null);

        assertThat(result).startsWith(ToolConstants.PREFIX_ORDER_CREATED);
        assertThat(result).contains("orderId=7");
        assertThat(result).contains("payment=QRIS");
        verify(cartService).clearCart(42L);
        orderServer.verify();
    }

    @Test
    void confirmOrder_withNote_includesRemarkInCreateRequest() {
        stubSession(555L);
        when(cartService.getCart(42L)).thenReturn(cartWithItems());
        orderServer.expect(requestTo("/api/v1/order")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.remark", org.hamcrest.Matchers.equalTo("es extra")))
                .andExpect(jsonPath("$.customerId", org.hamcrest.Matchers.equalTo(555)))
                .andExpect(jsonPath("$.customerName", org.hamcrest.Matchers.equalTo("Budi")))
                .andRespond(withSuccess(ORDER_JSON, MediaType.APPLICATION_JSON));
        orderServer.expect(requestTo("/api/v1/order")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(ORDER_JSON, MediaType.APPLICATION_JSON));

        String result = orderTools.confirmOrder(42L, "Budi", "qris", "es extra");

        assertThat(result).startsWith(ToolConstants.PREFIX_ORDER_CREATED);
        verify(cartService).clearCart(42L);
        orderServer.verify();
    }

    @Test
    void confirmOrder_emptyCart_doesNotClearCart() {
        stubSession(555L);
        when(cartService.getCart(42L))
                .thenReturn(new CartResponse(List.of(), 0, BigDecimal.ZERO));

        String result = orderTools.confirmOrder(42L, "Budi", "tunai", null);

        assertThat(result).startsWith(ToolConstants.PREFIX_CART_EMPTY);
        verify(cartService, never()).clearCart(anyLong());
    }

    @Test
    void confirmOrder_unknownPaymentMethod_returnsOrderErrorAndDoesNotClearCart() {
        String result = orderTools.confirmOrder(42L, "Budi", "bank transfer", null);

        assertThat(result).startsWith(ToolConstants.PREFIX_ORDER_ERROR);
        verify(cartService, never()).clearCart(anyLong());
    }

    @Test
    void confirmOrder_createOrderFails_doesNotClearCart() {
        stubSession(555L);
        when(cartService.getCart(42L)).thenReturn(cartWithItems());
        orderServer.expect(requestTo("/api/v1/order")).andRespond(withServerError());

        String result = orderTools.confirmOrder(42L, "Budi", "tunai", null);

        assertThat(result).startsWith(ToolConstants.PREFIX_ORDER_ERROR);
        verify(cartService, never()).clearCart(anyLong());
        orderServer.verify();
    }

    @Test
    void confirmOrder_paymentFails_doesNotClearCart() {
        stubSession(555L);
        when(cartService.getCart(42L)).thenReturn(cartWithItems());
        orderServer.expect(requestTo("/api/v1/order")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(ORDER_JSON, MediaType.APPLICATION_JSON));
        orderServer.expect(requestTo("/api/v1/order")).andExpect(method(HttpMethod.PUT))
                .andRespond(withServerError());

        String result = orderTools.confirmOrder(42L, "Budi", "kartu", null);

        assertThat(result).startsWith(ToolConstants.PREFIX_ORDER_ERROR);
        verify(cartService, never()).clearCart(anyLong());
        orderServer.verify();
    }
}