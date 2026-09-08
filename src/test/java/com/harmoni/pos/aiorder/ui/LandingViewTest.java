package com.harmoni.pos.aiorder.ui;

import com.harmoni.pos.aiorder.ui.component.CustomerGateDialog;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerResponse;
import com.harmoni.pos.customer.application.service.CustomerService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Optional;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static org.mockito.Mockito.when;

/**
 * Browserless integration tests for the {@link LandingView} entry screen:
 * <ul>
 *   <li>an already-registered session is forwarded straight to the {@link OrderView}</li>
 *   <li>an unknown session opens the {@link CustomerGateDialog}</li>
 * </ul>
 *
 * @author Husain Harmoni
 */
class LandingViewTest extends AbstractUiTest {

    private static final CustomerResponse BUDI = new CustomerResponse(1L, "Budi", "08123456789", null, Instant.now(), Instant.now());

    @MockitoBean
    CustomerService customerService;

    @Test
    void existingCustomerIsForwardedToOrderView() {
        when(customerService.exists("sid-1")).thenReturn(true);
        when(customerService.getCustomerResponse("sid-1")).thenReturn(Optional.of(BUDI));

        UI.getCurrent().navigate(LandingView.class, QueryParameters.of("sessionId", "sid-1"));

        _assertOne(OrderView.class);
    }

    @Test
    void unknownCustomerGetsLoginDialog() {
        when(customerService.exists("sid-2")).thenReturn(false);

        UI.getCurrent().navigate(LandingView.class, QueryParameters.of("sessionId", "sid-2"));

        _assertOne(CustomerGateDialog.class);
    }
}