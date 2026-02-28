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

    /** Fire / launch key — Space bar. Also used to initiate the boost sequence. */
    public static final KeyMapping FIRE_KEY = new KeyMapping(
            "key.vs_shields.pod_fire",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_SPACE,
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

        } else if (phase == CockpitSeatEntity.Phase.BOOST || phase == CockpitSeatEntity.Phase.COAST) {
            // ── RCS: A/D (lateral) + Space (boost fuel) while in flight ──────
            boolean pressLeft  = mc.options.keyLeft.isDown();
            boolean pressRight = mc.options.keyRight.isDown();
            boolean boostDown  = FIRE_KEY.isDown();

            int lateralDir = (pressRight && !pressLeft) ? 1 : (pressLeft && !pressRight) ? -1 : 0;

            // Lateral is gated by cooldown and charges; boost is raw
            int lateralToSend = 0;
            if (lateralDir != 0 && localRcsCooldown == 0 && seat.getRcsCharges() > 0) {
                lateralToSend = lateralDir;
                localRcsCooldown = 12; // mirrors RCS_COOLDOWN_TICKS on server
            }

            // Always send each tick so server receives boost state every tick
            ModNetwork.sendBoardingPodRcs(seat.getId(), lateralToSend, boostDown ? 1 : 0);
        }
    }
}
