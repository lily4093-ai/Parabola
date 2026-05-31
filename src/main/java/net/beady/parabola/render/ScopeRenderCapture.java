package net.beady.parabola.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.beady.parabola.mixin.CameraAccessor;
import net.beady.parabola.trajectory.TrajectoryResult;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class ScopeRenderCapture {

    private static final int SCOPE_W = 160;
    private static final int SCOPE_H = 90;
    private static final long CAPTURE_INTERVAL_MS = 100L;

    private static TextureTarget scopeTarget = null;
    private static long lastCaptureMs = 0L;
    private static BlockPos lastImpact = null;

    // Guard against recursive calls (captureScope itself calls renderLevel)
    static volatile boolean isCapturing = false;

    private ScopeRenderCapture() {}

    /**
     * Called from GameRendererMixin right before the normal renderLevel() runs.
     * At this point, extractCamera() has already populated CameraRenderState.
     * We do a secondary render with the scope camera, blit to our texture, then restore.
     */
    public static void captureScope(Minecraft mc, DeltaTracker dt) {
        if (isCapturing) return;
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        TrajectoryResult result = TrajectoryCache.peekCached();
        if (result == null || !result.hasImpact() || result.impactPos() == null) return;

        BlockPos impact = result.impactPos();
        long now = System.currentTimeMillis();
        boolean impactSame = impact.equals(lastImpact);
        if (impactSame && now - lastCaptureMs < CAPTURE_INTERVAL_MS) return;

        lastCaptureMs = now;
        lastImpact = impact;

        // Initialise offscreen target (once)
        if (scopeTarget == null) {
            scopeTarget = new TextureTarget("parabola_scope", SCOPE_W, SCOPE_H, true);
        }

        // Compute scope camera: 5 blocks above impact, slightly toward player horizontally
        Vec3 impactCenter = Vec3.atCenterOf(impact);
        Vec3 playerPos = mc.player.getEyePosition(1.0f);
        Vec3 delta = impactCenter.subtract(playerPos);
        double hLen = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        double hx = hLen > 0 ? delta.x / hLen : 0;
        double hz = hLen > 0 ? delta.z / hLen : 0;
        Vec3 scopePos = impactCenter.add(0, 5, 0).subtract(hx * 2, 0, hz * 2);

        Vec3 toImpact = impactCenter.subtract(scopePos).normalize();
        float scopeYaw   = (float) Math.toDegrees(Math.atan2(-toImpact.x, toImpact.z));
        float scopePitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-toImpact.y, -1.0, 1.0)));

        // Get the CameraRenderState that renderLevel() will read from
        LevelRenderState lrs = mc.gameRenderer.getGameRenderState().levelRenderState;
        CameraRenderState crs = lrs.cameraRenderState;

        // Save fields we will overwrite
        Vec3    savedPos      = crs.pos;
        float   savedXRot     = crs.xRot;
        float   savedYRot     = crs.yRot;
        Quaternionf savedOri  = new Quaternionf(crs.orientation);
        Matrix4f savedViewRot = new Matrix4f(crs.viewRotationMatrix);
        Frustum savedFrustum  = new Frustum(crs.cullFrustum);

        // Save Camera object state so TrajectoryRenderer's camera.position() is correct
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3  savedCamPos  = cam.position();
        float savedCamXRot = cam.xRot();
        float savedCamYRot = cam.yRot();

        // Bring Camera to scope position so extractRenderState fills crs correctly
        CameraAccessor camAcc = (CameraAccessor) cam;
        camAcc.invokeSetPosition(scopePos.x, scopePos.y, scopePos.z);
        camAcc.invokeSetRotation(scopeYaw, scopePitch);

        float pt = dt.getGameTimeDeltaPartialTick(false);
        cam.extractRenderState(crs, pt);

        // Render scope view (writes to mainRenderTarget)
        isCapturing = true;
        try {
            mc.gameRenderer.renderLevel(dt);
            // Copy result into our persistent scope texture
            mc.getMainRenderTarget().blitAndBlendToTexture(scopeTarget.getColorTextureView());
        } finally {
            isCapturing = false;
        }

        // Restore CameraRenderState
        crs.pos = savedPos;
        crs.xRot = savedXRot;
        crs.yRot = savedYRot;
        crs.orientation.set(savedOri);
        crs.viewRotationMatrix.set(savedViewRot);
        crs.cullFrustum.set(savedFrustum);

        // Restore Camera object (used by TrajectoryRenderer in the normal renderLevel)
        camAcc.invokeSetPosition(savedCamPos.x, savedCamPos.y, savedCamPos.z);
        camAcc.invokeSetRotation(savedCamYRot, savedCamXRot);
        // Re-extract so matrixPropertiesDirty flags are clean
        cam.extractRenderState(crs, pt);

        // extractRenderState above overwrites crs again — re-restore the saved values
        crs.pos = savedPos;
        crs.xRot = savedXRot;
        crs.yRot = savedYRot;
        crs.orientation.set(savedOri);
        crs.viewRotationMatrix.set(savedViewRot);
        crs.cullFrustum.set(savedFrustum);
    }

    public static GpuTextureView getScopeView() {
        return scopeTarget != null ? scopeTarget.getColorTextureView() : null;
    }

    public static GpuSampler getScopeSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static void invalidate() {
        lastImpact = null;
        lastCaptureMs = 0L;
    }
}
