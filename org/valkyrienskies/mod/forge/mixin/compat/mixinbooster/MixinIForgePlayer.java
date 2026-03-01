package org.valkyrienskies.mod.forge.mixin.compat.mixinbooster;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.config.VSGameConfig;

/**
 * A variant of {@link org.valkyrienskies.mod.forge.mixin.feature.forge_interact.MixinIForgePlayer} that uses
 * injectors to be more compatible with other mods
 */
@Mixin(IForgePlayer.class)
@Pseudo
public interface MixinIForgePlayer {

    @Shadow
    Player self();

    /**
     * Include ships in server-side distance check when player interacts with a block.
     *
     * @return
     */
    @ModifyExpressionValue(
        method = "canReach(Lnet/minecraft/core/BlockPos;D)Z",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;")
    )
    default Vec3 replacePosition(final Vec3 original) {
        return VSGameUtilsKt.toWorldCoordinates(self().m_9236_(), original);
    }

    @Inject(
        method = "isCloseEnough(Lnet/minecraft/world/entity/Entity;D)Z",
        at = @At(value = "HEAD"),
        cancellable = true,
        remap = false
    )
    default void preIsCloseEnough(final Entity entity, final double distance, final CallbackInfoReturnable<Double> cir) {
        if (VSGameConfig.SERVER.getEnableInteractDistanceChecks() &&
            VSGameUtilsKt.isBlockInShipyard(entity.m_9236_(), entity.m_20183_())) {
            final Vec3 eye = this.self().m_146892_();
            final Vec3 targetCenter = entity.m_20318_(1.0F).m_82520_(0.0, entity.m_20206_() / 2.0F, 0.0);
            final Optional<Vec3> hit = entity.m_20191_().m_82371_(eye, targetCenter);
            hit.ifPresent(vec3 -> cir.setReturnValue(VSGameUtilsKt.squaredDistanceBetweenInclShips(this.self().m_9236_(),
                vec3.f_82479_, vec3.f_82480_, vec3.f_82481_, eye.f_82479_, eye.f_82480_, eye.f_82481_)));
        }
    }
}
