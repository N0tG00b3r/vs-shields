package org.valkyrienskies.mod.mixin.feature.ai.node_evaluator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(WalkNodeEvaluator.class)
public class WalkNodeEvaluatorMixin {

    @Unique
    private static boolean isModifyingPathType = false;

    //Several node evaluators use getBlockPathTypeRaw, including WalkNodeEvaluator's specialized getBlockPathTypeStatic.
    //Mojang should really put this in the base NodeEvaluator.
    @Shadow()
    protected static BlockPathTypes getBlockPathTypeRaw(final BlockGetter blockGetter, final BlockPos blockPos) {
        return null;
    }

    @Inject(method = "getBlockPathTypeRaw", at = @At("RETURN"), cancellable = true)
    private static void getBlockPathTypeForShips(final BlockGetter blockGetter, final BlockPos blockPos,
        final CallbackInfoReturnable<BlockPathTypes> cir) {
        if (isModifyingPathType || !VSGameConfig.SERVER.getAiOnShips()) {
            return;
        }

        isModifyingPathType = true;

        final double origX = blockPos.m_123341_();
        final double origY = blockPos.m_123342_();
        final double origZ = blockPos.m_123343_();

        if (blockGetter instanceof PathNavigationRegion) {
            VSGameUtilsKt.transformToNearbyShipsAndWorld(((PathNavigationRegionAccessor) blockGetter).getLevel(), origX,
                origY, origZ, 2,
                (x, y, z) -> {
                    final BlockPos groundPos = BlockPos.m_274561_(x, y, z);
                    BlockPathTypes pathType =
                        getBlockPathTypeRaw(((PathNavigationRegionAccessor) blockGetter).getLevel(), groundPos);
                    //Check block path types all around target for walkable space. Not accurate, but helps with pathfinding on ships.
                    for (final Direction dir : Direction.values()) {
                        if (pathType == BlockPathTypes.OPEN || pathType == BlockPathTypes.BLOCKED) {
                            pathType = getBlockPathTypeRaw(((PathNavigationRegionAccessor) blockGetter).getLevel(),
                                groundPos.m_121945_(dir));
                        } else {
                            break;
                        }
                    }
                    cir.setReturnValue(pathType);
                });
        }

        isModifyingPathType = false;
    }
}
