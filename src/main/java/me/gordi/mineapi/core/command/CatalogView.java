package me.gordi.mineapi.core.command;

import me.gordi.mineapi.core.module.ModuleCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que la ayuda y {@code modules} necesitan saber de un plugin: sus grupos de módulos y sus líneas de ayuda.
 * {@link #CORE} es el del núcleo; un addon pasa el suyo a {@code LosPerrisApi.addons()} y se une a /lpa
 * ({@link CommandCatalog}). What help and the modules list need from a plugin: module groups and help lines.
 */
public interface CatalogView {

    /**
     * @param id          id del módulo
     * @param category    clave del grupo ({@code module-info.category.<category>})
     * @param where       dónde están sus ajustes (texto) o null
     * @param commands    subcomandos que registra
     * @param permissions permisos propios
     * @param requires    plugin externo necesario o null
     */
    record Mod(String id, String category, String where, List<String> commands, List<String> permissions, String requires) {
    }

    /** El comando de todo (núcleo y addons). The command for everything. */
    String COMMAND = "/lpa";

    /** Claves de los grupos, en el orden en que se muestran. */
    List<String> categories();

    /** Todos los módulos, en orden. */
    List<Mod> all();

    /** Módulo por id (o alias) o null. */
    Mod get(String id);

    /** Todas las líneas de ayuda. */
    List<HelpCatalog.Entry> help();

    /** Enlace a la guía. */
    String guide();

    /** Guía del plugin que trae el módulo (la del núcleo salvo en {@link CommandCatalog}). Guide of a module. */
    default String guide(String module) { return guide(); }

    default List<Mod> in(String category) {
        List<Mod> out = new ArrayList<>();
        for (Mod m : all()) if (m.category().equals(category)) out.add(m);
        return out;
    }

    default List<HelpCatalog.Entry> help(String module) {
        List<HelpCatalog.Entry> out = new ArrayList<>();
        for (HelpCatalog.Entry e : help()) if (e.module().equals(module)) out.add(e);
        return out;
    }

    /** El del núcleo: {@link ModuleCatalog} y {@link HelpCatalog}. */
    CatalogView CORE = new CatalogView() {
        private final List<String> categories = categories0();

        private List<String> categories0() {
            List<String> out = new ArrayList<>();
            for (ModuleCatalog.Category c : ModuleCatalog.Category.values()) out.add(c.key());
            return List.copyOf(out);
        }

        public List<String> categories() { return categories; }
        public List<Mod> all() {
            List<Mod> out = new ArrayList<>();
            for (ModuleCatalog.Info i : ModuleCatalog.all()) out.add(of(i));
            return out;
        }
        public Mod get(String id) {
            ModuleCatalog.Info i = ModuleCatalog.get(id);
            return i == null ? null : of(i);
        }
        public List<HelpCatalog.Entry> help() { return HelpCatalog.all(); }
        public String guide() { return me.gordi.mineapi.core.Links.DOCS; }
    };

    static Mod of(ModuleCatalog.Info i) {
        return new Mod(i.id(), i.category().key(), i.where(), i.commands(), i.permissions(), i.requires());
    }
}
