package org.valkyrienskies.mod.mixin.feature.clip_replace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;

@Mixin(Level.class)
public abstract class MixinLevel implements BlockGetter {

    @Override
    public BlockHitResult m_45547_(final ClipContext clipContext) {

        if (VSGameUtilsKt.getShipManagingPos(Level.class.cast(this), clipContext.m_45693_()) !=
            VSGameUtilsKt.getShipManagingPos(Level.class.cast(this), clipContext.m_45702_())) {
            LogManager.getLogger().warn("Trying to clip from " +
                clipContext.m_45702_() + " to " + clipContext.m_45693_() +
                " wich one of them is in a shipyard wich is ... sus!!");

            final Vec3 vec3 = clipContext.m_45702_().m_82546_(clipContext.m_45693_());
            return BlockHitResult.m_82426_(
                clipContext.m_45693_(), Direction.m_122366_(vec3.f_82479_, vec3.f_82480_, vec3.f_82481_),
                BlockPos.m_274446_(clipContext.m_45693_())
            );
        } else {
            return RaycastUtilsKt.clipIncludeShips(Level.class.cast(this), clipContext);
        }
    }
}
