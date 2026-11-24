package me.gordi.mineapi.module.web;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generación de la API key y escritura en settings/web.yml sin perder los comentarios. */
final class ApiKeys {

    static final String PLACEHOLDER = "PON_TU_CLAVE_AQUI";
    private static final Pattern KEY_LINE = Pattern.compile(
            "(?m)^([ \\t]+api-key:[ \\t]*)(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^#\\r\\n]*?)([ \\t]*(#[^\\r\\n]*)?)$");

    private ApiKeys() {
    }

    static boolean needsKey(String key) {
        return key == null || key.isBlank() || key.trim().equalsIgnoreCase(PLACEHOLDER);
    }

    static String generate() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Sustituye solo el valor de la línea api-key. @return null si no encontró la línea. */
    static String replaceKey(String yaml, String newKey) {
        Matcher m = KEY_LINE.matcher(yaml);
        if (!m.find()) return null;
        return yaml.substring(0, m.start()) + m.group(1) + "\"" + newKey + "\"" + m.group(3) + yaml.substring(m.end());
    }
}
