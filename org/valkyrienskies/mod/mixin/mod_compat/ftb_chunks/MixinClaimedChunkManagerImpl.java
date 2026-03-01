package org.valkyrienskies.mod.mixin.mod_compat.ftb_chunks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl")
public abstract class MixinClaimedChunkManagerImpl {
    @Unique
    private Entity entity = null;

    @ModifyVariable(method = "shouldPreventInteraction", at = @At("HEAD"), name = "actor", remap = false)
    private Entity ValkyrienSkies$entity(final Entity entity) {
        this.entity = entity;
        return entity;
    }

    @ModifyArg(
        method = "shouldPreventInteraction",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ftb/mods/ftblibrary/math/ChunkDimPos;<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"
        )
    )
    private BlockPos ValkyrienSkies$newChunkDimPos(final BlockPos pos) {
        if (entity == null || !VSGameConfig.SERVER.getFTBChunks().getShipsProtectedByClaims()) {
            return pos;
        }

        final Level level = entity.m_9236_();

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            return pos;
        }

        final Vector3d vec = ship.getShipToWorld().transformPosition(new Vector3d(pos.m_123341_(), pos.m_123342_(), pos.m_123343_()));
        final BlockPos newPos = BlockPos.m_274446_(VectorConversionsMCKt.toMinecraft(vec));

        if (
            (newPos.m_123342_() > level.m_151558_() || newPos.m_123342_() < level.m_141937_()) &&
                !VSGameConfig.SERVER.getFTBChunks().getShipsProtectionOutOfBuildHeight()
        ) {
            return pos;
        }

        return newPos;
    }
}
