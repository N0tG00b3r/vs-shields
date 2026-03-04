package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.item.AethericCompassItem;
import com.mechanicalskies.vsshields.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * HUD overlay that draws a rotating compass needle when the player holds
 * an Aetheric Compass in either hand.
 *
 * The needle uses {@link AethericCompassItem#computeAngle} for direction:
 * - No anomaly: slow spin (searching)
 * - > chaos radius: points toward anomaly
 * - ≤ chaos radius: wild erratic spin (interference)
 */
public class CompassHudOverlay {

    private static final int COMPASS_SIZE = 48;
    private static final int NEEDLE_LEN = 18;

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Check if holding compass in either hand
        ItemStack mainHand = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = mc.player.getItemInHand(InteractionHand.OFF_HAND);
        boolean hasCompass = (!mainHand.isEmpty() && mainHand.is(ModItems.AETHERIC_COMPASS.get()))
                || (!offHand.isEmpty() && offHand.is(ModItems.AETHERIC_COMPASS.get()));
        if (!hasCompass) return;

        // Get the angle
        float angle01 = AethericCompassItem.computeAngle(mc.level, mc.player, mainHand);
        double angleRad = angle01 * Math.PI * 2.0;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Position: bottom center, above hotbar
        int cx = sw / 2;
        int cy = sh - 60;

        // Draw background circle
        drawCircle(graphics, cx, cy, COMPASS_SIZE / 2, 0x88000000);
        drawRing(graphics, cx, cy, COMPASS_SIZE / 2, 0xAA6644AA);

        // Cardinal letters
        graphics.drawCenteredString(mc.font, "N", cx, cy - COMPASS_SIZE / 2 - 10, 0xFFCC88FF);

        // Draw needle
        // Needle points in the direction of angleRad (0 = north/up on screen)
        // Screen coords: up = -Y, right = +X
        double ndx = Math.sin(angleRad);
        double ndy = -Math.cos(angleRad);

        int tipX = cx + (int) (ndx * NEEDLE_LEN);
        int tipY = cy + (int) (ndy * NEEDLE_LEN);
        int tailX = cx - (int) (ndx * 6);
        int tailY = cy - (int) (ndy * 6);

        // Red tip (forward)
        drawLine(graphics, cx, cy, tipX, tipY, 0xFFFF4444);
        // White tail (backward)
        drawLine(graphics, cx, cy, tailX, tailY, 0xFFCCCCDD);

        // Dot at center
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
    }

    private static void drawCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        // Approximate with filled rects
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private static void drawRing(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        int steps = 64;
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2.0 * i / steps;
            int x = cx + (int) (Math.cos(a) * radius);
            int y = cy + (int) (Math.sin(a) * radius);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int steps = 0;
        while (steps < 200) {
            // Draw a 2px wide point for visibility
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
            steps++;
        }
    }
}
