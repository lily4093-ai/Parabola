package net.beady.parabola.trajectory;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class TrajectorySimulator {

    private static final int MAX_TICKS = 240;

    private TrajectorySimulator() {}

    /**
     * Returns null when no supported item is held or conditions aren't met.
     */
    public static TrajectoryResult simulate(LocalPlayer player, ClientLevel level) {
        if (player == null || level == null) return null;
        if (player.isDeadOrDying()) return null;

        ItemStack stack = player.getMainHandItem();
        Item item = stack.getItem();

        // ── Trident ─────────────────────────────────────────────────────────
        if (item instanceof TridentItem) {
            if (hasEnchantment(stack, Enchantments.RIPTIDE)) {
                if (player.isInWaterOrRain()) {
                    // Riptide active: show text, no arc
                    return new TrajectoryResult(List.of(), null, 0, ProjectileType.TRIDENT, false, true);
                }
                // Riptide but not in water/rain — fall through to normal trident
            }
            return buildResult(player, level, stack, ProjectileType.TRIDENT, 1.0f, false);
        }

        // ── Bow ──────────────────────────────────────────────────────────────
        if (item instanceof BowItem) {
            if (!player.isUsingItem()) return null;
            float pull = computeBowPull(player);
            if (pull < 0.01f) return null;
            return buildResult(player, level, stack, ProjectileType.BOW, pull, false);
        }

        // ── Crossbow ─────────────────────────────────────────────────────────
        if (item instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack)) return null;
            ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (charged == null || charged.isEmpty()) return null;

            boolean isFirework = charged.getItems().stream()
                    .anyMatch(p -> p.getItem() instanceof FireworkRocketItem);
            boolean multishot = hasEnchantment(stack, Enchantments.MULTISHOT);

            if (isFirework) {
                return buildFireworkResult(player, level, charged, multishot);
            } else {
                return buildResult(player, level, stack, ProjectileType.CROSSBOW_ARROW, 1.0f, multishot);
            }
        }

        // ── Ender Pearl ───────────────────────────────────────────────────────
        if (item instanceof EnderPearlItem) {
            return buildResult(player, level, stack, ProjectileType.ENDER_PEARL, 1.0f, false);
        }

        // ── Snowball / Egg ────────────────────────────────────────────────────
        if (item instanceof SnowballItem || item instanceof EggItem) {
            return buildResult(player, level, stack, ProjectileType.SNOWBALL, 1.0f, false);
        }

        return null;
    }

    // ── Standard arc helper ──────────────────────────────────────────────────

    private static TrajectoryResult buildResult(LocalPlayer player, ClientLevel level,
                                                 ItemStack stack, ProjectileType type,
                                                 float speedScale, boolean multishot) {
        Vec3 origin = player.getEyePosition(1.0f);
        Vec3 look   = player.getLookAngle();
        float speed = type.baseSpeed * speedScale;

        if (multishot) {
            Vec3 velCenter = look.scale(speed);
            Vec3 velLeft   = rotateY(look, -10f).scale(speed);
            Vec3 velRight  = rotateY(look,  10f).scale(speed);

            List<Vec3> center = simulateStandard(origin, velCenter, type.gravity, type.drag, level, player);
            List<Vec3> left   = simulateStandard(origin, velLeft,   type.gravity, type.drag, level, player);
            List<Vec3> right  = simulateStandard(origin, velRight,  type.gravity, type.drag, level, player);

            BlockPos impact = impactBlock(center);
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(List.of(center, left, right), impact, dist, type, true, false);
        } else {
            Vec3 vel = look.scale(speed);
            List<Vec3> arc = simulateStandard(origin, vel, type.gravity, type.drag, level, player);
            BlockPos impact = impactBlock(arc);
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(List.of(arc), impact, dist, type, false, false);
        }
    }

    private static TrajectoryResult buildFireworkResult(LocalPlayer player, ClientLevel level,
                                                         ChargedProjectiles charged, boolean multishot) {
        Vec3 origin = player.getEyePosition(1.0f);
        Vec3 look   = player.getLookAngle();
        int lifetime = computeFireworkLifetime(charged);

        if (multishot) {
            Vec3 vCenter = look.scale(ProjectileType.CROSSBOW_FIREWORK.baseSpeed);
            Vec3 vLeft   = rotateY(look, -10f).scale(ProjectileType.CROSSBOW_FIREWORK.baseSpeed);
            Vec3 vRight  = rotateY(look,  10f).scale(ProjectileType.CROSSBOW_FIREWORK.baseSpeed);

            List<Vec3> center = simulateFirework(origin, vCenter, lifetime, level, player);
            List<Vec3> left   = simulateFirework(origin, vLeft,   lifetime, level, player);
            List<Vec3> right  = simulateFirework(origin, vRight,  lifetime, level, player);

            BlockPos impact = impactBlock(center);
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(List.of(center, left, right), impact, dist,
                    ProjectileType.CROSSBOW_FIREWORK, true, false);
        } else {
            Vec3 vel = look.scale(ProjectileType.CROSSBOW_FIREWORK.baseSpeed);
            List<Vec3> arc = simulateFirework(origin, vel, lifetime, level, player);
            BlockPos impact = impactBlock(arc);
            double dist = impact != null ? origin.distanceTo(Vec3.atCenterOf(impact)) : 0;
            return new TrajectoryResult(List.of(arc), impact, dist,
                    ProjectileType.CROSSBOW_FIREWORK, false, false);
        }
    }

    // ── Simulation loops ─────────────────────────────────────────────────────

    /**
     * Standard physics: gravity subtracted from yVel, then drag applied.
     * Matches Minecraft's AbstractArrow tick order.
     */
    private static List<Vec3> simulateStandard(Vec3 origin, Vec3 initialVel,
                                                float gravity, float drag,
                                                ClientLevel level, LocalPlayer player) {
        List<Vec3> points = new ArrayList<>();
        Vec3 pos = origin;
        Vec3 vel = initialVel;

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            points.add(pos);
            vel = new Vec3(vel.x, vel.y - gravity, vel.z).scale(drag);
            Vec3 next = pos.add(vel);

            BlockHitResult hit = level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getLocation());
                break;
            }
            pos = next;
        }
        return points;
    }

    /**
     * Firework physics: horizontal acceleration, upward acceleration (no gravity).
     */
    private static List<Vec3> simulateFirework(Vec3 origin, Vec3 initialVel,
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
            pos = next;
        }
        return points;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float computeBowPull(LocalPlayer player) {
        // usedTicks starts at 0, full power at 20 ticks (1 second)
        int used = 72000 - player.getUseItemRemainingTicks();
        float t = used / 20.0f;
        t = (t * t + t * 2.0f) / 3.0f;
        return Mth.clamp(t, 0.0f, 1.0f);
    }

    private static int computeFireworkLifetime(ChargedProjectiles charged) {
        // Find the first firework rocket and read its flight_duration
        for (ItemStack proj : charged.getItems()) {
            Fireworks fw = proj.get(DataComponents.FIREWORKS);
            if (fw != null) {
                int gunpowder = fw.flightDuration() & 0xFF; // byte → unsigned int
                return 10 * (gunpowder + 1) + 5; // midpoint of 0–11 random extra ticks
            }
        }
        return 15; // fallback: 1 gunpowder
    }

    private static boolean hasEnchantment(ItemStack stack, ResourceKey<Enchantment> key) {
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants == null) return false;
        for (Holder<Enchantment> h : enchants.keySet()) {
            if (h.is(key)) return true;
        }
        return false;
    }

    /**
     * Rotates a direction vector around the Y-axis by angleDeg degrees.
     * Positive = clockwise when viewed from above (right strafe).
     */
    private static Vec3 rotateY(Vec3 v, float angleDeg) {
        float rad = angleDeg * Mth.DEG_TO_RAD;
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        return new Vec3(
                v.x * cos + v.z * sin,
                v.y,
                -v.x * sin + v.z * cos
        );
    }

    private static BlockPos impactBlock(List<Vec3> arc) {
        if (arc.isEmpty()) return null;
        Vec3 last = arc.get(arc.size() - 1);
        return BlockPos.containing(last);
    }
}
