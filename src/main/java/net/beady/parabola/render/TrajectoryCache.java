package net.beady.parabola.render;

import net.beady.parabola.trajectory.TrajectoryResult;
import net.beady.parabola.trajectory.TrajectorySimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * Caches the last simulated trajectory. Recalculates only when pitch or yaw changes,
 * keeping rendering cost near zero when the player isn't moving their aim.
 */
public final class TrajectoryCache {

    private static float lastPitch = Float.NaN;
    private static float lastYaw   = Float.NaN;
    private static int   lastSlot  = -1;
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

        float pitch = player.getXRot();
        float yaw   = player.getYRot();
        int   slot  = player.getInventory().selected;

        if (pitch != lastPitch || yaw != lastYaw || slot != lastSlot) {
            lastPitch = pitch;
            lastYaw   = yaw;
            lastSlot  = slot;
            cached = TrajectorySimulator.simulate(player, level);
        }

        return cached;
    }

    public static void invalidate() {
        cached = null;
        lastPitch = Float.NaN;
        lastYaw   = Float.NaN;
    }
}
