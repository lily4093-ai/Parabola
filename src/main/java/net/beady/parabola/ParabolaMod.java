package net.beady.parabola;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.beady.parabola.config.ModConfig;
import net.beady.parabola.render.HudOverlayRenderer;
import net.beady.parabola.render.TrajectoryRenderer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParabolaMod implements ClientModInitializer {

    public static final String MOD_ID = "parabola";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        TrajectoryRenderer.register();
        HudOverlayRenderer.register();
        LOGGER.info("[Parabola] Initialized — physics-accurate trajectory preview active.");
    }
}
