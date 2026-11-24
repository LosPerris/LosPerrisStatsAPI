package me.gordi.mineapi.module.milestones;

/** Rareza según el % de jugadores que tienen el logro (puro, probado con JUnit). */
public final class Rarity {

    public enum Tier { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY }

    /** Umbrales en %: por debajo de cada uno se sube de rareza. */
    public record Thresholds(double uncommon, double rare, double epic, double legendary) {
        public static final Thresholds DEFAULT = new Thresholds(50, 20, 5, 1);
    }

    private Rarity() {
    }

    public static Tier tier(double percent, Thresholds t) {
        if (percent < t.legendary()) return Tier.LEGENDARY;
        if (percent < t.epic()) return Tier.EPIC;
        if (percent < t.rare()) return Tier.RARE;
        if (percent < t.uncommon()) return Tier.UNCOMMON;
        return Tier.COMMON;
    }

    /** % con un decimal; 0 jugadores = 0 %. */
    public static double percent(int owners, int players) {
        if (players <= 0) return 0;
        return Math.round(owners * 1000.0 / players) / 10.0;
    }
}
