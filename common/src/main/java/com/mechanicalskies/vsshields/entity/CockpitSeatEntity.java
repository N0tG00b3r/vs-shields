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

    /** Total boost fuel ticks (mirrors PodShipManager.BOOST_TICKS). */
    public static final int BOOST_TICKS_MAX = 80;

    // ── Synced data ───────────────────────────────────────────────────────────

    private static final EntityDataAccessor<Integer> PHASE_ID =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DRILL_TIMER =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    /** Remaining boost fuel ticks (0–80). Synced for HUD display. */
    private static final EntityDataAccessor<Integer> BOOST_FUEL =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    /** Current pod speed in m/s (rounded). Synced from server each tick for HUD display. */
    private static final EntityDataAccessor<Integer> SPEED_MPS =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.INT);

    /**
     * VS2 pod ship ID encoded as a String (MC 1.20.1 has no LONG serializer).
     * Empty string = no ship assigned yet.
     */
    private static final EntityDataAccessor<String> POD_SHIP_ID_STR =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.STRING);

    /**
     * Cockpit block's shipyard-space position encoded as "x,y,z" (Double precision).
     * Computed once on the server from the seat's world position at spawn time, synced to client.
     * Never changes while the pod is alive.
     * Used by MixinCockpitSeatShipMount as the stable mountPosInShip for setupWithShipMounted.
     */
    private static final EntityDataAccessor<String> COCKPIT_SY_POS =
            SynchedEntityData.defineId(CockpitSeatEntity.class, EntityDataSerializers.STRING);


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
        /** Called each tick during BOOST/COAST. yaw/pitch = player look angles (degrees). boostActive: 1=Space held. */
        void onRcs(int seatEntityId, float yaw, float pitch, int boostActive);
    }

    public interface TrustCallback {
        void trustPassenger(UUID uuid, long gameTime, int ticks);
    }

    public interface RegisterCallback {
        void onPodRegistered(long podShipId, int seatEntityId, long ignoredShipId, String dimensionId, int facingOrdinal);
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
    public static void notifyPodRegistered(long podShipId, int seatEntityId, long ignoredShipId, String dimensionId, int facingOrdinal) {
        if (registerCallback != null) registerCallback.onPodRegistered(podShipId, seatEntityId, ignoredShipId, dimensionId, facingOrdinal);
    }

    // ── Construction ──────────────────────────────────────────────────────────

    public CockpitSeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE_ID,       Phase.AIMING.id);
        entityData.define(DRILL_TIMER,    0);
        entityData.define(BOOST_FUEL,     BOOST_TICKS_MAX);
        entityData.define(SPEED_MPS,      0);
        entityData.define(POD_SHIP_ID_STR, "");
        entityData.define(COCKPIT_SY_POS, "");
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
     * <p>Math (with MixinPlayerCockpitSize active, dims=0.6×0.9, eyeHeight=0.7):
     * <pre>
     *   seatY   = blockY + 0.5 − 0.2 (BoardingPodCockpitBlock spawn offset)
     *           = blockY + 0.3
     *   playerY = seatY + ridingOffset = blockY + 0.3 + (−0.3) = blockY + 0.0
     *   eyeY    = playerY + 0.7       = blockY + 0.7  (camera unchanged)
     *   player model spans blockY+0.0 → blockY+0.9, butt at ~blockY+0.33
     * </pre>
     */
    @Override
    public double getPassengersRidingOffset() { return -0.3; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Phase getPhase()          { return Phase.byId(entityData.get(PHASE_ID)); }
    public void  setPhase(Phase p)   { entityData.set(PHASE_ID, p.id); }

    public int  getDrillTimer()      { return entityData.get(DRILL_TIMER); }
    public void setDrillTimer(int n) { entityData.set(DRILL_TIMER, n); }

    public int  getBoostFuel()       { return entityData.get(BOOST_FUEL); }
    public void setBoostFuel(int n)  { entityData.set(BOOST_FUEL, n); }

    public int  getSpeedMps()        { return entityData.get(SPEED_MPS); }
    public void setSpeedMps(int n)   { entityData.set(SPEED_MPS, n); }

    /** Returns the VS2 pod ship ID, or {@link Long#MIN_VALUE} if not yet assigned. */
    public long getPodShipId() {
        String s = entityData.get(POD_SHIP_ID_STR);
        return s.isEmpty() ? Long.MIN_VALUE : Long.parseLong(s);
    }
    public void setPodShipId(long id) {
        entityData.set(POD_SHIP_ID_STR, id == Long.MIN_VALUE ? "" : Long.toString(id));
    }

    public String getCockpitShipyardPosStr() { return entityData.get(COCKPIT_SY_POS); }
    public void   setCockpitShipyardPosStr(String s) { entityData.set(COCKPIT_SY_POS, s); }

    /**
     * Returns the cockpit block's shipyard-space position, or null if not yet synced from server.
     * Used by MixinCockpitSeatShipMount as the stable mountPosInShip for VS2's camera pipeline.
     */
    @org.jetbrains.annotations.Nullable
    public org.joml.Vector3d getCockpitShipyardPos() {
        String s = entityData.get(COCKPIT_SY_POS);
        if (s.isEmpty()) return null;
        String[] p = s.split(",");
        return new org.joml.Vector3d(Double.parseDouble(p[0]),
                                     Double.parseDouble(p[1]),
                                     Double.parseDouble(p[2]));
    }

    // ── Network delegates (called from ModNetwork) ────────────────────────────

    /** Delegates FIRE packet to forge-side PodShipManager. */
    public void onFire(float yaw, float pitch) {
        if (fireCallback != null) fireCallback.onFire(getId(), yaw, pitch);
    }

    /** Delegates steering/boost packet to forge-side PodShipManager. */
    public void onRcs(float yaw, float pitch, int boostActive) {
        if (rcsCallback != null) rcsCallback.onRcs(getId(), yaw, pitch, boostActive);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) spawnPhaseParticles();
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
        entityData.set(PHASE_ID,        tag.getInt("Phase"));
        entityData.set(DRILL_TIMER,     tag.getInt("DrillTimer"));
        entityData.set(BOOST_FUEL,      tag.getInt("BoostFuel"));
        entityData.set(POD_SHIP_ID_STR, tag.getString("PodShipId"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Phase",      getPhase().id);
        tag.putInt("DrillTimer", getDrillTimer());
        tag.putInt("BoostFuel",  getBoostFuel());
        tag.putString("PodShipId", entityData.get(POD_SHIP_ID_STR));
    }
}
