package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.anomaly.AnomalyInstance
import com.mechanicalskies.vsshields.anomaly.AnomalyManager
import com.mechanicalskies.vsshields.config.ShieldConfig
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.eventbus.api.SubscribeEvent

/**
 * Admin commands for the Aetheric Anomaly system.
 * All under /vs_shields anomaly ...
 *
 * Permission level 2 (op).
 */
object AnomalyCommandHandler {

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher
        registerCommands(dispatcher)
    }

    private fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("vs_shields")
                .then(
                    Commands.literal("anomaly")
                        .requires { it.hasPermission(2) }
                        .then(
                            Commands.literal("spawn")
                                .executes { ctx -> spawnRandom(ctx) }
                                .then(
                                    Commands.argument("x", DoubleArgumentType.doubleArg())
                                        .then(
                                            Commands.argument("y", DoubleArgumentType.doubleArg())
                                                .then(
                                                    Commands.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes { ctx -> spawnAt(ctx) }
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("despawn")
                                .executes { ctx -> despawn(ctx) }
                        )
                        .then(
                            Commands.literal("info")
                                .executes { ctx -> info(ctx) }
                        )
                        .then(
                            Commands.literal("timer")
                                .then(
                                    Commands.literal("set")
                                        .then(
                                            Commands.argument("seconds", IntegerArgumentType.integer(1))
                                                .executes { ctx -> timerSet(ctx) }
                                        )
                                )
                        )
                        .then(
                            Commands.literal("reload")
                                .executes { ctx -> reload(ctx) }
                        )
                )
        )
    }

    private fun spawnRandom(ctx: CommandContext<CommandSourceStack>): Int {
        val mgr = AnomalyManager.getInstance()
        val started = mgr.spawnAnomaly()
        if (started) {
            val sx = AnomalySpawner.getPendingCenterX().toInt()
            val sy = AnomalySpawner.getPendingCenterY().toInt()
            val sz = AnomalySpawner.getPendingCenterZ().toInt()
            val blocks = AnomalySpawner.getPendingTotalBlocks()
            ctx.source.sendSuccess(
                { Component.literal("[Anomaly] Spawning at ($sx, $sy, $sz), $blocks blocks materializing...") },
                true
            )
            return 1
        } else {
            ctx.source.sendFailure(Component.literal("[Anomaly] Failed to spawn. Check server logs."))
            return 0
        }
    }

    private fun spawnAt(ctx: CommandContext<CommandSourceStack>): Int {
        val x = DoubleArgumentType.getDouble(ctx, "x")
        val y = DoubleArgumentType.getDouble(ctx, "y")
        val z = DoubleArgumentType.getDouble(ctx, "z")

        val mgr = AnomalyManager.getInstance()
        val started = mgr.spawnAnomalyAt(x, y, z)
        if (started) {
            val blocks = AnomalySpawner.getPendingTotalBlocks()
            ctx.source.sendSuccess(
                { Component.literal("[Anomaly] Spawning at (${x.toInt()}, ${y.toInt()}, ${z.toInt()}), $blocks blocks materializing...") },
                true
            )
            return 1
        } else {
            ctx.source.sendFailure(Component.literal("[Anomaly] Failed to spawn at specified position."))
            return 0
        }
    }

    private fun despawn(ctx: CommandContext<CommandSourceStack>): Int {
        val mgr = AnomalyManager.getInstance()
        val active = mgr.active
        val spawning = mgr.isSpawning
        if (active == null && !spawning) {
            ctx.source.sendFailure(Component.literal("[Anomaly] No active anomaly to despawn."))
            return 0
        }
        mgr.despawnImmediate()
        if (spawning) {
            ctx.source.sendSuccess(
                { Component.literal("[Anomaly] Cancelled pending spawn.") },
                true
            )
        } else {
            ctx.source.sendSuccess(
                { Component.literal("[Anomaly] Despawned (shipId=${active!!.shipId}).") },
                true
            )
        }
        return 1
    }

    private fun info(ctx: CommandContext<CommandSourceStack>): Int {
        val mgr = AnomalyManager.getInstance()

        // Show spawning status if in progress
        if (mgr.isSpawning) {
            val sx = AnomalySpawner.getPendingCenterX().toInt()
            val sy = AnomalySpawner.getPendingCenterY().toInt()
            val sz = AnomalySpawner.getPendingCenterZ().toInt()
            val blocks = AnomalySpawner.getPendingTotalBlocks()
            ctx.source.sendSuccess(
                { Component.literal("[Anomaly] Spawning in progress at ($sx, $sy, $sz), $blocks blocks...") },
                false
            )
            return 1
        }

        val active = mgr.active
        if (active == null) {
            ctx.source.sendSuccess(
                { Component.literal("[Anomaly] No active anomaly.") },
                false
            )
            return 1
        }

        val config = ShieldConfig.get().anomaly
        val tick = mgr.currentTick
        val globalRemaining = active.getGlobalTTLRemaining(tick, config.globalLifetimeTicks) / 20
        val extractionRemaining = active.getExtractionRemaining(tick, config.extractionTimerTicks)
        val extractionStr = if (extractionRemaining < 0) "not started" else "${extractionRemaining / 20}s"

        val msg = """
            |[Anomaly] Info:
            |  Position: (${active.worldX.toInt()}, ${active.worldY.toInt()}, ${active.worldZ.toInt()})
            |  ShipId: ${active.shipId}
            |  Phase: ${active.phase}
            |  Total blocks: ${active.totalBlocks}
            |  Dissolved: ${active.dissolvedBlocks}
            |  Global TTL: ${globalRemaining}s
            |  Extraction: $extractionStr
            |  Dimension: ${active.dimensionId}
        """.trimMargin()

        ctx.source.sendSuccess({ Component.literal(msg) }, false)
        return 1
    }

    private fun timerSet(ctx: CommandContext<CommandSourceStack>): Int {
        val seconds = IntegerArgumentType.getInteger(ctx, "seconds")
        val mgr = AnomalyManager.getInstance()
        mgr.overrideGlobalTTL(seconds)
        ctx.source.sendSuccess(
            { Component.literal("[Anomaly] Timer set to ${seconds}s.") },
            true
        )
        return 1
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        try {
            val gamedir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
            com.mechanicalskies.vsshields.VSShieldsMod.loadConfig(gamedir)
            ctx.source.sendSuccess(
                { Component.literal("[Anomaly] Config reloaded from disk.") },
                true
            )
            return 1
        } catch (e: Exception) {
            ctx.source.sendFailure(Component.literal("[Anomaly] Failed to reload: ${e.message}"))
            return 0
        }
    }
}
