package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * HUD overlay shown while the player is inside a {@link CockpitSeatEntity}.
 *
 * Displays:
 *  - AIMING phase: status header + keybind hint
 *  - BOOST/COAST phase: speed (m/s) + boost fuel bar
 */
public class BoardingPodHudOverlay {

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;  // don't draw over open GUIs

        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof CockpitSeatEntity seat)) return;

        CockpitSeatEntity.Phase phase = seat.getPhase();

        if (phase == CockpitSeatEntity.Phase.AIMING) {
            renderAimingHud(graphics, mc, seat);
        } else {
            renderFlightHud(graphics, mc, seat);
        }
    }

    // ── Per-phase renders ─────────────────────────────────────────────────────

    private static void renderAimingHud(GuiGraphics graphics, Minecraft mc, CockpitSeatEntity seat) {
        int sw  = mc.getWindow().getGuiScaledWidth();
        int sh  = mc.getWindow().getGuiScaledHeight();
        int cx  = sw / 2;
        int top = sh / 4;

        graphics.drawCenteredString(mc.font, Component.literal("§6§lBOARDING POD"), cx, top,      0xFFFFFF);
        graphics.drawCenteredString(mc.font, Component.literal("§a[ AIMING ]"),     cx, top + 12, 0xFFFFFF);

        float yaw   = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        graphics.drawCenteredString(mc.font,
                Component.literal(String.format("§7Yaw: %.1f°  Pitch: %.1f°", yaw, pitch)),
                cx, top + 24, 0xFFFFFF);

        String keyName = BoardingPodClientHandler.FIRE_KEY.getTranslatedKeyMessage().getString();
        graphics.drawCenteredString(mc.font,
                Component.literal("§e[" + keyName + "]§7 FIRE   §e[Sneak]§7 Dismount"),
                cx, top + 38, 0xFFFFFF);
    }

    private static void renderFlightHud(GuiGraphics g, Minecraft mc, CockpitSeatEntity seat) {
        int sw      = mc.getWindow().getGuiScaledWidth();
        int sh      = mc.getWindow().getGuiScaledHeight();
        int cx      = sw / 2;
        int bottom  = sh - 50; // just above the hotbar

        // Speed (m/s) synced from server via SPEED_MPS each tick
        int speedMps = seat.getSpeedMps();
        g.drawCenteredString(mc.font, Component.literal("§f" + speedMps + " §7m/s"), cx, bottom - 26, 0xFFFFFF);

        int fuel    = seat.getBoostFuel();
        int fuelMax = CockpitSeatEntity.BOOST_TICKS_MAX;

        // "BOOST" label
        g.drawCenteredString(mc.font, Component.literal("§7BOOST"), cx, bottom - 14, 0xFFFFFF);

        // Fuel bar (40 px wide, colour: green → yellow → red as fuel drains)
        int barW   = 40;
        int filled = (int) Math.round((double) fuel / fuelMax * barW);
        float frac = (float) fuel / fuelMax;
        int colour = frac > 0.5f ? 0xFF00FF00 : frac > 0.25f ? 0xFFFFAA00 : 0xFFFF4444;
        g.fill(cx - barW / 2,          bottom, cx - barW / 2 + filled, bottom + 4, colour);
        g.fill(cx - barW / 2 + filled, bottom, cx + barW / 2,          bottom + 4, 0xFF444444);
    }
}
