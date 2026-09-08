package com.harmoni.pos.aiorder.ui.component;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Right-aligned chat bubble representing the customer's own message.
 * <p>
 * Renders a green bubble with white text on the right side of the chat,
 * accompanied by a user avatar icon on the far right.
 *
 * @author Husain Harmoni
 */
public class UserMessage extends VerticalLayout {

    /**
     * Creates a user chat bubble with the given message text.
     *
     * @param message the message content to display
     */
    public UserMessage(String message) {
        addClassName("user-message");
        setWidthFull();
        setPadding(false);
        setSpacing(false);

        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setJustifyContentMode(JustifyContentMode.END);
        layout.setDefaultVerticalComponentAlignment(Alignment.START);
        layout.setSpacing(true);

        VerticalLayout messageWrapper = new VerticalLayout();
        messageWrapper.addClassName("message-bubble");
        messageWrapper.addClassName("user-bubble");
        messageWrapper.setPadding(false);
        messageWrapper.setSpacing(false);
        messageWrapper.setMaxWidth("75%");

        Icon avatar = new Icon(VaadinIcon.USER);
        avatar.addClassName("message-avatar");
        avatar.setSize("28px");

        Span text = new Span(message);
        text.addClassName("message-text");
        text.getStyle().set("white-space", "pre-wrap");

        messageWrapper.add(text);

        layout.add(messageWrapper, avatar);
        add(layout);
    }
}
