package com.mechanicalskies.vsshields.block;

import com.mechanicalskies.vsshields.blockentity.SolidProjectionModuleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

public class SolidProjectionModuleBlock extends BaseEntityBlock {

    public SolidProjectionModuleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolidProjectionModuleBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SolidProjectionModuleBlockEntity module) {
                if (player instanceof ServerPlayer serverPlayer) {
                    dev.architectury.registry.menu.MenuRegistry.openExtendedMenu(serverPlayer,
                            (ExtendedMenuProvider) module);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.SOLID_PROJECTION_MODULE.get(),
                SolidProjectionModuleBlockEntity::tick);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SolidProjectionModuleBlockEntity module) {
                module.onBroken();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
