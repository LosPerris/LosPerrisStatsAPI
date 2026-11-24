package me.gordi.mineapi.module.placeholders;

import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.hook.PlaceholderApiHook;
import me.gordi.mineapi.module.stats.StatsModule;

/**
 * Expansión de PlaceholderAPI {@code %losperrisapi_...%}. Si PlaceholderAPI no está instalado el
 * módulo no hace nada (la clase de la expansión ni siquiera se carga).
 */
public final class PlaceholdersModule extends AbstractModule {

    public static final String ID = "placeholders";

    private LosPerrisExpansion expansion;

    /** Activo sin PlaceholderAPI: no registra nada. */
    @Override
    public String problem() {
        return isEnabled() && expansion == null ? "no-papi" : null;
    }

    public PlaceholdersModule(MineAPI plugin) {
        super(plugin, ID, StatsModule.ID);
    }

    @Override
    protected void onEnable() {
        if (!PlaceholderApiHook.isAvailable()) return;
        expansion = new LosPerrisExpansion(plugin, plugin.modules().get(StatsModule.class).text());
        if (expansion.register()) log().info("PlaceholderAPI: expansión 'losperrisapi' registrada.");
    }

    @Override
    protected void onDisable() {
        if (expansion != null) expansion.unregister();
        expansion = null;
    }
}
