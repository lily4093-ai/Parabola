package net.beady.parabola.render;

import me.shedaniel.autoconfig.AutoConfig;
import net.beady.parabola.config.ModConfig;
import net.beady.parabola.trajectory.ProjectileType;
import net.beady.parabola.trajectory.TrajectoryResult;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class HudOverlayRenderer {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("parabola", "impact_scope");

    // Panel dimensions
    private static final int PANEL_W = 120;
    private static final int PANEL_H = 120;
    private static final int MARGIN  = 8;

    // Scope grid: 5×5 blocks at 18px each
    private static final int CELL    = 18;
    private static final int GRID    = 5;
    private static final int GRID_PX = CELL * GRID; // 90

    // Colors
    private static final int TEXT_W   = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_HIT = 0xFFFF5555;
    private static final int COL_BORDER_OUTER = 0xFF1A1A1A;
    private static final int COL_BORDER_INNER = 0xFF4B0082;

    private HudOverlayRenderer() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, HudOverlayRenderer::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tick) {
        ModConfig cfg = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        if (!cfg.enabled || !cfg.showHudPanel) return;

        TrajectoryResult result = TrajectoryCache.getCached();
        if (result == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // ── Riptide special label ─────────────────────────────────────────────
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

        // ── Panel background (tooltip-style) ─────────────────────────────────
        drawPanelBackground(g, px, py, PANEL_W, PANEL_H, panelAlpha);

        int contentX = px + 5;
        int rgb = cfg.rgbFor(result.type());
        int typeColor = 0xFF000000 | rgb;

        // ── Header: type label ────────────────────────────────────────────────
        String typeLabel = typeName(result.type(), result.isMultishot());
        g.text(mc.font, typeLabel, contentX, py + 5, typeColor, false);

        // ── Distance ─────────────────────────────────────────────────────────
        String distStr = String.format("%.1fm", result.impactDistance());
        int distW = mc.font.width(distStr);
        g.text(mc.font, distStr, px + PANEL_W - distW - 5, py + 5, TEXT_DIM, false);

        // ── Separator ─────────────────────────────────────────────────────────
        g.fill(contentX, py + 15, px + PANEL_W - 5, py + 16, 0x55FFFFFF);

        // ── Scope grid (top-down zoomed view) ────────────────────────────────
        int gridX = px + (PANEL_W - GRID_PX) / 2;
        int gridY = py + 19;

        drawScopeGrid(g, mc, result, gridX, gridY);

        // ── Entity / block label at bottom ────────────────────────────────────
        int labelY = gridY + GRID_PX + 3;
        if (result.hitEntity() && cfg.showEntityHit) {
            // Red background flash for entity hit
            g.fill(px + 3, labelY - 1, px + PANEL_W - 3, labelY + 10, 0xBB660000);
            String hitLabel = "⚔ " + result.hitEntityName();
            g.text(mc.font, hitLabel, contentX, labelY, TEXT_HIT, false);
        } else {
            ClientLevel level = mc.level;
            BlockPos impact = result.impactPos();
            if (level != null && impact != null) {
                BlockState state = level.getBlockState(impact);
                String blockName = state.getBlock().getName().getString();
                g.text(mc.font, blockName, contentX, labelY, TEXT_DIM, false);
            }
        }

        // ── Color accent strip ────────────────────────────────────────────────
        int stripY = py + PANEL_H - 4;
        g.fill(px + 3, stripY, px + PANEL_W - 3, py + PANEL_H - 1, 0xFF000000 | rgb);
    }

    private static void drawScopeGrid(GuiGraphicsExtractor g, Minecraft mc,
                                       TrajectoryResult result, int gx, int gy) {
        ClientLevel level = mc.level;
        BlockPos impact = result.impactPos();

        // Dark background
        g.fill(gx - 1, gy - 1, gx + GRID_PX + 1, gy + GRID_PX + 1, 0xCC000000);

        // Block cells
        if (level != null && impact != null) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos bp = impact.offset(dx, 0, dz);
                    BlockState bs = level.getBlockState(bp);
                    int cx = gx + (dx + 2) * CELL;
                    int cz = gy + (dz + 2) * CELL;
                    if (!bs.isAir() && !bs.is(Blocks.WATER) && !bs.is(Blocks.LAVA) && !bs.is(Blocks.BUBBLE_COLUMN)) {
                        MapColor mc2 = bs.getMapColor(level, bp);
                        g.fill(cx, cz, cx + CELL - 1, cz + CELL - 1, 0xFF000000 | mc2.col);
                        // Subtle cell highlight top+left
                        g.fill(cx, cz, cx + CELL - 1, cz + 1, 0x30FFFFFF);
                        g.fill(cx, cz, cx + 1, cz + CELL - 1, 0x30FFFFFF);
                    }
                }
            }
        }

        // ── Scope crosshair ───────────────────────────────────────────────────
        int cx = gx + GRID_PX / 2;
        int cy = gy + GRID_PX / 2;

        // Long crosshair lines (scope-style)
        int lineAlpha = result.hitEntity() ? 0xCCFF3333 : 0xCCFFFFFF;
        // Horizontal
        g.fill(gx,      cy,     cx - 4, cy + 1, lineAlpha);
        g.fill(cx + 5,  cy,     gx + GRID_PX, cy + 1, lineAlpha);
        // Vertical
        g.fill(cx,      gy,     cx + 1, cy - 4, lineAlpha);
        g.fill(cx,      cy + 5, cx + 1, gy + GRID_PX, lineAlpha);

        // Center dot / hit indicator
        int dotColor = result.hitEntity() ? 0xFFFF3333 : 0xFFFF0000;
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, dotColor);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, result.hitEntity() ? 0xFFFFAAAA : 0xFFFFFFFF);

        // Corner tick marks (scope aesthetic)
        int tl = 5;
        g.fill(gx + 1,          gy + 1,          gx + 1 + tl, gy + 2,          0x88FFFFFF);
        g.fill(gx + 1,          gy + 1,          gx + 2,      gy + 1 + tl,     0x88FFFFFF);
        g.fill(gx + GRID_PX - tl - 1, gy + 1,   gx + GRID_PX - 1, gy + 2,     0x88FFFFFF);
        g.fill(gx + GRID_PX - 2, gy + 1,         gx + GRID_PX - 1, gy + 1 + tl, 0x88FFFFFF);
        g.fill(gx + 1,          gy + GRID_PX - 2, gx + 1 + tl, gy + GRID_PX - 1, 0x88FFFFFF);
        g.fill(gx + 1,          gy + GRID_PX - tl - 1, gx + 2, gy + GRID_PX - 1, 0x88FFFFFF);
        g.fill(gx + GRID_PX - tl - 1, gy + GRID_PX - 2, gx + GRID_PX - 1, gy + GRID_PX - 1, 0x88FFFFFF);
        g.fill(gx + GRID_PX - 2, gy + GRID_PX - tl - 1, gx + GRID_PX - 1, gy + GRID_PX - 1, 0x88FFFFFF);
    }

    private static void drawPanelBackground(GuiGraphicsExtractor g,
                                             int x, int y, int w, int h, int alpha) {
        int bg = (alpha << 24) | 0x100010;
        // Main body
        g.fill(x + 1, y,     x + w - 1, y + h,     bg);
        g.fill(x,     y + 1, x + 1,     y + h - 1, bg);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
        // Outer border
        g.fill(x + 1, y,         x + w - 1, y + 1,         COL_BORDER_OUTER);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h,         COL_BORDER_OUTER);
        g.fill(x,     y + 1,     x + 1,     y + h - 1,     COL_BORDER_OUTER);
        g.fill(x + w - 1, y + 1, x + w,     y + h - 1,     COL_BORDER_OUTER);
        // Inner accent (purple)
        g.fill(x + 1, y + 1,     x + 2,     y + h - 1, COL_BORDER_INNER);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, COL_BORDER_INNER);
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
