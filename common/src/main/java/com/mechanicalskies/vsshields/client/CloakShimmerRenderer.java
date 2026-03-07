package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.blockentity.CloakingFieldGeneratorBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.joml.Matrix4f;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Renders a "Predator + Hologram" shimmer shell around a cloaked ship.
 * Only visible to players who are ON the cloaked ship (crew sees the cloak is active).
 *
 * Visual: teal/cyan scan line bands + vertical sweep pulse.
 * No fresnel (camera is inside the sphere).
 * Matches the external renderer's visual language.
 */
public class CloakShimmerRenderer implements BlockEntityRenderer<CloakingFieldGeneratorBlockEntity> {

    private static final int STACKS = 28;
    private static final int SLICES = 24;

    // Teal/cyan — same as external renderer
    private static final float COLOR_R = 0.1f;
    private static final float COLOR_G = 0.85f;
    private static final float COLOR_B = 0.75f;

    // Scan line parameters — same frequency as external
    private static final double BAND_COUNT = 10.0;
    private static final double BAND_SHARPNESS = 4.0;

    // Base alpha (crew sees it subtler than external)
    private static final float BASE_ALPHA = 0.04f;

    // Sweep pulse
    private static final float PULSE_BOOST = 0.04f;
    private static final long PULSE_PERIOD_MS = 4000L;
    private static final double PULSE_WIDTH = 6.0;

    public CloakShimmerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CloakingFieldGeneratorBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null)
            return;

        Ship ship = VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos());
        if (ship == null)
            return;

        if (!CloakedShipsRegistry.getInstance().isCloaked(ship.getId()))
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        org.valkyrienskies.core.api.ships.ClientShip playerShip = VSClientGameUtils.getClientShip(mc.player.getX(),
                mc.player.getY(), mc.player.getZ());
        if (playerShip == null || playerShip.getId() != ship.getId())
            return;

        AABBic shipAABB = ship.getShipAABB();
        if (shipAABB == null)
            return;

        float cx = (shipAABB.minX() + shipAABB.maxX()) / 2.0f - be.getBlockPos().getX();
        float cy = (shipAABB.minY() + shipAABB.maxY()) / 2.0f - be.getBlockPos().getY();
        float cz = (shipAABB.minZ() + shipAABB.maxZ()) / 2.0f - be.getBlockPos().getZ();

        float rx = (shipAABB.maxX() - shipAABB.minX()) / 2.0f + 1.5f;
        float ry = (shipAABB.maxY() - shipAABB.minY()) / 2.0f + 1.5f;
        float rz = (shipAABB.maxZ() - shipAABB.minZ()) / 2.0f + 1.5f;

        long now = System.currentTimeMillis();

        // Sweep pulse Y: -1 (bottom) → +1 (top)
        float pulsePhase = (now % PULSE_PERIOD_MS) / (float) PULSE_PERIOD_MS;
        float pulseY = -1.0f + 2.0f * pulsePhase;

        // Global breathing
        float breathe = (now % 6000) / 6000.0f;
        float breatheMul = 1.0f + (float) Math.sin(breathe * Math.PI * 2.0) * 0.20f;

        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        poseStack.scale(rx, ry, rz);

        renderShimmerSphere(poseStack.last().pose(), pulseY, breatheMul, now);

        poseStack.popPose();
    }

    private void renderShimmerSphere(Matrix4f matrix, float pulseY, float breatheMul, long now) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        double wt = (now % 8000) / 8000.0 * Math.PI * 2.0;

        for (int stack = 0; stack < STACKS; stack++) {
            double phi1 = Math.PI * stack / STACKS;
            double phi2 = Math.PI * (stack + 1) / STACKS;
            float s1 = (float) Math.sin(phi1), c1 = (float) Math.cos(phi1);
            float s2 = (float) Math.sin(phi2), c2 = (float) Math.cos(phi2);

            // Scan line intensity
            double phiMid = (phi1 + phi2) / 2.0;
            float scanLine = (float) Math.pow(Math.abs(Math.sin(phiMid * BAND_COUNT)), BAND_SHARPNESS);

            for (int slice = 0; slice < SLICES; slice++) {
                double t1 = 2.0 * Math.PI * slice / SLICES;
                double t2 = 2.0 * Math.PI * (slice + 1) / SLICES;
                float st1 = (float) Math.sin(t1), ct1 = (float) Math.cos(t1);
                float st2 = (float) Math.sin(t2), ct2 = (float) Math.cos(t2);

                // Wave distortion
                float w1 = 1.0f + (float) (Math.sin((phi1 * 3 + t1 * 2 + wt) * 1.5) * 0.03
                        + Math.sin((phi1 * 5 - t1 * 3 + wt * 0.7) * 2.0) * 0.02);
                float w2 = 1.0f + (float) (Math.sin((phi1 * 3 + t2 * 2 + wt) * 1.5) * 0.03
                        + Math.sin((phi1 * 5 - t2 * 3 + wt * 0.7) * 2.0) * 0.02);
                float w3 = 1.0f + (float) (Math.sin((phi2 * 3 + t2 * 2 + wt) * 1.5) * 0.03
                        + Math.sin((phi2 * 5 - t2 * 3 + wt * 0.7) * 2.0) * 0.02);
                float w4 = 1.0f + (float) (Math.sin((phi2 * 3 + t1 * 2 + wt) * 1.5) * 0.03
                        + Math.sin((phi2 * 5 - t1 * 3 + wt * 0.7) * 2.0) * 0.02);

                float x1 = s1 * ct1 * w1, y1 = c1 * w1, z1 = s1 * st1 * w1;
                float x2 = s1 * ct2 * w2, y2 = c1 * w2, z2 = s1 * st2 * w2;
                float x3 = s2 * ct2 * w3, y3 = c2 * w3, z3 = s2 * st2 * w3;
                float x4 = s2 * ct1 * w4, y4 = c2 * w4, z4 = s2 * st1 * w4;

                // Per-vertex alpha: base × scan line + pulse
                float a1 = vertexAlpha(y1, scanLine, pulseY, breatheMul);
                float a2 = vertexAlpha(y2, scanLine, pulseY, breatheMul);
                float a3 = vertexAlpha(y3, scanLine, pulseY, breatheMul);
                float a4 = vertexAlpha(y4, scanLine, pulseY, breatheMul);

                builder.vertex(matrix, x1, y1, z1).color(COLOR_R, COLOR_G, COLOR_B, a1).endVertex();
                builder.vertex(matrix, x2, y2, z2).color(COLOR_R, COLOR_G, COLOR_B, a2).endVertex();
                builder.vertex(matrix, x3, y3, z3).color(COLOR_R, COLOR_G, COLOR_B, a3).endVertex();

                builder.vertex(matrix, x1, y1, z1).color(COLOR_R, COLOR_G, COLOR_B, a1).endVertex();
                builder.vertex(matrix, x3, y3, z3).color(COLOR_R, COLOR_G, COLOR_B, a3).endVertex();
                builder.vertex(matrix, x4, y4, z4).color(COLOR_R, COLOR_G, COLOR_B, a4).endVertex();
            }
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static float vertexAlpha(float vy, float scanLine, float pulseY, float breatheMul) {
        float base = BASE_ALPHA * (0.3f + 0.7f * scanLine);
        double pd = vy - pulseY;
        float pulse = PULSE_BOOST * (float) Math.exp(-pd * pd * PULSE_WIDTH);
        return Math.min((base + pulse) * breatheMul, 0.13f);
    }

    @Override
    public boolean shouldRenderOffScreen(CloakingFieldGeneratorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
