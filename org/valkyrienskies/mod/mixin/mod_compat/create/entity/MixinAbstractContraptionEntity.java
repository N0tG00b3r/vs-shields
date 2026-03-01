package org.valkyrienskies.mod.mixin.mod_compat.create.entity;

import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toJOML;
import static org.valkyrienskies.mod.common.util.VectorConversionsMCKt.toMinecraft;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ContraptionWingProvider;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.WingManager;
import org.valkyrienskies.mod.common.CompatUtil;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountedToData;
import org.valkyrienskies.mod.common.entity.ShipMountedToDataProvider;
import org.valkyrienskies.mod.common.util.IEntityDraggingInformationProvider;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.CreateConversionsKt;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.MixinAbstractContraptionEntityDuck;

@Mixin(AbstractContraptionEntity.class)
public abstract class MixinAbstractContraptionEntity extends Entity implements MixinAbstractContraptionEntityDuck,
    ContraptionWingProvider, IEntityDraggingInformationProvider, ShipMountedToDataProvider {

    public MixinAbstractContraptionEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    protected abstract StructureTransform makeStructureTransform();

    public StructureTransform getStructureTransform() {
        return this.makeStructureTransform();
    }

    @Unique
    private static final Logger LOGGER = LogManager.getLogger("Clockwork.MixinAbstractContraptionEntity");

    @Unique
    private int wingGroupId = -1;

    @Shadow(remap = false)
    protected Contraption contraption;

    @Shadow
    public abstract Vec3 getPassengerPosition(Entity passenger, float partialTicks);

    @Shadow
    public abstract Vec3 applyRotation(Vec3 localPos, float partialTicks);

    @Shadow
    public abstract Vec3 getAnchorVec();

    @Shadow
    public abstract Vec3 getPrevAnchorVec();

    @Nullable
    @Override
    public ShipMountedToData provideShipMountedToData(@NotNull final Entity passenger, @Nullable final Float partialTicks) {
        final LoadedShip shipObjectEntityMountedTo = VSGameUtilsKt.getLoadedShipManagingPos(passenger.m_9236_(), toJOML(this.m_20182_()));
        if (shipObjectEntityMountedTo == null) return null;

        Vec3 transformedPos = this.getPassengerPosition(passenger, partialTicks == null ? 1 : partialTicks);
        if (transformedPos == null) transformedPos = this.m_20318_(partialTicks == null ? 0.0f : partialTicks);

        return new ShipMountedToData(shipObjectEntityMountedTo, toJOML(transformedPos));
    }

    //Region start - fix being sent to the  ̶s̶h̶a̶d̶o̶w̶r̶e̶a̶l̶m̶ shipyard on ship contraption disassembly
    @Redirect(method = "moveCollidedEntitiesOnDisassembly", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setPos(DDD)V"))
    private void redirectSetPos(Entity instance, double x, double y, double z) {
        Vec3 result = CompatUtil.INSTANCE.toSameSpaceAs(instance.m_20193_(), x, y, z, instance.m_20182_());
        if (instance.m_20182_().m_82554_(result) < 20) {
            instance.m_6034_(result.f_82479_, result.f_82480_, result.f_82481_);
        } else LOGGER.warn("Warning distance too high ignoring setPos request");
    }

    @Redirect(method = "moveCollidedEntitiesOnDisassembly", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;teleportTo(DDD)V"))
    private void redirectTeleportTo(Entity instance, double x, double y, double z) {
        Vec3 result = CompatUtil.INSTANCE.toSameSpaceAs(instance.m_20193_(), x, y, z, instance.m_20182_());
        if (instance.m_20182_().m_82554_(result) < 20) {
            if (VSGameUtilsKt.isBlockInShipyard(instance.m_20193_(), result.f_82479_, result.f_82480_, result.f_82481_) && instance instanceof AbstractMinecart) {
                result.m_82520_(0, 0.5, 0);
            }
            instance.m_6021_(result.f_82479_, result.f_82480_, result.f_82481_);
        } else {
            LOGGER.warn("Warning distance too high ignoring teleportTo request");
        }
    }

    @Inject(method = "toGlobalVector(Lnet/minecraft/world/phys/Vec3;FZ)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true)
    private void redirectToGlobalVector(Vec3 localVec, final float partialTicks, final boolean prevAnchor, final CallbackInfoReturnable<Vec3> cir) {
        if (partialTicks != 1 && !prevAnchor) {
            final Vec3 anchor = getAnchorVec();
            final Vec3 oldAnchor = getPrevAnchorVec();
            final Vec3 lerpedAnchor =
                    new Vec3(
                            Mth.m_14139_(partialTicks, oldAnchor.f_82479_, anchor.f_82479_),
                            Mth.m_14139_(partialTicks, oldAnchor.f_82480_, anchor.f_82480_),
                            Mth.m_14139_(partialTicks, oldAnchor.f_82481_, anchor.f_82481_)
                    );
            final Vec3 rotationOffset = VecHelper.getCenterOf(BlockPos.f_121853_);
            localVec = localVec.m_82546_(rotationOffset);
            localVec = applyRotation(localVec, partialTicks);
            localVec = localVec.m_82549_(rotationOffset)
                    .m_82549_(lerpedAnchor);
            cir.setReturnValue(localVec);
        }
    }

    //Region end
    //Region start - Ship contraption actors affecting world
    @Shadow
    public abstract Vec3 toGlobalVector(Vec3 localVec, float partialTicks);

    @Shadow
    public abstract Vec3 getPrevPositionVec();

    @Unique
    private boolean vs$shouldMod(final MovementBehaviour moveBehaviour) {
        return ((moveBehaviour instanceof BlockBreakingMovementBehaviour) || (moveBehaviour instanceof HarvesterMovementBehaviour));
    }

    @Unique
    private BlockPos vs$getTargetPos(final MovementBehaviour instance, final MovementContext context, final BlockPos pos, final Vec3 actorPosition) {
        if (vs$shouldMod(instance) && context.world.m_8055_(pos).m_60795_() && VSGameUtilsKt.isBlockInShipyard(context.world, pos)) {
            final Ship ship = VSGameUtilsKt.getShipManagingPos(context.world, pos);
            if (ship != null) {
                final Vector3dc actorPosInWorld = ship.getTransform().getShipToWorld().transformPosition(toJOML(actorPosition));
                return BlockPos.m_274561_(actorPosInWorld.x(), actorPosInWorld.y(), actorPosInWorld.z());
            }
        }
        return pos;
    }

    @Unique
    private boolean vs$forceStall = false;

    @Shadow
    private boolean skipActorStop;

    @Shadow
    @Final
    private static EntityDataAccessor<Boolean> STALLED;

    @Shadow
    public abstract boolean isStalled();

    @Shadow
    protected abstract boolean shouldActorTrigger(MovementContext context, StructureBlockInfo blockInfo, MovementBehaviour actor, Vec3 actorPosition, BlockPos gridPosition);

    @Shadow
    protected abstract boolean isActorActive(MovementContext context, MovementBehaviour actor);

    @Shadow
    protected abstract void onContraptionStalled();

    @Inject(method = "tickActors", at = @At("HEAD"), cancellable = true, remap = false)
    private void preTickActors(final CallbackInfo ci) {
        ci.cancel();

        final boolean stalledPreviously = contraption.stalled;

        if (!m_9236_().f_46443_)
            contraption.stalled = vs$forceStall;

        skipActorStop = true;
        for (final MutablePair<StructureBlockInfo, MovementContext> pair : contraption.getActors()) {
            final MovementContext context = pair.right;
            final StructureBlockInfo blockInfo = pair.left;
            final MovementBehaviour actor = MovementBehaviour.REGISTRY.get(blockInfo.f_74676_());

            if (actor == null)
                continue;

            final Vec3 oldMotion = context.motion;
            final Vec3 actorPosition = toGlobalVector(VecHelper.getCenterOf(blockInfo.f_74675_())
                .m_82549_(actor.getActiveAreaOffset(context)), 1);
            final BlockPos gridPosition = vs$getTargetPos(actor, context, BlockPos.m_274446_(actorPosition), actorPosition); // BlockPos.containing(actorPosition);
            final boolean newPosVisited =
                !context.stall && shouldActorTrigger(context, blockInfo, actor, actorPosition, gridPosition);

            context.rotation = v -> applyRotation(v, 1);
            context.position = actorPosition;
            if (!isActorActive(context, actor) && !actor.mustTickWhileDisabled())
                continue;
            if (newPosVisited && !context.stall) {
                actor.visitNewPosition(context, gridPosition);
                if (!m_6084_())
                    break;
                context.firstMovement = false;
            }
            if (!oldMotion.equals(context.motion)) {
                actor.onSpeedChanged(context, oldMotion, context.motion);
                if (!m_6084_())
                    break;
            }
            actor.tick(context);
            if (!m_6084_())
                break;
            contraption.stalled |= context.stall;
        }
        if (!m_6084_()) {
            contraption.stop(m_9236_());
            return;
        }
        skipActorStop = false;

        for (final Entity entity : m_20197_()) {
            if (!(entity instanceof final OrientedContraptionEntity orientedCE))
                continue;
            if (contraption.getBearingPosOf(entity.m_20148_()) == null)
                continue;
            if (orientedCE.getContraption() != null && orientedCE.getContraption().stalled) {
                contraption.stalled = true;
                break;
            }
        }

        if (!m_9236_().f_46443_) {
            if (!stalledPreviously && contraption.stalled)
                onContraptionStalled();
            f_19804_.m_135381_(STALLED, contraption.stalled);
            return;
        }

        contraption.stalled = isStalled();
    }

    @Override
    public void vs$setForceStall(final boolean forceStall) {
        this.vs$forceStall = forceStall;
    }

    //Region end
    //Region start - Contraption Entity Collision
    @Inject(method = "getContactPointMotion", at = @At("HEAD"))
    private void modGetContactPointMotion(Vec3 globalContactPoint, CallbackInfoReturnable<Vec3> cir) {
        if (VSGameUtilsKt.isBlockInShipyard(m_9236_(), getAnchorVec().f_82479_, getAnchorVec().f_82480_, getAnchorVec().f_82481_) != VSGameUtilsKt.isBlockInShipyard(m_9236_(), getPrevAnchorVec().f_82479_, getPrevAnchorVec().f_82480_, getPrevAnchorVec().f_82481_)) {
            Ship ship = VSGameUtilsKt.getShipManagingPos(m_9236_(), getAnchorVec());
            if (ship != null) {
                Vec3 result = toMinecraft(ship.getWorldToShip().transformPosition(toJOML(getPrevPositionVec())));
                f_19854_ = result.f_82479_;
                f_19855_ = result.f_82480_;
                f_19856_ = result.f_82481_;
            }
        }
    }
    //Region end

    @Override
    public int getWingGroupId() {
        return wingGroupId;
    }

    @Override
    public void setWingGroupId(final int wingGroupId) {
        this.wingGroupId = wingGroupId;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void postTick(final CallbackInfo ci) {
        final AbstractContraptionEntity thisAsAbstractContraptionEntity = AbstractContraptionEntity.class.cast(this);
        final Level level = thisAsAbstractContraptionEntity.m_9236_();
        if (wingGroupId != -1 && level instanceof final ServerLevel serverLevel) {
            final LoadedServerShip ship = VSGameUtilsKt.getLoadedShipManagingPos(serverLevel,
                VectorConversionsMCKt.toJOML(thisAsAbstractContraptionEntity.m_20182_()));
            if (ship != null) {
                try {
                    // This can happen if a player moves a train contraption from ship to world using a wrench
                    ship.getWingManager()
                        .setWingGroupTransform(wingGroupId, computeContraptionWingTransform());
                } catch (final Exception e) {
                    // I'm not sure why, but this fails sometimes. For now just catch the error and print it
                    e.printStackTrace();
                }
            }
        }
    }

    @NotNull
    @Override
    public Matrix4dc computeContraptionWingTransform() {
        final AbstractContraptionEntity thisAsAbstractContraptionEntity = AbstractContraptionEntity.class.cast(this);
        final Matrix3d rotationMatrix = CreateConversionsKt.toJOML(thisAsAbstractContraptionEntity.getRotationState().asMatrix());
        final Vector3d pos = VectorConversionsMCKt.toJOML(thisAsAbstractContraptionEntity.getAnchorVec());
        return new Matrix4d(rotationMatrix).setTranslation(pos);
    }

    @Override
    public boolean vs$shouldDrag() {
        return false;
    }
}
