package com.mechanicalskies.vsshields.menu;

import com.mechanicalskies.vsshields.blockentity.ShieldBatteryControllerBlockEntity;
import com.mechanicalskies.vsshields.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Shield Battery Controller.
 */
public class ShieldBatteryMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos blockPos;
    private final long shipId;

    public ShieldBatteryMenu(int containerId, Inventory playerInv,
                             ShieldBatteryControllerBlockEntity be, ContainerData data) {
        super(ModMenus.SHIELD_BATTERY_MENU.get(), containerId);
        this.data = data;
        this.blockPos = be.getBlockPos();
        this.shipId = be.getTrackedShipId();
        addDataSlots(data);
    }

    public ShieldBatteryMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenus.SHIELD_BATTERY_MENU.get(), containerId);
        this.blockPos = buf.readBlockPos();
        this.shipId = buf.readLong();
        this.data = new SimpleContainerData(6);
        addDataSlots(data);
    }

    private static int fromSplitShort(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

    public int getCellCount() { return data.get(0); }
    public int getEnergyStored() { return fromSplitShort(data.get(1), data.get(2)); }
    public int getMaxEnergy() { return fromSplitShort(data.get(3), data.get(4)); }
    public int getStatus() { return data.get(5); }
    public long getShipId() { return shipId; }
    public BlockPos getBlockPos() { return blockPos; }

    public boolean isFormed() { return getCellCount() == 26; }

    public double getEnergyPercent() {
        int max = getMaxEnergy();
        return max > 0 ? (double) getEnergyStored() / max : 0;
    }

    public String getStatusText() {
        return switch (getStatus()) {
            case ShieldBatteryControllerBlockEntity.STATUS_NO_SHIELD -> "\u00a7eNo Shield";
            case ShieldBatteryControllerBlockEntity.STATUS_READY -> "\u00a7aReady";
            case ShieldBatteryControllerBlockEntity.STATUS_DEPLETED -> "\u00a7cDepleted";
            default -> "\u00a7cIncomplete";
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }
}
