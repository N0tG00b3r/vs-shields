package org.valkyrienskies.mod.mixin.client.player;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.mixinducks.client.player.LocalPlayerDuck;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends LivingEntity implements LocalPlayerDuck {
    @Shadow
    private float yRotLast;
    @Shadow
    private float xRotLast;
    @Unique
    private Vec3 lastPosition = null;
    @Unique
    private Vector3dc velocity = new Vector3d();

    protected MixinLocalPlayer() {
        super(null, null);
    }

    /**
     * @reason We need to overwrite this method to force Minecraft to smoothly interpolate the Y rotation of the player
     * during rendering. Why it wasn't like this originally is beyond me \(>.<)/
     * @author StewStrong
     */
    @Inject(method = "getViewYRot", at = @At("HEAD"), cancellable = true)
    private void preGetViewYRot(final float partialTick, final CallbackInfoReturnable<Float> cir) {
        if (this.m_20159_()) {
            cir.setReturnValue(super.m_5675_(partialTick));
        } else {
            cir.setReturnValue(Mth.m_14179_(partialTick, this.f_19859_, this.m_146908_()));
        }
    }

    @Override
    public Vector3dc vs$getVelocity() {
        return this.velocity;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(final CallbackInfo ci) {
        final Vec3 pos = this.m_20182_();
        if (this.lastPosition != null) {
            this.velocity = new Vector3d(pos.f_82479_ - this.lastPosition.f_82479_, pos.f_82480_ - this.lastPosition.f_82480_, pos.f_82481_ - this.lastPosition.f_82481_);
        }
        this.lastPosition = pos;
    }

    @WrapMethod(
        method = "startRiding(Lnet/minecraft/world/entity/Entity;Z)Z"
    )
    private boolean adjustLookOnMount(Entity entity, boolean bl, Operation<Boolean> original) {
        Vector3d lookVector = VectorConversionsMCKt.toJOML(this.m_20154_());
        if(original.call(entity, bl)) {
            Ship ship = VSGameUtilsKt.getShipMountedTo(Entity.class.cast(this));
            if (ship != null) {
                final Vector3d transformedLook = ship.getTransform().getWorldToShip().transformDirection(lookVector);
                final double yaw = Math.atan2(-transformedLook.x, transformedLook.z) * 180.0 / Math.PI;
                final double pitch = Math.atan2(-transformedLook.y, Math.sqrt((transformedLook.x * transformedLook.x) + (transformedLook.z * transformedLook.z))) * 180.0 / Math.PI;
                this.m_146922_((float) yaw);
                this.m_146926_((float) pitch);
                this.f_19859_ = this.m_146908_();
                this.yRotLast = this.m_146908_();
                this.f_20885_ = this.m_146908_();
                this.f_20886_ = this.m_146908_();
                this.f_19860_ = this.m_146909_();
                this.xRotLast = this.m_146909_();
            }
            return true;
        }
        return false;
    }
}
