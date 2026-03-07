package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.anomaly.ClientAnomalyData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Top-center HUD displaying the anomaly timer when active.
 * Shows global TTL in ACTIVE phase, extraction countdown in EXTRACTION phase.
 * Visible to all players within 100 blocks of the anomaly.
 */
public class AnomalyTimerHud {

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!ClientAnomalyData.isTimerActive()) return;

        int seconds = ClientAnomalyData.getTimerSeconds();
        if (seconds <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int phase = ClientAnomalyData.getAnomalyPhase();
        int extractSec = ClientAnomalyData.getExtractionSeconds();

        // During extraction phase, show extraction timer instead of global TTL
        boolean showExtraction = (phase == ClientAnomalyData.PHASE_EXTRACTION && extractSec >= 0);
        int displaySeconds = showExtraction ? extractSec : seconds;

        int mins = displaySeconds / 60;
        int secs = displaySeconds % 60;

        String label = showExtraction ? "EXTRACTION" : "ANOMALY";
        String text = String.format("%s: %d:%02d", label, mins, secs);

        // Color based on phase and time remaining
        int color;
        if (showExtraction) {
            // Extraction: orange/yellow, blink when low
            if (displaySeconds > 60) {
                color = 0xFFFF8800;
            } else if (displaySeconds > 30) {
                color = 0xFFFF4400;
            } else {
                long gameTick = mc.level != null ? mc.level.getGameTime() : 0;
                color = (gameTick % 10 < 5) ? 0xFFFF4400 : 0xFFAA2200;
            }
        } else {
            if (seconds > 300) {
                color = 0xFFFFFFFF;
            } else if (seconds > 60) {
                color = 0xFFFFFF44;
            } else if (seconds > 30) {
                color = 0xFFFF4444;
            } else {
                long gameTick = mc.level != null ? mc.level.getGameTime() : 0;
                color = (gameTick % 10 < 5) ? 0xFFFF4444 : 0xFFAA0000;
            }
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int textWidth = mc.font.width(text);
        int x = (screenWidth - textWidth) / 2;
        int y = 5;

        // Background
        graphics.fill(x - 4, y - 2, x + textWidth + 4, y + 11, 0x88000000);
        graphics.drawString(mc.font, text, x, y, color, true);
    }
}
