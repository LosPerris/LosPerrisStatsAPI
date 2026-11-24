package me.gordi.mineapi.module.discordbot;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lectura de cuentas vinculadas de DiscordSRV por reflexión (sin dependencia de compilación).
 * Sus métodos pueden consultar una base de datos: llamar SIEMPRE fuera del hilo principal.
 */
public final class DiscordSrvHook {

    private final Plugin plugin;
    private final Method getPlugin;
    private final Method manager;
    private final Method discordId;
    private final Method uuid;
    private final Method linked;
    private final java.lang.reflect.Field ready;

    private DiscordSrvHook(Plugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        ClassLoader cl = plugin.getClass().getClassLoader();
        Class<?> main = Class.forName("github.scarsz.discordsrv.DiscordSRV", true, cl);
        Class<?> alm = Class.forName("github.scarsz.discordsrv.objects.managers.AccountLinkManager", true, cl);
        getPlugin = main.getMethod("getPlugin");
        manager = main.getMethod("getAccountLinkManager");
        ready = main.getField("isReady");
        discordId = alm.getMethod("getDiscordId", UUID.class);
        uuid = alm.getMethod("getUuid", String.class);
        linked = alm.getMethod("getLinkedAccounts");
    }

    /** Gancho si DiscordSRV está activo; null si no está o su API no coincide. */
    public static DiscordSrvHook find(Plugin dsrv, java.util.function.Consumer<String> warn) {
        if (dsrv == null || !dsrv.isEnabled()) return null;
        try {
            return new DiscordSrvHook(dsrv);
        } catch (ReflectiveOperationException | LinkageError e) {
            warn.accept("DiscordSRV " + dsrv.getDescription().getVersion() + " no es compatible (" + e.getClass().getSimpleName() + "); se usa el bot integrado.");
            return null;
        }
    }

    public String version() {
        return plugin.getDescription().getVersion();
    }

    public boolean isReady() {
        try {
            return ready.getBoolean(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private Object manager() throws ReflectiveOperationException {
        Object main = getPlugin.invoke(null);
        return main == null ? null : manager.invoke(main);
    }

    public String discordId(UUID player) {
        try {
            Object m = manager();
            return m == null ? null : (String) discordId.invoke(m, player);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public UUID uuid(String discord) {
        try {
            Object m = manager();
            return m == null ? null : (UUID) uuid.invoke(m, discord);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** discordId → uuid de todas las cuentas vinculadas (vacío si falla). */
    @SuppressWarnings("unchecked")
    public Map<String, UUID> linked() {
        try {
            Object m = manager();
            Object r = m == null ? null : linked.invoke(m);
            return r instanceof Map ? new HashMap<>((Map<String, UUID>) r) : Map.of();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Map.of();
        }
    }
}
