package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * HUD overlay shown while the player is inside a {@link BoardingPodEntity}
 * in AIMING phase.
 *
 * Displays:
 *  - Status header + keybind hint
 *  - Ballistic arc projected to screen (replaces the plain crosshair)
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

        renderArc(graphics, mc, seat, yaw, pitch);
    }

    private static void renderFlightHud(GuiGraphics g, Minecraft mc, CockpitSeatEntity seat) {
        int sw      = mc.getWindow().getGuiScaledWidth();
        int sh      = mc.getWindow().getGuiScaledHeight();
        int cx      = sw / 2;
        int bottom  = sh - 50; // just above the hotbar

        int charges  = seat.getRcsCharges();
        int cooldown = seat.getRcsCooldown();

        // "RCS" label
        g.drawCenteredString(mc.font, Component.literal("§7RCS"), cx, bottom - 14, 0xFFFFFF);

        // Charge pips: ● available (green / yellow-on-cooldown), ○ spent (dark grey)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CockpitSeatEntity.RCS_MAX_CHARGES; i++) {
            if (i < charges) {
                sb.append(cooldown > 0 ? "§6●" : "§a●");
            } else {
                sb.append("§8○");
            }
            if (i < CockpitSeatEntity.RCS_MAX_CHARGES - 1) sb.append(" ");
        }
        g.drawCenteredString(mc.font, Component.literal(sb.toString()), cx, bottom, 0xFFFFFF);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void renderArc(GuiGraphics g, Minecraft mc,
                                   CockpitSeatEntity seat, float fireYaw, float firePitch) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3  camPos  = camera.getPosition();

        // Camera basis vectors
        Vec3 fwd = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        Vec3 worldUpRef = Math.abs(fwd.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right  = cross(fwd, worldUpRef).normalize();
        Vec3 camUp  = cross(right, fwd).normalize();

        // Perspective parameters (use window pixel ratio for correct aspect)
        int   winW  = mc.getWindow().getWidth();
        int   winH  = Math.max(1, mc.getWindow().getHeight());
        float aspect    = (float) winW / winH;
        float fovRad    = (float) Math.toRadians(mc.options.fov().get());
        float tHalfFovV = (float) Math.tan(fovRad * 0.5);
        float tHalfFovH = tHalfFovV * aspect;

        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();

        // ── Simulate trajectory (server physics mirrored client-side) ─────────
        double speed = 1.2;
        double radY  = Math.toRadians(fireYaw);
        double radP  = Math.toRadians(firePitch);
        double vx = -Math.sin(radY) * Math.cos(radP) * speed;
        double vy = -Math.sin(radP)                  * speed;
        double vz =  Math.cos(radY) * Math.cos(radP) * speed;
        double px = seat.getX(), py = seat.getY() + 0.5, pz = seat.getZ();

        int prevSx = Integer.MIN_VALUE, prevSy = Integer.MIN_VALUE;
        final int STEPS = 200;   // simulate up to 200 ticks (10 seconds)
        final int DOT_EVERY = 4; // draw a dot every 4 ticks

        for (int t = 1; t <= STEPS; t++) {
            if (t <= 40) {           // BOOST: minimal gravity
                vy -= 0.005;
            } else {                 // COAST: full gravity + drag
                vx *= 0.99;
                vy -= 0.08;
                vz *= 0.99;
            }
            px += vx;  py += vy;  pz += vz;

            if (t % DOT_EVERY != 0) continue;

            float[] sc = project(px, py, pz, camPos, fwd, right, camUp,
                                 tHalfFovH, tHalfFovV, guiW, guiH);
            if (sc == null) { prevSx = Integer.MIN_VALUE; continue; }

            int sx = (int) sc[0];
            int sy = (int) sc[1];

            // Gradient: bright orange at start → dark red at end
            float frac  = (float) t / STEPS;
            int   alpha = (int) (200 - frac * 120);
            int   green = (int) (160 - frac * 160);
            int   color = (alpha << 24) | (255 << 16) | (green << 8);

            // Line from previous dot to current
            if (prevSx != Integer.MIN_VALUE) {
                drawLine(g, prevSx, prevSy, sx, sy, color);
            }

            // Dot (3×3)
            g.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);
            prevSx = sx;  prevSy = sy;
        }
    }

    // ── Projection helpers ────────────────────────────────────────────────────

    /**
     * Projects a world-space point onto GUI screen coordinates.
     * Returns null if the point is behind the camera or outside the frustum.
     */
    @Nullable
    private static float[] project(double wx, double wy, double wz,
                                    Vec3 camPos, Vec3 fwd, Vec3 right, Vec3 up,
                                    float tHalfFovH, float tHalfFovV,
                                    int guiW, int guiH) {
        double dx    = wx - camPos.x;
        double dy    = wy - camPos.y;
        double dz    = wz - camPos.z;
        double depth = dx * fwd.x   + dy * fwd.y   + dz * fwd.z;
        if (depth < 1.0) return null; // behind camera or too close

        double rComp = dx * right.x + dy * right.y + dz * right.z;
        double uComp = dx * up.x    + dy * up.y    + dz * up.z;

        float ndcX = (float) (rComp / (depth * tHalfFovH));
        float ndcY = (float) (uComp / (depth * tHalfFovV));
        if (ndcX < -1.1f || ndcX > 1.1f || ndcY < -1.1f || ndcY > 1.1f) return null;

        return new float[]{
            (float) ((ndcX + 1.0) * 0.5 * guiW),
            (float) ((1.0 - ndcY) * 0.5 * guiH)
        };
    }

    /** Rasterise a line segment with single-pixel fills. */
    private static void drawLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx    = Math.abs(x2 - x1);
        int dy    = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) return;
        // Cap to avoid huge loops when a point suddenly jumps across the screen
        if (steps > 120) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    /** Manual cross product (Vec3 in MC 1.20.1 has no cross() method). */
    private static Vec3 cross(Vec3 a, Vec3 b) {
        return new Vec3(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x
        );
    }
}
