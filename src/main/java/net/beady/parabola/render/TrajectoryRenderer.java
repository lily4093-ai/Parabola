package net.beady.parabola.render;

import me.shedaniel.autoconfig.AutoConfig;
import net.beady.parabola.config.ModConfig;
import net.beady.parabola.trajectory.ProjectileType;
import net.beady.parabola.trajectory.TrajectoryResult;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class TrajectoryRenderer {

    private TrajectoryRenderer() {}

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT.register(TrajectoryRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        ModConfig cfg = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        if (!cfg.enabled || !cfg.showWorldArc) return;

        TrajectoryResult result = TrajectoryCache.getCached();
        if (result == null || result.isRiptide()) return;
        if (result.arcs().isEmpty()) return;

        Camera camera = context.camera();
        Vec3 camPos = camera.getPosition();

        PoseStack poseStack = context.poseStack();
        MultiBufferSource bufferSource = context.bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        ProjectileType type = result.type();
        VertexConsumer consumer =
                bufferSource.getBuffer(RenderType.lines());

        List<List<Vec3>> arcs = result.arcs();

        if (result.isMultishot() && arcs.size() == 3) {
            // Center arc at full opacity
            renderArc(poseStack, consumer, arcs.get(0), type.r(), type.g(), type.b(), 1.0f, 0.1);
            // Side arcs at 60% opacity
            renderArc(poseStack, consumer, arcs.get(1), type.r(), type.g(), type.b(), 0.6f, 0.07);
            renderArc(poseStack, consumer, arcs.get(2), type.r(), type.g(), type.b(), 0.6f, 0.07);
        } else if (!arcs.isEmpty()) {
            renderArc(poseStack, consumer, arcs.get(0), type.r(), type.g(), type.b(), 1.0f, 0.1);
        }

        // Impact block wireframe
        if (result.hasImpact()) {
            BlockPos imp = result.impactPos();
            AABB box = new AABB(imp.getX(), imp.getY(), imp.getZ(),
                                imp.getX() + 1, imp.getY() + 1, imp.getZ() + 1);
            LevelRenderer.renderLineBox(poseStack, consumer, box,
                    type.r(), type.g(), type.b(), 1.0f);
        }

        poseStack.popPose();

        // Flush lines batch
        if (bufferSource instanceof MultiBufferSource.BufferSource immediate) {
            immediate.endBatch(RenderType.lines());
        }
    }

    /**
     * Renders each trajectory point as a tiny wireframe dot (small box).
     * Every other point is skipped to reduce visual clutter at close range.
     */
    private static void renderArc(PoseStack poseStack,
                                   VertexConsumer consumer,
                                   List<Vec3> arc, float r, float g, float b, float a, double radius) {
        for (int i = 0; i < arc.size(); i += 2) {
            Vec3 p = arc.get(i);
            AABB box = new AABB(p.x - radius, p.y - radius, p.z - radius,
                                p.x + radius, p.y + radius, p.z + radius);
            LevelRenderer.renderLineBox(poseStack, consumer, box, r, g, b, a);
        }
    }
}
