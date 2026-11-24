package me.gordi.mineapi.module.discordwebhook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscordWebhookModuleTest {

    @Test
    void readColor() {
        assertEquals(0xFF0000, DiscordWebhookModule.readColor("#FF0000"));
        assertEquals(0x00FF00, DiscordWebhookModule.readColor("00ff00"));
        assertEquals(123, DiscordWebhookModule.readColor(123));
        assertEquals(5636053, DiscordWebhookModule.readColor("verde"));
        assertEquals(5636053, DiscordWebhookModule.readColor(null));
    }

    @Test
    void retryAfterIsBounded() {
        assertEquals(1500, DiscordWebhookModule.retryAfterMs("1.5"));
        assertEquals(250, DiscordWebhookModule.retryAfterMs("0"));
        assertEquals(60_000, DiscordWebhookModule.retryAfterMs("999"));
        assertEquals(2000, DiscordWebhookModule.retryAfterMs(null));
        assertEquals(2000, DiscordWebhookModule.retryAfterMs("x"));
    }
}
