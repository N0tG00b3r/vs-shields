package org.valkyrienskies.mod.mixin.feature.shipyard_entities;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(value = EntityRenderDispatcher.class, priority = 500)
public class MixinEntityRenderDispatcher {

    @Shadow
    public Camera camera;

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/entity/Entity;)D", at = @At("HEAD"), cancellable = true)
    private void preDistanceToSqr(final Entity entity, final CallbackInfoReturnable<Double> cir) {
        final Vec3 pos = entity.m_20182_();
        cir.setReturnValue(VSGameUtilsKt.squaredDistanceToInclShips(entity, pos.f_82479_, pos.f_82480_, pos.f_82481_));
    }

    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void preDistanceToSqr(final double x, final double y, final double z,
        final CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(VSGameUtilsKt.squaredDistanceToInclShips(camera.m_90592_(), x, y, z));
    }

    @Inject(method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            shift = At.Shift.BEFORE),
        locals = LocalCapture.CAPTURE_FAILHARD)
    <T extends Entity> void render(
        final T entity, final double x, final double y, final double z, final float rotationYaw,
        final float partialTicks, final PoseStack matrixStack,
        final MultiBufferSource buffer, final int packedLight, final CallbackInfo ci,
        final EntityRenderer<T> entityRenderer
    ) {
        final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(entity, partialTicks);

        if (shipMountedToData != null) {
            // Remove the earlier applied translation
            matrixStack.m_85849_();
            matrixStack.m_85836_();

            final ShipTransform renderTransform = ((ClientShip) shipMountedToData.getShipMountedTo()).getRenderTransform();

            final Vec3 entityPosition = entity.m_20318_(partialTicks);
            final Vector3dc transformed = renderTransform.getShipToWorld().transformPosition(shipMountedToData.getMountPosInShip(), new Vector3d());

            final double camX = x - entityPosition.f_82479_;
            final double camY = y - entityPosition.f_82480_;
            final double camZ = z - entityPosition.f_82481_;

            final Vec3 offset = entityRenderer.m_7860_(entity, partialTicks);
            final Vector3dc scale = renderTransform.getShipToWorldScaling();

            matrixStack.m_85837_(transformed.x() + camX, transformed.y() + camY, transformed.z() + camZ);
            matrixStack.m_252781_(new Quaternionf(renderTransform.getShipToWorldRotation()));
            matrixStack.m_85841_((float) scale.x(), (float) scale.y(), (float) scale.z());
            matrixStack.m_85837_(offset.f_82479_, offset.f_82480_, offset.f_82481_);
        } else {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.m_9236_(), entity.m_20183_());
            if (ship != null) {
                // Remove the earlier applied translation
                matrixStack.m_85849_();
                matrixStack.m_85836_();

                VSEntityManager.INSTANCE.getHandler(entity)
                    .applyRenderTransform(ship, entity, entityRenderer, x, y, z,
                        rotationYaw, partialTicks, matrixStack,
                        buffer, packedLight);
            } else if (entity.m_20159_()) {
                final ClientShip vehicleShip =
                    (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.m_9236_(),
                        entity.m_20202_().m_20183_());
                // If the entity is a passenger and that vehicle is in ship space
                if (vehicleShip != null) {
                    VSEntityManager.INSTANCE.getHandler(entity.m_20202_())
                        .applyRenderOnMountedEntity(vehicleShip, entity.m_20202_(), entity, partialTicks,
                            matrixStack);
                }
            }
        }
    }

    @ModifyReturnValue(
        method = "shouldRender",
        at = @At("RETURN")
    )
    boolean shouldRender(final boolean returns, final Entity entity, final Frustum frustum,
        final double camX, final double camY, final double camZ) {

        if (!returns) {
            final ClientShip ship =
                (ClientShip) VSGameUtilsKt.getLoadedShipManagingPos(entity.m_9236_(), entity.m_20183_());
            if (ship != null) {
                AABB aABB = entity.m_6921_().m_82400_(0.5);
                if (aABB.m_82392_() || aABB.m_82309_() == 0.0) {
                    aABB = new AABB(entity.m_20185_() - 2.0, entity.m_20186_() - 2.0,
                        entity.m_20189_() - 2.0, entity.m_20185_() + 2.0,
                        entity.m_20186_() + 2.0, entity.m_20189_() + 2.0);
                }
                final AABBd aabb = VectorConversionsMCKt.toJOML(aABB);

                // Get the in world position and do it minus what the aabb already has and then add the offset
                aabb.transform(ship.getRenderTransform().getShipToWorld());
                return frustum.m_113029_(VectorConversionsMCKt.toMinecraft(aabb));
            }
        }

        return returns;
    }

}
