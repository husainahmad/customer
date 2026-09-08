package com.harmoni.pos.aiorder.ui.component;

import com.harmoni.pos.aiorder.ui.AbstractComponentTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.junit.jupiter.api.Test;

import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Browserless tests for the {@link UserMessage} chat bubble:
 * <ul>
 *   <li>the message text is rendered inside the {@code message-text} span</li>
 *   <li>the bubble carries the {@code message-bubble} and {@code user-bubble} style classes</li>
 * </ul>
 *
 * @author Husain Harmoni
 */
class UserMessageTest extends AbstractComponentTest {

    @Test
    void rendersMessageText() {
        UserMessage message = new UserMessage("Satu Kopi Susu, ya!");
        UI.getCurrent().add(message);

        Span text = _get(message, Span.class, spec -> spec.withClasses("message-text"));
        assertEquals("Satu Kopi Susu, ya!", text.getText());
    }

    @Test
    void bubbleCarriesUserStyleClasses() {
        UserMessage message = new UserMessage("Terima kasih!");
        UI.getCurrent().add(message);

        VerticalLayout bubble = _get(message, VerticalLayout.class, spec -> spec.withClasses("message-bubble user-bubble"));
        assertNotNull(bubble);
    }
}