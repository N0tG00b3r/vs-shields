package org.valkyrienskies.core.internal.ships

import org.valkyrienskies.core.api.bodies.properties.BodyKinematics
import org.valkyrienskies.core.api.bodies.properties.BodyTransform
import org.valkyrienskies.core.api.ships.Ship

interface VsiShip : Ship {
    fun setFromTransform(transform: BodyTransform)

    fun unsafeSetKinematics(kinematics: BodyKinematics)
}
