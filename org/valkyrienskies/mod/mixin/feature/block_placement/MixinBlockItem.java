package org.valkyrienskies.mod.mixin.feature.block_placement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Intersectiond;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.PlayerUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(BlockItem.class)
public abstract class MixinBlockItem {

    @WrapOperation(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;getPlacementState(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        method = "place"
    )
    private BlockState transformPlayerWhenPlacing(
        final BlockItem _instance, final BlockPlaceContext _ctx,
        final Operation<BlockState> original, final BlockPlaceContext ctx
    ) {
        if (ctx == null || ctx.m_43723_() == null) {
            return original.call(this, ctx);
        }
        return PlayerUtil.transformPlayerTemporarily(
            ctx.m_43723_(),
            ctx.m_43725_(),
            ctx.m_8083_(),
            () -> original.call(this, ctx)
        );
    }

    @WrapOperation(
        method = "canPlace",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isUnobstructed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Z"
        )
    )
    private boolean checkObstructionByPlayer(
        final Level level, final BlockState blockState, final BlockPos blockPos, final CollisionContext ctx,
        Operation<Boolean> original, @Local(argsOnly = true) BlockPlaceContext blockPlaceContext
    ) {
        boolean result = original.call(level, blockState, blockPos, ctx);
        if (result) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(level, blockPos);
            if (ship != null) {
                VoxelShape voxelShape = blockState.m_60742_(level, blockPos, ctx);
                Player player = blockPlaceContext.m_43723_();

                if (player != null
                    && !player.m_213877_()
                    && player.f_19850_
                    && !voxelShape.m_83281_()
                    && vs_testObAab(
                        VectorConversionsMCKt.toJOML(voxelShape.m_83215_().m_82338_(blockPos)),
                        ship.getShipToWorld(),
                        VectorConversionsMCKt.toJOML(player.m_20191_().m_82406_(0.1))
                    )
                ) return false;
            }
        }
        return result;
    }

    @Unique
    private static boolean vs_testObAab(AABBd aabb, Matrix4dc transform, AABBd other) {
        // Axes of OBB. Not scaled on purpose.
        Vector3d tX = new Vector3d(transform.m00(), transform.m01(), transform.m02()).normalize();
        Vector3d tY = new Vector3d(transform.m10(), transform.m11(), transform.m12()).normalize();
        Vector3d tZ = new Vector3d(transform.m20(), transform.m21(), transform.m22()).normalize();
        Vector3d transformedCenter = transform.transformPosition(aabb.center(new Vector3d()));
        // Axes of an AABB are world axes by definition.
        final Vector3d wX = new Vector3d(1, 0, 0);
        final Vector3d wY = new Vector3d(0, 1, 0);
        final Vector3d wZ = new Vector3d(0, 0, 1);

        return Intersectiond.testObOb(
            transformedCenter,
            tX, tY, tZ, aabb.getSize(new Vector3d()).mul(0.5),
            other.center(new Vector3d()),
            wX, wY, wZ, other.getSize(new Vector3d()).mul(0.5)
        );
    }

}
