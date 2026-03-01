package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.simibubi.create.content.trains.track.ITrackBlock;
import java.util.List;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(ITrackBlock.class)
public interface MixinITrackBlock {

    @Shadow
    List<Vec3> getTrackAxes(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState);

    /**
     * @author Bunting_chj
     * @reason to adjust the look vector of player to the ship
     */
    @Overwrite
    default Pair<Vec3, AxisDirection> getNearestTrackAxis(BlockGetter world, BlockPos pos, BlockState state,
        Vec3 lookVec) {
        final Ship ship = VSGameUtilsKt.getShipManagingPos((Level) world, pos);
        if(ship != null) {
            final Vector3d vecInShipJOML = ship.getTransform().getWorldToShip().transformDirection(lookVec.f_82479_, lookVec.f_82480_, lookVec.f_82481_, new Vector3d());
            lookVec = VectorConversionsMCKt.toMinecraft(vecInShipJOML);
        }
        Vec3 best = null;
        double bestDiff = Double.MAX_VALUE;
        for (Vec3 vec3 : getTrackAxes(world, pos, state)) {
            for (int opposite : Iterate.positiveAndNegative) {
                double distanceTo = vec3.m_82541_()
                    .m_82554_(lookVec.m_82490_(opposite));
                if (distanceTo > bestDiff)
                    continue;
                bestDiff = distanceTo;
                best = vec3;
            }
        }
        return Pair.of(best, lookVec.m_82526_(best.m_82542_(1, 0, 1)
            .m_82541_()) < 0 ? AxisDirection.POSITIVE : AxisDirection.NEGATIVE);
    }
}
