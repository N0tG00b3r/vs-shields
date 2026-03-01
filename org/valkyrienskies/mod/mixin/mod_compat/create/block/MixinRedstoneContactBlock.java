package org.valkyrienskies.mod.mixin.mod_compat.create.block;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.redstone.contact.RedstoneContactBlock;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
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
import org.valkyrienskies.mod.common.util.VectorConversionsMCKt;

@Mixin(RedstoneContactBlock.class)
public abstract class MixinRedstoneContactBlock extends WrenchableDirectionalBlock {

    @Shadow
    @Final
    public static BooleanProperty POWERED;
    @Unique
    private static final double CHECK_BOUND = 2.0 / 16;
    @Unique
    private static final double INTERSECT_BOUND = CHECK_BOUND + 0.1;

    protected MixinRedstoneContactBlock() {
        super(null);
    }

    @Override
    public void m_6807_(
        final BlockState state,
        final Level world,
        final BlockPos pos,
        final BlockState oldState,
        final boolean isMoving
    ) {
        super.m_6807_(state, world, pos, oldState, isMoving);
        world.m_186460_(pos, this, 2);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void injectTick(
        final BlockState state,
        final ServerLevel world,
        final BlockPos pos,
        final RandomSource random,
        final CallbackInfo ci
    ) {
        if (!world.m_183326_().m_183582_(pos, this)) {
            world.m_186460_(pos, this, 2);
        }
    }

    @Unique
    private static boolean hasContact(
        final LevelAccessor world,
        final BlockPos selfPos,
        final Direction selfDir,
        final Ship ship,
        final BlockPos targetPos,
        final Ship targetShip
    ) {
        final BlockState blockState = world.m_8055_(targetPos);
        if (!isContact(blockState)) {
            return false;
        }
        final Direction targetDir = blockState.m_61143_(f_52588_);
        final Vector3d[] checkPoints = makeCheckPoints(targetPos.m_121945_(targetDir).m_252807_(), targetDir);
        if (targetShip != null) {
            final Matrix4dc shipMat = targetShip.getShipToWorld();
            for (final Vector3d checkPoint : checkPoints) {
                shipMat.transformPosition(checkPoint);
            }
        }
        if (ship != null) {
            final Matrix4dc shipMat = ship.getWorldToShip();
            for (final Vector3d checkPoint : checkPoints) {
                shipMat.transformPosition(checkPoint);
            }
        }
        for (final Vector3d checkPoint : checkPoints) {
            if (selfPos.equals(BlockPos.m_274561_(checkPoint.x, checkPoint.y, checkPoint.z))) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "hasValidContact", at = @At("RETURN"), cancellable = true)
    private static void injectHasValidContact(
        final LevelAccessor world,
        final BlockPos pos,
        final Direction direction,
        final CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ()) {
            return;
        }
        final Level level = (Level) (world);
        final BlockPos detectPos = pos.m_121945_(direction);
        final BlockState facingState = world.m_8055_(detectPos);
        if (isContact(facingState)) {
            cir.setReturnValue(facingState.m_61143_(f_52588_) == direction.m_122424_());
            return;
        }
        if (world.m_8055_(pos).m_60713_(AllBlocks.ELEVATOR_CONTACT.get())) {
            // DO NOT RAY CAST ELEVATOR CONTACT
            // BECAUSE IT IS
            // BASED ON ELEVATOR'S
            // TARGET POSITION
            // NOT THE
            // PEER CONTACT'S POSITION
            return;
        }
        final Vec3 point = detectPos.m_252807_();
        final Vector3d[] checkPoints = makeCheckPoints(point, direction);
        final Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship != null) {
            final Matrix4dc shipMat = ship.getShipToWorld();
            for (final Vector3d checkPoint : checkPoints) {
                shipMat.transformPosition(checkPoint);
            }
        }
        final AABB searchAABB = VSGameUtilsKt.transformAabbToWorld(level, new AABB(
            point.f_82479_ - INTERSECT_BOUND, point.f_82480_ - INTERSECT_BOUND, point.f_82481_ - INTERSECT_BOUND,
            point.f_82479_ + INTERSECT_BOUND, point.f_82480_ + INTERSECT_BOUND, point.f_82481_ + INTERSECT_BOUND
        ));
        BlockPos foundBlock = null;
        boolean found = false;

        for (final Vector3d checkPoint : checkPoints) {
            foundBlock = BlockPos.m_274561_(checkPoint.x, checkPoint.y, checkPoint.z);
            if (hasContact(world, pos, direction, ship, foundBlock, null)) {
                found = true;
                break;
            }
            final Vec3 checkPos = new Vec3(checkPoint.x, checkPoint.y, checkPoint.z);
            for (final AbstractContraptionEntity contraption : world.m_45976_(AbstractContraptionEntity.class, searchAABB)) {
                final Vec3 localPos = contraption.toLocalVector(checkPos, 1);
                final StructureBlockInfo info = contraption.getContraption().getBlocks().get(BlockPos.m_274446_(localPos));
                if (info == null) {
                    continue;
                }
                if (!isContact(info.f_74676_())) {
                    continue;
                }
                final Direction dir = info.f_74676_().m_61143_(f_52588_);
                final Vec3 checkVec = contraption.toGlobalVector(localPos.m_231075_(dir, 0.65), 1);
                final Vector3d checkP = new Vector3d(checkVec.f_82479_, checkVec.f_82480_, checkVec.f_82481_);
                if (ship != null) {
                    ship.getWorldToShip().transformPosition(checkP);
                }
                if (pos.equals(BlockPos.m_274561_(checkP.x, checkP.y, checkP.z))) {
                    foundBlock = null;
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        if (!found) {
            final Vector3d foundPos = new Vector3d();
            for (final Ship targetShip : VSGameUtilsKt.getShipsIntersecting(level, searchAABB)) {
                for (final Vector3d checkPoint : checkPoints) {
                    targetShip.getWorldToShip().transformPosition(checkPoint, foundPos);
                    if (targetShip != ship) {
                        foundBlock = BlockPos.m_274561_(foundPos.x, foundPos.y, foundPos.z);
                        if (hasContact(world, pos, direction, ship, foundBlock, targetShip)) {
                            found = true;
                            break;
                        }
                    }
                    final Vec3 checkPos = new Vec3(foundPos.x, foundPos.y, foundPos.z);
                    final AABB searchAABB2 = VectorConversionsMCKt.toMinecraft(
                        VectorConversionsMCKt.toJOML(searchAABB).transform(targetShip.getWorldToShip())
                    );
                    for (final AbstractContraptionEntity contraption : world.m_45976_(AbstractContraptionEntity.class, searchAABB2)) {
                        final Vec3 localPos = contraption.toLocalVector(checkPos, 1);
                        final StructureBlockInfo info = contraption.getContraption().getBlocks().get(BlockPos.m_274446_(localPos));
                        if (info == null) {
                            continue;
                        }
                        if (!isContact(info.f_74676_())) {
                            continue;
                        }
                        final Direction dir = info.f_74676_().m_61143_(f_52588_);
                        final Vec3 checkVec = contraption.toGlobalVector(localPos.m_231075_(dir, 0.65), 1);
                        final Vector3d checkP = new Vector3d(checkVec.f_82479_, checkVec.f_82480_, checkVec.f_82481_);
                        if (targetShip != ship) {
                            targetShip.getShipToWorld().transformPosition(checkP);
                            if (ship != null) {
                                ship.getWorldToShip().transformPosition(checkP);
                            }
                        }
                        if (pos.equals(BlockPos.m_274561_(checkP.x, checkP.y, checkP.z))) {
                            foundBlock = null;
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
        }
        if (!found) {
            return;
        }
        if (foundBlock != null) {
            final BlockState targetState = world.m_8055_(foundBlock);
            if (!targetState.m_61143_(POWERED)) {
                level.m_46597_(foundBlock, targetState.m_61124_(POWERED, true));
            }
        }
        cir.setReturnValue(true);
    }

    @Unique
    private static boolean isContact(final BlockState state) {
        return state.m_60713_(AllBlocks.REDSTONE_CONTACT.get()) || state.m_60713_(AllBlocks.ELEVATOR_CONTACT.get());
    }

    @Unique
    private static Vector3d[] makeCheckPoints(final Vec3 point, final Direction direction) {
        return switch (direction.m_122434_()) {
            case X -> new Vector3d[]{
                new Vector3d(point.f_82479_, point.f_82480_ - CHECK_BOUND, point.f_82481_ - CHECK_BOUND),
                new Vector3d(point.f_82479_, point.f_82480_ - CHECK_BOUND, point.f_82481_ + CHECK_BOUND),
                new Vector3d(point.f_82479_, point.f_82480_ + CHECK_BOUND, point.f_82481_ - CHECK_BOUND),
                new Vector3d(point.f_82479_, point.f_82480_ + CHECK_BOUND, point.f_82481_ + CHECK_BOUND)
            };
            case Y -> new Vector3d[]{
                new Vector3d(point.f_82479_ - CHECK_BOUND, point.f_82480_, point.f_82481_ - CHECK_BOUND),
                new Vector3d(point.f_82479_ - CHECK_BOUND, point.f_82480_, point.f_82481_ + CHECK_BOUND),
                new Vector3d(point.f_82479_ + CHECK_BOUND, point.f_82480_, point.f_82481_ - CHECK_BOUND),
                new Vector3d(point.f_82479_ + CHECK_BOUND, point.f_82480_, point.f_82481_ + CHECK_BOUND)
            };
            case Z -> new Vector3d[]{
                new Vector3d(point.f_82479_ - CHECK_BOUND, point.f_82480_ - CHECK_BOUND, point.f_82481_),
                new Vector3d(point.f_82479_ - CHECK_BOUND, point.f_82480_ + CHECK_BOUND, point.f_82481_),
                new Vector3d(point.f_82479_ + CHECK_BOUND, point.f_82480_ - CHECK_BOUND, point.f_82481_),
                new Vector3d(point.f_82479_ + CHECK_BOUND, point.f_82480_ + CHECK_BOUND, point.f_82481_)
            };
        };
    }
}
