package me.gordi.mineapi.module.milestones;

import me.gordi.mineapi.core.text.Text;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Efectos visuales y sonoros de los hitos. Los nombres de sonido/partícula se resuelven una vez
 * y se cachean; un nombre inválido avisa una sola vez por carga.
 */
final class MilestoneEffects implements Listener {

    static final String FIREWORK_META = "losperris_milestone_fw";

    /** Nombres antiguos (≤1.20.4) ↔ nuevos (1.20.5+). */
    static final Map<String, String> PARTICLE_ALIASES = new HashMap<>();

    static {
        String[][] pairs = {
                {"VILLAGER_HAPPY", "HAPPY_VILLAGER"}, {"VILLAGER_ANGRY", "ANGRY_VILLAGER"},
                {"TOTEM", "TOTEM_OF_UNDYING"}, {"BLOCK_CRACK", "BLOCK"}, {"BLOCK_DUST", "BLOCK"},
                {"ITEM_CRACK", "ITEM"}, {"REDSTONE", "DUST"}, {"SPELL", "EFFECT"},
                {"SPELL_INSTANT", "INSTANT_EFFECT"}, {"SPELL_MOB", "ENTITY_EFFECT"},
                {"SPELL_WITCH", "WITCH"}, {"FIREWORKS_SPARK", "FIREWORK"}, {"SLIME", "ITEM_SLIME"},
                {"SNOWBALL", "ITEM_SNOWBALL"}, {"WATER_DROP", "RAIN"}, {"WATER_SPLASH", "SPLASH"},
                {"WATER_BUBBLE", "BUBBLE"}, {"WATER_WAKE", "FISHING"}, {"SUSPENDED", "UNDERWATER"},
                {"SUSPENDED_DEPTH", "MYCELIUM"}, {"CRIT_MAGIC", "ENCHANTED_HIT"},
                {"ENCHANTMENT_TABLE", "ENCHANT"}, {"EXPLOSION_NORMAL", "POOF"},
                {"EXPLOSION_LARGE", "EXPLOSION"}, {"EXPLOSION_HUGE", "EXPLOSION_EMITTER"},
                {"SMOKE_NORMAL", "SMOKE"}, {"SMOKE_LARGE", "LARGE_SMOKE"}, {"TOWN_AURA", "MYCELIUM"},
                {"DRIP_WATER", "DRIPPING_WATER"}, {"DRIP_LAVA", "DRIPPING_LAVA"}, {"MOB_APPEARANCE", "ELDER_GUARDIAN"}
        };
        for (String[] pair : pairs) {
            PARTICLE_ALIASES.putIfAbsent(pair[0], pair[1]);
            PARTICLE_ALIASES.putIfAbsent(pair[1], pair[0]);
        }
    }

    private final Plugin plugin;
    private final Logger log;
    private final Map<String, Sound> sounds = new HashMap<>();
    private final Map<String, Particle> particles = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    MilestoneEffects(Plugin plugin, Logger log) {
        this.plugin = plugin;
        this.log = log;
    }

    void sound(Player player, String name, boolean everyone) {
        Sound sound = sounds.computeIfAbsent(name.toUpperCase(Locale.ROOT), MilestoneEffects::soundOrNull);
        if (sound == null) {
            warnOnce("Sonido '" + name + "' no existe en esta versión de Minecraft.");
            return;
        }
        if (everyone) {
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), sound, 1f, 1f);
        } else {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }

    @SuppressWarnings("deprecation") // Sound.valueOf sigue siendo la única vía común 1.20 - 1.21
    private static Sound soundOrNull(String upper) {
        try {
            return Sound.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Particle resolveParticle(String name) {
        String upper = name.trim().toUpperCase(Locale.ROOT);
        try {
            return Particle.valueOf(upper);
        } catch (IllegalArgumentException e) {
            String alias = PARTICLE_ALIASES.get(upper);
            if (alias != null) {
                try {
                    return Particle.valueOf(alias);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    void particles(Player player, String name, int count) {
        Particle particle = particles.computeIfAbsent(name, MilestoneEffects::resolveParticle);
        if (particle == null) {
            warnOnce("Partícula '" + name + "' no existe en esta versión de Minecraft.");
            return;
        }
        try {
            Location loc = player.getLocation().add(0, 1, 0);
            Object data = particleData(particle, player);
            if (data != null) player.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0, data);
            else player.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0);
        } catch (Exception e) {
            warnOnce("Error generando partículas '" + name + "': " + e.getMessage());
        }
    }

    /** Algunas partículas (BLOCK, DUST, ITEM...) necesitan datos extra. */
    private static Object particleData(Particle particle, Player player) {
        Class<?> type = particle.getDataType();
        if (type == Void.class) return null;
        if (type == BlockData.class) {
            Material below = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
            return (below.isBlock() && !below.isAir() ? below : Material.STONE).createBlockData();
        }
        if (type == Particle.DustOptions.class) return new Particle.DustOptions(Color.fromRGB(85, 255, 85), 1.0f);
        if (type == ItemStack.class) return new ItemStack(Material.DIAMOND);
        if (type == Color.class) return Color.fromRGB(85, 255, 85);
        if (type == Float.class) return 0f;
        if (type == Integer.class) return 0;
        return null;
    }

    void title(Player player, String main, String subtitle, MilestonesConfig cfg) {
        player.sendTitle(Text.color(main.replace("%player%", player.getName())), subtitle, cfg.fadeIn, cfg.stay, cfg.fadeOut);
    }

    void actionbar(Player player, String format, String message) {
        String text = Text.color(format.replace("%message%", message).replace("%player%", player.getName()));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    void firework(Player player) {
        try {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
            fw.setMetadata(FIREWORK_META, new FixedMetadataValue(plugin, true));
            FireworkMeta meta = fw.getFireworkMeta();
            FireworkEffect.Type[] types = FireworkEffect.Type.values();
            meta.addEffect(FireworkEffect.builder()
                    .flicker(r.nextBoolean())
                    .withColor(randomColor(r))
                    .withFade(randomColor(r))
                    .with(types[r.nextInt(types.length)])
                    .trail(r.nextBoolean())
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        } catch (Exception e) {
            warnOnce("Error lanzando fuego artificial: " + e.getMessage());
        }
    }

    private static Color randomColor(ThreadLocalRandom r) {
        return Color.fromBGR(r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }

    /** Los fuegos artificiales de los hitos son decorativos: no hacen daño. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework && event.getDamager().hasMetadata(FIREWORK_META)) {
            event.setCancelled(true);
        }
    }

    private void warnOnce(String message) {
        if (warned.add(message)) log.warning(message);
    }
}
