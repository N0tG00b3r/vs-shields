package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.client.ClientCloakManager;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectList;
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
 * Mixin² on LevelRenderer — targets the per-ship render lambda injected by
 * VS2's
 * MixinLevelRendererVanilla.redirectRenderChunkLayer @WrapOperation.
 *
 * Exact signature from VS2 2.4.10 bytecode:
 * lambda$redirectRenderChunkLayer$3(PoseStack, d, d, d, LevelRenderer,
 * RenderType, Matrix4f, ClientShip, ObjectList)V
 *
 * require=0 so the inject fails silently if VS2 changes the lambda ordinal.
 */
@Mixin(LevelRenderer.class)
public abstract class VS2ShipRenderMixin {

    @Inject(method = "lambda$redirectRenderChunkLayer$3", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void vs_shields$skipCloakedShip(
            PoseStack poseStack,
            double camX, double camY, double camZ,
            LevelRenderer renderer,
            RenderType renderType,
            Matrix4f projMatrix,
            ClientShip ship,
            ObjectList<?> chunks,
            CallbackInfo ci) {
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
