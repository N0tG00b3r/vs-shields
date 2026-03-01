package org.valkyrienskies.mod.mixin.mod_compat.hexcasting;

import at.petrak.hexcasting.api.player.Sentinel;
import at.petrak.hexcasting.client.render.HexAdditionalRenderers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(HexAdditionalRenderers.class)
public class MixinHexAdditionalRenderers {
    @WrapOperation(method = "renderSentinel", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"))
    private static void valkyrienskies$renderOnShip(PoseStack instance, double d, double e, double f, Operation<Void> original, @Local(argsOnly = true) Sentinel sentinel, @Local Vec3 playerPos) {
        ClientShip ship = VSClientGameUtils.getClientShip(sentinel.position().f_82479_, sentinel.position().f_82480_, sentinel.position().f_82481_);
        if (ship != null) {
            Vec3 distance = VectorConversionsMCKt.toMinecraft(ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOML(sentinel.position()))).m_82546_(playerPos);
            original.call(instance, distance.f_82479_, distance.f_82480_, distance.f_82481_);
        } else {
            original.call(instance, d, e, f);
        }
    }

    @WrapOperation(method = "renderSentinel", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private static void valkyrienskies$scaleWithShip(PoseStack instance, float f, float g, float h, Operation<Void> original, @Local(argsOnly = true) Sentinel sentinel) {
        ClientShip ship = VSClientGameUtils.getClientShip(sentinel.position().f_82479_, sentinel.position().f_82480_, sentinel.position().f_82481_);
        if (ship != null) {
            Vector3fc scale = ship.getRenderTransform().getShipToWorldScaling().get(new Vector3f());
            original.call(instance, scale.x(), scale.y(), scale.z());
        }
        original.call(instance, f, g, h);
    }
}
