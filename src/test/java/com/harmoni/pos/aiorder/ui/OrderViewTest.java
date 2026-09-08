package com.harmoni.pos.aiorder.ui;

import com.harmoni.pos.aiorder.service.OrderingService;
import com.harmoni.pos.aiorder.ui.component.AiMessage;
import com.harmoni.pos.aiorder.ui.component.ChatInput;
import com.harmoni.pos.aiorder.ui.component.Header;
import com.harmoni.pos.aiorder.ui.component.TypingIndicator;
import com.harmoni.pos.aiorder.ui.component.UserMessage;
import com.harmoni.pos.customer.adapter.in.web.dto.CustomerResponse;
import com.harmoni.pos.customer.application.service.CustomerService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assert;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertEnabled;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertNone;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertOne;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._find;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Browserless integration tests for the {@link OrderView} chat screen:
 * <ul>
 *   <li>an existing customer sees the personalized greeting and an enabled composer</li>
 *   <li>sending a message streams the AI reply into the conversation</li>
 *   <li>the typing indicator is shown while waiting and removed afterwards</li>
 * </ul>
 * <p>
 * The heavy HTTP client services are replaced with Mockito mocks; only Spring bean
 * wiring and the navigation flow are exercised end-to-end.
 *
 * @author Husain Harmoni
 */
class OrderViewTest extends AbstractUiTest {

    private static final CustomerResponse BUDI = new CustomerResponse(1L, "Budi", "08123456789", null, Instant.now(), Instant.now());

    @MockitoBean
    CustomerService customerService;

    @MockitoBean
    OrderingService orderingService;

    @Test
    void existingCustomerSeesGreetingHeaderAndEnabledComposer() {
        when(customerService.exists("sid-1")).thenReturn(true);
        when(customerService.getCustomerResponse("sid-1")).thenReturn(Optional.of(BUDI));

        UI.getCurrent().navigate(OrderView.class, QueryParameters.of("sessionId", "sid-1"));

        Header header = _get(Header.class);
        Span storeName = _get(header, Span.class, spec -> spec.withClasses("store-name"));
        assertEquals("Kopi Harmoni", storeName.getText());

        _assertOne(ChatInput.class);
        _assertEnabled(_get(ChatInput.class));

        List<AiMessage> greetings = _find(AiMessage.class);
        assertEquals(1, greetings.size());
        assertTrue(greetings.get(0).getText().contains("Halo Budi"));
    }

    @Test
    void sendingMessageStreamsAiReplyIntoConversation() {
        when(customerService.exists("sid-2")).thenReturn(true);
        when(customerService.getCustomerResponse("sid-2")).thenReturn(Optional.of(BUDI));
        when(orderingService.streamMessage(anyString(), anyString()))
                .thenReturn(Flux.just("Halo Budi! Ingin pesan kopi apa hari ini?"));

        UI.getCurrent().navigate(OrderView.class, QueryParameters.of("sessionId", "sid-2"));

        _setValue(_get(TextArea.class), "Halo, ada kopi susu?");
        _click(_get(Button.class, spec -> spec.withClasses("send-button")));

        // user message added, typing indicator gone, AI reply rendered
        _assertOne(UserMessage.class);
        _assertNone(TypingIndicator.class);
        _assert(AiMessage.class, 2);
        _assertEnabled(_get(ChatInput.class));
        _assertEnabled(_get(Button.class, spec -> spec.withClasses("send-button")));

        List<AiMessage> replies = _find(AiMessage.class);
        assertTrue(replies.get(1).getText().contains("Halo Budi! Ingin pesan kopi apa hari ini?"));
    }
}