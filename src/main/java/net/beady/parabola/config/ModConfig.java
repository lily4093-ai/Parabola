package net.beady.parabola.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "parabola")
public class ModConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip(count = 1)
    public boolean enabled = true;

    @ConfigEntry.Gui.Tooltip(count = 1)
    public boolean showWorldArc = true;

    @ConfigEntry.Gui.Tooltip(count = 1)
    public boolean showHudPanel = true;
}
