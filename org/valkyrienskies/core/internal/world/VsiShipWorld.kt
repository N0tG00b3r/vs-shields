package org.valkyrienskies.core.internal.world

import org.joml.Vector3dc
import org.valkyrienskies.core.api.ships.LoadedShip
import org.valkyrienskies.core.api.ships.Ship
import org.valkyrienskies.core.api.util.AerodynamicUtils.Companion.DEFAULT_MAX
import org.valkyrienskies.core.api.util.AerodynamicUtils.Companion.DEFAULT_SEA_LEVEL
import org.valkyrienskies.core.api.world.LevelYRange
import org.valkyrienskies.core.api.world.ShipWorld
import org.valkyrienskies.core.internal.ships.VsiQueryableShipData
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate
import org.valkyrienskies.core.internal.world.properties.DimensionId

interface VsiShipWorld : ShipWorld {

    override val allShips: VsiQueryableShipData<Ship>

    override val loadedShips: VsiQueryableShipData<LoadedShip>

    fun onSetBlock(
        posX: Int,
        posY: Int,
        posZ: Int,
        dimensionId: DimensionId,
        oldBlockType: VsiBlockType,
        newBlockType: VsiBlockType,
        oldBlockMass: Double,
        newBlockMass: Double
    )

    fun addTerrainUpdates(dimensionId: org.valkyrienskies.core.api.world.properties.DimensionId, terrainUpdates: List<VsiTerrainUpdate>)

    fun forceUpdateConnectivityChunk(dimensionId: org.valkyrienskies.core.api.world.properties.DimensionId, chunkX: Int, chunkY: Int, chunkZ: Int, update: VsiTerrainUpdate)

    /**
     * Adds a newly loaded dimension with [dimensionId]. [yRange] specifies the range of valid y values for this dimension.
     * In older versions of Minecraft, this should be `[0, 255]`
     */
    fun addDimension(dimensionId: org.valkyrienskies.core.api.world.properties.DimensionId, yRange: LevelYRange, gravity: Vector3dc, seaLevel: Double = DEFAULT_SEA_LEVEL, maxY: Double = DEFAULT_MAX)
    fun updateDimension(dimensionId: org.valkyrienskies.core.api.world.properties.DimensionId, gravity: Vector3dc, seaLevel: Double? = null, maxY: Double? = null)
    fun removeDimension(dimensionId: org.valkyrienskies.core.api.world.properties.DimensionId)
}
