package org.valkyrienskies.mod.mixin.mod_compat.create.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;

@Mixin(ValueBox.class)
public class MixinValueBox {

    @Shadow
    protected BlockPos pos;

    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V",
            ordinal = 0
        )
    )
    public void wrapOpTranslate(final PoseStack instance, final double x, final double y, final double z, final Operation<Void> operation) {
        final ClientShip ship = VSClientGameUtils.getClientShip(x, y, z);
        if (ship != null) {
            final var camera = Minecraft.m_91087_().f_91063_.m_109153_().m_90583_();
            VSClientGameUtils.transformRenderWithShip(ship.getRenderTransform(), instance, pos.m_123341_(),pos.m_123342_(),pos.m_123343_(), camera.f_82479_, camera.f_82480_, camera.f_82481_ );
        } else {
            operation.call(instance, x, y, z);
        }
    }
}
