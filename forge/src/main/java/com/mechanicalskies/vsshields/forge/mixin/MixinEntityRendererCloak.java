package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Client-side: suppresses rendering of non-living entities on cloaked ships.
 * LivingEntity suppression is handled by CloakEntityRenderHandler (Forge event).
 */
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRendererCloak {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void vs_shields$skipCloaked(
            E entity, double x, double y, double z,
            float yRot, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            CallbackInfo ci) {

        if (entity instanceof LivingEntity) return;

        CloakedShipsRegistry registry = CloakedShipsRegistry.getInstance();
        if (!registry.hasAnyCloakedShips()) return;

        Ship ship = VSGameUtilsKt.getShipManagingPos(entity.level(), entity.blockPosition());
        if (ship == null) return;
        if (!registry.isCloaked(ship.getId())) return;

        Player player = Minecraft.getInstance().player;
        if (player != null && isPlayerAboard(ship, player)) return;

        ci.cancel();
    }

    private static boolean isPlayerAboard(Ship ship, Player player) {
        try {
            AABBdc aabb = ship.getWorldAABB();
            double t = 2.0;
            return player.getX() >= aabb.minX() - t && player.getX() <= aabb.maxX() + t
                    && player.getY() >= aabb.minY() - t && player.getY() <= aabb.maxY() + t
                    && player.getZ() >= aabb.minZ() - t && player.getZ() <= aabb.maxZ() + t;
        } catch (Exception e) {
            return false;
        }
    }
}
