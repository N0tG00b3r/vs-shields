package org.valkyrienskies.mod.mixin.feature.ai.goal.bees;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(Bee.BeeGrowCropGoal.class)
public class MixinGrowCropGoal {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState onTick(Level instance, BlockPos blockPos, Operation<BlockState> original) {
        List<Vector3d> possibleCandidates = VSGameUtilsKt.transformToNearbyShipsAndWorld(instance, blockPos.m_123341_(), blockPos.m_123342_(), blockPos.m_123343_(), 1.5);
        for (Vector3d candidate : possibleCandidates) {
            BlockState blockState = instance.m_8055_(BlockPos.m_274561_(candidate.x, candidate.y, candidate.z));
            if (blockState.m_204336_(BlockTags.f_13074_)) {
                return blockState;
            }
        }
        return original.call(instance, blockPos);
    }
}
