package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.shield.CloakManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Server-side: prevents entity tracking for non-crew players on cloaked ships.
 * Entities placed on ship blocks (item frames, armor stands, etc.) have shipyard
 * coordinates, so getShipManagingPos finds their ship.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class MixinTrackedEntityCloak {

    @Shadow @Final Entity entity;

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void vs_shields$blockCloakedEntity(ServerPlayer player, CallbackInfo ci) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(entity.level(), entity.blockPosition());
        if (ship == null) return;
        if (!CloakManager.getInstance().isShipCloaked(ship.getId())) return;

        // Allow crew aboard to track entities on their own ship
        try {
            AABBdc aabb = ship.getWorldAABB();
            double t = 2.0;
            if (player.getX() >= aabb.minX() - t && player.getX() <= aabb.maxX() + t
                    && player.getY() >= aabb.minY() - t && player.getY() <= aabb.maxY() + t
                    && player.getZ() >= aabb.minZ() - t && player.getZ() <= aabb.maxZ() + t) {
                return;
            }
        } catch (Exception ignored) {
            return; // Fail open if AABB unavailable
        }

        ci.cancel();
    }
}
