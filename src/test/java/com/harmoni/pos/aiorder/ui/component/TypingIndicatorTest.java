package com.harmoni.pos.aiorder.ui.component;

import com.harmoni.pos.aiorder.ui.AbstractComponentTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assert;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Browserless tests for the {@link TypingIndicator}:
 * <ul>
 *   <li>renders exactly three animated dot elements</li>
 *   <li>shows the "mengetik" label</li>
 * </ul>
 *
 * @author Husain Harmoni
 */
class TypingIndicatorTest extends AbstractComponentTest {

    @Test
    void rendersThreeAnimatedDots() {
        TypingIndicator indicator = new TypingIndicator();
        UI.getCurrent().add(indicator);

        _assert(indicator, Span.class, 3, spec -> spec.withClasses("typing-dot"));
    }

    @Test
    void rendersMengetikLabel() {
        TypingIndicator indicator = new TypingIndicator();
        UI.getCurrent().add(indicator);

        Span label = _get(indicator, Span.class, spec -> spec.withClasses("typing-text"));
        assertEquals("mengetik", label.getText());
    }
}