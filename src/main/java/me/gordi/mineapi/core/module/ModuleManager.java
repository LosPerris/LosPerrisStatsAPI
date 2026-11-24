package me.gordi.mineapi.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registra, activa, recarga y desactiva módulos respetando dependencias. Se registran en orden
 * de dependencia (primero los que no dependen de nadie).
 */
public final class ModuleManager {

    private final Logger logger;
    /** Se reemplazan enteros al cambiar (copia al escribir): la web los lee desde otros hilos. Copy-on-write. */
    private volatile Map<String, Module> modules = new LinkedHashMap<>();
    private volatile Map<Class<?>, Module> byClass = new LinkedHashMap<>();
    private final Predicate<String> flags;
    /** Por qué un módulo pedido en config.yml no está funcionando (id -> motivo). */
    private final Map<String, Problem> failures = new HashMap<>();

    /** Estado visible en /lpa modules. */
    public enum Status { ON, OFF, BROKEN }

    /**
     * Motivo de un problema: clave bajo {@code commands.modules.reason.} y su argumento.
     * Reason key under {@code commands.modules.reason.} plus its argument.
     */
    public record Problem(String key, String arg) {
    }

    /** Cambios hechos por {@link #sync()}. */
    public record Changes(List<String> enabled, List<String> disabled, List<String> failed) {
        public boolean isEmpty() { return enabled.isEmpty() && disabled.isEmpty() && failed.isEmpty(); }
    }

    /**
     * @param flags devuelve si {@code modules.<id>} está activo en la configuración (los addons registran sus módulos
     *              aquí, con {@link #add}, y sus interruptores están en la misma config.yml)
     */
    public ModuleManager(Logger logger, Predicate<String> flags) {
        this.logger = logger;
        this.flags = flags;
    }

    /** ¿Está activa la dependencia? ({@code wanted} = lo que se va a activar, o null = el estado actual). */
    private boolean depOn(String dep, Map<String, Boolean> wanted) {
        if (!modules.containsKey(dep)) return false;
        return wanted == null ? isEnabled(dep) : Boolean.TRUE.equals(wanted.get(dep));
    }

    public void register(Module module) {
        if (modules.containsKey(module.id())) throw new IllegalArgumentException("Módulo duplicado: " + module.id());
        for (String dep : module.dependencies()) {
            if (!modules.containsKey(dep)) {
                throw new IllegalArgumentException("El módulo " + module.id() + " depende de " + dep + ", que debe registrarse antes");
            }
        }
        Map<String, Module> ids = new LinkedHashMap<>(modules);
        ids.put(module.id(), module);
        Map<Class<?>, Module> types = new LinkedHashMap<>(byClass);
        types.put(module.getClass(), module);
        modules = ids;
        byClass = types;
    }

    /**
     * Módulos de un addon que llegan con el servidor ya en marcha: se registran (en orden de dependencias) y se
     * activan los que estén encendidos en config.yml. Devuelve los ids activados. Adds an addon's modules at runtime.
     */
    public List<String> add(List<? extends Module> list) {
        for (Module m : list) register(m);
        List<String> on = new ArrayList<>();
        for (Module m : list) {
            if (flags.test(m.id()) && enable(m)) on.add(m.id());
        }
        return on;
    }

    /** Apaga (en orden inverso) y olvida módulos que se van con su addon. Removes an addon's modules. */
    public void remove(List<String> ids) {
        List<String> reversed = new ArrayList<>(ids);
        Collections.reverse(reversed);
        for (String id : reversed) {
            Module m = modules.get(id);
            if (m == null) continue;
            Map<String, Module> left = new LinkedHashMap<>(modules);
            left.remove(id);
            Map<Class<?>, Module> types = new LinkedHashMap<>(byClass);
            types.remove(m.getClass());
            modules = left;
            byClass = types;
            failures.remove(id);
            try {
                m.disable();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error desactivando " + id, e);
            }
        }
    }

    /** Qué módulos deberían estar activos según la configuración y sus dependencias. */
    private Map<String, Boolean> wanted() {
        Map<String, Boolean> wanted = new LinkedHashMap<>();
        for (Module m : modules.values()) {
            boolean want = flags.test(m.id());
            failures.remove(m.id());
            if (want) {
                for (String dep : m.dependencies()) {
                    if (!depOn(dep, wanted)) {
                        failures.put(m.id(), new Problem("needs", dep));
                        logger.warning("Módulo '" + m.id() + "' desactivado: necesita el módulo '" + dep + "'.");
                        want = false;
                        break;
                    }
                }
            }
            wanted.put(m.id(), want);
        }
        return wanted;
    }

    public void enableAll() {
        Map<String, Boolean> wanted = wanted();
        for (Module m : modules.values()) {
            if (wanted.get(m.id())) enable(m);
        }
        List<String> on = enabledIds();
        logger.info("Módulos activos / enabled modules: " + (on.isEmpty() ? "ninguno / none" : String.join(", ", on)));
    }

