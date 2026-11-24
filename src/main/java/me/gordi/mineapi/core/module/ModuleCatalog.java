package me.gordi.mineapi.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lista única de módulos: grupo (núcleo o extra), si tiene ajustes, sus comandos y permisos. La usan la ayuda, {@code /lpa modules}, la migración y los tests de la guía.
 * Single source of truth for modules: category, config file, commands and permissions.
 */
public final class ModuleCatalog {

    /**
     * Grupos en el orden en que se muestran. {@link #CORE} es lo que el plugin tuvo desde el principio
     * (estadísticas, API web y dashboard, Discord, hitos, scoreboard, AFK, menús y PlaceholderAPI); el resto son
     * extras del núcleo: no crean archivos hasta que se activan. Lo demás (títulos, misiones, temporadas...)
     * está en el addon LosPerrisExtras. Core first; the other groups are optional extras.
     */
    public enum Category {
        CORE, WEB, SERVER;

        /** Clave de idioma del nombre: {@code module-info.category.<name>}. */
        public String key() { return this == CORE ? "core" : "extra-" + name().toLowerCase(Locale.ROOT); }
    }

    /**
     * @param id          id del módulo (clave en {@code modules:} de config.yml)
     * @param category    grupo en la ayuda y en /lpa modules
     * @param hasSettings si tiene ajustes propios (sección o archivo, ver {@code ConfigLayout.place})
     * @param commands    subcomandos de /lpa que registra (para mostrarlos aunque esté apagado)
     * @param permissions permisos propios (todos hijos de losperris.admin salvo los de jugador)
     * @param requires    plugin externo necesario (texto libre) o null
     */
    public record Info(String id, Category category, boolean hasSettings, List<String> commands, List<String> permissions, String requires) {
        /**
         * Dónde están sus ajustes: {@code config.yml → stats}, {@code settings/discord.yml → discord-bot} o un
         * archivo entero como {@code settings/web.yml} o {@code settings/titles.yml}. Null si no tiene.
         */
        public String settings() {
            return hasSettings ? me.gordi.mineapi.core.config.ConfigLayout.place(id).label() : null;
        }

        /** Texto para la ayuda y /lpa modules (igual que {@link #settings()}). */
        public String where() {
            return settings();
        }

        /** Núcleo (true) o extra (false). */
        public boolean core() {
            return category == Category.CORE;
        }
    }

    private static final Map<String, Info> BY_ID = new LinkedHashMap<>();
    /**
     * Nombres antiguos o cortos -> id actual: {@code discord}/{@code discordbot} (v5), {@code public}/
     * {@code console} (hasta v8) y {@code achievements} (hasta v9: ahora es la galería de los hitos).
     * Valen en config.yml, en /lpa modules y en la migración.
     */
    public static final Map<String, String> ALIASES = Map.of("discord", "discord-webhook", "discordbot", "discord-bot",
            "public", "public-page", "console", "web-console", "achievements", "milestones");

    /**
     * Módulos que estuvieron en el núcleo hasta v1.3 (config v9) y ahora son del addon LosPerrisExtras. Solo los
     * usa la migración: sus ajustes y datos se pasan a plugins/LosPerrisExtras/. Moved to the addon (migration only).
     */
    public static final List<String> MOVED = List.of("titles", "quests", "streaks", "playtime", "seasons", "events",
            "holograms", "moderation", "alerts");

    /** Módulos que ya no existen como tales (los de {@link #MOVED} y achievements); solo para migrar. */
    private static final Map<String, Info> LEGACY = new LinkedHashMap<>();

    static {
        // ---- núcleo: lo que tuvo desde el principio (mejorado)
        add("stats", Category.CORE, true, List.of("top", "reset"), List.of(Perms.STATS_RESET), null);
        add("web", Category.CORE, true, List.of(), List.of(), null);
        add("discord-webhook", Category.CORE, true, List.of(), List.of(), null);
        add("discord-bot", Category.CORE, true, List.of("discord"), List.of(), "DiscordSRV (opcional)");
        add("milestones", Category.CORE, true, List.of(), List.of(), null);
        add("scoreboard", Category.CORE, true, List.of("sb"), List.of(Perms.SCOREBOARD), null);
        add("afk", Category.CORE, true, List.of(), List.of(), null);
        add("gui", Category.CORE, false, List.of("menu", "view", "admin"), List.of(Perms.INSPECT, Perms.GUI_ADMIN), null);
        add("placeholders", Category.CORE, false, List.of(), List.of(), "PlaceholderAPI");

        // ---- extras del núcleo
        add("public-page", Category.WEB, true, List.of(), List.of(), null);
        add("web-console", Category.WEB, true, List.of(), List.of(), null);
        add("heatmap", Category.WEB, true, List.of(), List.of(), null);

        add("periods", Category.SERVER, true, List.of("period"), List.of(Perms.PERIODS_ADMIN), null);
        add("history", Category.SERVER, true, List.of(), List.of(), null);
        add("backups", Category.SERVER, true, List.of("backup"), List.of(Perms.BACKUPS_ADMIN), null);
        add("updates", Category.SERVER, true, List.of(), List.of(Perms.UPDATES_NOTIFY), null);

        for (String id : MOVED) LEGACY.put(id, new Info(id, Category.SERVER, true, List.of(), List.of(), null));
        LEGACY.put("achievements", new Info("achievements", Category.SERVER, true, List.of(), List.of(), null));
    }

    private ModuleCatalog() {
    }

    private static void add(String id, Category c, boolean file, List<String> cmds, List<String> perms, String requires) {
        BY_ID.put(id, new Info(id, c, file, cmds, perms, requires));
    }

    /** Info del módulo (acepta ids antiguos) o null. */
    public static Info get(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT).trim();
        return BY_ID.get(ALIASES.getOrDefault(key, key));
    }

    /**
     * Id para migrar una configuración antigua: como {@link #canonical(String)}, pero los módulos antiguos
     * ({@link #MOVED} y achievements) conservan su id para que el paso a v10 los lleve a su sitio.
     */
    public static String legacyId(String id) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT).trim();
        return LEGACY.containsKey(key) ? key : canonical(key);
    }

    /** Los módulos actuales y los antiguos (solo para la migración). */
    public static List<Info> migration() {
        List<Info> out = new ArrayList<>(BY_ID.values());
        out.addAll(LEGACY.values());
        return out;
    }

    /** Id actual para un id (o alias antiguo); null si no existe. */
    public static String canonical(String id) {
        Info i = get(id);
        return i == null ? null : i.id();
    }

    public static List<Info> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    public static List<Info> in(Category c) {
        List<Info> out = new ArrayList<>();
        for (Info i : BY_ID.values()) if (i.category() == c) out.add(i);
        return out;
    }
}
