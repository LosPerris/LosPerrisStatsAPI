package me.gordi.mineapi;

import me.gordi.mineapi.api.LosPerrisApi;
import me.gordi.mineapi.core.command.CommandRouter;
import me.gordi.mineapi.core.command.CoreCommands;
import me.gordi.mineapi.core.config.ConfigService;
import me.gordi.mineapi.core.config.DependencyWarnings;
import me.gordi.mineapi.core.data.PlayerDataListener;
import me.gordi.mineapi.core.data.PlayerDataService;
import me.gordi.mineapi.core.event.EventBus;
import me.gordi.mineapi.core.i18n.I18n;
import me.gordi.mineapi.core.log.DebugLog;
import me.gordi.mineapi.core.event.CoreReloadedEvent;
import me.gordi.mineapi.core.module.ModuleHost;
import me.gordi.mineapi.core.module.ModuleManager;
import me.gordi.mineapi.core.placeholder.Placeholders;
import me.gordi.mineapi.core.scheduler.TaskScheduler;
import me.gordi.mineapi.core.reward.Rewards;
import me.gordi.mineapi.core.storage.StorageFactory;
import me.gordi.mineapi.module.afk.AfkModule;
import me.gordi.mineapi.module.backups.BackupsModule;
import me.gordi.mineapi.module.console.ConsoleModule;
import me.gordi.mineapi.module.discordwebhook.DiscordWebhookModule;
import me.gordi.mineapi.module.discordbot.DiscordBotModule;
import me.gordi.mineapi.module.gui.GuiModule;
import me.gordi.mineapi.module.heatmap.HeatmapModule;
import me.gordi.mineapi.module.history.HistoryModule;
import me.gordi.mineapi.module.milestones.MilestonesModule;
import me.gordi.mineapi.module.periods.PeriodsModule;
import me.gordi.mineapi.module.placeholders.PlaceholdersModule;
import me.gordi.mineapi.module.publicweb.PublicModule;
import me.gordi.mineapi.module.scoreboard.ScoreboardModule;
import me.gordi.mineapi.module.stats.StatsModule;
import me.gordi.mineapi.module.updates.UpdatesModule;
import me.gordi.mineapi.module.web.WebModule;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * Punto de entrada (Bukkit). Solo arranca el núcleo y registra los módulos: la lógica de cada
 * funcionalidad vive en {@code module/<nombre>}. Ver docs/es/ARQUITECTURA.md.
 */
public final class MineAPI extends JavaPlugin implements ModuleHost {

