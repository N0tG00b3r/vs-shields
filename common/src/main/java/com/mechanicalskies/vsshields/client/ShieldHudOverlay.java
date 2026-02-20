package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.network.ClientShieldManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShieldHudOverlay {
    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Player player = mc.player;
        Ship ship = VSGameUtilsKt.getShipManagingPos(player.level(), player.blockPosition());
        if (ship == null) return;

        ClientShieldManager.ClientShieldData data = ClientShieldManager.getInstance().getShield(ship.getId());
        if (data == null || !data.active || data.currentHP <= 0) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int barWidth = 100;
        int barHeight = 10;
        int x = (screenWidth - barWidth) / 2;
        int y = 10;

        double hpPercent = data.getHPPercent();
        int fillColor = getHPColor(hpPercent);

        graphics.fill(x, y, x + barWidth, y + barHeight, 0x80000000);
        graphics.fill(x, y, x + (int) (barWidth * hpPercent), y + barHeight, fillColor);
        graphics.renderOutline(x, y, barWidth, barHeight, 0xFFFFFFFF);
    }

    private static int getHPColor(double percent) {
        if (percent > 0.5) return 0xFF3399FF;
        if (percent > 0.25) return 0xFFFFAA00;
        return 0xFFFF3333;
    }
}
