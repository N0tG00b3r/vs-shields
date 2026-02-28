package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.config.ShieldConfig;
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
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShieldRenderer implements BlockEntityRenderer<ShieldGeneratorBlockEntity> {

    private static final int SPHERE_STACKS = 32;
    private static final int SPHERE_SLICES = 48;
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

        // Config: bubble rendering can be disabled globally
        if (!ShieldConfig.get().getGeneral().showShieldBubble)
            return;

        ClientShieldManager.ClientShieldData data = ClientShieldManager.getInstance().getShield(ship.getId());
        if (data == null || !data.active || data.currentHP <= 0)
            return;

        float padding = (float) ShieldConfig.get().getGeneral().shieldPadding;

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

        // --- Cloaking override ---
        if (CloakedShipsRegistry.getInstance().isCloaked(ship.getId())) {
            r = 1.0f;
            g = 0.3f;
            b = 0.8f;
            alpha = 0.08f;
        }

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

        float sizeX = (float) (renderBounds.getXsize() / 2.0) + padding;
        float sizeY = (float) (renderBounds.getYsize() / 2.0) + padding;
        float sizeZ = (float) (renderBounds.getZsize() / 2.0) + padding;
        poseStack.scale(sizeX, sizeY, sizeZ);

        boolean isCloaked = CloakedShipsRegistry.getInstance().isCloaked(ship.getId());
        boolean hideInside = ShieldConfig.get().getGeneral().hideShieldBubbleInside;
        renderSphere(poseStack, 1.0f, r, g, b, alpha, isCloaked, hideInside);

        poseStack.popPose();
    }

    // Honeycomb grid parameters
    private static final float HEX_SCALE = 8.0f; // How many hex cells across the sphere
    private static final float EDGE_WIDTH = 0.12f; // Width of hex edges (0-1, fraction of cell)
    private static final float EDGE_BRIGHTNESS = 1.2f; // How bright edges are vs fill
    private static final float FILL_ALPHA_MULT = 0.2f; // Alpha multiplier for hex cell interiors

    private void renderSphere(PoseStack poseStack, float radius, float r, float g, float b, float alpha,
            boolean isCloaked, boolean hideInside) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // hideInside=true: enableCull keeps only front faces (CCW from outside) → invisible from inside
        // hideInside=false: disableCull renders both sides → crew inside can see the grid
        if (hideInside) {
            RenderSystem.enableCull();
        } else {
            RenderSystem.disableCull();
        }
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = poseStack.last().pose();

        float time = (System.currentTimeMillis() % 60000) / 60000f; // slow rotation
        float rotOffset = time * (float) Math.PI * 2.0f;

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

                float x1 = radius * sinPhi1 * cosTheta1, y1 = radius * cosPhi1, z1 = radius * sinPhi1 * sinTheta1;
                float x2 = radius * sinPhi1 * cosTheta2, y2 = radius * cosPhi1, z2 = radius * sinPhi1 * sinTheta2;
                float x3 = radius * sinPhi2 * cosTheta2, y3 = radius * cosPhi2, z3 = radius * sinPhi2 * sinTheta2;
                float x4 = radius * sinPhi2 * cosTheta1, y4 = radius * cosPhi2, z4 = radius * sinPhi2 * sinTheta1;

                // Compute per-vertex honeycomb modulation
                float[] c1 = hexColor(phi1, theta1 + rotOffset, r, g, b, alpha);
                float[] c2 = hexColor(phi1, theta2 + rotOffset, r, g, b, alpha);
                float[] c3 = hexColor(phi2, theta2 + rotOffset, r, g, b, alpha);
                float[] c4 = hexColor(phi2, theta1 + rotOffset, r, g, b, alpha);

                // CCW winding from outside → front-facing. With hideInside=true (enableCull),
                // inside faces are automatically culled. With hideInside=false (disableCull),
                // both sides render so crew inside see the hex grid.
                builder.vertex(matrix, x1, y1, z1).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
                builder.vertex(matrix, x3, y3, z3).color(c3[0], c3[1], c3[2], c3[3]).endVertex();
                builder.vertex(matrix, x2, y2, z2).color(c2[0], c2[1], c2[2], c2[3]).endVertex();

                builder.vertex(matrix, x1, y1, z1).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
                builder.vertex(matrix, x4, y4, z4).color(c4[0], c4[1], c4[2], c4[3]).endVertex();
                builder.vertex(matrix, x3, y3, z3).color(c3[0], c3[1], c3[2], c3[3]).endVertex();
            }
        }

        tesselator.end();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Compute RGBA for a point on the sphere based on its distance to the nearest
     * hexagonal grid edge. Points on edges are brighter; cell interiors are dimmer.
     *
     * Edge detection uses the cube max-norm which equals exactly 1.0 uniformly
     * across the entire hexagon boundary (both straight sides and corners).
     */
    private float[] hexColor(double phi, double theta, float r, float g, float b, float baseAlpha) {
        // Equirectangular projection, scaled by HEX_SCALE
        float u = (float) (theta / (2.0 * Math.PI)) * HEX_SCALE * 2.0f;
        float v = (float) (phi / Math.PI) * HEX_SCALE;

        // UV → fractional axial (pointy-top hex, inverse of Cartesian→axial matrix)
        float sqrt3 = 1.7320508f;
        float q  = sqrt3 / 3.0f * u - 1.0f / 3.0f * v;
        float rr = 2.0f / 3.0f * v;

        // Cube coordinate rounding — snap to nearest hex center
        float s = -q - rr;
        int qi = Math.round(q);
        int ri = Math.round(rr);
        int si = Math.round(s);

        float qDiff = Math.abs(qi - q);
        float rDiff = Math.abs(ri - rr);
        float sDiff = Math.abs(si - s);
        if (qDiff > rDiff && qDiff > sDiff) {
            qi = -ri - si;
        } else if (rDiff > sDiff) {
            ri = -qi - si;
        }

        // Fractional offset from the hex center in cube coords
        float dq = q - qi;
        float dr = rr - ri;
        float ds = -dq - dr;

        // --- FIX: cube max-norm is exactly 0.5 on the full hexagon boundary ---
        // (at every edge midpoint AND every corner, max(|dq|,|dr|,|ds|) == 0.5)
        // Multiply by 2 to normalize to [0, 1]: 0 at center, 1 at boundary.
        float hexNorm  = 2.0f * Math.max(Math.abs(dq), Math.max(Math.abs(dr), Math.abs(ds)));
        float edgeDist = 1.0f - hexNorm;          // 1 at center, 0 at boundary
        float edgeFactor = smoothstep(0.0f, EDGE_WIDTH, edgeDist);

        // Edge: bright, full alpha. Fill: dimmer, lower alpha.
        float fillAlpha = baseAlpha * FILL_ALPHA_MULT;
        float edgeAlpha = Math.min(0.9f, baseAlpha * EDGE_BRIGHTNESS);
        float finalAlpha = lerp(edgeAlpha, fillAlpha, edgeFactor);

        // --- FIX: apply EDGE_BRIGHTNESS directly, without pre-clamping to 1.0 ---
        float finalR = lerp(Math.min(1.0f, r * EDGE_BRIGHTNESS), r * 0.6f, edgeFactor);
        float finalG = lerp(Math.min(1.0f, g * EDGE_BRIGHTNESS), g * 0.6f, edgeFactor);
        float finalB = lerp(Math.min(1.0f, b * EDGE_BRIGHTNESS), b * 0.6f, edgeFactor);

        // Fade near poles to hide equirectangular projection distortion
        float poleFade = (float) Math.sin(phi); // 0 at poles, 1 at equator
        finalAlpha *= 0.3f + 0.7f * poleFade;

        return new float[] { finalR, finalG, finalB, finalAlpha };
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
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
