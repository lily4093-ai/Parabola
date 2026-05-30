package net.beady.parabola.trajectory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * arcs: one list per arc. Index 0 = center, 1 = left (-10°), 2 = right (+10°).
 * Multishot produces 3 arcs; all others produce 1.
 */
public record TrajectoryResult(
        List<List<Vec3>> arcs,
        BlockPos impactPos,
        double impactDistance,
        ProjectileType type,
        boolean isMultishot,
        boolean isRiptide
) {
    public List<Vec3> centerArc() {
        return arcs.isEmpty() ? List.of() : arcs.get(0);
    }

    public boolean hasImpact() {
        return impactPos != null;
    }
}
