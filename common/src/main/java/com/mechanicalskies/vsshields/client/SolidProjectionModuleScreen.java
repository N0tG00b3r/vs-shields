package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.menu.SolidProjectionModuleMenu;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class SolidProjectionModuleScreen extends AbstractContainerScreen<SolidProjectionModuleMenu> {
    private static final int BG_COLOR     = 0xCC0A1A1A;
    private static final int BORDER_COLOR = 0xFF1A6A6A;
    private static final int BAR_BG       = 0xFF0A1818;

    private Button toggleButton;
    private EditBox codeBox;

    public SolidProjectionModuleScreen(SolidProjectionModuleMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = 200;
        this.imageHeight = 160;
        this.inventoryLabelY = this.imageHeight + 1;
    }

    @Override
    protected void init() {
        super.init();

        // Access code EditBox
        codeBox = new EditBox(font, leftPos + 10, topPos + 85, 100, 18,
                Component.translatable("gui.vs_shields.solid_module.access_code"));
        codeBox.setMaxLength(8);
        codeBox.setValue(menu.getAccessCode());
        codeBox.setFilter(s -> s.matches("[a-zA-Z0-9]*"));
        addRenderableWidget(codeBox);

        // Save code button
        Button saveButton = Button.builder(
                Component.translatable("gui.vs_shields.solid_module.save_code"),
                btn -> sendCode())
                .bounds(leftPos + 115, topPos + 85, 72, 18)
                .build();
        addRenderableWidget(saveButton);

        // Toggle button
        toggleButton = Button.builder(getToggleText(),
                btn -> ModNetwork.sendSolidToggleToServer(menu.getBlockPos(), !menu.isActive()))
                .bounds(leftPos + (imageWidth - 80) / 2, topPos + 130, 80, 18)
                .build();
        addRenderableWidget(toggleButton);
    }

    private void sendCode() {
        String code = codeBox.getValue().replaceAll("[^a-zA-Z0-9]", "");
        if (code.length() > 8) code = code.substring(0, 8);
        ModNetwork.sendSolidCodeSetToServer(menu.getBlockPos(), code);
    }

    @Override
    public void removed() {
        // Send code on close
        sendCode();
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / NumPad Enter
            sendCode();
            return true;
        }
        // Allow ESC to close even when editbox has focus
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private Component getToggleText() {
        return Component.translatable(menu.isActive()
                ? "gui.vs_shields.solid_module.deactivate"
                : "gui.vs_shields.solid_module.activate");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (toggleButton != null) toggleButton.setMessage(getToggleText());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_COLOR);

        int cx  = leftPos + imageWidth / 2;
        int y   = topPos + 8;
        g.drawCenteredString(font, title, cx, y, 0xFF88FFFF);
        y += 18;

        // Energy bar
        int barX = leftPos + 8;
        int barW = imageWidth - 16;
        int barH = 12;
        g.fill(barX, y, barX + barW, y + barH, BAR_BG);
        double pct = menu.getEnergyPercent();
        int fill = (int)(barW * pct);
        if (fill > 0) {
            int col = pct > 0.5 ? 0xFF00DDDD : (pct > 0.2 ? 0xFF009999 : 0xFF004444);
            g.fill(barX, y, barX + fill, y + barH, col);
        }
        g.renderOutline(barX, y, barW, barH, BORDER_COLOR);
        g.drawCenteredString(font,
                String.format("%,d / %,d FE", menu.getEnergyStored(), menu.getMaxEnergy()),
                cx, y + 2, 0xFFFFFFFF);
        y += barH + 6;

        // Status
        String statusText;
        int statusColor;
        if (menu.isDuplicate()) {
            statusText  = "STATUS: DUPLICATE";
            statusColor = 0xFFFF5555;
        } else if (menu.getShipId() == -1) {
            statusText  = "STATUS: GROUNDED";
            statusColor = 0xFF5566FF;
        } else if (menu.isActive()) {
            statusText  = "STATUS: ACTIVE";
            statusColor = 0xFF00FFAA;
        } else if (menu.getEnergyStored() == 0) {
            statusText  = "STATUS: NO ENERGY";
            statusColor = 0xFFFF9900;
        } else {
            statusText  = "STATUS: OFFLINE";
            statusColor = 0xFF778899;
        }
        g.drawCenteredString(font, statusText, cx, y, statusColor);
        y += 14;

        // Access code label
        g.drawString(font, Component.translatable("gui.vs_shields.solid_module.access_code"),
                leftPos + 10, y, 0xFF88FFFF, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}
}
