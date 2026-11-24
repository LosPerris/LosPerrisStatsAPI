package me.gordi.mineapi.core.text;

import java.util.regex.Pattern;

/**
 * Una variable admitida en un contexto.
 *
 * @param name        nombre tal como se documenta (puede incluir partes &lt;variables&gt;)
 * @param pattern     expresión que reconoce el nombre (sin delimitadores)
 * @param description qué devuelve (ES/EN)
 * @param example     ejemplo de uso
 * @param validIn     dónde es válida dentro del contexto (ES/EN)
 */
public record Variable(String name, Pattern pattern, Localized description, String example, Localized validIn) {

    public boolean matches(String token) {
        return pattern.matcher(token).matches();
    }
}
