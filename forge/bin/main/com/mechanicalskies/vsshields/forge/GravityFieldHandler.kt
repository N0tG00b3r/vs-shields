package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.shield.GravityFieldRegistry
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.living.LivingFallEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

/**
 * Applies Gravity Field Generator effects to players server-side:
 *  - Creative-style flight (mayfly) when flight toggle is on
 *  - Cancels fall damage when fall-protection toggle is on
 */
class GravityFieldHandler {

    @SubscribeEvent
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = event.player
        if (player.level().isClientSide) return
        if (player !is ServerPlayer) return

        val state = GravityFieldRegistry.getForPlayer(player)
        val abilities = player.abilities

        // Never override creative / spectator permanent flight
        if (!abilities.instabuild) {
            val shouldFly = state?.flightEnabled == true
            if (shouldFly && !abilities.mayfly) {
                abilities.mayfly = true
                player.onUpdateAbilities()
            } else if (!shouldFly && abilities.mayfly) {
                abilities.mayfly = false
                abilities.flying = false
                player.onUpdateAbilities()
            }
        }
    }

    @SubscribeEvent
    fun onLivingFall(event: LivingFallEvent) {
        val entity = event.entity
        if (entity !is Player) return
        if (entity.level().isClientSide) return
        val state = GravityFieldRegistry.getForPlayer(entity) ?: return
        if (state.fallProtectionEnabled) {
            event.isCanceled = true
        }
    }
}
