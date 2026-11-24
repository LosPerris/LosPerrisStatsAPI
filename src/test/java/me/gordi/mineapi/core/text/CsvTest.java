package me.gordi.mineapi.core.text;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CsvTest {

    @Test
    void escapesAndNeutralizesFormulas() {
        assertEquals("", Csv.cell(null));
        assertEquals("plain", Csv.cell("plain"));
        assertEquals("\"a,b\"", Csv.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""));
        assertEquals("'=HYPERLINK(1)", Csv.cell("=HYPERLINK(1)"));
        assertEquals("'@cmd", Csv.cell("@cmd"));
        assertEquals("-5", Csv.cell("-5"), "los números negativos no se alteran");
    }

    @Test
    void tableHasLeadingColumnsThenSortedRestWithoutExcluded() {
        JsonObject a = new JsonObject();
        a.addProperty("name", "Steve");
        a.addProperty("deaths", 3);
        a.addProperty("ip_address", "1.2.3.4");
        a.addProperty("blocks_broken", 10.0);
        a.add("nested", new JsonObject());
        JsonObject b = new JsonObject();
        b.addProperty("name", "Alex");
        b.addProperty("zeta", 1.5);
        String csv = Csv.table(List.of(a, b), List.of("name"), Set.of("ip_address"));
        assertTrue(csv.startsWith("\uFEFF"));
        String[] lines = csv.substring(1).split("\r\n");
        assertEquals("name,blocks_broken,deaths,zeta", lines[0]);
        assertEquals("Steve,10,3,", lines[1]);
        assertEquals("Alex,,,1.5", lines[2]);
        assertFalse(csv.contains("1.2.3.4"));
    }
}
