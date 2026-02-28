package com.mechanicalskies.vsshields.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Invisible seat entity used by the VS2 Ship-based Boarding Pod.
 *
 * When the player interacts with a Boarding Pod Cockpit, the multiblock is
 * assembled into a VS2 physical ship and this invisible entity is spawned
 * inside the cockpit block. VS2 automatically carries the seat entity with
 * the ship. The player mounts this entity; the camera follows it.
 *
 * All physics and phase logic live in {@code PodShipManager} (forge module).
 * This entity is purely a data-carrier (synced phase/RCS data) and passenger mount.
 *
 * Phases match {@link BoardingPodEntity.Phase} IDs for HUD compatibility.
 */
public class CockpitSeatEntity extends Entity {

    // ── RCS constant (mirrored from BoardingPodEntity for HUD use) ───────────
    public static final int RCS_MAX_CHARGES = 5;

    // ── Synced data ───────────────────────────────────────────────────────────

    private static final EntityDataAccessor<Integer> PHASE_ID =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> RCS_CHARGES =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> RCS_COOLDOWN =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DRILL_TIMER =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    // ── Phase enum (same IDs as BoardingPodEntity.Phase) ─────────────────────

    public enum Phase {
        AIMING(0), BOOST(1), COAST(2), DRILLING(3);

        public final int id;
        Phase(int id) { this.id = id; }
        public static Phase byId(int i) { return values()[Mth.clamp(i, 0, values().length - 1)]; }
    }

    // ── Static callbacks (set from forge module, avoid cross-module coupling) ─

    public interface FireCallback {
        void onFire(int seatEntityId, float yaw, float pitch);
    }

    public interface RcsCallback {
        void onRcs(int seatEntityId, int lateralDir, int verticalDir);
    }

    public interface TrustCallback {
        void trustPassenger(UUID uuid, long gameTime, int ticks);
    }

    public interface RegisterCallback {
        void onPodRegistered(long podShipId, int seatEntityId, long ignoredShipId, String dimensionId);
    }

    private static FireCallback     fireCallback     = null;
    private static RcsCallback      rcsCallback      = null;
    private static TrustCallback    trustCallback    = null;
    private static RegisterCallback registerCallback = null;

    public static void setFireCallback(FireCallback cb)         { fireCallback     = cb; }
    public static void setRcsCallback(RcsCallback cb)           { rcsCallback      = cb; }
    public static void setTrustCallback(TrustCallback cb)       { trustCallback    = cb; }
    public static void setRegisterCallback(RegisterCallback cb) { registerCallback = cb; }

    /** Called by forge module to grant passage through solid shields. */
    public static TrustCallback getTrustCallback() { return trustCallback; }

    /** Called from BoardingPodCockpitBlock.use() to notify forge-side PodShipManager. */
    public static void notifyPodRegistered(long podShipId, int seatEntityId, long ignoredShipId, String dimensionId) {
        if (registerCallback != null) registerCallback.onPodRegistered(podShipId, seatEntityId, ignoredShipId, dimensionId);
    }

    // ── Construction ──────────────────────────────────────────────────────────

    public CockpitSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE_ID,     Phase.AIMING.id);
        entityData.define(RCS_CHARGES,  RCS_MAX_CHARGES);
        entityData.define(RCS_COOLDOWN, 0);
        entityData.define(DRILL_TIMER,  0);
    }

    // ── Dimensions / physics ─────────────────────────────────────────────────

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.3f, 0.3f);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    /**
     * Positions the passenger inside the cockpit block so the camera sits
     * just behind the windshield glass.
     *
     * <p>Math (with MixinPlayerCockpitSize active, eyeHeight = 0.6):
     * <pre>
     *   seatY   = blockY + 0.5 − 0.2 (BoardingPodCockpitBlock spawn offset)
     *           = blockY + 0.3
     *   playerY = seatY + ridingOffset = blockY + 0.3 + (−0.2) = blockY + 0.1
     *   eyeY    = playerY + 0.6       = blockY + 0.7  (70 % of block height)
     * </pre>
     */
    @Override
    public double getPassengersRidingOffset() { return -0.2; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Phase getPhase()                  { return Phase.byId(entityData.get(PHASE_ID)); }
    public void  setPhase(Phase p)           { entityData.set(PHASE_ID, p.id); }

    public int  getRcsCharges()              { return entityData.get(RCS_CHARGES); }
    public void setRcsCharges(int n)         { entityData.set(RCS_CHARGES, n); }

    public int  getRcsCooldown()             { return entityData.get(RCS_COOLDOWN); }
    public void setRcsCooldown(int n)        { entityData.set(RCS_COOLDOWN, n); }

    public int  getDrillTimer()              { return entityData.get(DRILL_TIMER); }
    public void setDrillTimer(int n)         { entityData.set(DRILL_TIMER, n); }

    // ── Network delegates (called from ModNetwork) ────────────────────────────

    /** Delegates FIRE packet to forge-side PodShipManager. */
    public void onFire(float yaw, float pitch) {
        if (fireCallback != null) fireCallback.onFire(getId(), yaw, pitch);
    }

    /** Delegates RCS packet to forge-side PodShipManager. */
    public void onRcs(int lateralDir, int verticalDir) {
        if (rcsCallback != null) rcsCallback.onRcs(getId(), lateralDir, verticalDir);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            // RCS cooldown ticks down each game tick (synced automatically)
            if (getRcsCooldown() > 0) setRcsCooldown(getRcsCooldown() - 1);
        } else {
            spawnPhaseParticles();
        }
    }

    private void spawnPhaseParticles() {
        Phase p = getPhase();
        if (p == Phase.BOOST) {
            // Non-directional flame + smoke (VS2 ship velocity not available client-side)
            level().addParticle(ParticleTypes.FLAME,
                    getX(), getY() - 0.3, getZ(), 0, -0.1, 0);
            level().addParticle(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() - 0.3, getZ(), 0, 0, 0);
        } else if (p == Phase.DRILLING) {
            for (int i = 0; i < 4; i++) {
                level().addParticle(ParticleTypes.CRIT,
                        getX() + (random.nextDouble() - 0.5) * 0.4,
                        getY() + (random.nextDouble() - 0.5) * 0.4,
                        getZ() + (random.nextDouble() - 0.5) * 0.4,
                        (random.nextDouble() - 0.5) * 0.8,
                        (random.nextDouble() - 0.5) * 0.8,
                        (random.nextDouble() - 0.5) * 0.8);
            }
            if (random.nextInt(3) == 0) {
                level().addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0.05, 0);
            }
        }
    }

    // ── Passenger / mounting ──────────────────────────────────────────────────

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        // Always discard seat on unmount — PodShipManager detects this on next tick
        // and handles pod ship cleanup (explosion in BOOST/COAST/DRILLING, drop items in AIMING).
        discard();
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(PHASE_ID,     tag.getInt("Phase"));
        entityData.set(RCS_CHARGES,  tag.getInt("RcsCharges"));
        entityData.set(RCS_COOLDOWN, tag.getInt("RcsCooldown"));
        entityData.set(DRILL_TIMER,  tag.getInt("DrillTimer"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Phase",      getPhase().id);
        tag.putInt("RcsCharges", getRcsCharges());
        tag.putInt("RcsCooldown", getRcsCooldown());
        tag.putInt("DrillTimer", getDrillTimer());
    }
}
