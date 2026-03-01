package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity
import com.mechanicalskies.vsshields.registry.ModBlocks
import com.mechanicalskies.vsshields.registry.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.internal.joints.VSFixedJoint
import org.valkyrienskies.core.internal.joints.VSJointPose
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.assembly.ShipAssembler
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider
import org.valkyrienskies.mod.common.dimensionId
import org.valkyrienskies.mod.common.getShipsIntersecting
import org.valkyrienskies.mod.common.shipObjectWorld
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Forge-side singleton that manages all active VS2 boarding pod ships.
 *
 * Lifecycle:
 *  - [register] called from BoardingPodCockpitBlock.use() after ShipAssembler assembly
 *  - [fire] called via CockpitSeatEntity.fireCallback on BOARDING_POD_FIRE packet
 *  - [tryRcs] called via CockpitSeatEntity.rcsCallback on BOARDING_POD_RCS packet
 *  - [onLevelTick] ticks all pods each game tick; handles phase transitions + cleanup
 *
 * Phase logic mirrors BoardingPodEntity's state machine but operates on VS2 ships
 * using GTPA forces instead of entity.setDeltaMovement().
 */
object PodShipManager {

    data class PodState(
        val seatEntityId: Int,
        val podShipId: Long,
        val ignoredShipId: Long,
        /** Dimension in which this pod lives — used to skip ticks from other dimensions. */
        val dimensionId: String,
        var phase: CockpitSeatEntity.Phase = CockpitSeatEntity.Phase.AIMING,
        /** Cached velocity (m/s in VS2 units) — updated from VS2 physics each tick. */
        var velocity: Vector3d = Vector3d(),
        /**
         * Fixed normalized launch direction — aim direction at the moment FIRE was pressed.
         * Used as the thrust axis for BOOST and the lateral axis for RCS.
         * Never overwritten after fire(); stable reference regardless of physics drift.
         */
        var launchDir: Vector3d = Vector3d(0.0, 0.0, 1.0),
        var drillingShipId: Long = Long.MIN_VALUE,
        /** Contact point in target ship's shipyard space. */
        var drillingLocalPos: Vector3d? = null,
        /** Normalized world-space approach direction at drill start. */
        var drillingDirection: Vector3d? = null,
        /**
         * Latest player look yaw/pitch (degrees) received each tick via BOARDING_POD_RCS packet.
         * Used by applyMouseSteering() to rotate launchDir at ≤STEER_RATE_DEG per tick.
         */
        var steerYaw: Float = 0f,
        var steerPitch: Float = 0f,
        /**
         * True while the client is holding Space — enables thrust in BOOST phase.
         * Set by tryRcs() from the BOARDING_POD_RCS packet each tick.
         */
        var boostActive: Boolean = false,
        /** Counts ticks from the first BOOST tick (used for liftoff + first-tick detection). */
        var boostPhaseTick: Int = 0,
        /**
         * Remaining fuel ticks.  Starts at [BOOST_TICKS] = 80.  Decrements only while
         * [boostActive] == true.  When it hits 0 the phase transitions to COAST.
         */
        var boostFuelTicks: Int = BOOST_TICKS,
        /** World position at fire time — used to abort flight if pod drifts too far. */
        var launchPos: Vector3d = Vector3d(),
        /**
         * VS2 joint ID of the VSFixedJoint that attaches the pod to the target ship
         * during DRILLING.  null until startDrilling() gets the callback from addJoint().
         * Must be removed via gtpa.removeJoint() when drilling ends.
         */
        var drillingJointId: Int? = null,
        /**
         * Eject position in target ship's shipyard (block) space, computed in
         * startDrilling() at the moment of contact — before any hull shake or movement.
         * Converted to world coords in breach() via shipToWorld.transformPosition().
         */
        var precomputedEjectLocal: Vector3d? = null,
        /**
         * Pre-computed breach tunnel: 4 depth layers, each containing the 2×2 = 4 block
         * positions for that depth slice, in target ship's shipyard coordinates.
         * Computed in [startDrilling] at contact time; one layer is broken every
         * [DRILL_TICKS] / 4 = 10 ticks during [tickDrilling].
         * null → fallback to [breachHull] at breach time (should not normally happen).
         */
        var breachLayers: List<List<BlockPos>>? = null,
        /** Index of the next layer in [breachLayers] that has not yet been broken. */
        var breachLayerIndex: Int = 0,
        /**
         * Seat entity's world position mapped into shipyard space, computed once on the first tick
         * after registration, then synced to the client as a String via COCKPIT_SY_POS.
         * Passed as the constant mountPosInShip by MixinCockpitSeatShipMount so VS2 uses
         * renderTransform × constant for perfectly smooth camera at any speed.
         */
        var cockpitShipyardPos: Vector3d? = null,
        /**
         * Normalized horizontal world-space vector in the cockpit block's FACING direction.
         * Set at registration time from the block's Direction ordinal; used by [fire] as the
         * initial launchDir so the pod always launches nose-forward regardless of player look.
         */
        val launchFacingDir: Vector3d = Vector3d(0.0, 0.0, 1.0),
    )

