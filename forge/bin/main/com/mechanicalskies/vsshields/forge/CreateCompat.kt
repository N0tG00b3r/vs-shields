package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.blockentity.CloakingFieldGeneratorBlockEntity
import com.mechanicalskies.vsshields.blockentity.ShieldBatteryInputBlockEntity
import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import java.lang.reflect.Method

/**
 * Soft compatibility with Create mod.
 * Reads rotation speed from adjacent KineticBlockEntity via reflection.
 * Converts SU (speed) into FE injected into the shield generator.
 *
 * Conversion: FE/tick = |speed| × SU_TO_FE_RATE
 * A shaft at 256 RPM → 256 FE/tick (fills iron generator buffer in ~195 ticks ≈ 10s)
 */
object CreateCompat {
    private const val SU_TO_FE_RATE = 1.0

    private var initialized = false
    private var available = false
    private var kineticBEClass: Class<*>? = null
    private var getSpeedMethod: Method? = null

    private fun init() {
        if (initialized) return
        initialized = true
        try {
            kineticBEClass = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity")
            getSpeedMethod = kineticBEClass!!.getMethod("getSpeed")
            available = true
        } catch (_: Exception) {
            available = false
        }
    }

    /**
     * Check all 6 adjacent blocks for a Create kinetic source.
     * If found, inject FE into the shield generator based on rotation speed.
     */
    fun tickKineticInput(level: Level, pos: BlockPos, generator: ShieldGeneratorBlockEntity) {
        init()
        if (!available) return

        var totalFE = 0.0

        for (dir in Direction.values()) {
            val adjPos = pos.relative(dir)
            val adjBE = level.getBlockEntity(adjPos) ?: continue

            if (!kineticBEClass!!.isInstance(adjBE)) continue

            try {
                val speed = getSpeedMethod!!.invoke(adjBE) as Float
                totalFE += Math.abs(speed.toDouble()) * SU_TO_FE_RATE
            } catch (_: Exception) {
                // Reflection failed — skip
            }
        }

        if (totalFE > 0) {
            generator.receiveEnergy(totalFE.toInt(), false)
        }
    }

    /**
     * Same as tickKineticInput but for CloakingFieldGeneratorBlockEntity.
     */
    fun tickCloakKineticInput(level: Level, pos: BlockPos, cloak: CloakingFieldGeneratorBlockEntity) {
        init()
        if (!available) return

        var totalFE = 0.0

        for (dir in Direction.values()) {
            val adjPos = pos.relative(dir)
            val adjBE = level.getBlockEntity(adjPos) ?: continue

            if (!kineticBEClass!!.isInstance(adjBE)) continue

            try {
                val speed = getSpeedMethod!!.invoke(adjBE) as Float
                totalFE += Math.abs(speed.toDouble()) * SU_TO_FE_RATE
            } catch (_: Exception) {
                // Reflection failed — skip
            }
        }

        if (totalFE > 0) {
            cloak.receiveEnergy(totalFE.toInt(), false)
        }
    }

    /**
     * Same as tickKineticInput but for ShieldBatteryInputBlockEntity.
     */
    fun tickBatteryKineticInput(level: Level, pos: BlockPos, input: ShieldBatteryInputBlockEntity) {
        init()
        if (!available) return

        var totalFE = 0.0

        for (dir in Direction.values()) {
            val adjPos = pos.relative(dir)
            val adjBE = level.getBlockEntity(adjPos) ?: continue

            if (!kineticBEClass!!.isInstance(adjBE)) continue

            try {
                val speed = getSpeedMethod!!.invoke(adjBE) as Float
                totalFE += Math.abs(speed.toDouble()) * SU_TO_FE_RATE
            } catch (_: Exception) {
                // Reflection failed — skip
            }
        }

        if (totalFE > 0) {
            input.receiveEnergy(totalFE.toInt(), false)
        }
    }

    /**
     * Same as tickKineticInput but for ShieldJammerInputBlockEntity.
     */
    fun tickShieldJammerInput(level: Level, pos: BlockPos, ram: com.mechanicalskies.vsshields.blockentity.ShieldJammerInputBlockEntity) {
        init()
        if (!available) return

        var totalFE = 0.0

        for (dir in Direction.values()) {
            val adjPos = pos.relative(dir)
            val adjBE = level.getBlockEntity(adjPos) ?: continue

            if (!kineticBEClass!!.isInstance(adjBE)) continue

            try {
                val speed = getSpeedMethod!!.invoke(adjBE) as Float
                totalFE += Math.abs(speed.toDouble()) * SU_TO_FE_RATE
            } catch (_: Exception) {
                // Reflection failed — skip
            }
        }

        if (totalFE > 0) {
            ram.receiveEnergy(totalFE.toInt(), false)
        }
    }

    fun isAvailable(): Boolean {
        init()
        return available
    }
}
