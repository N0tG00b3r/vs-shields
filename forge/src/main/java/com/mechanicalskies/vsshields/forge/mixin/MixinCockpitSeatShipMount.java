package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.ShipMountedToDataProvider;

/**
 * Makes CockpitSeatEntity implement ShipMountedToDataProvider so VS2's MixinGameRenderer
 * automatically handles camera and player rendering via setupWithShipMounted.
 *
 * <p>The seat stays in WORLD space, so controls (camera rotation, Space, Shift) work normally.
 * We return a <em>constant</em> cockpitShipyardPos → VS2 computes
 * {@code renderTransform.shipToWorld × constant} = smooth camera at any speed, no 20 Hz desync.
 *
 * <p>Called by VS2's {@code getShipMountedToData(player)} when the player's vehicle (this seat)
 * implements {@code ShipMountedToDataProvider}. Only active client-side (client mixin array).
 */
@Mixin(value = CockpitSeatEntity.class, remap = false)
public abstract class MixinCockpitSeatShipMount implements ShipMountedToDataProvider {

    @Shadow public abstract long getPodShipId();
    @Shadow public abstract Vector3d getCockpitShipyardPos();

    @SuppressWarnings("unchecked")
    @Override
    public ShipMountedToData provideShipMountedToData(Entity passenger, Float partialTicks) {
        long shipId = getPodShipId();
        if (shipId == Long.MIN_VALUE) return null;

        Vector3d pos = getCockpitShipyardPos();
        if (pos == null) return null;  // not yet synced from server (first ~1 tick)

        // Only called client-side from MixinGameRenderer / MixinCamera
        VsiClientShipWorld shipWorld =
            IShipObjectWorldClientProvider.class.cast(Minecraft.getInstance()).getShipObjectWorld();
        if (shipWorld == null) return null;

        ClientShip ship = (ClientShip) shipWorld.getLoadedShips().getById(shipId);
        if (ship == null) return null;

        // pos.y = seat entity's shipyard Y (= cockpit block Y+0.3 in world → shipyard space).
        // Player feet = seatY + ridingOffset(−0.2) = blockY+0.1 in world → subtract 0.2 in shipyard.
        // setupWithShipMounted adds eyeHeight(0.6) separately → camera at blockY+0.7.
        return new ShipMountedToData(ship, new Vector3d(pos.x, pos.y - 0.2, pos.z));
    }
}
