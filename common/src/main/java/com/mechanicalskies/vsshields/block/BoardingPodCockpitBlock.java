package com.mechanicalskies.vsshields.block;

import com.mechanicalskies.vsshields.entity.CockpitSeatEntity;
import com.mechanicalskies.vsshields.pod.BoardingPodAssembler;
import com.mechanicalskies.vsshields.registry.ModBlocks;
import com.mechanicalskies.vsshields.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.Set;

/**
 * Cockpit block of the Boarding Pod multiblock.
 *
 * Right-click when the adjacent {@link BoardingPodMotorBlock} is present:
 *   1. Transforms both block positions (shipyard→world) via the VS2 ship matrix.
 *   2. Spawns a {@link BoardingPodEntity} at their world-space midpoint.
 *   3. Mounts the player as passenger (AIMING phase).
 *
 * The motor can be in any of the 6 adjacent directions.
 */
public class BoardingPodCockpitBlock extends HorizontalDirectionalBlock {

    public BoardingPodCockpitBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;

        // Compute world-space spawn pos BEFORE assembly (ShipAssembler moves blocks to shipyard)
        Vec3 spawnPos = computeWorldCockpitPos(level, pos);

        // Capture the ship we're launching FROM so the pod ignores it during flight
        Ship launchShip = VSGameUtilsKt.getShipManagingPos(sl, pos);
        long ignoredShipId = launchShip != null ? launchShip.getId() : Long.MIN_VALUE;

        // BFS-collect all connected pod blocks (cockpit + engine) and assemble into a VS2 ship
        Set<BlockPos> blocks = BoardingPodAssembler.collectPodBlocks(sl, pos);

        // Require at least one engine block — cockpit alone cannot launch or RCS
        boolean hasEngine = blocks.stream()
                .anyMatch(bp -> sl.getBlockState(bp).getBlock() == ModBlocks.BOARDING_POD_ENGINE.get());
        if (!hasEngine) {
            player.sendSystemMessage(
                    Component.translatable("message.vs_shields.pod_no_motor")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        long podShipId = BoardingPodAssembler.assemble(sl, blocks);
        if (podShipId == Long.MIN_VALUE) {
            player.sendSystemMessage(
                    Component.translatable("message.vs_shields.pod_assembly_failed")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // Delay seat spawn by 2 ticks — gives VS2 time to finish assembly before the
        // player is seated inside the pod, avoiding a solid-block collision on mount.
        final Vec3   finalSpawnPos      = spawnPos;
        final long   finalPodShipId     = podShipId;
        final long   finalIgnoredShipId = ignoredShipId;
        final float  yRot               = player.getYRot();
        final float  xRot               = player.getXRot();

        sl.getServer().tell(new net.minecraft.server.TickTask(
                sl.getServer().getTickCount() + 2, () -> {
            if (!player.isAlive()) return;  // player logged off in 2 ticks — skip
            CockpitSeatEntity delayedSeat = new CockpitSeatEntity(ModEntities.COCKPIT_SEAT.get(), sl);
            // Lower Y by 0.2 so entity centre sits deeper inside the cockpit block
            // Combined with getPassengersRidingOffset()=-1.15, eyes land at ~blockY+0.77
            delayedSeat.moveTo(finalSpawnPos.x, finalSpawnPos.y - 0.2, finalSpawnPos.z, yRot, xRot);
            sl.addFreshEntity(delayedSeat);
            player.startRiding(delayedSeat, true);
            CockpitSeatEntity.notifyPodRegistered(finalPodShipId, delayedSeat.getId(), finalIgnoredShipId,
                    org.valkyrienskies.mod.common.VSGameUtilsKt.getDimensionId(sl));
        }));

        return InteractionResult.CONSUME;
    }

    /**
     * Returns the world-space center of a cockpit block.
     * Handles shipyard→world transform for blocks that are already on a VS2 ship.
     */
    private Vec3 computeWorldCockpitPos(Level level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship != null) {
            Vector3d world = ship.getShipToWorld().transformPosition(new Vector3d(cx, cy, cz), new Vector3d());
            return new Vec3(world.x, world.y, world.z);
        }
        return new Vec3(cx, cy, cz);
    }
}
