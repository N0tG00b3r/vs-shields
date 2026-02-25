package com.mechanicalskies.vsshields.entity;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.registry.ModSounds;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import com.mechanicalskies.vsshields.registry.ModItems;

/**
 * Gravitational Mine entity.
 *
 * State machine:
 * FLIGHT — flies straight at 2.5 blocks/tick, no gravity
 * ARMED — hovers in place with bobbing, awaits ship collision (or 2-min
 * timeout)
 * DETONATING— applying physics impulse and discarding
 *
 * On detonation: applies massive VS2 physics impulse to the target ship via
 * {@link PhysicsApplier} (set from VSShieldsModForge on the forge side).
 * No damage to shield HP or blocks.
 */
public class GravitationalMineEntity extends Entity implements ItemSupplier {

    // ── Synced phase ─────────────────────────────────────────────────────────

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(GravitationalMineEntity.class,
            EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> ARMING_TICK = SynchedEntityData.defineId(
            GravitationalMineEntity.class,
            EntityDataSerializers.INT);

    public enum Phase {
        FLIGHT(0), PRE_ARMED(1), ARMED(2), DETONATING(3);

        public final int id;

        Phase(int id) {
            this.id = id;
        }

        public static Phase byId(int id) {
            return values()[Mth.clamp(id, 0, values().length - 1)];
        }
    }

    // ── Server-only fields ────────────────────────────────────────────────────

    /** World position where the mine was launched (for distance tracking). */
    public Vec3 flightStartPos;
    private int deploymentDistance = 30;
    private int armedStartTick;
    /** Ship that fired this mine — skipped during FLIGHT collision to allow launching from inside own ship. */
    private long ownerShipId = -1L;

    // ── Physics callback ─────────────────────────────────────────────────────

    public interface PhysicsApplier {
        void apply(Level level, long shipId,
                double fx, double fy, double fz,
                double px, double py, double pz);
    }

    private static PhysicsApplier physicsApplier = null;

    public static void setPhysicsApplier(PhysicsApplier applier) {
        physicsApplier = applier;
    }

    // ── Construction ──────────────────────────────────────────────────────────

    public GravitationalMineEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE, Phase.FLIGHT.id);
        entityData.define(ARMING_TICK, 0);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Phase getPhase() {
        return Phase.byId(entityData.get(PHASE));
    }

    private void setPhase(Phase p) {
        entityData.set(PHASE, p.id);
    }

    public void setDeploymentDistance(int d) {
        this.deploymentDistance = d;
    }

    public void setOwnerShipId(long id) {
        this.ownerShipId = id;
    }

    public int getArmingTick() {
        return entityData.get(ARMING_TICK);
    }

    private void setArmingTick(int t) {
        entityData.set(ARMING_TICK, t);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        // Apply velocity (Entity base class doesn't do this for non-physics entities)
        if (getPhase() == Phase.FLIGHT) {
            Vec3 dv = getDeltaMovement();
            setPos(getX() + dv.x, getY() + dv.y, getZ() + dv.z);

            // Check terrain (simple Y check and block detection)
            if (!level().isClientSide && !level().noCollision(getBoundingBox())) {
                discard();
                return;
            }
        }

        switch (getPhase()) {
            case FLIGHT -> tickFlight();
            case PRE_ARMED -> tickPreArmed();
            case ARMED -> tickArmed();
            case DETONATING -> discard();
        }
    }

    private void tickFlight() {
        // VS2 ships are in shipyard space — block clip won't detect them;
        // check manually every tick on the server
        if (!level().isClientSide) {
            // Anti-Rocket Exploit: Collision in FLIGHT phase = discard WITHOUT detonation
            if (checkShipAABBCollision(false))
                return;
        }

        if (flightStartPos != null &&
                position().distanceToSqr(flightStartPos) >= (double) deploymentDistance * deploymentDistance) {
            setPhase(Phase.PRE_ARMED);
            setDeltaMovement(Vec3.ZERO);
            setArmingTick(0);
        }
    }

