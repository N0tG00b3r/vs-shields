package org.valkyrienskies.mod.forge.mixin.feature.forge_interact;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

@Mixin(IForgePlayer.class)
public interface MixinIForge2Player {

    @Shadow
    Player self();

    @Shadow
    double getBlockReach();

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Overwrite(remap = false)
    default boolean canReach(final BlockPos pos, final double padding) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            final double reach = this.getBlockReach() + padding;
            final Vec3 eyes = this.self().m_146892_();
            return VSGameUtilsKt.squaredDistanceBetweenInclShips(this.self().m_9236_(),
                pos.m_123341_() + 0.5,
                pos.m_123342_() + 0.5,
                pos.m_123343_() + 0.5,
                eyes.f_82479_, eyes.f_82480_, eyes.f_82481_
            ) <= reach * reach;
        } else {
            return true;
        }
    }

    /**
     * Include ships in server-side distance check when player interacts with a block.
     */
    @Overwrite(remap = false)
    default boolean canReachRaw(final BlockPos pos, final double padding) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            double reach = self().m_21133_(ForgeMod.BLOCK_REACH.get()) + padding;
            final Vec3 eyes = this.self().m_146892_();
            return VSGameUtilsKt.squaredDistanceBetweenInclShips(this.self().m_9236_(),
                pos.m_123341_() + 0.5,
                pos.m_123342_() + 0.5,
                pos.m_123343_() + 0.5,
                eyes.f_82479_, eyes.f_82480_, eyes.f_82481_
            ) <= reach * reach;
        } else {
            return true;
        }
    }


    @Overwrite(remap = false)
    default boolean isCloseEnough(final Entity entity, final double distance) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks()) {
            final Vec3 eye = this.self().m_146892_();
            final Vec3 targetCenter = entity.m_20318_(1.0F).m_82520_(0.0, (double) (entity.m_20206_() / 2.0F), 0.0);
            final Optional<Vec3> hit = entity.m_20191_().m_82371_(eye, targetCenter);
            return (hit.isPresent() ?
                VSGameUtilsKt.squaredDistanceBetweenInclShips(this.self().m_9236_(),
                    hit.get().f_82479_, hit.get().f_82480_, hit.get().f_82481_, eye.f_82479_, eye.f_82480_, eye.f_82481_)
                : this.self().m_20280_(entity)) < distance * distance;
        } else {
            return true;
        }
    }
}
