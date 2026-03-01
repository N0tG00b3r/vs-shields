package org.valkyrienskies.core.internal.world

import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.core.api.ships.properties.ShipId
import org.valkyrienskies.core.api.ships.properties.ShipTransform
import org.valkyrienskies.core.api.util.GameTickOnly
import org.valkyrienskies.core.api.world.ClientShipWorld
import org.valkyrienskies.core.internal.ships.VsiQueryableShipData
import java.net.SocketAddress

// not impressed at all with this interface, just exists because
// it's too much of a hassle to refactor everything right now
// just how many tick() methods do we need?!
@GameTickOnly
interface VsiClientShipWorld : VsiShipWorld, ClientShipWorld {

    override val allShips: VsiQueryableShipData<ClientShip>
    override val loadedShips: VsiQueryableShipData<ClientShip>

    fun getPhysEntityClientRenderTransform(id: ShipId): ShipTransform?

    fun tickNetworking(server: SocketAddress)

    fun postTick()

    fun updateRenderTransforms(partialTicks: Double)

    fun destroyWorld()

    val isSyncedWithServer: Boolean
}
