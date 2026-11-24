package me.gordi.mineapi.core;

/**
 * Enlaces que el plugin muestra (config.yml, /lpa modules, registro). Cámbielos aquí y en ningún otro sitio:
 * las plantillas usan {@code {{DOCS_URL}}} y se rellenan con {@link #DOCS} al crear cada archivo.
 * Links shown by the plugin; change them here only.
 */
public final class Links {

    /** Carpeta de documentación publicada (sin barra final): {@code <DOCS>/es/GUIA.md}, {@code <DOCS>/en/GUIDE.md}. */
    public static final String DOCS = "https://github.com/LosPerris/LosPerrisStatsAPI/blob/main/docs";

    /** Marca de las plantillas que se sustituye por {@link #DOCS}. */
    public static final String DOCS_TOKEN = "{{DOCS_URL}}";

    private Links() {
    }

    /** Guía en el idioma pedido (es o en), con ancla opcional (p. ej. {@code "web"}). */
    public static String guide(String lang, String anchor) {
        String base = DOCS + ("es".equals(lang) ? "/es/GUIA.md" : "/en/GUIDE.md");
        return anchor == null || anchor.isEmpty() ? base : base + "#" + anchor;
    }

    /** Sustituye {@code {{DOCS_URL}}} en el texto de una plantilla. */
    public static String fill(String template) {
        return template == null ? null : template.replace(DOCS_TOKEN, DOCS);
    }
}
