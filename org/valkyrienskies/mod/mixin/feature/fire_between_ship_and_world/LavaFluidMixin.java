package org.valkyrienskies.mod.mixin.feature.fire_between_ship_and_world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.LavaFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(LavaFluid.class)
public abstract class LavaFluidMixin extends FlowingFluid {
    @Unique
    private boolean isModifyingFireTick = false;

    @Inject(method = "randomTick", at = @At("TAIL"))
    public void fireTickMixin(final Level level, final BlockPos pos, final FluidState state, final RandomSource random,
        final CallbackInfo ci) {
        if (isModifyingFireTick) {
            return;
        }

        isModifyingFireTick = true;

        final double origX = pos.m_123341_();
        final double origY = pos.m_123342_();
        final double origZ = pos.m_123343_();

        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX, origY, origZ, 3, (x, y, z) -> {
            m_213812_(level, BlockPos.m_274561_(x, y, z), state, random);
        });

        isModifyingFireTick = false;

    }

}
