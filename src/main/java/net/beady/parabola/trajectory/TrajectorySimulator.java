package net.beady.parabola.trajectory;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TrajectorySimulator {

    private static final int MAX_TICKS = 240;

    private TrajectorySimulator() {}

    public static TrajectoryResult simulate(LocalPlayer player, ClientLevel level, Vec3 playerVelocity) {
        if (player == null || level == null) return null;
        if (player.isDeadOrDying()) return null;

        _playerVel = playerVelocity;
        ItemStack stack = player.getMainHandItem();

        // ── Trident ─────────────────────────────────────────────────────────
        if (stack.is(Items.TRIDENT)) {
            if (hasEnchantment(stack, Enchantments.RIPTIDE)) {
                if (player.isInWaterOrRain()) {
                    return new TrajectoryResult(List.of(), null, 0, ProjectileType.TRIDENT, false, true, null);
                }
            }
            return buildResult(player, level, ProjectileType.TRIDENT, player.getLookAngle(), 1.0f, false);
        }

        // ── Bow ──────────────────────────────────────────────────────────────
        if (stack.is(Items.BOW)) {
            if (!player.isUsingItem()) return null;
            float pull = computeBowPull(player);
            if (pull < 0.01f) return null;
            return buildResult(player, level, ProjectileType.BOW, player.getLookAngle(), pull, false);
        }

        // ── Crossbow ─────────────────────────────────────────────────────────
        if (stack.is(Items.CROSSBOW)) {
            ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (charged == null || charged.isEmpty()) return null;

            boolean isFirework = charged.items().stream().anyMatch(p -> p.is(Items.FIREWORK_ROCKET));
            boolean multishot  = hasEnchantment(stack, Enchantments.MULTISHOT);

            if (isFirework) {
                return buildFireworkResult(player, level, charged, multishot);
            } else {
                return buildResult(player, level, ProjectileType.CROSSBOW_ARROW, player.getLookAngle(), 1.0f, multishot);
            }
        }

        // ── Ender Pearl ───────────────────────────────────────────────────────
        if (stack.is(Items.ENDER_PEARL)) {
            return buildResult(player, level, ProjectileType.ENDER_PEARL, player.getLookAngle(), 1.0f, false);
        }

        // ── Snowball / Egg ────────────────────────────────────────────────────
        if (stack.is(Items.SNOWBALL)) {
            return buildResult(player, level, ProjectileType.SNOWBALL, player.getLookAngle(), 1.0f, false);
        }
        if (stack.is(Items.EGG)) {
            return buildResult(player, level, ProjectileType.EGG, player.getLookAngle(), 1.0f, false);
        }

        // ── Wind Charge ───────────────────────────────────────────────────────
        if (stack.is(Items.WIND_CHARGE)) {
            return buildResult(player, level, ProjectileType.WIND_CHARGE, player.getLookAngle(), 1.0f, false);
        }

        // ── Splash / Lingering Potion ─────────────────────────────────────────
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            Vec3 dir = getPotionThrowDir(player);
            return buildResult(player, level, ProjectileType.SPLASH_POTION, dir, 1.0f, false);
        }

        // ── Experience Bottle ─────────────────────────────────────────────────
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            Vec3 dir = getPotionThrowDir(player);
            return buildResult(player, level, ProjectileType.EXP_BOTTLE, dir, 1.0f, false);
        }

        // ── Fishing Rod ───────────────────────────────────────────────────────
        if (stack.is(Items.FISHING_ROD) && player.fishing == null) {
            return buildFishingRodResult(player, level);
        }

        return null;
    }

    // ── Build helpers ─────────────────────────────────────────────────────────

    // Caller supplies the (smoothed) player velocity to avoid per-frame jitter.
    private static Vec3 _playerVel = Vec3.ZERO;

    private static TrajectoryResult buildResult(LocalPlayer player, ClientLevel level,
                                                 ProjectileType type, Vec3 lookDir,
                                                 float speedScale, boolean multishot) {
        Vec3 origin = player.getEyePosition(1.0f);
        float speed = type.baseSpeed * speedScale;
        Vec3 playerVel = _playerVel;

        if (multishot) {
            var center = simulateArc(origin, lookDir.scale(speed).add(playerVel),                type, level, player);
            var left   = simulateArc(origin, rotateY(lookDir, -10f).scale(speed).add(playerVel), type, level, player);
            var right  = simulateArc(origin, rotateY(lookDir,  10f).scale(speed).add(playerVel), type, level, player);

            BlockPos impact = impactBlock(center.points());
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(
                    List.of(center.points(), left.points(), right.points()),
                    impact, dist, type, true, false, center.entityName());
        } else {
            var result = simulateArc(origin, lookDir.scale(speed).add(playerVel), type, level, player);
            BlockPos impact = impactBlock(result.points());
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(
                    List.of(result.points()), impact, dist, type, false, false, result.entityName());
        }
    }

    private static TrajectoryResult buildFireworkResult(LocalPlayer player, ClientLevel level,
                                                         ChargedProjectiles charged, boolean multishot) {
        Vec3 origin = player.getEyePosition(1.0f);
        Vec3 look   = player.getLookAngle();
        Vec3 pv     = _playerVel;
        int lifetime = computeFireworkLifetime(charged);
        float fw = ProjectileType.CROSSBOW_FIREWORK.baseSpeed;

        if (multishot) {
            var center = simulateFirework(origin, look.scale(fw).add(pv),                lifetime, level, player);
            var left   = simulateFirework(origin, rotateY(look, -10f).scale(fw).add(pv), lifetime, level, player);
            var right  = simulateFirework(origin, rotateY(look,  10f).scale(fw).add(pv), lifetime, level, player);

            BlockPos impact = impactBlock(center.points());
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(
                    List.of(center.points(), left.points(), right.points()),
                    impact, dist, ProjectileType.CROSSBOW_FIREWORK, true, false, center.entityName());
        } else {
            var result = simulateFirework(origin, look.scale(fw).add(pv), lifetime, level, player);
            BlockPos impact = impactBlock(result.points());
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(
                    List.of(result.points()), impact, dist, ProjectileType.CROSSBOW_FIREWORK, false, false, result.entityName());
        }
    }

    private static TrajectoryResult buildFishingRodResult(LocalPlayer player, ClientLevel level) {
        float xRot = player.getXRot();
        float yRot = player.getYRot();
        float h = Mth.cos(-yRot * Mth.DEG_TO_RAD);
        float i = Mth.sin(-yRot * Mth.DEG_TO_RAD);
        float j = -Mth.cos(-xRot * Mth.DEG_TO_RAD);
        float k = Mth.sin(-xRot * Mth.DEG_TO_RAD);

        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 origin = new Vec3(eyePos.x - i * 0.3, eyePos.y, eyePos.z - h * 0.3);

        double velY = (j != 0) ? Mth.clamp(-(double)(k / j), -5.0, 5.0) : (k > 0 ? -5.0 : 5.0);
        Vec3 rawVel = new Vec3(-i, velY, -h);
        double len = rawVel.length();
        Vec3 vel = (len > 0 ? rawVel.scale(0.6 / len + 0.5) : rawVel).add(_playerVel);

        var result = simulateArc(origin, vel, ProjectileType.FISHING_ROD, level, player);
        BlockPos impact = impactBlock(result.points());
        double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
        return new TrajectoryResult(
                List.of(result.points()), impact, dist, ProjectileType.FISHING_ROD, false, false, result.entityName());
    }

    // ── Simulation loops ─────────────────────────────────────────────────────

    private record ArcResult(List<Vec3> points, String entityName) {}

    private static ArcResult simulateArc(Vec3 origin, Vec3 initialVel,
                                          ProjectileType type, ClientLevel level, LocalPlayer player) {
        List<Vec3> points = new ArrayList<>();
        Vec3 pos = origin;
        Vec3 vel = initialVel;

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            points.add(pos);

            boolean wet = type.isAffectedByWater() && inWater(level, pos);
            if (wet) {
                vel = vel.scale(type.waterDrag);
            } else {
                vel = new Vec3(vel.x, vel.y - type.gravity, vel.z).scale(type.drag);
            }
            Vec3 next = pos.add(vel);

            BlockHitResult hit = level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getLocation());
                break;
            }

            EntityHit eh = checkSegmentEntityHit(level, player, pos, next);
            if (eh != null) {
                points.add(eh.hitPos());
                return new ArcResult(points, eh.name());
            }

            pos = next;
        }
        return new ArcResult(points, null);
    }

    private static ArcResult simulateFirework(Vec3 origin, Vec3 initialVel,
                                               int lifetime, ClientLevel level, LocalPlayer player) {
        List<Vec3> points = new ArrayList<>();
        Vec3 pos = origin;
        Vec3 vel = initialVel;

        for (int tick = 0; tick < lifetime && tick < MAX_TICKS; tick++) {
            points.add(pos);
            vel = inWater(level, pos) ? vel.scale(0.6) : vel.scale(1.15);
            Vec3 next = pos.add(vel);

            BlockHitResult hit = level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getLocation());
                break;
            }

            EntityHit eh = checkSegmentEntityHit(level, player, pos, next);
            if (eh != null) {
                points.add(eh.hitPos());
                return new ArcResult(points, eh.name());
            }

            pos = next;
        }
        return new ArcResult(points, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private record EntityHit(String name, Vec3 hitPos) {}

    /** Checks entity collision along the full segment [from→to] using AABB raycast (PTP style). */
    private static EntityHit checkSegmentEntityHit(ClientLevel level, LocalPlayer player, Vec3 from, Vec3 to) {
        AABB seg = new AABB(from, to).inflate(0.5);
        LivingEntity best = null;
        Vec3 bestHit = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, seg,
                ent -> ent != player && !ent.isSpectator() && ent.isAlive())) {
            Optional<Vec3> res = e.getBoundingBox().inflate(e.getPickRadius()).clip(from, to);
            if (res.isPresent()) {
                double d = from.distanceToSqr(res.get());
                if (d < bestD) { bestD = d; best = e; bestHit = res.get(); }
            }
        }
        return best != null ? new EntityHit(best.getDisplayName().getString(), bestHit) : null;
    }

    /** Returns a throw direction with a pitch offset (negative = more upward). */
    private static Vec3 getPotionThrowDir(LocalPlayer player) {
        float pitch = player.getXRot();
        float yaw   = player.getYRot();
        float kx = -Mth.sin(yaw   * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        float ky = -Mth.sin((pitch - 20.0f) * Mth.DEG_TO_RAD);
        float kz =  Mth.cos(yaw   * Mth.DEG_TO_RAD) * Mth.cos(pitch * Mth.DEG_TO_RAD);
        return new Vec3(kx, ky, kz).normalize();
    }

    private static boolean inWater(ClientLevel level, Vec3 pos) {
        return level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
    }

    private static float computeBowPull(LocalPlayer player) {
        int used = 72000 - player.getUseItemRemainingTicks();
        float t = used / 20.0f;
        t = (t * t + t * 2.0f) / 3.0f;
        return Mth.clamp(t, 0.0f, 1.0f);
    }

    private static int computeFireworkLifetime(ChargedProjectiles charged) {
        for (var proj : charged.items()) {
            if (proj.is(Items.FIREWORK_ROCKET)) {
                Fireworks fw = proj.get(DataComponents.FIREWORKS);
                if (fw != null) {
                    int gunpowder = fw.flightDuration() & 0xFF;
                    return 10 * (gunpowder + 1) + 5;
                }
            }
        }
        return 15;
    }

    private static boolean hasEnchantment(ItemStack stack, ResourceKey<Enchantment> key) {
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants == null) return false;
        for (Holder<Enchantment> h : enchants.keySet()) {
            if (h.is(key)) return true;
        }
        return false;
    }

    private static Vec3 rotateY(Vec3 v, float angleDeg) {
        float rad = angleDeg * Mth.DEG_TO_RAD;
        float cos = Mth.cos(rad), sin = Mth.sin(rad);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    private static BlockPos impactBlock(List<Vec3> arc) {
        if (arc.isEmpty()) return null;
        return BlockPos.containing(arc.get(arc.size() - 1));
    }
}
