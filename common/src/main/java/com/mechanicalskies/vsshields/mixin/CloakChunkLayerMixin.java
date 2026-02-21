package com.mechanicalskies.vsshields.mixin;

import com.mechanicalskies.vsshields.client.CloakRenderState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts vanilla renderChunkLayer calls.
 * When VS2 renders a ship, it calls renderChunkLayer for each RenderType.
 * If CloakRenderState says the current ship should be skipped (cloaked),
 * we cancel the render call entirely.
 *
 * This works in conjunction with VSGameEvents.renderShip/postRenderShip
 * listeners registered in VSShieldsModClient.
 */
@Mixin(LevelRenderer.class)
public abstract class CloakChunkLayerMixin {

    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void vs_shields$skipCloakedChunkLayer(
            RenderType renderType,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            double camX, double camY, double camZ,
            org.joml.Matrix4f projectionMatrix,
            CallbackInfo ci) {
        if (CloakRenderState.shouldSkipCurrentRender()) {
            ci.cancel();
        }
    }
}
