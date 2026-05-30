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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class HudOverlayRenderer {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("parabola", "impact_scope");

    private static final int PANEL_W = 160;
    private static final int PANEL_H = 120;
    private static final int MARGIN  = 6;

    // MC tooltip-style border colors
    private static final int BORDER_OUTER = 0xFF1E1E1E;
    private static final int BORDER_INNER = 0xFF4B0082;
    private static final int TEXT_W = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_HIT = 0xFFFF5555;

    private static final int CELL = 14;
    private static final int GRID = 5;
    private static final int GRID_PX = CELL * GRID; // 70

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

        // ── Riptide text ─────────────────────────────────────────────────────
        if (result.isRiptide()) {
            String txt = "⚡ Riptide";
            int tw = mc.font.width(txt);
            drawShadowedText(g, mc, txt, sw - tw - MARGIN, sh - 24, 0xFF55FFFF);
            return;
        }

        if (result.arcs().isEmpty() || !result.hasImpact()) return;

        int panelAlpha = (int) Math.round(cfg.arcStyle.hudPanelAlpha * 2.55);
        int px = sw - PANEL_W - MARGIN;
        int py = sh - PANEL_H - MARGIN;

        // ── Vanilla-style tooltip background ─────────────────────────────────
        drawTooltipBackground(g, px, py, PANEL_W, PANEL_H, panelAlpha);

        int contentX = px + 5;
        int lineY = py + 5;

        // ── Header: projectile type label + distance ──────────────────────────
        int rgb = cfg.rgbFor(result.type());
        int typeColor = 0xFF000000 | rgb;
        String typeLabel = typeName(result.type(), result.isMultishot());
        g.text(mc.font, typeLabel, contentX, lineY, typeColor, false);
        lineY += 10;

        // Distance
        String distStr = String.format("%.1f m", result.impactDistance());
        g.text(mc.font, "◎ " + distStr, contentX, lineY, TEXT_W, false);
        lineY += 10;

        // ── Entity hit or block name ──────────────────────────────────────────
        if (result.hitEntity() && cfg.showEntityHit) {
            g.text(mc.font, "⚔ " + result.hitEntityName(), contentX, lineY, TEXT_HIT, false);
        } else {
            ClientLevel level = mc.level;
            BlockPos impact = result.impactPos();
            if (level != null) {
                BlockState state = level.getBlockState(impact);
                String blockName = state.getBlock().getName().getString();
                g.text(mc.font, blockName, contentX, lineY, TEXT_DIM, false);
            }
        }
        lineY += 12;

        // ── Separator line ────────────────────────────────────────────────────
        g.fill(contentX, lineY, px + PANEL_W - 5, lineY + 1, 0x44FFFFFF);
        lineY += 4;

        // ── 2D block cross-section ────────────────────────────────────────────
        ClientLevel level = mc.level;
        BlockPos impact = result.impactPos();
        int gridX = px + (PANEL_W - GRID_PX) / 2;
        int gridY = lineY;

        // Dark background for the grid
        g.fill(gridX - 1, gridY - 1, gridX + GRID_PX + 1, gridY + GRID_PX + 1, 0x88000000);

        if (level != null) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos bp = impact.offset(dx, 0, dz);
                    BlockState bs = level.getBlockState(bp);
                    int cx = gridX + (dx + 2) * CELL;
                    int cz = gridY + (dz + 2) * CELL;

                    if (!bs.isAir()) {
                        MapColor mc2 = bs.getMapColor(level, bp);
                        g.fill(cx, cz, cx + CELL - 1, cz + CELL - 1, 0xFF000000 | mc2.col);
                        // Cell border
                        g.fill(cx, cz, cx + CELL - 1, cz + 1, 0x40FFFFFF);
                        g.fill(cx, cz, cx + 1, cz + CELL - 1, 0x40FFFFFF);
                    }
                    // Air: no fill = dark background shows through
                }
            }
        }

        // Crosshair
        int cxc = gridX + GRID_PX / 2;
        int cyc = gridY + GRID_PX / 2;
        g.fill(cxc - 3, cyc,     cxc + 4, cyc + 1,     0xFFFFFFFF);
        g.fill(cxc,     cyc - 3, cxc + 1, cyc + 4,     0xFFFFFFFF);
        g.fill(cxc - 1, cyc - 1, cxc + 2, cyc + 2,     result.hitEntity() ? TEXT_HIT : 0xFFFF0000);

        // ── Color strip at bottom ─────────────────────────────────────────────
        int stripY = py + PANEL_H - 5;
        g.fill(px + 3, stripY, px + PANEL_W - 3, py + PANEL_H - 2, 0xFF000000 | rgb);
    }

    /**
     * Draws a tooltip-style background matching vanilla MC's tooltip look.
     * Background: near-black. Border: thin dark outer + thin purple inner.
     */
    private static void drawTooltipBackground(GuiGraphicsExtractor g,
                                               int x, int y, int w, int h, int alpha) {
        int bg = (alpha << 24) | 0x100010;

        // Main body
        g.fill(x + 1, y,     x + w - 1, y + h,     bg);
        g.fill(x,     y + 1, x + 1,     y + h - 1, bg);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);

        // Outer border (dark)
        g.fill(x + 1, y,         x + w - 1, y + 1,         BORDER_OUTER);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h,         BORDER_OUTER);
        g.fill(x,     y + 1,     x + 1,     y + h - 1,     BORDER_OUTER);
        g.fill(x + w - 1, y + 1, x + w,     y + h - 1,     BORDER_OUTER);

        // Inner accent line (purple, 1px inside the border on vertical sides)
        g.fill(x + 1, y + 1,     x + 2,     y + h - 1, BORDER_INNER);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, BORDER_INNER);
    }

    private static void drawShadowedText(GuiGraphicsExtractor g, Minecraft mc,
                                          String text, int x, int y, int color) {
        g.text(mc.font, text, x + 1, y + 1, 0xFF000000, false);
        g.text(mc.font, text, x, y, color, false);
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
        };
        return multishot ? base + " ×3" : base;
    }
}
