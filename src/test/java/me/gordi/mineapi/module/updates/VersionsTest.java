package me.gordi.mineapi.module.updates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionsTest {

    @Test
    void comparesSemanticVersions() {
        assertEquals(0, Versions.compare("1.3", "v1.3.0"));
        assertTrue(Versions.isNewer("1.10", "1.9"));
        assertTrue(Versions.isNewer("1.3.1", "1.3"));
        assertTrue(Versions.isNewer("1.3-V3", "1.3-V2"));
        assertTrue(Versions.isNewer("1.4", "1.3-V2"));
        assertTrue(Versions.isNewer("1.3-V2", "1.3"));
        assertFalse(Versions.isNewer("1.3", "1.3-V2"));
    }

    @Test
    void preReleasesLose() {
        assertTrue(Versions.isNewer("2.0", "2.0-beta"));
        assertTrue(Versions.isNewer("2.0-rc1", "2.0-beta3"));
        assertFalse(Versions.isNewer("2.0-SNAPSHOT", "2.0"));
        assertTrue(Versions.isNewer("2.0.1", "2.0-beta"));
        assertFalse(Versions.isNewer(null, "1.0"));
        assertFalse(Versions.isNewer(" ", "1.0"));
    }
}
