package me.gordi.mineapi.core.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registro central de variables por contexto. Es la ÚNICA fuente de verdad: la validación de
 * los archivos de configuración y la documentación de docs/es|en/variables salen de aquí.
 */
public final class Variables {

    private static final Pattern BRACES = Pattern.compile("\\{([a-z0-9_-]+)\\}");
    private static final Pattern PERCENT = Pattern.compile("%([A-Za-z0-9_]+)%");
    private static final String TOP = "top_<type>_<position>_<name|value>";
    private static final String TOP_REGEX = "top_[a-z_]+_\\d+_(name|value)";
    private static final String PERIOD_TOP = "<weekly|monthly>_<type>_<position>_<name|value>";
    private static final String PERIOD_TOP_REGEX = "(weekly|monthly)_[a-z_]+_\\d+_(name|value)";
    private static final String PERIOD_OWN = "<weekly|monthly>_<type>";
    private static final String PERIOD_OWN_REGEX = "(weekly|monthly)_[a-z_]+";
    private static final String TYPE_LIST = "kills, deaths, mob_kills, time, blocks_broken, blocks_placed, ores_mined, jumps, "
            + "distance_walked, fish_caught, trades, damage_dealt, damage_taken";
    private static final Localized TYPES = Localized.of(
            "Tipos: " + TYPE_LIST + ". Posición 1-100.",
            "Types: " + TYPE_LIST + ". Position 1-100.");
    private static final Localized PERIOD_TYPES = Localized.of(
            "Solo las estadísticas de stats en config.yml → periods. Posición 1-100. Requiere el módulo periods.",
            "Only the stats listed in stats in config.yml → periods. Position 1-100. Requires the periods module.");
    private static final Localized ANY_PAPI = Localized.of("Cualquier plugin con PlaceholderAPI", "Any plugin with PlaceholderAPI");
    private static final Localized SB_WHERE = Localized.of("title, lines", "title, lines");
    private static final Localized ALL_TEXTS = Localized.of("Todos los textos visibles en el juego", "Every text shown in game");

    /**
     * Estadísticas con variable propia en scoreboard y PlaceholderAPI: token, descripción ES, descripción EN.
     * Un test comprueba que cada {@code StatType} tiene su fila.
     */
    private static final String[][] STATS = {
            {"kills", "Asesinatos PvP", "PvP kills"},
            {"deaths", "Muertes", "Deaths"},
            {"mob_kills", "Mobs eliminados", "Mobs killed"},
            {"time", "Horas jugadas (formato 1.25h)", "Hours played (format 1.25h)"},
            {"blocks_broken", "Bloques rotos", "Blocks broken"},
            {"blocks_placed", "Bloques colocados (suma de las estadísticas vanilla de uso de bloques)", "Blocks placed (sum of the vanilla block use statistics)"},
            {"ores_mined", "Minerales extraídos (todos los tipos de mena)", "Ores mined (every ore type)"},
            {"jumps", "Saltos", "Jumps"},
            {"distance_walked", "Distancia caminada (formato 1.25km)", "Distance walked (format 1.25km)"},
            {"fish_caught", "Peces pescados", "Fish caught"},
            {"trades", "Comercios con aldeanos", "Villager trades"},
            {"damage_dealt", "Daño causado en corazones (formato 12.5❤)", "Damage dealt in hearts (format 12.5❤)"},
            {"damage_taken", "Daño recibido en corazones (formato 12.5❤)", "Damage taken in hearts (format 12.5❤)"},
    };

    private static final Map<VariableContext, List<Variable>> REGISTRY = new EnumMap<>(VariableContext.class);

