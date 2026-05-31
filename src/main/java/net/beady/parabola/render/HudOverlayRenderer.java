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
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class HudOverlayRenderer {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("parabola", "impact_scope");

    private static final int PANEL_W = 120;
    private static final int PANEL_H = 122;
    private static final int MARGIN  = 8;

    // Scope grid: 5 cols × 5 rows
    private static final int CELL    = 18;
    private static final int GRID_W  = 5;
    private static final int GRID_H  = 5;
    private static final int GRID_PX_W = CELL * GRID_W; // 90
    private static final int GRID_PX_H = CELL * GRID_H; // 90

    private static final int TEXT_W       = 0xFFFFFFFF;
    private static final int TEXT_DIM     = 0xFFAAAAAA;
    private static final int TEXT_HIT     = 0xFFFF5555;
    private static final int BORDER_OUTER = 0xFF1A1A1A;
    private static final int BORDER_INNER = 0xFF4B0082;

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

        // ── Scope view ────────────────────────────────────────────────────────
        int gridX = px + (PANEL_W - GRID_PX_W) / 2;
        int gridY = py + 19;

        boolean usedScope = ScopeCapture.capture(mc, GRID_PX_W, GRID_PX_H);
        if (usedScope) {
            g.blit(ScopeCapture.ID, gridX, gridY, 0, 0, GRID_PX_W, GRID_PX_H, GRID_PX_W, GRID_PX_H);
            drawScopeCrosshair(g, gridX, gridY, result.hitEntity());
        } else {
            drawScopeView(g, mc, result, gridX, gridY);
        }

        // ── Bottom label ─────────────────────────────────────────────────────
        int labelY = gridY + GRID_PX_H + 3;
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

    // ────────────────────────────────────────────────────────────────────────
    // Scope view: front-facing cross-section perpendicular to shot direction
    // ────────────────────────────────────────────────────────────────────────

    private static void drawScopeView(GuiGraphicsExtractor g, Minecraft mc,
                                       TrajectoryResult result, int gx, int gy) {
        ClientLevel level = mc.level;
        if (level == null) return;
        BlockPos center = impactAdjusted(level, result.impactPos());
        if (center == null) return;

        // Shot direction from last arc segment
        List<Vec3> arc = result.centerArc();
        Vec3 shotDir = getShotDir(arc);

        // Choose cross-section axis based on dominant shot direction
        double absX = Math.abs(shotDir.x);
        double absY = Math.abs(shotDir.y);
        double absZ = Math.abs(shotDir.z);

        // Dark background
        g.fill(gx - 1, gy - 1, gx + GRID_PX_W + 1, gy + GRID_PX_H + 1, 0xCC000000);

        for (int col = -2; col <= 2; col++) {       // horizontal in cross-section
            for (int row = -2; row <= 2; row++) {   // vertical
                BlockPos bp = crossSectionBlock(center, col, row, absX, absY, absZ, shotDir);
                BlockState bs = getVisibleBlock(level, bp, shotDir);

                int cx = gx + (col + 2) * CELL;
                int cy = gy + (2 - row) * CELL; // row +2 = top of grid

                if (bs != null) {
                    MapColor mc2 = bs.getMapColor(level, bp);
                    int base = 0xFF000000 | mc2.col;
                    // Apply subtle height tinting (darker = lower)
                    float heightFade = (row + 2) / 4.0f; // 0.0 at bottom, 1.0 at top
                    int dimming = (int)(40 * (1.0f - heightFade));
                    int shaded = darken(base, dimming);

                    g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, shaded);
                    // Light edge (top-left)
                    g.fill(cx, cy, cx + CELL - 1, cy + 1, 0x35FFFFFF);
                    g.fill(cx, cy, cx + 1, cy + CELL - 1, 0x35FFFFFF);
                    // Dark edge (bottom-right)
                    g.fill(cx, cy + CELL - 2, cx + CELL - 1, cy + CELL - 1, 0x35000000);
                    g.fill(cx + CELL - 2, cy, cx + CELL - 1, cy + CELL - 1, 0x35000000);
                }
            }
        }

        drawScopeCrosshair(g, gx, gy, result.hitEntity());
    }

    /** Returns the dominant-axis cross-section block for a given grid cell. */
    private static BlockPos crossSectionBlock(BlockPos center, int col, int row,
                                               double absX, double absY, double absZ, Vec3 shot) {
        if (absY > 0.65) {
            // Shot mostly vertical → top-down (X-Z plane)
            return center.offset(col, 0, row);
        } else if (absX >= absZ) {
            // Shot mostly along X → show Z-Y cross-section
            return center.offset(0, row, col);
        } else {
            // Shot mostly along Z → show X-Y cross-section
            return center.offset(col, row, 0);
        }
    }

    /** Gets the first non-air/non-fluid block at bp or one step further along shot direction. */
    private static BlockState getVisibleBlock(ClientLevel level, BlockPos bp, Vec3 shot) {
        BlockState bs = level.getBlockState(bp);
        if (isRenderable(bs)) return bs;

        // Try one step further into the surface (behind the face)
        int dx = (int) Math.round(shot.x);
        int dy = (int) Math.round(shot.y);
        int dz = (int) Math.round(shot.z);
        if (dx != 0 || dy != 0 || dz != 0) {
            BlockPos bp2 = bp.offset(dx, dy, dz);
            BlockState bs2 = level.getBlockState(bp2);
            if (isRenderable(bs2)) return bs2;
        }
        return null;
    }

    private static boolean isRenderable(BlockState bs) {
        return !bs.isAir() && !bs.is(Blocks.WATER) && !bs.is(Blocks.LAVA) && !bs.is(Blocks.BUBBLE_COLUMN);
    }

    private static void drawScopeCrosshair(GuiGraphicsExtractor g, int gx, int gy, boolean hit) {
        int cx = gx + GRID_PX_W / 2;
        int cy = gy + GRID_PX_H / 2;
        int lineColor = hit ? 0xCCFF3333 : 0xCCFFFFFF;

        // Horizontal crosshair lines (gap in center)
        g.fill(gx + 2,    cy,     cx - 5,       cy + 1, lineColor);
        g.fill(cx + 6,    cy,     gx + GRID_PX_W - 2, cy + 1, lineColor);
        // Vertical crosshair lines
        g.fill(cx,        gy + 2, cx + 1, cy - 5,       lineColor);
        g.fill(cx,        cy + 6, cx + 1, gy + GRID_PX_H - 2, lineColor);

        // Center diamond
        int dotCol = hit ? 0xFFFF3333 : 0xFFFF0000;
        g.fill(cx - 1, cy,     cx + 2, cy + 1, dotCol);
        g.fill(cx,     cy - 1, cx + 1, cy + 2, dotCol);

        // Corner tick marks
        int t = 5;
        drawTick(g, gx + 1,             gy + 1,              t, true,  true);
        drawTick(g, gx + GRID_PX_W - 2, gy + 1,              t, false, true);
        drawTick(g, gx + 1,             gy + GRID_PX_H - 2,  t, true,  false);
        drawTick(g, gx + GRID_PX_W - 2, gy + GRID_PX_H - 2,  t, false, false);
    }

    private static void drawTick(GuiGraphicsExtractor g, int x, int y, int len,
                                  boolean rightward, boolean downward) {
        int col = 0x88FFFFFF;
        int dx = rightward ? 1 : -1;
        int dy = downward  ? 1 : -1;
        g.fill(x,          y,          x + dx * len, y + dy,          col);
        g.fill(x,          y,          x + dx,        y + dy * len,   col);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Vec3 getShotDir(List<Vec3> arc) {
        if (arc.size() >= 2) {
            Vec3 a = arc.get(arc.size() - 1);
            Vec3 b = arc.get(arc.size() - 2);
            Vec3 d = a.subtract(b);
            if (d.length() > 0) return d.normalize();
        }
        return new Vec3(0, -1, 0);
    }

    /** When a block face top is hit, BlockPos.containing lands in the air above. Walk down. */
    private static BlockPos impactAdjusted(ClientLevel level, BlockPos pos) {
        if (pos == null || level == null) return pos;
        int attempts = 0;
        while (attempts++ < 3 && level.getBlockState(pos).isAir()) {
            pos = pos.below();
        }
        return pos;
    }

    private static int darken(int argb, int amount) {
        int r = Math.max(0, ((argb >> 16) & 0xFF) - amount);
        int gr = Math.max(0, ((argb >>  8) & 0xFF) - amount);
        int b  = Math.max(0,  (argb        & 0xFF) - amount);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    private static void drawPanelBackground(GuiGraphicsExtractor g,
                                             int x, int y, int w, int h, int alpha) {
        int bg = (alpha << 24) | 0x100010;
        g.fill(x + 1, y,     x + w - 1, y + h,     bg);
        g.fill(x,     y + 1, x + 1,     y + h - 1, bg);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
        g.fill(x + 1, y,         x + w - 1, y + 1,         BORDER_OUTER);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h,         BORDER_OUTER);
        g.fill(x,     y + 1,     x + 1,     y + h - 1,     BORDER_OUTER);
        g.fill(x + w - 1, y + 1, x + w,     y + h - 1,     BORDER_OUTER);
        g.fill(x + 1, y + 1,     x + 2,     y + h - 1, BORDER_INNER);
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
