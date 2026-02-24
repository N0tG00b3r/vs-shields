package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.menu.ShieldGeneratorMenu;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ShieldGeneratorScreen extends AbstractContainerScreen<ShieldGeneratorMenu> {
    private static final int BG_COLOR = 0xCC1A1A2E;
    private static final int BORDER_COLOR = 0xFF3A3A5E;
    private static final int BAR_BG_COLOR = 0xFF2A2A3E;
    private static final int LABEL_COLOR = 0xFF8888AA;

    private Button toggleButton;

    public ShieldGeneratorScreen(ShieldGeneratorMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 140;
        this.inventoryLabelY = this.imageHeight + 1;
    }

    @Override
    protected void init() {
        super.init();
        int btnX = this.leftPos + (this.imageWidth - 60) / 2;
        int btnY = this.topPos + 110;
        toggleButton = Button.builder(getToggleText(), this::onTogglePress)
                .bounds(btnX, btnY, 60, 20)
                .build();
        addRenderableWidget(toggleButton);
    }

    private Component getToggleText() {
        return Component.translatable(menu.isShieldActive()
                ? "gui.vs_shields.shield_generator.deactivate"
                : "gui.vs_shields.shield_generator.activate");
    }

    private void onTogglePress(Button button) {
        boolean newState = !menu.isShieldActive();
        ModNetwork.sendToggleToServer(menu.getShipId(), newState);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (toggleButton != null) {
            toggleButton.setMessage(getToggleText());
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_COLOR);

        int x = leftPos + 12;
        int y = topPos + 8;

        graphics.drawCenteredString(font, title, leftPos + imageWidth / 2, y, 0xFFFFFFFF);
        y += 16;

        double hpPct = menu.getHPPercent();
        int barX = x;
        int barWidth = imageWidth - 24;
        int barHeight = 12;

        graphics.fill(barX, y, barX + barWidth, y + barHeight, BAR_BG_COLOR);
        int fillWidth = (int) (barWidth * hpPct);
        if (fillWidth > 0) {
            graphics.fill(barX, y, barX + fillWidth, y + barHeight, getHPColor(hpPct));
        }
        graphics.renderOutline(barX, y, barWidth, barHeight, BORDER_COLOR);

        String hpText = String.format("%.0f / %.0f HP", menu.getCurrentHP(), menu.getMaxHP());
        graphics.drawCenteredString(font, hpText, leftPos + imageWidth / 2, y + 2, 0xFFFFFFFF);
        y += barHeight + 8;

        String status = menu.isShieldActive() ? "\u00a7aActive" : "\u00a7cInactive";
        graphics.drawString(font, Component.translatable("gui.vs_shields.shield_generator.status", status), x, y,
                LABEL_COLOR, false);
        y += 12;

        String rechargeStatus = hpPct >= 1.0 ? "\u00a7aFull"
                : (menu.isShieldActive() ? (menu.isRegenStalled() ? "\u00a7eStalled (Low Pwr)" : "\u00a7eRecharging")
                        : "\u00a78Offline");
        graphics.drawString(font, Component.translatable("gui.vs_shields.shield_generator.recharge", rechargeStatus), x,
                y, LABEL_COLOR, false);
        y += 14;

        double energyPct = menu.getEnergyPercent();
        graphics.fill(barX, y, barX + barWidth, y + barHeight, BAR_BG_COLOR);
        int energyFill = (int) (barWidth * energyPct);
        if (energyFill > 0) {
            graphics.fill(barX, y, barX + energyFill, y + barHeight, 0xFFFF4400);
        }
        graphics.renderOutline(barX, y, barWidth, barHeight, BORDER_COLOR);
        String energyText = String.format("%,d / %,d FE", menu.getEnergyStored(), menu.getMaxEnergy());
        graphics.drawCenteredString(font, energyText, leftPos + imageWidth / 2, y + 2, 0xFFFFFFFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private static int getHPColor(double percent) {
        if (percent > 0.5)
            return 0xFF3399FF;
        if (percent > 0.25)
            return 0xFFFFAA00;
        return 0xFFFF3333;
    }
}