    private val pods = ConcurrentHashMap<Long, PodState>()

    // ── Physics constants ─────────────────────────────────────────────────────
    private const val BOOST_TICKS      = 80    // total boost duration (4 s at 20 tps)
    /** Ticks over which speed ramps from 0 → TARGET_SPEED (3 s). */
    private const val BOOST_RAMP_TICKS = 60
    /** Peak cruise speed in m/s.
     *  40 m/s = 2 blocks/tick.  Reached at tick 60 of BOOST; held to tick 80. */
    private const val TARGET_SPEED     = 40.0
    /**
     * Velocity proportional-control gain (N per m/s error).
     * α = KP*dt/M with dt=0.05, M≈2000 kg gives α=0.75 → stable, ~3-tick convergence.
     * Used in BOOST and COAST — self-correcting, no mass estimation needed for COAST.
     */
    private const val VEL_KP           = 30_000.0
    /**
     * Anti-gravity bias: computed dynamically as podShip.inertiaData.mass * 9.81 in each tick
     * so it scales with actual pod size regardless of block count.
     */
    /**
     * Extra upward force applied for the first [LIFTOFF_TICKS] BOOST ticks.
     * Breaks contact-constraint equilibrium so the pod lifts off any surface.
     * 15 000 N on a 2000 kg pod ≈ +7.5 m/s² net → ~0.2 m clearance in 5 ticks.
     */
    private const val LIFTOFF_FORCE    = 15_000.0
    private const val LIFTOFF_TICKS    = 5
    /**
     * Angular velocity damping coefficient applied during BOOST and COAST.
     * Torque = -ANGULAR_DRAG × mass × ω  keeps the pod flying nose-forward.
     * Scales with ship mass so it works for any pod size.
     */
    private const val ANGULAR_DRAG   = 10.0   // angular damping — keeps pod flying nose-forward
    /** Max steering rotation per tick in degrees (3°/tick = 60°/s). */
    private const val STEER_RATE_DEG = 3.0
    /** Abort flight if pod travels further than this from its launch point (blocks). */
    private const val MAX_RANGE      = 100.0
    private const val DRILL_TICKS      = 40

    // ── Registry ──────────────────────────────────────────────────────────────

    fun register(podShipId: Long, seatEntityId: Int, ignoredShipId: Long, dimensionId: String, facingOrdinal: Int) {
        // Convert Direction ordinal to a normalized horizontal world-space vector.
        // Direction ordinals: DOWN=0 UP=1 NORTH=2(0,0,-1) SOUTH=3(0,0,1) WEST=4(-1,0,0) EAST=5(1,0,0)
        val facing = net.minecraft.core.Direction.from3DDataValue(facingOrdinal)
        val facingDir = Vector3d(facing.stepX.toDouble(), 0.0, facing.stepZ.toDouble()).normalize()

        pods[podShipId] = PodState(
            seatEntityId   = seatEntityId,
            podShipId      = podShipId,
            ignoredShipId  = ignoredShipId,
            dimensionId    = dimensionId,
            launchFacingDir = facingDir
        )
    }

    fun unregister(podShipId: Long) { pods.remove(podShipId) }

    fun isTrustedPodShip(shipId: Long): Boolean = pods.containsKey(shipId)

    // ── Network callbacks (set from VSShieldsModForge.init) ───────────────────

    /** Called via CockpitSeatEntity.fireCallback. */
    fun fire(seatEntityId: Int, yaw: Float, pitch: Float) {
        val state = pods.values.find { it.seatEntityId == seatEntityId } ?: return
        if (state.phase != CockpitSeatEntity.Phase.AIMING) return
        // Use the cockpit block's FACING direction — pod always launches nose-forward.
        // Ignore player yaw/pitch for initial direction (player can still steer with mouse after launch).
        val dir = Vector3d(state.launchFacingDir)
        // Fix launch direction at fire time — thrust and RCS axes stay locked to this
        state.launchDir  = dir
        state.velocity   = Vector3d(dir)   // unit vec; speed built up by tickBoost
        state.phase           = CockpitSeatEntity.Phase.BOOST
        state.boostPhaseTick  = 0
        state.boostFuelTicks  = BOOST_TICKS
        state.boostActive     = false
        state.drillingShipId        = Long.MIN_VALUE
        state.drillingLocalPos      = null
        state.drillingDirection     = null
        state.drillingJointId       = null
        state.precomputedEjectLocal = null
        state.breachLayers          = null
        state.breachLayerIndex      = 0
    }

