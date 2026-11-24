package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cuentas vinculadas Minecraft ↔ Discord (modo integrado) y roles que el plugin puso a cada
 * usuario de Discord (para quitar solo esos; también en modo DiscordSRV). Archivo
 * {@code data/discord_links.json}. Seguro entre hilos (sincronizado).
 */
public final class LinkStore {

    /** Vinculación de un jugador. */
    public record Link(UUID uuid, String name, String discordId) {
    }

    private final Map<UUID, Link> byUuid = new HashMap<>();
    private final Map<String, UUID> byDiscord = new HashMap<>();
    private final Map<String, Set<String>> roles = new HashMap<>();
    private boolean dirty;

    /** Vincula (y deshace vínculos anteriores de cualquiera de los dos lados). */
    public synchronized void link(UUID uuid, String name, String discordId) {
        unlinkUuid(uuid);
        unlinkDiscord(discordId);
        byUuid.put(uuid, new Link(uuid, name, discordId));
        byDiscord.put(discordId, uuid);
        dirty = true;
    }

    public synchronized Link unlinkUuid(UUID uuid) {
        Link l = byUuid.remove(uuid);
        if (l != null) {
            byDiscord.remove(l.discordId());
            dirty = true;
        }
        return l;
    }

    public synchronized Link unlinkDiscord(String discordId) {
        UUID u = byDiscord.get(discordId);
        return u == null ? null : unlinkUuid(u);
    }

    public synchronized Link byUuid(UUID uuid) {
        return byUuid.get(uuid);
    }

    public synchronized Link byDiscord(String discordId) {
        UUID u = byDiscord.get(discordId);
        return u == null ? null : byUuid.get(u);
    }

    /** Roles que el plugin puso a ese usuario de Discord (vacío si ninguno). */
    public synchronized Set<String> roles(String discordId) {
        return roles.getOrDefault(discordId, Set.of());
    }

    public synchronized void setRoles(String discordId, Set<String> set) {
        Set<String> old = roles.getOrDefault(discordId, Set.of());
        if (old.equals(set)) return;
        if (set.isEmpty()) roles.remove(discordId);
        else roles.put(discordId, Set.copyOf(set));
        dirty = true;
    }

    /** Usuarios de Discord con roles puestos por el plugin. */
    public synchronized Set<String> withRoles() {
        return Set.copyOf(roles.keySet());
    }

    public synchronized List<Link> all() {
        return new ArrayList<>(byUuid.values());
    }

    public synchronized int size() {
        return byUuid.size();
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized JsonObject toJson() {
        JsonArray arr = new JsonArray();
        for (Link l : byUuid.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", l.uuid().toString());
            o.addProperty("name", l.name());
            o.addProperty("discord", l.discordId());
            arr.add(o);
        }
        JsonObject r = new JsonObject();
        for (Map.Entry<String, Set<String>> e : roles.entrySet()) {
            JsonArray a = new JsonArray();
            e.getValue().forEach(a::add);
            r.add(e.getKey(), a);
        }
        JsonObject root = new JsonObject();
        root.add("links", arr);
        root.add("roles", r);
        dirty = false;
        return root;
    }

    public static LinkStore fromJson(JsonObject root) {
        LinkStore s = new LinkStore();
        if (root == null) return s;
        if (root.has("links") && root.get("links").isJsonArray()) {
            for (JsonElement e : root.getAsJsonArray("links")) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    UUID u = UUID.fromString(o.get("uuid").getAsString());
                    String d = o.get("discord").getAsString();
                    s.byUuid.put(u, new Link(u, o.has("name") ? o.get("name").getAsString() : "?", d));
                    s.byDiscord.put(d, u);
                } catch (RuntimeException ignored) {
                    // entrada inválida: se omite
                }
            }
        }
        if (root.has("roles") && root.get("roles").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("roles").entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                Set<String> set = new LinkedHashSet<>();
                for (JsonElement r : e.getValue().getAsJsonArray()) set.add(r.getAsString());
                if (!set.isEmpty()) s.roles.put(e.getKey(), Set.copyOf(set));
            }
        }
        return s;
    }
}
