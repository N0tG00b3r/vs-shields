package com.mechanicalskies.vsshields.entity;

import com.mechanicalskies.vsshields.registry.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.UUID;

/**
 * Boarding Pod entity — rideable projectile used for ship boarding.
 *
 * Phases:
 *   AIMING   — stationary, waiting for fire packet (player aims with mouse)
 *   BOOST    — 40-tick rocket burn, minimal gravity (0.005/tick)
 *   COAST    — full gravity (0.08/tick), drag 0.99 until impact
 *   DRILLING — attached to target ship hull for 40 ticks; breach + disembark on finish
 */
public class BoardingPodEntity extends Entity implements ItemSupplier {

    private static final Logger LOGGER = LoggerFactory.getLogger("vs_shields/boarding_pod");

    // ── RCS constants ─────────────────────────────────────────────────────────

    public  static final int    RCS_MAX_CHARGES    = 5;
    private static final int    RCS_COOLDOWN_TICKS = 12;  // 0.6 s between impulses
    private static final double RCS_THRUST         = 0.18; // blocks/tick sideways

    // ── Magnetic Lock constants ────────────────────────────────────────────────

    private static final int   MAG_LOCK_RANGE = 7;    // blocks ahead to scan
    private static final float MAG_LOCK_LERP  = 0.30f; // velocity rotation speed per tick

    // ── Drill constants ────────────────────────────────────────────────────────

    private static final int DRILL_TOTAL_TICKS = 40; // 2 seconds of drilling

