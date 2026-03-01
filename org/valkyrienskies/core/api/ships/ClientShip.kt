package org.valkyrienskies.core.api.ships

import org.joml.primitives.AABBdc
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.api.util.GameTickOnly

/**
 * A ship as represented on the client.
 */
@GameTickOnly
interface ClientShip : LoadedShip {
    /**
     * The transform used when rendering the ship
     */
    val renderTransform: ShipTransform
    val renderAABB: AABBdc
    var transformProvider: ClientShipTransformProvider?
}
