package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.network.ClientShieldManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShieldHudOverlay {
    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;

        Player player = mc.player;
        ClientShieldManager.ClientShieldData data = null;

        // Fast path: player stands on a ship with a shield
        Ship ship = VSGameUtilsKt.getShipManagingPos(player.level(), player.blockPosition());
        if (ship != null) {
            data = ClientShieldManager.getInstance().getShield(ship.getId());
        }

        // Slow path: player is near a shielded ship (within the shield zone)
        if (data == null) {
            double padding = ShieldConfig.get().getGeneral().shieldPadding;
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            for (ClientShieldManager.ClientShieldData candidate : ClientShieldManager.getInstance().getAllShields()
                    .values()) {
                if (!candidate.active || candidate.currentHP <= 0)
                    continue;
                if (candidate.containsInflated(px, py, pz, padding)) {
                    data = candidate;
                    break;
                }
            }
        }

        if (data == null || !data.active || data.currentHP <= 0)
            return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int barWidth = 100;
        int barHeight = 10;
        int x = (screenWidth - barWidth) / 2;
        int y = 10;

        double hpPercent = data.getHPPercent();
        int fillColor = getHPColor(hpPercent);

        graphics.fill(x, y, x + barWidth, y + barHeight, 0x80000000);
        graphics.fill(x, y, x + (int) (barWidth * hpPercent), y + barHeight, fillColor);
        graphics.renderOutline(x, y, barWidth, barHeight, 0xFFFFFFFF);

        // Shield HP label
        String label = String.format("Shield: %.0f / %.0f (%.0f%%)", data.currentHP, data.maxHP, hpPercent * 100);
        int textWidth = mc.font.width(label);
        graphics.drawString(mc.font, label, (screenWidth - textWidth) / 2, y + barHeight + 2, 0xFFFFFFFF, true);

        // Solid mode indicator
        if (data.solidMode) {
            String lockLabel = "\u26d4 SOLID";  // ⛔ SOLID
            int lockWidth = mc.font.width(lockLabel);
            graphics.drawString(mc.font, lockLabel, (screenWidth - lockWidth) / 2, y + barHeight + 13, 0xFF00FFCC, true);
        }
    }

    private static int getHPColor(double percent) {
        if (percent > 0.5)
            return 0xFF3399FF;
        if (percent > 0.25)
            return 0xFFFFAA00;
        return 0xFFFF3333;
    }
}
