package org.valkyrienskies.mod.mixin.feature.ai.goal;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Mixin(GoalUtils.class)
public class MixinGoalUtil {

    @WrapOperation(
        method = "isSolid",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private static BlockState vs$getBlockStateIsSolid(
        Level instance, BlockPos blockPos, Operation<BlockState> original) {
        BlockState originalState = original.call(instance, blockPos);
        if (originalState.m_280296_()) return originalState;
        Iterable<Vector3d> candidates = ValkyrienSkies.positionToNearbyShips(instance, blockPos.m_123341_(), blockPos.m_123342_(), blockPos.m_123343_());
        for (Vector3d candidate : candidates) {
            BlockPos candidatePos = BlockPos.m_274446_(ValkyrienSkies.toMinecraft(candidate));
            BlockState candidateState = instance.m_8055_(candidatePos);
            if (candidateState.m_280296_()) {
                return candidateState;
            }
        }
        return originalState;
    }

    @WrapOperation(
        method = "isWater",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
        )
    )
    private static FluidState vs$getFluidStateIsWater(
        Level instance, BlockPos blockPos, Operation<FluidState> original) {
        FluidState originalState = original.call(instance, blockPos);
        if (originalState.m_205070_(FluidTags.f_13131_)) return originalState;
        Iterable<Vector3d> candidates = ValkyrienSkies.positionToNearbyShips(instance, blockPos.m_123341_(), blockPos.m_123342_(), blockPos.m_123343_());
        for (Vector3d candidate : candidates) {
            BlockPos candidatePos = BlockPos.m_274446_(ValkyrienSkies.toMinecraft(candidate));
            FluidState candidateState = instance.m_6425_(candidatePos);
            if (candidateState.m_205070_(FluidTags.f_13131_)) {
                return candidateState;
            }
        }
        return originalState;
    }
}