    /** Called via CockpitSeatEntity.rcsCallback every client tick during BOOST/COAST.
     *  Stores yaw/pitch for mouse steering; [boostActive]==1 means Space held → thrust. */
    fun tryRcs(seatEntityId: Int, yaw: Float, pitch: Float, boostActive: Int) {
        val state = pods.values.find { it.seatEntityId == seatEntityId } ?: return
        if (state.phase == CockpitSeatEntity.Phase.AIMING ||
            state.phase == CockpitSeatEntity.Phase.DRILLING) return
        state.steerYaw   = yaw
        state.steerPitch = pitch
        if (state.phase == CockpitSeatEntity.Phase.BOOST)
            state.boostActive = boostActive != 0
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    @SubscribeEvent
    fun onLevelTick(event: TickEvent.LevelTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val level = event.level as? ServerLevel ?: return
        if (level.isClientSide) return

        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)

        val toRemove = ArrayList<Long>()

        for ((podShipId, state) in pods) {
            // Only process this pod in its home dimension — skip all others to avoid false cleanup
            if (level.dimensionId != state.dimensionId) continue

            val podShip = level.shipObjectWorld.loadedShips.getById(podShipId) as? LoadedServerShip
            val seat    = level.getEntity(state.seatEntityId) as? CockpitSeatEntity

            if (podShip == null) {
                // Ship unloaded or deleted — eject player before discarding seat
                seat?.let { s ->
                    s.ejectPassengers()
                    if (!s.isRemoved) s.discard()
                }
                toRemove.add(podShipId)
                continue
            }

            if (seat == null || seat.isRemoved) {
                // Seat was discarded (player dismounted or breach completed).
                // AIMING uses setStatic — release it.
                if (state.phase == CockpitSeatEntity.Phase.AIMING) {
                    gtpa.setStatic(state.podShipId, false)
                }
                // DRILLING uses a VSFixedJoint — remove it and re-enable collision.
                if (state.phase == CockpitSeatEntity.Phase.DRILLING) {
                    state.drillingJointId?.let { jid ->
                        try { gtpa.removeJoint(jid) } catch (_: Exception) {}
                    }
                    try { gtpa.enableCollisionBetween(state.podShipId, state.drillingShipId) } catch (_: Exception) {}
                }
                destroyPodShip(podShip, state, level)
                toRemove.add(podShipId)
                continue
            }

            // Compute cockpit shipyard pos once on the first tick (world → ship transform).
            // Synced to client as COCKPIT_SY_POS string; used by MixinCockpitSeatShipMount
            // as a stable constant mountPosInShip so VS2's renderTransform × constant is smooth.
            if (state.cockpitShipyardPos == null) {
                state.cockpitShipyardPos = podShip.worldToShip
                    .transformPosition(Vector3d(seat.x, seat.y, seat.z), Vector3d())
                val cp = state.cockpitShipyardPos!!
                seat.setCockpitShipyardPosStr("${cp.x},${cp.y},${cp.z}")
            }

            // VS2-native entity tracking: drag the seat with the pod ship each tick.
            // Keeps the seat entity at the correct world position via VS2's EntityDragger.
            // Must be called every tick — dragging expires after TICKS_TO_DRAG_ENTITIES (25) ticks.
            (seat as? IEntityDraggingInformationProvider)?.`vs$dragImmediately`(podShip)

            // Sync pod speed to seat entity so the client HUD can display it
            seat.setSpeedMps(podShip.velocity.length().roundToInt())

            when (state.phase) {
                CockpitSeatEntity.Phase.AIMING   -> tickAiming(state, podShip, gtpa)
                CockpitSeatEntity.Phase.BOOST    -> tickBoost(state, podShip, seat, gtpa, level)
                CockpitSeatEntity.Phase.COAST    -> tickCoast(state, podShip, seat, gtpa, level)
                CockpitSeatEntity.Phase.DRILLING -> tickDrilling(state, podShip, seat, gtpa, level, toRemove)
            }
        }

        toRemove.forEach { pods.remove(it) }
    }

    private fun destroyPodShip(podShip: LoadedServerShip, state: PodState, level: ServerLevel) {
        val pos = podShip.transform.positionInWorld
        if (state.phase == CockpitSeatEntity.Phase.AIMING) {
            // Drop cockpit + engine items
            fun drop(item: net.minecraft.world.item.Item) =
                level.addFreshEntity(ItemEntity(level, pos.x(), pos.y() + 0.3, pos.z(),
                    ItemStack(item)))
            drop(ModItems.BOARDING_POD_COCKPIT.get())
            drop(ModItems.BOARDING_POD_ENGINE.get())
            // Delete VS2 ship cleanly (no explosion in AIMING)
            try {
                ShipAssembler.deleteShip(level, podShip, true, false)
            } catch (e: Exception) {
                // Fallback: small explosion
                level.explode(null, pos.x(), pos.y(), pos.z(), 1.0f, Level.ExplosionInteraction.BLOCK)
            }
        } else {
            // Visual-only explosion (sound + particles) — no block damage, no item drops
            level.explode(null, pos.x(), pos.y(), pos.z(), 5.0f, Level.ExplosionInteraction.NONE)
            // Silently remove the VS2 ship (no block drops)
            try { ShipAssembler.deleteShip(level, podShip, false, false) } catch (_: Exception) {}
        }
    }

