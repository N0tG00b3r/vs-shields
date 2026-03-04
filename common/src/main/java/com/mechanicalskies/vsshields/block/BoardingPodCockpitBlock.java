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
import org.valkyrienskies.core.api.ships.LoadedServerShip;
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
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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

        // BFS-collect all connected pod blocks and detect any unrecognised adjacent vs_shields blocks
        BoardingPodAssembler.CollectResult collected = BoardingPodAssembler.collectPodBlocks(sl, pos);
        Set<BlockPos> blocks = collected.podBlocks;

        // Reject if any adjacent vs_shields block is not in the allowed whitelist
        if (!collected.invalidBlocks.isEmpty()) {
            player.sendSystemMessage(
                    Component.translatable("message.vs_shields.pod_invalid_block")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // Count cockpits and engines — exactly 1 of each is required
        Block cockpitBlock = ModBlocks.BOARDING_POD_COCKPIT.get();
        Block engineBlock  = ModBlocks.BOARDING_POD_ENGINE.get();
        long cockpitCount = blocks.stream()
                .filter(bp -> sl.getBlockState(bp).getBlock() == cockpitBlock).count();
        long engineCount  = blocks.stream()
                .filter(bp -> sl.getBlockState(bp).getBlock() == engineBlock).count();

        if (cockpitCount > 1) {
            player.sendSystemMessage(
                    Component.translatable("message.vs_shields.pod_too_many_cockpits")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (engineCount == 0) {
            player.sendSystemMessage(
                    Component.translatable("message.vs_shields.pod_no_motor")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (engineCount > 1) {
            player.sendSystemMessage(
                    Component.translatable("message.vs_shields.pod_too_many_engines")
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

        // Capture FACING before assembly — blocks move to shipyard but we need the world direction.
        // Direction.ordinal(): DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5
        final int facingOrdinal = state.getValue(FACING).ordinal();

        // Delay seat spawn by 2 ticks — gives VS2 time to finish assembly before the
        // player is seated inside the pod, avoiding a solid-block collision on mount.
        final Vec3   finalSpawnPos      = spawnPos;
        final long   finalPodShipId     = podShipId;
        final long   finalIgnoredShipId = ignoredShipId;

        sl.getServer().tell(new net.minecraft.server.TickTask(
                sl.getServer().getTickCount() + 2, () -> {
            if (!player.isAlive()) return;  // player logged off in 2 ticks — skip

            // Compute yaw facing the cockpit's forward direction (= FACING).
            // Ship-local yaw = world yaw for a fresh pod ship (identity rotation).
            Direction cockpitFacing = Direction.from3DDataValue(facingOrdinal);
            float cockpitYaw = (float) Math.toDegrees(
                    Math.atan2(-(double) cockpitFacing.getStepX(), (double) cockpitFacing.getStepZ()));

            CockpitSeatEntity delayedSeat = new CockpitSeatEntity(ModEntities.COCKPIT_SEAT.get(), sl);
            delayedSeat.moveTo(finalSpawnPos.x, finalSpawnPos.y - 0.2, finalSpawnPos.z, cockpitYaw, 0f);
            delayedSeat.setPodShipId(finalPodShipId);
            sl.addFreshEntity(delayedSeat);

            // Set player rotation to cockpit forward BEFORE mounting —
            // VS2's ShipMountedToData takes over camera from the next frame,
            // using the player's current yRot as ship-local direction.
            player.setYRot(cockpitYaw);
            player.setXRot(0f);
            player.setYHeadRot(cockpitYaw);
            player.startRiding(delayedSeat, true);

            CockpitSeatEntity.notifyPodRegistered(finalPodShipId, delayedSeat.getId(), finalIgnoredShipId,
                    org.valkyrienskies.mod.common.VSGameUtilsKt.getDimensionId(sl), facingOrdinal);
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
