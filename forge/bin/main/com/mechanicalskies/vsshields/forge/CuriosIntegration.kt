package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.item.FrequencyIDCardItem
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * Soft dependency on Curios API.
 * Checks if a player holds a FrequencyIDCard matching a given access code
 * either in their inventory/hands or in a Curios slot.
 */
object CuriosIntegration {

    val LOADED: Boolean = runCatching {
        Class.forName("top.theillusivec4.curios.api.CuriosApi")
    }.isSuccess

    /**
     * Returns true if the entity has a card matching [code] in inventory or Curios slots.
     */
    fun hasMatchingCard(entity: LivingEntity, code: String): Boolean {
        if (code.isBlank()) return false
        val player = entity as? Player ?: return false

        // Check regular inventory (main + off-hand)
        val inv = player.inventory.items + listOf(player.offhandItem)
        if (inv.any { isCard(it, code) }) return true

        // Check Curios if loaded
        return if (LOADED) checkCurios(player, code) else false
    }

    private fun isCard(stack: ItemStack, code: String): Boolean =
        stack.item is FrequencyIDCardItem &&
        FrequencyIDCardItem.getCode(stack) == code &&
        FrequencyIDCardItem.getCode(stack).isNotBlank()

    private fun checkCurios(player: Player, code: String): Boolean {
        return runCatching {
            val api = Class.forName("top.theillusivec4.curios.api.CuriosApi")
            // getCuriosHelper returns Optional<ICuriosItemHandler>
            val helperOpt = api.getMethod("getCuriosHelper", Player::class.java)
                .invoke(null, player) as java.util.Optional<*>
            val helper = helperOpt.orElse(null) ?: return false
            // getEquippedCurios returns Optional<IItemHandlerModifiable>
            val equippedOpt = helper.javaClass.getMethod("getEquippedCurios")
                .invoke(helper) as java.util.Optional<*>
            val handler = equippedOpt.orElse(null) ?: return false
            val slots = handler.javaClass.getMethod("getSlots").invoke(handler) as Int
            val getStack = handler.javaClass.getMethod("getStackInSlot", Int::class.java)
            for (i in 0 until slots) {
                val stack = getStack.invoke(handler, i) as? ItemStack ?: continue
                if (isCard(stack, code)) return true
            }
            false
        }.getOrDefault(false)
    }
}
