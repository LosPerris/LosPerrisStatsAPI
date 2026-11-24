package me.gordi.mineapi.module.history;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class HistoryMathTest {

    @Test
    void trimsOldDaysAndSorts() {
        JsonObject days = new JsonObject();
        days.add("2026-10-29", new JsonObject());
        days.add("2026-07-01", new JsonObject());
        days.add("2026-10-01", new JsonObject());
        days.add("2026-08-01", new JsonObject());
        JsonObject out = HistoryMath.trim(days, "2026-08-01");
        assertEquals(java.util.List.of("2026-08-01", "2026-10-01", "2026-10-29"), new ArrayList<>(out.keySet()));
    }
}
