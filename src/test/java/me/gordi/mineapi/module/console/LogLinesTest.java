package me.gordi.mineapi.module.console;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogLinesTest {

    @Test
    void stripsColorsMasksSecretIpsAndPatterns() {
        LogLines l = new LogLines("supersecretkey", true, List.of("token=\\w+", "(["));
        String out = l.clean("\u001B[31m§aSteve joined from 192.168.10.20 key supersecretkey token=abc123\u001B[0m");
        assertEquals("Steve joined from 192.168.x.x key **** ****", out);
    }

    @Test
    void shortSecretIgnoredAndLongLinesCut() {
        LogLines l = new LogLines("abc", false, null);
        assertEquals("abc 1.2.3.4", l.clean("abc 1.2.3.4"));
        assertEquals("", l.clean(null));
        String longLine = l.clean("x".repeat(LogLines.MAX_LINE + 50));
        assertEquals(LogLines.MAX_LINE + 1, longLine.length());
        assertTrue(longLine.endsWith("…"));
    }
}
