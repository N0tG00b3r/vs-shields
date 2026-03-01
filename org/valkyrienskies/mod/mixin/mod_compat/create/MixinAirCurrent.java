package org.valkyrienskies.mod.mixin.mod_compat.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.apache.commons.lang3.tuple.Pair;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.primitives.AABBd;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.handling.DefaultShipyardEntityHandler;
import org.valkyrienskies.mod.common.entity.handling.VSEntityManager;
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;
import org.valkyrienskies.mod.compat.create.AdvancedAirCurrentSegment;
import org.valkyrienskies.mod.compat.create.AirFlowClipContext;
import org.valkyrienskies.mod.mixinducks.mod_compat.create.IExtendedAirCurrentSource;
import org.valkyrienskies.mod.util.AdvancedBlockWalker;

@Mixin(value = AirCurrent.class)
public abstract class MixinAirCurrent {
    @Unique
    private static final boolean[] FALSE_THEN_TRUE = new boolean[]{false, true};
    @Unique
    private static final double NON_BLOCK_EXTEND = 1 / 32d;
    @Unique
    private static final double EPS1 = 1e-6;
    @Unique
    private static final double EPS2 = 2e-6;
    @Unique
    private static final double EPS3 = 4e-6;

    @Shadow
    @Final
    public IAirCurrentSource source;
    @Shadow
    public Direction direction;
    @Shadow
    public boolean pushing;
    @Shadow
    public float maxDistance;
    @Shadow
    protected List<Pair<TransportedItemStackHandlerBehaviour, FanProcessingType>> affectedItemHandlers;

    @Unique
    private double shipScale = 1.0;
    @Unique
    private FanProcessingType initialProcessingType = null;
    @Unique
    private List<AdvancedAirCurrentSegment> segments = new ArrayList<>();

    @Shadow
    private static boolean shouldAlwaysPass(BlockState state) {
        return false;
    }

    @Shadow
    protected abstract int getLimit();

    @Unique
    private Ship getShip() {
        if (source instanceof IExtendedAirCurrentSource se) {
            return se.getShip();
        }
        if (source.getAirCurrentWorld() != null) {
            return VSGameUtilsKt.getShipManagingPos(source.getAirCurrentWorld(), source.getAirCurrentPos());
        }
        return null;
    }

