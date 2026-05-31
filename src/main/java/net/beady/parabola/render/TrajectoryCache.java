package net.beady.parabola.render;

import net.beady.parabola.trajectory.TrajectoryResult;
import net.beady.parabola.trajectory.TrajectorySimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

public final class TrajectoryCache {

    private static float lastPitch    = Float.NaN;
    private static float lastYaw      = Float.NaN;
    private static Item  lastItem     = null;
    private static int   lastUseTicks = -1;
    private static TrajectoryResult cached = null;

    // Smoothed player velocity — heavy EMA to eliminate per-frame jitter from movement.
    private static Vec3 smoothedVel = Vec3.ZERO;
    private static final double VEL_SMOOTH = 0.18; // higher = snappier, lower = smoother
    private static double lastSmVx, lastSmVy, lastSmVz;

    // Rate-limit: max 25 simulations/sec.
    private static long lastSimMs = 0L;
    private static final long SIM_INTERVAL_MS = 40L;

    private static final double VEL_THRESHOLD = 0.008;

    private TrajectoryCache() {}

    /** Called each render frame; rate-limited and screen-guarded. */
    public static TrajectoryResult getCached() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level  = mc.level;

        if (player == null || level == null) { cached = null; return null; }
        if (mc.screen != null) return null;

        long now = System.currentTimeMillis();
        if (now - lastSimMs < SIM_INTERVAL_MS) return cached;

        float  pitch    = player.getXRot();
        float  yaw      = player.getYRot();
        Item   curItem  = player.getMainHandItem().getItem();
        int    useTicks = player.isUsingItem() ? player.getUseItemRemainingTicks() : -1;

        // Update smoothed velocity (EMA)
        Vec3 raw = player.getDeltaMovement();
        smoothedVel = new Vec3(
            smoothedVel.x * (1 - VEL_SMOOTH) + raw.x * VEL_SMOOTH,
            smoothedVel.y * (1 - VEL_SMOOTH) + raw.y * VEL_SMOOTH,
            smoothedVel.z * (1 - VEL_SMOOTH) + raw.z * VEL_SMOOTH
        );

        boolean velChanged = Math.abs(smoothedVel.x - lastSmVx) > VEL_THRESHOLD
                          || Math.abs(smoothedVel.y - lastSmVy) > VEL_THRESHOLD
                          || Math.abs(smoothedVel.z - lastSmVz) > VEL_THRESHOLD;

        if (pitch != lastPitch || yaw != lastYaw || curItem != lastItem
                || useTicks != lastUseTicks || velChanged) {
            lastPitch    = pitch;
            lastYaw      = yaw;
            lastItem     = curItem;
            lastUseTicks = useTicks;
            lastSmVx = smoothedVel.x;
            lastSmVy = smoothedVel.y;
            lastSmVz = smoothedVel.z;
            cached = TrajectorySimulator.simulate(player, level, smoothedVel);
        }

        lastSimMs = now;
        return cached;
    }

    /** Returns the last cached result WITHOUT triggering simulation. Safe to call from render. */
    public static TrajectoryResult peekCached() {
        return cached;
    }

    public static void invalidate() {
        cached      = null;
        lastPitch   = Float.NaN;
        lastYaw     = Float.NaN;
        lastItem    = null;
        lastSimMs   = 0L;
        smoothedVel = Vec3.ZERO;
        ScopeRenderCapture.invalidate();
    }
}
