package org.valkyrienskies.core.api.ships.properties

import org.jetbrains.annotations.ApiStatus.NonExtendable
import org.joml.Matrix4dc
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.joml.primitives.AABBd
import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.VsBeta
import org.valkyrienskies.core.api.bodies.properties.BodyTransform

/**
 * This class is soft-deprecated and exists only for compatibility purposes. Prefer to use [BodyTransform] in all cases.
 *
 * It is always safe to cast [BodyTransform] to [ShipTransform].
 */
@NonExtendable
interface ShipTransform : BodyTransform {

    /**
     * Alias for [position]
     */
    val positionInWorld: Vector3dc get() = position

    /**
     * Alias for [positionInModel]
     */
    val positionInShip: Vector3dc get() = positionInModel

    /**
     * Alias for [rotation]
     */
    val shipToWorldRotation: Quaterniondc get() = rotation

    /**
     * Alias for [scaling]
     */
    val shipToWorldScaling: Vector3dc get() = scaling

    /**
     * Alias for [toWorld]
     */
    val shipToWorld: Matrix4dc get() = toWorld

    /**
     * Alias for [toModel]
     */
    val worldToShip: Matrix4dc get() = toModel

    /**
     * Create an empty [AABBdc] centered around [positionInWorld].
     */
    @Deprecated("not sure why this exists", ReplaceWith("AABBd(position, position)", "org.joml.primitives.AABBd"))
    fun createEmptyAABB(): AABBdc = AABBd(position, position)

    // ReplaceWith is broken with default parameters equal to `this.x`
    @Suppress("DeprecatedCallableAddReplaceWith")
    @OptIn(VsBeta::class)
    @Deprecated("use toBuilder or rebuild instead - you can cast the result back to ShipTransform if needed")
    fun copy(
        positionInWorld: Vector3dc = this.positionInWorld,
        positionInShip: Vector3dc = this.positionInShip,
        shipToWorldRotation: Quaterniondc = this.shipToWorldRotation,
        shipToWorldScaling: Vector3dc = this.shipToWorldScaling
    ): ShipTransform = toBuilder()
        .position(positionInWorld)
        .rotation(shipToWorldRotation)
        .scaling(shipToWorldScaling)
        .positionInModel(positionInShip)
        .build() as ShipTransform

    // note `rotation.transform` is like half the characters of `transformDirectionNoScalingFromShipToWorld`
    @Deprecated("redundant", ReplaceWith("rotation.transform(directionInShip, dest)"))
    fun transformDirectionNoScalingFromShipToWorld(directionInShip: Vector3dc, dest: Vector3d): Vector3d =
        rotation.transform(directionInShip, dest)

    @Deprecated("redundant", ReplaceWith("rotation.transformInverse(directionInWorld, dest)"))
    fun transformDirectionNoScalingFromWorldToShip(directionInWorld: Vector3dc, dest: Vector3d): Vector3d =
        rotation.transformInverse(directionInWorld, dest)

    @Deprecated("renamed", ReplaceWith("position"))
    val shipPositionInWorldCoordinates: Vector3dc get() = position

    @Deprecated("renamed", ReplaceWith("positionInModel"))
    val shipPositionInShipCoordinates: Vector3dc get() = positionInModel

    @Deprecated("renamed", ReplaceWith("rotation"))
    val shipCoordinatesToWorldCoordinatesRotation: Quaterniondc get() = rotation

    @Deprecated("renamed", ReplaceWith("scaling"))
    val shipCoordinatesToWorldCoordinatesScaling: Vector3dc get() = scaling

    @Deprecated("renamed", ReplaceWith("toWorld"))
    val shipToWorldMatrix: Matrix4dc get() = toWorld

    @Deprecated("renamed", ReplaceWith("toModel"))
    val worldToShipMatrix: Matrix4dc get() = toModel

}
