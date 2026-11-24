package me.gordi.mineapi.core.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimingTest {

    @Test
    void resetKeepsTheSameObjectUsable() {
        TaskScheduler.Timing t = new TaskScheduler.Timing(false);
        t.record(2_000_000L);
        t.record(4_000_000L);
        assertEquals(2, t.runs());
        assertEquals(3.0, t.avgMs(), 1e-9);
        assertEquals(4.0, t.maxMs(), 1e-9);
        t.reset();
        assertEquals(0, t.runs());
        assertEquals(0.0, t.maxMs(), 1e-9);
        t.record(1_000_000L); // una tarea ya programada sigue registrando tras el reinicio
        assertEquals(1, t.runs());
        assertEquals(1.0, t.avgMs(), 1e-9);
    }
}
