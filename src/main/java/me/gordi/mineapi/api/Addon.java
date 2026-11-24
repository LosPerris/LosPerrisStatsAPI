package me.gordi.mineapi.api;

import me.gordi.mineapi.core.command.CatalogView;
import me.gordi.mineapi.core.i18n.I18n;
import me.gordi.mineapi.core.module.Module;

import java.util.List;
import java.util.Map;

/**
 * Lo que un addon entrega a {@link AddonApi#register}: sus módulos pasan a ser parte del núcleo (/lpa modules,
 * /lpa reload, el dashboard y los interruptores de config.yml del núcleo). What an addon hands to the core.
 */
public interface Addon {

    /** Sus módulos, en orden de dependencias (pueden depender de módulos del núcleo). Its modules, dependencies first. */
    List<Module> modules();

    /** Grupos, nombres, comandos y ayuda de sus módulos para /lpa help y /lpa modules. */
    CatalogView catalog();

    /** Sus idiomas: nombres de sus módulos, ayuda y uso de sus comandos. Its languages. */
    I18n texts();

    /**
     * Valor de cada interruptor que todavía no está en config.yml del núcleo (p. ej. el que tenía en su propia
     * configuración antes de la 1.5); los que falten aquí quedan en false. Values for switches not yet in config.yml.
     */
    default Map<String, Boolean> initialSwitches() {
        return Map.of();
    }

    /** /lpa reload: relee sus archivos (ajustes, idiomas) antes de que el núcleo recargue sus módulos. */
    default void reload() {
    }

    /** Uno de sus módulos se apagó: puede soltar sus ajustes de la memoria. One of its modules was turned off. */
    default void forget(String module) {
    }
}
