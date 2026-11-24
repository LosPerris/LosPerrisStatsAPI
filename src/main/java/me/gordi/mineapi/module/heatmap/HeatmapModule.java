package me.gordi.mineapi.module.heatmap;

import me.gordi.mineapi.core.time.Durations;
import me.gordi.mineapi.core.data.DataDir;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.storage.JsonFiles;
import me.gordi.mineapi.core.time.Zones;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;

/**
 * Mapa de calor de actividad (día × hora, media de conectados). Toma una muestra cada
 * {@code heatmap.sample-minutes} (solo cuenta jugadores: coste despreciable) y guarda en
 * data/heatmap.json. La primera vez se rellena con el historial del dashboard.
 */
public final class HeatmapModule extends AbstractModule {

    public static final String ID = "heatmap";

    private Heatmap heatmap;
    private ZoneId zone;
    private int sinceSave;

    public HeatmapModule(MineAPI plugin) {
        super(plugin, ID);
    }

    @Override
    protected void onEnable() {
        zone = Zones.of(settings().getString("timezone", ""));
        heatmap = new Heatmap(Math.max(1, Math.min(52, config().getInt("weeks", 8))));
        File file = file();
        if (file.exists()) heatmap.load(JsonFiles.read(file));
        else seedFromHistory();
        long ticks = Math.max(60L, Durations.seconds(config(), 300L, 60, "sample-every", "sample-minutes")) * 20L;
        syncTimer("sample", ticks, ticks, this::sample);
    }

    @Override
    protected void onDisable() {
        save();
        heatmap = null;
    }

    private void sample() {
        int online = Bukkit.getOnlinePlayers().size();
        synchronized (this) {
            heatmap.sample(System.currentTimeMillis(), zone, online);
        }
        if (++sinceSave >= 6) {
            sinceSave = 0;
            scheduler().async(this::save);
        }
    }

    /** Para la API: medias, pico y semanas usadas. */
    public synchronized JsonObject toApi() {
        JsonObject o = heatmap.toApi();
        o.addProperty("timezone", zone.getId());
        o.addProperty("sample_minutes", Math.max(1, config().getInt("sample-minutes", 5)));
        return o;
    }

    private void seedFromHistory() {
        File history = new File(plugin.getDataFolder(), DataDir.NAME + "/history.json");
        if (!history.exists()) return;
        try {
            JsonElement el = JsonParser.parseString(Files.readString(history.toPath(), StandardCharsets.UTF_8));
            if (!el.isJsonArray()) return;
            int n = 0;
            for (JsonElement p : el.getAsJsonArray()) {
                if (!p.isJsonObject()) continue;
                JsonObject o = p.getAsJsonObject();
                if (!o.has("t") || !o.has("online")) continue;
                heatmap.sample(o.get("t").getAsLong(), zone, o.get("online").getAsInt());
                n++;
            }
            if (n > 0) log().info("Mapa de calor: " + n + " muestras importadas del historial del dashboard.");
        } catch (Exception e) {
            log().warning("No se pudo importar data/history.json al mapa de calor: " + e.getMessage());
        }
    }

    private void save() {
        JsonObject json;
        synchronized (this) {
            if (heatmap == null) return;
            json = heatmap.toJson();
        }
        try {
            JsonFiles.write(file(), json, false);
        } catch (IOException e) {
            log().warning("No se pudo guardar heatmap.json: " + e.getMessage());
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), DataDir.NAME + "/heatmap.json");
    }
}
