package org.valkyrienskies.mod.forge.mixin.compat.thermalexpansion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(targets = "cofh.core.block.entity.TileCoFH", remap = false)
@Pseudo
public abstract class MixinTileCoFH extends BlockEntity {

    // Necessary constructor because we are extending [BlockEntity]
    public MixinTileCoFH(final BlockEntityType<?> arg, final BlockPos arg2, final BlockState arg3) {
        super(arg, arg2, arg3);
    }

    @Inject(
        at = @At("HEAD"),
        method = "playerWithinDistance",
        cancellable = true
    )
    private void prePlayerWithinDistance(final Player player, final double distanceSq,
        final CallbackInfoReturnable<Boolean> ci) {
        final Level level = m_58904_();
        // Sanity check
        if (level == null) {
            return;
        }
        final BlockPos worldPos = m_58899_();
        final double squareDistance =
            VSGameUtilsKt.squaredDistanceToInclShips(player, worldPos.m_123341_(), worldPos.m_123342_(), worldPos.m_123343_());
        final boolean isPlayerWithinDistance = !m_58901_() && squareDistance <= distanceSq;
        ci.setReturnValue(isPlayerWithinDistance);
    }
}