    static {
        // ---------------------------------------------------------------- lang/*.yml
        lang("player", "Nombre del jugador que recibe o provoca el mensaje", "Name of the player who receives or triggers the message", "&a¡Hola {player}!");
        lang("target", "Jugador sobre el que se actúa (view, reset, admin...) o material/criatura de un objetivo de misión", "Player being acted on (view, reset, admin...) or the material/mob of a quest objective", "Estadísticas de {target} reseteadas.");
        lang("value", "Valor numérico ya formateado", "Already formatted numeric value", "Valor actual: {value}");
        lang("stat", "Nombre traducido de la estadística", "Translated statistic name", "{stat}: {value}");
        lang("type", "Categoría de ranking escrita por el usuario", "Ranking category typed by the user", "TOP 10 {type}");
        lang("position", "Posición en un ranking", "Position in a ranking", "#{position} {player} - {value}");
        lang("usage", "Sintaxis correcta del comando", "Correct command syntax", "Uso: {usage}");
        lang("version", "Versión del plugin", "Plugin version", "Versión: {version}");
        lang("authors", "Autores del plugin", "Plugin authors", "Autores: {authors}");
        lang("language", "Código de idioma (es, en...)", "Language code (es, en...)", "Idioma cambiado a {language}");
        lang("languages", "Idiomas disponibles separados por comas", "Available languages, comma separated", "Disponibles: {languages}");
        lang("module", "Identificador de un módulo", "Module identifier", "Módulo {module} desactivado");
        lang("modules", "Lista de módulos", "List of modules", "Activos: {modules}");
        lang("count", "Cantidad genérica", "Generic amount", "{count} entradas");
        lang("amount", "Cantidad de dinero ya formateada por la economía", "Money amount already formatted by the economy plugin", "+{amount} recibido");
        lang("task", "Nombre interno de una tarea (timings)", "Internal task name (timings)", "{task}: {avg} ms");
        lang("avg", "Tiempo medio en milisegundos", "Average time in milliseconds", "{task}: {avg} ms");
        lang("max", "Tiempo máximo en milisegundos", "Maximum time in milliseconds", "máx {max} ms");
        lang("runs", "Número de ejecuciones", "Number of runs", "{runs} ejecuciones");
        lang("ratio", "Ratio ya formateado (KDR)", "Already formatted ratio (KDR)", "KDR: {ratio}");
        lang("period", "Nombre traducido del periodo (semanal, mensual)", "Translated period name (weekly, monthly)", "Ranking {period}");
        lang("time", "Tiempo restante ya formateado (2d 4h)", "Remaining time, already formatted (2d 4h)", "Termina en {time}");
        lang("rank", "Posición del jugador en un ranking (o «-»)", "Player position in a ranking (or «-»)", "Puesto #{rank}");
        lang("rarity", "Porcentaje de jugadores que tienen el logro", "Percentage of players who own the achievement", "{rarity}% de los jugadores");
        lang("tier", "Rareza traducida (común, raro, épico...)", "Translated rarity tier (common, rare, epic...)", "Rareza: {tier}");
        lang("obtained", "Logros obtenidos", "Achievements obtained", "{obtained}/{total}");
        lang("total", "Total (logros, jugadores...)", "Total (achievements, players...)", "{obtained}/{total}");
        lang("page", "Número de página", "Page number", "Página {page}");
        lang("days", "Días (tiempo restante)", "Days (remaining time)", "{days}d {hours}h");
        lang("hours", "Horas (tiempo restante)", "Hours (remaining time)", "{hours}h {minutes}m");
        lang("minutes", "Minutos (tiempo restante)", "Minutes (remaining time)", "{hours}h {minutes}m");
        lang("quest", "Nombre traducido de la misión", "Translated quest name", "Misión completada: {quest}");
        lang("progress", "Progreso actual ya formateado", "Current progress, already formatted", "{progress}/{goal}");
        lang("goal", "Objetivo ya formateado", "Goal, already formatted", "{progress}/{goal}");
        lang("reward", "Recompensa en texto (o «-»)", "Reward as text (or «-»)", "Recompensa: {reward}");
        lang("duration", "Duración ya formateada (o ∞ si es permanente)", "Duration, already formatted (or ∞ if permanent)", "Silenciado ({duration})");
        lang("reason", "Motivo de una sanción", "Reason of a sanction", "Motivo: {reason}");
        lang("action", "Acción de moderación (kick, mute, ban...)", "Moderation action (kick, mute, ban...)", "{action} aplicado");
        lang("by", "Quién aplicó la sanción", "Who applied the sanction", "(por {by})");
        lang("date", "Fecha y hora (AAAA-MM-DD HH:mm)", "Date and time (YYYY-MM-DD HH:mm)", "{date} {action}");
        lang("size", "Tamaño de archivo ya formateado", "File size, already formatted", "{value} ({size})");
        lang("threshold", "Umbral configurado de una alerta", "Configured alert threshold", "TPS {value} (umbral {threshold})");
        lang("url", "Enlace (p. ej. a la versión nueva)", "Link (e.g. to the new version)", "Descargar: {url}");
        lang("season", "Nombre de la temporada", "Season name", "Termina la {season}");
        lang("number", "Número genérico (temporada, jugadores, hologramas...)", "Generic number (season, players, holograms...)", "{number} conectados");
        lang("title", "Nombre de un título o encabezado", "Name of a title or heading", "Nuevo título: {title}");
        lang("event", "Nombre del evento", "Event name", "¡Empieza {event}!");
        lang("score", "Puntuación del jugador en el evento", "Player score in the event", "Llevas {score}");
        lang("source", "Origen de un ranking (total, semanal, temporada...)", "Ranking source (all time, weekly, season...)", "{stat} ({source})");
        lang("world", "Nombre de un mundo", "World name", "{world} {x} {y} {z}");
        lang("x", "Coordenada X", "X coordinate", "{world} {x} {y} {z}");
        lang("y", "Coordenada Y", "Y coordinate", "{world} {x} {y} {z}");
        lang("z", "Coordenada Z", "Z coordinate", "{world} {x} {y} {z}");
        lang("code", "Código de vinculación de Discord", "Discord link code", "Use /link {code}");
        lang("user", "Nombre de un usuario de Discord", "Discord user name", "Vinculado con {user}");
        lang("permission", "Nombre de un permiso", "Permission name", "Permiso: {permission}");
        lang("file", "Archivo de configuración", "Configuration file", "Ajustes: {file}");
        lang("category", "Categoría de un módulo (traducida)", "Module category (translated)", "▸ {category}");
        lang("description", "Descripción traducida", "Translated description", "{description}");
        lang("command", "Comando listo para escribir", "Command ready to type", "Clic: {command}");

        // ---------------------------------------------------------------- settings/milestones.yml
        add(VariableContext.MILESTONES, "player", "player",
                "Nombre del jugador que completa el hito", "Name of the player who completes the milestone",
                "\"&a¡%player% ha roto 1000 bloques!\"", "message, broadcast, title, actionbar", "message, broadcast, title, actionbar");
        add(VariableContext.MILESTONES, "message", "message",
                "Mensaje del hito ya formateado", "Already formatted milestone message",
                "actionbar: \"&e✦ %message% ✦\"", "actionbar (global y por hito)", "actionbar (global and per milestone)");

        add(VariableContext.REWARDS, "player", "player",
                "Nombre del jugador que recibe la recompensa", "Name of the player who receives the reward",
                "\"give %player% diamond 1\"", "rewards (comandos de consola)", "rewards (console commands)");
        add(VariableContext.REWARDS, "money:<cantidad>", "money:[0-9.]+",
                "Prefijo especial (no es variable): deposita dinero con Vault", "Special prefix (not a variable): deposits money with Vault",
                "\"money:500\"", "rewards (línea completa)", "rewards (whole line)");
        add(VariableContext.REWARDS, "item:<MATERIAL>:<cantidad>", "item:[A-Za-z_]+(:[0-9]+)?",
                "Prefijo especial (no es variable): da un objeto; lo que no cabe cae al suelo",
                "Special prefix (not a variable): gives an item; what does not fit is dropped",
                "\"item:DIAMOND:3\"", "rewards (línea completa)", "rewards (whole line)");
        add(VariableContext.REWARDS, "command:<comando>", "command:.+",
                "Prefijo opcional (no es variable): comando de consola, igual que una línea sin prefijo",
                "Optional prefix (not a variable): console command, same as a line without a prefix",
                "\"command:give %player% emerald 2\"", "rewards (línea completa)", "rewards (whole line)");

        // ---------------------------------------------------------------- discord
        add(VariableContext.DISCORD, "player", "player", "Jugador que completó el hito", "Player who completed the milestone",
                "\"🏆 {player}\"", "discord.title", "discord.title");
        add(VariableContext.DISCORD, "stat", "stat", "Estadística del hito (clave interna, ej. mob_kills)", "Milestone statistic (internal key, e.g. mob_kills)",
                "\"{stat} completado\"", "discord.title", "discord.title");
        add(VariableContext.DISCORD, "value", "value", "Umbral alcanzado", "Threshold reached",
                "\"¡{value} alcanzado!\"", "discord.title", "discord.title");

        // ---------------------------------------------------------------- scoreboard
        add(VariableContext.SCOREBOARD, "player_name", Pattern.quote("player_name"), "Nombre del jugador", "Player name",
                "\"   %player_name%\"", SB_WHERE);
        for (String[] s : STATS) {
            add(VariableContext.SCOREBOARD, s[0], Pattern.quote(s[0]), Localized.of(s[1], s[2]), "\"%" + s[0] + "%\"", SB_WHERE);
        }
        add(VariableContext.SCOREBOARD, TOP, TOP_REGEX, Localized.of(
                        "Nombre o valor de una posición del ranking. " + TYPES.es(),
                        "Name or value of a ranking position. " + TYPES.en()),
                "\"%top_kills_1_name%: %top_kills_1_value%\"", SB_WHERE);
        add(VariableContext.SCOREBOARD, PERIOD_TOP, PERIOD_TOP_REGEX, Localized.of(
                        "Nombre o valor de una posición del ranking semanal o mensual. " + PERIOD_TYPES.es(),
                        "Name or value of a weekly or monthly ranking position. " + PERIOD_TYPES.en()),
                "\"%weekly_kills_1_name%\"", SB_WHERE);
        add(VariableContext.SCOREBOARD, PERIOD_OWN, PERIOD_OWN_REGEX, Localized.of(
                        "Lo que lleva el jugador en el periodo actual. " + PERIOD_TYPES.es(),
                        "The player's amount in the current period. " + PERIOD_TYPES.en()),
                "\"Semana: %weekly_kills%\"", SB_WHERE);
        achievementVars(VariableContext.SCOREBOARD, "", SB_WHERE);
        moduleVars(VariableContext.SCOREBOARD, "", SB_WHERE);

        // ---------------------------------------------------------------- PlaceholderAPI
        for (String[] s : STATS) papi(s[0], s[1], s[2]);
        papi("language", "Idioma que usa el plugin con el jugador (es, en...)", "Language the plugin uses with the player (es, en...)");
        add(VariableContext.PLACEHOLDERAPI, "losperrisapi_" + TOP, "losperrisapi_" + TOP_REGEX, Localized.of(
                        "Nombre o valor de una posición del ranking. " + TYPES.es(),
                        "Name or value of a ranking position. " + TYPES.en()),
                "%losperrisapi_top_time_1_name%", ANY_PAPI);
        add(VariableContext.PLACEHOLDERAPI, "losperrisapi_" + PERIOD_TOP, "losperrisapi_" + PERIOD_TOP_REGEX, Localized.of(
                        "Posición del ranking semanal o mensual. " + PERIOD_TYPES.es(),
                        "Weekly or monthly ranking position. " + PERIOD_TYPES.en()),
                "%losperrisapi_monthly_time_1_name%", ANY_PAPI);
        add(VariableContext.PLACEHOLDERAPI, "losperrisapi_" + PERIOD_OWN, "losperrisapi_" + PERIOD_OWN_REGEX, Localized.of(
                        "Lo que lleva el jugador en el periodo actual. " + PERIOD_TYPES.es(),
                        "The player's amount in the current period. " + PERIOD_TYPES.en()),
                "%losperrisapi_weekly_kills%", ANY_PAPI);
        achievementVars(VariableContext.PLACEHOLDERAPI, "losperrisapi_", ANY_PAPI);
        moduleVars(VariableContext.PLACEHOLDERAPI, "losperrisapi_", ANY_PAPI);

        // ---------------------------------------------------------------- colores
        add(VariableContext.COLORS, "&0 … &9, &a … &f", "&[0-9a-f]", "Colores de Minecraft", "Minecraft colors",
                "\"&aVerde &cRojo\"", ALL_TEXTS);
        add(VariableContext.COLORS, "&l &o &n &m &k", "&[lonmk]", "Negrita, cursiva, subrayado, tachado, ofuscado",
                "Bold, italic, underline, strikethrough, obfuscated", "\"&l¡IMPORTANTE!\"", ALL_TEXTS);
        add(VariableContext.COLORS, "&r", "&r", "Reinicia color y formato", "Resets color and format", "\"&cRojo&r normal\"", ALL_TEXTS);

        // ---------------------------------------------------------------- settings/quests.yml: objective
        objective("stat:<tipo>", "stat:[a-z_]+", "Sube una estadística (ver tipos de /lpa top). Cuenta desde que empieza la misión",
                "Raise a statistic (see the /lpa top types). Counts from when the quest starts", "stat:mob_kills");
        objective("event:break[:MATERIAL]", "event:break(:[A-Z_]+)?", "Romper bloques (todos o de un material)", "Break blocks (any or one material)", "event:break:DIAMOND_ORE");
        objective("event:place[:MATERIAL]", "event:place(:[A-Z_]+)?", "Colocar bloques", "Place blocks", "event:place");
        objective("event:kill[:ENTIDAD]", "event:kill(:[A-Z_]+)?", "Matar criaturas o jugadores (ENTITY_TYPE)", "Kill mobs or players (ENTITY_TYPE)", "event:kill:ZOMBIE");
        objective("event:craft[:MATERIAL]", "event:craft(:[A-Z_]+)?", "Fabricar objetos (cuenta la cantidad del resultado)", "Craft items (counts the result amount)", "event:craft:BREAD");
        objective("event:consume[:MATERIAL]", "event:consume(:[A-Z_]+)?", "Comer o beber", "Eat or drink", "event:consume:GOLDEN_APPLE");
        objective("event:fish", "event:fish", "Pescar peces", "Catch fish", "event:fish");
        objective("event:enchant", "event:enchant", "Encantar en la mesa de encantamientos", "Enchant at an enchanting table", "event:enchant");
        objective("event:chat", "event:chat", "Enviar mensajes al chat", "Send chat messages", "event:chat");

        // ---------------------------------------------------------------- temporadas, títulos y bot
        add(VariableContext.SEASONS, "number", Pattern.quote("number"), "Número de la temporada", "Season number",
                "name-format: \"Temporada {number}\"", Localized.of("settings/seasons.yml → name-format", "settings/seasons.yml → name-format"));
        add(VariableContext.TITLES, "title", Pattern.quote("title"), "Nombre del título (con sus colores)", "Title name (with its colors)",
                "format: \"&8[&r{title}&8] \"", Localized.of("settings/titles.yml → format", "settings/titles.yml → format"));
        Localized toDiscord = Localized.of("settings/discord.yml → discord-bot.bridge.to-discord-format", "settings/discord.yml → discord-bot.bridge.to-discord-format");
        Localized toGame = Localized.of("settings/discord.yml → discord-bot.bridge.to-minecraft-format", "settings/discord.yml → discord-bot.bridge.to-minecraft-format");
        add(VariableContext.DISCORDBOT, "player", Pattern.quote("player"), "Jugador que escribe en el juego", "Player writing in game",
                "\"**{player}**: {message}\"", toDiscord);
        add(VariableContext.DISCORDBOT, "user", Pattern.quote("user"), "Apodo en el servidor de Discord (o nombre global)",
                "Discord server nickname (or global name)", "\"&9[Discord] &b{user}&7: &f{message}\"", toGame);
        add(VariableContext.DISCORDBOT, "message", Pattern.quote("message"),
                "Mensaje: sin menciones masivas hacia Discord; sin colores ni códigos hacia el juego",
                "Message: without mass mentions towards Discord; without colors or codes towards the game",
                "\"{player}: {message}\"", Localized.of("to-discord-format, to-minecraft-format", "to-discord-format, to-minecraft-format"));
    }

