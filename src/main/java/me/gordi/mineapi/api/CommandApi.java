package me.gordi.mineapi.api;

import me.gordi.mineapi.core.command.HelpCatalog;
import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.i18n.I18n;
import org.bukkit.command.TabExecutor;

/**
 * Subcomandos de /lpa para addons: nombre, alias, permiso, ejecutor y autocompletado van en {@link SubCommand};
 * las líneas de /lpa help en {@link HelpCatalog.Entry}. Los textos ({@code commands.<nombre>.usage} y
 * {@code help.cmd.<clave>.usage|desc|info}) salen de los idiomas del núcleo y de los que se añadan con
 * {@link #texts}. Todo se quita con {@link LosPerrisApi#unregisterAll(Object)}.
 * /lpa subcommands for addons; texts come from the core languages plus the ones added with {@link #texts}.
 */
public interface CommandApi {

    /** Añade {@code /lpa <nombre>} (y sus alias) con sus líneas de ayuda. Adds a subcommand and its help lines. */
    void register(Object owner, SubCommand command, HelpCatalog.Entry... help);

    /**
     * Línea de /lpa help ({@code admin} = /lpa help admin). Se ve si su módulo está activo ({@link HelpCatalog#CORE}
     * = siempre) y quien pregunta tiene el permiso. A /lpa help line.
     */
    void help(Object owner, HelpCatalog.Entry entry);

    /** Idiomas del addon como fuente extra de los textos de /lpa. Addon languages for /lpa texts. */
    void texts(Object owner, I18n texts);

    /** El ejecutor de /lpa, para enlazar un comando antiguo del addon (p. ej. /lpx) a los mismos subcomandos. */
    TabExecutor executor();

    /** Quita lo que registró {@code owner} (también lo hace {@link LosPerrisApi#unregisterAll(Object)}). */
    void unregisterAll(Object owner);
}
