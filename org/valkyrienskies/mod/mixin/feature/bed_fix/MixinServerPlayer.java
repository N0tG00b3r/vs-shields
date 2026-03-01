package org.valkyrienskies.mod.mixin.feature.bed_fix;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer extends Entity {

    public MixinServerPlayer(final EntityType<?> entityType, final Level level) {
        super(entityType, level);
    }

    @Inject(
        at = @At("TAIL"),
        method = "isReachableBedBlock",
        cancellable = true
    )
    private void isReachableBedBlock(final BlockPos blockPos, final CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            final Vec3 vec3 = Vec3.m_82539_(blockPos);

            final double origX = vec3.f_82479_;
            final double origY = vec3.f_82480_;
            final double origZ = vec3.f_82481_;

            VSGameUtilsKt.transformToNearbyShipsAndWorld(this.m_9236_(), origX, origY, origZ, 1, (x, y, z) -> {
                cir.setReturnValue(Math.abs(this.m_20185_() - x) <= 3.0 && Math.abs(this.m_20186_() - y) <= 2.0
                    && Math.abs(this.m_20189_() - z) <= 3.0);
            });
        }
    }

}
