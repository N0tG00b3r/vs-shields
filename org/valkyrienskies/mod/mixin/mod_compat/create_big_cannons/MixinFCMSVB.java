package org.valkyrienskies.mod.mixin.mod_compat.create_big_cannons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity.FixedCannonMountScrollValueBehaviour;

@Mixin(FixedCannonMountScrollValueBehaviour.class)
public abstract class MixinFCMSVB extends BlockEntityBehaviour {
    public MixinFCMSVB(SmartBlockEntity be) {
        super(be);
    }

    @WrapOperation(
        method = "testHit",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    public Vec3 redirectSubtract(Vec3 instance, Vec3 vec, Operation<Vec3> original) {
        Level level = this.getWorld();

        Vec3 pos1 = instance;
        Vec3 pos2 = vec;

        if (level != null) {
            Ship ship1 = VSGameUtilsKt.getShipManagingPos(level, pos1.f_82479_, pos1.f_82480_, pos1.f_82481_);
            Ship ship2 = VSGameUtilsKt.getShipManagingPos(level, pos2.f_82479_, pos2.f_82480_, pos2.f_82481_);
            if (ship1 != null && ship2 == null) {
                pos2 = VectorConversionsMCKt.toMinecraft(
                    ship1.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(pos2))
                );
            } else if (ship1 == null && ship2 != null) {
                pos1 = VectorConversionsMCKt.toMinecraft(
                    ship2.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(pos1))
                );
            }
        }
        return pos1.m_82546_(pos2);
    }
}
