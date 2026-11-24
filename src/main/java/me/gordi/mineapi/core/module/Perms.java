package me.gordi.mineapi.core.module;

/**
 * Permisos del plugin. Todos los de administración son hijos de {@link #ADMIN} en plugin.yml:
 * quien tenga losperris.admin los tiene todos. Plugin permissions (admin ones are children of losperris.admin).
 */
public final class Perms {

    public static final String ADMIN = "losperris.admin";

    // núcleo
    public static final String RELOAD = "losperris.reload";
    public static final String MODULES = "losperris.modules";
    public static final String MODULES_TOGGLE = "losperris.modules.toggle";
    public static final String TIMINGS = "losperris.timings";

    // módulos
    public static final String STATS_RESET = "losperris.stats.reset";
    public static final String INSPECT = "losperris.inspect";
    public static final String GUI_ADMIN = "losperris.gui.admin";
    public static final String SCOREBOARD = "losperris.scoreboard";
    public static final String PERIODS_ADMIN = "losperris.periods.admin";
    public static final String BACKUPS_ADMIN = "losperris.backups.admin";
    public static final String UPDATES_NOTIFY = "losperris.updates.notify";

    private Perms() {
    }
}
