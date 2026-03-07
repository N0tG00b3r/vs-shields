package com.mechanicalskies.vsshields.forge.client

import com.mechanicalskies.vsshields.client.CloakedShipsRegistry
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.joml.Matrix4f
import org.joml.primitives.AABBdc
import org.valkyrienskies.mod.common.shipObjectWorld

/**
 * Renders a "Predator + Hologram" shimmer ellipsoid around cloaked ships
 * for EXTERNAL players (not crew aboard).
 *
 * Visual: fresnel edge glow (bright teal edges, transparent center) +
 * horizontal scan line bands + vertical sweep pulse every ~4s.
 * Color: monochrome teal/cyan. Balanced to not break invisibility.
 */
object CloakExternalShimmerRenderer {

    private const val STACKS = 28
    private const val SLICES = 24

    // Teal/cyan color
    private const val COLOR_R = 0.1f
    private const val COLOR_G = 0.85f
    private const val COLOR_B = 0.75f

    // Scan line parameters
    private const val BAND_COUNT = 10.0
    private const val BAND_SHARPNESS = 4.0

    // Fresnel parameters — edges glow, center nearly invisible
    private const val FRESNEL_MIN = 0.003f
    private const val FRESNEL_MAX = 0.12f

    // Sweep pulse: bottom-to-top every 4 seconds
    private const val PULSE_BOOST = 0.05f
    private const val PULSE_PERIOD_MS = 4000L
    private const val PULSE_WIDTH = 6.0

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return

        val registry = CloakedShipsRegistry.getInstance()
        if (!registry.hasAnyCloakedShips()) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val camPos = mc.gameRenderer.mainCamera.position

        val shipIds = registry.cloakedShipIds
        if (shipIds.isEmpty()) return

        val level = mc.level ?: return
        val shipWorld = try { level.shipObjectWorld } catch (_: Exception) { return }

        val now = System.currentTimeMillis()

