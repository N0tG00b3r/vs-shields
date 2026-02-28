package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Client-side handler for Boarding Pod aiming, fire key, and RCS impulses.
 * Registered via {@link VSShieldsModClient#initClient()}.
 *
 * Each tick:
 * - AIMING phase: if FIRE key is pressed → sends BOARDING_POD_FIRE packet.
 * - BOOST/COAST phase: if A or D is held → sends RCS impulse packet + applies
 *   local client-side prediction to avoid rubberbanding.
 */
public class BoardingPodClientHandler {

    /** Default: comma key (,) — can be rebound by the player. */
    public static final KeyMapping FIRE_KEY = new KeyMapping(
            "key.vs_shields.pod_fire",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_COMMA,
            "key.categories.vs_shields");

    /** RCS thrust upward (default: Space). */
    public static final KeyMapping RCS_UP_KEY = new KeyMapping(
            "key.vs_shields.rcs_up",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_SPACE,
            "key.categories.vs_shields");

    /** RCS thrust downward (default: C). */
    public static final KeyMapping RCS_DOWN_KEY = new KeyMapping(
            "key.vs_shields.rcs_down",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_C,
            "key.categories.vs_shields");

    /**
     * Client-side cooldown mirror — prevents sending repeated RCS packets
     * before the server-side cooldown expires.
     */
    private static int localRcsCooldown = 0;

    public static void tick() {
        // Always tick down so it resets even if the player isn't in a pod
        if (localRcsCooldown > 0) localRcsCooldown--;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof CockpitSeatEntity seat)) return;

        CockpitSeatEntity.Phase phase = seat.getPhase();

        if (phase == CockpitSeatEntity.Phase.AIMING) {
            // ── Fire key ──────────────────────────────────────────────────────
            while (FIRE_KEY.consumeClick()) {
                ModNetwork.sendBoardingPodFire(seat.getId(),
                        mc.player.getYRot(), mc.player.getXRot());
            }

        } else {
            // ── RCS: A/D (lateral) + Space/C (vertical) while in flight ──────
            boolean pressLeft  = mc.options.keyLeft.isDown();
            boolean pressRight = mc.options.keyRight.isDown();
            boolean pressUp    = RCS_UP_KEY.isDown();
            boolean pressDown  = RCS_DOWN_KEY.isDown();

            // Resolve lateral: cancel if both pressed
            int lateralDir  = (pressRight && !pressLeft) ? 1 : (pressLeft && !pressRight) ? -1 : 0;
            // Resolve vertical: cancel if both pressed
            int verticalDir = (pressUp && !pressDown)    ? 1 : (pressDown && !pressUp)    ? -1 : 0;

            if ((lateralDir != 0 || verticalDir != 0)
                    && localRcsCooldown == 0
                    && seat.getRcsCharges() > 0) {

                localRcsCooldown = 12; // mirrors RCS_COOLDOWN_TICKS on server

                // Tell the server — PodShipManager applies momentum burst to VS2 ship physics
                ModNetwork.sendBoardingPodRcs(seat.getId(), lateralDir, verticalDir);
            }
        }
    }
}
