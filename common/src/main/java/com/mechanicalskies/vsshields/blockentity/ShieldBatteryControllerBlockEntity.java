package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.menu.ShieldBatteryMenu;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModBlocks;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Shield Battery Controller — center of one face of a 3x3x3 multiblock.
 */
public class ShieldBatteryControllerBlockEntity extends BlockEntity
        implements ExtendedMenuProvider, ShieldInstance.DamageListener {

    public static final int STATUS_INCOMPLETE = 0;
    public static final int STATUS_NO_SHIELD = 1;
    public static final int STATUS_READY = 2;
    public static final int STATUS_DEPLETED = 3;

    private long trackedShipId = -1;
    private int energyStored = 0;
    private int maxEnergy = 500000;
    private int cellCount = 0;
    private int storageCellCount = 0;
    private boolean formed = false;
    private long lastEmergencyTick = -99999;
    private int status = STATUS_INCOMPLETE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> cellCount;
                case 1 -> energyStored & 0xFFFF;
                case 2 -> (energyStored >> 16) & 0xFFFF;
                case 3 -> maxEnergy & 0xFFFF;
                case 4 -> (maxEnergy >> 16) & 0xFFFF;
                case 5 -> status;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 6;
        }
    };

    public ShieldBatteryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIELD_BATTERY_CONTROLLER.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;

        scanCells(level, pos, state);
        formed = (cellCount == 26);

        if (!formed) {
            maxEnergy = 0;
            unregisterListener();
            status = STATUS_INCOMPLETE;
            return;
        }

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            unregisterListener();
            status = STATUS_NO_SHIELD;
            return;
        }

        long shipId = ship.getId();

        if (trackedShipId != shipId) {
            unregisterListener();
            trackedShipId = shipId;
        }

        ShieldInstance shield = ShieldManager.getInstance().getShield(shipId);
        if (shield == null) {
            status = STATUS_NO_SHIELD;
            return;
        }

        if (shield.getDamageListener() != this) {
            shield.setDamageListener(this);
        }

        if (energyStored <= 0) {
            status = STATUS_DEPLETED;
        } else {
            status = STATUS_READY;
        }
    }

    private void scanCells(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction behind = facing.getOpposite();
        Direction right = facing.getClockWise();

        int total = 0;
        int storage = 0;
        for (int depth = 0; depth < 3; depth++) {
            for (int side = -1; side <= 1; side++) {
                for (int up = -1; up <= 1; up++) {
                    if (depth == 0 && side == 0 && up == 0) continue;

                    BlockPos checkPos = pos
                            .relative(behind, depth)
                            .relative(right, side)
                            .relative(Direction.UP, up);

                    BlockState checkState = level.getBlockState(checkPos);
                    if (checkState.is(ModBlocks.SHIELD_BATTERY_CELL.get())) {
                        total++;
                        storage++;
                    } else if (checkState.is(ModBlocks.SHIELD_BATTERY_INPUT.get())) {
                        total++;
                    }
                }
            }
        }
        this.cellCount = total;
        this.storageCellCount = storage;
    }

    @Override
    public void onShieldDamaged(ShieldInstance shield, double absorbed, long tick) {
        if (!formed || energyStored <= 0) return;
    }

    public int getEnergyStored() { return energyStored; }
    public int getMaxEnergy() { return maxEnergy; }
    public boolean isFormed() { return formed; }

    public int receiveEnergy(int amount, boolean simulate) {
        if (!formed) return 0;
        int accepted = Math.min(amount, maxEnergy - energyStored);
        if (!simulate) {
            energyStored += accepted;
            setChanged();
        }
        return accepted;
    }

    public long getTrackedShipId() { return trackedShipId; }

    public void onRemoved() {
        unregisterListener();
    }

    private void unregisterListener() {
        if (trackedShipId != -1) {
            ShieldInstance shield = ShieldManager.getInstance().getShield(trackedShipId);
            if (shield != null && shield.getDamageListener() == this) {
                shield.setDamageListener(null);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStored = tag.getInt("Energy");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vs_shields.shield_battery");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ShieldBatteryMenu(containerId, playerInv, this, dataAccess);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(getBlockPos());
        buf.writeLong(trackedShipId);
    }
}
