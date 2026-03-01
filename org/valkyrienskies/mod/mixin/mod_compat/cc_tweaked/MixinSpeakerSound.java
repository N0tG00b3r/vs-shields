package org.valkyrienskies.mod.mixin.mod_compat.cc_tweaked;

import dan200.computercraft.client.sound.SpeakerSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.mod.client.audio.VelocityTickableSoundInstance;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(value = SpeakerSound.class, priority = 2000)
public abstract class MixinSpeakerSound extends AbstractSoundInstance implements VelocityTickableSoundInstance {
    @Unique
    private Vector3dc position = null;
    @Unique
    private Vector3dc velocity = new Vector3d();

    protected MixinSpeakerSound() {
        super((ResourceLocation) (null), null, null);
    }

    @Override
    public double m_7772_() {
        return this.position == null ? this.f_119575_ : this.position.x();
    }

    @Override
    public double m_7780_() {
        return this.position == null ? this.f_119576_ : this.position.y();
    }

    @Override
    public double m_7778_() {
        return this.position == null ? this.f_119577_ : this.position.z();
    }

    @Override
    public Vector3dc getVelocity() {
        return this.velocity;
    }

    @Override
    public void updateVelocity() {
        final Vector3d newPosition = new Vector3d(this.f_119575_, this.f_119576_, this.f_119577_);
        final ClientShip ship = VSGameUtilsKt.getLoadedShipManagingPos(Minecraft.m_91087_().f_91073_, this.f_119575_, this.f_119576_, this.f_119577_);
        if (ship != null) {
            ship.getRenderTransform().getShipToWorld().transformPosition(newPosition);
        }
        if (this.position != null) {
            this.velocity = newPosition.sub(this.position, new Vector3d());
        }
        this.position = newPosition;
    }
}
