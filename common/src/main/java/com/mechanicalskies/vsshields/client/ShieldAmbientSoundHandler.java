package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.network.ClientShieldManager;
import com.mechanicalskies.vsshields.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

import java.util.Map;

/**
 * Client-side handler that plays a looping ambient hum near active shields.
 * Manages a single {@link ShieldHumSound} instance — starts/stops/adjusts
 * volume
 * each client tick based on proximity to the nearest active shield.
 */
public class ShieldAmbientSoundHandler {

    private static final double MAX_HEAR_DISTANCE = 48.0;
    private static final double FADE_START = 32.0;

    private static ShieldHumSound currentSound = null;
    private static boolean soundPlaying = false;

    /**
     * Called every client tick from VSShieldsModClient.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stopSound();
            return;
        }

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        // Find distance to nearest active shield
        double nearestDist = Double.MAX_VALUE;
        double nearestX = 0, nearestY = 0, nearestZ = 0;

        Map<Long, ClientShieldManager.ClientShieldData> shields = ClientShieldManager.getInstance().getAllShields();

        for (ClientShieldManager.ClientShieldData data : shields.values()) {
            if (!data.active || data.currentHP <= 0)
                continue;

            // Center of shield's world AABB
            double cx = (data.worldMinX + data.worldMaxX) / 2.0;
            double cy = (data.worldMinY + data.worldMaxY) / 2.0;
            double cz = (data.worldMinZ + data.worldMaxZ) / 2.0;

            double dist = Math.sqrt(
                    (px - cx) * (px - cx) +
                            (py - cy) * (py - cy) +
                            (pz - cz) * (pz - cz));

            if (dist < nearestDist) {
                nearestDist = dist;
                nearestX = cx;
                nearestY = cy;
                nearestZ = cz;
            }
        }

        if (nearestDist > MAX_HEAR_DISTANCE) {
            stopSound();
            return;
        }

        // Calculate volume based on distance (1.0 at center, fades beyond FADE_START)
        float volume;
        if (nearestDist <= FADE_START) {
            volume = 2.0f;
        } else {
            volume = 2.0f * (1.0f - (float) ((nearestDist - FADE_START) / (MAX_HEAR_DISTANCE - FADE_START)));
        }
        volume = Math.max(0.0f, Math.min(2.0f, volume));

        if (!soundPlaying || currentSound == null || currentSound.isStopped()) {
            currentSound = new ShieldHumSound(nearestX, nearestY, nearestZ, volume);
            mc.getSoundManager().play(currentSound);
            soundPlaying = true;
        } else {
            currentSound.updatePosition(nearestX, nearestY, nearestZ);
            currentSound.updateVolume(volume);
        }
    }

    private static void stopSound() {
        if (soundPlaying && currentSound != null) {
            Minecraft.getInstance().getSoundManager().stop(currentSound);
            currentSound = null;
            soundPlaying = false;
        }
    }

    /**
     * Custom looping sound instance for the shield hum.
     */
    private static class ShieldHumSound extends AbstractTickableSoundInstance {

        private float targetVolume;

        protected ShieldHumSound(double x, double y, double z, float volume) {
            super(ModSounds.SHIELD_HUM.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.x = x;
            this.y = y;
            this.z = z;
            this.looping = true;
            this.delay = 0;
            this.volume = volume;
            this.targetVolume = volume;
            this.attenuation = Attenuation.LINEAR;
        }

        public void updatePosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public void updateVolume(float vol) {
            this.targetVolume = vol;
        }

        @Override
        public void tick() {
            // Smooth volume transitions
            if (Math.abs(volume - targetVolume) > 0.01f) {
                volume += (targetVolume - volume) * 0.1f;
            } else {
                volume = targetVolume;
            }
        }
    }
}
