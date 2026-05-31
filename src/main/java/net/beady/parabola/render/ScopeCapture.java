package net.beady.parabola.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Captures a 4× zoomed region of the main framebuffer using GL blitting,
 * stores the result in a RenderTarget, and wraps it as an AbstractTexture
 * so GuiGraphicsExtractor.blit() can draw it in the HUD panel.
 */
public final class ScopeCapture {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("parabola", "dynamic/scope");

    private static final float ZOOM = 4.0f; // 4× zoom

    private static RenderTarget scopeTarget;
    private static boolean registered = false;

    private ScopeCapture() {}

    /**
     * Call at the very start of the HUD render, before any HUD element is drawn.
     * At that point the main framebuffer contains the complete world render.
     */
    public static boolean capture(Minecraft mc, int guiW, int guiH) {
        int scale   = mc.getWindow().getGuiScale();
        int physW   = Math.max(1, guiW  * scale);
        int physH   = Math.max(1, guiH  * scale);
        int screenW = mc.getWindow().getWidth();
        int screenH = mc.getWindow().getHeight();
        if (screenW <= 0 || screenH <= 0) return false;

        // (Re)create scope RenderTarget when the panel size changes.
        if (scopeTarget == null
                || scopeTarget.width  != physW
                || scopeTarget.height != physH) {
            if (scopeTarget != null) scopeTarget.destroyBuffers();
            // false = no depth buffer, false = not on macOS (depth-only quirk)
            scopeTarget = mc.getMainRenderTarget().createRenderTarget(physW, physH);

            if (registered) mc.getTextureManager().destroyTexture(ID);
            mc.getTextureManager().register(ID, new ScopeTexture());
            registered = true;
        }

        // Save current GL FBO bindings (main render target at HUD-render time).
        int mainFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        // Bind the scope target to obtain its FBO id, then restore main.
        scopeTarget.bindWrite(true);
        int scopeFbo = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);

        // Compute the zoom source region (center ZOOM_FRACTION of the screen).
        float half = 0.5f / ZOOM;
        int x0 = clamp((int) ((0.5f - half) * screenW), 0, screenW);
        int x1 = clamp((int) ((0.5f + half) * screenW), 0, screenW);
        // Swap y0/y1 in source so the image is Y-flipped: GL framebuffer Y=0 is at
        // the bottom of the window, but GUI UV Y=0 is at the top — the swap corrects this.
        int y0 = clamp((int) ((0.5f - half) * screenH), 0, screenH);
        int y1 = clamp((int) ((0.5f + half) * screenH), 0, screenH);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scopeFbo);
        // Y-flip: source reads y1..y0 (top→bottom in GL) → dest 0..physH (bottom→top).
        GL30.glBlitFramebuffer(x0, y1, x1, y0, 0, 0, physW, physH,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

        // Restore main framebuffer.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
        return true;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Thin AbstractTexture subclass that forwards to the scope RenderTarget's
     * colour texture view, which is what GuiGraphicsExtractor.blit() needs in
     * MC 26.1's GpuTexture-based rendering system.
     */
    private static final class ScopeTexture extends AbstractTexture {
        @Override
        public GpuTextureView getGlTextureView() {
            return scopeTarget == null ? null : scopeTarget.getColorTextureView();
        }

        @Override
        public void load(ResourceManager manager) { /* dynamic — managed externally */ }
    }
}
