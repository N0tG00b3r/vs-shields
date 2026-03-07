package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.network.ClientShieldManager;
import com.mechanicalskies.vsshields.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Client-side handler for shield visual effects:
 * - Hit flash: burst of particles at impact point
 * - Shield break: expanding sphere of shards + particles at shield center
 */
public class ShieldEffectHandler {

    private static final Random RANDOM = new Random();

    // Active break animations
    private static final List<BreakAnimation> breakAnimations = new ArrayList<>();

    /**
     * Called when a shield hit packet arrives.
     * Spawns a burst of particles at the hit location.
     */
    public static void onShieldHit(long shipId, double hitX, double hitY, double hitZ, float damage) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null)
            return;

        // Scale particle count with damage (min 6, max 40)
        int particleCount = Math.min(40, Math.max(6, (int) (damage / 5.0)));

        // Electric spark particles at impact point
        for (int i = 0; i < particleCount; i++) {
            double vx = (RANDOM.nextGaussian()) * 0.15;
            double vy = (RANDOM.nextGaussian()) * 0.15;
            double vz = (RANDOM.nextGaussian()) * 0.15;

            // Blue-ish sparks: use electric_spark and end_rod for the glow effect
            if (i % 3 == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        hitX + RANDOM.nextGaussian() * 0.5,
                        hitY + RANDOM.nextGaussian() * 0.5,
                        hitZ + RANDOM.nextGaussian() * 0.5,
                        vx, vy, vz);
            } else if (i % 3 == 1) {
                level.addParticle(ParticleTypes.END_ROD,
                        hitX + RANDOM.nextGaussian() * 0.3,
                        hitY + RANDOM.nextGaussian() * 0.3,
                        hitZ + RANDOM.nextGaussian() * 0.3,
                        vx * 0.5, vy * 0.5, vz * 0.5);
            } else {
                level.addParticle(ParticleTypes.CRIT,
                        hitX + RANDOM.nextGaussian() * 0.4,
                        hitY + RANDOM.nextGaussian() * 0.4,
                        hitZ + RANDOM.nextGaussian() * 0.4,
                        vx, vy, vz);
            }
        }

        // Central flash — a bright enchant particle burst
        for (int i = 0; i < 3; i++) {
            level.addParticle(ParticleTypes.FLASH,
                    hitX, hitY, hitZ,
                    0, 0, 0);
        }

        level.playLocalSound(hitX, hitY, hitZ, ModSounds.SHIELD_HIT.get(), SoundSource.BLOCKS, 1.0f, 1.0f, false);

        // Notify panel animator for hit flash + ripple
        ShieldPanelAnimator animator = ClientShieldManager.getInstance().getAnimator(shipId);
        if (animator != null) {
            ClientShieldManager.ClientShieldData data = ClientShieldManager.getInstance().getShield(shipId);
            if (data != null) {
                animator.onHit(hitX, hitY, hitZ, data);
            }
        }
    }

    /**
     * Called when a shield break packet arrives.
     * Starts a "shattered glass" break animation — particles fly outward from the
     * shield center.
     */
    public static void onShieldBreak(long shipId) {
        ClientShieldManager.ClientShieldData data = ClientShieldManager.getInstance().getShield(shipId);
        if (data == null)
            return;

        double cx = (data.worldMinX + data.worldMaxX) / 2.0;
        double cy = (data.worldMinY + data.worldMaxY) / 2.0;
        double cz = (data.worldMinZ + data.worldMaxZ) / 2.0;

        double radiusX = (data.worldMaxX - data.worldMinX) / 2.0;
        double radiusY = (data.worldMaxY - data.worldMinY) / 2.0;
        double radiusZ = (data.worldMaxZ - data.worldMinZ) / 2.0;

        breakAnimations.add(new BreakAnimation(cx, cy, cz, radiusX, radiusY, radiusZ));

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.level.playLocalSound(cx, cy, cz, ModSounds.SHIELD_COLLAPSE.get(), SoundSource.BLOCKS, 1.5f, 1.0f, false);
        }
    }

    public static void onShieldActivate(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(x, y, z, ModSounds.SHIELD_ACTIVATION.get(), SoundSource.BLOCKS, 1.0f, 1.0f, false);
    }

    public static void onShieldDeactivate(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(x, y, z, ModSounds.SHIELD_DEACTIVATION.get(), SoundSource.BLOCKS, 1.0f, 1.0f, false);
    }

    public static void onShieldRegen(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(x, y, z, ModSounds.SHIELD_REGENERATION.get(), SoundSource.BLOCKS, 1.0f, 1.0f, false);
    }

    /**
     * Called each client tick to progress break animations.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            breakAnimations.clear();
            return;
        }

        Iterator<BreakAnimation> it = breakAnimations.iterator();
        while (it.hasNext()) {
            BreakAnimation anim = it.next();
            anim.tick(level);
            if (anim.isFinished()) {
                it.remove();
            }
        }
    }

    /**
     * Break animation: spreads "shard" particles outward from the shield surface
     * over ~1 second (20 ticks), then fades out.
     */
    private static class BreakAnimation {
        private final double cx, cy, cz;
        private final double rx, ry, rz;
        private int ticksAlive = 0;
        private static final int DURATION = 20; // 1 second

        BreakAnimation(double cx, double cy, double cz, double rx, double ry, double rz) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        void tick(ClientLevel level) {
            ticksAlive++;

            // Phase 1 (ticks 1-5): intense burst — shards fly outward from shield surface
            if (ticksAlive <= 5) {
                int count = ticksAlive == 1 ? 60 : 20;
                for (int i = 0; i < count; i++) {
                    // Random point on the ellipsoid surface
                    double theta = RANDOM.nextDouble() * Math.PI * 2;
                    double phi = Math.acos(2.0 * RANDOM.nextDouble() - 1.0);
                    double sx = Math.sin(phi) * Math.cos(theta);
                    double sy = Math.cos(phi);
                    double sz = Math.sin(phi) * Math.sin(theta);

                    double px = cx + sx * rx;
                    double py = cy + sy * ry;
                    double pz = cz + sz * rz;

                    // Velocity: outward from center
                    double speed = 0.2 + RANDOM.nextDouble() * 0.3;
                    double vx = sx * speed;
                    double vy = sy * speed;
                    double vz = sz * speed;

                    // Mix of shard-like particles
                    if (i % 4 == 0) {
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                                px, py, pz, vx, vy, vz);
                    } else if (i % 4 == 1) {
                        level.addParticle(ParticleTypes.END_ROD,
                                px, py, pz, vx * 0.6, vy * 0.6, vz * 0.6);
                    } else if (i % 4 == 2) {
                        level.addParticle(ParticleTypes.CRIT,
                                px, py, pz, vx, vy, vz);
                    } else {
                        level.addParticle(ParticleTypes.ENCHANTED_HIT,
                                px, py, pz, vx, vy, vz);
                    }
                }
            }

            // Phase 2 (ticks 6-15): residual sparks falling
            if (ticksAlive > 5 && ticksAlive <= 15) {
                int count = 5;
                for (int i = 0; i < count; i++) {
                    double px = cx + (RANDOM.nextDouble() - 0.5) * rx * 2.5;
                    double py = cy + (RANDOM.nextDouble() - 0.5) * ry * 2.5;
                    double pz = cz + (RANDOM.nextDouble() - 0.5) * rz * 2.5;
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            px, py, pz,
                            RANDOM.nextGaussian() * 0.05,
                            -0.05 - RANDOM.nextDouble() * 0.1,
                            RANDOM.nextGaussian() * 0.05);
                }
            }
        }

        boolean isFinished() {
            return ticksAlive >= DURATION;
        }
    }
}
