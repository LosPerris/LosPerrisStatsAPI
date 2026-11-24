package me.gordi.mineapi.core.command;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatOutTest {

    @Test
    void fileNamesAreNotTurnedIntoLinks() {
        for (BaseComponent c : ChatOut.components("§7Ajustes: §fevents.yml §7Guía: docs/es/GUIA.md")) {
            assertNull(c.getClickEvent(), c.toPlainText());
        }
    }

    @Test
    void realUrlsStayClickable() {
        int links = 0;
        for (BaseComponent c : ChatOut.components("§7Web: https://example.com/web")) {
            ClickEvent ev = c.getClickEvent();
            if (ev != null) {
                assertEquals(ClickEvent.Action.OPEN_URL, ev.getAction());
                links++;
            }
        }
        assertEquals(1, links);
        assertNotNull(ChatOut.components(""));
    }
}
