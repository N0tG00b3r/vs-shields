package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.simibubi.create.content.logistics.depot.SharedDepotBlockMethods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(SharedDepotBlockMethods.class)
public abstract class MixinSharedDepotBlockMethods {
    @Redirect(method = "onLanded", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
    ))
    private static BlockPos redirectBlockPosition(Entity instance) {
        BlockPos result = instance.m_20183_();
        Ship ship = VSGameUtilsKt.getLoadedShipManagingPos(instance.m_9236_(), instance.m_20097_());
        if (ship != null) {
            Vector3d tempVec = new Vector3d(instance.m_20182_().f_82479_, instance.m_20182_().f_82480_, instance.m_20182_().f_82481_);
            ship.getWorldToShip().transformPosition(tempVec, tempVec);
            result = BlockPos.m_274561_(Math.floor(tempVec.x), Math.floor(tempVec.y), Math.floor(tempVec.z));
        }
        return result;
    }
}
