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
 * Renders a subtle translucent shimmer shell around a cloaked ship.
 * Only visible to players who are ON the cloaked ship (they see the cloak is active).
 * For players NOT on the ship, the ship is hidden by VS2ShipRenderMixin.
 */
public class CloakShimmerRenderer implements BlockEntityRenderer<CloakingFieldGeneratorBlockEntity> {

    private static final int STACKS = 14;
    private static final int SLICES = 20;

    public CloakShimmerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CloakingFieldGeneratorBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (be.getLevel() == null) return;

        Ship ship = VSGameUtilsKt.getShipManagingPos(be.getLevel(), be.getBlockPos());
        if (ship == null) return;

        // Only show shimmer if the ship is actively cloaked on the client
        if (!ClientCloakManager.getInstance().isCloaked(ship.getId())) return;

        // Only show shimmer to a player who is ON this ship (they can see their own cloak)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        org.valkyrienskies.core.api.ships.ClientShip playerShip =
            VSClientGameUtils.getClientShip(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (playerShip == null || playerShip.getId() != ship.getId()) return;

        AABBic shipAABB = ship.getShipAABB();
        if (shipAABB == null) return;

        // BER coordinate space is shipyard-space relative to the block's position.
        // Compute offset from the generator block to the AABB center.
        float cx = (shipAABB.minX() + shipAABB.maxX()) / 2.0f - be.getBlockPos().getX();
        float cy = (shipAABB.minY() + shipAABB.maxY()) / 2.0f - be.getBlockPos().getY();
        float cz = (shipAABB.minZ() + shipAABB.maxZ()) / 2.0f - be.getBlockPos().getZ();

        float rx = (shipAABB.maxX() - shipAABB.minX()) / 2.0f + 1.5f;
        float ry = (shipAABB.maxY() - shipAABB.minY()) / 2.0f + 1.5f;
        float rz = (shipAABB.maxZ() - shipAABB.minZ()) / 2.0f + 1.5f;

        // Gentle pulsing alpha
        float time = (System.currentTimeMillis() % 6000) / 6000.0f;
        float alpha = 0.06f + (float) (Math.sin(time * Math.PI * 2.0) * 0.025);

        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        poseStack.scale(rx, ry, rz);

        renderShimmerSphere(poseStack, alpha);

        poseStack.popPose();
    }

    private void renderShimmerSphere(PoseStack poseStack, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        Matrix4f matrix = poseStack.last().pose();

        // Teal shimmer — distinct from the energy shield colours
        float r = 0.15f, g = 0.85f, b = 0.75f;

        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int stack = 0; stack < STACKS; stack++) {
            double phi1 = Math.PI * stack / STACKS;
            double phi2 = Math.PI * (stack + 1) / STACKS;
            float s1 = (float) Math.sin(phi1), c1 = (float) Math.cos(phi1);
            float s2 = (float) Math.sin(phi2), c2 = (float) Math.cos(phi2);

            for (int slice = 0; slice < SLICES; slice++) {
                double t1 = 2.0 * Math.PI * slice / SLICES;
                double t2 = 2.0 * Math.PI * (slice + 1) / SLICES;
                float st1 = (float) Math.sin(t1), ct1 = (float) Math.cos(t1);
                float st2 = (float) Math.sin(t2), ct2 = (float) Math.cos(t2);

                float x1 = s1 * ct1, y1 = c1, z1 = s1 * st1;
                float x2 = s1 * ct2, y2 = c1, z2 = s1 * st2;
                float x3 = s2 * ct2, y3 = c2, z3 = s2 * st2;
                float x4 = s2 * ct1, y4 = c2, z4 = s2 * st1;

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
    public boolean shouldRenderOffScreen(CloakingFieldGeneratorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
