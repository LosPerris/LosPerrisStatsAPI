package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LinkStoreTest {

    private final UUID ana = UUID.randomUUID(), bob = UUID.randomUUID();

    @Test
    void linkingIsOneToOne() {
        LinkStore s = new LinkStore();
        s.link(ana, "Ana", "111");
        s.link(bob, "Bob", "111"); // la cuenta de Discord pasa a Bob
        assertNull(s.byUuid(ana));
        assertEquals(bob, s.byDiscord("111").uuid());
        s.link(bob, "Bob", "222"); // Bob cambia de cuenta
        assertNull(s.byDiscord("111"));
        assertEquals(1, s.size());
        assertEquals("222", s.unlinkUuid(bob).discordId());
        assertEquals(0, s.size());
        assertNull(s.unlinkDiscord("222"));
    }

    @Test
    void rolesAndDirtyFlag() {
        LinkStore s = new LinkStore();
        assertFalse(s.isDirty());
        s.setRoles("111", Set.of());
        assertFalse(s.isDirty(), "sin cambios no se marca");
        s.setRoles("111", Set.of("9"));
        assertTrue(s.isDirty());
        assertEquals(Set.of("111"), s.withRoles());
        s.toJson();
        assertFalse(s.isDirty());
        s.setRoles("111", Set.of());
        assertTrue(s.withRoles().isEmpty());
    }

    @Test
    void jsonRoundTripSkipsCorruptEntries() {
        LinkStore s = new LinkStore();
        s.link(ana, "Ana", "111");
        s.setRoles("111", Set.of("55555", "66666"));
        LinkStore copy = LinkStore.fromJson(s.toJson());
        assertEquals("Ana", copy.byDiscord("111").name());
        assertEquals(Set.of("55555", "66666"), copy.roles("111"));

        LinkStore bad = LinkStore.fromJson(JsonParser.parseString(
                "{\"links\":[{\"uuid\":\"nope\",\"discord\":\"1\"},{\"uuid\":\"" + bob + "\",\"discord\":\"2\"}],\"roles\":{\"x\":5}}").getAsJsonObject());
        assertEquals(1, bad.size());
        assertEquals("?", bad.byUuid(bob).name());
        assertTrue(bad.withRoles().isEmpty());
        assertEquals(0, LinkStore.fromJson(null).size());
    }
}
