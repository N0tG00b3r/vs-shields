package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.blockentity.CloakingFieldGeneratorBlockEntity
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.energy.IEnergyStorage
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

/**
 * Attaches Forge Energy (IEnergyStorage) capability to CloakingFieldGeneratorBlockEntity.
 * Allows FE pipes/cables from other mods to insert energy into the cloaking generator.
 */
class CloakEnergyCapability {

    @SubscribeEvent
    fun onAttachCapabilities(event: AttachCapabilitiesEvent<BlockEntity>) {
        val be = event.`object`
        if (be !is CloakingFieldGeneratorBlockEntity) return

        val storage = CloakEnergyStorage(be)
        val lazy = LazyOptional.of { storage as IEnergyStorage }

        event.addCapability(
            ResourceLocation("vs_shields", "cloak_energy"),
            object : ICapabilityProvider {
                override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> {
                    return if (cap == ForgeCapabilities.ENERGY) lazy.cast() else LazyOptional.empty()
                }
            }
        )
    }

    private class CloakEnergyStorage(private val be: CloakingFieldGeneratorBlockEntity) : IEnergyStorage {
        override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int =
            be.receiveEnergy(maxReceive, simulate)

        override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int = 0
        override fun getEnergyStored(): Int = be.energyStored
        override fun getMaxEnergyStored(): Int = be.maxEnergy
        override fun canExtract(): Boolean = false
        override fun canReceive(): Boolean = true
    }
}
