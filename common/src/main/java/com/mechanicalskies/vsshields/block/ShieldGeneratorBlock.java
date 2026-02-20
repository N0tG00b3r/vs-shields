package com.mechanicalskies.vsshields.block;

import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.shield.ShieldTier;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ShieldGeneratorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private final ShieldTier tier;

    public ShieldGeneratorBlock(BlockBehaviour.Properties properties, ShieldTier tier) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public ShieldTier getTier() {
        return tier;
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShieldGeneratorBlockEntity generator) {
                if (generator.isDuplicate()) {
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.vs_shields.duplicate_generator")
                                    .withStyle(ChatFormatting.RED));
                    return InteractionResult.CONSUME;
                }
                MenuRegistry.openExtendedMenu(serverPlayer, generator);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShieldGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != ModBlockEntities.SHIELD_GENERATOR.get()) return null;
        return (lvl, pos, st, be) -> ((ShieldGeneratorBlockEntity) be).serverTick(lvl, pos, st);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShieldGeneratorBlockEntity generator) {
                generator.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
