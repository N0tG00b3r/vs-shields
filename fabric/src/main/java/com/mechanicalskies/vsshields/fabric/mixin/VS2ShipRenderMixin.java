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
 * Intercepts VS2's per-ship render dispatch lambda.
 * VS2's mixin code injects into LevelRenderer — the lambda lives there at runtime.
 * Targeting MixinLevelRendererVanilla directly crashes with "target is a mixin".
 * require=0 so the inject is silent if the lambda name changes across VS2 versions.
 */
@Mixin(LevelRenderer.class)
public abstract class VS2ShipRenderMixin {

    @Inject(
        method = "lambda$redirectRenderChunkLayer$3",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void vs_shields$skipCloakedShip(
        PoseStack poseStack,
        double camX, double camY, double camZ,
        LevelRenderer renderer,
        RenderType renderType,
        Matrix4f projMatrix,
        ClientShip ship,
        CallbackInfo ci
    ) {
        if (!ClientCloakManager.getInstance().isCloaked(ship.getId())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ci.cancel();
            return;
        }

        ClientShip playerShip = VSClientGameUtils.getClientShip(
            mc.player.getX(), mc.player.getY(), mc.player.getZ()
        );
        if (playerShip == null || playerShip.getId() != ship.getId()) {
            ci.cancel();
        }
    }
}
