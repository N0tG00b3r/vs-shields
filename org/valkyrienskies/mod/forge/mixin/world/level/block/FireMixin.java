package org.valkyrienskies.mod.forge.mixin.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(FireBlock.class)
public abstract class FireMixin {

    @Unique
    private boolean isModifyingFireTick = false;

    @Shadow
    @Final
    public static IntegerProperty AGE;

    @Inject(method = "tick", at = @At("TAIL"))
    public void fireTickMixin(final BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random,
        final CallbackInfo ci) {
        if (isModifyingFireTick) {
            return;
        }

        isModifyingFireTick = true;

        final double origX = pos.m_123341_();
        final double origY = pos.m_123342_();
        final double origZ = pos.m_123343_();

        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX, origY, origZ, 3, (x, y, z) -> {
            final BlockPos newPos = BlockPos.m_274561_(x, y, z);

            if (level.m_46801_(newPos)) {
                level.m_7471_(pos, false);
            }

            final int i = state.m_61143_(AGE);

            final boolean bl2 = level.m_46758_(newPos);
            final int k = bl2 ? -50 : 0;
            this.tryCatchFire(level, pos.m_122029_(), 300 + k, random, i, Direction.WEST);
            this.tryCatchFire(level, pos.m_122024_(), 300 + k, random, i, Direction.EAST);
            this.tryCatchFire(level, pos.m_7495_(), 250 + k, random, i, Direction.UP);
            this.tryCatchFire(level, pos.m_7494_(), 250 + k, random, i, Direction.DOWN);
            this.tryCatchFire(level, pos.m_122012_(), 300 + k, random, i, Direction.SOUTH);
            this.tryCatchFire(level, pos.m_122019_(), 300 + k, random, i, Direction.NORTH);
            final BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int l = -1; l <= 1; ++l) {
                for (int m = -1; m <= 1; ++m) {
                    for (int n = -1; n <= 4; ++n) {
                        if (l != 0 || n != 0 || m != 0) {
                            int o = 100;
                            if (n > 1) {
                                o += (n - 1) * 100;
                            }

                            mutableBlockPos.m_122154_(newPos, l, n, m);
                            final int p = this.getIgniteOdds(level, mutableBlockPos);
                            if (p > 0) {
                                int q = (p + 40 + level.m_46791_().m_19028_() * 7) / (i + 30);
                                if (bl2) {
                                    q /= 2;
                                }

                                if (q > 0 && random.m_188503_(o) <= q
                                    && (!level.m_46471_() || !this.isNearRain(level, mutableBlockPos))) {
                                    final int r = Math.min(15, i + random.m_188503_(5) / 4);
                                    level.m_7731_(mutableBlockPos, this.getStateWithAge(level, mutableBlockPos, r), 3);
                                }
                            }
                        }
                    }
                }
            }
        });

        isModifyingFireTick = false;

    }

    @Inject(method = "onPlace", at = @At("HEAD"))
    public void onPlaceMixin(final BlockState state, final Level level, final BlockPos pos, final BlockState oldState,
        final boolean isMoving,
        final CallbackInfo ci) {
        final double origX = pos.m_123341_();
        final double origY = pos.m_123342_();
        final double origZ = pos.m_123343_();

        VSGameUtilsKt.transformToNearbyShipsAndWorld(level, origX, origY, origZ, 1, (x, y, z) -> {

            final BlockPos newPos = BlockPos.m_274561_(x, y, z);
            if (level.m_46801_(newPos)) {
                level.m_7471_(pos, false);
            }
        });
    }

    @Shadow
    private void tryCatchFire(final Level arg, final BlockPos arg2, final int k, final RandomSource random, final int l,
        final Direction face) {
    }

    @Shadow
    protected abstract BlockState getStateWithAge(LevelAccessor levelAccessor, BlockPos blockPos, int i);

    @Shadow
    protected abstract boolean isNearRain(Level level, BlockPos blockPos);

    @Shadow
    protected abstract int getIgniteOdds(LevelReader levelReader, BlockPos blockPos);
}