    // ── AIMING (hover) ────────────────────────────────────────────────────────

    /**
     * Freezes the pod ship in place while the player is aiming.
     *
     * Using setStatic(true) is numerically stable for any ship mass,
     * and completely prevents the upward-pull issue caused by force-based
     * anti-gravity overcompensation.  The static lock is released by
     * tickBoost() on the first BOOST tick when the player fires.
     */
    private fun tickAiming(state: PodState, podShip: LoadedServerShip,
                           gtpa: GameToPhysicsAdapter) {
        gtpa.setStatic(state.podShipId, true)
    }

    // ── BOOST ─────────────────────────────────────────────────────────────────

    private fun tickBoost(state: PodState, podShip: LoadedServerShip,
                          seat: CockpitSeatEntity, gtpa: GameToPhysicsAdapter,
                          level: ServerLevel) {
        val pos  = podShip.transform.positionInWorld
        val mass = podShip.inertiaData.mass

        // Release static lock and save launch position on the very first BOOST tick
        if (state.boostPhaseTick == 0) {
            gtpa.setStatic(state.podShipId, false)
            state.launchPos = Vector3d(pos.x(), pos.y(), pos.z())
        }
        state.boostPhaseTick++

        // Range abort — destroy pod if it drifted too far from launch point
        val lp = state.launchPos
        val distSq = (pos.x() - lp.x) * (pos.x() - lp.x) +
                     (pos.y() - lp.y) * (pos.y() - lp.y) +
                     (pos.z() - lp.z) * (pos.z() - lp.z)
        if (distSq > MAX_RANGE * MAX_RANGE) {
            seat.ejectPassengers(); seat.discard(); return
        }

        val curVel  = podShip.velocity
        val antiGrav = mass * 9.81
        // Liftoff burst: first few ticks after launch regardless of Space state
        val liftoff = if (state.boostPhaseTick <= LIFTOFF_TICKS) LIFTOFF_FORCE else 0.0

        // Steering: rotate launchDir toward player look direction ≤STEER_RATE_DEG/tick
        applyMouseSteering(state)

        if (state.boostActive && state.boostFuelTicks > 0) {
            // ── Thrusting (Space held, fuel available) ───────────────────────
            val fuelConsumed = (BOOST_TICKS - state.boostFuelTicks).toDouble()
            val t = (fuelConsumed / BOOST_RAMP_TICKS).coerceIn(0.0, 1.0)
            val desiredVel = Vector3d(state.launchDir).mul(TARGET_SPEED * t)
            gtpa.applyInvariantForce(state.podShipId, Vector3d(
                (desiredVel.x - curVel.x()) * VEL_KP,
                (desiredVel.y - curVel.y()) * VEL_KP + antiGrav + liftoff,
                (desiredVel.z - curVel.z()) * VEL_KP
            ))

            state.boostFuelTicks--
            seat.setBoostFuel(state.boostFuelTicks)
            if (state.boostFuelTicks % 4 == 0) {
                level.playSound(null, pos.x(), pos.y(), pos.z(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
                    0.9f, 0.85f + level.random.nextFloat() * 0.3f)
            }
            if (state.boostFuelTicks <= 0) {
                state.phase = CockpitSeatEntity.Phase.COAST
                seat.setPhase(CockpitSeatEntity.Phase.COAST)
            }
        } else {
            // ── Gliding (Space released or no fuel) ──────────────────────────
            val speed = Vector3d(curVel.x(), 0.0, curVel.z()).length().coerceAtLeast(0.1)
            val desiredVel = Vector3d(state.launchDir).mul(speed)
            gtpa.applyInvariantForce(state.podShipId, Vector3d(
                (desiredVel.x - curVel.x()) * VEL_KP,
                antiGrav + liftoff,
                (desiredVel.z - curVel.z()) * VEL_KP
            ))
        }

        val omega = podShip.angularVelocity
        gtpa.applyInvariantTorque(state.podShipId, Vector3d(
            -ANGULAR_DRAG * mass * omega.x(),
            -ANGULAR_DRAG * mass * omega.y(),
            -ANGULAR_DRAG * mass * omega.z()
        ))

        state.velocity = Vector3d(curVel)
        seat.setPhase(CockpitSeatEntity.Phase.BOOST)
        checkShipCollision(state, podShip, level)
    }

    // ── COAST ─────────────────────────────────────────────────────────────────

    private fun tickCoast(state: PodState, podShip: LoadedServerShip,
                          seat: CockpitSeatEntity, gtpa: GameToPhysicsAdapter,
                          level: ServerLevel) {
        val pos  = podShip.transform.positionInWorld
        val mass = podShip.inertiaData.mass

        // Range abort — destroy pod if it drifted too far from launch point
        val lp = state.launchPos
        val distSq = (pos.x() - lp.x) * (pos.x() - lp.x) +
                     (pos.y() - lp.y) * (pos.y() - lp.y) +
                     (pos.z() - lp.z) * (pos.z() - lp.z)
        if (distSq > MAX_RANGE * MAX_RANGE) {
            seat.ejectPassengers()
            seat.discard()
            return
        }

        state.velocity = Vector3d(podShip.velocity)
        seat.setPhase(CockpitSeatEntity.Phase.COAST)

        // Steering: rotate launchDir toward player look direction
        applyMouseSteering(state)

        // Glide at current speed; maintain XZ direction via launchDir; antiGrav on Y.
        val curVelCoast = podShip.velocity
        val coastSpeed = Vector3d(curVelCoast.x(), 0.0, curVelCoast.z()).length().coerceAtLeast(0.1)
        val desiredVelCoast = Vector3d(state.launchDir).mul(coastSpeed)
        gtpa.applyInvariantForce(state.podShipId, Vector3d(
            (desiredVelCoast.x - curVelCoast.x()) * VEL_KP,
            mass * 9.81,
            (desiredVelCoast.z - curVelCoast.z()) * VEL_KP
        ))

        val omega = podShip.angularVelocity
        gtpa.applyInvariantTorque(state.podShipId, Vector3d(
            -ANGULAR_DRAG * mass * omega.x(),
            -ANGULAR_DRAG * mass * omega.y(),
            -ANGULAR_DRAG * mass * omega.z()
        ))

        checkShipCollision(state, podShip, level)

        if (pos.y() < level.minBuildHeight.toDouble() - 30.0) {
            seat.ejectPassengers()
            seat.discard()
        }
    }

    // ── Mouse steering ────────────────────────────────────────────────────────

    /**
     * Rotates [PodState.launchDir] toward the player's current look direction at a
     * maximum of [STEER_RATE_DEG] degrees per tick.  This replaces RCS + magnetic align:
     * the VEL_KP controller then naturally follows the updated launchDir.
     *
     * When the angular delta is ≤ STEER_RATE_DEG the pod snaps exactly to the target.
     * At larger angles it turns at exactly STEER_RATE_DEG/tick regardless of deviation.
     */
    private fun applyMouseSteering(state: PodState) {
        val yaw = Math.toRadians(state.steerYaw.toDouble())
        val pit = Math.toRadians(state.steerPitch.toDouble())
        val desiredDir = Vector3d(
            -sin(yaw) * cos(pit),
            -sin(pit),
             cos(yaw) * cos(pit)
        ).normalize()

        val cosAngle = state.launchDir.dot(desiredDir).coerceIn(-1.0, 1.0)
        if (cosAngle >= 1.0 - 1e-6) return  // already aligned

        val angleDeg = Math.toDegrees(acos(cosAngle))
        val t = (STEER_RATE_DEG / angleDeg).coerceIn(0.0, 1.0)
        state.launchDir = Vector3d(state.launchDir).lerp(desiredDir, t).normalize()
    }

    // ── Collision check (AABB overlap) ────────────────────────────────────────

    private fun checkShipCollision(state: PodState, podShip: LoadedServerShip,
                                    level: ServerLevel) {
        val podAABB = podShip.worldAABB ?: return
        // Convert to MC AABB for getShipsIntersecting — it does the overlap check for us
        val podAABBMc = AABB(podAABB.minX(), podAABB.minY(), podAABB.minZ(),
                             podAABB.maxX(), podAABB.maxY(), podAABB.maxZ())

        for (ship in level.getShipsIntersecting(podAABBMc)) {
            if (ship.id == state.podShipId || ship.id == state.ignoredShipId) continue
            val targetShip = level.shipObjectWorld.loadedShips.getById(ship.id) as? LoadedServerShip ?: continue
            startDrilling(state, podShip, targetShip, level)
            return
        }
    }

    // ── DRILLING start ────────────────────────────────────────────────────────

    private fun startDrilling(state: PodState, podShip: LoadedServerShip,
                               targetShip: LoadedServerShip, level: ServerLevel) {
        val podPos    = podShip.transform.positionInWorld
        val podPosVec = Vector3d(podPos.x(), podPos.y(), podPos.z())

        // Approach direction — the world-space direction the pod was flying when it hit
        val approachDir = if (state.velocity.lengthSquared() > 1e-6)
            Vector3d(state.velocity).normalize() else Vector3d(state.launchDir)

        // Drilling origin = pod center mapped into target ship's shipyard space.
        // This is identical to the VSFixedJoint attachment point, so breachHull and
        // computeEjectLocal ray-marches begin at the exact physical contact site.
        // Eliminates the fragile back-ray that could miss angled or fast-moving hulls.
        val w2s            = targetShip.worldToShip
        val attachLocalPos = w2s.transformPosition(podPosVec, Vector3d())

        state.drillingLocalPos  = attachLocalPos
        state.drillingDirection = Vector3d(approachDir)
        state.drillingShipId    = targetShip.id
        state.phase             = CockpitSeatEntity.Phase.DRILLING

        (level.getEntity(state.seatEntityId) as? CockpitSeatEntity)?.setDrillTimer(DRILL_TICKS)

        // Precompute eject position in target's shipyard space at contact time.
        // Converted to world coords in breach() so ship movement during drilling is handled.
        state.precomputedEjectLocal = computeEjectLocal(level, targetShip, attachLocalPos, approachDir)

        // Precompute the 4 breach layers so they can be broken progressively during tickDrilling.
        state.breachLayers     = precomputeBreachLayers(level, targetShip, attachLocalPos, approachDir)
        state.breachLayerIndex = 0

        // Attach pod rigidly to target ship via VSFixedJoint.
        // The joint attachment point on the target IS attachLocalPos — no extra computation.
        val poseOnPod    = VSJointPose(podShip.worldToShip.transformPosition(podPosVec, Vector3d()), Quaterniond())
        val poseOnTarget = VSJointPose(attachLocalPos, Quaterniond())
        val gtpa = ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
        gtpa.addJoint(
            VSFixedJoint(state.podShipId, poseOnPod, targetShip.id, poseOnTarget),
            delay = 0
        ) { id -> state.drillingJointId = id }

        // Disable collision so hull blocks don't push the pod away during drilling.
        try { gtpa.disableCollisionBetween(state.podShipId, targetShip.id) } catch (_: Exception) {}

        level.playSound(null, podPos.x(), podPos.y(), podPos.z(),
            SoundEvents.IRON_GOLEM_HURT, SoundSource.PLAYERS, 1.2f, 0.7f)
    }

    // ── DRILLING tick ─────────────────────────────────────────────────────────

    private fun tickDrilling(state: PodState, podShip: LoadedServerShip,
                              seat: CockpitSeatEntity, gtpa: GameToPhysicsAdapter,
                              level: ServerLevel,
                              toRemove: ArrayList<Long>) {
        val targetShip = level.shipObjectWorld.loadedShips.getById(state.drillingShipId) as? LoadedServerShip

        if (targetShip == null) {
            // Target ship gone — remove joint, re-enable collision and eject
            state.drillingJointId?.let { jid ->
                try { gtpa.removeJoint(jid) } catch (_: Exception) {}
            }
            try { gtpa.enableCollisionBetween(state.podShipId, state.drillingShipId) } catch (_: Exception) {}
            seat.ejectPassengers()
            seat.discard()
            return
        }

        // Pod is attached via VSFixedJoint — no forces needed; joint holds position.
        seat.setPhase(CockpitSeatEntity.Phase.DRILLING)

        val timer = seat.getDrillTimer()
        val pos = podShip.transform.positionInWorld

        // Progressive hull breach: break one 2×2 depth layer every (DRILL_TICKS / 4) ticks.
        // elapsed=0 on the first drilling tick (timer==DRILL_TICKS), rises to DRILL_TICKS-1.
        val elapsed = DRILL_TICKS - timer
        val targetLayerIdx = elapsed / (DRILL_TICKS / 4)  // 0, 1, 2, 3
        val layers = state.breachLayers
        if (layers != null &&
            state.breachLayerIndex <= targetLayerIdx &&
            state.breachLayerIndex < layers.size) {
            val layer = layers[state.breachLayerIndex]
            for (bp in layer) {
                if (!level.getBlockState(bp).isAir) level.destroyBlock(bp, false)
            }
            level.playSound(null, pos.x(), pos.y(), pos.z(),
                SoundEvents.STONE_BREAK, SoundSource.PLAYERS,
                1.0f, 0.65f + level.random.nextFloat() * 0.3f)
            state.breachLayerIndex++
        }

        seat.setDrillTimer(timer - 1)

        if (timer % 8 == 0) {
            level.playSound(null, pos.x(), pos.y(), pos.z(),
                SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS,
                0.9f, 0.7f + level.random.nextFloat() * 0.4f)
        }

        if (timer <= 1) {
            breach(state, podShip, targetShip, seat, level, gtpa)
            toRemove.add(state.podShipId)
        }
    }

    // ── Breach ────────────────────────────────────────────────────────────────

    private fun breach(state: PodState, podShip: LoadedServerShip,
                        targetShip: LoadedServerShip, seat: CockpitSeatEntity,
                        level: ServerLevel, gtpa: GameToPhysicsAdapter) {
        val podPos = podShip.transform.positionInWorld

        // Remove fixed joint and re-enable collision (both set in startDrilling)
        state.drillingJointId?.let { jid ->
            try { gtpa.removeJoint(jid) } catch (_: Exception) {}
        }
        try { gtpa.enableCollisionBetween(state.podShipId, targetShip.id) } catch (_: Exception) {}

        val passenger         = seat.passengers.firstOrNull()
        val drillingDirection = state.drillingDirection

        // ── Eject position: precomputed at contact time in target's shipyard space ──
        // Converting to world coords NOW (at breach time) accounts for any ship movement
        // that occurred during the 40-tick drilling window — correct world pos every time.
        val ejectWorld: Vector3d? = state.precomputedEjectLocal
            ?.let { local -> targetShip.shipToWorld.transformPosition(local, Vector3d()) }

        // Trust passenger through solid shield (200 ticks)
        if (passenger != null) {
            CockpitSeatEntity.getTrustCallback()
                ?.trustPassenger(passenger.uuid, level.gameTime, 200)
        }

        // Breach hull in target ship — layers were broken progressively during tickDrilling;
        // only fall back to breachHull() if precomputeBreachLayers() returned nothing.
        val drillingLocalPos = state.drillingLocalPos
        if (drillingLocalPos != null && drillingDirection != null && state.breachLayers.isNullOrEmpty()) {
            breachHull(level, targetShip, drillingLocalPos, drillingDirection)
        }

        // Teleport passenger into the pre-computed tunnel position
        if (passenger != null && ejectWorld != null) {
            passenger.stopRiding()
            passenger.teleportTo(ejectWorld.x, ejectWorld.y + 0.3, ejectWorld.z)

            // Face breach direction
            if (drillingDirection != null) {
                passenger.setYRot(Math.toDegrees(atan2(-drillingDirection.x, drillingDirection.z)).toFloat())
                passenger.setXRot(Math.toDegrees(asin(-drillingDirection.y.coerceIn(-1.0, 1.0))).toFloat())
            }
            passenger.deltaMovement = net.minecraft.world.phys.Vec3.ZERO
            passenger.fallDistance = 0f
        }

        // Visual effects — smoke/sparks without explosion damage
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
            podPos.x(), podPos.y(), podPos.z(), 20, 0.5, 0.5, 0.5, 0.02)
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
            podPos.x(), podPos.y(), podPos.z(), 12, 0.4, 0.4, 0.4, 0.3)