    /** Aplica la configuración: activa, desactiva o recarga cada módulo según corresponda. */
    public void reloadAll() {
        Map<String, Boolean> wanted = wanted();
        List<Module> reversed = new ArrayList<>(modules.values());
        Collections.reverse(reversed);
        for (Module m : reversed) {
            if (m.isEnabled() && !wanted.get(m.id())) {
                m.disable();
                logger.info("Módulo desactivado: " + m.id());
            }
        }
        for (Module m : modules.values()) {
            if (!wanted.get(m.id())) continue;
            if (!m.isEnabled()) {
                if (enable(m)) logger.info("Módulo activado: " + m.id());
            } else {
                try {
                    m.reload();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error recargando el módulo " + m.id() + "; se desactiva.", e);
                    failures.put(m.id(), new Problem("error", String.valueOf(e.getMessage())));
                    m.disable();
                }
            }
        }
    }

    /**
     * Aplica solo los interruptores de config.yml sin recargar nada más (/lpa modules enable|disable):
     * apaga los que ya no se piden (dependientes primero) y enciende los pedidos.
     * Applies only the on/off switches; running modules are not reloaded.
     */
    public Changes sync() {
        Map<String, Boolean> wanted = wanted();
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<Module> reversed = new ArrayList<>(modules.values());
        Collections.reverse(reversed);
        for (Module m : reversed) {
            if (m.isEnabled() && !wanted.get(m.id())) {
                try {
                    m.disable();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error desactivando " + m.id(), e);
                }
                off.add(m.id());
                logger.info("Módulo desactivado: " + m.id());
            }
        }
        for (Module m : modules.values()) {
            if (!wanted.get(m.id()) || m.isEnabled()) continue;
            if (enable(m)) {
                on.add(m.id());
                logger.info("Módulo activado: " + m.id());
            } else {
                failed.add(m.id());
            }
        }
        return new Changes(on, off, failed);
    }

    /** Ids registrados que dependen (directa o indirectamente) de {@code id}. */
    public List<String> dependents(String id) {
        List<String> out = new ArrayList<>();
        for (Module m : modules.values()) {
            for (String dep : m.dependencies()) {
                if (dep.equals(id) || out.contains(dep)) {
                    out.add(m.id());
                    break;
                }
            }
        }
        return out;
    }

    /** Dependencias del módulo que están apagadas en config.yml (para avisar antes de activarlo). */
    public List<String> missingDependencies(String id) {
        Module m = modules.get(id);
        List<String> out = new ArrayList<>();
        if (m == null) return out;
        for (String dep : m.dependencies()) {
            if (!modules.containsKey(dep) || !flags.test(dep)) out.add(dep);
        }
        return out;
    }

    /** ON (funciona), OFF (apagado en config.yml) o BROKEN (pedido pero no funciona o funciona a medias). */
    public Status status(String id) {
        Module m = modules.get(id);
        if (m == null) return Status.OFF;
        if (m.isEnabled()) return m.problem() == null ? Status.ON : Status.BROKEN;
        return flags.test(id) ? Status.BROKEN : Status.OFF;
    }

    /** Motivo del estado BROKEN, o null. */
    public Problem problem(String id) {
        Module m = modules.get(id);
        if (m == null) return null;
        if (m.isEnabled()) {
            String key = m.problem();
            return key == null ? null : new Problem(key, null);
        }
        if (!flags.test(id)) return null;
        return failures.getOrDefault(id, new Problem("unknown", null));
    }

    public Module byId(String id) {
        return modules.get(id);
    }

    public void disableAll() {
        List<Module> reversed = new ArrayList<>(modules.values());
        Collections.reverse(reversed);
        for (Module m : reversed) {
            if (!m.isEnabled()) continue;
            try {
                m.disable();
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error desactivando " + m.id(), e);
            }
        }
    }

    private boolean enable(Module m) {
        for (String dep : m.dependencies()) {
            if (!depOn(dep, null)) {
                failures.put(m.id(), new Problem("needs", dep));
                logger.warning("Módulo '" + m.id() + "' no activado: el módulo '" + dep + "' no arrancó.");
                return false;
            }
        }
        try {
            m.enable();
            failures.remove(m.id());
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "No se pudo activar el módulo '" + m.id() + "': " + e.getMessage(), e);
            failures.put(m.id(), new Problem("error", String.valueOf(e.getMessage())));
            try {
                m.disable();
            } catch (Exception ignored) {
                // ya se informó del error original
            }
            return false;
        }
    }

    /** Devuelve el módulo si está ACTIVO, o null. */
    public <T extends Module> T get(Class<T> type) {
        Module m = byClass.get(type);
        return m != null && m.isEnabled() ? type.cast(m) : null;
    }

    public boolean isEnabled(String id) {
        Module m = modules.get(id);
        return m != null && m.isEnabled();
    }

    public List<String> enabledIds() {
        List<String> ids = new ArrayList<>();
        for (Module m : modules.values()) if (m.isEnabled()) ids.add(m.id());
        return ids;
    }

    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(modules.values()));
    }
}
