package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.menu.ShieldJammerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.world.entity.player.Inventory;

public class ShieldJammerScreen extends AbstractContainerScreen<ShieldJammerMenu> {
    private static final int BG_COLOR = 0xCC1A1A2E;
    private static final int BORDER_COLOR = 0xFF3A3A5E;
    private static final int BAR_BG_COLOR = 0xFF2A2A3E;

    private Button forceReloadButton;
    private Button enableToggleButton;

    public ShieldJammerScreen(ShieldJammerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 130;
        this.inventoryLabelY = this.imageHeight + 1;
    }

    @Override
    protected void init() {
        super.init();
        // Two buttons side-by-side: [Reload 76px] [8px gap] [Disable/Enable 76px]
        int btnY = topPos + 95;
        int startX = leftPos + 8;
        forceReloadButton = Button.builder(Component.translatable("gui.vs_shields.shield_jammer.reload"), btn -> {
            ModNetwork.sendJammerReloadToServer(menu.getBlockPos());
        }).bounds(startX, btnY, 76, 20).build();
        addRenderableWidget(forceReloadButton);

        enableToggleButton = Button.builder(Component.translatable("gui.vs_shields.shield_jammer.disable"), btn -> {
            ModNetwork.sendJammerEnableToServer(menu.getBlockPos(), !menu.isEnabled());
        }).bounds(startX + 76 + 8, btnY, 76, 20).build();
        addRenderableWidget(enableToggleButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_COLOR);

        int x = leftPos + 12;
        int y = topPos + 8;

        graphics.drawCenteredString(font, title, leftPos + imageWidth / 2, y, 0xFFFFFFFF);
        y += 20;

        int barX = x;
        int barWidth = imageWidth - 24;
        int barHeight = 12;

        double energyPct = menu.getEnergyPercent();
        graphics.fill(barX, y, barX + barWidth, y + barHeight, BAR_BG_COLOR);
        int energyFill = (int) (barWidth * energyPct);
        if (energyFill > 0) {
            graphics.fill(barX, y, barX + energyFill, y + barHeight, 0xFFFF4400);
        }
        graphics.renderOutline(barX, y, barWidth, barHeight, BORDER_COLOR);
        String energyText = String.format("%,d / %,d FE", menu.getEnergyStored(), menu.getMaxEnergy());
        graphics.drawCenteredString(font, energyText, leftPos + imageWidth / 2, y + 2, 0xFFFFFFFF);

        y += barHeight + 15;

        // Draw Status
        String statusText;
        int statusColor;

        boolean isEnabled = menu.isEnabled();
        boolean inCooldown = menu.isCooldown() || menu.getForcedCooldownTicks() > 0;

        if (menu.isDuplicate()) {
            statusText = "STATUS: DUPLICATE (Only 1 allowed)";
            statusColor = 0xFFFF5555; // Red
            forceReloadButton.active = false;
            enableToggleButton.active = false;
        } else if (menu.getShipId() == -1) {
            statusText = "STATUS: GROUNDED (Must be on Ship)";
            statusColor = 0xFF5555FF; // Blue
            forceReloadButton.active = false;
            enableToggleButton.active = false;
        } else if (menu.getForcedCooldownTicks() > 0) {
            statusText = String.format("RECHARGING: %d s", menu.getForcedCooldownTicks() / 20);
            statusColor = 0xFFFF9900; // Orange
            forceReloadButton.active = false;
            enableToggleButton.active = false;
        } else if (menu.isCooldown()) {
            statusText = "STATUS: REBOOTING (LOW ENERGY)";
            statusColor = 0xFFFF3333; // Red
            forceReloadButton.active = false;
            enableToggleButton.active = false;
        } else if (!isEnabled) {
            statusText = "STATUS: OFFLINE (DISABLED)";
            statusColor = 0xFF888888; // Gray
            forceReloadButton.active = false;
            enableToggleButton.active = true;
        } else if (menu.isActive()) {
            statusText = "STATUS: ACTIVE (SCANNING)";
            statusColor = 0xFF55FF55; // Green
            forceReloadButton.active = true;
            enableToggleButton.active = true;
        } else {
            statusText = String.format("STRUCTURAL ERROR (26 req, found %d)", menu.getFrameCount());
            statusColor = 0xFFCCCCCC; // Gray
            forceReloadButton.active = false;
            enableToggleButton.active = false;
        }

        // Update toggle button label to match current state
        if (isEnabled) {
            enableToggleButton.setMessage(Component.translatable("gui.vs_shields.shield_jammer.disable"));
        } else {
            enableToggleButton.setMessage(Component.translatable("gui.vs_shields.shield_jammer.enable"));
        }

        graphics.drawCenteredString(font, statusText, leftPos + imageWidth / 2, y, statusColor);
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
