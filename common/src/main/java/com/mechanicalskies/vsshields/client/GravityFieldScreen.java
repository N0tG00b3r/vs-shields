package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.blockentity.GravityFieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.menu.GravityFieldMenu;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class GravityFieldScreen extends AbstractContainerScreen<GravityFieldMenu> {
    private static final int BG_COLOR     = 0xCC080E20;
    private static final int BORDER_COLOR = 0xFF1A4A6E;
    private static final int BAR_BG       = 0xFF0A1830;

    private Button powerButton;
    private Button flightButton;
    private Button fallProtButton;

    public GravityFieldScreen(GravityFieldMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = 176;
        this.imageHeight = 145;
        this.inventoryLabelY = this.imageHeight + 1;
    }

    @Override
    protected void init() {
        super.init();
        // Power toggle — full width minus margins
        powerButton = Button.builder(Component.literal("ON"),
                btn -> ModNetwork.sendGravityToggleToServer(menu.getBlockPos(), !menu.isActive()))
                .bounds(leftPos + 8, topPos + 94, 160, 20).build();
        addRenderableWidget(powerButton);

        // Flight and fall-protection toggles side by side
        int subY = topPos + 118;
        flightButton = Button.builder(Component.literal("Flight: ON"),
                btn -> ModNetwork.sendGravityFlightToggleToServer(menu.getBlockPos(), !menu.isFlightEnabled()))
                .bounds(leftPos + 8, subY, 76, 20).build();
        addRenderableWidget(flightButton);

        fallProtButton = Button.builder(Component.literal("Fall: ON"),
                btn -> ModNetwork.sendGravityFallToggleToServer(menu.getBlockPos(), !menu.isFallProtectionEnabled()))
                .bounds(leftPos + 92, subY, 76, 20).build();
        addRenderableWidget(fallProtButton);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_COLOR);

        int cx = leftPos + imageWidth / 2;
        int y  = topPos + 8;
        g.drawCenteredString(font, title, cx, y, 0xFF88CCFF);
        y += 18;

        // --- Energy bar ---
        int barX = leftPos + 8;
        int barW = imageWidth - 16;
        int barH = 12;
        g.fill(barX, y, barX + barW, y + barH, BAR_BG);
        double pct = menu.getEnergyPercent();
        int fill = (int) (barW * pct);
        if (fill > 0) {
            int barColor = pct > 0.5 ? 0xFF0055FF : (pct > 0.2 ? 0xFF0033AA : 0xFF001166);
            g.fill(barX, y, barX + fill, y + barH, barColor);
        }
        g.renderOutline(barX, y, barW, barH, BORDER_COLOR);
        g.drawCenteredString(font,
                String.format("%,d / %,d FE", menu.getEnergyStored(), menu.getMaxEnergy()),
                cx, y + 2, 0xFFFFFFFF);
        y += barH + 4;

        // --- Cost hint ---
        boolean flight = menu.isFlightEnabled();
        boolean fall   = menu.isFallProtectionEnabled();
        int cost = GravityFieldGeneratorBlockEntity.COST_BASE
                 + (flight ? GravityFieldGeneratorBlockEntity.COST_FLIGHT    : 0)
                 + (fall   ? GravityFieldGeneratorBlockEntity.COST_FALL_PROT : 0);
        g.drawCenteredString(font, String.format("Usage: %,d FE/t", cost), cx, y, 0xFF6688AA);
        y += 14;

        // --- Status ---
        String statusText;
        int    statusColor;
        boolean active = menu.isActive();

        if (menu.isDuplicate()) {
            statusText  = "STATUS: DUPLICATE (Only 1 per ship)";
            statusColor = 0xFFFF5555;
        } else if (menu.getShipId() == -1) {
            statusText  = "STATUS: GROUNDED (Must be on ship)";
            statusColor = 0xFF5566FF;
        } else if (active) {
            statusText  = "STATUS: ACTIVE";
            statusColor = 0xFF00FFAA;
        } else {
            statusText  = "STATUS: OFFLINE";
            statusColor = 0xFF778899;
        }
        g.drawCenteredString(font, statusText, cx, y, statusColor);

        // --- Update buttons ---
        boolean canControl = !menu.isDuplicate() && menu.getShipId() != -1;

        powerButton.active = canControl;
        powerButton.setMessage(Component.translatable(active
                ? "gui.vs_shields.gravity_field.deactivate"
                : "gui.vs_shields.gravity_field.activate"));

        flightButton.active = canControl;
        flightButton.setMessage(Component.translatable(flight
                ? "gui.vs_shields.gravity_field.flight_on"
                : "gui.vs_shields.gravity_field.flight_off"));

        fallProtButton.active = canControl;
        fallProtButton.setMessage(Component.translatable(fall
                ? "gui.vs_shields.gravity_field.fall_on"
                : "gui.vs_shields.gravity_field.fall_off"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {}
}
