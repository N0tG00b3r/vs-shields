package org.valkyrienskies.mod.mixin.feature.save_mob_location_on_ship;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.entity.ShipyardPosSavable;

@Mixin(Mob.class)
public class MixinMob implements ShipyardPosSavable {

    @Unique
    public Vector3d valkyrienskies$unloadedShipyardPos = null;

    @Override
    public Vector3d valkyrienskies$getUnloadedShipyardPos() {
        return valkyrienskies$unloadedShipyardPos;
    }

    @Override
    public void valkyrienskies$setUnloadedShipyardPos(Vector3d vector3d) {
        this.valkyrienskies$unloadedShipyardPos = vector3d;
    }


    /**
     * Save mob's shipyard position to nbt, or clear it if null
     *
     * @author G_Mungus
     */
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    public void addAdditionalSaveDataMixin(CompoundTag nbt, CallbackInfo info) {
        Vector3d position = this.valkyrienskies$getUnloadedShipyardPos();
        if (position != null && VSGameConfig.SERVER.getSaveMobsPositionOnShip()) {
            nbt.m_128347_("valkyrienskies$unloadedX",position.x);
            nbt.m_128347_("valkyrienskies$unloadedY",position.y);
            nbt.m_128347_("valkyrienskies$unloadedZ", position.z);
        } else {
            nbt.m_128473_("valkyrienskies$unloadedX");
            nbt.m_128473_("valkyrienskies$unloadedY");
            nbt.m_128473_("valkyrienskies$unloadedZ");
        }
    }


    /**
     * Read mob's shipyard position from nbt
     *
     * @author G_Mungus
     */
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    public void readAdditionalSaveData(CompoundTag nbt, CallbackInfo info) {
        if (nbt.m_128441_("valkyrienskies$unloadedX") && nbt.m_128441_("valkyrienskies$unloadedY") && nbt.m_128441_("valkyrienskies$unloadedZ")) {
            double[] xyz = {nbt.m_128459_("valkyrienskies$unloadedX"), nbt.m_128459_("valkyrienskies$unloadedY"), nbt.m_128459_("valkyrienskies$unloadedZ")};
            this.valkyrienskies$setUnloadedShipyardPos(new Vector3d(xyz));
        }
    }



}

