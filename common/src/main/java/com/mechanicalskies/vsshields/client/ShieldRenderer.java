package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.network.ClientShieldManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShieldRenderer implements BlockEntityRenderer<ShieldGeneratorBlockEntity> {

    private static final int SPHERE_STACKS = 16;
    private static final int SPHERE_SLICES = 24;
    private static final float ALPHA = 0.35f;
    private static final float PULSE_SPEED = 0.03f;
    private static final float PULSE_AMPLITUDE = 0.05f;

    public ShieldRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShieldGeneratorBlockEntity be, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null)
            return;
        Ship ship = VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos());
        if (ship == null)
            return;

        ClientShieldManager.ClientShieldData data = ClientShieldManager.getInstance().getShield(ship.getId());
        if (data == null || !data.active || data.currentHP <= 0)
            return;

        AABBic shipAABB = ship.getShipAABB();
        if (shipAABB == null)
            return;

        // AABBic is a JOML interface with minX()/minY()/minZ()/maxX()/maxY()/maxZ()
        // methods.
        // These are shipyard-space coordinates, matching the BER's coordinate space.
        AABB renderBounds = new AABB(
                shipAABB.minX(), shipAABB.minY(), shipAABB.minZ(),
                shipAABB.maxX(), shipAABB.maxY(), shipAABB.maxZ());

        double hpPercent = data.getHPPercent();
        float r, g, b;
        if (hpPercent > 0.5) {
            r = 0.2f;
            g = 0.4f;
            b = 1.0f;
        } else if (hpPercent > 0.25) {
            r = 1.0f;
            g = 0.8f;
            b = 0.2f;
        } else {
            r = 1.0f;
            g = 0.2f;
            b = 0.2f;
        }

        float time = (System.currentTimeMillis() % 10000) / 1000f;
        float alpha = ALPHA + (float) Math.sin(time * Math.PI * 2 * PULSE_SPEED * 10) * PULSE_AMPLITUDE;
        alpha = Math.max(0.15f, Math.min(0.5f, alpha));

        // --- Low-energy flicker effect (FE < 20%) ---
        double energyPct = data.energyPercent;
        if (energyPct < 0.20) {
            // Intensity ramps from 0 at 20% to 1 at 0%
            float intensity = (float) (1.0 - energyPct / 0.20);

            // Multi-frequency distortion: overlay fast + slow oscillations
            float fastFlicker = (float) Math.sin(time * 47.3) * 0.5f + 0.5f;
            float slowFlicker = (float) Math.sin(time * 7.1) * 0.5f + 0.5f;
            float combined = fastFlicker * 0.6f + slowFlicker * 0.4f;

            // Random dropout: brief moments where shield almost disappears
            float hash = (float) Math.abs(Math.sin(time * 113.7 + 7.3));
            boolean dropout = hash < 0.08f * intensity;

            if (dropout) {
                alpha *= 0.05f; // near-invisible flash
            } else {
                alpha *= (1.0f - intensity * 0.7f) + combined * intensity * 0.5f;
            }

            // Color shifts toward orange/red as energy drains
            r = r + (1.0f - r) * intensity * 0.5f;
            g = g * (1.0f - intensity * 0.4f);
            b = b * (1.0f - intensity * 0.7f);
        } else if (hpPercent < 0.25) {
            // Low HP flicker (but energy is fine): mild warning
            float flicker = (float) Math.sin(time * 37.0) * 0.5f + 0.5f;
            alpha *= 0.5f + flicker * 0.5f;
        }

        poseStack.pushPose();

        Vector3f center = new Vector3f((float) renderBounds.getCenter().x, (float) renderBounds.getCenter().y,
                (float) renderBounds.getCenter().z);
        Vector3f blockPosInShipyard = new Vector3f(be.getBlockPos().getX(), be.getBlockPos().getY(),
                be.getBlockPos().getZ());
        center.sub(blockPosInShipyard);

        poseStack.translate(center.x(), center.y(), center.z());

        float padding = (float) com.mechanicalskies.vsshields.config.ShieldConfig.get().getGeneral().shieldPadding;
        float sizeX = (float) (renderBounds.getXsize() / 2.0) + padding;
        float sizeY = (float) (renderBounds.getYsize() / 2.0) + padding;
        float sizeZ = (float) (renderBounds.getZsize() / 2.0) + padding;
        poseStack.scale(sizeX, sizeY, sizeZ);

        renderSphere(poseStack, 1.0f, r, g, b, alpha);

        poseStack.popPose();
    }

    private void renderSphere(PoseStack poseStack, float radius, float r, float g, float b, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = poseStack.last().pose();

        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int stack = 0; stack < SPHERE_STACKS; stack++) {
            double phi1 = Math.PI * stack / SPHERE_STACKS;
            double phi2 = Math.PI * (stack + 1) / SPHERE_STACKS;
            float sinPhi1 = (float) Math.sin(phi1);
            float cosPhi1 = (float) Math.cos(phi1);
            float sinPhi2 = (float) Math.sin(phi2);
            float cosPhi2 = (float) Math.cos(phi2);

            for (int slice = 0; slice < SPHERE_SLICES; slice++) {
                double theta1 = 2.0 * Math.PI * slice / SPHERE_SLICES;
                double theta2 = 2.0 * Math.PI * (slice + 1) / SPHERE_SLICES;
                float sinTheta1 = (float) Math.sin(theta1);
                float cosTheta1 = (float) Math.cos(theta1);
                float sinTheta2 = (float) Math.sin(theta2);
                float cosTheta2 = (float) Math.cos(theta2);

                float x1 = radius * sinPhi1 * cosTheta1;
                float y1 = radius * cosPhi1;
                float z1 = radius * sinPhi1 * sinTheta1;

                float x2 = radius * sinPhi1 * cosTheta2;
                float y2 = radius * cosPhi1;
                float z2 = radius * sinPhi1 * sinTheta2;

                float x3 = radius * sinPhi2 * cosTheta2;
                float y3 = radius * cosPhi2;
                float z3 = radius * sinPhi2 * sinTheta2;

                float x4 = radius * sinPhi2 * cosTheta1;
                float y4 = radius * cosPhi2;
                float z4 = radius * sinPhi2 * sinTheta1;

                builder.vertex(matrix, x1, y1, z1).color(r, g, b, alpha).endVertex();
                builder.vertex(matrix, x2, y2, z2).color(r, g, b, alpha).endVertex();
                builder.vertex(matrix, x3, y3, z3).color(r, g, b, alpha).endVertex();

                builder.vertex(matrix, x1, y1, z1).color(r, g, b, alpha).endVertex();
                builder.vertex(matrix, x3, y3, z3).color(r, g, b, alpha).endVertex();
                builder.vertex(matrix, x4, y4, z4).color(r, g, b, alpha).endVertex();
            }
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @Override
    public boolean shouldRenderOffScreen(ShieldGeneratorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
