package com.harmoni.pos.customer.ai.tool;

import com.harmoni.pos.customer.cart.CustomerCartService;
import com.harmoni.pos.customer.cart.dto.CartItemResponse;
import com.harmoni.pos.customer.cart.dto.CartResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartToolsTest {

    @Mock
    private CustomerCartService cartService;

    @InjectMocks
    private CartTools cartTools;

    @Test
    void addToOrder_passesSkuIdToCart() {
        CartResponse cart = new CartResponse(
                List.of(new CartItemResponse(15L, 27L, "Kopi Tubruk", new BigDecimal("7000"), 2, new BigDecimal("14000"))),
                2, new BigDecimal("14000"));
        when(cartService.addToCart(42L, 15L, 27L, 2)).thenReturn(cart);

        String result = cartTools.addToOrder(42L, 15L, 27L, 2);

        assertThat(result).startsWith(ToolConstants.PREFIX_ITEM_ADDED);
        ArgumentCaptor<Long> skuCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cartService).addToCart(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(15L), skuCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(2));
        assertThat(skuCaptor.getValue()).isEqualTo(27L);
    }

    @Test
    void addToOrder_nullProductId_returnsCartError() {
        String result = cartTools.addToOrder(42L, null, 27L, 1);

        assertThat(result).startsWith(ToolConstants.PREFIX_CART_ERROR);
    }

    @Test
    void addToOrder_nullSessionId_returnsCartError() {
        String result = cartTools.addToOrder(null, 15L, 27L, 1);

        assertThat(result).startsWith(ToolConstants.PREFIX_CART_ERROR);
    }

    @Test
    void addToOrder_nullQuantity_defaultsToOne() {
        CartResponse cart = new CartResponse(
                List.of(new CartItemResponse(15L, 27L, "Kopi Tubruk", new BigDecimal("7000"), 1, new BigDecimal("7000"))),
                1, new BigDecimal("7000"));
        when(cartService.addToCart(42L, 15L, 27L, 1)).thenReturn(cart);

        String result = cartTools.addToOrder(42L, 15L, 27L, null);

        assertThat(result).startsWith(ToolConstants.PREFIX_ITEM_ADDED);
        verify(cartService).addToCart(42L, 15L, 27L, 1);
    }
}