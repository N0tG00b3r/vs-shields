package com.mechanicalskies.vsshields.forge.mixin

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import org.joml.Vector3d
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.client.IVSCamera
import org.valkyrienskies.mod.common.shipObjectWorld

/**
 * Locks the camera to the VS2 pod ship using VS2's render-interpolated transform.
 *
 * **Root cause of rubber band:** PodShipManager was calling seat.setPos(shipCOM) every
 * game tick (20 Hz), fighting VS2's own entity dragger.  The resulting oscillating entity
 * position propagated through worldToShip → jittery inShipPlayerPosition → camera shake.
 *
 * **Fix (two parts):**
 *
 * 1. PodShipManager no longer calls seat.setPos() — VS2 drags the entity naturally.
 *
 * 2. Camera anchor = ship's own COM in shipyard space:
 *      comInShipyard = worldToShip × positionInWorld
 *    Both factors update at 20 Hz together, so the product is CONSTANT regardless of
 *    ship velocity.  VS2's setupWithShipMounted then projects it through
 *    renderTransform.shipToWorld (updated every render frame) → perfectly smooth result.
 *
 * Call chain:
 *   GameRenderer.render HEAD → VS2 updateRenderTransforms(pt)   ← renderTransform ready
 *   → Camera.setup(...)       → vanilla places camera at entity pos
 *   → our RETURN inject: cast Camera to IVSCamera, call setupWithShipMounted
 *       → renderTransform.shipToWorld × comInShipyard → render-interp world eye pos
 */
@Mixin(Camera::class)
abstract class MixinCockpitCamera {

    @Inject(method = ["setup"], at = [At("RETURN")])
    private fun onSetup(
        level: BlockGetter,
        entity: Entity,
        detached: Boolean,
        invertedView: Boolean,
        partialTick: Float,
        ci: CallbackInfo
    ) {
        val player = entity as? LocalPlayer ?: return
        val seat   = player.vehicle as? CockpitSeatEntity ?: return

        val podShipId = seat.getPodShipId()
        if (podShipId == Long.MIN_VALUE) return               // ship ID not yet synced

        val clientLevel = Minecraft.getInstance().level ?: return
        val ship = (clientLevel.shipObjectWorld.loadedShips.getById(podShipId)
                        as? ClientShip) ?: return

        // Ship's centre-of-mass in its own shipyard coordinate space.
        // worldToShip × positionInWorld = COM in shipyard = CONSTANT.
        // VS2's setupWithShipMounted projects it through renderTransform.shipToWorld
        // (render-frame-interpolated) → smooth camera, no 20 Hz lag, no rubber band.
        val pos = ship.transform.positionInWorld
        val comInShipyard = ship.worldToShip
            .transformPosition(Vector3d(pos.x(), pos.y(), pos.z()), Vector3d())

        // Delegate to VS2's own camera setup — uses renderTransform internally:
        //  • render-frame-interpolated position (no more 20 Hz lag)
        //  • camera rotation accounts for ship orientation (angular damping keeps pod upright)
        //  • 3rd-person zoom handled correctly by VS2
        (this as? IVSCamera)?.setupWithShipMounted(
            level, entity, detached, invertedView, partialTick, ship, comInShipyard
        )
    }
}
