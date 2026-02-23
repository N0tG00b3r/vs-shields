package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.blockentity.GravityFieldGeneratorBlockEntity
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
 * Attaches Forge Energy capability to GravityFieldGeneratorBlockEntity.
 */
class GravityFieldEnergyCapability {

    @SubscribeEvent
    fun onAttachCapabilities(event: AttachCapabilitiesEvent<BlockEntity>) {
        val be = event.`object`
        if (be !is GravityFieldGeneratorBlockEntity) return

        val storage = GravityEnergyStorage(be)
        val lazy = LazyOptional.of { storage as IEnergyStorage }

        event.addCapability(
            ResourceLocation("vs_shields", "gravity_field_energy"),
            object : ICapabilityProvider {
                override fun <T : Any?> getCapability(cap: Capability<T>, side: Direction?): LazyOptional<T> =
                    if (cap == ForgeCapabilities.ENERGY) lazy.cast() else LazyOptional.empty()
            }
        )
    }

    private class GravityEnergyStorage(private val be: GravityFieldGeneratorBlockEntity) : IEnergyStorage {
        override fun receiveEnergy(maxReceive: Int, simulate: Boolean) = be.receiveEnergy(maxReceive, simulate)
        override fun extractEnergy(maxExtract: Int, simulate: Boolean) = 0
        override fun getEnergyStored() = be.energyStored
        override fun getMaxEnergyStored() = be.maxEnergy
        override fun canExtract() = false
        override fun canReceive() = true
    }
}
