package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.menu.ShieldBatteryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ShieldBatteryScreen extends AbstractContainerScreen<ShieldBatteryMenu> {
    private static final int BG_COLOR = 0xCC1A1A2E;
    private static final int BORDER_COLOR = 0xFF3A3A5E;
    private static final int BAR_BG_COLOR = 0xFF2A2A3E;
    private static final int LABEL_COLOR = 0xFF8888AA;
    private static final int CELL_BAR_FULL = 0xFF33CC33;
    private static final int CELL_BAR_PARTIAL = 0xFFCC3333;
    private static final int FE_BAR_COLOR = 0xFF9933FF;

    public ShieldBatteryScreen(ShieldBatteryMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 120;
        this.inventoryLabelY = this.imageHeight + 1;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_COLOR);

        int x = leftPos + 12;
        int y = topPos + 8;
        int barX = x;
        int barWidth = imageWidth - 24;
        int barHeight = 12;

        graphics.drawCenteredString(font, title, leftPos + imageWidth / 2, y, 0xFFFFFFFF);
        y += 16;

        int cells = menu.getCellCount();
        boolean complete = menu.isFormed();
        double cellPct = cells / 26.0;

        graphics.fill(barX, y, barX + barWidth, y + barHeight, BAR_BG_COLOR);
        int cellFill = (int) (barWidth * Math.min(cellPct, 1.0));
        if (cellFill > 0) {
            graphics.fill(barX, y, barX + cellFill, y + barHeight, complete ? CELL_BAR_FULL : CELL_BAR_PARTIAL);
        }
        graphics.renderOutline(barX, y, barWidth, barHeight, BORDER_COLOR);
        String cellText = cells + "/26 Cells";
        graphics.drawCenteredString(font, cellText, leftPos + imageWidth / 2, y + 2, 0xFFFFFFFF);
        y += barHeight + 8;

        double energyPct = menu.getEnergyPercent();
        graphics.fill(barX, y, barX + barWidth, y + barHeight, BAR_BG_COLOR);
        int energyFill = (int) (barWidth * energyPct);
        if (energyFill > 0) {
            graphics.fill(barX, y, barX + energyFill, y + barHeight, FE_BAR_COLOR);
        }
        graphics.renderOutline(barX, y, barWidth, barHeight, BORDER_COLOR);
        String energyText = String.format("%,d / %,d FE", menu.getEnergyStored(), menu.getMaxEnergy());
        graphics.drawCenteredString(font, energyText, leftPos + imageWidth / 2, y + 2, 0xFFFFFFFF);
        y += barHeight + 10;

        String statusLabel = "Status: " + menu.getStatusText();
        graphics.drawString(font, statusLabel, x, y, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }
}
