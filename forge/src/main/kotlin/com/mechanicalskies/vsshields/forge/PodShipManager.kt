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
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.joml.Quaterniond
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.LoadedServerShip
import org.valkyrienskies.core.internal.joints.VSFixedJoint
import org.valkyrienskies.core.internal.joints.VSJointPose
import org.valkyrienskies.mod.common.ValkyrienSkiesMod
import org.valkyrienskies.mod.common.assembly.ShipAssembler
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
        var boostTicks: Int = 0,
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
        /** Pending lateral RCS direction (+1 = right, -1 = left, 0 = none). */
        var pendingRcs: Int = 0,
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
        /**
         * Decaying lateral/vertical offset added to desiredVel each tick.
         * Set by applyPendingRcs() and decays by RCS_BURST_DECAY per tick.
         * Gives a physical "momentum burst" feel without permanently changing launchDir.
         */
        var rcsOffset: Vector3d = Vector3d(),
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
        var precomputedEjectLocal: Vector3d? = null
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
    private const val ANGULAR_DRAG        = 10.0   // doubled — eliminates tumbling in flight
    private const val MAG_LOCK_RANGE      = 7.0
    private const val MAG_LOCK_LERP       = 0.25   // slightly smoother alignment
    /** Lateral burst speed added to rcsOffset per RCS fire (m/s). */
    private const val RCS_BURST_SPEED = 4.0
    /** rcsOffset decay factor per tick (~3.5 ticks to reach < 0.01 m/s). */
    private const val RCS_BURST_DECAY = 0.82
    private const val RCS_COOLDOWN_TICKS  = 12
    /** Abort flight if pod travels further than this from its launch point (blocks). */
    private const val MAX_RANGE           = 100.0
    private const val DRILL_TICKS      = 40

    // ── Registry ──────────────────────────────────────────────────────────────

    fun register(podShipId: Long, seatEntityId: Int, ignoredShipId: Long, dimensionId: String) {
        pods[podShipId] = PodState(
            seatEntityId  = seatEntityId,
            podShipId     = podShipId,
            ignoredShipId = ignoredShipId,
            dimensionId   = dimensionId
        )
    }

    fun unregister(podShipId: Long) { pods.remove(podShipId) }

    fun isTrustedPodShip(shipId: Long): Boolean = pods.containsKey(shipId)

    // ── Network callbacks (set from VSShieldsModForge.init) ───────────────────

    /** Called via CockpitSeatEntity.fireCallback. */
    fun fire(seatEntityId: Int, yaw: Float, pitch: Float) {
        val state = pods.values.find { it.seatEntityId == seatEntityId } ?: return
        if (state.phase != CockpitSeatEntity.Phase.AIMING) return
        val radY = Math.toRadians(yaw.toDouble())
        val radP = Math.toRadians(pitch.toDouble())
        val dir = Vector3d(
            -sin(radY) * cos(radP),
            -sin(radP) * 1.15,    // slight vertical amplification so pod tracks aimed altitude
             cos(radY) * cos(radP)
        )
        dir.normalize()
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
        state.pendingRcs            = 0
    }

    /** Called via CockpitSeatEntity.rcsCallback every client tick during BOOST/COAST.
     *  [boostActive] == 1 means Space is held → apply thrust (BOOST phase only, uses fuel). */
    fun tryRcs(seatEntityId: Int, lateralDir: Int, boostActive: Int) {
        val state = pods.values.find { it.seatEntityId == seatEntityId } ?: return
        if (state.phase == CockpitSeatEntity.Phase.AIMING ||
            state.phase == CockpitSeatEntity.Phase.DRILLING) return
        if (lateralDir != 0) state.pendingRcs = lateralDir
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

            // Sync seat position to pod ship center each tick
            val podPos = podShip.transform.positionInWorld
            seat.setPos(podPos.x(), podPos.y(), podPos.z())

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
            level.explode(null, pos.x(), pos.y(), pos.z(), 5.0f, Level.ExplosionInteraction.BLOCK)
            // Fallback cleanup if explosion left the VS2 ship intact
            val shipId = podShip.id
            level.server.tell(net.minecraft.server.TickTask(level.server.tickCount + 2) {
                try {
                    val remaining = level.shipObjectWorld.loadedShips.getById(shipId)
                    if (remaining != null)
                        ShipAssembler.deleteShip(level, remaining as LoadedServerShip, true, false)
                } catch (_: Exception) {}
            })
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

        if (state.boostActive && state.boostFuelTicks > 0) {
            // ── Thrusting (Space held, fuel available) ───────────────────────
            // Ramp: speed increases as fuel is consumed (0 → TARGET_SPEED over BOOST_RAMP_TICKS).
            val fuelConsumed = (BOOST_TICKS - state.boostFuelTicks).toDouble()
            val t = (fuelConsumed / BOOST_RAMP_TICKS).coerceIn(0.0, 1.0)
            decayRcsOffset(state)
            val desiredVel = Vector3d(state.launchDir).mul(TARGET_SPEED * t).add(state.rcsOffset)
            gtpa.applyInvariantForce(state.podShipId, Vector3d(
                (desiredVel.x - curVel.x()) * VEL_KP,
                (desiredVel.y - curVel.y()) * VEL_KP + antiGrav + liftoff,
                (desiredVel.z - curVel.z()) * VEL_KP
            ))

            state.boostFuelTicks--
            // Engine sound — pitch varies slightly for a less robotic feel
            if (state.boostFuelTicks % 4 == 0) {
                level.playSound(null, pos.x(), pos.y(), pos.z(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS,
                    0.9f, 0.85f + level.random.nextFloat() * 0.3f)
            }
            // Auto-aim toward detected target hull while thrusting
            tryMagneticAlign(state, podShip, gtpa, level)
            // Fuel exhausted → COAST
            if (state.boostFuelTicks <= 0) {
                state.phase = CockpitSeatEntity.Phase.COAST
                seat.setPhase(CockpitSeatEntity.Phase.COAST)
            }
        } else {
            // ── Coasting (Space released or no fuel) ─────────────────────────
            // Maintain current speed and direction — pod glides until Space is pressed again.
            decayRcsOffset(state)
            val speed = Vector3d(curVel.x(), 0.0, curVel.z()).length().coerceAtLeast(0.1)
            val desiredVel = Vector3d(state.launchDir).mul(speed).add(state.rcsOffset)
            gtpa.applyInvariantForce(state.podShipId, Vector3d(
                (desiredVel.x - curVel.x()) * VEL_KP,
                antiGrav + liftoff,
                (desiredVel.z - curVel.z()) * VEL_KP
            ))
        }

        // Angular damping — keeps pod flying nose-forward regardless of thrust state
        val omega = podShip.angularVelocity
        gtpa.applyInvariantTorque(state.podShipId, Vector3d(
            -ANGULAR_DRAG * mass * omega.x(),
            -ANGULAR_DRAG * mass * omega.y(),
            -ANGULAR_DRAG * mass * omega.z()
        ))

        applyPendingRcs(state, seat, level, pos)
        state.velocity = Vector3d(curVel)
        seat.setPhase(CockpitSeatEntity.Phase.BOOST)

        // Always check for hull contact so drilling can start in any thrust state
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

        // No more fuel — pod glides at current speed.
        // Maintain XZ speed in launchDir so magnetic align can still steer it.
        // AntiGrav on Y prevents gravity from pulling the pod into the ground.
        val curVelCoast = podShip.velocity
        decayRcsOffset(state)
        val coastSpeed = Vector3d(curVelCoast.x(), 0.0, curVelCoast.z()).length().coerceAtLeast(0.1)
        val desiredVelCoast = Vector3d(state.launchDir).mul(coastSpeed).add(state.rcsOffset)
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

        applyPendingRcs(state, seat, level, pos)

        tryMagneticAlign(state, podShip, gtpa, level)
        checkShipCollision(state, podShip, level)

        if (pos.y() < level.minBuildHeight.toDouble() - 30.0) {
            seat.ejectPassengers()
            seat.discard()
        }
    }

    // ── RCS impulse ───────────────────────────────────────────────────────────

    /**
     * Consumes [PodState.pendingRcs]/[PodState.pendingRcsV] and adds a momentum burst to
     * [PodState.rcsOffset].  The velocity controller in tickBoost/tickCoast uses
     * `desiredVel + rcsOffset` as its target, so the burst is applied each tick until it
     * decays to zero (≈3–4 ticks at RCS_BURST_DECAY=0.82).
     *
     * launchDir is intentionally NOT modified — the pod returns to its original course after
     * the burst fades.  Charge/cooldown are consumed here (not in ModNetwork).
     */
    private fun applyPendingRcs(state: PodState, seat: CockpitSeatEntity,
                                 level: ServerLevel, pos: org.joml.Vector3dc) {
        val latDir = state.pendingRcs; state.pendingRcs = 0
        if (latDir == 0) return
        if (seat.rcsCharges <= 0 || seat.rcsCooldown > 0) return

        val forward = Vector3d(state.launchDir)
        val worldUp = if (abs(forward.y) < 0.9) Vector3d(0.0, 1.0, 0.0)
                      else                       Vector3d(1.0, 0.0, 0.0)
        // forward × worldUp = right-hand direction relative to flight direction
        val right = Vector3d(forward).cross(worldUp, Vector3d()).normalize()

        state.rcsOffset.add(Vector3d(right).mul(latDir.toDouble() * RCS_BURST_SPEED))

        seat.setRcsCharges(seat.rcsCharges - 1)
        seat.setRcsCooldown(RCS_COOLDOWN_TICKS)
        level.playSound(null, pos.x(), pos.y(), pos.z(),
            SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.5f, 1.2f)
    }

    /** Decays rcsOffset each tick and zeroes it when negligible. */
    private fun decayRcsOffset(state: PodState) {
        state.rcsOffset.mul(RCS_BURST_DECAY)
        if (state.rcsOffset.lengthSquared() < 0.01) state.rcsOffset.set(0.0, 0.0, 0.0)
    }

    // ── Terminal Magnetic Align ────────────────────────────────────────────────

    private fun tryMagneticAlign(state: PodState, podShip: LoadedServerShip,
                                  gtpa: GameToPhysicsAdapter,
                                  level: ServerLevel) {
        val vel = state.velocity
        if (vel.lengthSquared() < 1e-4) return

        val podPos = podShip.transform.positionInWorld
        val velNorm = vel.normalize(Vector3d())
        val rayEnd = Vector3d(podPos).add(Vector3d(velNorm).mul(MAG_LOCK_RANGE))

        // lockAABB is a sphere-like box around rayEnd: ships whose AABB intersects this
        // are candidates for magnetic alignment (same logic as the old expanded-AABB check)
        val lockAABB = AABB(
            rayEnd.x - MAG_LOCK_RANGE, rayEnd.y - MAG_LOCK_RANGE, rayEnd.z - MAG_LOCK_RANGE,
            rayEnd.x + MAG_LOCK_RANGE, rayEnd.y + MAG_LOCK_RANGE, rayEnd.z + MAG_LOCK_RANGE
        )
        for (ship in level.getShipsIntersecting(lockAABB)) {
            if (ship.id == state.podShipId || ship.id == state.ignoredShipId) continue

            // Raycast in shipyard space
            val w2s = ship.worldToShip
            val spStart = w2s.transformPosition(Vector3d(podPos.x(), podPos.y(), podPos.z()), Vector3d())
            val spEnd   = w2s.transformPosition(Vector3d(rayEnd.x, rayEnd.y, rayEnd.z),       Vector3d())
            val clipCtx = ClipContext(
                Vec3(spStart.x, spStart.y, spStart.z),
                Vec3(spEnd.x,   spEnd.y,   spEnd.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null)
            val bhr = level.clip(clipCtx)
            if (bhr.type == HitResult.Type.MISS) continue

            // Transform face normal to world space
            val face = bhr.direction
            val nShip = Vector3d(face.stepX.toDouble(), face.stepY.toDouble(), face.stepZ.toDouble())
            ship.shipToWorld.transformDirection(nShip).normalize()

            // Lerp launchDir toward -nShip (into hull) so the velocity controller
            // steers the pod toward the hull face on its own — no separate corrective force needed.
            val speed = vel.length()
            val target = nShip.negate(Vector3d())
            val aligned = Vector3d(velNorm)
                .lerp(target, MAG_LOCK_LERP)
                .normalize()

            state.velocity  = Vector3d(aligned).mul(speed)
            state.launchDir = Vector3d(aligned)   // controller will steer to hull face
            break
        }
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
        seat.setDrillTimer(timer - 1)

        val pos = podShip.transform.positionInWorld
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

        // Breach hull in target ship (eject position was precomputed before this call)
        val drillingLocalPos = state.drillingLocalPos
        if (drillingLocalPos != null && drillingDirection != null) {
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
