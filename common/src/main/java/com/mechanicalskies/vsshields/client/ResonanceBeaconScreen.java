package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.menu.ResonanceBeaconMenu;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ResonanceBeaconScreen extends AbstractContainerScreen<ResonanceBeaconMenu> {

    // Scan result state (set by S2C packet via ClientNetworkHandler)
    private static boolean hasResult = false;
    private static boolean resultFound = false;
    private static double resultX, resultY, resultZ;
    private static int resultTTL;

    private Button scanButton;

    public ResonanceBeaconScreen(ResonanceBeaconMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    public static void setResult(boolean found, double x, double y, double z, int ttl) {
        hasResult = true;
        resultFound = found;
        resultX = x;
        resultY = y;
        resultZ = z;
        resultTTL = ttl;
    }

    public static void clearResult() {
        hasResult = false;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        scanButton = Button.builder(
                Component.translatable("gui.vs_shields.beacon.scan"),
                btn -> {
                    ModNetwork.sendBeaconScanStart(menu.getBlockPos());
                    clearResult();
                })
                .bounds(leftPos + 105, topPos + 32, 50, 20)
                .build();
        addRenderableWidget(scanButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Dark background
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xCC1A1A2E);
        // Border
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF6644AA);
        graphics.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF6644AA);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF6644AA);
        graphics.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF6644AA);

        // Energy bar (left side, 10×52)
        int barX = leftPos + 10;
        int barY = topPos + 18;
        int barW = 10;
        int barH = 52;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF333355);
        double ep = menu.getEnergyPercent();
        int fillH = (int) (barH * ep);
        if (fillH > 0) {
            int color = ep > 0.5 ? 0xFF44CCCC : ep > 0.2 ? 0xFF228888 : 0xFF881111;
            graphics.fill(barX, barY + barH - fillH, barX + barW, barY + barH, color);
        }

        // Energy text
        String feText = formatFE(menu.getEnergyStored()) + " FE";
        graphics.drawString(font, feText, leftPos + 25, topPos + 58, 0xFFAAAACC, false);

        // Crystal slot outline
        graphics.fill(leftPos + 78, topPos + 33, leftPos + 98, topPos + 53, 0xFF333355);

        // Scan progress bar
        if (menu.isScanning()) {
            int progX = leftPos + 30;
            int progY = topPos + 70;
            int progW = 116;
            int progH = 6;
            graphics.fill(progX - 1, progY - 1, progX + progW + 1, progY + progH + 1, 0xFF222244);
            int fillW = (int) (progW * menu.getScanPercent());
            if (fillW > 0) {
                graphics.fill(progX, progY, progX + fillW, progY + progH, 0xFF8844FF);
            }
            graphics.drawCenteredString(font,
                    Component.translatable("gui.vs_shields.beacon.scanning"),
                    leftPos + imageWidth / 2, progY - 10, 0xFFCC88FF);
        }

        // Scan button state
        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();
        boolean canScan = !menu.isScanning()
                && menu.getEnergyStored() >= config.beaconScanCost;
        scanButton.active = canScan;

        // Results area
        if (hasResult && !menu.isScanning()) {
            int resY = topPos + 70;
            if (resultFound) {
                String coords = String.format("X: %d  Y: %d  Z: %d",
                        (int) resultX, (int) resultY, (int) resultZ);
                graphics.drawCenteredString(font, coords, leftPos + imageWidth / 2, resY, 0xFF44FF88);

                int mins = resultTTL / 60;
                int secs = resultTTL % 60;
                String timeStr = String.format("TTL: %d:%02d", mins, secs);
                int timeColor = resultTTL > 300 ? 0xFFFFFFFF : resultTTL > 60 ? 0xFFFFFF44 : 0xFFFF4444;
                graphics.drawCenteredString(font, timeStr, leftPos + imageWidth / 2, resY + 12, timeColor);
            } else {
                graphics.drawCenteredString(font,
                        Component.translatable("gui.vs_shields.beacon.no_anomaly"),
                        leftPos + imageWidth / 2, resY, 0xFF888888);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFCC88FF, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                0xFFAAAACC, false);
    }

    private String formatFE(int fe) {
        if (fe >= 1_000_000) return String.format("%.1fM", fe / 1_000_000.0);
        if (fe >= 1_000) return String.format("%.1fk", fe / 1_000.0);
        return String.valueOf(fe);
    }
}
