package me.gordi.mineapi.module.updates;

import me.gordi.mineapi.core.time.Durations;
import me.gordi.mineapi.core.module.Perms;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.log.DebugLog;
import me.gordi.mineapi.core.module.AbstractModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Buscador de actualizaciones asíncrono (GitHub releases, Modrinth o Spigot). Sin
 * {@code updates.source}/{@code updates.id} no hace nada (ni red ni temporizadores).
 */
public final class UpdatesModule extends AbstractModule implements Listener {

    public static final String ID = "updates";

    private HttpClient http;
    private String source;
    private String project;
    private volatile String latest;
    private volatile String url;
    private volatile String error;
    private volatile long checkedAt;

    public UpdatesModule(MineAPI plugin) {
        super(plugin, ID);
    }

    @Override
    protected void onEnable() {
        source = config().getString("source", "").trim().toLowerCase(Locale.ROOT);
        project = config().getString("id", "").trim();
        latest = url = error = null;
        if (source.isEmpty() || project.isEmpty()) return; // inactivo
        if (!source.equals("github") && !source.equals("modrinth") && !source.equals("spigot")) {
            log().warning("config.yml → updates.source desconocido: '" + source + "' (use github, modrinth o spigot).");
            source = "";
            return;
        }
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
        long every = Math.max(3600L, Durations.seconds(config(), 43_200L, 3600, "check-every", "check-hours")) * 1000L;
        asyncTimer("check", 30_000L, every, this::check);
        if (config().getBoolean("notify-admins", true)) listen(this);
    }

    @Override
    protected void onDisable() {
        http = null;
    }

    public boolean configured() {
        return http != null;
    }

    public boolean updateAvailable() {
        return Versions.isNewer(latest, current());
    }

    private String current() {
        return plugin.getDescription().getVersion();
    }

    /** Hilo de I/O. */
    public void check() {
        HttpClient client = http;
        if (client == null) return;
        try {
            String endpoint = switch (source) {
                case "github" -> "https://api.github.com/repos/" + project + "/releases/latest";
                case "modrinth" -> "https://api.modrinth.com/v2/project/" + URLEncoder.encode(project, StandardCharsets.UTF_8) + "/version";
                default -> "https://api.spigotmc.org/legacy/update.php?resource=" + URLEncoder.encode(project, StandardCharsets.UTF_8);
            };
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "LosPerrisStatsAPI/" + current())
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            String body = res.body();
            String version;
            String link;
            switch (source) {
                case "github" -> {
                    JsonObject o = JsonParser.parseString(body).getAsJsonObject();
                    version = o.get("tag_name").getAsString();
                    link = o.has("html_url") ? o.get("html_url").getAsString() : "https://github.com/" + project + "/releases";
                }
                case "modrinth" -> {
                    JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                    if (arr.isEmpty()) throw new IllegalStateException("sin versiones publicadas");
                    JsonElement first = arr.get(0);
                    version = first.getAsJsonObject().get("version_number").getAsString();
                    link = "https://modrinth.com/plugin/" + project;
                }
                default -> {
                    version = body.trim();
                    if (version.isEmpty() || version.length() > 64 || version.contains("<")) throw new IllegalStateException("respuesta inválida");
                    link = "https://www.spigotmc.org/resources/" + project;
                }
            }
            boolean wasNew = updateAvailable();
            latest = version;
            url = link;
            error = null;
            checkedAt = System.currentTimeMillis();
            if (!wasNew && updateAvailable()) {
                log().info("Hay una versión nueva: " + version + " (tiene " + current() + "). " + link);
            }
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            checkedAt = System.currentTimeMillis();
            plugin.debug().log(DebugLog.Category.GENERAL, "No se pudo buscar actualizaciones: " + error);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!updateAvailable() || !p.hasPermission(Perms.UPDATES_NOTIFY)) return;
        String v = latest, link = url;
        scheduler().later(60L, () -> {
            if (p.isOnline()) i18n().send(p, "updates.available", "value", v, "version", current(), "url", link);
        });
    }

    public JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("configured", configured());
        o.addProperty("source", source == null ? "" : source);
        o.addProperty("current", current());
        if (latest != null) o.addProperty("latest", latest);
        if (url != null) o.addProperty("url", url);
        if (error != null) o.addProperty("error", error);
        o.addProperty("update_available", updateAvailable());
        if (checkedAt > 0) o.addProperty("checked_at", checkedAt);
        return o;
    }
}
