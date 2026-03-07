package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.network.ClientShieldManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShieldRenderer implements BlockEntityRenderer<ShieldGeneratorBlockEntity> {

    private static final int SPHERE_STACKS = TruncatedIcosahedronMesh.STACKS;
    private static final int SPHERE_SLICES = TruncatedIcosahedronMesh.SLICES;
    private static final float ALPHA = 0.85f;
    private static final float PULSE_SPEED = 0.03f;
    private static final float PULSE_AMPLITUDE = 0.06f;
    private static final int FULL_BRIGHT = LightTexture.pack(15, 15);

    // Precomputed fresnel lookup table (256 entries, indexed by quantized NdotV)
    private static final float[] FRESNEL_LUT = new float[256];
    // Precomputed UV and poleFade per stack/slice
    private static final float[] SLICE_U;
    private static final float[] STACK_TV;
    private static final float[] POLE_FADE;

    static {
        for (int i = 0; i < 256; i++) {
            float NdotV = i / 255f;
            FRESNEL_LUT[i] = 0.65f + 0.35f * (float) Math.pow(1.0 - NdotV, 2.5);
        }
        SLICE_U = new float[SPHERE_SLICES + 1];
        for (int s = 0; s <= SPHERE_SLICES; s++) {
            SLICE_U[s] = (float) s / SPHERE_SLICES;
        }
        STACK_TV = new float[SPHERE_STACKS + 1];
        POLE_FADE = new float[SPHERE_STACKS + 1];
        for (int st = 0; st <= SPHERE_STACKS; st++) {
            double phi = Math.PI * st / SPHERE_STACKS;
            STACK_TV[st] = (float) (phi / Math.PI);
            POLE_FADE[st] = 0.40f + 0.60f * (float) Math.sin(phi);
        }
    }

    // Reusable vectors for per-vertex normal transform (avoids allocation in hot loop)
    private final Vector3f viewN1 = new Vector3f();
    private final Vector3f viewN2 = new Vector3f();
    private final Vector3f viewN3 = new Vector3f();
    private final Vector3f viewN4 = new Vector3f();

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

        if (!ShieldConfig.get().getGeneral().showShieldBubble)
            return;

        ClientShieldManager.ClientShieldData data = ClientShieldManager.getInstance().getShield(ship.getId());

        // Track wasActive=false when shield is off so off→on toggle triggers activation wave
        if (data != null && !data.active) {
            ShieldPanelAnimator anim = ClientShieldManager.getInstance().getAnimator(ship.getId());
            if (anim != null) anim.wasActive = false;
        }

        if (data == null || !data.active || data.currentHP <= 0)
            return;

        float padding = (float) ShieldConfig.get().getGeneral().shieldPadding;

        AABBic shipAABB = ship.getShipAABB();
        if (shipAABB == null)
            return;

        AABB renderBounds = new AABB(
                shipAABB.minX(), shipAABB.minY(), shipAABB.minZ(),
                shipAABB.maxX(), shipAABB.maxY(), shipAABB.maxZ());

        double hpPercent = data.getHPPercent();
        float r, g, b;
        if (hpPercent > 0.5) {
            r = 0.15f;
            g = 0.5f;
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
        alpha = Math.max(0.50f, Math.min(0.90f, alpha));

        // --- Cloaking override ---
        boolean isCloaked = CloakedShipsRegistry.getInstance().isCloaked(ship.getId());
        if (isCloaked) {
            r = 1.0f;
            g = 0.3f;
            b = 0.8f;
            alpha = 0.08f;
        }

        // --- Low-energy flicker effect (FE < 20%) ---
        double energyPct = data.energyPercent;
        if (energyPct < 0.20) {
            float intensity = (float) (1.0 - energyPct / 0.20);

            float fastFlicker = (float) Math.sin(time * 47.3) * 0.5f + 0.5f;
            float slowFlicker = (float) Math.sin(time * 7.1) * 0.5f + 0.5f;
            float combined = fastFlicker * 0.6f + slowFlicker * 0.4f;

            float hash = (float) Math.abs(Math.sin(time * 113.7 + 7.3));
            boolean dropout = hash < 0.08f * intensity;

            if (dropout) {
                alpha *= 0.05f;
            } else {
                alpha *= (1.0f - intensity * 0.7f) + combined * intensity * 0.5f;
            }

            r = r + (1.0f - r) * intensity * 0.5f;
            g = g * (1.0f - intensity * 0.4f);
            b = b * (1.0f - intensity * 0.7f);
        } else if (hpPercent < 0.25) {
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

        // Panel animator — per-face animations (hit flash, activation, dropout, recharge)
        // Activation wave detection is inside tick() with firstTick skip for login
        ShieldPanelAnimator animator = ClientShieldManager.getInstance().getOrCreateAnimator(ship.getId());
        if (!isCloaked) {
            Vector3f genDir = new Vector3f(
                    be.getBlockPos().getX() - (float) renderBounds.getCenter().x,
                    be.getBlockPos().getY() - (float) renderBounds.getCenter().y,
                    be.getBlockPos().getZ() - (float) renderBounds.getCenter().z);
            float genLen = genDir.length();
            if (genLen > 0.001f) genDir.div(genLen);
            animator.tick(hpPercent, data.active, data.currentHP,
                    genDir.x(), genDir.y(), genDir.z());
        }

        boolean hideInside = ShieldConfig.get().getGeneral().hideShieldBubbleInside;

        // Distance LOD: compute world-space distance from camera to shield center
        net.minecraft.world.phys.Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        org.joml.Vector3dc shipWorldPos = ship.getTransform().getPositionInWorld();
        double distSq = camPos.distanceToSqr(shipWorldPos.x(), shipWorldPos.y(), shipWorldPos.z());

        renderSphere(poseStack, bufferSource, r, g, b, alpha, isCloaked, hideInside, hpPercent, animator, distSq);

        poseStack.popPose();
    }

    private static final double LOD_DISTANCE_SQ = 80.0 * 80.0; // 80 blocks

    private void renderSphere(PoseStack poseStack, MultiBufferSource bufferSource,
            float r, float g, float b, float alpha,
            boolean isCloaked, boolean hideInside, double hpPercent,
            ShieldPanelAnimator animator, double distSq) {

        ShieldConfig.GeneralConfig general = ShieldConfig.get().getGeneral();
        boolean farLOD = distSq > LOD_DISTANCE_SQ;
        float bloomIntensity = (isCloaked || farLOD) ? 0.0f : general.shieldBloomIntensity;
        // Layer 3 (energy streams) removed
        float conduitIntensity = (isCloaked || farLOD) ? 0.0f : general.shieldConduitIntensity;
        int tileCount = general.shieldHexTileCount;

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        RenderType baseType = hideInside
                ? ShieldRenderTypes.shieldTranslucentCulled()
                : ShieldRenderTypes.shieldTranslucent();
        VertexConsumer base = bufferSource.getBuffer(baseType);
        VertexConsumer bloom = bloomIntensity > 0
                ? bufferSource.getBuffer(ShieldRenderTypes.shieldBloom())
                : null;
        // Fill layer merged into base: fillAlpha acts as minimum alpha floor
        float fillAlpha = isCloaked ? 0.0f : 0.12f;
        VertexConsumer conduit = conduitIntensity > 0
                ? bufferSource.getBuffer(ShieldRenderTypes.shieldConduit())
                : null;

        int r255 = (int) (Math.min(1.0f, r) * 255);
        int g255 = (int) (Math.min(1.0f, g) * 255);
        int b255 = (int) (Math.min(1.0f, b) * 255);

        // White-shifted color for conduit layer
        int cR = Math.min(r255 + 80, 255);
        int cG = Math.min(g255 + 50, 255);
        int cB = Math.min(b255 + 30, 255);

        // RD texture is self-animated — no UV scroll for layers 0, 1, 2, 4.
        float scrollTime = (System.currentTimeMillis() % 120000) / 120000f; // 120-second full cycle

        // Layer 4: conduit pulse timing (scroll for traveling pulse effect, not for texture UV)
        float conduitPulseScrollV = scrollTime * tileCount * -0.8f;

        for (int stack = 0; stack < SPHERE_STACKS; stack++) {
            float poleFade1 = POLE_FADE[stack];
            float poleFade2 = POLE_FADE[stack + 1];

            float tv1 = STACK_TV[stack] * tileCount + 0.5f;
            float tv2 = STACK_TV[stack + 1] * tileCount + 0.5f;

            for (int slice = 0; slice < SPHERE_SLICES; slice++) {
                int sliceNext = (slice + 1) % SPHERE_SLICES;

                // Projected vertex positions from truncated icosahedron mesh
                float x1 = TruncatedIcosahedronMesh.PX[stack][slice];
                float y1 = TruncatedIcosahedronMesh.PY[stack][slice];
                float z1 = TruncatedIcosahedronMesh.PZ[stack][slice];

                float x2 = TruncatedIcosahedronMesh.PX[stack][sliceNext];
                float y2 = TruncatedIcosahedronMesh.PY[stack][sliceNext];
                float z2 = TruncatedIcosahedronMesh.PZ[stack][sliceNext];

                float x3 = TruncatedIcosahedronMesh.PX[stack + 1][sliceNext];
                float y3 = TruncatedIcosahedronMesh.PY[stack + 1][sliceNext];
                float z3 = TruncatedIcosahedronMesh.PZ[stack + 1][sliceNext];

                float x4 = TruncatedIcosahedronMesh.PX[stack + 1][slice];
                float y4 = TruncatedIcosahedronMesh.PY[stack + 1][slice];
                float z4 = TruncatedIcosahedronMesh.PZ[stack + 1][slice];

                // Edge factors per vertex (0=face center, 1=face edge)
                float e1 = TruncatedIcosahedronMesh.EDGE_FACTOR[stack][slice];
                float e2 = TruncatedIcosahedronMesh.EDGE_FACTOR[stack][sliceNext];
                float e3 = TruncatedIcosahedronMesh.EDGE_FACTOR[stack + 1][sliceNext];
                float e4 = TruncatedIcosahedronMesh.EDGE_FACTOR[stack + 1][slice];

                // Face indices for panel animations
                int fi1 = TruncatedIcosahedronMesh.FACE_INDEX[stack][slice];
                int fi2 = TruncatedIcosahedronMesh.FACE_INDEX[stack][sliceNext];
                int fi3 = TruncatedIcosahedronMesh.FACE_INDEX[stack + 1][sliceNext];
                int fi4 = TruncatedIcosahedronMesh.FACE_INDEX[stack + 1][slice];

                // Per-face animation multipliers
                float fm1 = animator != null ? animator.getFaceMultiplier(fi1) : 1.0f;
                float fm2 = animator != null ? animator.getFaceMultiplier(fi2) : 1.0f;
                float fm3 = animator != null ? animator.getFaceMultiplier(fi3) : 1.0f;
                float fm4 = animator != null ? animator.getFaceMultiplier(fi4) : 1.0f;
                float edgeBoost = animator != null ? animator.getRechargeEdgeBoost() : 0.0f;

                // UV (tiled via GL_REPEAT in TilingTextureState)
                float tu1 = SLICE_U[slice] * tileCount;
                float tu2 = SLICE_U[slice + 1] * tileCount;

                // Flat face normals for fresnel (same across entire face)
                float fnx1 = TruncatedIcosahedronMesh.FNX[stack][slice];
                float fny1 = TruncatedIcosahedronMesh.FNY[stack][slice];
                float fnz1 = TruncatedIcosahedronMesh.FNZ[stack][slice];

                float fnx2 = TruncatedIcosahedronMesh.FNX[stack][sliceNext];
                float fny2 = TruncatedIcosahedronMesh.FNY[stack][sliceNext];
                float fnz2 = TruncatedIcosahedronMesh.FNZ[stack][sliceNext];

                float fnx3 = TruncatedIcosahedronMesh.FNX[stack + 1][sliceNext];
                float fny3 = TruncatedIcosahedronMesh.FNY[stack + 1][sliceNext];
                float fnz3 = TruncatedIcosahedronMesh.FNZ[stack + 1][sliceNext];

                float fnx4 = TruncatedIcosahedronMesh.FNX[stack + 1][slice];
                float fny4 = TruncatedIcosahedronMesh.FNY[stack + 1][slice];
                float fnz4 = TruncatedIcosahedronMesh.FNZ[stack + 1][slice];

                // View-space normals + CPU fresnel using flat face normals
                float f1 = computeFresnel(normalMatrix, viewN1, fnx1, fny1, fnz1);
                float f2 = computeFresnel(normalMatrix, viewN2, fnx2, fny2, fnz2);
                float f3 = computeFresnel(normalMatrix, viewN3, fnx3, fny3, fnz3);
                float f4 = computeFresnel(normalMatrix, viewN4, fnx4, fny4, fnz4);

                // --- Layer 1: Structural wireframe + fill merged (edge-weighted + fill floor + faceMul) ---
                int a1 = clampAlpha(Math.max(fillAlpha, alpha * ((0.15f + 0.85f * e1) + edgeBoost * e1)) * f1 * poleFade1 * fm1);
                int a2 = clampAlpha(Math.max(fillAlpha, alpha * ((0.15f + 0.85f * e2) + edgeBoost * e2)) * f2 * poleFade1 * fm2);
                int a3 = clampAlpha(Math.max(fillAlpha, alpha * ((0.15f + 0.85f * e3) + edgeBoost * e3)) * f3 * poleFade2 * fm3);
                int a4 = clampAlpha(Math.max(fillAlpha, alpha * ((0.15f + 0.85f * e4) + edgeBoost * e4)) * f4 * poleFade2 * fm4);

                emitVertex(base, matrix, x1, y1, z1, r255, g255, b255, a1, tu1, tv1, viewN1);
                emitVertex(base, matrix, x2, y2, z2, r255, g255, b255, a2, tu2, tv1, viewN2);
                emitVertex(base, matrix, x3, y3, z3, r255, g255, b255, a3, tu2, tv2, viewN3);

                emitVertex(base, matrix, x1, y1, z1, r255, g255, b255, a1, tu1, tv1, viewN1);
                emitVertex(base, matrix, x3, y3, z3, r255, g255, b255, a3, tu2, tv2, viewN3);
                emitVertex(base, matrix, x4, y4, z4, r255, g255, b255, a4, tu1, tv2, viewN4);

                // --- Layer 2: Bloom (edge-weighted + recharge boost + faceMul) ---
                if (bloom != null) {
                    int ba1 = clampAlpha(alpha * f1 * poleFade1 * bloomIntensity * ((0.15f + 0.85f * e1) + edgeBoost * e1) * fm1);
                    int ba2 = clampAlpha(alpha * f2 * poleFade1 * bloomIntensity * ((0.15f + 0.85f * e2) + edgeBoost * e2) * fm2);
                    int ba3 = clampAlpha(alpha * f3 * poleFade2 * bloomIntensity * ((0.15f + 0.85f * e3) + edgeBoost * e3) * fm3);
                    int ba4 = clampAlpha(alpha * f4 * poleFade2 * bloomIntensity * ((0.15f + 0.85f * e4) + edgeBoost * e4) * fm4);

                    emitVertex(bloom, matrix, x1, y1, z1, r255, g255, b255, ba1, tu1, tv1, viewN1);
                    emitVertex(bloom, matrix, x2, y2, z2, r255, g255, b255, ba2, tu2, tv1, viewN2);
                    emitVertex(bloom, matrix, x3, y3, z3, r255, g255, b255, ba3, tu2, tv2, viewN3);

                    emitVertex(bloom, matrix, x1, y1, z1, r255, g255, b255, ba1, tu1, tv1, viewN1);
                    emitVertex(bloom, matrix, x3, y3, z3, r255, g255, b255, ba3, tu2, tv2, viewN3);
                    emitVertex(bloom, matrix, x4, y4, z4, r255, g255, b255, ba4, tu1, tv2, viewN4);
                }

                // --- Layer 4: Conduit pulses (edge-weighted + faceMul) ---
                if (conduit != null) {
                    // Pulse timing uses scrolled V for traveling bands; texture UVs are static
                    float pv1 = tv1 + conduitPulseScrollV;
                    float pv2 = tv2 + conduitPulseScrollV;

                    float p1 = pulseFn(pv1, scrollTime, tileCount);
                    float p2 = p1;
                    float p3 = pulseFn(pv2, scrollTime, tileCount);
                    float p4 = p3;

                    int ca1 = clampAlpha(alpha * f1 * poleFade1 * conduitIntensity * p1 * (0.2f + 0.8f * e1) * fm1);
                    int ca2 = clampAlpha(alpha * f2 * poleFade1 * conduitIntensity * p2 * (0.2f + 0.8f * e2) * fm2);
                    int ca3 = clampAlpha(alpha * f3 * poleFade2 * conduitIntensity * p3 * (0.2f + 0.8f * e3) * fm3);
                    int ca4 = clampAlpha(alpha * f4 * poleFade2 * conduitIntensity * p4 * (0.2f + 0.8f * e4) * fm4);

                    emitVertex(conduit, matrix, x1, y1, z1, cR, cG, cB, ca1, tu1, tv1, viewN1);
                    emitVertex(conduit, matrix, x2, y2, z2, cR, cG, cB, ca2, tu2, tv1, viewN2);
                    emitVertex(conduit, matrix, x3, y3, z3, cR, cG, cB, ca3, tu2, tv2, viewN3);

                    emitVertex(conduit, matrix, x1, y1, z1, cR, cG, cB, ca1, tu1, tv1, viewN1);
                    emitVertex(conduit, matrix, x3, y3, z3, cR, cG, cB, ca3, tu2, tv2, viewN3);
                    emitVertex(conduit, matrix, x4, y4, z4, cR, cG, cB, ca4, tu1, tv2, viewN4);
                }
            }
        }
    }

    /**
     * Transform object-space normal to view space, normalize, compute fresnel.
     * Writes the view-space normal into {@code out} and returns the fresnel alpha factor.
     */
    private float computeFresnel(Matrix3f normalMatrix, Vector3f out, float nx, float ny, float nz) {
        out.set(nx, ny, nz);
        normalMatrix.transform(out);
        float len = out.length();
        if (len > 0.0001f) {
            out.div(len);
        }
        // NdotV ≈ abs(viewNormal.z) — camera looks along -Z in view space
        int idx = Math.min(255, (int) (Math.abs(out.z()) * 255f));
        return FRESNEL_LUT[idx];
    }

    private void emitVertex(VertexConsumer consumer, Matrix4f matrix,
            float x, float y, float z,
            int r, int g, int b, int a,
            float u, float v, Vector3f viewNormal) {
        consumer.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(viewNormal.x(), viewNormal.y(), viewNormal.z())
                .endVertex();
    }

    /**
     * Sharp pulse function for conduit energy packets.
     * Returns 0.0–1.0 based on scrolled V coordinate, creating narrow bright bands.
     */
    private static float pulseFn(float scrolledTV, float scrollTime, int tileCount) {
        float phase = scrolledTV * 6.0f + scrollTime * tileCount * 3.0f;
        return (float) Math.pow(Math.abs(Math.sin(phase * Math.PI)), 10.0);
    }

    private static int clampAlpha(float a) {
        return (int) (Math.max(0.0f, Math.min(1.0f, a)) * 255);
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