        for (shipId in shipIds) {
            val ship = try {
                shipWorld.loadedShips.getById(shipId)
            } catch (_: Exception) { null } ?: continue

            val aabb: AABBdc = try { ship.worldAABB } catch (_: Exception) { continue }

            // Skip if player is aboard this ship
            val tolerance = 2.0
            if (player.x >= aabb.minX() - tolerance && player.x <= aabb.maxX() + tolerance &&
                player.y >= aabb.minY() - tolerance && player.y <= aabb.maxY() + tolerance &&
                player.z >= aabb.minZ() - tolerance && player.z <= aabb.maxZ() + tolerance) {
                continue
            }

            val cx = (aabb.minX() + aabb.maxX()) / 2.0 - camPos.x
            val cy = (aabb.minY() + aabb.maxY()) / 2.0 - camPos.y
            val cz = (aabb.minZ() + aabb.maxZ()) / 2.0 - camPos.z

            val rx = ((aabb.maxX() - aabb.minX()) / 2.0 + 1.5).toFloat()
            val ry = ((aabb.maxY() - aabb.minY()) / 2.0 + 1.5).toFloat()
            val rz = ((aabb.maxZ() - aabb.minZ()) / 2.0 + 1.5).toFloat()

            // Camera direction to sphere center in scaled ellipsoid space (for fresnel)
            val scaledCX = (cx / rx).toFloat()
            val scaledCY = (cy / ry).toFloat()
            val scaledCZ = (cz / rz).toFloat()
            val camLen = Math.sqrt((scaledCX * scaledCX + scaledCY * scaledCY + scaledCZ * scaledCZ).toDouble()).toFloat()
            val camDirX = if (camLen > 0.001f) scaledCX / camLen else 0f
            val camDirY = if (camLen > 0.001f) scaledCY / camLen else 0f
            val camDirZ = if (camLen > 0.001f) scaledCZ / camLen else 1f

            // Sweep pulse Y position: -1 (bottom) → +1 (top)
            val pulsePhase = (now % PULSE_PERIOD_MS) / PULSE_PERIOD_MS.toFloat()
            val pulseY = -1.0f + 2.0f * pulsePhase

            // Gentle global breathing
            val breathe = (now % 6000) / 6000.0f
            val breatheMul = 1.0f + Math.sin(breathe.toDouble() * Math.PI * 2.0).toFloat() * 0.15f

            val poseStack = event.poseStack
            poseStack.pushPose()
            poseStack.translate(cx, cy, cz)
            poseStack.scale(rx, ry, rz)

            renderSphere(poseStack.last().pose(), camDirX, camDirY, camDirZ, pulseY, breatheMul, now)

            poseStack.popPose()
        }
    }

    private fun renderSphere(matrix: Matrix4f, camDirX: Float, camDirY: Float, camDirZ: Float,
                              pulseY: Float, breatheMul: Float, now: Long) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableCull()
        RenderSystem.depthMask(false)
        RenderSystem.setShader(GameRenderer::getPositionColorShader)

        val tesselator = Tesselator.getInstance()
        val builder = tesselator.builder
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)

        val wt = (now % 8000) / 8000.0 * Math.PI * 2.0

        for (stack in 0 until STACKS) {
            val phi1 = Math.PI * stack / STACKS
            val phi2 = Math.PI * (stack + 1) / STACKS
            val s1 = Math.sin(phi1).toFloat(); val c1 = Math.cos(phi1).toFloat()
            val s2 = Math.sin(phi2).toFloat(); val c2 = Math.cos(phi2).toFloat()

            // Scan line intensity: sine-based banding by latitude
            val phiMid = (phi1 + phi2) / 2.0
            val scanLine = Math.pow(Math.abs(Math.sin(phiMid * BAND_COUNT)), BAND_SHARPNESS).toFloat()

            for (slice in 0 until SLICES) {
                val t1 = 2.0 * Math.PI * slice / SLICES
                val t2 = 2.0 * Math.PI * (slice + 1) / SLICES
                val st1 = Math.sin(t1).toFloat(); val ct1 = Math.cos(t1).toFloat()
                val st2 = Math.sin(t2).toFloat(); val ct2 = Math.cos(t2).toFloat()

                // Wave distortion — organic wobble
                val w1 = 1.0f + (Math.sin((phi1 * 3.0 + t1 * 2.0 + wt) * 1.5) * 0.03
                        + Math.sin((phi1 * 5.0 - t1 * 3.0 + wt * 0.7) * 2.0) * 0.02).toFloat()
                val w2 = 1.0f + (Math.sin((phi1 * 3.0 + t2 * 2.0 + wt) * 1.5) * 0.03
                        + Math.sin((phi1 * 5.0 - t2 * 3.0 + wt * 0.7) * 2.0) * 0.02).toFloat()
                val w3 = 1.0f + (Math.sin((phi2 * 3.0 + t2 * 2.0 + wt) * 1.5) * 0.03
                        + Math.sin((phi2 * 5.0 - t2 * 3.0 + wt * 0.7) * 2.0) * 0.02).toFloat()
                val w4 = 1.0f + (Math.sin((phi2 * 3.0 + t1 * 2.0 + wt) * 1.5) * 0.03
                        + Math.sin((phi2 * 5.0 - t1 * 3.0 + wt * 0.7) * 2.0) * 0.02).toFloat()

                val x1 = s1 * ct1 * w1; val y1 = c1 * w1; val z1 = s1 * st1 * w1
                val x2 = s1 * ct2 * w2; val y2 = c1 * w2; val z2 = s1 * st2 * w2
                val x3 = s2 * ct2 * w3; val y3 = c2 * w3; val z3 = s2 * st2 * w3
                val x4 = s2 * ct1 * w4; val y4 = c2 * w4; val z4 = s2 * st1 * w4

                // Per-vertex alpha: fresnel + scan lines + pulse
                fun alpha(vx: Float, vy: Float, vz: Float): Float {
                    val dot = vx * camDirX + vy * camDirY + vz * camDirZ
                    val fresnel = 1.0f - Math.abs(dot.toDouble()).toFloat()
                    val fresnelAlpha = FRESNEL_MIN + (FRESNEL_MAX - FRESNEL_MIN) * fresnel * fresnel
                    val withBands = fresnelAlpha * (0.3f + 0.7f * scanLine)
                    val pd = (vy - pulseY).toDouble()
                    val pulse = PULSE_BOOST * Math.exp(-pd * pd * PULSE_WIDTH).toFloat()
                    return ((withBands + pulse) * breatheMul).coerceIn(0f, 0.22f)
                }

                val a1 = alpha(x1, y1, z1)
                val a2 = alpha(x2, y2, z2)
                val a3 = alpha(x3, y3, z3)
                val a4 = alpha(x4, y4, z4)

                builder.vertex(matrix, x1, y1, z1).color(COLOR_R, COLOR_G, COLOR_B, a1).endVertex()
                builder.vertex(matrix, x2, y2, z2).color(COLOR_R, COLOR_G, COLOR_B, a2).endVertex()
                builder.vertex(matrix, x3, y3, z3).color(COLOR_R, COLOR_G, COLOR_B, a3).endVertex()

                builder.vertex(matrix, x1, y1, z1).color(COLOR_R, COLOR_G, COLOR_B, a1).endVertex()
                builder.vertex(matrix, x3, y3, z3).color(COLOR_R, COLOR_G, COLOR_B, a3).endVertex()
                builder.vertex(matrix, x4, y4, z4).color(COLOR_R, COLOR_G, COLOR_B, a4).endVertex()
            }
        }

        tesselator.end()

        RenderSystem.depthMask(true)
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
    }
}
