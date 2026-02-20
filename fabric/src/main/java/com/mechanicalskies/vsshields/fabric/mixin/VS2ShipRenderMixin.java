package com.mechanicalskies.vsshields.fabric.mixin;

import com.mechanicalskies.vsshields.client.ClientCloakManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.common.VSClientGameUtils;

/**
 * Mixin² — targets VS2's MixinLevelRendererVanilla merged into LevelRenderer.
 * Tries multiple lambda ordinals for cross-version compatibility.
 * require=0 so only the matching lambda activates — no crash on mismatch.
 */
@Mixin(LevelRenderer.class)
public abstract class VS2ShipRenderMixin {

    @Inject(method = "lambda$redirectRenderChunkLayer$3", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void vs_shields$skipCloakedShipLambda3(
            PoseStack poseStack,
            double camX, double camY, double camZ,
            LevelRenderer renderer,
            RenderType renderType,
            Matrix4f projMatrix,
            ClientShip ship,
            CallbackInfo ci) {
        vs_shields$handleCloakedShip(ship, ci);
    }

    @Inject(method = "lambda$redirectRenderChunkLayer$2", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void vs_shields$skipCloakedShipLambda2(
            PoseStack poseStack,
            double camX, double camY, double camZ,
            LevelRenderer renderer,
            RenderType renderType,
            Matrix4f projMatrix,
            ClientShip ship,
            CallbackInfo ci) {
        vs_shields$handleCloakedShip(ship, ci);
    }

    @Inject(method = "lambda$redirectRenderChunkLayer$4", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void vs_shields$skipCloakedShipLambda4(
            PoseStack poseStack,
            double camX, double camY, double camZ,
            LevelRenderer renderer,
            RenderType renderType,
            Matrix4f projMatrix,
            ClientShip ship,
            CallbackInfo ci) {
        vs_shields$handleCloakedShip(ship, ci);
    }

    private void vs_shields$handleCloakedShip(ClientShip ship, CallbackInfo ci) {
        if (!ClientCloakManager.getInstance().isCloaked(ship.getId()))
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ci.cancel();
            return;
        }

        // Don't cloak if the local player is aboard this ship
        ClientShip playerShip = VSClientGameUtils.getClientShip(
                mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (playerShip == null || playerShip.getId() != ship.getId()) {
            ci.cancel();
        }
    }
}
