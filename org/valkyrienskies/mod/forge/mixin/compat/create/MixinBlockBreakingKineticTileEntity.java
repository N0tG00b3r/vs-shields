package org.valkyrienskies.mod.forge.mixin.compat.create;

import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(BlockBreakingKineticBlockEntity.class)
public abstract class MixinBlockBreakingKineticTileEntity {

    @Shadow
    protected abstract BlockPos getBreakingPos();

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/base/BlockBreakingKineticBlockEntity;getBreakingPos()Lnet/minecraft/core/BlockPos;"
        ), remap = false
    )
    private BlockPos getBreakingBlockPos(final BlockBreakingKineticBlockEntity self) {
        final BlockPos orig = this.getBreakingPos();
        final Vec3 origin;
        final Vec3 target;
        final Ship ship = VSGameUtilsKt.getShipManagingPos(self.m_58904_(), self.m_58899_());

        if (ship != null) {
            origin = VectorConversionsMCKt.toMinecraft(
                ship.getShipToWorld()
                    .transformPosition(VectorConversionsMCKt.toJOMLD(self.m_58899_()).add(0.5, 0.5, 0.5))
            );
            target = VectorConversionsMCKt.toMinecraft(
                ship.getShipToWorld().transformPosition(VectorConversionsMCKt.toJOMLD(orig).add(0.5, 0.5, 0.5))
            );
        } else {
            origin = Vec3.m_82512_(self.m_58899_());
            target = Vec3.m_82512_(orig);
        }

        final Vec3 diff = target.m_82546_(origin);
        final BlockHitResult result = self.m_58904_().m_45547_(new ClipContext(
            origin.m_82549_(diff.m_82490_(0.4)),
            target.m_82549_(diff.m_82490_(0.2)),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            null
        ));

        if (result.m_6662_() == HitResult.Type.MISS) {
            return orig;
        }

        return result.m_82425_();
    }

}

