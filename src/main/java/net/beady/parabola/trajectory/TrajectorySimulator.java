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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;


import java.util.ArrayList;
import java.util.List;

public final class TrajectorySimulator {

    private static final int MAX_TICKS = 240;

    private TrajectorySimulator() {}

    public static TrajectoryResult simulate(LocalPlayer player, ClientLevel level) {
        if (player == null || level == null) return null;
        if (player.isDeadOrDying()) return null;

        ItemStack stack = player.getMainHandItem();

        // ── Trident ─────────────────────────────────────────────────────────
        if (stack.is(Items.TRIDENT)) {
            if (hasEnchantment(stack, Enchantments.RIPTIDE)) {
                if (player.isInWaterOrRain()) {
                    return new TrajectoryResult(List.of(), null, 0, ProjectileType.TRIDENT, false, true, null);
                }
            }
            return buildResult(player, level, stack, ProjectileType.TRIDENT, 1.0f, false);
        }

        // ── Bow ──────────────────────────────────────────────────────────────
        if (stack.is(Items.BOW)) {
            if (!player.isUsingItem()) return null;
            float pull = computeBowPull(player);
            if (pull < 0.01f) return null;
            return buildResult(player, level, stack, ProjectileType.BOW, pull, false);
        }

        // ── Crossbow ─────────────────────────────────────────────────────────
        if (stack.is(Items.CROSSBOW)) {
            ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (charged == null || charged.isEmpty()) return null;

            boolean isFirework = charged.items().stream()
                    .anyMatch(p -> p.is(Items.FIREWORK_ROCKET));
            boolean multishot = hasEnchantment(stack, Enchantments.MULTISHOT);

            if (isFirework) {
                return buildFireworkResult(player, level, charged, multishot);
            } else {
                return buildResult(player, level, stack, ProjectileType.CROSSBOW_ARROW, 1.0f, multishot);
            }
        }

        // ── Ender Pearl ───────────────────────────────────────────────────────
        if (stack.is(Items.ENDER_PEARL)) {
            return buildResult(player, level, stack, ProjectileType.ENDER_PEARL, 1.0f, false);
        }

        // ── Snowball / Egg ────────────────────────────────────────────────────
        if (stack.is(Items.SNOWBALL) || stack.is(Items.EGG)) {
            return buildResult(player, level, stack, ProjectileType.SNOWBALL, 1.0f, false);
        }

        // ── Wind Charge ───────────────────────────────────────────────────────
        if (stack.is(Items.WIND_CHARGE)) {
            return buildResult(player, level, stack, ProjectileType.WIND_CHARGE, 1.0f, false);
        }

        return null;
    }

    // ── Standard arc ─────────────────────────────────────────────────────────

    private static TrajectoryResult buildResult(LocalPlayer player, ClientLevel level,
                                                 ItemStack stack, ProjectileType type,
                                                 float speedScale, boolean multishot) {
        Vec3 origin = player.getEyePosition(1.0f);
        Vec3 look   = player.getLookAngle();
        float speed = type.baseSpeed * speedScale;

        if (multishot) {
            var center = simulateArc(origin, look.scale(speed),          type, level, player);
            var left   = simulateArc(origin, rotateY(look, -10f).scale(speed), type, level, player);
            var right  = simulateArc(origin, rotateY(look,  10f).scale(speed), type, level, player);

            BlockPos impact = impactBlock(center.points());
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(
                    List.of(center.points(), left.points(), right.points()),
                    impact, dist, type, true, false, center.entityName());
        } else {
            var result = simulateArc(origin, look.scale(speed), type, level, player);
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
        int lifetime = computeFireworkLifetime(charged);

        if (multishot) {
            var center = simulateFirework(origin, look.scale(ProjectileType.CROSSBOW_FIREWORK.baseSpeed), lifetime, level, player);
            var left   = simulateFirework(origin, rotateY(look, -10f).scale(ProjectileType.CROSSBOW_FIREWORK.baseSpeed), lifetime, level, player);
            var right  = simulateFirework(origin, rotateY(look,  10f).scale(ProjectileType.CROSSBOW_FIREWORK.baseSpeed), lifetime, level, player);

            BlockPos impact = impactBlock(center.points());
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(
                    List.of(center.points(), left.points(), right.points()),
                    impact, dist, ProjectileType.CROSSBOW_FIREWORK, true, false, center.entityName());
        } else {
            var result = simulateFirework(origin, look.scale(ProjectileType.CROSSBOW_FIREWORK.baseSpeed), lifetime, level, player);
            BlockPos impact = impactBlock(result.points());
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(
                    List.of(result.points()), impact, dist, ProjectileType.CROSSBOW_FIREWORK, false, false, result.entityName());
        }
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
            vel = new Vec3(vel.x, vel.y - type.gravity, vel.z).scale(type.drag);
            Vec3 next = pos.add(vel);

            // Block collision
            BlockHitResult hit = level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getLocation());
                break;
            }

            // Entity collision — check every 5 ticks to keep simulation cheap
            if (tick % 5 == 0) {
                Vec3 mid = pos.lerp(next, 0.5);
                String entityHit = checkEntityHit(level, player, mid);
                if (entityHit != null) {
                    points.add(mid);
                    return new ArcResult(points, entityHit);
                }
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
            vel = new Vec3(vel.x * 1.15, vel.y + 0.04, vel.z * 1.15);
            Vec3 next = pos.add(vel);

            BlockHitResult hit = level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getLocation());
                break;
            }

            Vec3 mid = pos.lerp(next, 0.5);
            String entityHit = checkEntityHit(level, player, mid);
            if (entityHit != null) {
                points.add(mid);
                return new ArcResult(points, entityHit);
            }

            pos = next;
        }
        return new ArcResult(points, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String checkEntityHit(ClientLevel level, LocalPlayer player, Vec3 pos) {
        AABB searchBox = AABB.ofSize(pos, 0.8, 1.8, 0.8);
        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && !e.isSpectator() && e.isAlive());
        if (!entities.isEmpty()) {
            return entities.get(0).getDisplayName().getString();
        }
        return null;
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
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    private static BlockPos impactBlock(List<Vec3> arc) {
        if (arc.isEmpty()) return null;
        Vec3 last = arc.get(arc.size() - 1);
        return BlockPos.containing(last);
    }
}