    private void tickPreArmed() {
        int t = getArmingTick() + 1;
        setArmingTick(t);

        // Visual/Audio feedback for arming
        if (!level().isClientSide) {
            // Sound feedback every 10 ticks, increasing pitch/speed
            if (t % 10 == 0) {
                level().playSound(null, getX(), getY(), getZ(),
                        BuiltInRegistries.SOUND_EVENT.get(new ResourceLocation("minecraft:ui.button.click")),
                        SoundSource.NEUTRAL, 0.5f, 0.5f + (t * 0.01f));
            }
            if (t >= 60) {
                setPhase(Phase.ARMED);
                armedStartTick = tickCount;
                level().playSound(null, getX(), getY(), getZ(),
                        BuiltInRegistries.SOUND_EVENT
                                .get(new ResourceLocation("minecraft:entity.experience_orb.pickup")),
                        SoundSource.NEUTRAL, 1.0f, 1.2f);
            }
        }
    }

    private void tickArmed() {
        // Auto-discard after 2 minutes (2400 ticks)
        if (tickCount - armedStartTick > 2400) {
            discard();
            return;
        }

        // Visual bobbing handled in GravitationalMineRenderer (partialTick-smooth)
        if (!level().isClientSide)
            checkShipAABBCollision(true);
    }

    // ── Ship collision ────────────────────────────────────────────────────────

