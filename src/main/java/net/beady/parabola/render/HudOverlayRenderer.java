package net.beady.parabola.render;

import me.shedaniel.autoconfig.AutoConfig;
import net.beady.parabola.config.ModConfig;
import net.beady.parabola.trajectory.TrajectoryResult;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class HudOverlayRenderer {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("parabola", "impact_scope");

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 150;
    private static final int MARGIN  = 8;

    private static final int BG_COLOR     = 0xC8000000;
    private static final int BORDER_COLOR = 0x80FFFFFF;
    private static final int TEXT_COLOR   = 0xFFFFFFFF;
    private static final int DIM_COLOR    = 0xFFAAAAAA;

    private static final int CELL_SIZE  = 16;
    private static final int GRID_CELLS = 5;
    private static final int GRID_PX    = CELL_SIZE * GRID_CELLS; // 80

    private HudOverlayRenderer() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, HudOverlayRenderer::renderHud);
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker ticker) {
        ModConfig cfg = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        if (!cfg.enabled || !cfg.showHudPanel) return;

        TrajectoryResult result = TrajectoryCache.getCached();
        if (result == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // ── Riptide special case ─────────────────────────────────────────────
        if (result.isRiptide()) {
            String text = "⚡ Riptide";
            int tw = mc.font.width(text);
            graphics.text(mc.font, text, sw - tw - MARGIN, sh - 20, 0xFF55FFFF, false);
            return;
        }

        if (result.arcs().isEmpty() || !result.hasImpact()) return;

        int px = sw - PANEL_W - MARGIN;
        int py = sh - PANEL_H - MARGIN;

        // Background + 1px border
        graphics.fill(px, py, px + PANEL_W, py + PANEL_H, BG_COLOR);
        graphics.fill(px,              py,               px + PANEL_W, py + 1,           BORDER_COLOR);
        graphics.fill(px,              py + PANEL_H - 1, px + PANEL_W, py + PANEL_H,     BORDER_COLOR);
        graphics.fill(px,              py,               px + 1,        py + PANEL_H,     BORDER_COLOR);
        graphics.fill(px + PANEL_W - 1, py,             px + PANEL_W,  py + PANEL_H,     BORDER_COLOR);

        // Distance label
        String distLabel = String.format("◎ %.1fm", result.impactDistance());
        graphics.text(mc.font, distLabel, px + 6, py + 6, TEXT_COLOR, false);

        // Multishot label
        if (result.isMultishot()) {
            String tag = "×3";
            graphics.text(mc.font, tag, px + PANEL_W - mc.font.width(tag) - 6, py + 6, 0xFFFFAA00, false);
        }

        // Block name
        ClientLevel level = mc.level;
        BlockPos impact = result.impactPos();
        if (level != null) {
            BlockState state = level.getBlockState(impact);
            graphics.text(mc.font, state.getBlock().getName().getString(), px + 6, py + 18, DIM_COLOR, false);
        }

        // 2D block cross-section (5×5)
        int gridX = px + (PANEL_W - GRID_PX) / 2;
        int gridY = py + 36;

        if (level != null) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos bp = impact.offset(dx, 0, dz);
                    BlockState bs = level.getBlockState(bp);
                    int cellColor;
                    if (bs.isAir()) {
                        cellColor = 0x30404040;
                    } else {
                        MapColor mc2 = bs.getMapColor(level, bp);
                        cellColor = 0xFF000000 | mc2.col;
                    }
                    int cx = gridX + (dx + 2) * CELL_SIZE;
                    int cz = gridY + (dz + 2) * CELL_SIZE;
                    graphics.fill(cx, cz, cx + CELL_SIZE - 1, cz + CELL_SIZE - 1, cellColor);
                }
            }
        }

        // Crosshair at center
        int cx = gridX + GRID_PX / 2;
        int cy = gridY + GRID_PX / 2;
        graphics.fill(cx - 4, cy,     cx + 5, cy + 1,     0xFFFFFFFF);
        graphics.fill(cx,     cy - 4, cx + 1, cy + 5,     0xFFFFFFFF);
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2,     0xFFFF0000);

        // Projectile-type color bar at bottom
        graphics.fill(px + 2, py + PANEL_H - 6, px + PANEL_W - 2, py + PANEL_H - 2, result.type().argb);
    }
}
