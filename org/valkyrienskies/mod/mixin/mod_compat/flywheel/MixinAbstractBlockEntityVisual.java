package org.valkyrienskies.mod.mixin.mod_compat.flywheel;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(AbstractBlockEntityVisual.class)
public class MixinAbstractBlockEntityVisual<T extends BlockEntity> {
    @Shadow
    @Final
    protected T blockEntity;

    @WrapOperation(
        method = "isVisible",
        at = @At(value = "INVOKE", target = "Lorg/joml/FrustumIntersection;testSphere(FFFF)Z"),
        remap = false
    )
    private boolean testRedirected(FrustumIntersection instance, float x, float y, float z, float r,
        Operation<Boolean> original) {
        Vec3 worldPos = VSGameUtilsKt.toWorldCoordinates(blockEntity.m_58904_(), blockEntity.m_58899_().m_252807_());
        return original.call(instance, (float)worldPos.f_82479_, (float)worldPos.f_82480_, (float)worldPos.f_82481_, r);
    }

    @Redirect(
        method = "doDistanceLimitThisFrame",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;distToCenterSqr(Lnet/minecraft/core/Position;)D")
    )
    private double distInclShips(BlockPos blockPos, Position position, @Local(argsOnly = true) DynamicVisual.Context context){
        return VSGameUtilsKt.toWorldCoordinates(context.camera().m_90592_().m_9236_(), blockPos.m_252807_()).m_82531_(position.m_7096_(), position.m_7098_(), position.m_7094_());
    }
}
