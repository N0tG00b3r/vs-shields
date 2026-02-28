package com.mechanicalskies.vsshields.forge.mixin;

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides {@link Entity#getEyeHeight()} for players seated in a
 * {@link CockpitSeatEntity} so the camera sits just behind the windshield glass.
 *
 * <p>Must target {@code Entity.class} (not {@code Player.class}) because
 * {@code getEyeHeight()} is declared {@code final} in Entity and is not
 * overridden by Player — a Player-targeted injection would silently no-op.
 */
@Mixin(Entity.class)
public class MixinEntityCockpitEye {

    @Inject(method = "getEyeHeight()F", at = @At("HEAD"), cancellable = true)
    private void cockpitEyeHeight(CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity)(Object)this;
        if (self instanceof Player && self.getVehicle() instanceof CockpitSeatEntity) {
            cir.setReturnValue(0.6f);
        }
    }
}
