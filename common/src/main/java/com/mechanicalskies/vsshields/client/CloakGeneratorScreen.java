package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.menu.CloakGeneratorMenu;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CloakGeneratorScreen extends AbstractContainerScreen<CloakGeneratorMenu> {
    private static final int BG_COLOR      = 0xCC0D1A1A;
    private static final int BORDER_COLOR  = 0xFF1A4A4A;
    private static final int BAR_BG_COLOR  = 0xFF0A2A2A;
    private static final int LABEL_COLOR   = 0xFF66BBAA;
    private static final int ENERGY_COLOR  = 0xFF00DDAA;

    private Button toggleButton;

    public CloakGeneratorScreen(CloakGeneratorMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = 176;
        this.imageHeight = 130;
        this.inventoryLabelY = this.imageHeight + 1;
    }

    @Override
    protected void init() {
        super.init();
        int btnX = this.leftPos + (this.imageWidth - 80) / 2;
        int btnY = this.topPos + 100;
        toggleButton = Button.builder(getToggleText(), this::onTogglePress)
                .bounds(btnX, btnY, 80, 20)
                .build();
        addRenderableWidget(toggleButton);
    }

    private Component getToggleText() {
        if (menu.isDuplicate()) {
            return Component.literal("\u00a7cDuplicate");
        }
        return Component.translatable(menu.isCloakActive()
                ? "gui.vs_shields.cloak_generator.deactivate"
                : "gui.vs_shields.cloak_generator.activate");
    }

    private void onTogglePress(Button button) {
        if (menu.isDuplicate()) return;
        boolean newState = !menu.isCloakActive();
        ModNetwork.sendCloakToggleToServer(menu.getShipId(), newState);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (toggleButton != null) {
            toggleButton.setMessage(getToggleText());
            toggleButton.active = !menu.isDuplicate();
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_COLOR);

        int x = leftPos + 12;
        int y = topPos + 8;
        int barWidth = imageWidth - 24;
        int barHeight = 12;

        graphics.drawCenteredString(font, title, leftPos + imageWidth / 2, y, 0xFFCCFFFF);
        y += 18;

        // Status line
        String statusStr;
        if (menu.isDuplicate()) {
            statusStr = "\u00a7cDuplicate";
        } else if (menu.isCloakActive()) {
            boolean hasEnergy = menu.getEnergyStored() >= 30;
            statusStr = hasEnergy ? "\u00a7aCloaking" : "\u00a7eNo Power";
        } else {
            statusStr = "\u00a78Inactive";
        }
        graphics.drawString(font,
                Component.translatable("gui.vs_shields.cloak_generator.status", statusStr),
                x, y, LABEL_COLOR, false);
        y += 16;

        // Energy bar
        double energyPct = menu.getEnergyPercent();
        graphics.fill(x, y, x + barWidth, y + barHeight, BAR_BG_COLOR);
        int energyFill = (int) (barWidth * energyPct);
        if (energyFill > 0) {
            graphics.fill(x, y, x + energyFill, y + barHeight, ENERGY_COLOR);
        }
        graphics.renderOutline(x, y, barWidth, barHeight, BORDER_COLOR);
        String energyText = String.format("%,d / %,d FE", menu.getEnergyStored(), menu.getMaxEnergy());
        graphics.drawCenteredString(font, energyText, leftPos + imageWidth / 2, y + 2, 0xFFFFFFFF);
        y += barHeight + 8;

        // Consumption info
        int fePt = ShieldConfig.get().getCloak().energyPerTick;
        graphics.drawString(font,
                Component.translatable("gui.vs_shields.cloak_generator.consumption", fePt),
                x, y, LABEL_COLOR, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // No default labels
    }
}
