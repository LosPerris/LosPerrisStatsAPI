package me.gordi.mineapi.module.discordwebhook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.log.DebugLog;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.text.Template;
import me.gordi.mineapi.core.text.VariableContext;
import me.gordi.mineapi.core.text.VariableValidator;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Avisos a un webhook de Discord. Un único hilo con cola limitada (50) que respeta los 429
 * (Retry-After). El hilo se crea al activar el módulo; desactivado no cuesta nada.
 */
public final class DiscordWebhookModule extends AbstractModule {

    public static final String ID = "discord-webhook";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_QUEUE = 50;
    private static final int MAX_ATTEMPTS = 3;
    private static final String DEFAULT_AVATAR = "https://minotar.net/helm/Steve/100.png";

    private ThreadPoolExecutor executor;
    private String url;

    /** Activo sin webhook-url válida: no envía avisos. */
    @Override
    public String problem() {
        return isEnabled() && !isUrlValid() ? "no-webhook" : null;
    }
    private String username;
    private String avatarUrl;
    private int color;
    private Template title;

    public DiscordWebhookModule(MineAPI plugin) {
        super(plugin, ID);
    }

    @Override
    protected void onEnable() {
        url = config().getString("webhook-url", "");
        username = config().getString("username", "LosPerrisStats");
        String avatar = config().getString("avatar-url", "");
        avatarUrl = avatar == null || avatar.isEmpty() ? DEFAULT_AVATAR : avatar;
        color = readColor(config().get("embed-color"));
        String rawTitle = config().getString("title", "¡Logro Desbloqueado!");
        new VariableValidator(log(), "settings/discord.yml → discord-webhook").check(VariableContext.DISCORD, "title", rawTitle);
        title = Template.compile(rawTitle, false);
        if (!isUrlValid()) {
            log().warning("Módulo discord activo pero webhook-url no es válida. Configúrela en settings/discord.yml → discord-webhook.");
            return;
        }
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_QUEUE), r -> {
            Thread t = new Thread(r, "LosPerris-Discord");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    protected void onDisable() {
        if (executor != null) executor.shutdownNow(); // los avisos pendientes se descartan
        executor = null;
    }

    private boolean isUrlValid() {
        return url != null && url.startsWith("https://") && !url.contains("PEGAR_TU_URL");
    }

    /** Aviso de hito. {@code content} ya sin colores. */
    public void sendMilestone(String player, String stat, int value, String content) {
        send(title.render("player", player, "stat", stat, "value", value), content, player);
    }

    public void send(String embedTitle, String content, String playerName) {
        send(embedTitle, content, playerName, color);
    }

    /** Aviso genérico (moderación...). playerName puede ser null. */
    public void sendNotice(String embedTitle, String content, String playerName) {
        send(embedTitle, content, playerName, color);
    }

    /** Alerta del servidor (sin jugador) con color propio (0xRRGGBB). */
    public void sendAlert(String embedTitle, String content, int alertColor) {
        send(embedTitle, content, null, alertColor);
    }

    private void send(String embedTitle, String content, String playerName, int embedColor) {
        ThreadPoolExecutor ex = executor;
        if (ex == null) return;

        JsonObject embed = new JsonObject();
        embed.addProperty("title", embedTitle);
        embed.addProperty("description", content);
        embed.addProperty("color", embedColor);
        embed.addProperty("timestamp", Instant.now().toString());
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "LosPerrisStats • " + plugin.getDescription().getVersion());
        footer.addProperty("icon_url", avatarUrl);
        embed.add("footer", footer);
        if (playerName != null && !playerName.isEmpty()) {
            String head = "https://minotar.net/helm/" + playerName + "/100.png";
            JsonObject thumbnail = new JsonObject();
            thumbnail.addProperty("url", head);
            embed.add("thumbnail", thumbnail);
            JsonObject author = new JsonObject();
            author.addProperty("name", playerName);
            author.addProperty("icon_url", head);
            embed.add("author", author);
        }
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject json = new JsonObject();
        json.addProperty("username", username);
        json.addProperty("avatar_url", avatarUrl);
        json.add("embeds", embeds);

        String body = GSON.toJson(json);
        String target = url;
        plugin.debug().log(DebugLog.Category.GENERAL, "Enviando webhook: " + body);
        try {
            ex.execute(() -> post(target, body));
        } catch (RejectedExecutionException e) {
            log().warning("Cola de Discord llena (" + MAX_QUEUE + "), se descarta un aviso.");
        }
    }

    private void post(String target, String body) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) java.net.URI.create(target).toURL().openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                c.setDoOutput(true);
                c.setConnectTimeout(10_000);
                c.setReadTimeout(10_000);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                drain(c, code);
                if (code == 429) {
                    long wait = retryAfterMs(c.getHeaderField("Retry-After"));
                    plugin.debug().log(DebugLog.Category.GENERAL, "Discord rate-limit, reintento en " + wait + " ms");
                    Thread.sleep(wait);
                    continue;
                }
                if (code < 200 || code > 299) log().warning("Error enviando webhook de Discord. Código: " + code);
                return;
            } catch (java.net.MalformedURLException | IllegalArgumentException e) {
                log().warning("URL de webhook malformada: " + e.getMessage());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log().warning("Fallo al enviar el webhook: " + e.getMessage());
                return;
            } finally {
                if (c != null) c.disconnect();
            }
        }
        log().warning("Webhook de Discord descartado tras " + MAX_ATTEMPTS + " intentos (rate-limit).");
    }

    public static long retryAfterMs(String header) {
        try {
            if (header != null) return Math.min(60_000L, Math.max(250L, (long) (Double.parseDouble(header.trim()) * 1000)));
        } catch (NumberFormatException ignored) {
        }
        return 2000L;
    }

    private static void drain(HttpURLConnection c, int code) {
        try (InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream()) {
            if (in == null) return;
            byte[] buf = new byte[1024];
            while (in.read(buf) != -1) {
                // descartar
            }
        } catch (Exception ignored) {
        }
    }

    public static int readColor(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof String s) {
            String hex = s.startsWith("#") ? s.substring(1) : s;
            try {
                return Integer.parseInt(hex, 16);
            } catch (NumberFormatException ignored) {
            }
        }
        return 5636053;
    }
}
