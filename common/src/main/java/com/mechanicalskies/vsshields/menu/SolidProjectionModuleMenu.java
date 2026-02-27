package com.mechanicalskies.vsshields.menu;

import com.mechanicalskies.vsshields.blockentity.SolidProjectionModuleBlockEntity;
import com.mechanicalskies.vsshields.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class SolidProjectionModuleMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos blockPos;
    private final long shipId;
    private final String accessCode;

    public SolidProjectionModuleMenu(int containerId, Inventory playerInv,
            SolidProjectionModuleBlockEntity be, ContainerData data) {
        super(ModMenus.SOLID_PROJECTION_MODULE_MENU.get(), containerId);
        this.data = data;
        this.blockPos = be.getBlockPos();
        this.shipId = be.getTrackedShipId();
        this.accessCode = be.getAccessCode();
        addDataSlots(data);
    }

    public SolidProjectionModuleMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenus.SOLID_PROJECTION_MODULE_MENU.get(), containerId);
        this.blockPos = buf.readBlockPos();
        this.shipId = buf.readLong();
        this.accessCode = buf.readUtf(8);
        this.data = new SimpleContainerData(6);
        addDataSlots(data);
    }

    private static int fromSplitShort(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

    public int getEnergyStored() { return fromSplitShort(data.get(0), data.get(1)); }
    public int getMaxEnergy()    { return fromSplitShort(data.get(2), data.get(3)); }
    public boolean isActive()    { return data.get(4) == 1; }
    public boolean isDuplicate() { return data.get(5) == 1; }
    public long getShipId()      { return shipId; }
    public BlockPos getBlockPos(){ return blockPos; }
    public String getAccessCode(){ return accessCode; }

    public double getEnergyPercent() {
        int max = getMaxEnergy();
        return max > 0 ? (double) getEnergyStored() / max : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }
}
