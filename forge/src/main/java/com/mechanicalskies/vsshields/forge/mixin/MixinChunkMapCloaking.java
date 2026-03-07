package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.shield.CloakManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.mutable.MutableObject;
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
 * Server-side: prevents chunk data from being sent to non-crew players
 * for cloaked ships. This is the primary anti-cheat layer — if chunk data
 * never leaves the server, clients cannot render the ship.
 */
@Mixin(ChunkMap.class)
public class MixinChunkMapCloaking {

    @Shadow @Final ServerLevel level;

    @Inject(method = "playerLoadedChunk", at = @At("HEAD"), cancellable = true)
    private void vs_shields$blockCloakedChunk(
            ServerPlayer player, MutableObject<?> packetCache,
            LevelChunk chunk, CallbackInfo ci) {

        BlockPos chunkOrigin = chunk.getPos().getWorldPosition();
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, chunkOrigin);
        if (ship == null) return;
        if (!CloakManager.getInstance().isShipCloaked(ship.getId())) return;

        // Allow crew aboard to see their own ship
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
