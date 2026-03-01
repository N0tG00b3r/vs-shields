package org.valkyrienskies.mod.mixin.feature.ai.path_retargeting;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

/**
 * @author Tomato
 * Should allow for mobs to pathfind on ships.
 */
@Mixin(DefaultRandomPos.class)
public class MixinDefaultRandomPos {
    @Inject(
        method = "generateRandomPosTowardDirection",
        at = @At(
            value = "TAIL"
        ),
        cancellable = true
    )
    private static void postGenerateRandomPosTowardDirection(PathfinderMob pathfinderMob, int i, boolean bl,
        BlockPos blockPos, final CallbackInfoReturnable<BlockPos> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        if (pathfinderMob.m_9236_() != null) {
            final BlockPos blockPos3 = RandomPos.m_217863_(pathfinderMob, i, pathfinderMob.m_217043_(), blockPos);
            if (blockPos3 == null) {
                return;
            }
            AABB checker = new AABB(blockPos3);
            Iterable<LoadedShip> ships = VSGameUtilsKt.getShipObjectWorld(pathfinderMob.m_9236_()).getLoadedShips().getIntersecting(VectorConversionsMCKt.toJOML(checker), VSGameUtilsKt.getDimensionId(pathfinderMob.m_9236_()));
            if (ships.iterator().hasNext()) {
                for (LoadedShip ship : ships) {
                    Vector3d posInShip = ship.getWorldToShip()
                        .transformPosition(VectorConversionsMCKt.toJOMLD(blockPos3), new Vector3d());
                    BlockPos blockPosInShip = BlockPos.m_274446_(VectorConversionsMCKt.toMinecraft(posInShip));
                    if (!GoalUtils.m_148454_(bl, pathfinderMob, blockPosInShip) &&
                        !GoalUtils.m_148448_(pathfinderMob.m_21573_(), blockPosInShip) &&
                        !GoalUtils.m_148458_(pathfinderMob, blockPosInShip)) {
                        cir.setReturnValue(blockPosInShip);
                        break;
                    }
                }
            }
        }
    }

    @WrapOperation(method = "getPosTowards", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/util/RandomPos;generateRandomPos(Lnet/minecraft/world/entity/PathfinderMob;Ljava/util/function/Supplier;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectGetPosInDirection(PathfinderMob arg, Supplier<BlockPos> supplier,
        Operation<Vec3> original) {
        Vec3 result = original.call(arg, supplier);
        if (result != null) {
            return VSGameUtilsKt.toWorldCoordinates(arg.m_9236_(), result);
        }
        return null;
    }

    @WrapOperation(method = "getPos", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/util/RandomPos;generateRandomPos(Lnet/minecraft/world/entity/PathfinderMob;Ljava/util/function/Supplier;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectGetPos(PathfinderMob arg, Supplier<BlockPos> supplier,
        Operation<Vec3> original) {
        Vec3 result = original.call(arg, supplier);
        if (result != null) {
            return VSGameUtilsKt.toWorldCoordinates(arg.m_9236_(), result);
        }
        return null;
    }


}
