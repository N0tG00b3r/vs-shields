package org.valkyrienskies.mod.mixin.feature.shipyard_leashing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(PathfinderMob.class)
public abstract class MixinPathfinderMob {
    // Used by Entity.restrictTo();
    @Redirect(
            method = "tickLeash",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"
            )
    )
    BlockPos worldAwareRestrictPos(Entity entity) {
        BlockPos entityBlockPos = entity.m_20183_();
        Ship shipThis = VSGameUtilsKt.getShipManaging(Entity.class.cast(this));
        Ship shipOther = VSGameUtilsKt.getShipManaging(entity);
        if (shipThis != shipOther) {
            if (shipOther != null) {
                // Sadly the restrictTo uses integer BlockPos, not a double value. Rounding to the closest block:
                entityBlockPos = BlockPos.m_274446_(VSGameUtilsKt.toWorldCoordinates(shipOther, entity.m_20182_()).m_82520_(0.5, 0.5, 0.5));
            }
        }
        return entityBlockPos;
    }

    @Redirect(
            method = "tickLeash",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/PathfinderMob;distanceTo(Lnet/minecraft/world/entity/Entity;)F"
            )
    )
    float worldAwareDistanceTo(PathfinderMob instance, Entity entity) {
        return (float)
                VSGameUtilsKt.toWorldCoordinates(instance.m_9236_(), instance.m_20182_())
                .m_82554_(
                VSGameUtilsKt.toWorldCoordinates(entity.m_9236_(), entity.m_20182_()));
    }

    // BAD CODE
    @Redirect(
            method = "tickLeash",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getX()D",
                    ordinal = -1
            )
    )
    double worldAwareLeashX(Entity instance) {
        return VSGameUtilsKt.toWorldCoordinates(instance.m_9236_(), instance.m_20182_()).f_82479_;
    }

    @Redirect(
            method = "tickLeash",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getY()D",
                    ordinal = -1
            )
    )
    double worldAwareLeashY(Entity instance) {
        return VSGameUtilsKt.toWorldCoordinates(instance.m_9236_(), instance.m_20182_()).f_82480_;
    }

    @Redirect(
            method = "tickLeash",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getZ()D",
                    ordinal = -1
            )
    )
    double worldAwareLeashZ(Entity instance) {
        return VSGameUtilsKt.toWorldCoordinates(instance.m_9236_(), instance.m_20182_()).f_82481_;
    }
}
