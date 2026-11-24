package me.gordi.mineapi.core.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UuidMigrationTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    @Test
    void migratesLegacyByName() {
        JsonObject legacy = JsonParser.parseString("""
                {"Steve": {"deaths": 3, "last_updated": 10},
                 "Alex": {"deaths": 1, "uuid": "EC561538-F3FD-461D-AFF5-086B22154BCE"},
                 "Ghost": {"deaths": 0},
                 "broken": 5}
                """).getAsJsonObject();
        UuidMigration.Result r = UuidMigration.migrate(legacy, n -> n.equals("Steve") ? STEVE : null);
        JsonObject players = r.root().getAsJsonObject(UuidMigration.PLAYERS_KEY);
        assertTrue(UuidMigration.isMigrated(r.root()));
        assertEquals(3, r.migrated());
        assertEquals(1, r.guessed());
        assertEquals(3, players.getAsJsonObject(STEVE.toString()).get("deaths").getAsInt());
        assertEquals("Steve", players.getAsJsonObject(STEVE.toString()).get("name").getAsString());
        assertTrue(players.has("ec561538-f3fd-461d-aff5-086b22154bce"));
        JsonObject ghost = players.getAsJsonObject(UuidMigration.offlineUuid("Ghost").toString());
        assertTrue(ghost.get(UuidMigration.GUESSED_KEY).getAsBoolean());
        assertFalse(legacy.getAsJsonObject("Steve").has("uuid"), "el original no se modifica");
    }

    @Test
    void duplicatesKeepNewest() {
        JsonObject legacy = JsonParser.parseString("""
                {"steve": {"deaths": 1, "last_updated": 5},
                 "Steve": {"deaths": 9, "last_updated": 50}}
                """).getAsJsonObject();
        UuidMigration.Result r = UuidMigration.migrate(legacy, n -> STEVE);
        assertEquals(1, r.merged());
        assertEquals(9, r.root().getAsJsonObject("players").getAsJsonObject(STEVE.toString()).get("deaths").getAsInt());
    }

    @Test
    void idempotentAndNullSafe() {
        JsonObject done = UuidMigration.empty();
        assertSame(done, UuidMigration.migrate(done, n -> null).root());
        assertTrue(UuidMigration.isMigrated(UuidMigration.migrate(null, null).root()));
    }

    @Test
    void offlineUuidMatchesMinecraft() {
        // Valor conocido de Minecraft para "Notch" en modo offline
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", UuidMigration.offlineUuid("Notch").toString());
    }
}