    @Inject(method = "getFlowLimit", at = @At("RETURN"), cancellable = true, remap = false)
    private static void clipFlowLimit(Level level, BlockPos start, float originalMax, Direction facing, CallbackInfoReturnable<Float> cir) {
        // First let Create do its job at finding block obstructions that will cap max length, then use this value as ship search range.
        final float flowLimit = cir.getReturnValue();

        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, start);
        if (ship != null) {
            final Vector3d startVec = new Vector3d(start.m_123341_(), start.m_123342_(), start.m_123343_());
            final Vector3d direction = VectorConversionsMCKt.toJOMLD(facing.m_122436_());
            startVec.add(0.5, 0.5, 0.5).add(direction.mul(0.5, new Vector3d()));
            ship.getTransform().getShipToWorld().transformPosition(startVec);
            ship.getTransform().getShipToWorld().transformDirection(direction);

            final Vector3dc scaling = ship.getTransform().getShipToWorldScaling();
            final double shipScale = facing.m_122434_().m_6150_(scaling.x(), scaling.y(), scaling.z());

            direction.mul(flowLimit);
            final Vec3 startPos = VectorConversionsMCKt.toMinecraft(startVec);
            final Vec3 endPos = VectorConversionsMCKt.toMinecraft(startVec.add(direction.x, direction.y, direction.z));
            final BlockHitResult result = level.m_45547_(new AirFlowClipContext(level, start, startPos, endPos, MixinAirCurrent::shouldAlwaysPass));

            // Convert world space distance to ship space distance by dividing by shipScale
            double limit = result.m_82450_().m_82554_(startPos) / shipScale + EPS2;
            // crazy Create compat
            if (result.m_6662_() == HitResult.Type.BLOCK) {
                final BlockPos pos = result.m_82425_();
                if (level.m_8055_(pos).m_60812_(level, pos) != Shapes.m_83144_()) {
                    limit += NON_BLOCK_EXTEND;
                }
            }
            cir.setReturnValue((float) (limit));
            return;
        }
        final BlockPos end = start.m_5484_(facing, (int) (Math.ceil(flowLimit)));
        if (
            VSGameUtilsKt.getShipsIntersecting(
                level,
                new AABB(start.m_123341_(), start.m_123342_(), start.m_123343_(), end.m_123341_() + 1, end.m_123342_() + 1, end.m_123343_() + 1)
            )
                .iterator()
                .hasNext()
        ) {
            final Vec3 startPos = Vec3.m_82512_(start).m_82520_(facing.m_122429_() * 0.5, facing.m_122430_() * 0.5, facing.m_122431_() * 0.5);
            final Vec3 endPos = Vec3.m_82512_(end).m_82520_(facing.m_122429_() * 0.5, facing.m_122430_() * 0.5, facing.m_122431_() * 0.5);
            final BlockHitResult result = level.m_45547_(new AirFlowClipContext(level, start, startPos, endPos, MixinAirCurrent::shouldAlwaysPass));
            double limit = result.m_82450_().m_82554_(startPos) + EPS2;
            // crazy Create compat
            if (result.m_6662_() == HitResult.Type.BLOCK) {
                final BlockPos pos = result.m_82425_();
                if (level.m_8055_(pos).m_60812_(level, pos) != Shapes.m_83144_()) {
                    limit += NON_BLOCK_EXTEND;
                }
            }
            cir.setReturnValue(Math.min((float) (limit), flowLimit));
        }
    }

    @Inject(
        method = "rebuild",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/fan/IAirCurrentSource;getAirCurrentWorld()Lnet/minecraft/world/level/Level;",
            remap = true
        ),
        remap = false
    )
    private void calcScaling(CallbackInfo ci) {
        Ship ship = this.getShip();
        if (ship != null) {
            final Vector3dc scaling = ship.getTransform().getShipToWorldScaling();
            this.shipScale = this.direction.m_122434_().m_6150_(scaling.x(), scaling.y(), scaling.z());
        }
    }

    @Inject(method = "rebuild", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/AirCurrent;getLimit()I"), remap = false)
    private void stealInitialType(CallbackInfo ci, @Local(name = "type") FanProcessingType type) {
        initialProcessingType = type;
    }

    /**
     * MIT License
     * Copyright (c) The Create Team / The Creators of Create
     * Modified by zyxkad, 2025
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
     * and associated documentation files (the "Software"), to deal in the Software without restriction,
     * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
     * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
     * subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
     * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
     * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
     * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
     * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
     * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
     */
    @Inject(method = "rebuild", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/AirCurrent;findAffectedHandlers()V"), remap = false)
    private void calcSegments(final CallbackInfo ci) {
        this.segments.clear();
        final Level level = this.source.getAirCurrentWorld();
        final BlockPos start = this.source.getAirCurrentPos();
        final Vec3 startCenter = start.m_252807_();
        AdvancedAirCurrentSegment currentSegment = null;
        FanProcessingType type = initialProcessingType; // do not assume this to be null, might be modified by another mixin ;)

        final int limit = this.getLimit();
        // Note: Weird create behaviour that makes pulling fan process depot right under a processor
        // but not for pushing fan.

        final Vec3 delta = new Vec3(this.direction.m_122429_() * 0.5, this.direction.m_122430_() * 0.5, this.direction.m_122431_() * 0.5);
        final Vec3 startPos = startCenter.m_82549_(delta);
        final Vec3 endPos = startCenter.m_231075_(this.direction, this.maxDistance).m_82549_(delta);
        final AdvancedBlockWalker walker = new AdvancedBlockWalker(level, startPos, endPos, !this.pushing, true);
        while (walker.hasNext()) {
            final AdvancedBlockWalker.BlockPosWithDistance data = walker.next();
            final FanProcessingType newType = FanProcessingType.getAt(level, data.pos());
            double dist = data.distance();
            if (dist < Integer.MAX_VALUE && Math.abs(dist - (int) (dist)) < EPS1) {
                dist = (int) (dist);
            }
            if (newType != null) {
                type = newType;
            }
            if (currentSegment == null) {
                currentSegment = new AdvancedAirCurrentSegment();
                currentSegment.startOffset = dist;
                currentSegment.type = type;
            } else if (currentSegment.type != type) {
                currentSegment.endOffset = dist;
                this.segments.add(currentSegment);
                currentSegment = new AdvancedAirCurrentSegment();
                currentSegment.startOffset = dist;
                currentSegment.type = type;
            }
        }
        if (currentSegment != null) {
            currentSegment.endOffset = this.pushing ? limit : 0;
            this.segments.add(currentSegment);
        }
    }

    /**
     * On scaled ships we move the entity position closer to the current source, so that subsequently called distance
     * calculations that might or might not be Create-specific give a value accounted for ship-to-world scaling.
     */
    @WrapOperation(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 transformEntityPos(Entity instance, Operation<Vec3> original) {
        Vec3 result = original.call(instance);

        Ship ship = this.getShip();
        if (ship == null || VSEntityManager.INSTANCE.getHandler(instance) instanceof DefaultShipyardEntityHandler) {
            return result;
        }
        Vector3dc sourcePos = VectorConversionsMCKt.toJOML(source.getAirCurrentPos().m_252807_());
        Vector3dc naiveEntityPos = ship.getWorldToShip().transformPosition(VectorConversionsMCKt.toJOML(result));

        Vector3dc distanceFromSource = VectorConversionsMCKt.toJOML(source.getAirCurrentPos().m_252807_()).sub(naiveEntityPos);
        Vector3dc adjustedEntityPos = sourcePos.sub(
            distanceFromSource.div(this.shipScale, new Vector3d()),
            new Vector3d()
        );
        return VectorConversionsMCKt.toMinecraft(adjustedEntityPos);
    }

    /**
     * Our fake entity position is really useful for all ship- and scale-aware of distance calculations, particles
     * should be spawned where the entity actually is.
     */
    @ModifyArg(
        method = "tickAffectedEntities",
        at = @At(value = "INVOKE", target = "Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;spawnProcessingParticles(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)V"),
        index = 1
    )
    private Vec3 useRealEntityPosition(Vec3 pos, @Local Entity entity) {
        return entity.m_20182_();
    }

    @Redirect(method = "tickAffectedEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean redirectIntersects(AABB entityAABB, AABB boundsAABB) {
        Ship ship = this.getShip();
        if (ship == null) {
            return entityAABB.m_82381_(boundsAABB);
        }
        AABBd entityInShipAABB = VectorConversionsMCKt.toJOML(entityAABB).transform(ship.getWorldToShip());
        AABBd boundsInWorldAABB = VectorConversionsMCKt.toJOML(boundsAABB).transform(ship.getShipToWorld());
        return
            boundsAABB.m_82314_(
                entityInShipAABB.minX, entityInShipAABB.minY, entityInShipAABB.minZ,
                entityInShipAABB.maxX, entityInShipAABB.maxY, entityInShipAABB.maxZ
            ) &&
            entityAABB.m_82314_(
                boundsInWorldAABB.minX, boundsInWorldAABB.minY, boundsInWorldAABB.minZ,
                boundsInWorldAABB.maxX, boundsInWorldAABB.maxY, boundsInWorldAABB.maxZ
            );
    }

    // Ordinals used here are correct both for v0.5.1 and v6 and in fact are stable all the way from Create v0.3.
    @WrapOperation(method = "tickAffectedEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V")
    )
    private void redirectSetDeltaMovement(Entity instance, Vec3 motion,
        Operation<Void> original,
        @Local(ordinal = 2) float acceleration, @Local(ordinal = 3) float maxAcceleration, @Local(ordinal = 0) Vec3i flow
    ) {
        Ship ship = this.getShip();

        if (ship != null && !(VSEntityManager.INSTANCE.getHandler(instance) instanceof DefaultShipyardEntityHandler)) {
            Vector3d tempVec = new Vector3d();
            ship.getTransform().getShipToWorld().transformDirection(flow.m_123341_(), flow.m_123342_(), flow.m_123343_(), tempVec);
            Vec3 transformedFlow = VectorConversionsMCKt.toMinecraft(tempVec);

            Vec3 previousMotion = instance.m_20184_();
            double xIn = Mth.m_14008_(transformedFlow.f_82479_ * acceleration - previousMotion.f_82479_, -maxAcceleration, maxAcceleration);
            double yIn = Mth.m_14008_(transformedFlow.f_82480_ * acceleration - previousMotion.f_82480_, -maxAcceleration, maxAcceleration);
            double zIn = Mth.m_14008_(transformedFlow.f_82481_ * acceleration - previousMotion.f_82481_, -maxAcceleration, maxAcceleration);
            motion = previousMotion.m_82549_(new Vec3(xIn, yIn, zIn).m_82490_(1 / 8f));
        }
        original.call(instance, motion);
    }

    /**
     * MIT License
     * Copyright (c) The Create Team / The Creators of Create
     * Modified by zyxkad, 2025
     */
    @Inject(method = "findAffectedHandlers", at = @At("HEAD"), cancellable = true, remap = false)
    private void findAffectedHandlers(final CallbackInfo ci) {
        ci.cancel();
        this.affectedItemHandlers.clear();
        final Level level = this.source.getAirCurrentWorld();
        final BlockPos start = this.source.getAirCurrentPos();
        final Vec3 startCenter = start.m_252807_();

        final List<AdvancedBlockWalker.BlockPosWithDistance> datas = new ArrayList<>();

        final Vec3 delta = new Vec3(this.direction.m_122429_() * 0.5, this.direction.m_122430_() * 0.5, this.direction.m_122431_() * 0.5);
        final Vec3 startPos = startCenter.m_82549_(delta);
        final Vec3 endPos = startCenter.m_231075_(this.direction, this.maxDistance).m_82549_(delta);
        final AdvancedBlockWalker walker = new AdvancedBlockWalker(level, startPos, endPos, !this.pushing, false);
        while (walker.hasNext()) {
            datas.add(walker.next());
        }

        final Set<BlockPos> processed = new HashSet<>();

        // Process below blocks such as depot, after processed all blocks on the path,
        // so vertical current will process with correct FanProcessingType.
        for (final boolean checkBelow : FALSE_THEN_TRUE) {
            for (final AdvancedBlockWalker.BlockPosWithDistance data : datas) {
                final BlockPos pos = checkBelow ? data.pos().m_7495_() : data.pos();
                final TransportedItemStackHandlerBehaviour behaviour =
                    BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
                if (behaviour == null) {
                    continue;
                }
                // Move the check point towards the block center for a bit,
                // so getTypeAt0 can correctly handle the case that a depot is
                // right after a processor.
                double dist = data.distance() + EPS3;
                if (dist < Integer.MAX_VALUE && Math.abs(dist - (int) (dist)) < EPS1) {
                    dist = (int) (dist);
                }
                if (dist > this.maxDistance) {
                    continue;
                }
                if (!processed.add(pos)) {
                    continue;
                }
                FanProcessingType type = FanProcessingType.getAt(level, pos);
                if (type == null) {
                    type = this.getTypeAt0(dist);
                }
                this.affectedItemHandlers.add(Pair.of(behaviour, type));
            }
        }
    }

    @Inject(method = "getTypeAt", at = @At("HEAD"), cancellable = true, remap = false)
    private void getTypeAt(float offset, final CallbackInfoReturnable<FanProcessingType> cir) {
        cir.setReturnValue(this.getTypeAt0(offset));
    }

    /**
     * MIT License
     * Copyright (c) The Create Team / The Creators of Create
     * Modified by zyxkad, 2025
     */
    @Unique
    private FanProcessingType getTypeAt0(double offset) {
        if (offset < Integer.MAX_VALUE && Math.abs(offset - (int) (offset)) < EPS1) {
            offset = (int) (offset);
        }
        if (offset < 0 || offset > this.maxDistance) {
            return null;
        }
        if (this.pushing) {
            for (final AdvancedAirCurrentSegment segment : this.segments) {
                if (offset <= segment.endOffset) {
                    return segment.type;
                }
            }
        } else {
            for (final AdvancedAirCurrentSegment segment : this.segments) {
                if (offset >= segment.endOffset) {
                    return segment.type;
                }
            }
        }
        return null;
    }
}
