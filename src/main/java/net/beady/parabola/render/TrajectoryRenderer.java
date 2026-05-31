package net.beady.parabola.render;

import java.util.List;
import java.util.stream.Collectors;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import me.shedaniel.autoconfig.AutoConfig;
import net.beady.parabola.config.ModConfig;
import net.beady.parabola.trajectory.ProjectileType;
import net.beady.parabola.trajectory.TrajectoryResult;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class TrajectoryRenderer {

    private static final float LINE_WIDTH = 2.0f;

    private TrajectoryRenderer() {}

    public static void register() {
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(TrajectoryRenderer::render);
    }

    private static void render(LevelRenderContext context) {
        ModConfig cfg = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        if (!cfg.enabled || !cfg.showWorldArc) return;

        TrajectoryResult result = TrajectoryCache.getCached();
        if (result == null || result.isRiptide() || result.arcs().isEmpty()) return;
        if (!cfg.isTypeEnabled(result.type())) return;

        int rgb = cfg.rgbFor(result.type());
        ProjectileType type = result.type();
        float r = type.r(rgb), g = type.g(rgb), b = type.b(rgb);
        float alpha = cfg.getAlpha();

        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.poseStack();

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f mat = pose.pose();

        // ── Trajectory lines ─────────────────────────────────────────────────
        VertexConsumer lineVc = context.bufferSource().getBuffer(RenderTypes.lines());
        List<List<Vec3>> arcs = result.arcs();
        ModConfig.TrajectoryStyle style = cfg.arcStyle.trajectoryStyle;

        for (int i = 0; i < arcs.size(); i++) {
            float a = (i == 0) ? alpha : alpha * 0.6f;
            renderArcLine(pose, mat, lineVc, arcs.get(i), r, g, b, a, style);
        }

        // ── Impact block highlights ───────────────────────────────────────────
        float impAlpha = cfg.arcStyle.impactBoxAlpha / 100.0f;
        if (impAlpha > 0.0f) {
            List<BlockPos> impacts = arcs.stream()
                    .map(arc -> arc.isEmpty() ? null : BlockPos.containing(arc.get(arc.size() - 1)))
                    .collect(Collectors.toList());

            VertexConsumer fillVc = context.bufferSource().getBuffer(RenderTypes.debugFilledBox());
            for (int i = 0; i < impacts.size(); i++) {
                BlockPos imp = impacts.get(i);
                if (imp == null) continue;
                boolean isCenter = (i == 0);
                boolean entityHit = result.hitEntity() && isCenter;
                float fa = entityHit ? 0.75f : impAlpha * (isCenter ? 1.0f : 0.6f);
                float fr = entityHit ? 1.0f : r;
                float fg = entityHit ? 0.2f : g;
                float fb = entityHit ? 0.2f : b;
                addFilledBox(mat, fillVc,
                        imp.getX(), imp.getY(), imp.getZ(),
                        imp.getX() + 1f, imp.getY() + 1f, imp.getZ() + 1f,
                        fr, fg, fb, fa);
            }
        }

        poseStack.popPose();
    }

    private static void renderArcLine(PoseStack.Pose pose, Matrix4f mat, VertexConsumer vc,
                                       List<Vec3> arc, float r, float g, float b, float a,
                                       ModConfig.TrajectoryStyle style) {
        if (arc.size() < 2) return;
        for (int i = 0; i < arc.size() - 1; i++) {
            Vec3 p0 = arc.get(i);
            Vec3 p1 = arc.get(i + 1);
            Vec3 dir = p1.subtract(p0);
            double len = dir.length();
            if (len == 0) continue;

            Vec3 end = switch (style) {
                case DASHED -> p0.add(dir.scale(0.5));
                case DOTTED -> p0.add(dir.scale(0.15));
                default     -> p1;
            };

            float nx = (float)(dir.x / len), ny = (float)(dir.y / len), nz = (float)(dir.z / len);
            vc.addVertex(mat, (float)p0.x, (float)p0.y, (float)p0.z)
              .setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
            vc.addVertex(mat, (float)end.x, (float)end.y, (float)end.z)
              .setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
        }
    }

    private static void addFilledBox(Matrix4f mat, VertexConsumer vc,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float r, float g, float b, float a) {
        // X-
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        // X+
        vc.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        // Y-
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        // Y+
        vc.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        // Z- (z2 face)
        vc.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        // Z+ (z1 face)
        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
    }
}
