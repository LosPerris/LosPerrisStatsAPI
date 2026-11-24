package me.gordi.mineapi.module.milestones;

import java.util.List;

/**
 * Un hito de settings/milestones.yml ya interpretado. Los campos de efecto son null cuando se usa el valor
 * global.
 *
 * @param broadcast mensaje para los demás; null = no se anuncia
 */
record Milestone(String stat, int value, String message, String broadcast, List<String> rewards,
                 String title, String sound, Boolean firework, String particles, String actionbar) {
}
