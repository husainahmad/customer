package com.harmoni.pos.aiorder.ui.component;

import com.harmoni.pos.aiorder.ui.AbstractComponentTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static com.github.mvysny.kaributesting.v10.LocatorJ._assertDisabled;
import static com.github.mvysny.kaributesting.v10.LocatorJ._assertEnabled;
import static com.github.mvysny.kaributesting.v10.LocatorJ._click;
import static com.github.mvysny.kaributesting.v10.LocatorJ._get;
import static com.github.mvysny.kaributesting.v10.LocatorJ._setValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless tests for the {@link ChatInput} message composer:
 * <ul>
 *   <li>initial state (placeholder, enabled controls)</li>
 *   <li>send via button click fires the listener with a trimmed message and clears the field</li>
 *   <li>blank messages are ignored</li>
 *   <li>the waiting-for-response state disables the controls</li>
 * </ul>
 *
 * @author Husain Harmoni
 */
class ChatInputTest extends AbstractComponentTest {

    private ChatInput chatInput;

    @BeforeEach
    void attach() {
        chatInput = new ChatInput();
        UI.getCurrent().add(chatInput);
    }

    @Test
    void initialStateShowsPlaceholderAndEnabledControls() {
        TextArea area = _get(TextArea.class);
        assertEquals("Tulis pesan...", area.getPlaceholder());
        _assertEnabled(area);
        _assertEnabled(_get(Button.class, spec -> spec.withClasses("send-button")));
    }

    @Test
    void clickSendFiresListenerTrimsMessageAndClearsField() {
        AtomicReference<String> sent = new AtomicReference<>();
        chatInput.setSendListener(sent::set);

        _setValue(_get(TextArea.class), "  Halo barista!  ");
        _click(_get(Button.class, spec -> spec.withClasses("send-button")));

        assertEquals("Halo barista!", sent.get());
        assertEquals("", _get(TextArea.class).getValue());
    }

    @Test
    void blankMessageDoesNotTriggerSend() {
        AtomicReference<String> sent = new AtomicReference<>();
        chatInput.setSendListener(sent::set);

        _setValue(_get(TextArea.class), "    ");
        _click(_get(Button.class, spec -> spec.withClasses("send-button")));

        assertNull(sent.get());
        assertTrue(_get(TextArea.class).getValue().isBlank());
    }

    @Test
    void waitingForResponseDisablesControls() {
        chatInput.setWaitingForResponse(true);
        _assertDisabled(_get(TextArea.class));
        _assertDisabled(_get(Button.class, spec -> spec.withClasses("send-button")));
    }

    @Test
    void clearingWaitingStateReEnablesControls() {
        chatInput.setWaitingForResponse(true);
        chatInput.setWaitingForResponse(false);
        _assertEnabled(_get(TextArea.class));
        _assertEnabled(_get(Button.class, spec -> spec.withClasses("send-button")));
    }
}