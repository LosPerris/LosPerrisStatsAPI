package me.gordi.mineapi.core.text;

import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Avisa en consola de variables desconocidas al cargar la configuración (una vez por carga).
 * Solo se ejecuta al cargar/recargar: nunca en tiempo de juego.
 */
public final class VariableValidator {

    private final Logger logger;
    private final String file;
    private int warnings;

    public VariableValidator(Logger logger, String file) {
        this.logger = logger;
        this.file = file;
    }

    /** Comprueba el texto y avisa si usa variables que el contexto no admite. */
    public void check(VariableContext ctx, String path, String text) {
        check(ctx, path, text, t -> false);
    }

    /**
     * @param ignore tokens que se aceptan aunque no estén en el registro (ej. placeholders de
     *               PlaceholderAPI cuando está instalado)
     */
    public void check(VariableContext ctx, String path, String text, Predicate<String> ignore) {
        Set<String> unknown = Variables.findUnknown(ctx, text);
        for (String token : unknown) {
            if (ignore.test(token)) continue;
            warnings++;
            logger.warning(file + " -> '" + path + "': variable desconocida " + token
                    + " (ver/see " + me.gordi.mineapi.core.Links.DOCS + "/es/variables/" + ctx.id() + ".md)");
        }
    }

    public int warnings() {
        return warnings;
    }
}
