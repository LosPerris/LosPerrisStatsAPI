package me.gordi.mineapi.core.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Ajustes que dependen de otro módulo: si están en {@code true} y ese módulo está apagado, no hacen nada.
 * Se avisa en la consola al arrancar y con /lpa reload (una sola línea). Solo se miran módulos activos, así que
 * no se abre ningún archivo de más. Settings that need another module (warned once per start/reload).
 */
public final class DependencyWarnings {

    /**
     * Módulo, ajuste dentro de sus ajustes, módulo que necesita y valor de la plantilla. Si la plantilla trae
     * el ajuste en true y el módulo que necesita viene apagado de serie (Discord), dejarlo así no se avisa: es
     * «listo para cuando active Discord», y una instalación nueva no debe arrancar con avisos.
     */
    public record Rule(String module, String path, String requires, boolean templateValue) {
    }

    /** Módulos que vienen apagados de serie (un true de plantilla que los necesita no se avisa). */
    static final java.util.Set<String> OFF_BY_DEFAULT = java.util.Set.of("discord-webhook", "discord-bot");

    public static final List<Rule> RULES = List.of(
            new Rule("periods", "announce.discord", "discord-webhook", true));

    private DependencyWarnings() {
    }

    /**
     * @param enabled  ¿está activo el módulo?
     * @param settings ajustes de un módulo (solo se pide para módulos activos)
     * @return líneas de aviso (vacía si no hay nada que avisar)
     */
    public static List<String> check(Predicate<String> enabled, Function<String, ConfigurationSection> settings) {
        return check(RULES, enabled, settings);
    }

    /** Igual, con otras reglas (los addons: sus módulos pueden necesitar uno del núcleo). */
    public static List<String> check(List<Rule> rules, Predicate<String> enabled, Function<String, ConfigurationSection> settings) {
        List<String> found = new ArrayList<>();
        for (Rule r : rules) {
            if (!enabled.test(r.module()) || enabled.test(r.requires())) continue;
            ConfigurationSection s = settings.apply(r.module());
            if (s == null || !s.getBoolean(r.path(), false)) continue;
            if (r.templateValue() && OFF_BY_DEFAULT.contains(r.requires())) continue;
            found.add(r.module() + "." + r.path() + " → " + r.requires());
        }
        if (found.isEmpty()) return List.of();
        return List.of("Ajustes sin efecto porque el módulo que necesitan está apagado (actívelo en config.yml → modules o ponga el ajuste en false) / "
                + "settings with no effect, their module is off: " + String.join(", ", found));
    }
}
