package me.gordi.mineapi.api;

import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.command.HelpCatalog;
import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.data.PlayerDataService;
import me.gordi.mineapi.core.event.EventBus;
import me.gordi.mineapi.core.module.ModuleManager;
import me.gordi.mineapi.core.reward.Rewards;
import me.gordi.mineapi.module.afk.AfkModule;
import me.gordi.mineapi.module.discordwebhook.DiscordWebhookModule;
import me.gordi.mineapi.module.stats.StatsModule;
import me.gordi.mineapi.core.i18n.I18n;
import org.bukkit.Bukkit;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * API pública de LosPerrisStatsAPI para addons (p. ej. LosPerrisExtras). Se obtiene con {@link #get()}.
 * Todo lo que registra un addon lleva un "dueño" ({@code owner}) y se quita con {@link #unregisterAll}.
 * Ver docs/es/API_REFERENCE.md (sección "API para addons"). Public API for addons.
 */
public final class LosPerrisApi {

    /**
     * Versión de la API (sube si cambia algo incompatible). Es una constante: un addon compilado la copia, así que
     * para saber qué núcleo hay instalado mire la versión del plugin. 2 = comandos y módulos de addons (1.5).
     */
    public static final int VERSION = 2;
    public static final String PLUGIN_NAME = "LosPerrisStatsAPI";

    private record Owned<T>(Object owner, T value) {
    }

    private final MineAPI plugin;
    private final WebRegistry web = new WebRegistry();
    private final MenuRegistry menus = new MenuRegistry();
    private final AddonRegistry addons;
    private final CommandApi commands = new CommandApi() {
        public void register(Object owner, SubCommand command, HelpCatalog.Entry... help) {
            plugin.commands().register(owner, command);
            for (HelpCatalog.Entry e : help) plugin.commands().catalog().help(owner, e);
        }
        public void help(Object owner, HelpCatalog.Entry entry) { plugin.commands().catalog().help(owner, entry); }
        public void texts(Object owner, I18n texts) { plugin.i18n().extend(owner, texts); }
        public TabExecutor executor() { return plugin.commands(); }
        public void unregisterAll(Object owner) { plugin.commands().unregisterAll(owner); }
    };
    private final List<Owned<PlayerTag>> tags = new CopyOnWriteArrayList<>();
    private final List<Owned<BooleanSupplier>> eventModes = new CopyOnWriteArrayList<>();
    private final DiscordApi discord = new DiscordApi() {
        private DiscordWebhookModule hook() {
            DiscordWebhookModule d = plugin.modules() == null ? null : plugin.modules().get(DiscordWebhookModule.class);
            return d != null && d.problem() == null ? d : null;
        }
        public boolean available() { return hook() != null; }
        public void notice(String title, String content, String player) {
            DiscordWebhookModule d = hook();
            if (d != null) d.sendNotice(title, content, player);
        }
        public void alert(String title, String content, int color) {
            DiscordWebhookModule d = hook();
            if (d != null) d.sendAlert(title, content, color);
        }
        public void send(String title, String content, String player) {
            DiscordWebhookModule d = hook();
            if (d != null) d.send(title, content, player);
        }
    };

    public LosPerrisApi(MineAPI plugin) {
        this.plugin = plugin;
        this.addons = new AddonRegistry(plugin);
    }

    /** La API si LosPerrisStatsAPI está activo; si no, null. */
    public static LosPerrisApi get() {
        Plugin p = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        return p instanceof MineAPI m && m.isEnabled() ? m.api() : null;
    }

    // ------------------------------------------------------------------ servicios del núcleo

    /** El plugin del núcleo (configuración general, idiomas, programador...). */
    public MineAPI core() { return plugin; }
    /** Datos de los jugadores (stats y claves propias de cada addon). */
    public PlayerDataService data() { return plugin.data(); }
    /** Módulos del núcleo ({@code modules().get(StatsModule.class)}...). */
    public ModuleManager modules() { return plugin.modules(); }
    /** Bus de eventos: StatChangedEvent, MilestoneReachedEvent, PlayerResetEvent, PlayerDataReadyEvent, CoreReloadedEvent. */
    public EventBus events() { return plugin.events(); }
    /** Premios (dinero con Vault, comandos, mensajes). */
    public Rewards rewards() { return plugin.rewards(); }
    /** Módulo de estadísticas (siempre activo). */
    public StatsModule stats() { return plugin.modules().get(StatsModule.class); }

    /** true si el jugador está AFK (módulo afk activo). */
    public boolean isAfk(Player p) {
        AfkModule afk = plugin.modules().get(AfkModule.class);
        return afk != null && afk.isAfk(p);
    }

    // ------------------------------------------------------------------ puntos de extensión

    public WebApi web() { return web; }

    public MenuApi menus() { return menus; }

    public DiscordApi discord() { return discord; }

    /** Subcomandos de /lpa (y sus líneas de ayuda) para addons. /lpa subcommands. */
    public CommandApi commands() { return commands; }

    /** Módulos de addons dentro del núcleo (/lpa modules, reload, dashboard y config.yml). Addon modules. */
    public AddonApi addons() { return addons; }

    /** Etiqueta del jugador (título activo...) para el perfil web y el bot de Discord. */
    public void playerTag(Object owner, PlayerTag tag) {
        tags.add(new Owned<>(owner, tag));
    }

    /** Primera etiqueta no vacía o null. */
    public String playerTag(String playerName, String lang) {
        for (Owned<PlayerTag> t : tags) {
            try {
                String s = t.value().tag(playerName, lang);
                if (s != null && !s.isEmpty()) return s;
            } catch (RuntimeException e) {
                plugin.getLogger().warning("PlayerTag de " + t.owner().getClass().getSimpleName() + ": " + e);
            }
        }
        return null;
    }

    /**
     * Modo evento del scoreboard: mientras {@code active} sea true, el scoreboard usa sus {@code event-lines}.
     * Event mode for the scoreboard.
     */
    public void eventMode(Object owner, BooleanSupplier active) {
        eventModes.add(new Owned<>(owner, active));
    }

    public boolean eventMode() {
        for (Owned<BooleanSupplier> e : eventModes) if (e.value().getAsBoolean()) return true;
        return false;
    }

    /** Quita todo lo que registró {@code owner} (módulos de addon, comandos, textos, web, menús, etiquetas, modo evento). */
    public void unregisterAll(Object owner) {
        addons.unregisterAll(owner);
        if (plugin.commands() != null) plugin.commands().unregisterAll(owner);
        if (plugin.i18n() != null) plugin.i18n().unextend(owner);
        web.unregisterAll(owner);
        menus.unregisterAll(owner);
        tags.removeIf(t -> t.owner() == owner);
        eventModes.removeIf(e -> e.owner() == owner);
    }

    /** Para el núcleo: los addons registrados (recarga, interruptores pendientes). */
    public AddonRegistry addonRegistry() { return addons; }

    /** Para el núcleo: el registro web con acceso a los endpoints. */
    public WebRegistry webRegistry() { return web; }

    /** Para el núcleo: el registro de páginas del menú. */
    public MenuRegistry menuRegistry() { return menus; }
}
