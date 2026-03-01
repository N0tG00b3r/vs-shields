package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.api.ValkyrienSkies;

@Mixin(MoveControl.class)
public class MixinMoveControl {

    @Shadow
    @Final
    protected Mob mob;

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;")
    )
    private VoxelShape vs$insertShipCollisions(BlockState instance, BlockGetter blockGetter, BlockPos blockPos,
        Operation<VoxelShape> original) {
        VoxelShape originalShape = original.call(instance, this.mob.m_9236_(), blockPos);
        if (originalShape.m_83281_()) {
            Iterable<Vector3d> alternates = ValkyrienSkies.positionToNearbyShips(this.mob.m_9236_(), blockPos.m_123341_(), blockPos.m_123342_(), blockPos.m_123343_());
            for (Vector3d alternate : alternates) {
                BlockPos alternatePos = BlockPos.m_274446_(ValkyrienSkies.toMinecraft(alternate));
                BlockState alternateState = this.mob.m_9236_().m_8055_(alternatePos);
                if (!alternateState.m_60795_()) {
                    VoxelShape alternateShape = alternateState.m_60812_(this.mob.m_9236_(), alternatePos);
                    if (!alternateShape.m_83281_()) {
                        return alternateShape;
                    }
                }
            }
        }
        return originalShape;
    }
}