    /**
     * @param armedMode true = ARMED phase (loose 8-block proximity check, all ships)
     *                  false = FLIGHT phase (tight AABB, skips ownerShipId)
     */
    private boolean checkShipAABBCollision(boolean armedMode) {
        ServerLevel sl = (ServerLevel) level();
        try {
            for (LoadedServerShip ship : VSGameUtilsKt.getShipObjectWorld(sl).getLoadedShips()) {
                // During FLIGHT, skip the ship we were launched from
                if (!armedMode && ship.getId() == ownerShipId) continue;

                double pad;
                if (armedMode) {
                    // ARMED: detonate when entering shield OR within 8 blocks of ship hull
                    double shieldPad = getShieldPadding(ship.getId());
                    pad = Math.max(8.0, shieldPad);
                } else {
                    // FLIGHT: exact hull contact only
                    pad = 0.0;
                }

                AABBdc w = ship.getWorldAABB();
                AABB check = new AABB(
                        w.minX() - pad, w.minY() - pad, w.minZ() - pad,
                        w.maxX() + pad, w.maxY() + pad, w.maxZ() + pad);
                if (check.contains(getX(), getY(), getZ())) {
                    detonate(sl, ship);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private double getShieldPadding(long shipId) {
        ShieldInstance si = ShieldManager.getInstance().getShield(shipId);
        return (si != null && si.isActive())
                ? ShieldConfig.get().getGeneral().shieldPadding
                : 0.0;
    }

    // ── Detonation ────────────────────────────────────────────────────────────

    private void detonate(ServerLevel level, LoadedServerShip ship) {
        setPhase(Phase.DETONATING);

        // Force direction: flight velocity OR outward from ship centre to mine
        Vec3 vel = getDeltaMovement();
        if (vel.lengthSqr() < 1e-4) {
            Vector3dc ctr = ship.getTransform().getPositionInWorld();
            vel = new Vec3(getX() - ctr.x(), getY() - ctr.y(), getZ() - ctr.z()).normalize();
        }

        // Lever-arm method: transform mine world-pos into ship model-space,
        // then amplify lever arms to create large yaw/pitch torque.
        Matrix4dc w2s = ship.getWorldToShip();
        Vector3d mineModel = w2s.transformPosition(
                new Vector3d(getX(), getY(), getZ()), new Vector3d());
        mineModel.x *= 40.0;
        mineModel.z *= 40.0;
        mineModel.y *= 10.0;

        double mag = ShieldConfig.get().getGeneral().gravMineForceMagnitude * 100.0;

        if (physicsApplier != null) {
            physicsApplier.apply(level, ship.getId(),
                    vel.x * mag, vel.y * mag, vel.z * mag,
                    mineModel.x, mineModel.y, mineModel.z);
        }

        // ── Visual effects ──────────────────────────────────────────────────────
        // Main blast
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        // Gravitational portal swirl
        level.sendParticles(ParticleTypes.PORTAL,
                getX(), getY(), getZ(), 80, 1.2, 1.2, 1.2, 0.5);
        // Energy sparks
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                getX(), getY(), getZ(), 30, 0.3, 0.3, 0.3, 0.6);
        // Smoke
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                getX(), getY(), getZ(), 15, 0.6, 0.6, 0.6, 0.02);

        // ── Sound ───────────────────────────────────────────────────────────────
        level.playSound(null, getX(), getY(), getZ(),
                ModSounds.MINE_EXPLOSION.get(), SoundSource.NEUTRAL, 3.0f, 1.0f);

        discard();
    }

    // ── Damage override ───────────────────────────────────────────────────────

    /**
     * Mine can be destroyed by weapons but NOT by a single bare-hand hit.
     * Bare hand deals 1 damage; swords deal 4+; arrows deal 3+.
     * Any projectile or explosion instantly destroys the mine.
     * Melee with a weapon (damage >= 2) also destroys it.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRemoved()) return false;
        Phase p = getPhase();
        if (p == Phase.ARMED || p == Phase.PRE_ARMED) {
            if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION) || amount >= 2.0f) {
                discard();
                return true;
            }
            // Bare-hand punch (1 damage) — ignore
            return false;
        }
        return false;
    }

    // ── Entity overrides ──────────────────────────────────────────────────────

    @Override
    public boolean isPickable() {
        return getPhase() == Phase.ARMED || getPhase() == Phase.PRE_ARMED;
    }

    /** Prevents players from walking through the mine when it is armed. */
    @Override
    public boolean canBeCollidedWith() {
        Phase p = getPhase();
        return p == Phase.ARMED || p == Phase.PRE_ARMED;
    }

    /**
     * WAILA-style name tag: visible whenever the mine is visible to players.
     * Shows arming progress (PRE_ARMED) or danger warning (ARMED).
     */
    @Override
    public boolean shouldShowName() {
        Phase p = getPhase();
        return p == Phase.PRE_ARMED || p == Phase.ARMED;
    }

    @Override
    public Component getDisplayName() {
        Phase p = getPhase();
        if (p == Phase.PRE_ARMED) {
            int pct = Math.min(100, (getArmingTick() * 100) / 60);
            return Component.literal("\u26A1 ARMING " + pct + "%")
                    .withStyle(s -> s.withColor(ChatFormatting.GOLD));
        }
        if (p == Phase.ARMED) {
            return Component.literal("\u26A0 GRAVITATIONAL MINE")
                    .withStyle(s -> s.withColor(ChatFormatting.RED));
        }
        return super.getDisplayName();
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return dev.architectury.networking.NetworkManager.createAddEntityPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        deploymentDistance = tag.getInt("deploymentDistance");
        armedStartTick = tag.getInt("armedStartTick");
        ownerShipId = tag.getLong("ownerShipId");
        if (tag.contains("startX")) {
            flightStartPos = new Vec3(
                    tag.getDouble("startX"),
                    tag.getDouble("startY"),
                    tag.getDouble("startZ"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("deploymentDistance", deploymentDistance);
        tag.putInt("armedStartTick", armedStartTick);
        tag.putLong("ownerShipId", ownerShipId);
        if (flightStartPos != null) {
            tag.putDouble("startX", flightStartPos.x);
            tag.putDouble("startY", flightStartPos.y);
            tag.putDouble("startZ", flightStartPos.z);
        }
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.GRAVITATIONAL_MINE_ITEM.get());
    }
}