    private ConfigService configs;
    private DebugLog debug;
    private I18n i18n;
    private TaskScheduler scheduler;
    private EventBus events;
    private CommandRouter commands;
    private StorageFactory storageFactory;
    private PlayerDataService data;
    private PlayerDataListener dataListener;
    private ModuleManager modules;
    private final Placeholders placeholders = new Placeholders();
    private Rewards rewards;
    private final LosPerrisApi api = new LosPerrisApi(this);

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        // 1. Configuración (con migración automática) y servicios básicos
        configs = new ConfigService(this);
        if (!configs.prepareMainConfig()) {
            getLogger().severe("LosPerrisStatsAPI se detiene para no perder su configuración. / Stopping to keep your configuration safe.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        debug = new DebugLog(getLogger());
        debug.reload(debugSettings());
        i18n = new I18n(this, configs);
        i18n.load(settings());
        scheduler = new TaskScheduler(this);
        events = new EventBus(getLogger());
        rewards = new Rewards(this);

        // 2. Almacenamiento y datos de jugadores (migración a UUID incluida)
        storageFactory = new StorageFactory(this);
        data = new PlayerDataService(this, storageFactory.createAndInit(databaseConfig()));
        dataListener = new PlayerDataListener(this, data);
        getServer().getPluginManager().registerEvents(dataListener, this);

        // 3. Comandos
        commands = new CommandRouter(this);
        PluginCommand cmd = getCommand("losperrisapi");
        if (cmd != null) {
            cmd.setExecutor(commands);
            cmd.setTabCompleter(commands);
        }
        CoreCommands.register(this, commands);

        // 4. Módulos (orden = dependencias primero)
        modules = new ModuleManager(getLogger(), this::isModuleFlagOn);
        modules.register(new StatsModule(this));
        modules.register(new AfkModule(this));
        modules.register(new DiscordWebhookModule(this));
        modules.register(new MilestonesModule(this));
        modules.register(new HistoryModule(this));
        modules.register(new PeriodsModule(this));
        modules.register(new DiscordBotModule(this));
        modules.register(new HeatmapModule(this));
        modules.register(new BackupsModule(this));
        modules.register(new UpdatesModule(this));
        modules.register(new ScoreboardModule(this));
        modules.register(new GuiModule(this));
        modules.register(new PlaceholdersModule(this));
        modules.register(new WebModule(this));
        modules.register(new PublicModule(this));
        modules.register(new ConsoleModule(this));
        modules.enableAll();
        warnDependencies();

        startAutoSave();

        // Jugadores ya conectados (ej. /reload del servidor)
        for (Player p : Bukkit.getOnlinePlayers()) data.loadAsync(p, dataListener::ready);
        getLogger().info("LosPerrisStatsAPI listo en " + (System.currentTimeMillis() - start) + " ms.");
    }

    @Override
    public void onDisable() {
        if (modules != null) modules.disableAll();
        if (data != null) {
            // Se encola todo en el hilo de E/S (FIFO) y shutdown() espera a que termine
            data.saveAll();
            data.shutdown();
        }
        if (scheduler != null) scheduler.shutdown();
    }

    /** /lpa reload: relee archivos y aplica cambios en el núcleo y en cada módulo. */
    public boolean reloadSystem() {
        if (!configs.prepareMainConfig()) {
            getLogger().severe("/lpa reload cancelado: config.yml no se pudo migrar con seguridad (vea el error anterior). "
                    + "Nada ha cambiado. / Reload aborted: config.yml could not be migrated safely; nothing changed.");
            return false;
        }
        configs.forgetModules();
        debug.reload(debugSettings());
        i18n.load(settings());
        FileConfiguration db = databaseConfig();
        if (storageFactory.changed(db)) {
            getLogger().info("database.yml cambió: cambiando el almacenamiento...");
            data.replaceStorage(storageFactory.create(db));
        }
        api.addonRegistry().reloadAll();
        modules.reloadAll();
        warnDependencies();
        startAutoSave();
        events.post(new CoreReloadedEvent(true));
        getLogger().info("Sistema recargado.");
        return true;
    }

    /**
     * Solo la cabecera de config.yml (config-version, general y modules): las secciones de los módulos
     * se analizan aparte y solo si el módulo está activo ({@link ConfigService#module}). Header only.
     */
    @Override
    public FileConfiguration getConfig() {
        return configs != null ? configs.main() : new org.bukkit.configuration.file.YamlConfiguration();
    }

    @Override
    public void reloadConfig() {
        if (configs != null) configs.reloadMain();
    }

    /** config.yml se escribe por secciones (ConfigService), nunca entero desde la cabecera. */
    @Override
    public void saveConfig() {
    }

    /**
     * {@code modules.<id>: true} en config.yml (también acepta el formato antiguo {@code <id>.enabled}). Un módulo de
     * addon cuyo interruptor no se pudo escribir usa su valor inicial.
     */
    public boolean isModuleFlagOn(String id) {
        FileConfiguration c = getConfig();
        String path = "modules." + id;
        if (c.isBoolean(path)) return c.getBoolean(path);
        if (c.isBoolean(path + ".enabled")) return c.getBoolean(path + ".enabled");
        Boolean pending = api.addonRegistry().pending(id);
        return pending != null && pending;
    }

    /** true si config.yml tiene el interruptor del módulo. */
    public boolean hasModuleFlag(String id) {
        FileConfiguration c = getConfig();
        return c.isBoolean("modules." + id) || c.isBoolean("modules." + id + ".enabled");
    }

    private void startAutoSave() {
        scheduler.cancelAll(this);
        long seconds = me.gordi.mineapi.core.time.Durations.seconds(settings(), 300L, 1L, "auto-save", "auto-save-interval");
        if (seconds <= 0) return;
        long ticks = seconds * 20L;
        // Lectura de stats en el hilo principal; la E/S va en cola al hilo de almacenamiento
        scheduler.syncTimer(this, "core:autosave", ticks, ticks, data::saveAll);
    }

    private FileConfiguration databaseConfig() {
        return configs.loadSettings(me.gordi.mineapi.core.config.ConfigLayout.DATABASE);
    }

    /** Depuración: {@code advanced.debug} (v9) o {@code general.debug} (versiones anteriores). */
    private ConfigurationSection debugSettings() {
        ConfigurationSection adv = getConfig().getConfigurationSection("advanced");
        return adv != null && adv.contains("debug") ? adv : settings();
    }

    /**
     * Avisa en la consola de los ajustes que dependen de un módulo apagado (p. ej. anunciar en Discord con
     * discord-webhook apagado). Solo mira módulos activos (sus ajustes ya están leídos). Warns about settings
     * that need a disabled module.
     */
    private void warnDependencies() {
        for (String w : DependencyWarnings.check(modules::isEnabled, configs::module)) getLogger().warning(w);
    }

    // ------------------------------------------------------------------ acceso a servicios

    @Override
    public JavaPlugin bukkit() { return this; }
    @Override
    public FileConfiguration moduleConfig(String id) { return configs.module(id); }
    /** API pública para addons (también con {@link LosPerrisApi#get()}). */
    @Override
    public LosPerrisApi api() { return api; }
    public ConfigService configs() { return configs; }
    /** Ajustes generales: sección {@code general} de config.yml (idioma, zona horaria, week-start...). */
    public ConfigurationSection settings() { return configs.general(); }
    public DebugLog debug() { return debug; }
    public I18n i18n() { return i18n; }
    public TaskScheduler scheduler() { return scheduler; }
    public EventBus events() { return events; }
    public CommandRouter commands() { return commands; }
    public PlayerDataService data() { return data; }
    public ModuleManager modules() { return modules; }
    public Placeholders placeholders() { return placeholders; }
    public Rewards rewards() { return rewards; }

    /** Prefijo de los mensajes (config.yml: prefix), ya coloreado. */
    public String getPrefix() { return i18n.prefix(); }
}
