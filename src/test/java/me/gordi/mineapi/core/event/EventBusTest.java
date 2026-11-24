package me.gordi.mineapi.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    record Ping(int n) { }

    @Test
    void deliversIsolatesAndUnsubscribes() {
        Logger l = Logger.getAnonymousLogger();
        l.setUseParentHandlers(false);
        EventBus bus = new EventBus(l);
        Object ownerA = new Object(), ownerB = new Object();
        List<Integer> got = new ArrayList<>();
        assertFalse(bus.hasSubscribers(Ping.class));
        bus.subscribe(ownerA, Ping.class, p -> { throw new RuntimeException("falla"); });
        bus.subscribe(ownerB, Ping.class, p -> got.add(p.n()));
        bus.post(new Ping(1));
        assertEquals(List.of(1), got, "un suscriptor que falla no corta a los demás");
        bus.unsubscribeAll(ownerB);
        bus.post(new Ping(2));
        assertEquals(List.of(1), got);
        bus.post("sin suscriptores");
    }
}
