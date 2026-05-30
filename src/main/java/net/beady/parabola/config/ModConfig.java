package net.beady.parabola.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "parabola")
public class ModConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showWorldArc = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showHudPanel = true;

    @ConfigEntry.Gui.Tooltip
    public boolean showEntityHit = true;

    @ConfigEntry.Gui.CollapsibleObject
    public ArcStyle arcStyle = new ArcStyle();

    @ConfigEntry.Gui.CollapsibleObject
    public Colors colors = new Colors();

    public static class ArcStyle implements ConfigData {

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 8)
        public int dotEveryNTicks = 3;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 1, max = 20)
        public int dotSizeUnit = 6;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 0, max = 100)
        public int impactBoxAlpha = 40;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.BoundedDiscrete(min = 10, max = 100)
        public int hudPanelAlpha = 78;
    }

    public static class Colors implements ConfigData {

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int bow = 0xFFFF00;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int crossbowArrow = 0xFF8800;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int crossbowFirework = 0xFF3300;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int trident = 0x00FFFF;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int enderPearl = 0xAA00FF;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int snowballEgg = 0xFFFFFF;

        @ConfigEntry.Gui.Tooltip
        @ConfigEntry.ColorPicker
        public int windCharge = 0x88DDFF;
    }

    /** Returns the 24-bit RGB int stored in Colors for the given projectile type. */
    public int rgbFor(net.beady.parabola.trajectory.ProjectileType type) {
        return switch (type) {
            case BOW              -> colors.bow;
            case CROSSBOW_ARROW   -> colors.crossbowArrow;
            case CROSSBOW_FIREWORK-> colors.crossbowFirework;
            case TRIDENT          -> colors.trident;
            case ENDER_PEARL      -> colors.enderPearl;
            case SNOWBALL, EGG    -> colors.snowballEgg;
            case WIND_CHARGE      -> colors.windCharge;
        };
    }
}
