package net.beady.parabola.render;

import me.shedaniel.autoconfig.AutoConfig;
import net.beady.parabola.config.ModConfig;
import net.beady.parabola.trajectory.ProjectileType;
import net.beady.parabola.trajectory.TrajectoryResult;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class HudOverlayRenderer {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("parabola", "impact_scope");

    private static final int PANEL_W = 120;
    private static final int PANEL_H = 110;
    private static final int MARGIN  = 8;

    // Scope view area inside the panel (16:9 aspect, matching the scope render target)
    private static final int SCOPE_W = 96;
    private static final int SCOPE_H = 54;

    private static final int TEXT_DIM     = 0xFFAAAAAA;
    private static final int TEXT_HIT     = 0xFFFF5555;
    private static final int BORDER_OUTER = 0xFF1A1A1A;
    private static final int BORDER_INNER = 0xFF4B0082;

    private HudOverlayRenderer() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, HudOverlayRenderer::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.screen != null) return;

        ModConfig cfg = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        if (!cfg.enabled || !cfg.showHudPanel) return;

        TrajectoryResult result = TrajectoryCache.getCached();
        if (result == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // ── Riptide ──────────────────────────────────────────────────────────
        if (result.isRiptide()) {
            String txt = "⚡ Riptide";
            int tw = mc.font.width(txt);
            g.text(mc.font, txt, sw - tw - MARGIN - 1, sh - 22, 0xFF000000, false);
            g.text(mc.font, txt, sw - tw - MARGIN - 2, sh - 23, 0xFF55FFFF, false);
            return;
        }

        if (result.arcs().isEmpty() || !result.hasImpact()) return;

        int panelAlpha = (int) Math.round(cfg.arcStyle.hudPanelAlpha * 2.55);
        int px = sw - PANEL_W - MARGIN;
        int py = sh - PANEL_H - MARGIN;

        drawPanelBackground(g, px, py, PANEL_W, PANEL_H, panelAlpha);

        int rgb = cfg.rgbFor(result.type());
        int typeColor = 0xFF000000 | rgb;

        // ── Header ───────────────────────────────────────────────────────────
        g.text(mc.font, typeName(result.type(), result.isMultishot()), px + 5, py + 5, typeColor, false);
        String distStr = String.format("%.1fm", result.impactDistance());
        g.text(mc.font, distStr, px + PANEL_W - mc.font.width(distStr) - 5, py + 5, TEXT_DIM, false);
        g.fill(px + 5, py + 15, px + PANEL_W - 5, py + 16, 0x55FFFFFF);

        // ── Scope view (real Minecraft render from above impact) ─────────────
        int scopeX = px + (PANEL_W - SCOPE_W) / 2;
        int scopeY = py + 19;

        GpuTextureView scopeView = ScopeRenderCapture.getScopeView();
        if (scopeView != null) {
            GpuSampler sampler = ScopeRenderCapture.getScopeSampler();
            g.enableScissor(scopeX, scopeY, scopeX + SCOPE_W, scopeY + SCOPE_H);
            g.blit(scopeView, sampler, scopeX, scopeY, SCOPE_W, SCOPE_H, 0.0f, 0.0f, 1.0f, 1.0f);
            drawScopeCrosshair(g, scopeX, scopeY, SCOPE_W, SCOPE_H, result.hitEntity());
            g.disableScissor();
        } else {
            // Scope not yet captured: show placeholder
            g.fill(scopeX, scopeY, scopeX + SCOPE_W, scopeY + SCOPE_H, 0xFF111111);
            g.text(mc.font, "...", scopeX + SCOPE_W / 2 - 4, scopeY + SCOPE_H / 2 - 4, 0xFF444444, false);
        }

        // ── Bottom label ─────────────────────────────────────────────────────
        int labelY = scopeY + SCOPE_H + 3;
        if (result.hitEntity() && cfg.showEntityHit) {
            g.fill(px + 3, labelY - 1, px + PANEL_W - 3, labelY + 10, 0xBB660000);
            g.text(mc.font, "⚔ " + result.hitEntityName(), px + 5, labelY, TEXT_HIT, false);
        } else {
            ClientLevel level = mc.level;
            BlockPos impact = result.impactPos();
            if (level != null && impact != null) {
                g.text(mc.font, level.getBlockState(impactAdjusted(level, impact))
                        .getBlock().getName().getString(), px + 5, labelY, TEXT_DIM, false);
            }
        }

        // ── Color strip ───────────────────────────────────────────────────────
        g.fill(px + 3, py + PANEL_H - 4, px + PANEL_W - 3, py + PANEL_H - 1, 0xFF000000 | rgb);
    }

    // ── Scope crosshair ───────────────────────────────────────────────────────

    private static void drawScopeCrosshair(GuiGraphicsExtractor g,
                                            int gx, int gy, int gw, int gh, boolean hit) {
        int cx = gx + gw / 2;
        int cy = gy + gh / 2;
        int lineColor = hit ? 0x88FF3333 : 0x88FFFFFF;

        g.fill(gx + 2, cy, cx - 5, cy + 1, lineColor);
        g.fill(cx + 6, cy, gx + gw - 2, cy + 1, lineColor);
        g.fill(cx, gy + 2, cx + 1, cy - 5, lineColor);
        g.fill(cx, cy + 6, cx + 1, gy + gh - 2, lineColor);

        int t = 4;
        drawTick(g, gx + 1, gy + 1, t, true,  true);
        drawTick(g, gx + gw - 2, gy + 1, t, false, true);
        drawTick(g, gx + 1, gy + gh - 2, t, true,  false);
        drawTick(g, gx + gw - 2, gy + gh - 2, t, false, false);
    }

    private static void drawTick(GuiGraphicsExtractor g, int x, int y, int len,
                                  boolean rightward, boolean downward) {
        int col = 0x88FFFFFF;
        int dx = rightward ? 1 : -1;
        int dy = downward  ? 1 : -1;
        g.fill(x, y, x + dx * len, y + dy, col);
        g.fill(x, y, x + dx,       y + dy * len, col);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BlockPos impactAdjusted(ClientLevel level, BlockPos pos) {
        if (pos == null || level == null) return pos;
        int attempts = 0;
        while (attempts++ < 3 && level.getBlockState(pos).isAir()) pos = pos.below();
        return pos;
    }

    private static void drawPanelBackground(GuiGraphicsExtractor g,
                                             int x, int y, int w, int h, int alpha) {
        int bg = (alpha << 24) | 0x100010;
        g.fill(x + 1, y, x + w - 1, y + h, bg);
        g.fill(x, y + 1, x + 1, y + h - 1, bg);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
        g.fill(x + 1, y, x + w - 1, y + 1, BORDER_OUTER);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, BORDER_OUTER);
        g.fill(x, y + 1, x + 1, y + h - 1, BORDER_OUTER);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, BORDER_OUTER);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, BORDER_INNER);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, BORDER_INNER);
    }

    private static String typeName(ProjectileType type, boolean multishot) {
        String base = switch (type) {
            case BOW               -> "Bow";
            case CROSSBOW_ARROW    -> "Crossbow";
            case CROSSBOW_FIREWORK -> "Firework";
            case TRIDENT           -> "Trident";
            case ENDER_PEARL       -> "Ender Pearl";
            case SNOWBALL          -> "Snowball";
            case EGG               -> "Egg";
            case WIND_CHARGE       -> "Wind Charge";
            case FISHING_ROD       -> "Fishing Rod";
            case SPLASH_POTION     -> "Potion";
            case EXP_BOTTLE        -> "XP Bottle";
        };
        return multishot ? base + " ×3" : base;
    }
}
