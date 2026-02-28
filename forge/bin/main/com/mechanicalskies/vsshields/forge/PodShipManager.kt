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
import org.joml.Vector3d
import org.valkyrienskies.core.api.ships.LoadedServerShip
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
        /** Countdown for braking phase at drilling start (in ticks). */
        var drillBrakeTicks: Int = 0,
        /** Pending RCS direction queued by tryRcs() and consumed on the next tick (+1 = right, -1 = left). */
        var pendingRcs: Int = 0,
        /** World position at fire time — used to abort flight if pod drifts too far. */
        var launchPos: Vector3d = Vector3d()
    )

    private val pods = ConcurrentHashMap<Long, PodState>()

    // ── Physics constants ─────────────────────────────────────────────────────
    private const val BOOST_TICKS      = 60    // total boost duration (3 s at 20 tps)
    /** Ticks over which speed ramps from 0 → TARGET_SPEED (2 s). */
    private const val BOOST_RAMP_TICKS = 40
    /** Peak cruise speed in m/s (= blocks/s in VS2 units).
     *  11.25 m/s → 50 blocks in ~6 s (2 s ramp + 4 s constant). */
    private const val TARGET_SPEED     = 11.25
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
    /** Lateral deflection of launchDir per RCS fire (fraction of forward vector). */
    private const val RCS_DEFLECTION      = 0.05
    private const val RCS_COOLDOWN_TICKS  = 12
    /** Weak Y velocity-controller gain during COAST — lets VS2 gravity dominate vertical. */
    private const val COAST_VEL_KP_Y      = 5_000.0
    /** Abort flight if pod travels further than this from its launch point (blocks). */
    private const val MAX_RANGE           = 100.0
    private const val DRILL_TICKS      = 40
    /** Ticks to apply braking force before spring tracking begins. */
    private const val DRILL_BRAKE_TICKS = 5
    /** Spring stiffness keeping pod at contact point while drilling (N/m). */
    private const val DRILL_SPRING_K    = 50_000.0
    /** Damping for drilling spring (N per m/s). */
    private const val DRILL_DAMPING     = 10_000.0
    /** Stronger angular drag during drilling to prevent tumbling. */
    private const val DRILL_ANG_DRAG    = 30.0

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
        state.phase      = CockpitSeatEntity.Phase.BOOST
        state.boostTicks = 0
        state.drillingShipId   = Long.MIN_VALUE
        state.drillingLocalPos = null
        state.drillingDirection = null
    }

    /** Called via CockpitSeatEntity.rcsCallback. Queues impulse; applied via GTPA next tick. */
    fun tryRcs(seatEntityId: Int, dir: Int) {
        val state = pods.values.find { it.seatEntityId == seatEntityId } ?: return
        if (state.phase == CockpitSeatEntity.Phase.AIMING ||
            state.phase == CockpitSeatEntity.Phase.DRILLING) return
        state.pendingRcs = dir
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
                // Seat was discarded (player dismounted or breach completed)
                // Release static lock — AIMING and DRILLING both use setStatic
                if (state.phase == CockpitSeatEntity.Phase.AIMING ||
                    state.phase == CockpitSeatEntity.Phase.DRILLING) {
                    gtpa.setStatic(state.podShipId, false)
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
        val pos = podShip.transform.positionInWorld

        // Release static lock and capture launch position on the very first BOOST tick
        if (state.boostTicks == 0) {
            gtpa.setStatic(state.podShipId, false)
            state.launchPos = Vector3d(pos.x(), pos.y(), pos.z())
        }

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

        // Ramp desired speed linearly from 0 → TARGET_SPEED over BOOST_RAMP_TICKS
        val t = (state.boostTicks.toDouble() / BOOST_RAMP_TICKS).coerceIn(0.0, 1.0)
        val desiredVel = Vector3d(state.launchDir).mul(TARGET_SPEED * t)

        // Proportional velocity controller: F = KP * (desired − current)
        val curVel = podShip.velocity
        val mass   = podShip.inertiaData.mass
        val antiGrav = mass * 9.81
        val liftoff  = if (state.boostTicks < LIFTOFF_TICKS) LIFTOFF_FORCE else 0.0
        gtpa.applyInvariantForce(state.podShipId, Vector3d(
            (desiredVel.x - curVel.x()) * VEL_KP,
            (desiredVel.y - curVel.y()) * VEL_KP + antiGrav + liftoff,
            (desiredVel.z - curVel.z()) * VEL_KP
        ))

        // Angular damping — prevents tumbling, keeps pod flying nose-forward
        val omega = podShip.angularVelocity
        gtpa.applyInvariantTorque(state.podShipId, Vector3d(
            -ANGULAR_DRAG * mass * omega.x(),
            -ANGULAR_DRAG * mass * omega.y(),
            -ANGULAR_DRAG * mass * omega.z()
        ))

        // RCS impulse — applied via GTPA so it actually moves the VS2 ship
        applyPendingRcs(state, podShip, seat, gtpa, level, pos)

        state.velocity = Vector3d(curVel)
        seat.setPhase(CockpitSeatEntity.Phase.BOOST)

        state.boostTicks++
        if (state.boostTicks >= BOOST_TICKS) {
            state.phase = CockpitSeatEntity.Phase.COAST
            seat.setPhase(CockpitSeatEntity.Phase.COAST)
        }

        if (state.boostTicks % 8 == 0) {
            level.playSound(null, pos.x(), pos.y(), pos.z(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8f, 1.0f)
        }
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

        // Horizontal velocity controller — maintains launchDir speed in XZ.
        // Y axis: weak controller (COAST_VEL_KP_Y), no anti-gravity →
        // VS2 gravity dominates and significantly pulls the pod downward in COAST.
        val desiredVelCoast = Vector3d(state.launchDir).mul(TARGET_SPEED)
        val curVelCoast = podShip.velocity
        gtpa.applyInvariantForce(state.podShipId, Vector3d(
            (desiredVelCoast.x - curVelCoast.x()) * VEL_KP,
            (desiredVelCoast.y - curVelCoast.y()) * COAST_VEL_KP_Y,
            (desiredVelCoast.z - curVelCoast.z()) * VEL_KP
        ))

        // Angular damping in COAST too — no tumbling during approach
        val omega = podShip.angularVelocity
        gtpa.applyInvariantTorque(state.podShipId, Vector3d(
            -ANGULAR_DRAG * mass * omega.x(),
            -ANGULAR_DRAG * mass * omega.y(),
            -ANGULAR_DRAG * mass * omega.z()
        ))

        // RCS impulse — applied via GTPA so it actually moves the VS2 ship
        applyPendingRcs(state, podShip, seat, gtpa, level, pos)

        tryMagneticAlign(state, podShip, gtpa, level)
        checkShipCollision(state, podShip, level)

        if (pos.y() < level.minBuildHeight.toDouble() - 30.0) {
            seat.ejectPassengers()
            seat.discard()
        }
    }

    // ── RCS impulse ───────────────────────────────────────────────────────────

    /**
     * Consumes [PodState.pendingRcs] and applies a lateral impulse via GTPA.
     * Using GTPA (not state.velocity) ensures the force actually reaches the VS2 physics engine.
     */
    private fun applyPendingRcs(state: PodState, podShip: LoadedServerShip,
                                 seat: CockpitSeatEntity, gtpa: GameToPhysicsAdapter,
                                 level: ServerLevel,
                                 pos: org.joml.Vector3dc) {
        val dir = state.pendingRcs
        state.pendingRcs = 0
        if (dir == 0) return
        if (seat.rcsCharges <= 0 || seat.rcsCooldown > 0) return

        val forward = Vector3d(state.launchDir)
        val worldUp = if (abs(forward.y) < 0.9) Vector3d(0.0, 1.0, 0.0)
                      else                       Vector3d(1.0, 0.0, 0.0)
        val right = worldUp.cross(forward, Vector3d()).normalize()

        // Deflect launchDir laterally — the velocity controller steers to the new direction.
        // This avoids the previous conflict where RCS impulse was immediately cancelled by
        // the controller targeting the old launchDir.
        state.launchDir = Vector3d(state.launchDir)
            .add(right.mul(dir.toDouble() * RCS_DEFLECTION))
            .normalize()

        seat.setRcsCharges(seat.rcsCharges - 1)
        seat.setRcsCooldown(RCS_COOLDOWN_TICKS)
        level.playSound(null, pos.x(), pos.y(), pos.z(),
            SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.5f, 1.2f)
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

            // Lerp velocity direction toward -nShip (into hull) and apply corrective force
            val speed = vel.length()
            val target = nShip.negate(Vector3d())
            val aligned = Vector3d(velNorm)
                .lerp(target, MAG_LOCK_LERP)
                .normalize()
                .mul(speed)

            state.velocity = aligned
            // Corrective impulse: push ship velocity toward desired in one tick
            val mass = 1000.0  // TODO: use podShip.inertiaData.mass when API confirmed
            val correction = Vector3d(aligned).sub(vel)
            gtpa.applyInvariantForce(state.podShipId, correction.mul(mass * 20.0))  // F = m*Δv/dt
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

        // ── Raycast to find exact hull face and its normal ────────────────────────
        // Start 6 blocks BEHIND the pod so the ray begins outside the hull even if
        // the pod's AABB already overlaps the target.
        val approachDir = if (state.velocity.lengthSquared() > 1e-6)
            Vector3d(state.velocity).normalize() else Vector3d(state.launchDir)

        val rayStart = Vector3d(podPosVec).add(Vector3d(approachDir).mul(-6.0))
        val rayEnd   = Vector3d(podPosVec).add(Vector3d(approachDir).mul(40.0))
        val w2s      = targetShip.worldToShip
        val spStart  = w2s.transformPosition(rayStart, Vector3d())
        val spEnd    = w2s.transformPosition(rayEnd,   Vector3d())

        val bhr = level.clip(ClipContext(
            Vec3(spStart.x, spStart.y, spStart.z),
            Vec3(spEnd.x,   spEnd.y,   spEnd.z),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null))

        if (bhr.type != HitResult.Type.MISS) {
            // Use hit block position (shipyard coords) and face normal perpendicular to hull
            val bp = bhr.blockPos
            state.drillingLocalPos = Vector3d(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble())
            val face = bhr.direction   // points OUT of the block toward the ray origin
            // Negate → direction pointing INTO the hull (what we drill along)
            val intoHullShip = Vector3d((-face.stepX).toDouble(),
                                        (-face.stepY).toDouble(),
                                        (-face.stepZ).toDouble())
            // Transform shipyard-space direction to world space for breachHull/eject use
            state.drillingDirection = targetShip.shipToWorld
                .transformDirection(intoHullShip, Vector3d()).normalize()
        } else {
            // Fallback (very rare): use approach direction + pod center as contact
            state.drillingLocalPos  = w2s.transformPosition(podPosVec, Vector3d())
            state.drillingDirection = Vector3d(approachDir)
        }

        state.drillingShipId  = targetShip.id
        state.drillBrakeTicks = 0  // not used; pod is static
        state.phase           = CockpitSeatEntity.Phase.DRILLING

        (level.getEntity(state.seatEntityId) as? CockpitSeatEntity)?.setDrillTimer(DRILL_TICKS)

        // Freeze pod immediately — locks position AND orientation so the drill aims
        // exactly perpendicular to the hull surface for the entire drill duration.
        ValkyrienSkiesMod.getOrCreateGTPA(level.dimensionId)
            .setStatic(state.podShipId, true)

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
            // Target ship gone — release static lock before ejecting
            gtpa.setStatic(state.podShipId, false)
            seat.ejectPassengers()
            seat.discard()
            return
        }

        // Pod is frozen (setStatic=true from startDrilling) — position and orientation
        // are locked. No forces needed; just count down the drill timer.
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

        // Release static lock (set in startDrilling) so explosion can destroy pod ship
        gtpa.setStatic(state.podShipId, false)
        try { gtpa.enableCollisionBetween(state.podShipId, targetShip.id) } catch (_: Exception) {}

        val passenger       = seat.passengers.firstOrNull()
        val drillingLocalPos = state.drillingLocalPos
        val drillingDirection = state.drillingDirection

        // ── Compute eject position BEFORE breachHull destroys the blocks ─────────
        // Ray-march finds the first solid block in the hull; we land 3 blocks past it.
        // Must run first — breachHull removes those blocks and the ray-march would miss.
        var ejectWorld: Vector3d? = null
        if (passenger != null && drillingLocalPos != null && drillingDirection != null) {
            val fwdShip = targetShip.worldToShip
                .transformDirection(Vector3d(drillingDirection), Vector3d()).normalize()
            var hitLocal = Vector3d(drillingLocalPos).add(fwdShip.mul(2.0, Vector3d()))  // fallback
            for (step in -6..8) {
                val tx = Math.round(drillingLocalPos.x + fwdShip.x * step).toInt()
                val ty = Math.round(drillingLocalPos.y + fwdShip.y * step).toInt()
                val tz = Math.round(drillingLocalPos.z + fwdShip.z * step).toInt()
                if (!level.getBlockState(BlockPos(tx, ty, tz)).isAir) {
                    hitLocal = Vector3d(tx + fwdShip.x * 3, ty + fwdShip.y * 3, tz + fwdShip.z * 3)
                    break
                }
            }
            ejectWorld = targetShip.shipToWorld.transformPosition(hitLocal, Vector3d())
        }

        // Trust passenger through solid shield (200 ticks)
        if (passenger != null) {
            CockpitSeatEntity.getTrustCallback()
                ?.trustPassenger(passenger.uuid, level.gameTime, 200)
        }

        // Breach hull in target ship (blocks destroyed AFTER eject position was computed)
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

        // Visual effects
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
            podPos.x(), podPos.y(), podPos.z(), 3, 0.3, 0.3, 0.3, 0.1)
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
            podPos.x(), podPos.y(), podPos.z(), 20, 0.5, 0.5, 0.5, 0.02)

        // Explode pod ship
        level.explode(null, podPos.x(), podPos.y(), podPos.z(), 5.0f,
            Level.ExplosionInteraction.BLOCK)

        // Deferred fallback: if the explosion didn't destroy all pod blocks,
        // force-delete the VS2 ship 2 ticks later.
        val podShipId = state.podShipId
        level.server.tell(net.minecraft.server.TickTask(level.server.tickCount + 2) {
            try {
                val remaining = level.shipObjectWorld.loadedShips.getById(podShipId)
                if (remaining != null) {
                    ShipAssembler.deleteShip(level, remaining as LoadedServerShip, true, false)
                }
            } catch (_: Exception) {}
        })

        seat.discard()
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
