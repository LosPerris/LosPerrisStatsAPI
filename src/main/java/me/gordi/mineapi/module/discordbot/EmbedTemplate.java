package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonObject;
import me.gordi.mineapi.module.discordwebhook.DiscordWebhookModule;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Instant;
import java.util.Map;

/** Plantilla de embed (title/color/author/footer…). Claves vacías se omiten al construir el JSON. */
public final class EmbedTemplate {

    public final String title;
    public final String description;
    public final int color;
    public final String url;
    public final String thumbnailUrl;
    public final String imageUrl;
    public final String authorName;
    public final String authorIconUrl;
    public final String footerText;
    public final String footerIconUrl;
    public final boolean timestamp;

    public EmbedTemplate(ConfigurationSection s) {
        if (s == null) s = new org.bukkit.configuration.MemoryConfiguration();
        title = blankToNull(s.getString("title", ""));
        description = blankToNull(s.getString("description", ""));
        color = DiscordWebhookModule.readColor(s.get("color", "#57F287"));
        url = blankToNull(s.getString("url", ""));
        thumbnailUrl = blankToNull(s.getString("thumbnail-url", ""));
        imageUrl = blankToNull(s.getString("image-url", ""));
        authorName = blankToNull(s.getString("author-name", ""));
        authorIconUrl = blankToNull(s.getString("author-icon-url", ""));
        footerText = blankToNull(s.getString("footer-text", ""));
        footerIconUrl = blankToNull(s.getString("footer-icon-url", ""));
        timestamp = s.getBoolean("timestamp", true);
    }

    /** Aplica variables {@code {clave}} en textos de la plantilla. */
    public JsonObject build(Map<String, String> vars) {
        JsonObject embed = new JsonObject();
        String t = fill(title, vars);
        String d = fill(description, vars);
        if (t != null) embed.addProperty("title", t);
        if (d != null) embed.addProperty("description", d);
        embed.addProperty("color", color);
        if (url != null) embed.addProperty("url", fill(url, vars));
        if (thumbnailUrl != null) {
            JsonObject th = new JsonObject();
            th.addProperty("url", fill(thumbnailUrl, vars));
            embed.add("thumbnail", th);
        }
        if (imageUrl != null) {
            JsonObject im = new JsonObject();
            im.addProperty("url", fill(imageUrl, vars));
            embed.add("image", im);
        }
        if (authorName != null) {
            JsonObject a = new JsonObject();
            a.addProperty("name", fill(authorName, vars));
            if (authorIconUrl != null) a.addProperty("icon_url", fill(authorIconUrl, vars));
            embed.add("author", a);
        }
        String foot = fill(footerText, vars);
        if (foot != null || footerIconUrl != null) {
            JsonObject f = new JsonObject();
            if (foot != null) f.addProperty("text", foot);
            if (footerIconUrl != null) f.addProperty("icon_url", fill(footerIconUrl, vars));
            embed.add("footer", f);
        }
        if (timestamp) embed.addProperty("timestamp", Instant.now().toString());
        return embed;
    }

    static String fill(String template, Map<String, String> vars) {
        if (template == null) return null;
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
