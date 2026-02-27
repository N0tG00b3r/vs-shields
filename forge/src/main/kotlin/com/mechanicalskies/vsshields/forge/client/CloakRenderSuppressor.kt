package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import org.joml.primitives.AABBdc
import org.slf4j.LoggerFactory
import org.valkyrienskies.core.api.ships.ClientShip
import org.valkyrienskies.mod.common.hooks.VSGameEvents

/**
 * Hides cloaked ships on the VANILLA render path (MixinLevelRendererVanilla).
 *
 * VS2's renderChunkLayer wrapper fires the following events in order:
 *   1. op.call() — renders normal world chunks
 *   2. shipsStartRendering — fired ONCE per renderChunkLayer call
 *   3. shipRenderChunks.forEach(lambda) — renders each ship's chunks, fires renderShip per ship
 *
 * Strategy: in the shipsStartRendering listener, use reflection to access shipRenderChunks
 * from the LevelRenderer (injected there by VS2's MixinLevelRendererVanilla) and remove
 * cloaked ships. The forEach at step 3 then sees an empty/smaller map and skips them.
 *
 * The renderShip listener is kept as a belt-and-suspenders fallback (GL mask approach).
 */
object CloakRenderSuppressor {

    private val LOGGER = LoggerFactory.getLogger("vs_shields/cloak")

    // Debug counters — log only first N times to avoid per-frame spam
    private var shipsStartLogCount = 0
    private var renderShipLogCount = 0

    // Cached reflection field for shipRenderChunks (injected into LevelRenderer by VS2)
    @Volatile
    private var shipRenderChunksField: java.lang.reflect.Field? = null
    private var fieldResolveFailed = false

    // GL suppression state for renderShip fallback
    private var suppressing = false
    private val savedChunks = ArrayList<Any>()
    private val suppressedShips = HashSet<Long>()

    // ─── Primary: shipsStartRendering ────────────────────────────────────────
    // Fires once per renderChunkLayer call, BEFORE the per-ship forEach loop.
    // We remove cloaked ships from shipRenderChunks so the forEach skips them.
    @Suppress("unused")
    private val shipsStartListener = VSGameEvents.shipsStartRendering.on { event ->
        if (shipsStartLogCount++ < 3) {
            LOGGER.info("shipsStartRendering fired (count={})", shipsStartLogCount)
        }

        if (fieldResolveFailed) return@on
        val registry = CloakedShipsRegistry.getInstance()
        if (!registry.hasAnyCloakedShips()) return@on

        val renderer = event.renderer

        // Resolve the field once and cache it
        val field = shipRenderChunksField ?: run {
            try {
                // Walk up the class hierarchy (LevelRenderer has the field injected by VS2's mixin)
                var c: Class<*>? = renderer.javaClass
                var found: java.lang.reflect.Field? = null
                while (c != null && found == null) {
                    found = try { c.getDeclaredField("shipRenderChunks").also { it.isAccessible = true } }
                    catch (e: NoSuchFieldException) { null }
                    c = c.superclass
                }
                if (found == null) {
                    LOGGER.warn("shipRenderChunks field not found on {}. Vanilla cloaking suppression will not work. " +
                            "(Expected if Sodium/OptiFine is installed, or VS renderer is Flywheel-only.)", renderer.javaClass.name)
                    fieldResolveFailed = true
                    return@on
                }
                found.also { shipRenderChunksField = it }
            } catch (e: Exception) {
                LOGGER.warn("Failed to resolve shipRenderChunks field: {}", e.toString())
                fieldResolveFailed = true
                return@on
            }
        }

        try {
            @Suppress("UNCHECKED_CAST")
            val shipMap = field.get(renderer) as? java.util.WeakHashMap<*, *> ?: return@on
            if (shipMap.isEmpty()) return@on

            val player = Minecraft.getInstance().player
            var removedCount = 0

            val iter = shipMap.entries.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val ship = entry.key as? ClientShip ?: continue
                val shipId = ship.id
                if (!registry.isCloaked(shipId)) continue
                if (player != null && isPlayerAboard(ship)) continue
                iter.remove()
                removedCount++
                suppressedShips.add(shipId)
            }

            if (removedCount > 0 && shipsStartLogCount <= 10) {
                LOGGER.info("Removed {} cloaked ship(s) from shipRenderChunks", removedCount)
            }
        } catch (e: Exception) {
            LOGGER.warn("Error accessing shipRenderChunks: {}", e.toString())
        }
    }

    // ─── Fallback: renderShip ─────────────────────────────────────────────────
    // Fires per-ship AFTER shipsStartRendering. If the shipsStartRendering approach
    // worked, the ship was already removed from shipRenderChunks and this won't fire
    // for cloaked ships. Keep it as a belt-and-suspenders GL mask fallback.
    @Suppress("unused")
    private val preListener = VSGameEvents.renderShip.on { event ->
        val sid = event.ship.id
        val isCloaked = CloakedShipsRegistry.getInstance().isCloaked(sid)
        if (renderShipLogCount++ < 5) {
            LOGGER.info("renderShip event: ship={} cloaked={}", sid, isCloaked)
        }
        suppressing = isCloaked && shouldSuppress(event.ship, event.ship.worldAABB)
        if (suppressing) {
            LOGGER.info("GL SUPPRESSING render for ship {}", sid)
            savedChunks.clear()
            savedChunks.addAll(event.chunks)
            event.chunks.clear()
            RenderSystem.colorMask(false, false, false, false)
            RenderSystem.depthMask(false)
        }
    }

    @Suppress("unused")
    private val postListener = VSGameEvents.postRenderShip.on { event ->
        if (suppressing) {
            event.chunks.clear()
            @Suppress("UNCHECKED_CAST")
            (event.chunks as it.unimi.dsi.fastutil.objects.ObjectList<Any>).addAll(savedChunks)
            savedChunks.clear()
            RenderSystem.colorMask(true, true, true, true)
            RenderSystem.depthMask(true)
            suppressing = false
        }
    }

    fun register() {
        LOGGER.info("CloakRenderSuppressor registered (vanilla renderShip + shipsStartRendering listeners active)")
    }

    private fun shouldSuppress(ship: ClientShip, aabb: AABBdc): Boolean {
        if (!CloakedShipsRegistry.getInstance().isCloaked(ship.id)) return false
        val player = Minecraft.getInstance().player ?: return true
        return !isPlayerAboard(ship)
    }

    private fun isPlayerAboard(ship: ClientShip): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return try {
            val aabb = ship.worldAABB
            val t = 2.0
            player.x >= aabb.minX() - t && player.x <= aabb.maxX() + t &&
            player.y >= aabb.minY() - t && player.y <= aabb.maxY() + t &&
            player.z >= aabb.minZ() - t && player.z <= aabb.maxZ() + t
        } catch (e: Exception) { false }
    }
}
