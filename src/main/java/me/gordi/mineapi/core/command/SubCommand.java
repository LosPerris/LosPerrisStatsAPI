package me.gordi.mineapi.core.command;

import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Subcomando de /lpa. Los textos de ayuda salen de lang: {@code commands.<name>.usage} y
 * {@code commands.<name>.description}.
 */
public final class SubCommand {

    @FunctionalInterface
    public interface Executor {
        /** @param args argumentos SIN el nombre del subcomando */
        void run(CommandSender sender, String[] args);
    }

    @FunctionalInterface
    public interface Completer {
        List<String> complete(CommandSender sender, String[] args);
    }

    final String name;
    final List<String> aliases;
    final String permission;
    final boolean playerOnly;
    final int minArgs;
    final Executor executor;
    final Completer completer;

    private SubCommand(Builder b) {
        this.name = b.name;
        this.aliases = b.aliases;
        this.permission = b.permission;
        this.playerOnly = b.playerOnly;
        this.minArgs = b.minArgs;
        this.executor = b.executor;
        this.completer = b.completer;
    }

    public String name() { return name; }
    public List<String> aliases() { return aliases; }
    public String permission() { return permission; }

    public boolean canUse(CommandSender sender) {
        return permission == null || sender.hasPermission(permission);
    }

    public static Builder of(String name) {
        return new Builder(name.toLowerCase(Locale.ROOT));
    }

    public static final class Builder {
        private final String name;
        private List<String> aliases = Collections.emptyList();
        private String permission;
        private boolean playerOnly;
        private int minArgs;
        private Executor executor;
        private Completer completer = (s, a) -> Collections.emptyList();

        private Builder(String name) {
            this.name = name;
        }

        public Builder aliases(String... aliases) { this.aliases = List.of(aliases); return this; }
        public Builder permission(String permission) { this.permission = permission; return this; }
        public Builder playerOnly() { this.playerOnly = true; return this; }
        /** Si faltan argumentos se muestra commands.&lt;name&gt;.usage. */
        public Builder minArgs(int minArgs) { this.minArgs = minArgs; return this; }
        public Builder executes(Executor executor) { this.executor = executor; return this; }
        public Builder completes(Completer completer) { this.completer = completer; return this; }

        public SubCommand build() {
            if (executor == null) throw new IllegalStateException("Falta executes() en " + name);
            return new SubCommand(this);
        }
    }
}