        // Silently remove the pod ship (no explosion, no item drops)
        try { ShipAssembler.deleteShip(level, podShip, false, false) } catch (_: Exception) {}

        seat.discard()
    }

    // ── Eject position pre-computation ────────────────────────────────────────

    /**
     * Computes the player eject position in the target ship's shipyard (block) space.
     * Called from [startDrilling] at contact time so the result is stable regardless
     * of ship movement during the 40-tick drilling window.
     *
     * Ray-marches from [drillingLocalPos] along [worldDrillingDir] (converted to
     * shipyard space) to find the first solid hull block, then returns a point
     * 3 blocks further inside the hull — a safe interior landing spot.
     *
     * @return position in shipyard space, or null if no solid block was found
     */
    private fun computeEjectLocal(level: ServerLevel, targetShip: LoadedServerShip,
                                   drillingLocalPos: Vector3d, worldDrillingDir: Vector3d): Vector3d? {
        val fwdShip = targetShip.worldToShip
            .transformDirection(Vector3d(worldDrillingDir), Vector3d()).normalize()
        for (step in -6..8) {
            val tx = Math.round(drillingLocalPos.x + fwdShip.x * step).toInt()
            val ty = Math.round(drillingLocalPos.y + fwdShip.y * step).toInt()
            val tz = Math.round(drillingLocalPos.z + fwdShip.z * step).toInt()
            if (!level.getBlockState(BlockPos(tx, ty, tz)).isAir) {
                // Land 3 blocks past the first solid block (inside the hull interior)
                return Vector3d(tx + fwdShip.x * 3.0, ty + fwdShip.y * 3.0, tz + fwdShip.z * 3.0)
            }
        }
        // Fallback: 2 blocks past contact (nothing solid found in search range)
        return Vector3d(drillingLocalPos).add(Vector3d(fwdShip).mul(2.0))
    }

    // ── Breach layer pre-computation ──────────────────────────────────────────

    /**
     * Pre-computes the 4 depth layers of a 2×2×4 breach tunnel in the target ship's
     * shipyard coordinate space.  Called once from [startDrilling] so the block positions
     * are stable even if the target ship moves or rotates during the 40-tick drilling window.
     *
     * Uses the same ray-march as [breachHull] to find the first solid contact block, then
     * returns four lists of [BlockPos] (one per depth slice, each with up to 4 blocks).
     */
    private fun precomputeBreachLayers(level: ServerLevel, targetShip: LoadedServerShip,
                                        localPos: Vector3d, worldDir: Vector3d): List<List<BlockPos>> {
        return try {
            val fwd = targetShip.worldToShip.transformDirection(Vector3d(worldDir), Vector3d()).normalize()

            // Ray-march to find first solid block (same search range as breachHull)
            var hitX = 0; var hitY = 0; var hitZ = 0
            var foundBlock = false
            for (step in -6..8) {
                val tx = Math.round(localPos.x + fwd.x * step).toInt()
                val ty = Math.round(localPos.y + fwd.y * step).toInt()
                val tz = Math.round(localPos.z + fwd.z * step).toInt()
                if (!level.getBlockState(BlockPos(tx, ty, tz)).isAir) {
                    hitX = tx; hitY = ty; hitZ = tz
                    foundBlock = true
                    break
                }
            }
            if (!foundBlock) return emptyList()

            val worldUp = if (abs(fwd.y) < 0.9) Vector3d(0.0, 1.0, 0.0)
                          else                   Vector3d(1.0, 0.0, 0.0)
            val right  = worldUp.cross(fwd,  Vector3d()).normalize()
            val upPerp = fwd.cross   (right, Vector3d()).normalize()

            // Build 4 depth layers, each containing the 2×2 cross-section at that depth
            (0..3).map { d ->
                val bx = hitX + Math.round(fwd.x * d).toInt()
                val by = hitY + Math.round(fwd.y * d).toInt()
                val bz = hitZ + Math.round(fwd.z * d).toInt()
                listOf(-1, 0).flatMap { i ->
                    listOf(-1, 0).map { j ->
                        BlockPos(
                            bx + Math.round(right.x * i + upPerp.x * j).toInt(),
                            by + Math.round(right.y * i + upPerp.y * j).toInt(),
                            bz + Math.round(right.z * i + upPerp.z * j).toInt()
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Hull breach ───────────────────────────────────────────────────────────

    /**
     * Destroys a 2×2 tunnel 4 blocks deep in the target ship's hull.
     * All positions are in the target ship's shipyard (block) coordinate space.
     *
     * Root cause of previous failure: [localPos] is the pod centre at the moment
     * AABB overlap was detected — this is often in *air* (the bounding box is larger
     * than the actual hull geometry).
     *
     * Fix: ray-march along [worldDir] (converted to shipyard space) from a range of
     * ±8 blocks around [localPos] until the first solid block is found, then dig the
     * 2×2×4 tunnel starting from that contact point.
     */
    private fun breachHull(level: ServerLevel, targetShip: LoadedServerShip,
                            localPos: Vector3d, worldDir: Vector3d) {
        try {
            // Forward direction in shipyard space
            val fwd = targetShip.worldToShip.transformDirection(Vector3d(worldDir), Vector3d())
            fwd.normalize()

            // ── Ray-march to find the first solid block ───────────────────────
            // Search from 6 blocks behind localPos to 8 blocks ahead so we catch
            // cases where the pod centre entered the AABB before hitting a block
            // AND cases where the pod was already several blocks deep at freeze time.
            var hitX = 0; var hitY = 0; var hitZ = 0
            var foundBlock = false
            search@ for (step in -6..8) {
                val tx = Math.round(localPos.x + fwd.x * step).toInt()
                val ty = Math.round(localPos.y + fwd.y * step).toInt()
                val tz = Math.round(localPos.z + fwd.z * step).toInt()
                if (!level.getBlockState(BlockPos(tx, ty, tz)).isAir) {
                    hitX = tx; hitY = ty; hitZ = tz
                    foundBlock = true
                    break@search
                }
            }
            if (!foundBlock) return   // nothing solid in search range — bail

            // ── Perpendicular axes for 2×2 cross-section ─────────────────────
            val worldUp = if (abs(fwd.y) < 0.9) Vector3d(0.0, 1.0, 0.0)
                          else                   Vector3d(1.0, 0.0, 0.0)
            val right  = worldUp.cross(fwd,   Vector3d()).normalize()
            val upPerp = fwd.cross   (right,  Vector3d()).normalize()

            // ── Destroy 2×2×4 tunnel ──────────────────────────────────────────
            for (d in 0..3) {
                val bx = hitX + Math.round(fwd.x * d).toInt()
                val by = hitY + Math.round(fwd.y * d).toInt()
                val bz = hitZ + Math.round(fwd.z * d).toInt()
                for (i in -1..0) {
                    for (j in -1..0) {
                        val bp = BlockPos(
                            bx + Math.round(right.x * i + upPerp.x * j).toInt(),
                            by + Math.round(right.y * i + upPerp.y * j).toInt(),
                            bz + Math.round(right.z * i + upPerp.z * j).toInt()
                        )
                        if (!level.getBlockState(bp).isAir) {
                            level.destroyBlock(bp, false)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Non-fatal — breach failed silently
        }
    }
}
