package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.anomaly.ClientAnomalyData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Top-center HUD displaying the anomaly timer when active.
 * Visible to all players within 100 blocks of the anomaly.
 */
public class AnomalyTimerHud {

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!ClientAnomalyData.isTimerActive()) return;

        int seconds = ClientAnomalyData.getTimerSeconds();
        if (seconds <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int mins = seconds / 60;
        int secs = seconds % 60;
        String text = String.format("ANOMALY: %d:%02d", mins, secs);

        // Color based on time remaining
        int color;
        if (seconds > 300) {        // >5 min: white
            color = 0xFFFFFFFF;
        } else if (seconds > 60) {  // 1-5 min: yellow
            color = 0xFFFFFF44;
        } else if (seconds > 30) {  // 30s-1min: red
            color = 0xFFFF4444;
        } else {
            // <30s: blinking red
            long gameTick = mc.level != null ? mc.level.getGameTime() : 0;
            color = (gameTick % 10 < 5) ? 0xFFFF4444 : 0xFFAA0000;
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
