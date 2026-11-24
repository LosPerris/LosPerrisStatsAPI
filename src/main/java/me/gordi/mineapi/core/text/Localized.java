package me.gordi.mineapi.core.text;

/**
 * Texto de documentación en los dos idiomas del proyecto (ES/EN).
 *
 * @param es texto en español
 * @param en text in English
 */
public record Localized(String es, String en) {

    /** Idiomas en los que se genera la documentación. */
    public static final String[] DOC_LANGUAGES = {"es", "en"};

    public static Localized of(String es, String en) {
        return new Localized(es, en);
    }

    /** Texto en el idioma pedido ("en" → inglés; cualquier otro → español). */
    public String get(String lang) {
        return "en".equals(lang) ? en : es;
    }
}
