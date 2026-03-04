package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.registry.ModItems
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.item.ItemStack
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

/**
 * Adds custom bonus drops for anomaly guardian mobs WITHOUT replacing vanilla drops.
 *
 * Enderman: +1 void_shard (30% chance)
 * Phantom: +1 raw_aether_crystal (25% chance)
 * Shulker: +1 void_shard (20% chance)
 */
object AnomalyGuardianDropHandler {

    private const val GUARDIAN_TAG = "anomaly_guardian"

    @SubscribeEvent
    fun onGuardianDrop(event: LivingDropsEvent) {
        val entity = event.entity
        if (!entity.tags.contains(GUARDIAN_TAG)) return

        // Keep all vanilla drops — do NOT clear

        val level = entity.level()
        val x = entity.x
        val y = entity.y
        val z = entity.z
        val random = level.random

        when (entity) {
            is EnderMan -> {
                // +1 void_shard (30% chance)
                if (random.nextFloat() < 0.3f) {
                    event.drops.add(ItemEntity(level, x, y, z,
                        ItemStack(ModItems.VOID_SHARD.get(), 1)))
                }
            }
            is Phantom -> {
                // +1 raw_aether_crystal (25% chance)
                if (random.nextFloat() < 0.25f) {
                    event.drops.add(ItemEntity(level, x, y, z,
                        ItemStack(ModItems.RAW_AETHER_CRYSTAL.get(), 1)))
                }
            }
            is Shulker -> {
                // +1 void_shard (20% chance)
                if (random.nextFloat() < 0.2f) {
                    event.drops.add(ItemEntity(level, x, y, z,
                        ItemStack(ModItems.VOID_SHARD.get(), 1)))
                }
            }
        }
    }
}
