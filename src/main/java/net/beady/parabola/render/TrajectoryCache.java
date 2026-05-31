package net.beady.parabola.render;

import net.beady.parabola.trajectory.TrajectoryResult;
import net.beady.parabola.trajectory.TrajectorySimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

public final class TrajectoryCache {

    private static float lastPitch    = Float.NaN;
    private static float lastYaw     = Float.NaN;
    private static Item  lastItem    = null;
    private static int   lastUseTicks = -1;
    private static double lastVx, lastVy, lastVz;
    private static TrajectoryResult cached = null;

    private static final double VEL_THRESHOLD = 0.005;

    private TrajectoryCache() {}

    public static TrajectoryResult getCached() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level  = mc.level;

        if (player == null || level == null) {
            cached = null;
            return null;
        }

        float pitch    = player.getXRot();
        float yaw      = player.getYRot();
        Item  curItem  = player.getMainHandItem().getItem();
        int   useTicks = player.isUsingItem() ? player.getUseItemRemainingTicks() : -1;
        var   v        = player.getDeltaMovement();

        boolean velChanged = Math.abs(v.x - lastVx) > VEL_THRESHOLD
                          || Math.abs(v.y - lastVy) > VEL_THRESHOLD
                          || Math.abs(v.z - lastVz) > VEL_THRESHOLD;

        if (pitch != lastPitch || yaw != lastYaw || curItem != lastItem
                || useTicks != lastUseTicks || velChanged) {
            lastPitch    = pitch;
            lastYaw      = yaw;
            lastItem     = curItem;
            lastUseTicks = useTicks;
            lastVx = v.x; lastVy = v.y; lastVz = v.z;
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
