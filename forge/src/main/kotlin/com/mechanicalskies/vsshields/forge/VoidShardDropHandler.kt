package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.config.ShieldConfig
import com.mechanicalskies.vsshields.registry.ModItems
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraftforge.event.entity.living.LivingDropsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

object VoidShardDropHandler {

    @SubscribeEvent
    fun onLivingDrops(event: LivingDropsEvent) {
        val entity = event.entity
        val level = entity.level() as? ServerLevel ?: return
        val rng = level.random
        val cfg = ShieldConfig.get().general

        val x = entity.x
        val y = entity.y + 0.5
        val z = entity.z

        when (entity.type) {
            EntityType.ENDERMAN -> {
                if (rng.nextFloat() < cfg.voidShardEndermanChance) {
                    val drop = ItemEntity(level, x, y, z, ItemStack(ModItems.VOID_SHARD.get(), 1))
                    drop.setDefaultPickUpDelay()
                    event.drops.add(drop)
                }
            }
            EntityType.ENDER_DRAGON -> {
                val count = cfg.voidShardDragonMin +
                    rng.nextInt(cfg.voidShardDragonMax - cfg.voidShardDragonMin + 1)
                val drop = ItemEntity(level, x, y, z, ItemStack(ModItems.VOID_SHARD.get(), count))
                drop.setDefaultPickUpDelay()
                event.drops.add(drop)
            }
            else -> return
        }
    }
}