    // ── Synced data ───────────────────────────────────────────────────────────

    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(BoardingPodEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> RCS_CHARGES =
            SynchedEntityData.defineId(BoardingPodEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> RCS_COOLDOWN =
            SynchedEntityData.defineId(BoardingPodEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> DRILL_TIMER =
            SynchedEntityData.defineId(BoardingPodEntity.class, EntityDataSerializers.INT);

    public enum Phase {
        AIMING(0), BOOST(1), COAST(2), DRILLING(3);

        public final int id;
        Phase(int id) { this.id = id; }
        public static Phase byId(int i) { return values()[Mth.clamp(i, 0, values().length - 1)]; }
    }

    // ── Static trust callback (set from ShieldSolidBarrier) ──────────────────

    public interface TrustCallback {
        void trustPassenger(UUID uuid, long gameTime, int ticks);
    }

    private static TrustCallback trustCallback = null;
    public static void setTrustCallback(TrustCallback cb) { trustCallback = cb; }

    // ── Server-only state ────────────────────────────────────────────────────

    private int  boostTicks    = 0;
    private static final int BOOST_DURATION = 40; // 2 seconds

    private int  hitCount      = 0;
    private static final int MAX_HITS = 5; // bare-hand hits to break the parked pod

    /** HP pool for in-flight interception. */
    private int podHealth = 10;

    /** ID of the ship this pod was launched from — excluded from collision during flight. */
    private long ignoredShipId = Long.MIN_VALUE;

    /** ID of the ship the pod is currently drilling into (DRILLING phase). */
    private long    drillingShipId   = Long.MIN_VALUE;

    /** Pod position in shipyard space of the drilled ship (updated on startDrilling). */
    private Vector3d drillingLocalPos = null;

    /** Normalized flight direction saved at drill-start (velocity is zeroed during drilling). */
    private Vec3 drillingDirection = null;

    /**
     * Called when a player punches the pod while it is in AIMING phase.
     * Returns {@code true} when the pod has been hit enough times to break.
     */
    public boolean onHit() {
        hitCount++;
        return hitCount >= MAX_HITS;
    }

    // ── Construction ──────────────────────────────────────────────────────────

    public BoardingPodEntity(EntityType<?> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE,        Phase.AIMING.id);
        entityData.define(RCS_CHARGES,  RCS_MAX_CHARGES);
        entityData.define(RCS_COOLDOWN, 0);
        entityData.define(DRILL_TIMER,  0);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Phase getPhase() { return Phase.byId(entityData.get(PHASE)); }
    private void setPhase(Phase p) { entityData.set(PHASE, p.id); }

    public int getRcsCharges()  { return entityData.get(RCS_CHARGES); }
    public int getRcsCooldown() { return entityData.get(RCS_COOLDOWN); }
    public int getDrillTimer()  { return entityData.get(DRILL_TIMER); }

    /**
     * Called by ModNetwork when the server receives a BOARDING_POD_FIRE packet.
     * Yaw/pitch come from the client's current look direction.
     */
    public void fire(float yaw, float pitch) {
        if (getPhase() != Phase.AIMING) return;
        double speed = 1.2; // blocks/tick — slow enough for reliable collision detection
        double radY = Math.toRadians(yaw);
        double radP = Math.toRadians(pitch);
        double vx = -Math.sin(radY) * Math.cos(radP) * speed;
        double vy = -Math.sin(radP) * speed;
        double vz =  Math.cos(radY) * Math.cos(radP) * speed;
        setDeltaMovement(vx, vy, vz);
        setNoGravity(false);
        setPhase(Phase.BOOST);
        boostTicks = 0;
        entityData.set(RCS_CHARGES,  RCS_MAX_CHARGES);
        entityData.set(RCS_COOLDOWN, 0);
        drillingShipId    = Long.MIN_VALUE;
        drillingLocalPos  = null;
        drillingDirection = null;

        // Remember the launch ship so we never impact our own ship during boost/coast
        if (!level().isClientSide) {
            try {
                ServerLevel sl = (ServerLevel) level();
                var sw = VSGameUtilsKt.getShipObjectWorld(sl);
                if (sw != null) {
                    for (LoadedServerShip s : sw.getLoadedShips()) {
                        AABBdc w = s.getWorldAABB();
                        if (new AABB(w.minX(), w.minY(), w.minZ(), w.maxX(), w.maxY(), w.maxZ())
                                .contains(getX(), getY(), getZ())) {
                            ignoredShipId = s.getId();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[BoardingPod] Could not detect launch ship: {}", e.toString());
            }
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        // Decrement RCS cooldown server-side (synced to client automatically)
        if (!level().isClientSide && getRcsCooldown() > 0) {
            entityData.set(RCS_COOLDOWN, getRcsCooldown() - 1);
        }

        if (level().isClientSide) {
            if (getPhase() == Phase.BOOST) {
                // Thruster flame + smoke particles in BOOST phase
                Vec3 vel = getDeltaMovement();
                if (vel.lengthSqr() > 1e-4) {
                    Vec3 exhaust = new Vec3(getX() - vel.x * 0.8, getY() - vel.y * 0.8, getZ() - vel.z * 0.8);
                    level().addParticle(ParticleTypes.FLAME, exhaust.x, exhaust.y, exhaust.z,
                            -vel.x * 0.3, -vel.y * 0.3, -vel.z * 0.3);
                    level().addParticle(ParticleTypes.LARGE_SMOKE, exhaust.x, exhaust.y, exhaust.z, 0, 0, 0);
                }
            } else if (getPhase() == Phase.DRILLING) {
                // Sparks + hot metal particles during drill phase
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
                    level().addParticle(ParticleTypes.LAVA,
                            getX(), getY(), getZ(), 0, 0.05, 0);
                }
            }
            return;
        }

        switch (getPhase()) {
            case AIMING   -> { /* stationary — wait for fire packet */ }
            case BOOST    -> tickBoost();
            case COAST    -> tickCoast();
            case DRILLING -> tickDrilling();
        }
    }

    private void tickBoost() {
        boostTicks++;
        Vec3 vel = getDeltaMovement();
        setDeltaMovement(vel.x, vel.y - 0.005, vel.z);
        advanceOrImpact();
        if (getPhase() == Phase.BOOST && boostTicks >= BOOST_DURATION) {
            setPhase(Phase.COAST);
        }
    }

    private void tickCoast() {
        Vec3 vel = getDeltaMovement();
        setDeltaMovement(vel.x * 0.99, vel.y - 0.08, vel.z * 0.99);
        tryMagneticAlign();
        advanceOrImpact();
        if (getY() < level().getMinBuildHeight() - 20) discard();
    }

    /** Move one tick along delta movement; check terrain and ship collisions. */
    private void advanceOrImpact() {
        Vec3 vel = getDeltaMovement();
        ServerLevel sl = (ServerLevel) level();

        // ── VS2 ship collision — checked BEFORE terrain so we don't miss ships ──
        try {
            var shipWorld = VSGameUtilsKt.getShipObjectWorld(sl);
            if (shipWorld != null) {
                Vec3 nextPos = new Vec3(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
                for (LoadedServerShip ship : shipWorld.getLoadedShips()) {
                    if (ship.getId() == ignoredShipId) continue; // never hit our own launch ship
                    AABBdc w = ship.getWorldAABB();
                    AABB shipBox = new AABB(w.minX(), w.minY(), w.minZ(), w.maxX(), w.maxY(), w.maxZ());
                    // Check both current and next position to prevent tunnelling
                    if (shipBox.contains(nextPos.x, nextPos.y, nextPos.z) ||
                        shipBox.contains(getX(), getY(), getZ())) {
                        setPos(nextPos.x, nextPos.y, nextPos.z);
                        startDrilling(sl, ship);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[BoardingPod] Ship scan error: {}", e.toString());
        }

        // ── Terrain collision ──
        if (!level().noCollision(getBoundingBox().move(vel.x, vel.y, vel.z))) {
            impact(sl, null);
            return;
        }

        setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
    }

    // ── Magnetic Lock ──────────────────────────────────────────────────────────

    /**
     * When within MAG_LOCK_RANGE blocks of a ship, lerp the velocity vector
     * to point perpendicularly into the nearest hull face.
     * Uses a block raycast in shipyard space so the normal is always accurate
     * regardless of ship rotation.
     */
    private void tryMagneticAlign() {
        ServerLevel sl = (ServerLevel) level();
        Vec3 vel = getDeltaMovement();
        if (vel.lengthSqr() < 1e-4) return;

        var sw = VSGameUtilsKt.getShipObjectWorld(sl);
        if (sw == null) return;

        Vec3 velNorm = vel.normalize();
        Vec3 podWorld = new Vec3(getX(), getY(), getZ());
        Vec3 rayEnd   = podWorld.add(velNorm.scale(MAG_LOCK_RANGE));

        for (LoadedServerShip ship : sw.getLoadedShips()) {
            if (ship.getId() == ignoredShipId) continue;

            // Step 1: cheap expanded-AABB pre-filter
            AABBdc w = ship.getWorldAABB();
            AABB expanded = new AABB(
                    w.minX() - MAG_LOCK_RANGE, w.minY() - MAG_LOCK_RANGE, w.minZ() - MAG_LOCK_RANGE,
                    w.maxX() + MAG_LOCK_RANGE, w.maxY() + MAG_LOCK_RANGE, w.maxZ() + MAG_LOCK_RANGE);
            AABB normalBox = new AABB(w.minX(), w.minY(), w.minZ(), w.maxX(), w.maxY(), w.maxZ());
            if (!expanded.contains(rayEnd.x, rayEnd.y, rayEnd.z) &&
                normalBox.clip(podWorld, rayEnd).isEmpty())
                continue;

            // Step 2: transform ray into shipyard space (blocks are physically there)
            Matrix4dc w2s = ship.getWorldToShip();
            Vector3d spStart = w2s.transformPosition(new Vector3d(podWorld.x, podWorld.y, podWorld.z), new Vector3d());
            Vector3d spEnd   = w2s.transformPosition(new Vector3d(rayEnd.x,   rayEnd.y,   rayEnd.z),   new Vector3d());

            // Step 3: block raycast in shipyard space
            ClipContext ctx = new ClipContext(
                    new Vec3(spStart.x, spStart.y, spStart.z),
                    new Vec3(spEnd.x,   spEnd.y,   spEnd.z),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this);
            BlockHitResult bhr = sl.clip(ctx);
            if (bhr.getType() == HitResult.Type.MISS) continue;

            // Step 4: face normal → world space via rotation-only transform
            Direction hitFace = bhr.getDirection();
            Vector3d nShip = new Vector3d(
                    hitFace.getNormal().getX(),
                    hitFace.getNormal().getY(),
                    hitFace.getNormal().getZ());
            ship.getShipToWorld().transformDirection(nShip).normalize();
            // nShip now points OUTWARD from the ship surface

            // Step 5: lerp velocity direction toward -normal (into the ship)
            double speed = vel.length();
            Vec3 targetDir  = new Vec3(-nShip.x, -nShip.y, -nShip.z);
            Vec3 currentDir = vel.normalize();
            Vec3 aligned = currentDir
                    .add(targetDir.subtract(currentDir).scale(MAG_LOCK_LERP))
                    .normalize();
            setDeltaMovement(aligned.scale(speed));

            // Step 6: update visual orientation of the entity
            setYRot((float) Math.toDegrees(Math.atan2(-aligned.x, aligned.z)));
            setXRot((float) Math.toDegrees(Math.asin(Mth.clamp(-aligned.y, -1.0, 1.0))));
            break; // only align to the nearest ship
        }
    }

    // ── Drill Phase ────────────────────────────────────────────────────────────

    /**
     * Transition from flight into the DRILLING phase.
     * Stores the pod position in the ship's local (shipyard) space so it can
     * follow a moving/rotating ship each tick.
     */
    private void startDrilling(ServerLevel sl, LoadedServerShip ship) {
        Matrix4dc w2s = ship.getWorldToShip();
        drillingLocalPos = w2s.transformPosition(
                new Vector3d(getX(), getY(), getZ()), new Vector3d());
        drillingShipId = ship.getId();

        // Save flight direction BEFORE zeroing velocity — breachHull needs it
        Vec3 velAtImpact = getDeltaMovement();
        drillingDirection = velAtImpact.lengthSqr() > 1e-6
                ? velAtImpact.normalize() : getLookAngle();

        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        setPhase(Phase.DRILLING);
        entityData.set(DRILL_TIMER, DRILL_TOTAL_TICKS);

        // Initial impact sound — heavy metallic hit
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.IRON_GOLEM_HURT, SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /**
     * Ticked each server tick while DRILLING.
     * The pod follows the target ship via its live ShipToWorld transform,
     * plays cutting sounds, and finally calls impact() to breach the hull.
     */
    private void tickDrilling() {
        ServerLevel sl = (ServerLevel) level();

        // Locate the ship (it may have moved or rotated)
        LoadedServerShip ship = null;
        try {
            var sw = VSGameUtilsKt.getShipObjectWorld(sl);
            if (sw != null) {
                for (LoadedServerShip s : sw.getLoadedShips()) {
                    if (s.getId() == drillingShipId) { ship = s; break; }
                }
            }
        } catch (Exception ignored) {}

        if (ship == null) {
            // Ship unloaded or destroyed — abort
            ejectPassengers();
            discard();
            return;
        }

        // Follow the ship by re-projecting our local position each tick
        Vector3d wp = ship.getShipToWorld().transformPosition(drillingLocalPos, new Vector3d());
        setPos(wp.x, wp.y, wp.z);

        // Tick down the drill timer
        int timer = getDrillTimer();
        entityData.set(DRILL_TIMER, timer - 1);

        // Metal-on-metal scraping sound every 8 ticks
        if (timer % 8 == 0) {
            sl.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS,
                    0.9f, 0.7f + random.nextFloat() * 0.4f);
        }

        // Drilling complete — breach hull and disembark passenger
        if (timer <= 1) {
            setNoGravity(false);
            // Restore flight direction so breachHull can compute the tunnel axis
            if (drillingDirection != null)
                setDeltaMovement(drillingDirection.scale(0.1));
            impact(sl, ship);
        }
    }

    // ── Impact ────────────────────────────────────────────────────────────────

    private void impact(ServerLevel sl, LoadedServerShip targetShip) {
        // Find passenger before ejecting
        Entity passenger = getPassengers().isEmpty() ? null : getPassengers().get(0);

        if (targetShip != null) {
            // Solid shield interaction
            ShieldInstance shield = ShieldManager.getInstance().getShield(targetShip.getId());
            if (shield != null && shield.isActive() && shield.isSolidMode() && shield.getCurrentHP() > 0) {
                // Use ShieldManager's internal tick counter (NOT sl.getGameTime()) so lastHitTick
                // stays in sync with ShieldManager.tick() and regeneration works correctly.
                shield.damage(100.0, ShieldManager.getInstance().getCurrentTick());
            }
            // Always trust the passenger through solid barriers — must come before stopRiding()
            // so the barrier can see the trust entry on the same tick the player is teleported inside.
            if (passenger != null && trustCallback != null) {
                trustCallback.trustPassenger(passenger.getUUID(), sl.getGameTime(), 200);
            }
            // Breach hull blocks
            breachHull(sl, targetShip);
        }

        // Teleport passenger to 2nd block deep, facing the flight direction
        if (passenger != null) {
            Vec3 velN = getDeltaMovement();
            if (velN.lengthSqr() > 1e-6) velN = velN.normalize();
            passenger.stopRiding();
            passenger.teleportTo(
                    getX() + velN.x * 1.0,
                    getY() + velN.y * 1.0 + 0.3,   // +0.3 to clear floor block
                    getZ() + velN.z * 1.0);
            // Face the breach direction so the player can move straight in
            float newYaw   = (float) Math.toDegrees(Math.atan2(-velN.x, velN.z));
            float newPitch = (float) Math.toDegrees(Math.asin(-velN.y));
            passenger.setYRot(newYaw);
            passenger.setXRot(newPitch);
            passenger.setDeltaMovement(Vec3.ZERO);
            passenger.fallDistance = 0;
        } else {
            ejectPassengers();
        }

        // Visual impact effects
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 3, 0.3, 0.3, 0.3, 0.1);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 20, 0.5, 0.5, 0.5, 0.02);

        discard();
    }

    /**
     * Destroy a 2×2 tunnel (4 blocks deep, starting 1 block before impact point)
     * in the ship's hull along the pod's travel direction.
     * Coordinates are in shipyard (model) space.
     */
    private void breachHull(ServerLevel sl, LoadedServerShip ship) {
        try {
            Matrix4dc w2s = ship.getWorldToShip();

            // Pod centre in shipyard space
            Vector3d podShip = w2s.transformPosition(
                    new Vector3d(getX(), getY(), getZ()), new Vector3d());

            // Travel direction in shipyard space — use JOML transformDirection (rotation-only, no translation)
            Vec3 vel = getDeltaMovement();
            if (vel.lengthSqr() < 1e-6) return;
            Vec3 velN = vel.normalize();
            Vector3d fwd = new Vector3d(velN.x, velN.y, velN.z);
            w2s.transformDirection(fwd);
            fwd.normalize();

            // Perpendicular axes for 2×2 cross-section
            Vector3d worldUp = (Math.abs(fwd.y) < 0.9) ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
            Vector3d right  = fwd.cross(worldUp, new Vector3d()).normalize();
            Vector3d upPerp = fwd.cross(right,   new Vector3d()).normalize();

            int cx = (int) Math.round(podShip.x);
            int cy = (int) Math.round(podShip.y);
            int cz = (int) Math.round(podShip.z);

            int destroyed = 0;
            // 2×2×4: 1 block before hull surface (d=-1) then 3 blocks deep (d=0,1,2)
            for (int d = -1; d < 3; d++) {
                int bx = cx + (int) Math.round(fwd.x * d);
                int by = cy + (int) Math.round(fwd.y * d);
                int bz = cz + (int) Math.round(fwd.z * d);
                for (int i = -1; i <= 0; i++) {
                    for (int j = -1; j <= 0; j++) {
                        BlockPos bp = new BlockPos(
                                bx + (int) Math.round(right.x * i + upPerp.x * j),
                                by + (int) Math.round(right.y * i + upPerp.y * j),
                                bz + (int) Math.round(right.z * i + upPerp.z * j));
                        if (!sl.getBlockState(bp).isAir()) {
                            sl.destroyBlock(bp, false); // no item drops — it's a breach
                            destroyed++;
                        }
                    }
                }
            }
            LOGGER.debug("[BoardingPod] Breach at shipyard ({},{},{}) fwd({},{},{}) destroyed={}",
                    cx, cy, cz, fwd.x, fwd.y, fwd.z, destroyed);
        } catch (Exception e) {
            LOGGER.warn("[BoardingPod] Hull breach failed at world ({},{},{}): {}",
                    getX(), getY(), getZ(), e.toString());
        }
    }

    // ── RCS ───────────────────────────────────────────────────────────────────

    /**
     * Applies a sideways impulse perpendicular to the current flight direction.
     * {@code dir}: -1 = left, +1 = right.
     * Called both server-side (from tryApplyRcs) and client-side (prediction).
     */
    public void applyRcsImpulse(int dir) {
        Vec3 vel     = getDeltaMovement();
        Vec3 fwd     = vel.lengthSqr() > 1e-6 ? vel.normalize() : getLookAngle();
        Vec3 worldUp = Math.abs(fwd.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right   = vecCross(fwd, worldUp).normalize();
        setDeltaMovement(getDeltaMovement().add(right.scale(dir * RCS_THRUST)));

        // Rotate the entity to face the new velocity direction
        Vec3 newVel = getDeltaMovement();
        if (newVel.lengthSqr() > 1e-6) {
            Vec3 newDir = newVel.normalize();
            setYRot((float) Math.toDegrees(Math.atan2(-newDir.x, newDir.z)));
            setXRot((float) Math.toDegrees(Math.asin(Mth.clamp(-newDir.y, -1.0, 1.0))));
        }

        // Client-side: white cloud burst from the exhaust side
        if (level().isClientSide) {
            Vec3 exhaust = right.scale(-dir * 0.6);
            for (int i = 0; i < 6; i++) {
                level().addParticle(ParticleTypes.CLOUD,
                        getX() + exhaust.x, getY() + 0.5, getZ() + exhaust.z,
                        exhaust.x * 0.3 + (random.nextDouble() - 0.5) * 0.05,
                        (random.nextDouble() - 0.5) * 0.05,
                        exhaust.z * 0.3 + (random.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    /**
     * Server-side: validates charges / cooldown, deducts 1 charge, applies impulse.
     * Returns {@code true} on success.
     */
    public boolean tryApplyRcs(int dir) {
        if (level().isClientSide) return false;
        if (getPhase() == Phase.AIMING) return false;
        if (getRcsCharges() <= 0 || getRcsCooldown() > 0) return false;
        entityData.set(RCS_CHARGES,  getRcsCharges() - 1);
        entityData.set(RCS_COOLDOWN, RCS_COOLDOWN_TICKS);
        applyRcsImpulse(dir);
        // Pneumatic-hiss sound (pitched slightly randomly for variation)
        ServerLevel sl = (ServerLevel) level();
        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS,
                0.5f, 1.3f + (random.nextFloat() * 0.2f - 0.1f));
        return true;
    }

    private static Vec3 vecCross(Vec3 a, Vec3 b) {
        return new Vec3(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x);
    }

    // ── Entity overrides ──────────────────────────────────────────────────────

    @Override
    public boolean canBeCollidedWith() { return getPhase() == Phase.AIMING; }

    /** Pickable in all phases so projectiles can target it mid-flight. */
    @Override
    public boolean isPickable() { return true; }

    /**
     * In AIMING phase: return false (hand-breaking handled by AttackEntityEvent).
     * In BOOST/COAST: deduct HP; destroy when HP ≤ 0.
     * In DRILLING: return false (pod is embedded in the hull, not in-flight).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (getPhase() == Phase.AIMING || getPhase() == Phase.DRILLING) return false;
        podHealth -= Math.max(1, (int) amount);
        if (podHealth <= 0) {
            ServerLevel sl = (ServerLevel) level();
            sl.sendParticles(ParticleTypes.EXPLOSION,    getX(), getY(), getZ(), 2,  0.3, 0.3, 0.3, 0.1);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE,  getX(), getY(), getZ(), 15, 0.5, 0.5, 0.5, 0.02);
            if (!getPassengers().isEmpty()) {
                ejectPassengers(); // → removePassenger() → discard()
            } else {
                discard();
            }
        }
        return true;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) { return getPassengers().isEmpty(); }

    /**
     * When a passenger dismounts while the pod is in flight or drilling,
     * immediately discard the pod so it cannot be used as an unmanned torpedo.
     */
    @Override
    public void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && getPhase() != Phase.AIMING) {
            discard();
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return dev.architectury.networking.NetworkManager.createAddEntityPacket(this);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        boostTicks = tag.getInt("boostTicks");
        hitCount   = tag.getInt("hitCount");
        if (tag.contains("podHealth"))   podHealth = tag.getInt("podHealth");
        if (tag.contains("rcsCharges"))  entityData.set(RCS_CHARGES,  tag.getInt("rcsCharges"));
        if (tag.contains("drillTimer"))  entityData.set(DRILL_TIMER,  tag.getInt("drillTimer"));
        if (tag.contains("phase")) setPhase(Phase.byId(tag.getInt("phase")));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("boostTicks",  boostTicks);
        tag.putInt("hitCount",    hitCount);
        tag.putInt("podHealth",   podHealth);
        tag.putInt("rcsCharges",  getRcsCharges());
        tag.putInt("drillTimer",  getDrillTimer());
        tag.putInt("phase", getPhase().id);
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.BOARDING_POD_COCKPIT.get());
    }
}
