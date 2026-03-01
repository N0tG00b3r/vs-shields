package org.valkyrienskies.mod.mixin.mod_compat.create.blockentity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorInteractionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ChainConveyorInteractionHandler.class)
public abstract class MixinChainConveyorInteractionHandler {
    @Shadow
    public static BlockPos selectedLift;

    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 wrapRelativePos(final Vec3 instance, final Vec3 liftVec, final Operation<Vec3> original) {
        ClientShip ship = VSClientGameUtils.getClientShip(liftVec.f_82479_, liftVec.f_82480_, liftVec.f_82481_);
        if (ship != null) {
            Vector3d shipInstance = VectorConversionsMCKt.toJOML(instance);
            shipInstance = ship.getTransform().getWorldToShip().transformPosition(shipInstance);
            return original.call(VectorConversionsMCKt.toMinecraft(shipInstance), liftVec);
        } else return original.call(instance, liftVec);
    }

    @WrapOperation(
        method = "clientTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D")
    )
    private static double wrapDistanceSqr(final Vec3 instance, final Vec3 from, Operation<Double> original) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(Minecraft.m_91087_().f_91073_, instance, from, original);
    }

    @WrapOperation(
        method = "drawCustomBlockSelection",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V")
    )
    private static void wrapOutlineTranslation(PoseStack instance, double x, double y, double z, Operation<Void> original, PoseStack ms, MultiBufferSource buffer, Vec3 camera){
        ClientShip ship = VSClientGameUtils.getClientShip(selectedLift.m_123341_(), selectedLift.m_123342_(), selectedLift.m_123343_());
        if(ship != null) {
            Vector3d liftShipPos = ship.getRenderTransform().getShipToWorld().transformPosition(selectedLift.m_123341_(), selectedLift.m_123342_(), selectedLift.m_123343_(), new Vector3d());
            original.call(instance, liftShipPos.x - camera.f_82479_, liftShipPos.y - camera.f_82480_, liftShipPos.z - camera.f_82481_);
            instance.m_85850_().m_252922_().rotate(ship.getRenderTransform().getShipToWorldRotation().get(new Quaternionf()));
        } else original.call(instance, x, y, z);
    }
}
