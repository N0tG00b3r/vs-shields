package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Shrinks the player's hitbox while seated in a {@link CockpitSeatEntity}
 * (VS2 boarding pod cockpit) so the player can crawl through the 2×2 breach
 * tunnel after landing: 0.6×1.8 → 0.5×0.7.
 *
 * <p>Eye-height override lives in {@link MixinEntityCockpitEye} which targets
 * {@code Entity.class} directly — required because {@code getEyeHeight()} is
 * declared {@code final} in Entity and is not visible from a Player-class mixin.
 */
@Mixin(Player.class)
public class MixinPlayerCockpitSize {

    /**
     * Returns a compact hitbox while seated in the boarding pod cockpit.
     * 0.6×1.8 → 0.5×0.7 so the player fits through tight hull breaches.
     */
    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void shrinkDimsInCockpit(Pose pose,
            CallbackInfoReturnable<EntityDimensions> cir) {
        if (((Player)(Object)this).getVehicle() instanceof CockpitSeatEntity) {
            cir.setReturnValue(EntityDimensions.fixed(0.5f, 0.7f));
        }
    }
}
