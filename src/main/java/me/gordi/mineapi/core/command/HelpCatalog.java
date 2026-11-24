package me.gordi.mineapi.core.command;

import me.gordi.mineapi.core.module.Perms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Líneas de /lpa help: una por acción (las de jugador y las de administración separadas). Los
 * textos salen de lang: {@code help.cmd.<key>.usage|desc|info}. Help lines, one per action.
 */
public final class HelpCatalog {

    /** Módulo "core": comandos del núcleo, siempre disponibles. */
    public static final String CORE = "core";

    /**
     * @param key        clave en lang ({@code help.cmd.<key>})
     * @param module     id del módulo que registra el comando, o {@link #CORE}
     * @param permission permiso necesario (null = todos)
     * @param admin      true = se muestra en /lpa help admin
     * @param suggest    texto que se escribe en el chat al hacer clic
     */
    public record Entry(String key, String module, String permission, boolean admin, String suggest) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        // jugador / player
        player("top", "stats", null, "/lpa top ");
        player("menu", "gui", null, "/lpa menu");
        player("sb", "scoreboard", Perms.SCOREBOARD, "/lpa sb");
        player("period", "periods", null, "/lpa period ");
        player("discord", "discord-bot", null, "/lpa discord link");
        player("lang", CORE, null, "/lpa lang ");
        player("version", CORE, null, "/lpa version");
        // administración / admin
        admin("reset", "stats", Perms.STATS_RESET, "/lpa reset ");
        admin("view", "gui", Perms.INSPECT, "/lpa view ");
        admin("admin", "gui", Perms.GUI_ADMIN, "/lpa admin ");
        admin("period-end", "periods", Perms.PERIODS_ADMIN, "/lpa period end ");
        admin("backup", "backups", Perms.BACKUPS_ADMIN, "/lpa backup now");
        admin("modules", CORE, Perms.MODULES, "/lpa modules");
        admin("modules-toggle", CORE, Perms.MODULES_TOGGLE, "/lpa modules enable ");
        admin("reload", CORE, Perms.RELOAD, "/lpa reload");
        admin("timings", CORE, Perms.TIMINGS, "/lpa timings");
    }

    private HelpCatalog() {
    }

    private static void player(String key, String module, String perm, String suggest) {
        ENTRIES.add(new Entry(key, module, perm, false, suggest));
    }

    private static void admin(String key, String module, String perm, String suggest) {
        ENTRIES.add(new Entry(key, module, perm, true, suggest));
    }

    public static List<Entry> all() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static List<Entry> of(String module) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) if (e.module().equals(module)) out.add(e);
        return out;
    }
}
