package org.valkyrienskies.mod.mixin.feature.ai.path_retargeting;

import static net.minecraft.world.entity.ai.util.LandRandomPos.m_148513_;
import static net.minecraft.world.entity.ai.util.LandRandomPos.m_148518_;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
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
@Mixin(LandRandomPos.class)
public class MixinLandRandomPos {

    @Inject(
        method = "generateRandomPosTowardDirection",
        at = @At(
            value = "TAIL"
        ),
        cancellable = true
    )
    private static void postGenerateRandomPosTowardDirection(PathfinderMob pathfinderMob, int i, boolean bl,
        BlockPos blockPos, CallbackInfoReturnable<BlockPos> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        if (pathfinderMob.m_9236_() != null) {
            final BlockPos blockPos3 = RandomPos.m_217863_(pathfinderMob, i, pathfinderMob.m_217043_(), blockPos);
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

    @WrapOperation(method = "getPosInDirection", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/util/RandomPos;generateRandomPos(Lnet/minecraft/world/entity/PathfinderMob;Ljava/util/function/Supplier;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 redirectGetPosInDirection(PathfinderMob arg, Supplier<BlockPos> supplier,
        Operation<Vec3> original) {
        Vec3 result = original.call(arg, supplier);
        if (result != null) {
            return VSGameUtilsKt.toWorldCoordinates(arg.m_9236_(), result);
        }
        return null;
    }

    @Inject(method = "getPos(Lnet/minecraft/world/entity/PathfinderMob;IILjava/util/function/ToDoubleFunction;)Lnet/minecraft/world/phys/Vec3;", at = @At("TAIL"), cancellable = true)
    private static void preGetPos(PathfinderMob pathfinderMob, int i, int j,
        ToDoubleFunction<BlockPos> toDoubleFunction, CallbackInfoReturnable<Vec3> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        boolean bl = GoalUtils.m_148442_(pathfinderMob, i);
        Vec3 randomPos = RandomPos.m_148561_(() -> {
            BlockPos blockPos = RandomPos.m_217851_(pathfinderMob.m_217043_(), i, j);
            BlockPos blockPos2 = m_148513_(pathfinderMob, i, bl, blockPos);
            return blockPos2 == null ? null : m_148518_(pathfinderMob, blockPos2);
        }, toDoubleFunction);

        if (randomPos != null) {
            cir.setReturnValue(VSGameUtilsKt.toWorldCoordinates(pathfinderMob.m_9236_(), randomPos));
        }
    }
}
