package com.mechanicalskies.vsshields.menu;

import com.mechanicalskies.vsshields.blockentity.ShieldJammerControllerBlockEntity;
import com.mechanicalskies.vsshields.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class ShieldJammerMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos blockPos;
    private final long shipId;

    public ShieldJammerMenu(int containerId, Inventory playerInv, ShieldJammerControllerBlockEntity be,
            ContainerData data) {
        super(ModMenus.shield_jammer_MENU.get(), containerId);
        this.data = data;
        this.blockPos = be.getBlockPos();
        this.shipId = be.getTrackedShipId();
        addDataSlots(data);
    }

    public ShieldJammerMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenus.shield_jammer_MENU.get(), containerId);
        this.blockPos = buf.readBlockPos();
        this.shipId = buf.readLong();
        this.data = new SimpleContainerData(10);
        addDataSlots(data);
    }

    private static int fromSplitShort(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

    public boolean isActive() {
        return data.get(0) == 1;
    }

    public boolean isCooldown() {
        return data.get(1) == 1;
    }

    public boolean isDuplicate() {
        return data.get(7) == 1;
    }

    public int getFrameCount() {
        return data.get(8);
    }

    public boolean isEnabled() {
        return data.get(9) == 1;
    }

    public int getForcedCooldownTicks() {
        return data.get(6);
    }

    public int getEnergyStored() {
        return fromSplitShort(data.get(2), data.get(3));
    }

    public int getMaxEnergy() {
        return fromSplitShort(data.get(4), data.get(5));
    }

    public long getShipId() {
        return shipId;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public double getEnergyPercent() {
        int max = getMaxEnergy();
        return max > 0 ? (double) getEnergyStored() / max : 0;
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
