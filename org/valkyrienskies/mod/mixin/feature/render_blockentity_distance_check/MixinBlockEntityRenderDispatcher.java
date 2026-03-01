package org.valkyrienskies.mod.mixin.feature.render_blockentity_distance_check;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * This mixin fixes {@link BlockEntity}s belonging to ships not rendering.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class MixinBlockEntityRenderDispatcher {

    @Shadow
    public Level level;

    @Shadow
    public Camera camera;

    /**
     * This mixin fixes the culling of {@link BlockEntity}s that belong to a ship.
     */
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"
        )
    )
    private <E extends BlockEntity> boolean isTileEntityInRenderRange(final BlockEntityRenderer<E> instance,
        final E blockEntity, final Vec3 cameraPos, final Operation<Boolean> operation) {

        if (operation.call(instance, blockEntity, cameraPos)) {
            return true;
        }

        // If by default was false, then check if this BlockEntity belongs to a ship
        final BlockPos bePos = blockEntity.m_58899_();
        final Ship nullableShip = VSGameUtilsKt.getLoadedShipManagingPos(level, bePos);
        if (nullableShip instanceof ClientShip ship) {
            final Matrix4dc m = ship.getRenderTransform().getShipToWorld();

            return new Vec3(
                m.m00() * bePos.m_123341_() + m.m10() * bePos.m_123342_() + m.m20() * bePos.m_123343_() + m.m30(),
                m.m01() * bePos.m_123341_() + m.m11() * bePos.m_123342_() + m.m21() * bePos.m_123343_() + m.m31(),
                m.m02() * bePos.m_123341_() + m.m12() * bePos.m_123342_() + m.m22() * bePos.m_123343_() + m.m32()
            ).m_82509_(this.camera.m_90583_(), instance.m_142163_());
        }

        return false;
    }
}
