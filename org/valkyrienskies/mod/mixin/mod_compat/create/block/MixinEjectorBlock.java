package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.simibubi.create.content.logistics.depot.EjectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(EjectorBlock.class)
public abstract class MixinEjectorBlock {
    @Redirect(method = "updateEntityAfterFallOn", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
    ))
    private BlockPos redirectBlockPosition(Entity instance) {
        return instance.m_20097_();
    }

    @Redirect(method = "updateEntityAfterFallOn", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"
    ))
    private Vec3 redirectEntityPosition(Entity instance) {
        Vec3 result = instance.m_20182_();
        if (VSGameUtilsKt.getShipManagingPos(instance.m_9236_(), instance.m_20182_()) == null) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(instance.m_9236_(), instance.m_20097_());
            if (ship != null) {
                Vector3d tempVec = VectorConversionsMCKt.toJOML(result);
                ship.getWorldToShip().transformPosition(tempVec, tempVec);
                result = VectorConversionsMCKt.toMinecraft(tempVec);
            }
        }
        return result;
    }

    @Redirect(method = "updateEntityAfterFallOn", at = @At(
            value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"
    ))
    private void redirectSetPos(Entity instance, double x, double y, double z) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(instance.m_9236_(), instance.m_20097_());
        if (ship != null) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformPosition(x, y, z, tempVec);
            instance.m_6034_(tempVec.x, tempVec.y, tempVec.z);
        } else {
            instance.m_6034_(x, y, z);
        }
    }
}
