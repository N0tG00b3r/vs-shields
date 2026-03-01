package org.valkyrienskies.mod.mixin.feature.tick_ship_chunks;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * These methods fix random ticking on ship chunks
 */
@Mixin(ChunkMap.class)
public abstract class MixinChunkMap {

    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "euclideanDistanceSquared", at = @At("HEAD"), cancellable = true)
    private static void preDistanceToSqr(final ChunkPos chunkPos, final Entity entity,
        final CallbackInfoReturnable<Double> cir) {
        final double d = chunkPos.f_45578_ * 16 + 8;
        final double e = chunkPos.f_45579_ * 16 + 8;
        final double retValue =
            VSGameUtilsKt.squaredDistanceBetweenInclShips(entity.m_9236_(), entity.m_20185_(), 0, entity.m_20189_(), d,
                0,
                e);

        cir.setReturnValue(retValue);
    }

    @Inject(method = "anyPlayerCloseEnoughForSpawning", at = @At("RETURN"), cancellable = true)
    void noPlayersCloseForSpawning(final ChunkPos chunkPos, final CallbackInfoReturnable<Boolean> cir) {
        if (VSGameUtilsKt.isChunkInShipyard(level, chunkPos.f_45578_, chunkPos.f_45579_)) {
            if (!cir.getReturnValue()) {
                final ServerShip ship = VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()
                    .getByChunkPos(chunkPos.f_45578_, chunkPos.f_45579_, VSGameUtilsKt.getDimensionId(level));
                if (ship != null) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

}