    private Variables() {
    }

    /** Tokens de estadística documentados (para el test de sincronía con StatType). */
    public static List<String> statTokens() {
        List<String> out = new ArrayList<>(STATS.length);
        for (String[] s : STATS) out.add(s[0]);
        return out;
    }

    private static void achievementVars(VariableContext ctx, String prefix, Localized where) {
        String[][] rows = {
                {"achievements_obtained", "Logros obtenidos por el jugador (requiere el módulo achievements)",
                        "Achievements the player owns (requires the achievements module)"},
                {"achievements_total", "Número total de logros", "Total number of achievements"},
                {"achievements_percent", "Porcentaje de logros obtenidos (0-100)", "Percentage of achievements obtained (0-100)"},
        };
        for (String[] r : rows) {
            add(ctx, prefix + r[0], Pattern.quote(prefix + r[0]), Localized.of(r[1], r[2]), "%" + prefix + r[0] + "%", where);
        }
    }

    private static void moduleVars(VariableContext ctx, String prefix, Localized where) {
        String[][] patterns = {
                {"season_<type>", "season_[a-z_]+", "Lo que lleva el jugador en la temporada (módulo seasons)",
                        "The player's amount this season (seasons module)", "season_kills"},
                {"season_rank_<type>", "season_rank_[a-z_]+", "Puesto del jugador en la temporada (o «-»)",
                        "Player position this season (or «-»)", "season_rank_kills"},
                {"season_<type>_<position>_<name|value>", "season_[a-z_]+_\\d+_(name|value)",
                        "Nombre o valor de una posición del ranking de temporada", "Name or value of a season ranking position", "season_kills_1_name"},
                {"event_<position>_<name|value>", "event_\\d+_(name|value)",
                        "Nombre o valor de una posición del evento en curso («---»/0 sin evento)",
                        "Name or value of a position in the running event («---»/0 with no event)", "event_1_name"},
        };
        for (String[] r : patterns) {
            add(ctx, prefix + r[0], prefix + r[1], Localized.of(r[2], r[3]), "%" + prefix + r[4] + "%", where);
        }
        String[][] rows = {
                {"season_name", "Nombre de la temporada actual (módulo seasons)", "Current season name (seasons module)"},
                {"season_number", "Número de la temporada actual", "Current season number"},
                {"season_time_left", "Tiempo hasta que termina la temporada (∞ si es manual)", "Time until the season ends (∞ if manual)"},
                {"season_days_left", "Días hasta que termina la temporada (∞ si es manual)", "Days until the season ends (∞ if manual)"},
                {"title", "Título activo con su formato (titles.format) o vacío (módulo titles)", "Active title with its format (titles.format) or empty (titles module)"},
                {"title_name", "Nombre del título activo, sin formato", "Active title name, without format"},
                {"title_id", "Id del título activo (settings/titles.yml)", "Active title id (settings/titles.yml)"},
                {"titles_unlocked", "Títulos conseguidos por el jugador", "Titles the player has unlocked"},
                {"titles_total", "Títulos que existen", "Number of titles"},
                {"event_active", "true si hay un evento en curso (módulo events)", "true if an event is running (events module)"},
                {"event_name", "Nombre del evento en curso", "Name of the running event"},
                {"event_stat", "Estadística del evento", "Event statistic"},
                {"event_time", "Tiempo que le queda al evento", "Time left in the event"},
                {"event_score", "Puntuación del jugador en el evento", "Player score in the event"},
                {"event_rank", "Puesto del jugador en el evento (o «-»)", "Player position in the event (or «-»)"},
                {"discord_linked", "true si el jugador vinculó su cuenta de Discord (módulo discord-bot)", "true if the player linked their Discord account (discord-bot module)"},
                {"quests_completed", "Misiones completadas en total (módulo quests)", "Quests completed in total (quests module)"},
                {"quests_daily_done", "Misiones diarias completadas hoy", "Daily quests completed today"},
                {"quests_daily_total", "Misiones diarias activas", "Active daily quests"},
                {"quests_daily_reset", "Tiempo hasta que cambian las diarias", "Time until the daily quests change"},
                {"quests_weekly_done", "Misiones semanales completadas esta semana", "Weekly quests completed this week"},
                {"quests_weekly_total", "Misiones semanales activas", "Active weekly quests"},
                {"quests_weekly_reset", "Tiempo hasta que cambian las semanales", "Time until the weekly quests change"},
                {"streak_current", "Racha de conexión actual en días (módulo streaks)", "Current login streak in days (streaks module)"},
                {"streak_best", "Mejor racha de conexión", "Best login streak"},
                {"streak_next_reward", "Día de racha de la próxima recompensa (o «-»)", "Streak day of the next reward (or «-»)"},
                {"playtime_active", "Horas de juego activo, sin AFK (módulo playtime)", "Active play hours, without AFK (playtime module)"},
                {"playtime_next", "Tiempo activo que falta para la próxima recompensa", "Active time left until the next reward"},
        };
        for (String[] r : rows) {
            add(ctx, prefix + r[0], Pattern.quote(prefix + r[0]), Localized.of(r[1], r[2]), "%" + prefix + r[0] + "%", where);
        }
    }

