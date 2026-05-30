package net.beady.parabola.render;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import me.shedaniel.autoconfig.AutoConfig;
import net.beady.parabola.config.ModConfig;
import net.beady.parabola.trajectory.ProjectileType;
import net.beady.parabola.trajectory.TrajectoryResult;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class TrajectoryRenderer {

    private static final RenderPipeline TRAJECTORY_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("parabola", "pipeline/trajectory_arc"))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static MappableRingBuffer ringBuffer;
    private static volatile RenderState renderState;

    private record RenderState(
            List<List<Vec3>> arcs,
            BlockPos impactPos,
            float r, float g, float b,
            boolean multishot,
            int dotSpacing,
            float dotRadius,
            float impactAlpha,
            boolean entityHit
    ) {}

    private TrajectoryRenderer() {}

    public static void register() {
        LevelRenderEvents.END_EXTRACTION.register(TrajectoryRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(TrajectoryRenderer::draw);
    }

    private static void extract(LevelExtractionContext ctx) {
        ModConfig cfg = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        if (!cfg.enabled || !cfg.showWorldArc) { renderState = null; return; }

        TrajectoryResult result = TrajectoryCache.getCached();
        if (result == null || result.isRiptide() || result.arcs().isEmpty()) {
            renderState = null;
            return;
        }

        List<List<Vec3>> arcsCopy = result.arcs().stream()
                .map(List::copyOf)
                .collect(Collectors.toUnmodifiableList());

        int rgb = cfg.rgbFor(result.type());
        ProjectileType type = result.type();
        renderState = new RenderState(
                arcsCopy, result.impactPos(),
                type.r(rgb), type.g(rgb), type.b(rgb),
                result.isMultishot(),
                cfg.arcStyle.dotEveryNTicks,
                cfg.arcStyle.dotSizeUnit * 0.01f,
                cfg.arcStyle.impactBoxAlpha / 100.0f,
                result.hitEntity()
        );
    }

    private static void draw(LevelRenderContext context) {
        RenderState state = renderState;
        if (state == null) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        PoseStack poseStack = context.poseStack();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4fc mat = poseStack.last().pose();

        BufferBuilder buf = new BufferBuilder(
                ALLOCATOR, TRAJECTORY_PIPELINE.getVertexFormatMode(), TRAJECTORY_PIPELINE.getVertexFormat());

        float r = state.r(), g = state.g(), b = state.b();

        List<List<Vec3>> arcs = state.arcs();
        if (state.multishot() && arcs.size() == 3) {
            addArcDots(buf, mat, arcs.get(0), r, g, b, 1.0f, state.dotSpacing(), state.dotRadius());
            addArcDots(buf, mat, arcs.get(1), r, g, b, 0.6f, state.dotSpacing(), state.dotRadius());
            addArcDots(buf, mat, arcs.get(2), r, g, b, 0.6f, state.dotSpacing(), state.dotRadius());
        } else if (!arcs.isEmpty()) {
            addArcDots(buf, mat, arcs.get(0), r, g, b, 1.0f, state.dotSpacing(), state.dotRadius());
        }

        if (state.impactPos() != null) {
            BlockPos imp = state.impactPos();
            // Entity hit: bright pulsing color; block hit: configured alpha
            float hitAlpha = state.entityHit() ? 0.75f : state.impactAlpha();
            float hr = state.entityHit() ? 1.0f : r;
            float hg = state.entityHit() ? 0.2f : g;
            float hb = state.entityHit() ? 0.2f : b;
            addFilledBox(buf, mat,
                    imp.getX(), imp.getY(), imp.getZ(),
                    imp.getX() + 1f, imp.getY() + 1f, imp.getZ() + 1f,
                    hr, hg, hb, hitAlpha);
        }

        poseStack.popPose();
        submitDraw(Minecraft.getInstance(), buf);
    }

    private static void addArcDots(BufferBuilder buf, Matrix4fc mat,
                                    List<Vec3> arc, float r, float g, float b, float a,
                                    int spacing, float rad) {
        for (int i = 0; i < arc.size(); i += spacing) {
            Vec3 p = arc.get(i);
            addFilledBox(buf, mat,
                    (float) p.x - rad, (float) p.y - rad, (float) p.z - rad,
                    (float) p.x + rad, (float) p.y + rad, (float) p.z + rad,
                    r, g, b, a);
        }
    }

    private static void addFilledBox(BufferBuilder buf, Matrix4fc mat,
                                      float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float r, float g, float b, float a) {
        buf.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);

        buf.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);

        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);

        buf.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);

        buf.addVertex(mat, x1, y2, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y2, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y2, z1).setColor(r, g, b, a);

        buf.addVertex(mat, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y1, z1).setColor(r, g, b, a);
        buf.addVertex(mat, x2, y1, z2).setColor(r, g, b, a);
        buf.addVertex(mat, x1, y1, z2).setColor(r, g, b, a);
    }

    private static void submitDraw(Minecraft mc, BufferBuilder builder) {
        MeshData mesh = builder.buildOrThrow();
        MeshData.DrawState drawState = mesh.drawState();
        VertexFormat format = drawState.format();

        int vbSize = drawState.vertexCount() * format.getVertexSize();
        if (ringBuffer == null || ringBuffer.size() < vbSize) {
            if (ringBuffer != null) ringBuffer.close();
            ringBuffer = new MappableRingBuffer(() -> "parabola trajectory",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vbSize);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                ringBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
        }

        RenderSystem.AutoStorageIndexBuffer idxStore = RenderSystem.getSequentialBuffer(TRAJECTORY_PIPELINE.getVertexFormatMode());
        GpuBuffer indices = idxStore.getBuffer(drawState.indexCount());
        VertexFormat.IndexType idxType = idxStore.type();

        GpuBufferSlice dynTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1f, 1f, 1f, 1f),
                new Vector3f(),
                new Matrix4f()
        );

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "parabola trajectory draw",
                mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(TRAJECTORY_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynTransforms);
            pass.setVertexBuffer(0, ringBuffer.currentBuffer());
            pass.setIndexBuffer(indices, idxType);
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }

        mesh.close();
        ringBuffer.rotate();
    }
}
