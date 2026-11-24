package me.gordi.mineapi.core.command;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Envía líneas de {@link HelpView.Line}: con hover y clic a jugadores, texto plano a la consola. */
public final class ChatOut {

    private ChatOut() {
    }

    public static void send(CommandSender to, List<HelpView.Line> lines) {
        for (HelpView.Line l : lines) row(to, List.of(l));
    }

    /** Varias partes en una sola línea de chat. */
    public static void row(CommandSender to, List<HelpView.Line> parts) {
        if (parts.isEmpty()) return;
        if (!(to instanceof Player p)) {
            StringBuilder sb = new StringBuilder();
            for (HelpView.Line l : parts) sb.append(l.text());
            if (sb.length() > 0) to.sendMessage(sb.toString());
            return;
        }
        List<BaseComponent> out = new ArrayList<>();
        for (HelpView.Line l : parts) {
            if (l.text().isEmpty()) continue;
            BaseComponent[] comps = components(l.text());
            for (BaseComponent c : comps) {
                if (l.hover() != null && !l.hover().isEmpty()) c.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(components(l.hover()))));
                if (l.command() != null) {
                    c.setClickEvent(new ClickEvent(l.run() ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND, l.command()));
                }
                out.add(c);
            }
        }
        if (!out.isEmpty()) p.spigot().sendMessage(out.toArray(new BaseComponent[0]));
    }

    /**
     * Texto con códigos de color a componentes. {@code fromLegacyText} convierte en enlace todo lo que
     * parece una URL, y nombres de archivo como {@code settings/events.yml} o {@code GUIDE.md} acababan abriendo
     * {@code http://events.yml}: solo se dejan los enlaces que empiezan por http:// o https://.
     */
    static BaseComponent[] components(String legacy) {
        BaseComponent[] comps = TextComponent.fromLegacyText(legacy);
        for (BaseComponent c : comps) {
            ClickEvent ev = c.getClickEvent();
            if (ev == null || ev.getAction() != ClickEvent.Action.OPEN_URL) continue;
            String shown = c instanceof TextComponent t ? t.getText().trim().toLowerCase(java.util.Locale.ROOT) : "";
            if (!shown.startsWith("http://") && !shown.startsWith("https://")) c.setClickEvent(null);
        }
        return comps;
    }
}