    private static void objective(String name, String regex, String es, String en, String example) {
        add(VariableContext.QUESTS, name, regex, Localized.of(es, en), "objective: \"" + example + "\"",
                Localized.of("settings/quests.yml: objective", "settings/quests.yml: objective"));
    }

    private static void lang(String name, String es, String en, String example) {
        add(VariableContext.LANG, name, Pattern.quote(name), es, en, "\"" + example + "\"",
                "Mensajes que la mencionan (ver comentarios del archivo)", "Messages that mention it (see the file comments)");
    }

    private static void papi(String name, String es, String en) {
        add(VariableContext.PLACEHOLDERAPI, "losperrisapi_" + name, Pattern.quote("losperrisapi_" + name), Localized.of(es, en),
                "%losperrisapi_" + name + "%", ANY_PAPI);
    }

    private static void add(VariableContext ctx, String name, String regex, String descEs, String descEn, String example,
                            String validEs, String validEn) {
        add(ctx, name, regex, Localized.of(descEs, descEn), example, Localized.of(validEs, validEn));
    }

    private static void add(VariableContext ctx, String name, String regex, String descEs, String descEn, String example, Localized validIn) {
        add(ctx, name, regex, Localized.of(descEs, descEn), example, validIn);
    }

    private static void add(VariableContext ctx, String name, String regex, Localized description, String example, Localized validIn) {
        REGISTRY.computeIfAbsent(ctx, k -> new ArrayList<>())
                .add(new Variable(name, Pattern.compile(regex), description, example, validIn));
    }

    /** Variables documentadas del contexto (lista inmutable, en orden). */
    public static List<Variable> of(VariableContext ctx) {
        List<Variable> list = REGISTRY.get(ctx);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /** true si el nombre (sin delimitadores) es una variable válida en el contexto. */
    public static boolean isKnown(VariableContext ctx, String token) {
        for (Variable v : of(ctx)) {
            if (v.matches(token)) return true;
        }
        return false;
    }

    /** Variables usadas en el texto que el contexto NO reconoce (vacío si todo está bien). */
    public static Set<String> findUnknown(VariableContext ctx, String text) {
        if (text == null || text.isEmpty()) return Collections.emptySet();
        Pattern p;
        switch (ctx.syntax()) {
            case BRACES: p = BRACES; break;
            case PERCENT: p = PERCENT; break;
            default: return Collections.emptySet();
        }
        Set<String> unknown = null;
        Matcher m = p.matcher(text);
        while (m.find()) {
            String token = m.group(1);
            if (!isKnown(ctx, token)) {
                if (unknown == null) unknown = new LinkedHashSet<>();
                unknown.add(ctx.wrap(token));
            }
        }
        return unknown == null ? Collections.emptySet() : unknown;
    }
}
