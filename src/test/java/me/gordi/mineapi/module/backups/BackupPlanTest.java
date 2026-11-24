package me.gordi.mineapi.module.backups;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackupPlanTest {

    private static final ZoneId Z = ZoneId.of("Europe/Madrid");

    @Test
    void namesAndTime() {
        ZonedDateTime t = ZonedDateTime.of(2026, 10, 29, 4, 5, 6, 0, Z);
        assertEquals("data-20261029-040506.zip", BackupPlan.fileName(t));
        assertTrue(BackupPlan.isBackupName("data-20261029-040506.zip"));
        assertTrue(BackupPlan.isBackupName("data-20261029-040506-2.zip"));
        assertFalse(BackupPlan.isBackupName("players.json"));
        assertFalse(BackupPlan.isBackupName("data-2026.zip"));
        assertEquals(LocalTime.of(3, 30), BackupPlan.parseTime("3:30"));
        assertEquals(LocalTime.of(4, 0), BackupPlan.parseTime("25:99"));
        assertEquals(LocalTime.of(4, 0), BackupPlan.parseTime(null));
    }

    @Test
    void nextRunIsStrictlyAfterNow() {
        ZonedDateTime before = ZonedDateTime.of(2026, 10, 29, 3, 0, 0, 0, Z);
        assertEquals(ZonedDateTime.of(2026, 10, 29, 4, 0, 0, 0, Z), BackupPlan.nextRun(before, LocalTime.of(4, 0)));
        ZonedDateTime exactly = ZonedDateTime.of(2026, 10, 29, 4, 0, 0, 0, Z);
        assertEquals(ZonedDateTime.of(2026, 10, 30, 4, 0, 0, 0, Z), BackupPlan.nextRun(exactly, LocalTime.of(4, 0)));
    }

    @Test
    void keepsNewestAndIgnoresForeignFiles() {
        List<String> names = new ArrayList<>();
        for (int d = 1; d <= 9; d++) names.add("data-2026100" + d + "-040000.zip");
        names.add("manual.zip");
        List<String> del = BackupPlan.toDelete(names, 7);
        assertEquals(List.of("data-20261002-040000.zip", "data-20261001-040000.zip"), del);
        assertTrue(BackupPlan.toDelete(names.subList(0, 3), 7).isEmpty());
        assertEquals(8, BackupPlan.toDelete(names, 0).size(), "keep mínimo 1");
    }
}
