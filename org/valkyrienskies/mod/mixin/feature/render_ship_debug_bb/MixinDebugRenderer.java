package org.valkyrienskies.mod.mixin.feature.render_ship_debug_bb;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Intersectiond;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.primitives.AABBic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSClientGameUtils;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.DragInfoReporter;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {

    /**
     * This mixin renders ship bounding boxes and center of masses.
     *
     * <p>They get rendered in the same pass as entities.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void postRender(final PoseStack matrices, final MultiBufferSource.BufferSource vertexConsumersIgnore,
        final double cameraX, final double cameraY, final double cameraZ, final CallbackInfo ci) {

        final MultiBufferSource.BufferSource bufferSource =
            MultiBufferSource.m_109898_(Tesselator.m_85913_().m_85915_());
        final ClientLevel world = Minecraft.m_91087_().f_91073_;
        final VsiClientShipWorld shipObjectClientWorld = VSGameUtilsKt.getShipObjectWorld(world);

        if (Minecraft.m_91087_().m_91290_().m_114377_()) {
            // Further on coordinates will be relative to (0, 0, 0).
            matrices.m_85836_();
            matrices.m_85837_(-cameraX, -cameraY, -cameraZ);

            // This raycast is used to determine if player's line of sight to a ship is obstructed.
            // [Entity#pick] produces false results for blocks like tall grass, hence we do a verbose raycast
            // with a different ClipContext.Block.
            Entity camera = Minecraft.m_91087_().m_91288_();
            Vec3 eyeVec = camera.m_20299_(0.0F);
            Vec3 viewVec = camera.m_20252_(0.0F).m_82490_(20.0F);
            Vec3 targetVec = eyeVec.m_82549_(viewVec);
            HitResult hit = world.m_45547_(
                new ClipContext(
                    eyeVec,
                    targetVec,
                    Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    camera
                )
            );
            // Reduced debug info (gamerule) disables ability to see normal hitboxes, so we disable ship ones too
            if (Minecraft.m_91087_().m_91299_()) return;

            for (final ClientShip shipObjectClient : shipObjectClientWorld.getLoadedShips()) {
                final ShipTransform shipRenderTransform = shipObjectClient.getRenderTransform();
                final Vector3dc shipRenderPosition = shipRenderTransform.getPosition();

                // For visibility, gizmos and everything should not be obstructed by terrain and ship blocks.
                // This "x-ray" behavior is limited to reduce visual clutter and prevent seeing ships through walls.
                boolean xrayEligible =
                    // Allow for spectators and creative builders.
                    Minecraft.m_91087_().f_91074_.m_5833_() || Minecraft.m_91087_().f_91074_.m_7500_()
                        ||
                    // Force xray if player is already inside the ship.
                        shipObjectClient.getRenderAABB().containsPoint(
                            VectorConversionsMCKt.toJOML(Minecraft.m_91087_().f_91074_.m_20182_())
                        )
                        ||
                    // Otherwise, only allow if line of sight to ship is not obstructed by a solid block.
                        shipObjectClient.getRenderAABB().intersectsLineSegment(
                            cameraX, cameraY, cameraZ,
                            hit.f_82445_.f_82479_, hit.f_82445_.f_82480_, hit.f_82445_.f_82481_,
                            new Vector2d()
                        ) != Intersectiond.OUTSIDE
                    ;

                final AABBic shipAABB = shipObjectClient.getShipAABB();
                final AABB renderAABB = VectorConversionsMCKt.toMinecraft(shipObjectClient.getRenderAABB());

                if (shipAABB == null) {
                    // Ship with no blocks, something is wrong. Rendering a small marker in its position.
                    LevelRenderer
                        .m_109646_(matrices, bufferSource.m_6299_(xrayEligible ? XRAY_LINES : RenderType.f_110371_),
                            renderAABB.m_82400_(0.25),
                            1.0F, 0.0F, 0.0F, 1.0F);
                    continue;
                }

                final Vector3dc centerOfShip = shipAABB.center(new Vector3d());

                // Offset the AABB by -[centerOfShip] to fix floating point errors.
                final AABB shipVoxelAABBAfterOffset =
                    new AABB(
                        shipAABB.minX() - centerOfShip.x(),
                        shipAABB.minY() - centerOfShip.y(),
                        shipAABB.minZ() - centerOfShip.z(),
                        shipAABB.maxX() - centerOfShip.x(),
                        shipAABB.maxY() - centerOfShip.y(),
                        shipAABB.maxZ() - centerOfShip.z()
                    );

                // Now rendering for shipyard coordinates.
                matrices.m_85836_();
                // Offset the transform of the AABB by [centerOfShip] to account for [shipVoxelAABBAfterOffset]
                // being offset by -[centerOfShip].
                VSClientGameUtils.transformRenderWithShip(
                    shipRenderTransform, matrices,
                    centerOfShip.x(), centerOfShip.y(), centerOfShip.z(),
                    0, 0, 0);

                // Draw voxel AABB (extent of the ship in ship coordinates)
                LevelRenderer.m_109646_(
                    matrices, bufferSource.m_6299_(RenderType.f_110371_),
                    shipVoxelAABBAfterOffset,
                    0.5F, 0.0F, 0.0F, 1.0F);

                // Center of mass (0, 0, 0 in model) relative to the voxel AABB
                Vector3d centerOfMass = shipRenderTransform.getPositionInModel().sub(centerOfShip, new Vector3d());

                // Render center of mass as a small cube
                final double comBoxSize = .25;
                final AABB comBox = AABB.m_165882_(
                    VectorConversionsMCKt.toMinecraft(centerOfMass), comBoxSize, comBoxSize, comBoxSize
                );
                LevelRenderer.m_109646_(
                    matrices, bufferSource.m_6299_(xrayEligible ? XRAY_LINES : RenderType.f_110371_),
                    comBox,
                    250.0F / 255.0F, 194.0F / 255.0F, 19.0F / 255.0F, 1.0F
                );
                // Render gizmos (X, Y, Z axes from center of mass)
                if (xrayEligible) {
                    vs_renderGizmoInsideAABB(
                        matrices, bufferSource.m_6299_(XRAY_LINES),
                        shipVoxelAABBAfterOffset,
                        centerOfMass.x, centerOfMass.y, centerOfMass.z, 1.0F, .125F
                    );
                }
                // Back to rendering in world coordinates.
                matrices.m_85849_();

                // Draw render AABB (extent of the ship in world coordinates)
                LevelRenderer
                    .m_109646_(matrices, bufferSource.m_6299_(RenderType.f_110371_),
                        renderAABB,
                        234.0F / 255.0F, 0.0F, 217.0f / 255.0f, 1.0F);

                // Render the ship's drag and lift forces as lines
                final Vector3dc dragForce = DragInfoReporter.INSTANCE.getShipDragValues().get(shipObjectClient.getId());
                final Vector3dc liftForce = DragInfoReporter.INSTANCE.getShipLiftValues().get(shipObjectClient.getId());
                if (dragForce != null) {
                    vs_renderForce(matrices, bufferSource.m_6299_(RenderType.f_110371_), shipRenderPosition, dragForce,
                    0.01, 10.0, 0.0F, 0.5F, 1.0F, 1.0F);
                }
                if (liftForce != null) {
                    vs_renderForce(matrices, bufferSource.m_6299_(RenderType.f_110371_), shipRenderPosition, liftForce,
                    0.01, 10.0, 0.0F, 1.0F, 0.5F, 1.0F);
                }
            }
            matrices.m_85849_();
        }
        bufferSource.m_109911_();
    }

    @Unique
    private static void vs_renderForce(PoseStack poseStack, VertexConsumer vertexConsumer, Vector3dc pos, Vector3dc force, double scale, double cap, float r, float g, float b, float alpha) {
        Matrix4f m4 = poseStack.m_85850_().m_252922_();
        Matrix3f m3 = poseStack.m_85850_().m_252943_();

        Vector3d diff = new Vector3d(
            Math.min(Math.max(-cap, force.x() * scale), cap),
            Math.min(Math.max(-cap, force.y() * scale), cap),
            Math.min(Math.max(-cap, force.z() * scale), cap)
        );

        vertexConsumer.m_252986_(m4, (float) pos.x(), (float) pos.y(), (float) pos.z())
            .m_85950_(r, g, b, alpha)
            .m_252939_(m3, (float) diff.x, (float) diff.y, (float) diff.z).m_5752_();
        vertexConsumer.m_252986_(m4, (float) (pos.x() + diff.x), (float) (pos.y() + diff.y), (float) (pos.z() + diff.z))
            .m_85950_(1.0F, 1.0F, 1.0F, 0.0F) // Fade out effect for force lines to stand out against AABBs and gizmos
            .m_252939_(m3, (float) diff.x, (float) diff.y, (float) diff.z).m_5752_();
    }

    @Unique
    private static void vs_renderGizmoInsideAABB(PoseStack poseStack, VertexConsumer vertexConsumer, AABB aABB, double cx, double cy, double cz, float alpha, float gizmoSize) {
        vs_renderGizmoInsideAABB(poseStack, vertexConsumer, aABB.f_82288_, aABB.f_82289_, aABB.f_82290_, aABB.f_82291_, aABB.f_82292_, aABB.f_82293_, cx, cy, cz, alpha, gizmoSize);
    }

    @Unique
    private static void vs_renderGizmoInsideAABB(
        PoseStack poseStack,
        VertexConsumer vertexConsumer,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        double cx, double cy, double cz,
        float alpha,
        float gizmoSize
    ) {
        Matrix4f m4 = poseStack.m_85850_().m_252922_();
        Matrix3f m3 = poseStack.m_85850_().m_252943_();

        float mx = (float) minX, my = (float) minY, mz = (float) minZ;
        float Mx = (float) maxX, My = (float) maxY, Mz = (float) maxZ;
        float fx = (float) cx, fy = (float) cy, fz = (float) cz;

        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();
        Vector3f color = new Vector3f();

        java.util.function.BiConsumer<Vector3f, Vector3f> line = (a, b) -> {
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float dz = b.z - a.z;
            vertexConsumer.m_252986_(m4, a.x, a.y, a.z)
                .m_85950_(color.x, color.y, color.z, alpha)
                .m_252939_(m3, dx, dy, dz).m_5752_();
            vertexConsumer.m_252986_(m4, b.x, b.y, b.z)
                .m_85950_(color.x, color.y, color.z, alpha)
                .m_252939_(m3, dx, dy, dz).m_5752_();
        };

        color.set(1, 0, 0);
        line.accept(p1.set(mx, fy, fz), p2.set(Mx, fy, fz));
        line.accept(p1.set(mx, fy - gizmoSize, fz - gizmoSize), p2.set(mx, fy + gizmoSize, fz + gizmoSize));
        line.accept(p1.set(mx, fy - gizmoSize, fz + gizmoSize), p2.set(mx, fy + gizmoSize, fz - gizmoSize));
        float backX = Mx - gizmoSize;
        line.accept(p1.set(backX, fy - gizmoSize, fz), p2.set(Mx, fy, fz));
        line.accept(p1.set(backX, fy + gizmoSize, fz), p2.set(Mx, fy, fz));
        line.accept(p1.set(backX, fy, fz - gizmoSize), p2.set(Mx, fy, fz));
        line.accept(p1.set(backX, fy, fz + gizmoSize), p2.set(Mx, fy, fz));

        color.set(0, 1, 0);
        line.accept(p1.set(fx, my, fz), p2.set(fx, My, fz));
        line.accept(p1.set(fx - gizmoSize, my, fz - gizmoSize), p2.set(fx + gizmoSize, my, fz + gizmoSize));
        line.accept(p1.set(fx - gizmoSize, my, fz + gizmoSize), p2.set(fx + gizmoSize, my, fz - gizmoSize));
        float backY = My - gizmoSize;
        line.accept(p1.set(fx - gizmoSize, backY, fz), p2.set(fx, My, fz));
        line.accept(p1.set(fx + gizmoSize, backY, fz), p2.set(fx, My, fz));
        line.accept(p1.set(fx, backY, fz - gizmoSize), p2.set(fx, My, fz));
        line.accept(p1.set(fx, backY, fz + gizmoSize), p2.set(fx, My, fz));

        color.set(0, 0, 1);
        line.accept(p1.set(fx, fy, mz), p2.set(fx, fy, Mz));
        line.accept(p1.set(fx - gizmoSize, fy - gizmoSize, mz), p2.set(fx + gizmoSize, fy + gizmoSize, mz));
        line.accept(p1.set(fx - gizmoSize, fy + gizmoSize, mz), p2.set(fx + gizmoSize, fy - gizmoSize, mz));
        float backZ = Mz - gizmoSize;
        line.accept(p1.set(fx - gizmoSize, fy, backZ), p2.set(fx, fy, Mz));
        line.accept(p1.set(fx + gizmoSize, fy, backZ), p2.set(fx, fy, Mz));
        line.accept(p1.set(fx, fy - gizmoSize, backZ), p2.set(fx, fy, Mz));
        line.accept(p1.set(fx, fy + gizmoSize, backZ), p2.set(fx, fy, Mz));
    }

    @Unique
    private static RenderType XRAY_LINES = new RenderStateShard(null, null, null) {
        // RenderStateShard.RENDERTYPE_LINES_SHADER and others are public in Fabric but protected in Forge.
        // Instead of accessor mixins or access transformers we will use a dummy anonymous class.
        // It is only called once and references static methods and fields. This should be safe.
        public static RenderType createXrayLines() {
            return RenderType.m_173215_(
                "xray_lines",
                DefaultVertexFormat.f_166851_,
                VertexFormat.Mode.LINES,
                256, false, false,
                RenderType.CompositeState.m_110628_()
                    .m_173292_(RenderStateShard.f_173095_)
                    .m_110673_(RenderStateShard.f_110130_) // Thinner than RenderType.LINES, though we do not really care.
                    .m_110669_(RenderStateShard.f_110119_)
                    .m_110685_(RenderStateShard.f_110139_)
                    .m_110675_(RenderStateShard.f_110129_)
                    .m_110687_(RenderStateShard.f_110114_)
                    .m_110661_(RenderStateShard.f_110110_)
                    .m_110663_(RenderStateShard.f_110111_)
                    .m_110691_(false)
            );
        }
    }.createXrayLines();
}
