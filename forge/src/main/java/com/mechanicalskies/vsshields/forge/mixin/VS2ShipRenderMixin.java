package com.mechanicalskies.vsshields.forge.mixin;

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
 * Mixin² — targets VS2's MixinLevelRendererVanilla which is merged into
 * LevelRenderer
 * at runtime. The redirectRenderChunkLayer method is injected by
 * VS2's @WrapOperation
 * and dispatches per-ship rendering. We inject at HEAD to conditionally skip
 * cloaked ships.
 *
 * We target LevelRenderer.class (where the VS2 code lands at runtime) and use
 * the
 * method name "redirectRenderChunkLayer" which is the real, stable method name
 * (unlike the compiler-generated lambda names which change between versions).
 *
 * require=0 so if VS2 changes the method name in future versions, this fails
 * silently
 * rather than crashing.
 */
@Mixin(LevelRenderer.class)
public abstract class VS2ShipRenderMixin {

    /**
     * Alternative: target the per-ship lambda that VS2 generates.
     * We try multiple possible lambda names for compatibility across VS2 builds.
     * The lambda receives ClientShip as a parameter, allowing direct ship
     * identification.
     */
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
