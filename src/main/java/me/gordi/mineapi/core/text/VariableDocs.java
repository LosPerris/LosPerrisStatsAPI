package me.gordi.mineapi.core.text;

import java.util.List;

/**
 * Genera la documentación Markdown de docs/&lt;idioma&gt;/variables a partir de {@link Variables}
 * en español y en inglés ({@link Localized#DOC_LANGUAGES}).
 */
public final class VariableDocs {

    private VariableDocs() {
    }

    public static String fileName(VariableContext ctx) {
        return ctx.id() + ".md";
    }

    /** Documento de un contexto en el idioma pedido ("es" o "en"). */
    public static String render(VariableContext ctx, String lang) {
        boolean en = "en".equals(lang);
        StringBuilder sb = new StringBuilder();
        sb.append("# Variables: ").append(ctx.title(lang)).append("\n\n");
        sb.append(en
                ? "> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.\n\n"
                : "> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.\n\n");
        sb.append(en ? "**Used in:** " : "**Dónde se usan:** ").append(ctx.files(lang)).append("  \n");
        String syntax = en ? "**Syntax:** " : "**Sintaxis:** ";
        switch (ctx.syntax()) {
            case BRACES: sb.append(syntax).append(en ? "`{name}`\n\n" : "`{nombre}`\n\n"); break;
            case PERCENT: sb.append(syntax).append(en ? "`%name%`\n\n" : "`%nombre%`\n\n"); break;
            case FORMAT: sb.append(syntax).append(en ? "the whole value, `kind:key[:FILTER]`\n\n" : "el valor completo, `tipo:clave[:FILTRO]`\n\n"); break;
            default: sb.append(syntax).append(en ? "`&` codes before the text\n\n" : "códigos `&` delante del texto\n\n"); break;
        }
        sb.append(en ? "| Variable | Description | Example | Valid in |\n" : "| Variable | Descripción | Ejemplo | Válida en |\n")
          .append("|---|---|---|---|\n");
        List<Variable> vars = Variables.of(ctx);
        for (Variable v : vars) {
            sb.append("| `").append(ctx.wrap(v.name())).append("` | ").append(cell(v.description().get(lang)))
              .append(" | `").append(v.example().replace("|", "\\|")).append("` | ").append(cell(v.validIn().get(lang))).append(" |\n");
        }
        if (ctx.syntax() == VariableContext.Syntax.FORMAT) {
            sb.append(en
                    ? "\nAn invalid objective (or an unknown statistic) is reported in the console and that quest is skipped.\n"
                    : "\nUn objetivo inválido (o una estadística desconocida) se avisa en consola y esa misión se omite.\n");
        } else if (ctx.syntax() != VariableContext.Syntax.NONE) {
            sb.append(en
                    ? "\nIf a file uses a variable that is not in this table, the console prints a warning on load or on `/lpa reload`.\n"
                    : "\nSi un archivo usa una variable que no está en esta tabla, la consola mostrará un aviso al cargar o al hacer `/lpa reload`.\n");
        }
        if (ctx == VariableContext.SCOREBOARD) {
            sb.append(en
                    ? "\nWith PlaceholderAPI installed its placeholders are accepted too (`%vault_eco_balance%`, ...).\n"
                    : "\nCon PlaceholderAPI instalado también se aceptan sus placeholders (`%vault_eco_balance%`, ...).\n");
        }
        return sb.toString();
    }

    /** Índice con todos los contextos en el idioma pedido. */
    public static String index(String lang) {
        boolean en = "en".equals(lang);
        StringBuilder sb = new StringBuilder(en ? "# Variables by context\n\n" : "# Variables por contexto\n\n");
        sb.append(en ? "> Generated automatically from `core/text/Variables.java`. " : "> Generado automáticamente desde `core/text/Variables.java`. ")
          .append(en ? "[Versión en español](../../es/variables/README.md)\n\n" : "[English version](../../en/variables/README.md)\n\n");
        sb.append(en ? "| Context | File | Syntax | Where |\n" : "| Contexto | Archivo | Sintaxis | Dónde |\n").append("|---|---|---|---|\n");
        for (VariableContext ctx : VariableContext.values()) {
            String syntax = switch (ctx.syntax()) {
                case BRACES -> "`{x}`";
                case PERCENT -> "`%x%`";
                case FORMAT -> "`a:b`";
                default -> "`&`";
            };
            sb.append("| ").append(ctx.title(lang)).append(" | [").append(fileName(ctx)).append("](").append(fileName(ctx)).append(") | ")
              .append(syntax).append(" | ").append(cell(ctx.files(lang))).append(" |\n");
        }
        return sb.toString();
    }

    private static String cell(String s) {
        return s.replace("|", "\\|");
    }
}
