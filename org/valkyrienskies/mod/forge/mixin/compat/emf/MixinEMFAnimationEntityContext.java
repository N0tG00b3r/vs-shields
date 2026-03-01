package org.valkyrienskies.mod.forge.mixin.compat.emf;

import D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import traben.entity_model_features.models.animation.EMFAnimationEntityContext;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;

@Mixin(EMFAnimationEntityContext.class)
public class MixinEMFAnimationEntityContext {

    @Shadow
    @Nullable
    private static EMFEntityRenderState emfState;

    @Inject(
        at = @At("HEAD"),
        method = "distanceOfEntityFrom",
        cancellable = true,
        remap = false
    )
    private static void distanceOfEntityFrom(final BlockPos pos, final CallbackInfoReturnable<Integer> cir) {

        if (emfState != null) {
            final var level = Minecraft.m_91087_().f_91073_;
            final var posW = VSGameUtilsKt.toWorldCoordinates(level, Vec3.m_82512_(pos));
            final var entityW = VSGameUtilsKt.toWorldCoordinates(level, Vec3.m_82512_(emfState.blockPos()));
            final var dist = posW.m_82554_(entityW);
            cir.setReturnValue((int) dist);
        }
    }
}
