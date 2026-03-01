package org.valkyrienskies.mod.mixin.client.renderer;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.ClientShip;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.client.IVSCamera;
import org.valkyrienskies.mod.common.IShipObjectWorldClientProvider;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDragger;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.world.RaycastUtilsKt;
import org.valkyrienskies.mod.mixinducks.client.MinecraftDuck;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;
    // region Mount the camera to the ship
    @Shadow
    @Final
    private Camera mainCamera;

    @Shadow
    protected abstract double getFov(Camera camera, float f, boolean bl);

    @Shadow
    public abstract Matrix4f getProjectionMatrix(double d);

    /**
     * {@link Entity#m_19907_(double, float, boolean)} except the hit pos is not transformed
     */
    @Unique
    private static HitResult entityRaycastNoTransform(
        final Entity entity, final double maxDistance, final float tickDelta, final boolean includeFluids) {
        final Vec3 vec3d = entity.m_20299_(tickDelta);
        final Vec3 vec3d2 = entity.m_20252_(tickDelta);
        final Vec3 vec3d3 = vec3d.m_82520_(vec3d2.f_82479_ * maxDistance, vec3d2.f_82480_ * maxDistance, vec3d2.f_82481_ * maxDistance);
        return RaycastUtilsKt.clipIncludeShips(
            entity.m_9236_(),
            new ClipContext(
                vec3d,
                vec3d3,
                ClipContext.Block.OUTLINE,
                includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                entity
            ),
            false
        );
    }

    @WrapOperation(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
        )
    )
    public HitResult modifyCrosshairTargetBlocks(final Entity receiver, final double maxDistance, final float tickDelta,
        final boolean includeFluids, final Operation<HitResult> pick) {

        final HitResult original = entityRaycastNoTransform(receiver, maxDistance, tickDelta, includeFluids);
        ((MinecraftDuck) this.minecraft).vs$setOriginalCrosshairTarget(original);

        return pick.call(receiver, maxDistance, tickDelta, includeFluids);
    }

    @WrapOperation(
        method = "pick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
        )
    )
    public double correctDistanceChecks(final Vec3 instance, final Vec3 vec3, final Operation<Double> original) {
        return VSGameUtilsKt.squaredDistanceBetweenInclShips(this.minecraft.f_91073_, instance, vec3, original);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void preRender(final float tickDelta, final long startTime, final boolean tick, final CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.f_91073_;
        if (clientWorld != null) {
            // Update ship render transforms
            final VsiClientShipWorld shipWorld =
                IShipObjectWorldClientProvider.class.cast(this.minecraft).getShipObjectWorld();
            if (shipWorld == null) {
                return;
            }

            shipWorld.updateRenderTransforms(tickDelta);

            // Also update entity last tick positions, so that they interpolate correctly
            for (final Entity entity : clientWorld.m_104735_()) {
                if (!EntityDragger.isDraggable(entity)) {
                    continue;
                }
                // The position we want to render [entity] at for this frame
                // This is set when an entity is mounted to a ship, or an entity is being dragged by a ship
                Vector3dc entityShouldBeHere = null;

                // First, try getting the ship the entity is mounted to, if one exists
                final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(entity, tickDelta);

                if (shipMountedToData != null) {
                    final ClientShip shipMountedTo = (ClientShip) shipMountedToData.getShipMountedTo();
                    // If the entity is mounted to a ship then update their position
                    final Vector3dc passengerPos = shipMountedToData.getMountPosInShip();
                    entityShouldBeHere = shipMountedTo.getRenderTransform().getShipToWorld()
                        .transformPosition(passengerPos, new Vector3d());
                    entity.m_6034_(entityShouldBeHere.x(), entityShouldBeHere.y(), entityShouldBeHere.z());
                    entity.f_19854_ = entityShouldBeHere.x();
                    entity.f_19855_ = entityShouldBeHere.y();
                    entity.f_19856_ = entityShouldBeHere.z();
                    entity.f_19790_ = entityShouldBeHere.x();
                    entity.f_19791_ = entityShouldBeHere.y();
                    entity.f_19792_ = entityShouldBeHere.z();
                    continue;
                }

                final EntityDraggingInformation entityDraggingInformation =
                    ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                final Long lastShipStoodOn = entityDraggingInformation.getLastShipStoodOn();
                // Then try getting [entityShouldBeHere] from [entityDraggingInformation]
                if (lastShipStoodOn != null && entityDraggingInformation.isEntityBeingDraggedByAShip()) {
                    final ClientShip shipObject =
                        VSGameUtilsKt.getShipObjectWorld(clientWorld).getLoadedShips().getById(lastShipStoodOn);
                    if (shipObject != null) {
                        entityDraggingInformation.setCachedLastPosition(
                            new Vector3d(entity.f_19854_, entity.f_19855_, entity.f_19856_));
                        entityDraggingInformation.setRestoreCachedLastPosition(true);

                        // The velocity added to the entity by ship dragging
                        final Vector3dc entityAddedVelocity = entityDraggingInformation.getAddedMovementLastTick();

                        // The velocity of the entity before we added ship dragging
                        final double entityMovementX = entity.m_20185_() - entityAddedVelocity.x() - entity.f_19854_;
                        final double entityMovementY = entity.m_20186_() - entityAddedVelocity.y() - entity.f_19855_;
                        final double entityMovementZ = entity.m_20189_() - entityAddedVelocity.z() - entity.f_19856_;

                        // Without ship dragging, the entity would've been here
                        final Vector3dc entityShouldBeHerePreTransform = new Vector3d(
                            entity.f_19854_ + entityMovementX * tickDelta,
                            entity.f_19855_ + entityMovementY * tickDelta,
                            entity.f_19856_ + entityMovementZ * tickDelta
                        );

                        // Move [entityShouldBeHerePreTransform] with the ship, using the prev transform and the
                        // current render transform
                        entityShouldBeHere = shipObject.getRenderTransform().getShipToWorldMatrix()
                            .transformPosition(
                                shipObject.getPrevTickShipTransform().getWorldToShipMatrix()
                                    .transformPosition(entityShouldBeHerePreTransform, new Vector3d()));
                    }
                }

                // Apply entityShouldBeHere, if its present
                //
                // Also, don't run this if [tickDelta] is too small, getting so close to dividing by 0 could mess
                // something up
                if (entityShouldBeHere != null && tickDelta < .99999) {
                    // Update the entity last tick positions such that the entity's render position will be
                    // interpolated to be [entityShouldBeHere]
                    entity.f_19854_ = (entityShouldBeHere.x() - (entity.m_20185_() * tickDelta)) / (1.0 - tickDelta);
                    entity.f_19855_ = (entityShouldBeHere.y() - (entity.m_20186_() * tickDelta)) / (1.0 - tickDelta);
                    entity.f_19856_ = (entityShouldBeHere.z() - (entity.m_20189_() * tickDelta)) / (1.0 - tickDelta);
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void postRender(final float tickDelta, final long startTime, final boolean tick, final CallbackInfo ci) {
        final ClientLevel clientWorld = minecraft.f_91073_;
        if (clientWorld != null) {
            // Restore the entity last tick positions that were replaced during this frame
            for (final Entity entity : clientWorld.m_104735_()) {
                final EntityDraggingInformation vsEntity =
                    ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                if (vsEntity.getRestoreCachedLastPosition()) {
                    vsEntity.setRestoreCachedLastPosition(false);
                    final Vector3dc cachedLastPosition = vsEntity.getCachedLastPosition();
                    if (cachedLastPosition != null) {
                        entity.f_19854_ = cachedLastPosition.x();
                        entity.f_19855_ = cachedLastPosition.y();
                        entity.f_19856_ = cachedLastPosition.z();
                    } else {
                        System.err.println("How was cachedLastPosition was null?");
                    }
                }
            }
        }
    }

    /**
     * Mount the player's camera to the ship they are mounted on.
     */
    @WrapOperation(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;prepareCullFrustum(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;Lorg/joml/Matrix4f;)V"
        )
    )
    private void setupCameraWithMountedShip(final LevelRenderer instance, final PoseStack ignore, final Vec3 vec3,
        final Matrix4f matrix4f, final Operation<Void> prepareCullFrustum, final float partialTicks,
        final long finishTimeNano, final PoseStack matrixStack) {

        final ClientLevel clientLevel = minecraft.f_91073_;
        final Entity player = minecraft.f_91074_;
        if (clientLevel == null || player == null) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }

        final ShipMountedToData shipMountedToData = VSGameUtilsKt.getShipMountedToData(player, partialTicks);
        if (shipMountedToData == null) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }

        final Entity playerVehicle = player.m_20202_();
        if (playerVehicle == null) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }

        // Update [matrixStack] to mount the camera to the ship

        final Camera camera = this.mainCamera;
        if (camera == null) {
            prepareCullFrustum.call(instance, matrixStack, vec3, matrix4f);
            return;
        }

        final ClientShip clientShip = (ClientShip) shipMountedToData.getShipMountedTo();

        ((IVSCamera) camera).setupWithShipMounted(
            this.minecraft.f_91073_,
            this.minecraft.m_91288_() == null ? this.minecraft.f_91074_ : this.minecraft.m_91288_(),
            !this.minecraft.f_91066_.m_92176_().m_90612_(),
            this.minecraft.f_91066_.m_92176_().m_90613_(),
            partialTicks,
            clientShip,
            shipMountedToData.getMountPosInShip()
        );

        // Apply the ship render transform to [matrixStack]
        final Quaternionf invShipRenderRotation = new Quaternionf(
            clientShip.getRenderTransform().getShipToWorldRotation().conjugate(new Quaterniond())
        );
        matrixStack.m_252781_(invShipRenderRotation);

        // We also need to recompute [inverseViewRotationMatrix] after updating [matrixStack]
        {
            final Matrix3f matrix3f = new Matrix3f(matrixStack.m_85850_().m_252943_());
            matrix3f.invert();
            RenderSystem.setInverseViewRotationMatrix(matrix3f);
        }

        // Camera FOV changes based on the position of the camera, so recompute FOV to account for the change of camera
        // position.
        final double fov = this.getFov(camera, partialTicks, true);

        // Use [camera.getPosition()] instead of [vec3] because mounting the player to the ship has changed the camera
        // position.
        prepareCullFrustum.call(instance, matrixStack, camera.m_90583_(),
            this.getProjectionMatrix(Math.max(fov, this.minecraft.f_91066_.m_231837_().m_231551_())));
    }
    // endregion

    @ModifyReturnValue(method = "getDepthFar", at = @At("RETURN"))
    public float includeShipsIn(final float originalDepth) {
        float maxDistance = originalDepth;
        for (final ClientShip ship : VSGameUtilsKt.getShipObjectWorld(Minecraft.m_91087_()).getLoadedShips()) {
            Vec3 cameraPos = this.mainCamera.m_90583_();
            AABBdc shipAABB = ship.getRenderAABB();
            // find the furthest distance from the camera to the ship AABB corners
            double furthestDistanceSq = 0;
            double dMinX = shipAABB.minX() - cameraPos.m_7096_();  
            double dMaxX = shipAABB.maxX() - cameraPos.m_7096_();  
            double dMinY = shipAABB.minY() - cameraPos.m_7098_();  
            double dMaxY = shipAABB.maxY() - cameraPos.m_7098_();  
            double dMinZ = shipAABB.minZ() - cameraPos.m_7094_();  
            double dMaxZ = shipAABB.maxZ() - cameraPos.m_7094_();  
            double furthestDist = Math.sqrt(Math.max(dMinX * dMinX, dMaxX * dMaxX) + Math.max(dMinY * dMinY, dMaxY * dMaxY) + Math.max(dMinZ * dMinZ, dMaxZ * dMaxZ));  
            maxDistance = Math.max(maxDistance, (float) furthestDist);  
        }

        return maxDistance;
    }
}
