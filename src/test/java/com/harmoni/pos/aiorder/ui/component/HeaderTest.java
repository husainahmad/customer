package com.harmoni.pos.aiorder.ui.component;

import com.harmoni.pos.aiorder.ui.AbstractComponentTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;

import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Browserless tests for the {@link Header} top app bar:
 * <ul>
 *   <li>the store name is displayed, with the configured value taking precedence</li>
 *   <li>a blank store name falls back to the default "Kopi Harmoni"</li>
 *   <li>the back navigation button is always present</li>
 * </ul>
 *
 * @author Husain Harmoni
 */
class HeaderTest extends AbstractComponentTest {

    @Test
    void displaysConfiguredStoreName() {
        Header header = new Header("Kopi Nusantara");
        UI.getCurrent().add(header);

        Span storeName = _get(header, Span.class, spec -> spec.withClasses("store-name"));
        assertEquals("Kopi Nusantara", storeName.getText());
    }

    @Test
    void blankStoreNameFallsBackToDefault() {
        Header header = new Header("   ");
        UI.getCurrent().add(header);

        Span storeName = _get(header, Span.class, spec -> spec.withClasses("store-name"));
        assertEquals("Kopi Harmoni", storeName.getText());
    }

    @Test
    void backButtonIsPresentWithAccessibleLabel() {
        Header header = new Header("Kopi Harmoni");
        UI.getCurrent().add(header);

        Button backButton = _get(header, Button.class, spec -> spec.withAttribute("aria-label", "Kembali"));
        assertNotNull(backButton);
    }
}