package com.mechanicalskies.vsshields.forge

import com.mechanicalskies.vsshields.client.ClientAnalyzerData
import com.mechanicalskies.vsshields.client.HelmAnalyzerHandler
import com.mechanicalskies.vsshields.registry.ModItems
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.joml.Matrix4f

/**
 * Client-only renderer that draws wireframe outlines of scanned ship blocks.
 *
 * Phosphor-green color scheme (CRT/radar aesthetic).
 *
 * Precision fix: shipyard coordinates are in the millions — converting them to float32
 * causes ±1-block jitter. Instead, the world position of each block is computed in
 * double precision on the CPU; only the small camera-relative offset is passed to OpenGL.
 *
 * Registered on the Forge EVENT_BUS via [VSShieldsModForge] (client-only via DistExecutor).
 */
class AnalyzerOutlineRenderer {

    // Phosphor green: warm P31 CRT monitor colour
    private val COLOR_CANNON   = floatArrayOf(0.20f, 1.00f, 0.30f, 1.0f)  // bright phosphor green
    private val COLOR_CRITICAL = floatArrayOf(0.70f, 1.00f, 0.10f, 1.0f)  // lime-green for generators
    private val COLOR_MINE     = floatArrayOf(1.00f, 0.20f, 0.20f, 1.0f)  // red for mines

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        // Render while using the hand-held Ship Analyzer OR while the helm scanner is active
        val usingAnalyzer = InteractionHand.entries.any { hand ->
            val held = player.getItemInHand(hand)
            !held.isEmpty && held.item == ModItems.SHIP_ANALYZER.get() &&
                    player.isUsingItem && player.useItem === held
        }
        if (!usingAnalyzer && !HelmAnalyzerHandler.isScanning()) return

        val data = ClientAnalyzerData.getInstance()
        if (!data.isValid) return

        val camPos  = mc.gameRenderer.mainCamera.position
        val buffers = mc.renderBuffers().bufferSource()
        val poseStack = event.poseStack
        val m = data.shipToWorldMatrix  // column-major double[16]

        // Rotation-only 4x4 (upper-left 3×3 of shipToWorld, float-safe since values are in [-1, 1]).
        // This orients the box to match the ship's current rotation.
        val rotMatrix = Matrix4f(
            m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), 0f,
            m[4].toFloat(), m[5].toFloat(), m[6].toFloat(), 0f,
            m[8].toFloat(), m[9].toFloat(), m[10].toFloat(), 0f,
            0f,             0f,             0f,              1f
        )

        val lines = buffers.getBuffer(RenderType.lines())

        for (pos in data.cannonPositions) {
            renderOrientedBox(poseStack, lines, m, camPos, pos, rotMatrix, COLOR_CANNON)
        }
        for (pos in data.criticalPositions) {
            renderOrientedBox(poseStack, lines, m, camPos, pos, rotMatrix, COLOR_CRITICAL)
        }

        // Mines: world-space positions — no ship rotation transform needed
        for (mine in data.mineWorldPositions) {
            val rx = mine[0] - camPos.x
            val ry = mine[1] - camPos.y
            val rz = mine[2] - camPos.z
            poseStack.pushPose()
            poseStack.translate(rx - 0.25, ry - 0.25, rz - 0.25)
            LevelRenderer.renderLineBox(poseStack, lines,
                0.0, 0.0, 0.0, 0.5, 0.5, 0.5,
                COLOR_MINE[0], COLOR_MINE[1], COLOR_MINE[2], COLOR_MINE[3])
            poseStack.popPose()
        }

        buffers.endBatch(RenderType.lines())
    }

    /**
     * Renders a 1×1×1 wireframe box oriented to the ship's rotation.
     *
     * The block's world position is computed in double precision to avoid float32 jitter
     * at large shipyard coordinates. Only the small camera-relative offset is stored as float.
     */
    private fun renderOrientedBox(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        m: DoubleArray,
        camPos: Vec3,
        pos: BlockPos,
        rotMatrix: Matrix4f,
        color: FloatArray
    ) {
        val sx = pos.x.toDouble()
        val sy = pos.y.toDouble()
        val sz = pos.z.toDouble()

        // Compute block min-corner in world space (full double precision, no float truncation)
        val wx = m[0] * sx + m[4] * sy + m[8]  * sz + m[12]
        val wy = m[1] * sx + m[5] * sy + m[9]  * sz + m[13]
        val wz = m[2] * sx + m[6] * sy + m[10] * sz + m[14]

        // Camera-relative offset: small values, perfectly safe for float32
        val rx = wx - camPos.x
        val ry = wy - camPos.y
        val rz = wz - camPos.z

        poseStack.pushPose()
        poseStack.translate(rx, ry, rz)      // double overload → no precision loss
        poseStack.mulPoseMatrix(rotMatrix)   // orient box to ship rotation

        LevelRenderer.renderLineBox(
            poseStack, consumer,
            0.0, 0.0, 0.0, 1.0, 1.0, 1.0,
            color[0], color[1], color[2], color[3]
        )
        poseStack.popPose()
    }
}
