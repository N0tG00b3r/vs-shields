package com.mechanicalskies.vsshields.forge.mixin;

import com.happysg.radar.block.radar.behavior.RadarScanningBlockBehavior;
import com.mechanicalskies.vsshields.anomaly.AnomalyManager;
import com.mechanicalskies.vsshields.shield.CloakManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.Set;

/**
 * Hides cloaked ships and anomaly islands from Create: Radars mod.
 * Injects after scanForVSTracks() fills the scannedShips set,
 * removing any ships that are currently cloaked or are anomaly islands.
 *
 * defaultRequire=0 in mixin JSON means this gracefully no-ops when the mod is absent.
 */
@Mixin(value = RadarScanningBlockBehavior.class, remap = false)
public class MixinRadarCloakFilter {

    @Shadow private Set<Ship> scannedShips;

    @Inject(method = "scanForVSTracks", at = @At("RETURN"))
    private void vsshields$removeCloakedShips(CallbackInfo ci) {
        scannedShips.removeIf(ship ->
                CloakManager.getInstance().isShipCloaked(ship.getId()) ||
                AnomalyManager.getInstance().isAnomalyShip(ship.getId()));
    }
}
