package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ShieldJammerInputBlockEntity extends BlockEntity {

    private int energyBuffer = 0;
    private static final int BUFFER_SIZE = 100000;

    public interface EnergyInputHook {
        void tick(Level level, BlockPos pos, ShieldJammerInputBlockEntity be);
    }

    private static EnergyInputHook energyInputHook = null;

    public static void setEnergyInputHook(EnergyInputHook hook) {
        energyInputHook = hook;
    }

    public ShieldJammerInputBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.shield_jammer_INPUT.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide)
            return;

        if (energyInputHook != null) {
            energyInputHook.tick(level, pos, this);
        }

        if (energyBuffer > 0) {
            ShieldJammerControllerBlockEntity controller = findController(level, pos);
            if (controller != null && !controller.isActive()) {
                int transferred = controller.receiveEnergy(energyBuffer, false);
                energyBuffer -= transferred;
                if (transferred > 0)
                    setChanged();
            }
        }
    }

    private ShieldJammerControllerBlockEntity findController(Level level, BlockPos pos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (level.getBlockState(check).is(ModBlocks.shield_jammer_CONTROLLER.get())) {
                        BlockEntity be = level.getBlockEntity(check);
                        if (be instanceof ShieldJammerControllerBlockEntity controller) {
                            return controller;
                        }
                    }
                }
            }
        }
        return null;
    }

    public int getEnergyStored() {
        return energyBuffer;
    }

    public int getMaxEnergy() {
        return BUFFER_SIZE;
    }

    public int receiveEnergy(int amount, boolean simulate) {
        if (level != null) {
            ShieldJammerControllerBlockEntity controller = findController(level, getBlockPos());
            if (controller == null || controller.isActive()) {
                return 0; // Reject energy only if missing controller, or if active
            }
        }

        int accepted = Math.min(amount, BUFFER_SIZE - energyBuffer);
        if (!simulate) {
            energyBuffer += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putInt("Energy", energyBuffer);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        energyBuffer = Math.min(nbt.getInt("Energy"), BUFFER_SIZE);
    }
}
