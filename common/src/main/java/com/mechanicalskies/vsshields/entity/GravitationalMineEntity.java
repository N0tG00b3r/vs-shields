package com.mechanicalskies.vsshields.entity;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

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
public class GravitationalMineEntity extends Entity {

    // ── Synced phase ─────────────────────────────────────────────────────────

    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(GravitationalMineEntity.class,
            EntityDataSerializers.INT);

    public enum Phase {
        FLIGHT(0), ARMED(1), DETONATING(2);

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
    private double bobBaseY;

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
            case ARMED -> tickArmed();
            case DETONATING -> discard();
        }
    }

    private void tickFlight() {
        // VS2 ships are in shipyard space — block clip won't detect them;
        // check manually every tick on the server
        if (!level().isClientSide)
            checkShipAABBCollision();

        if (flightStartPos != null &&
                position().distanceToSqr(flightStartPos) >= (double) deploymentDistance * deploymentDistance) {
            setPhase(Phase.ARMED);
            setDeltaMovement(Vec3.ZERO);
            armedStartTick = tickCount;
            bobBaseY = getY();
        }
    }

    private void tickArmed() {
        // Slow bobbing oscillation (±0.04 blocks on Y axis)
        setPos(getX(), bobBaseY + Math.sin(tickCount * 0.1) * 0.04, getZ());

        // Auto-discard after 2 minutes (2400 ticks)
        if (tickCount - armedStartTick > 2400) {
            discard();
            return;
        }

        if (!level().isClientSide)
            checkShipAABBCollision();
    }

    // ── Ship collision ────────────────────────────────────────────────────────

    private void checkShipAABBCollision() {
        ServerLevel sl = (ServerLevel) level();
        try {
            for (LoadedServerShip ship : VSGameUtilsKt.getShipObjectWorld(sl).getLoadedShips()) {
                double pad = getShieldPadding(ship.getId());
                AABBdc w = ship.getWorldAABB();
                AABB check = new AABB(
                        w.minX() - pad, w.minY() - pad, w.minZ() - pad,
                        w.maxX() + pad, w.maxY() + pad, w.maxZ() + pad);
                if (check.contains(getX(), getY(), getZ())) {
                    detonate(sl, ship);
                    return;
                }
            }
        } catch (Exception ignored) {
            // VS2 not loaded or world not ready
        }
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

        // Force direction: use flight velocity (FLIGHT phase) or
        // outward vector from ship center to mine (ARMED phase)
        Vec3 vel = getDeltaMovement();
        if (vel.lengthSqr() < 1e-4) {
            Vector3dc ctr = ship.getTransform().getPositionInWorld();
            vel = new Vec3(getX() - ctr.x(), getY() - ctr.y(), getZ() - ctr.z()).normalize();
        }

        // Convert mine world position to ship model-space (creates the lever arm for
        // torque)
        Matrix4dc w2s = ship.getWorldToShip();
        Vector3d mineModel = w2s.transformPosition(
                new Vector3d(getX(), getY(), getZ()), new Vector3d());

        // Amplify the lever arm (x and z) to impart significant torque (yaw/pitch/roll)
        mineModel.x *= 40.0;
        mineModel.z *= 40.0;
        mineModel.y *= 10.0;

        double mag = ShieldConfig.get().getGeneral().gravMineForceMagnitude * 5.0;

        if (physicsApplier != null) {
            physicsApplier.apply(level, ship.getId(),
                    vel.x * mag, vel.y * mag, vel.z * mag,
                    mineModel.x, mineModel.y, mineModel.z);
        }

        // Signal clients to play particle burst (entity event 70)
        level.broadcastEntityEvent(this, (byte) 70);
        discard();
    }

    // ── Damage override ───────────────────────────────────────────────────────

    /**
     * Armed mine hit by arrow/bullet — destroy safely without triggering impulse.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getPhase() == Phase.ARMED) {
            discard();
            return true;
        }
        return false;
    }

    // ── Entity overrides ──────────────────────────────────────────────────────

    @Override
    public boolean isPickable() {
        return getPhase() == Phase.ARMED;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return dev.architectury.networking.NetworkManager.createAddEntityPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        deploymentDistance = tag.getInt("deploymentDistance");
        armedStartTick = tag.getInt("armedStartTick");
        bobBaseY = tag.getDouble("bobBaseY");
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
        tag.putDouble("bobBaseY", bobBaseY);
        if (flightStartPos != null) {
            tag.putDouble("startX", flightStartPos.x);
            tag.putDouble("startY", flightStartPos.y);
            tag.putDouble("startZ", flightStartPos.z);
        }
    }
}
