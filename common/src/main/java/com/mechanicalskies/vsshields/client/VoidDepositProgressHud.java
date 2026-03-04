package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.anomaly.ClientAnomalyData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Client-side HUD overlay showing extraction progress when mining a void deposit.
 * Displays a centered progress bar below the crosshair.
 */
public class VoidDepositProgressHud {

    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_Y_OFFSET = 20; // pixels below crosshair

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!ClientAnomalyData.isExtractionActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int barX = (screenWidth - BAR_WIDTH) / 2;
        int barY = screenHeight / 2 + BAR_Y_OFFSET;

        float progress = ClientAnomalyData.getExtractionProgress();

        // Background (dark gray)
        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xAA000000);

        // Progress fill (purple-cyan gradient feel)
        int fillWidth = (int) (BAR_WIDTH * progress);
        if (fillWidth > 0) {
            graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, 0xDD8844FF);
        }

        // Remaining (dark)
        if (fillWidth < BAR_WIDTH) {
            graphics.fill(barX + fillWidth, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x44222222);
        }

        // Label
        String label = String.format("Extracting... %d%%", (int) (progress * 100));
        int textWidth = mc.font.width(label);
        graphics.drawString(mc.font, label, (screenWidth - textWidth) / 2, barY - 12, 0xFFCC88FF, true);
    }
}
