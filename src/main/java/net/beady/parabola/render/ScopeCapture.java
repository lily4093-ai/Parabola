package net.beady.parabola.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/**
 * Captures a zoomed region of the main framebuffer into a reusable GL texture.
 * Call capture() at the very start of the HUD render (before any drawing) to get
 * a real Minecraft zoom for the scope panel. Falls back gracefully if GL fails.
 */
public final class ScopeCapture {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("parabola", "dynamic/scope");

    /** 4× zoom: sample the center 25 % of the screen */
    private static final float ZOOM = 4.0f;

    private static int scopeFbo = -1;
    private static int scopeTex = -1;
    private static int allocW   = 0;
    private static int allocH   = 0;
    private static boolean registered = false;

    private ScopeCapture() {}

    /**
     * Blits the zoomed center region from the main framebuffer into the scope texture.
     *
     * @param guiW width  in GUI (virtual) pixels that the scope panel occupies
     * @param guiH height in GUI (virtual) pixels
     * @return true on success — call g.blit(ID, …) to draw the result
     */
    public static boolean capture(Minecraft mc, int guiW, int guiH) {
        int scale   = mc.getWindow().getGuiScale();
        int fboW    = Math.max(1, guiW * scale);
        int fboH    = Math.max(1, guiH * scale);
        int screenW = mc.getWindow().getWidth();
        int screenH = mc.getWindow().getHeight();
        if (screenW <= 0 || screenH <= 0) return false;

        // Save current GL framebuffer bindings so we can restore them.
        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        // (Re)create scope FBO when size changes.
        if (scopeFbo == -1 || allocW != fboW || allocH != fboH) {
            destroyGL();
            allocW = fboW;
            allocH = fboH;

            scopeTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, scopeTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8,
                    fboW, fboH, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            scopeFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, scopeFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, scopeTex, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevDraw);

            // Register with TextureManager once so g.blit(ID, …) can resolve it.
            if (registered) mc.getTextureManager().destroyTexture(ID);
            mc.getTextureManager().register(ID, new ScopeTexture());
            registered = true;
        }

        // Source region: center ZOOM_FRACTION of the screen in GL pixel coordinates.
        // GL Y = 0 is at the bottom of the window.
        float half = 0.5f / ZOOM;
        int x0 = clamp((int)((0.5f - half) * screenW), 0, screenW);
        int x1 = clamp((int)((0.5f + half) * screenW), 0, screenW);
        int y0 = clamp((int)((0.5f - half) * screenH), 0, screenH); // GL bottom of region
        int y1 = clamp((int)((0.5f + half) * screenH), 0, screenH); // GL top  of region

        // Blit main FBO → scope FBO with Y-flip so the scope texture is GUI-compatible.
        // Swapping srcY0/srcY1 (y1, y0 instead of y0, y1) makes UV (0,0) = screen top,
        // which matches what GuiGraphicsExtractor.blit() expects.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevDraw);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scopeFbo);
        GL30.glBlitFramebuffer(x0, y1, x1, y0, 0, 0, fboW, fboH,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

        // Restore GL state.
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        return true;
    }

    private static void destroyGL() {
        if (scopeFbo != -1) { GL30.glDeleteFramebuffers(scopeFbo); scopeFbo = -1; }
        if (scopeTex != -1) { GL11.glDeleteTextures(scopeTex);     scopeTex = -1; }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Thin AbstractTexture wrapper so TextureManager can resolve our GL texture ID. */
    private static final class ScopeTexture extends AbstractTexture {
        @Override public void load(ResourceManager mgr) {}
        @Override public int  getId()                   { return scopeTex == -1 ? 0 : scopeTex; }
    }
}
