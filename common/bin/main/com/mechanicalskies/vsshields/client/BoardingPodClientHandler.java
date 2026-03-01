package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Client-side handler for Boarding Pod aiming, fire key, and mouse steering.
 * Registered via {@link VSShieldsModClient#initClient()}.
 *
 * Each tick:
 * - AIMING phase: detect fresh Space press (edge, not queued) → send BOARDING_POD_FIRE.
 * - BOOST/COAST phase: send yaw/pitch + boostActive every tick → server rotates launchDir.
 */
public class BoardingPodClientHandler {

    /** Fire / launch key — Space bar. Also used to hold for boost thrust. */
    public static final KeyMapping FIRE_KEY = new KeyMapping(
            "key.vs_shields.pod_fire",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_SPACE,
            "key.categories.vs_shields");

    /** Edge-detect for Space in AIMING — prevents queued pre-board presses from auto-firing. */
    private static boolean prevFireDown = false;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof CockpitSeatEntity seat)) {
            prevFireDown = false;
            return;
        }

        CockpitSeatEntity.Phase phase = seat.getPhase();

        if (phase == CockpitSeatEntity.Phase.AIMING) {
            // ── Fire: only on fresh key-down edge, ignores queued presses ────
            boolean fireDown = FIRE_KEY.isDown();
            if (fireDown && !prevFireDown) {
                ModNetwork.sendBoardingPodFire(seat.getId(),
                        mc.player.getYRot(), mc.player.getXRot());
            }
            prevFireDown = fireDown;

        } else {
            prevFireDown = false; // reset so re-entry to AIMING can't auto-fire

            if (phase == CockpitSeatEntity.Phase.BOOST || phase == CockpitSeatEntity.Phase.COAST) {
                // ── Steering: send look direction + boost state every tick ───
                // Server rotates launchDir toward player look at ≤3°/tick.
                // Use camera's actual look vector — VS2 applies pod ship rotation to the camera
                // via setupWithShipMounted, so mc.player.getYRot/XRot() (raw world angles) don't
                // match what the crosshair actually points at when the ship has any rotation.
                boolean boostDown = FIRE_KEY.isDown();
                org.joml.Vector3f look = mc.gameRenderer.getMainCamera().getLookVector();
                float cameraYaw   = (float) Math.toDegrees(Math.atan2(-look.x(), look.z()));
                float cameraPitch = (float) Math.toDegrees(
                        Math.asin(net.minecraft.util.Mth.clamp(-look.y(), -1.0, 1.0)));
                ModNetwork.sendBoardingPodRcs(seat.getId(),
                        cameraYaw, cameraPitch,
                        boostDown ? 1 : 0);
            }
        }
    }
}
