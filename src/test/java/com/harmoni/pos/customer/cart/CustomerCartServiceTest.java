package com.harmoni.pos.customer.cart;

import com.harmoni.pos.customer.application.port.out.CustomerSessionRepository;
import com.harmoni.pos.customer.cart.dto.CartItemResponse;
import com.harmoni.pos.customer.cart.dto.CartResponse;
import com.harmoni.pos.customer.cart.dto.CustomizationGroupSelection;
import com.harmoni.pos.customer.cart.dto.CustomizationOptionSelection;
import com.harmoni.pos.customer.config.MenuServiceProperties;
import com.harmoni.pos.customer.domain.model.CustomerSession;
import com.harmoni.pos.customer.domain.model.CustomerSessionSource;
import com.harmoni.pos.customer.domain.model.CustomerSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pricing behaviour of the in-memory cart: a line must total
 * {@code (SKU price + option surcharge) x quantity}, never the SKU price alone.
 */
class CustomerCartServiceTest {

    private static final long SESSION_ID = 42L;
    private static final Long PRODUCT_ID = 15L;
    private static final Long SKU_ID = 27L;

    private CustomerCartService cartService;
    private RestClient menuRestClient;

    @BeforeEach
    void setUp() {
        CustomerSessionRepository sessionRepository = mock(CustomerSessionRepository.class);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(openSession()));

        menuRestClient = mock(RestClient.class);
        MenuServiceProperties menuProps = mock(MenuServiceProperties.class);
        when(menuProps.getEndpoints()).thenReturn(new MenuServiceProperties.Endpoints());

        cartService = new CustomerCartService(sessionRepository, menuRestClient, menuProps);
    }

    @Test
    void addToCart_shouldAddTheOptionSurchargeToTheLineTotal() {
        stubProduct(productWithCustomizations(optionTierPrice(1001L, "4000")));

        CartResponse cart = cartService.addToCart(SESSION_ID, PRODUCT_ID, SKU_ID, 2,
                List.of(group(100L, option(1001L, 1))));

        CartItemResponse line = cart.items().get(0);
        assertAmount("20000", line.price());
        assertAmount("4000", line.surcharge());
        assertAmount("24000", line.unitPrice());
        assertAmount("48000", line.totalPrice());
        assertAmount("48000", cart.totalPrice());
    }

    @Test
    void addToCart_shouldChargeARepeatedOptionOncePerUnit() {
        stubProduct(productWithCustomizations(optionTierPrice(1001L, "1500")));

        CartResponse cart = cartService.addToCart(SESSION_ID, PRODUCT_ID, SKU_ID, 3,
                List.of(group(100L, option(1001L, 2))));

        CartItemResponse line = cart.items().get(0);
        assertAmount("20000", line.price());
        assertAmount("3000", line.surcharge());
        assertAmount("23000", line.unitPrice());
        assertAmount("69000", line.totalPrice());
    }

    @Test
    void addToCart_shouldSumTheSurchargesOfSeveralGroups() {
        stubProduct(productWithCustomizations(
                optionTierPrice(1001L, "4000"),
                optionTierPrice(1002L, "2500")));

        CartResponse cart = cartService.addToCart(SESSION_ID, PRODUCT_ID, SKU_ID, 1,
                List.of(group(100L, option(1001L, 1)), group(200L, option(1002L, 1))));

        CartItemResponse line = cart.items().get(0);
        assertAmount("6500", line.surcharge());
        assertAmount("26500", line.totalPrice());
        assertAmount("26500", cart.totalPrice());
    }

    @Test
    void addToCart_shouldKeepAPlainLineApartFromACustomizedOne() {
        stubProduct(productWithCustomizations(optionTierPrice(1001L, "4000")));

        cartService.addToCart(SESSION_ID, PRODUCT_ID, SKU_ID, 1, List.of());
        CartResponse cart = cartService.addToCart(SESSION_ID, PRODUCT_ID, SKU_ID, 1,
                List.of(group(100L, option(1001L, 1))));

        assertThat(cart.items()).hasSize(2);
        assertAmount("20000", cart.items().get(0).totalPrice());
        assertAmount("24000", cart.items().get(1).totalPrice());
        assertAmount("44000", cart.totalPrice());
    }

    @Test
    void addToCart_shouldRepriceAnExistingLineWhenRepeatedWithTheSameChoices() {
        stubProduct(productWithCustomizations(optionTierPrice(1001L, "4000")));

        cartService.addToCart(SESSION_ID, PRODUCT_ID, SKU_ID, 1, List.of(group(100L, option(1001L, 1))));
        CartResponse cart = cartService.addToCart(SESSION_ID, PRODUCT_ID, SKU_ID, 2,
                List.of(group(100L, option(1001L, 1))));

        assertThat(cart.items()).hasSize(1);
        CartItemResponse line = cart.items().get(0);
        assertThat(line.quantity()).isEqualTo(3);
        assertAmount("4000", line.surcharge());
        assertAmount("72000", line.totalPrice());
        assertAmount("72000", cart.totalPrice());
    }

    @Test
    void addToCart_shouldPriceAPlainLineAtTheSkuPriceWhenThereAreNoOptions() {
        stubProduct(productWithCustomizations());

        CartResponse cart = cartService.addToCart(SESSION_ID, PRODUCT_ID, SKU_ID, 2, List.of());

        CartItemResponse line = cart.items().get(0);
        assertAmount("0", line.surcharge());
        assertAmount("20000", line.unitPrice());
        assertAmount("40000", line.totalPrice());
    }

    // ----------------------------------------------------------------------- fixtures

    private static CustomerSession openSession() {
        return new CustomerSession(SESSION_ID, 7L, "token", CustomerSessionSource.WEB_CHAT,
                CustomerSessionStatus.OPEN, Instant.now(), Instant.now());
    }

    private static CustomizationGroupSelection group(Long customizationId, CustomizationOptionSelection... options) {
        return new CustomizationGroupSelection(customizationId, List.of(options));
    }

    private static CustomizationOptionSelection option(Long optionId, int quantity) {
        return new CustomizationOptionSelection(optionId, quantity);
    }

    private static String optionTierPrice(Long optionId, String price) {
        return """
                { "id": %d, "name": "Option %d", "tierPrices": [ { "id": 1, "tierId": 1, "price": %s } ] }
                """.formatted(optionId, optionId, price);
    }

    /** A product payload with one SKU priced 20000 and the given option nodes. */
    private static String productWithCustomizations(String... options) {
        return """
                {
                  "data": {
                    "id": 15,
                    "name": "Kopi Tubruk",
                    "skus": [ { "id": 27, "name": "Regular", "tierPrice": { "price": 20000 } } ],
                    "customizations": [
                      {
                        "customizationId": 100,
                        "name": "Size",
                        "selectionType": "SINGLE",
                        "options": [ %s ]
                      }
                    ]
                  }
                }
                """.formatted(String.join(",", options));
    }

    @SuppressWarnings("rawtypes")
    private void stubProduct(String payload) {
        RestClient.RequestHeadersUriSpec get = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(get.uri(any(Function.class))).thenReturn(get);
        when(get.uri(any(String.class), any(Object[].class))).thenReturn(get);
        when(get.header(any(String.class), any(String.class))).thenReturn(get);
        when(get.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(payload);

        when(menuRestClient.get()).thenReturn(get);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertThat(actual).isNotNull();
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
