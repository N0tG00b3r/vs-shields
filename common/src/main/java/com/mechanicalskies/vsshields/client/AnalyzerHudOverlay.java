package com.mechanicalskies.vsshields.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.mechanicalskies.vsshields.registry.ModItems;

/**
 * HUD panel shown while the player is using a Ship Analyzer and a target ship
 * is locked.
 *
 * Layout (top-right corner):
 * ┌─────────────────────────────────────────┐
 * │ ENEMY VESSEL DETECTED │
 * │ Shield: 45,000 / 100,000 HP │
 * │ [████████░░░░░░░░░░] 45% │
 * │ Generator: ACTIVE ⚡ 80% │
 * │ Crew: 4 | Turrets: 6 | Core: 1 │
 * └─────────────────────────────────────────┘
 */
public class AnalyzerHudOverlay {

    private static final int PANEL_W = 200;
    private static final int PANEL_H = 82;
    private static final int MARGIN = 8;
    private static final int BAR_H = 8;
    private static final int LINE_HEIGHT = 10;

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;

        // Show while holding the Ship Analyzer OR scanning via the helm keybind
        if (!isHoldingAnalyzer(mc) && !HelmAnalyzerHandler.isScanning())
            return;

        ClientAnalyzerData data = ClientAnalyzerData.getInstance();
        if (!data.isValid())
            return;

        boolean hasShip = data.targetShipId != -1;
        boolean hasMines = !data.mineWorldPositions.isEmpty();

        int sw = mc.getWindow().getGuiScaledWidth();
        int x = sw - PANEL_W - MARGIN;
        int y = MARGIN;

        // Dynamic height based on ship presence
        int actualH = hasShip ? PANEL_H : 32;

        // Background panel
        graphics.fill(x - 2, y - 2, x + PANEL_W + 2, y + actualH + 2, 0xA0000000);
        graphics.renderOutline(x - 2, y - 2, PANEL_W + 4, actualH + 4,
                hasShip ? 0xFF33FF33 : (hasMines ? 0xFFFF6666 : 0xFFFFFF33));

        int cx = x;
        int cy = y + 2;

        if (hasShip) {
            // Title
            graphics.drawString(mc.font, "◉ VESSEL DETECTED", cx, cy, 0xFF55FF55, true);
            cy += LINE_HEIGHT + 2;

            // Shield HP bar
            double hpPercent = data.maxShieldHP > 0 ? data.shieldHP / data.maxShieldHP : 0;
            int barW = PANEL_W - 2;
            int fillPx = (int) (barW * hpPercent);
            int barColor = getHPColor(hpPercent);

            graphics.fill(cx, cy, cx + barW, cy + BAR_H, 0x80113311);
            graphics.fill(cx, cy, cx + fillPx, cy + BAR_H, barColor);
            graphics.renderOutline(cx, cy, barW, BAR_H, 0xFF33FF33);
            cy += BAR_H + 2;

            String hpLabel = String.format("Shield: %.0f / %.0f  (%.0f%%)",
                    data.shieldHP, data.maxShieldHP, hpPercent * 100);
            graphics.drawString(mc.font, hpLabel, cx, cy, 0xFF55FF55, true);
            cy += LINE_HEIGHT;

            // Status and energy
            String statusStr = data.shieldActive ? "ACTIVE" : "INACTIVE";
            String energyStr = String.format("⚡ %.0f%%", data.energyPercent * 100);
            String statusLine = "Generator: " + statusStr + "  " + energyStr;
            graphics.drawString(mc.font, statusLine, cx, cy, 0xFF55FF55, true);
            cy += LINE_HEIGHT;

            // Crew / turrets / core counts
            String statsLine = String.format("Crew: %d  |  Turrets: %d  |  Core: %d",
                    data.crewEntityIds.size(),
                    data.cannonPositions.size(),
                    data.criticalPositions.size());
            graphics.drawString(mc.font, statsLine, cx, cy, 0xFF22CC22, true);
            cy += LINE_HEIGHT;

            // Mine count
            String mineLine = String.format("Mines: %d", data.mineWorldPositions.size());
            graphics.drawString(mc.font, mineLine, cx, cy, 0xFFFF4444, true);
        } else if (hasMines) {
            // Vessel Detected (via Mines)
            graphics.drawString(mc.font, "◉ VESSEL DETECTED (MINES)", cx, cy, 0xFFFF6666, true);
            cy += LINE_HEIGHT + 4;

            // Mine count
            String mineLine = String.format("Mines Detected: %d", data.mineWorldPositions.size());
            graphics.drawString(mc.font, mineLine, cx, cy, 0xFFFF4444, true);
        } else {
            // No Target Title
            graphics.drawString(mc.font, "◉ SCANNING: NO TARGET", cx, cy, 0xFFFFFF33, true);
            cy += LINE_HEIGHT + 4;

            graphics.drawString(mc.font, "No signatures found.", cx, cy, 0xFFAAAAAA, true);
        }
    }

    private static boolean isHoldingAnalyzer(Minecraft mc) {
        if (mc.player == null)
            return false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = mc.player.getItemInHand(hand);
            if (!held.isEmpty() && held.getItem() == ModItems.SHIP_ANALYZER.get()) {
                // Check if player is actively using it
                if (mc.player.isUsingItem() && mc.player.getUseItem() == held)
                    return true;
            }
        }
        return false;
    }

    private static int getHPColor(double percent) {
        if (percent > 0.5)
            return 0xFF55FF55; // bright green
        if (percent > 0.25)
            return 0xFFFFAA00; // yellow
        return 0xFFFF3333; // red
    }
}
