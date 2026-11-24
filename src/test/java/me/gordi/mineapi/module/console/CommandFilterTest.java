package me.gordi.mineapi.module.console;

import org.junit.jupiter.api.Test;

import java.util.List;

import static me.gordi.mineapi.module.console.CommandFilter.Verdict.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandFilterTest {

    @Test
    void normalizes() {
        assertEquals("say hola mundo", CommandFilter.normalize("  /Minecraft:Say   hola   mundo "));
        assertEquals("list", CommandFilter.normalize("//LIST"));
        assertNull(CommandFilter.normalize("/"));
        assertNull(CommandFilter.normalize("say a\nop me"));
        assertNull(CommandFilter.normalize("x".repeat(CommandFilter.MAX_LENGTH + 1)));
    }

    @Test
    void denyWinsAndAllowListRestricts() {
        CommandFilter f = new CommandFilter(List.of("say *", "list", "minecraft:tps"), List.of("say *secret*", "op *"));
        assertEquals(ALLOWED, f.check("say hola"));
        assertEquals(ALLOWED, f.check("say"));
        assertEquals(ALLOWED, f.check("/LIST"));
        assertEquals(ALLOWED, f.check("tps"));
        assertEquals(DENIED, f.check("say my SECRET"));
        assertEquals(DENIED, f.check("/minecraft:op Steve"));
        assertEquals(NOT_ALLOWED, f.check("stop"));
        assertEquals(NOT_ALLOWED, f.check("sayx"));
        assertEquals(INVALID, f.check(""));
    }

    @Test
    void emptyAllowMeansEverythingButDenied() {
        CommandFilter f = new CommandFilter(List.of(), List.of("stop", "op *", "deop *"));
        assertEquals(ALLOWED, f.check("weather clear"));
        assertEquals(DENIED, f.check("stop"));
        assertEquals(DENIED, f.check("bukkit:op x"));
    }

    @Test
    void legacyRegexStyleDenyStillBlocks() {
        CommandFilter f = new CommandFilter(List.of(), List.of("op .*", "lp .*"));
        assertEquals(DENIED, f.check("op Steve"));
        assertEquals(DENIED, f.check("lp user Steve permission set *"));
        assertEquals(ALLOWED, f.check("opx"));
    }
}
