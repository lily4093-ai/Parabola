package net.beady.parabola.render;

import java.util.List;

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

        // ── Impact highlights ─────────────────────────────────────────────────
        float impAlpha = cfg.arcStyle.impactBoxAlpha / 100.0f;
        if (impAlpha > 0.0f) {
            VertexConsumer fillVc = context.bufferSource().getBuffer(RenderTypes.debugFilledBox());

            // Center arc: entity-sized box if entity hit, else block box
            if (!arcs.isEmpty() && !arcs.get(0).isEmpty()) {
                Vec3 last0 = arcs.get(0).get(arcs.get(0).size() - 1);
                if (result.hitEntity()) {
                    float hw = 0.4f;
                    addFilledBox(mat, fillVc,
                            (float)last0.x - hw, (float)last0.y - 0.9f, (float)last0.z - hw,
                            (float)last0.x + hw, (float)last0.y + 0.9f, (float)last0.z + hw,
                            1.0f, 0.2f, 0.2f, 0.8f);
                } else {
                    BlockPos imp = BlockPos.containing(last0);
                    addFilledBox(mat, fillVc,
                            imp.getX(), imp.getY(), imp.getZ(),
                            imp.getX() + 1f, imp.getY() + 1f, imp.getZ() + 1f,
                            r, g, b, impAlpha);
                }
            }

            // Side arcs (multishot): always block boxes at 60% alpha
            for (int i = 1; i < arcs.size(); i++) {
                if (arcs.get(i).isEmpty()) continue;
                BlockPos imp = BlockPos.containing(arcs.get(i).get(arcs.get(i).size() - 1));
                addFilledBox(mat, fillVc,
                        imp.getX(), imp.getY(), imp.getZ(),
                        imp.getX() + 1f, imp.getY() + 1f, imp.getZ() + 1f,
                        r, g, b, impAlpha * 0.6f);
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
