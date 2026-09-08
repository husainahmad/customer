package com.harmoni.pos.aiorder.ui.component;

import com.harmoni.pos.aiorder.ui.AbstractComponentTest;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Browserless tests for the {@link AiMessage} assistant bubble:
 * <ul>
 *   <li>plain text messages are rendered as-is</li>
 *   <li>markdown pipe tables are converted to styled HTML with a header row,
 *       numbered first column and right-aligned price column</li>
 *   <li>{@code **bold**} markdown in table cells becomes {@code <strong>}</li>
 * </ul>
 *
 * @author Husain Harmoni
 */
class AiMessageTest extends AbstractComponentTest {

    @Test
    void plainTextMessageIsDisplayedAsIs() {
        AiMessage message = new AiMessage("Halo, mau pesan kopi hari ini?");
        UI.getCurrent().add(message);

        assertEquals("Halo, mau pesan kopi hari ini?", message.getText());
    }

    @Test
    void markdownTableRendersAsHtmlTable() {
        String markdownTable = """
                Berikut menu kami:

                | No | Menu       | Harga      |
                |----|------------|------------|
                | 1  | Kopi Susu  | Rp 15.000  |
                | 2  | Kopi Hitam | Rp 10.000  |

                Mau pesan yang mana?
                """;

        AiMessage message = new AiMessage(markdownTable);
        UI.getCurrent().add(message);

        String html = message.getText();
        assertTrue(html.contains("<table class=\"menu-table\">"), "expected rendered <table>");
        assertTrue(html.contains("<th>No</th>"), "expected header row");
        assertTrue(html.contains("<th>Menu</th>"), "expected header row");
        assertTrue(html.contains("<td class=\"num-col\">1</td>"), "expected numbered first column");
        assertTrue(html.contains("<td class=\"price-col\">Rp 15.000</td>"), "expected right-aligned price column");
    }

    @Test
    void boldMarkdownInsideTableCellIsConverted() {
        String markdownTable = """
                | No | Menu          | Harga      |
                |----|---------------|------------|
                | 1  | **Kopi Susu** | Rp 15.000  |
                """;

        AiMessage message = new AiMessage(markdownTable);
        UI.getCurrent().add(message);

        String html = message.getText();
        assertTrue(html.contains("<strong>Kopi Susu</strong>"), "expected <strong> around bold text");
    }

    @Test
    void menuTextInsideTableMessageIsKeptAsLine() {
        String markdownTable = """
                Berikut menu kami:

                | No | Menu       | Harga      |
                |----|------------|------------|
                | 1  | Kopi Susu  | Rp 15.000  |
                """;

        AiMessage message = new AiMessage(markdownTable);
        UI.getCurrent().add(message);

        assertTrue(message.getText().contains("Berikut menu kami:"), "intro line should be kept");
    }

    @Test
    void nullMessageRendersEmptyText() {
        AiMessage message = new AiMessage(null);
        UI.getCurrent().add(message);

        assertEquals("", message.getText());
    }

    @Test
    void plainMessageIsNotTreatedAsTable() {
        AiMessage message = new AiMessage("Mau pesan apa hari ini?");
        UI.getCurrent().add(message);

        assertFalse(message.getText().contains("<table"));
    }
}