package net.beady.parabola.trajectory;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

public record TrajectoryResult(
        List<List<Vec3>> arcs,
        BlockPos impactPos,
        double impactDistance,
        ProjectileType type,
        boolean isMultishot,
        boolean isRiptide,
        @Nullable String hitEntityName
) {
    public List<Vec3> centerArc() {
        return arcs.isEmpty() ? List.of() : arcs.get(0);
    }

    public boolean hasImpact() {
        return impactPos != null;
    }

    public boolean hitEntity() {
        return hitEntityName != null;
    }
}
