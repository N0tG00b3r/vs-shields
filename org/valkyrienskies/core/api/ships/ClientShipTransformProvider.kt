package org.valkyrienskies.core.api.ships

import org.valkyrienskies.core.api.bodies.properties.BodyTransform
import org.valkyrienskies.core.api.ships.properties.ShipTransform

/**
 * This controls the behavior of how client ships determine their position and render position.
 */
interface ClientShipTransformProvider {
    /**
     * @param prevShipTransform The transform of the ship during the last tick
     * @param shipTransform The current transform of the ship
     * @param latestNetworkTransform The transform of the ship in the latest position packet received
     * @return The transform that will be used for the ship for the next tick. If this returns null then VS will use the
     *         default behavior for computing the ShipTransform.
     */
    fun provideNextTransform(prevShipTransform: ShipTransform, shipTransform: ShipTransform, latestNetworkTransform: ShipTransform): BodyTransform?

    /**
     * @param prevShipTransform The transform of the ship during the last tick
     * @param shipTransform The current transform of the ship
     * @return The render transform that will be used for the ship for the next frame. If this returns null then VS will
     *         use the default behavior for computing the ShipTransform.
     */
    fun provideNextRenderTransform(prevShipTransform: ShipTransform, shipTransform: ShipTransform, partialTick: Double): BodyTransform?
}
