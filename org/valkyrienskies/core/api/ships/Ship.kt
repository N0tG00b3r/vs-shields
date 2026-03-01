package org.valkyrienskies.core.api.ships

import org.joml.Matrix4dc
import org.joml.Vector3dc
import org.joml.primitives.AABBdc
import org.joml.primitives.AABBic
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.bodies.properties.BodyKinematics
import org.valkyrienskies.core.api.ships.properties.*
import org.valkyrienskies.core.api.util.Identified
import org.valkyrienskies.core.api.world.properties.DimensionId

/**
 * Abstraction of a ship, there are many types such as offline ships
 *  or loaded ships so this is the generic interface for all ships.
 */
interface Ship : Identified {

    /**
     * The ID of the ship.
     *
     * - **Unique**: No two ships have the same ID, and generally, no other [Identified] object will have the same ID
     * (unless it's related to this [Ship] in some way).
     * - **Not -1**: -1 is reserved to be used as a sentinel value. However, they can be anything else.
     *
     * @see Identified
     */
    override val id: ShipId

    /**
     * A unique String identifier for the ship. This is only used for UI purposes, such as the `/vs` commands.
     */
    val slug: String?

    /**
     * The current game-tick kinematics of the ship.
     */
    @VsBeta
    val kinematics: BodyKinematics

    /**
     * The current game-tick transform of the ship - an alias for
     * [kinematics][kinematics]`.`[transform][BodyKinematics.transform]
     *
     * If you use this for rendering on the client side, the ship's movement will be uninterpolated. You need to instead
     * use [ClientShip.renderTransform].
     */
    val transform: ShipTransform
        @OptIn(VsBeta::class)
        get() = kinematics.transform as ShipTransform

    /**
     * The transform of the ship as it was on the previous game tick, or the current transform if there was no previous
     * game tick.
     *
     * @see transform
     */
    val prevTickTransform: ShipTransform

    /**
     * The ship's [ChunkClaim]. This is the area in the Shipyard where the ship's blocks reside.
     */
    val chunkClaim: ChunkClaim

    /**
     * The [DimensionId] of the ship's [ChunkClaim], i.e., the dimension where the ship's blocks are stored.
     *
     * Currently, every ship exists in the same dimension as its [ChunkClaim], but we might change this in the future to
     * support cross-dimensional portals.
     */
    val chunkClaimDimension: DimensionId

    /**
     * The bounding box of the ship in world-space.
     *
     * - **If the ship has blocks**: This is equivalent to the [shipAABB] transformed into world-space using
     * `transform`.
     * - **If the ship has no blocks**: This is equivalent to a zero-volume AABB at the ship's
     * [transform][transform]`.`[position][ShipTransform.position]
     *
     * The bounding box doesn't account for weirdly shaped blocks - e.g., a ship composed of a single slab will have a
     * full-block bounding box.
     */
    val worldAABB: AABBdc

    /**
     * The bounding box of the ship in ship-space. If the ship has no blocks, this is `null`.
     *
     * The bounding box doesn't account for weirdly shaped blocks - e.g., a ship composed of a single slab will have a
     * full-block bounding box.
     */
    val shipAABB: AABBic?

    /**
     * Convenience method to access [kinematics][kinematics]`.`[velocity][BodyKinematics.velocity]
     *
     * @see BodyKinematics.velocity
     */
    val velocity: Vector3dc
        @OptIn(VsBeta::class)
        get() = kinematics.velocity

    /**
     * Convenience method to access [kinematics][kinematics]`.`[angularVelocity][BodyKinematics.angularVelocity]
     *
     * @see BodyKinematics.angularVelocity
     */
    val angularVelocity: Vector3dc
        @OptIn(VsBeta::class)
        get() = kinematics.angularVelocity

    val activeChunksSet: IShipActiveChunksSet

    /**
     * Convenience method to access [transform][transform]`.`[toWorld][ShipTransform.toWorld]
     */
    val shipToWorld: Matrix4dc get() = transform.toWorld

    /**
     * Convenience method to access [transform][transform]`.`[toModel][ShipTransform.toModel]
     */
    val worldToShip: Matrix4dc get() = transform.toModel

    @Deprecated("renamed", ReplaceWith("prevTickTransform"))
    val prevTickShipTransform: ShipTransform get() = prevTickTransform

    @Deprecated("renamed", ReplaceWith("angularVelocity"))
    val omega: Vector3dc get() = angularVelocity

    @Deprecated("renamed", ReplaceWith("transform"))
    val shipTransform: ShipTransform get() = transform

    @Deprecated("renamed", ReplaceWith("shipAABB"))
    val shipVoxelAABB: AABBic? get() = shipAABB

    @Deprecated("renamed", ReplaceWith("activeChunksSet"))
    val shipActiveChunksSet: IShipActiveChunksSet get() = activeChunksSet
}
