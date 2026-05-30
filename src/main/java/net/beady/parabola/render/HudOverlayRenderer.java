package net.beady.parabola.render;

import me.shedaniel.autoconfig.AutoConfig;
import net.beady.parabola.config.ModConfig;
import net.beady.parabola.trajectory.TrajectoryResult;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.hud.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.List;

public final class HudOverlayRenderer {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("parabola", "impact_scope");

    // Panel dimensions
    private static final int PANEL_W = 200;
    private static final int PANEL_H = 150;
    private static final int MARGIN  = 8;  // distance from screen edge

    private static final int BG_COLOR       = 0xC8000000; // ~78% opaque black
    private static final int BORDER_COLOR   = 0x80FFFFFF;
    private static final int TEXT_COLOR     = 0xFFFFFFFF;
    private static final int DIM_TEXT_COLOR = 0xFFAAAAAA;

    // 2D cross-section: 5×5 cells of 16px each
    private static final int CELL_SIZE  = 16;
    private static final int GRID_CELLS = 5;
    private static final int GRID_PX    = CELL_SIZE * GRID_CELLS; // 80px

    private HudOverlayRenderer() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                ID,
                HudOverlayRenderer::renderHud
        );
    }

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker ticker) {
        ModConfig cfg = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        if (!cfg.enabled || !cfg.showHudPanel) return;

        TrajectoryResult result = TrajectoryCache.getCached();
        if (result == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; // hide when a GUI is open

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // ── Riptide special case ─────────────────────────────────────────────
        if (result.isRiptide()) {
            String text = "⚡ Riptide";
            int tw = mc.font.width(text);
            graphics.drawString(mc.font, text, sw - tw - MARGIN, sh - 20, 0xFF55FFFF);
            return;
        }

        if (result.arcs().isEmpty() || !result.hasImpact()) return;

        // ── Panel position: bottom-right ──────────────────────────────────────
        int px = sw - PANEL_W - MARGIN;
        int py = sh - PANEL_H - MARGIN;

        // Background
        graphics.fill(px, py, px + PANEL_W, py + PANEL_H, BG_COLOR);
        // Border (1px lines)
        graphics.fill(px,             py,              px + PANEL_W, py + 1,         BORDER_COLOR);
        graphics.fill(px,             py + PANEL_H - 1, px + PANEL_W, py + PANEL_H, BORDER_COLOR);
        graphics.fill(px,             py,              px + 1,         py + PANEL_H, BORDER_COLOR);
        graphics.fill(px + PANEL_W - 1, py,            px + PANEL_W, py + PANEL_H, BORDER_COLOR);

        // ── Distance label ────────────────────────────────────────────────────
        String distLabel = String.format("◎ %.1fm", result.impactDistance());
        graphics.drawString(mc.font, distLabel, px + 6, py + 6, TEXT_COLOR);

        // ── Multishot label ───────────────────────────────────────────────────
        if (result.isMultishot()) {
            graphics.drawString(mc.font, "×3", px + PANEL_W - mc.font.width("×3") - 6, py + 6, 0xFFFFAA00);
        }

        // ── Block name ────────────────────────────────────────────────────────
        ClientLevel level = mc.level;
        BlockPos impact = result.impactPos();
        if (level != null) {
            BlockState state = level.getBlockState(impact);
            String blockName = state.getBlock().getName().getString();
            graphics.drawString(mc.font, blockName, px + 6, py + 18, DIM_TEXT_COLOR);
        }

        // ── 2D block cross-section ────────────────────────────────────────────
        int gridX = px + (PANEL_W - GRID_PX) / 2;
        int gridY = py + 36;

        if (level != null) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos bp = impact.offset(dx, 0, dz);
                    BlockState bs = level.getBlockState(bp);
                    MapColor mc2 = bs.getMapColor(level, bp);
                    int cellColor = (bs.isAir())
                            ? 0x40404040
                            : (0xFF000000 | mc2.col);

                    int cx = gridX + (dx + 2) * CELL_SIZE;
                    int cz = gridY + (dz + 2) * CELL_SIZE;
                    graphics.fill(cx, cz, cx + CELL_SIZE - 1, cz + CELL_SIZE - 1, cellColor);
                }
            }
        }

        // ── Crosshair at center of grid ───────────────────────────────────────
        int crossX = gridX + GRID_PX / 2;
        int crossY = gridY + GRID_PX / 2;
        graphics.fill(crossX - 4, crossY,     crossX + 5, crossY + 1,     0xFFFFFFFF); // horizontal
        graphics.fill(crossX,     crossY - 4, crossX + 1, crossY + 5,     0xFFFFFFFF); // vertical
        graphics.fill(crossX - 1, crossY - 1, crossX + 2, crossY + 2,     0xFFFF0000); // center dot

        // ── Color bar strip at bottom matching projectile type ────────────────
        int typeColor = 0xFF000000 | result.type().argb();
        graphics.fill(px + 2, py + PANEL_H - 6, px + PANEL_W - 2, py + PANEL_H - 2, typeColor);
    }
}
