package org.valkyrienskies.mod.mixin.feature.entity_collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.EntityDraggingInformation;
import org.valkyrienskies.mod.common.util.EntityShipCollisionUtils;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntityDraggingInformationProvider {

    // region collision

    @Shadow
    public boolean hasImpulse;
    @Shadow
    protected boolean firstTick;
    @Shadow
    public int tickCount;

    @Shadow
    public abstract void setPos(Vec3 arg);

    @Shadow
    public abstract boolean is(Entity arg);

    @Shadow
    public abstract boolean isControlledByLocalInstance();

    @Shadow
    public abstract EntityType<?> getType();

    @Shadow
    public abstract Iterable<Entity> getIndirectPassengers();

    @Shadow
    public abstract BlockPos getOnPos();

    /**
     * Cancel movement of entities that are colliding with unloaded ships
     */
    @Inject(
        at = @At("HEAD"),
        method = "move",
        cancellable = true
    )
    private void beforeMove(final MoverType type, final Vec3 pos, final CallbackInfo ci) {
        if (EntityShipCollisionUtils.isCollidingWithUnloadedShips(Entity.class.cast(this))) {
            ci.cancel();
        }
    }

    /**
     * Allows entities to collide with ships by modifying the movement vector.
     */
    @WrapOperation(
        method = "move",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        )
    )
    public Vec3 collideWithShips(final Entity entity, Vec3 movement, final Operation<Vec3> collide) {
        final AABB box = this.getBoundingBox();
        movement = EntityShipCollisionUtils.INSTANCE
            .adjustEntityMovementForShipCollisions(entity, movement, box, this.level);
        final Vec3 collisionResultWithWorld = collide.call(entity, movement);

        if (collisionResultWithWorld.m_82557_(movement) > 1e-12) {
            // We collided with the world? Set the dragging ship to null.
            final EntityDraggingInformation entityDraggingInformation = getDraggingInformation();
            if (entityDraggingInformation.getIgnoreNextGroundStand()) {
                entityDraggingInformation.setIgnoreNextGroundStand(false);
                return collisionResultWithWorld;
            }
            entityDraggingInformation.setLastShipStoodOn(null);
            entityDraggingInformation.setAddedYawRotLastTick(0.0);

            for (Entity entityRiding : entity.m_146897_()) {
                final EntityDraggingInformation passengerDraggingInformation =
                    ((IEntityDraggingInformationProvider) entityRiding).getDraggingInformation();
                passengerDraggingInformation.setLastShipStoodOn(null);
                passengerDraggingInformation.setAddedYawRotLastTick(0.0);
            }
        }
        return collisionResultWithWorld;
    }

    /**
     * This mixin replaces the following code in Entity.move().
     *
     * <p>if (movement.x != vec3d.x) { this.setVelocity(0.0D, vec3d2.y, vec3d2.z); } </p>
     *
     * <p>if (movement.z != vec3d.z) { this.setVelocity(vec3d2.x, vec3d2.y, 0.0D); } </p>
     *
     * <p>This code makes accurate collision with non axis-aligned surfaces impossible, so this mixin replaces it. </p>
     */
    @Inject(method = "move", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(DDD)V"),
        locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true
    )
    private void redirectSetVelocity(final MoverType moverType, final Vec3 movement, final CallbackInfo callbackInfo,
        final Vec3 movementAdjustedForCollisions) {

        // Compute the collision response horizontal
        final Vector3dc collisionResponseHorizontal =
            new Vector3d(movementAdjustedForCollisions.f_82479_ - movement.f_82479_, 0.0,
                movementAdjustedForCollisions.f_82481_ - movement.f_82481_);

        // Remove the component of [movementAdjustedForCollisions] that is parallel to [collisionResponseHorizontal]
        if (collisionResponseHorizontal.lengthSquared() > 1e-6) {
            final Vec3 deltaMovement = getDeltaMovement();

            final Vector3dc collisionResponseHorizontalNormal = collisionResponseHorizontal.normalize(new Vector3d());
            final double parallelHorizontalVelocityComponent =
                collisionResponseHorizontalNormal
                    .dot(deltaMovement.f_82479_, 0.0, deltaMovement.f_82481_);

            setDeltaMovement(
                deltaMovement.f_82479_
                    - collisionResponseHorizontalNormal.x() * parallelHorizontalVelocityComponent,
                deltaMovement.f_82480_,
                deltaMovement.f_82481_
                    - collisionResponseHorizontalNormal.z() * parallelHorizontalVelocityComponent
            );
        }
        // The rest of the move function (including tryCheckInsideBlocks) is skipped, so calling it here
        tryCheckInsideBlocks();
        // Cancel the original invocation of Entity.setVelocity(DDD)V to remove vanilla behavior
        callbackInfo.cancel();
    }

    // endregion

    // region Block standing on friction and sprinting particles mixins
    @Unique
    private BlockPos getPosStandingOnFromShips(final Vector3dc blockPosInGlobal) {
        final double radius = 0.5;
        final AABBdc testAABB = new AABBd(
            blockPosInGlobal.x() - radius, blockPosInGlobal.y() - radius, blockPosInGlobal.z() - radius,
            blockPosInGlobal.x() + radius, blockPosInGlobal.y() + radius, blockPosInGlobal.z() + radius
        );
        final Iterable<Ship> intersectingShips = VSGameUtilsKt.getShipsIntersecting(level, testAABB);
        for (final Ship ship : intersectingShips) {
            final Vector3dc blockPosInLocal =
                ship.getTransform().getWorldToShip().transformPosition(blockPosInGlobal, new Vector3d());
            final BlockPos blockPos = BlockPos.m_274561_(
                blockPosInLocal.x(), blockPosInLocal.y(), blockPosInLocal.z()
            );
            final BlockState blockState = level.m_8055_(blockPos);
            if (!blockState.m_60795_()) {
                return blockPos;
            } else {
                // Check the block below as well, in the cases of fences
                final Vector3dc blockPosInLocal2 = ship.getTransform().getWorldToShip()
                    .transformPosition(
                        new Vector3d(blockPosInGlobal.x(), blockPosInGlobal.y() - 1.0, blockPosInGlobal.z()));
                final BlockPos blockPos2 = BlockPos.m_274561_(blockPosInLocal2.x(), blockPosInLocal2.y(), blockPosInLocal2.z());
                final BlockState blockState2 = level.m_8055_(blockPos2);
                if (!blockState2.m_60795_()) {
                    return blockPos2;
                }
            }
        }
        return null;
    }

    @Inject(method = "getBlockPosBelowThatAffectsMyMovement", at = @At("HEAD"), cancellable = true)
    private void preGetBlockPosBelowThatAffectsMyMovement(final CallbackInfoReturnable<BlockPos> cir) {
        final Vector3dc blockPosInGlobal = new Vector3d(
            position.f_82479_,
            getBoundingBox().f_82289_ - 0.5,
            position.f_82481_
        );
        final BlockPos blockPosStandingOnFromShip = getPosStandingOnFromShips(blockPosInGlobal);
        if (blockPosStandingOnFromShip != null) {
            cir.setReturnValue(blockPosStandingOnFromShip);
        }
    }


    /**
     * @author tri0de
     * @reason Allows ship blocks to spawn landing particles, running particles, and play step sounds
     */
    @Inject(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void preGetOnPos(final CallbackInfoReturnable<BlockPos> cir) {
        final Vector3dc blockPosInGlobal = new Vector3d(
            position.f_82479_,
            position.f_82480_ - 0.2,
            position.f_82481_
        );
        final BlockPos blockPosStandingOnFromShip = getPosStandingOnFromShips(blockPosInGlobal);
        if (blockPosStandingOnFromShip != null) {
            cir.setReturnValue(blockPosStandingOnFromShip);
        }
    }

    @WrapOperation(method = "spawnSprintParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos skipBlockPosition(final Entity entity, final Operation<BlockPos> original, @Local final BlockPos posOn) {
        if (VSGameUtilsKt.isBlockInShipyard(level, posOn)) return posOn;
        return original.call(entity);
    }

    /**
     * This will set the entity impulsed if it is dragged by a ship on its first tick.
     * Marking impulse forces the sync over server-client, so this will also sync dragging information.
     */
    @Inject(
        method = "tick",
        at = @At("HEAD")
    )
    private void markImpulsedFirstTick(CallbackInfo ci) {
        if (firstTick && getDraggingInformation().isEntityBeingDraggedByAShip() && !level.f_46443_) {
            hasImpulse = true;
        }
    }

    @Inject(
        method = "baseTick",
        at = @At("TAIL")
    )
    private void postBaseTick(final CallbackInfo ci) {
        final EntityDraggingInformation entityDraggingInformation = getDraggingInformation();

        if (level != null && level.f_46443_ && tickCount > 1) { //baseTick sets the firstTick false, use tickCount instead.
            final Ship ship = VSGameUtilsKt.getLoadedShipManagingPos(level, getOnPos());
            if (ship != null) {
//                if (entityDraggingInformation.getLastShipStoodOnServerWriteOnly() == null) {
//                    return;
//                }
                entityDraggingInformation.setLastShipStoodOn(ship.getId());
                getIndirectPassengers().forEach(entity -> {
                    final EntityDraggingInformation passengerDraggingInformation =
                        ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                    passengerDraggingInformation.setLastShipStoodOn(ship.getId());
                });
            } else {
                if (!level.m_8055_(getOnPos()).m_60795_()) {
                    if (entityDraggingInformation.getIgnoreNextGroundStand()) {
                        entityDraggingInformation.setIgnoreNextGroundStand(false);
                    } else {
//                        if (entityDraggingInformation.getLastShipStoodOnServerWriteOnly() != null) {
//                            return;
//                        }
                        entityDraggingInformation.setLastShipStoodOn(null);
                        getIndirectPassengers().forEach(entity -> {
                            final EntityDraggingInformation passengerDraggingInformation =
                                ((IEntityDraggingInformationProvider) entity).getDraggingInformation();
                            passengerDraggingInformation.setLastShipStoodOn(null);
                        });
                    }

                }
            }
        }
    }

    // endregion

    // region shadow functions and fields
    @Shadow
    public Level level;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract void setDeltaMovement(double x, double y, double z);

    @Shadow
    protected abstract void tryCheckInsideBlocks();

    @Shadow
    protected abstract Vec3 collide(Vec3 vec3d);

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Shadow
    public abstract double getZ();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getX();

    @Shadow
    private Vec3 position;

    @Shadow
    @Final
    protected RandomSource random;

    @Shadow
    private EntityDimensions dimensions;
    // endregion
}
