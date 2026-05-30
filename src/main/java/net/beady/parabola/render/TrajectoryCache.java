package net.beady.parabola.render;

import net.beady.parabola.trajectory.TrajectoryResult;
import net.beady.parabola.trajectory.TrajectorySimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

public final class TrajectoryCache {

    private static float lastPitch = Float.NaN;
    private static float lastYaw   = Float.NaN;
    private static Item  lastItem  = null;
    private static TrajectoryResult cached = null;

    private TrajectoryCache() {}

    public static TrajectoryResult getCached() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level  = mc.level;

        if (player == null || level == null) {
            cached = null;
            return null;
        }

        float pitch   = player.getXRot();
        float yaw     = player.getYRot();
        Item  curItem = player.getMainHandItem().getItem();

        if (pitch != lastPitch || yaw != lastYaw || curItem != lastItem) {
            lastPitch = pitch;
            lastYaw   = yaw;
            lastItem  = curItem;
            cached = TrajectorySimulator.simulate(player, level);
        }

        return cached;
    }

    public static void invalidate() {
        cached = null;
        lastPitch = Float.NaN;
        lastYaw   = Float.NaN;
        lastItem  = null;
    }
}
