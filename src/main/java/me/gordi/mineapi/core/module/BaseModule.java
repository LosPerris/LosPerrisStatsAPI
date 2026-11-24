package me.gordi.mineapi.core.module;

import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.event.EventBus;
import me.gordi.mineapi.core.i18n.I18n;
import me.gordi.mineapi.core.placeholder.Placeholders;
import me.gordi.mineapi.core.scheduler.TaskScheduler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base de los módulos (núcleo y addons). Todo lo que se registra con {@link #listen}, {@link #syncTimer},
 * {@link #asyncTimer}, {@link #subscribe}, {@link #command} o {@link #placeholder} se libera solo al desactivar.
 */
public abstract class BaseModule<P extends ModuleHost> implements Module {

    protected final P plugin;
    private final String id;
    private final List<String> dependencies;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean enabled;

    protected BaseModule(P plugin, String id, String... dependencies) {
        this.plugin = plugin;
        this.id = id;
        this.dependencies = List.of(dependencies);
    }

    @Override public final String id() { return id; }
    @Override public final List<String> dependencies() { return dependencies; }
    @Override public final boolean isEnabled() { return enabled; }

    @Override
    public final void enable() throws Exception {
        enabled = true;
        onEnable();
    }

    @Override
    public final void disable() {
        if (!enabled) return;
        try {
            onDisable();
        } catch (Exception e) {
            log().log(Level.WARNING, "Error al desactivar el módulo " + id, e);
        } finally {
            for (Listener l : listeners) HandlerList.unregisterAll(l);
            listeners.clear();
            plugin.scheduler().cancelAll(this);
            plugin.events().unsubscribeAll(this);
            plugin.commands().unregisterAll(this);
            plugin.placeholders().unregisterAll(this);
            plugin.api().unregisterAll(this);
            enabled = false;
        }
    }

    /** Por defecto recargar = desactivar y volver a activar (sencillo y sin estados a medias). */
    @Override
    public void reload() throws Exception {
        disable();
        enable();
    }

    protected abstract void onEnable() throws Exception;

    protected void onDisable() throws Exception {
    }

    // ------------------------------------------------------------------ ayudas

    protected final void listen(Listener listener) {
        plugin.bukkit().getServer().getPluginManager().registerEvents(listener, plugin.bukkit());
        listeners.add(listener);
    }

    protected final void syncTimer(String name, long delayTicks, long periodTicks, Runnable task) {
        plugin.scheduler().syncTimer(this, id + ":" + name, delayTicks, periodTicks, task);
    }

    protected final void asyncTimer(String name, long delayMs, long periodMs, Runnable task) {
        plugin.scheduler().asyncTimer(this, id + ":" + name, delayMs, periodMs, task);
    }

    protected final <E> void subscribe(Class<E> type, Consumer<? super E> handler) {
        plugin.events().subscribe(this, type, handler);
    }

    protected final void command(SubCommand command) {
        plugin.commands().register(this, command);
    }

    /** Registra placeholders con un prefijo (scoreboard y PlaceholderAPI); se quitan al desactivar. */
    protected final void placeholder(String prefix, Placeholders.Resolver resolver) {
        plugin.placeholders().register(this, prefix, resolver);
    }

    /** Archivo del módulo ({@code settings/<id>.yml}); se lee la primera vez que se pide. */
    protected final FileConfiguration config() { return plugin.moduleConfig(id); }
    /** Ajustes generales de config.yml (timezone, week-start, disabled-worlds...). */
    protected final ConfigurationSection settings() { return plugin.settings(); }
    protected final I18n i18n() { return plugin.i18n(); }
    protected final TaskScheduler scheduler() { return plugin.scheduler(); }
    protected final EventBus events() { return plugin.events(); }
    protected final Logger log() { return plugin.bukkit().getLogger(); }
    /** API pública del núcleo (web, menús, Discord...). */
    protected final me.gordi.mineapi.api.LosPerrisApi api() { return plugin.api(); }
}
