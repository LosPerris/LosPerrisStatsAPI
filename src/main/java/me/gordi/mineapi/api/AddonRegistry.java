package me.gordi.mineapi.api;

import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.command.CatalogView;
import me.gordi.mineapi.core.config.ModuleToggleWriter;
import me.gordi.mineapi.core.module.Module;
import me.gordi.mineapi.core.module.ModuleManager;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/** Implementación de {@link AddonApi} (la usa el núcleo para recargar y apagar los módulos de los addons). */
public final class AddonRegistry implements AddonApi {

    private record Entry(Plugin owner, Addon addon, List<String> ids) {
    }

    private final MineAPI plugin;
    private final List<Entry> addons = new CopyOnWriteArrayList<>();
    /** Interruptores que no se pudieron escribir en config.yml: valor que se usa mientras tanto. */
    private final Map<String, Boolean> pending = new ConcurrentHashMap<>();

    AddonRegistry(MineAPI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean register(Plugin owner, Addon addon) {
        ModuleManager mm = plugin.modules();
        List<String> ids = new ArrayList<>();
        for (Module m : addon.modules()) {
            if (mm.byId(m.id()) != null) throw new IllegalArgumentException("Módulo duplicado / duplicate module: " + m.id());
            ids.add(m.id());
        }
        plugin.i18n().extend(owner, addon.texts());
        plugin.commands().catalog().add(owner, addon.catalog());
        boolean saved = writeSwitches(owner.getName(), addon, ids);
        addons.add(new Entry(owner, addon, ids));
        List<String> on = mm.add(addon.modules());
        plugin.getLogger().info(owner.getName() + ": módulos activos / enabled modules: "
                + (on.isEmpty() ? "ninguno / none" : String.join(", ", on)));
        return saved;
    }

    /** Añade a config.yml (bajo "# ===== Addon =====") los interruptores que falten; nunca cambia los existentes. */
    private boolean writeSwitches(String name, Addon addon, List<String> ids) {
        Map<String, Boolean> initial = addon.initialSwitches();
        Map<String, ModuleToggleWriter.Switch> switches = new LinkedHashMap<>();
        for (String id : ids) {
            CatalogView.Mod mod = addon.catalog().get(id);
            String label = addon.texts().trDefault("module-info." + id + ".name");
            String comment = mod != null && mod.where() != null ? label + " → " + mod.where() : label;
            switches.put(id, new ModuleToggleWriter.Switch(initial.getOrDefault(id, false), comment));
        }
        try {
            plugin.configs().editText(text -> ModuleToggleWriter.addGroup(text, name, switches));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "No se pudieron añadir los interruptores de " + name + " a config.yml; se usan sus "
                    + "valores iniciales hasta el próximo arranque. / Could not add its switches to config.yml.", e);
            switches.forEach((id, sw) -> {
                if (!plugin.hasModuleFlag(id)) pending.put(id, sw.on());
            });
            return false;
        }
    }

    /** Valor de un interruptor de addon que no está en config.yml (o null). */
    public Boolean pending(String id) {
        return pending.get(id);
    }

    /** /lpa reload: cada addon relee sus archivos antes de que se recarguen los módulos. */
    public void reloadAll() {
        for (Entry e : addons) {
            try {
                e.addon().reload();
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.SEVERE, "Error al recargar " + e.owner().getName(), ex);
            }
        }
    }

    /** Un módulo se apagó: su addon suelta sus ajustes. */
    public void forget(String id) {
        for (Entry e : addons) if (e.ids().contains(id)) e.addon().forget(id);
    }

    /** Addons registrados (para /lpa version). */
    public List<Plugin> plugins() {
        List<Plugin> out = new ArrayList<>();
        for (Entry e : addons) out.add(e.owner());
        return out;
    }

    void unregisterAll(Object owner) {
        for (Entry e : addons) {
            if (e.owner() != owner) continue;
            addons.remove(e);
            plugin.modules().remove(e.ids());
            e.ids().forEach(pending::remove);
            plugin.i18n().unextend(owner);
            plugin.commands().catalog().unregisterAll(owner);
        }
    }
}
