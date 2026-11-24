package me.gordi.mineapi.module.history;

import me.gordi.mineapi.core.time.Durations;
import me.gordi.mineapi.core.data.DataDir;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.data.PlayerDataService;
import me.gordi.mineapi.core.event.PlayerResetEvent;
import me.gordi.mineapi.core.log.DebugLog;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.storage.JsonFiles;
import me.gordi.mineapi.core.time.Zones;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.stats.StatsModule;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Historial diario por jugador: una instantánea por día (valores al final del día) en
 * {@code data/player_history/<uuid>.json}, con retención configurable.
 *
 * <p>Rendimiento: cada {@code history.interval-minutes} (hilo asíncrono) se copian solo los
 * jugadores cuyo {@code last_updated} cambió desde la última pasada; los inactivos no generan
 * E/S. La poda de días antiguos se hace una vez al día.</p>
 */
public final class HistoryModule extends AbstractModule {

    public static final String ID = "history";
    static final String FOLDER = DataDir.NAME + "/player_history";

    /** uuid → last_updated ya escrito (evita reescribir jugadores sin cambios). */
    private final Map<UUID, Long> written = new ConcurrentHashMap<>();
    private File dir;
    private volatile int retentionDays = 90;
    private volatile ZoneId zone = ZoneId.systemDefault();
    private volatile String lastPruneDay = "";

    private record Pending(UUID uuid, String name, long lastUpdated, double[] values) {
    }

    public HistoryModule(MineAPI plugin) {
        super(plugin, ID, StatsModule.ID);
    }

    @Override
    protected void onEnable() {
        dir = new File(plugin.getDataFolder(), FOLDER);
        retentionDays = Math.max(1, Math.min(3650, config().getInt("retention-days", 90)));
        zone = Zones.of(settings().getString("timezone", ""));
        long intervalMs = Math.max(60L, Durations.seconds(config(), 600L, 60, "update-every", "interval-minutes")) * 1000L;
        asyncTimer("snapshot", 30_000L, intervalMs, this::snapshot);
        subscribe(PlayerResetEvent.class, e -> {
            written.remove(e.uuid());
            scheduler().async(() -> file(e.uuid()).delete());
        });
    }

    @Override
    protected void onDisable() {
        snapshot(); // último punto del día (solo jugadores con cambios)
    }

    private File file(UUID uuid) {
        return new File(dir, uuid + ".json");
    }

    /** Escribe el punto de hoy de los jugadores que cambiaron. Hilo asíncrono (o al apagar). */
    synchronized void snapshot() {
        LocalDate today = LocalDate.now(zone);
        long dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli();
        List<Pending> pending = new ArrayList<>();
        plugin.data().forEach(json -> {
            long last = (long) PlayerDataService.num(json, "last_updated");
            if (last < dayStart) return;
            String id = PlayerDataService.str(json, "uuid");
            if (id == null) return;
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                return;
            }
            Long done = written.get(uuid);
            if (done != null && done >= last) return;
            double[] v = new double[StatType.ALL.length];
            for (StatType t : StatType.ALL) v[t.ordinal()] = PlayerDataService.num(json, t.key);
            pending.add(new Pending(uuid, PlayerDataService.str(json, "name"), last, v));
        });
        String day = today.toString();
        String oldest = today.minusDays(retentionDays - 1L).toString();
        for (Pending p : pending) {
            try {
                write(p, day, oldest);
                written.put(p.uuid, p.lastUpdated);
            } catch (IOException e) {
                log().warning("Historial: no se pudo guardar " + p.name + ": " + e.getMessage());
            }
        }
        if (!pending.isEmpty()) plugin.debug().log(DebugLog.Category.STATS, "Historial: " + pending.size() + " jugadores guardados.");
        if (!day.equals(lastPruneDay)) {
            lastPruneDay = day;
            prune(oldest);
        }
    }

    private void write(Pending p, String day, String oldest) throws IOException {
        File f = file(p.uuid);
        JsonObject root = JsonFiles.read(f);
        if (root == null) root = new JsonObject();
        root.addProperty("uuid", p.uuid.toString());
        if (p.name != null) root.addProperty("name", p.name);
        JsonObject days = root.has("days") && root.get("days").isJsonObject() ? root.getAsJsonObject("days") : new JsonObject();
        JsonObject point = new JsonObject();
        for (StatType t : StatType.ALL) point.addProperty(t.key, p.values[t.ordinal()]);
        days.add(day, point);
        root.add("days", HistoryMath.trim(days, oldest));
        JsonFiles.write(f, root, false);
    }

    /** Quita días fuera de la retención en todos los archivos (una vez al día). */
    private void prune(String oldest) {
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        int changed = 0;
        for (File f : files) {
            JsonObject root = JsonFiles.read(f);
            if (root == null || !root.has("days") || !root.get("days").isJsonObject()) continue;
            JsonObject days = root.getAsJsonObject("days");
            JsonObject kept = HistoryMath.trim(days, oldest);
            if (kept.size() == days.size()) continue;
            try {
                if (kept.size() == 0) {
                    if (f.delete()) changed++;
                    continue;
                }
                root.add("days", kept);
                JsonFiles.write(f, root, false);
                changed++;
            } catch (IOException e) {
                log().warning("Historial: no se pudo podar " + f.getName() + ": " + e.getMessage());
            }
        }
        if (changed > 0) plugin.debug().log(DebugLog.Category.STATS, "Historial: " + changed + " archivos podados.");
    }

    // ------------------------------------------------------------------ API para la web y la GUI

    /**
     * Historial de un jugador: {@code {name, days:[{date, values{...}}]}} con los últimos
     * {@code days} días (como mucho la retención). null si no hay datos. Cualquier hilo.
     */
    public JsonObject history(UUID uuid, int days) {
        JsonObject root = JsonFiles.read(file(uuid));
        if (root == null || !root.has("days") || !root.get("days").isJsonObject()) return null;
        String oldest = LocalDate.now(zone).minusDays(Math.max(1, Math.min(days, retentionDays)) - 1L).toString();
        JsonObject kept = HistoryMath.trim(root.getAsJsonObject("days"), oldest);
        JsonArray arr = new JsonArray();
        for (Map.Entry<String, JsonElement> e : kept.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("date", e.getKey());
            o.add("values", e.getValue());
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        if (root.has("name")) out.add("name", root.get("name"));
        out.addProperty("retention_days", retentionDays);
        out.add("days", arr);
        return out;
    }

    public int retentionDays() {
        return retentionDays;
    }
}
