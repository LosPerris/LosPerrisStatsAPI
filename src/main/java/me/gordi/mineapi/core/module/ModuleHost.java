package me.gordi.mineapi.core.module;

import me.gordi.mineapi.api.LosPerrisApi;
import me.gordi.mineapi.core.command.CommandRouter;
import me.gordi.mineapi.core.event.EventBus;
import me.gordi.mineapi.core.i18n.I18n;
import me.gordi.mineapi.core.placeholder.Placeholders;
import me.gordi.mineapi.core.scheduler.TaskScheduler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lo que un módulo necesita de su plugin. Lo implementan el núcleo ({@code MineAPI}) y los addons
 * (p. ej. LosPerrisExtras), así sus módulos comparten {@link BaseModule}. What a module needs from its plugin.
 */
public interface ModuleHost {

    /** El plugin de Bukkit dueño de los listeners y tareas. */
    JavaPlugin bukkit();

    TaskScheduler scheduler();

    /** Bus de eventos del núcleo (compartido: los addons escuchan los mismos eventos). */
    EventBus events();

    CommandRouter commands();

    Placeholders placeholders();

    I18n i18n();

    /** Ajustes del módulo ({@code settings/<id>.yml} del plugin que lo registra). */
    FileConfiguration moduleConfig(String id);

    /** Ajustes generales del núcleo (zona horaria, idioma, disabled-worlds...). */
    ConfigurationSection settings();

    /** API pública del núcleo. */
    LosPerrisApi api();
}
