package me.gordi.mineapi.core.module;

import java.util.List;

/**
 * Contrato de todos los módulos. Un módulo desactivado no registra listeners, tareas, comandos
 * ni hilos: no cuesta nada. Se activa con {@code modules: <id>: true} en config.yml.
 */
public interface Module {

    /** Identificador estable, en minúsculas (coincide con la clave de config). */
    String id();

    /** Módulos que deben estar activos antes que este. */
    List<String> dependencies();

    /** Activa el módulo. Si lanza una excepción, el gestor llama a {@link #disable()}. */
    void enable() throws Exception;

    /** Libera todo lo que el módulo registró. Debe ser idempotente. */
    void disable();

    /** Aplica la configuración nueva (/lpa reload) estando ya activo. */
    void reload() throws Exception;

    boolean isEnabled();

    /**
     * Activo pero sin poder hacer su trabajo (falta un plugin, un token...): devuelve la clave del
     * motivo bajo {@code commands.modules.reason.}; null si todo va bien.
     * Running but unable to work: reason key, or null when fine.
     */
    default String problem() {
        return null;
    }
}
