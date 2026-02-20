package com.mechanicalskies.vsshields.block;

import com.mechanicalskies.vsshields.blockentity.ShieldCapacitorBlockEntity;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ShieldCapacitorBlock extends Block implements EntityBlock {

    public ShieldCapacitorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShieldCapacitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type != ModBlockEntities.SHIELD_CAPACITOR.get()) return null;
        return (lvl, pos, st, be) -> ((ShieldCapacitorBlockEntity) be).serverTick(lvl, pos, st);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShieldCapacitorBlockEntity capacitor) {
                capacitor.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
