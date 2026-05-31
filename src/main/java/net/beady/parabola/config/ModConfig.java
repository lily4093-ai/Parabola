package net.beady.parabola.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.beady.parabola.trajectory.ProjectileType;

@Config(name = "parabola")
public class ModConfig implements ConfigData {

    // ── Line style ────────────────────────────────────────────────────────────
    public enum TrajectoryStyle { SOLID, DASHED, DOTTED }

    // ── Opacity mode ──────────────────────────────────────────────────────────
    public enum OpacityMode { OPAQUE, TRANSPARENT, PULSING }

    // ── Master toggles ────────────────────────────────────────────────────────

    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showWorldArc = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showHudPanel = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showEntityHit = true;

    // ── Per-projectile on/off ─────────────────────────────────────────────────

    @ConfigEntry.Gui.CollapsibleObject
    public Toggles toggles = new Toggles();

    // ── Visual style ──────────────────────────────────────────────────────────

    @ConfigEntry.Gui.CollapsibleObject
    public ArcStyle arcStyle = new ArcStyle();

    // ── Colors ────────────────────────────────────────────────────────────────

    @ConfigEntry.Gui.CollapsibleObject
    public Colors colors = new Colors();

    // ── Nested classes ────────────────────────────────────────────────────────

    public static class Toggles implements ConfigData {
        @ConfigEntry.Gui.Tooltip public boolean bow             = true;
        @ConfigEntry.Gui.Tooltip public boolean crossbow        = true;
        @ConfigEntry.Gui.Tooltip public boolean trident         = true;
        @ConfigEntry.Gui.Tooltip public boolean enderPearl      = true;
        @ConfigEntry.Gui.Tooltip public boolean snowball        = true;
        @ConfigEntry.Gui.Tooltip public boolean egg             = true;
        @ConfigEntry.Gui.Tooltip public boolean windCharge      = true;
        @ConfigEntry.Gui.Tooltip public boolean fishingRod      = false;
        @ConfigEntry.Gui.Tooltip public boolean splashPotion    = true;
        @ConfigEntry.Gui.Tooltip public boolean expBottle       = true;
    }

    public static class ArcStyle implements ConfigData {

        @ConfigEntry.Gui.Tooltip
        public TrajectoryStyle trajectoryStyle = TrajectoryStyle.SOLID;

        @ConfigEntry.Gui.Tooltip
        public OpacityMode opacityMode = OpacityMode.OPAQUE;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int impactBoxAlpha = 40;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
        public int hudPanelAlpha = 78;
    }

    public static class Colors implements ConfigData {

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int bow             = 0xFFFF00;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int crossbowArrow   = 0xFF8800;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int crossbowFirework= 0xFF3300;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int trident         = 0x00FFFF;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int enderPearl      = 0xAA00FF;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int snowballEgg     = 0xFFFFFF;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int windCharge      = 0x88DDFF;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int fishingRod      = 0x44AAFF;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int splashPotion    = 0xFF88FF;

        @ConfigEntry.Gui.Tooltip @ConfigEntry.ColorPicker
        public int expBottle       = 0x00EE88;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isTypeEnabled(ProjectileType type) {
        return switch (type) {
            case BOW               -> toggles.bow;
            case CROSSBOW_ARROW,
                 CROSSBOW_FIREWORK -> toggles.crossbow;
            case TRIDENT           -> toggles.trident;
            case ENDER_PEARL       -> toggles.enderPearl;
            case SNOWBALL          -> toggles.snowball;
            case EGG               -> toggles.egg;
            case WIND_CHARGE       -> toggles.windCharge;
            case FISHING_ROD       -> toggles.fishingRod;
            case SPLASH_POTION     -> toggles.splashPotion;
            case EXP_BOTTLE        -> toggles.expBottle;
        };
    }

    public float getAlpha() {
        return switch (arcStyle.opacityMode) {
            case TRANSPARENT -> 100 / 255.0f;
            case PULSING -> {
                double t = (System.currentTimeMillis() % 2000) / 2000.0 * Math.PI;
                yield (float)(Math.sin(t) * 0.8 + 0.2);
            }
            default -> 1.0f;
        };
    }

    public int rgbFor(ProjectileType type) {
        return switch (type) {
            case BOW               -> colors.bow;
            case CROSSBOW_ARROW    -> colors.crossbowArrow;
            case CROSSBOW_FIREWORK -> colors.crossbowFirework;
            case TRIDENT           -> colors.trident;
            case ENDER_PEARL       -> colors.enderPearl;
            case SNOWBALL, EGG     -> colors.snowballEgg;
            case WIND_CHARGE       -> colors.windCharge;
            case FISHING_ROD       -> colors.fishingRod;
            case SPLASH_POTION     -> colors.splashPotion;
            case EXP_BOTTLE        -> colors.expBottle;
        };
    }
}
