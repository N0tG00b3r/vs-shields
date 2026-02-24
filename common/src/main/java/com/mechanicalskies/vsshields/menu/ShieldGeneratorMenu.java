package com.mechanicalskies.vsshields.menu;

import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity;
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
 * Menu for the Shield Generator.
 */
public class ShieldGeneratorMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos blockPos;
    private final long shipId;

    public ShieldGeneratorMenu(int containerId, Inventory playerInv, ShieldGeneratorBlockEntity be,
            ContainerData data) {
        super(ModMenus.SHIELD_GENERATOR_MENU.get(), containerId);
        this.data = data;
        this.blockPos = be.getBlockPos();
        this.shipId = be.getTrackedShipId();
        addDataSlots(data);
    }

    public ShieldGeneratorMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenus.SHIELD_GENERATOR_MENU.get(), containerId);
        this.blockPos = buf.readBlockPos();
        this.shipId = buf.readLong();
        this.data = new SimpleContainerData(8);
        addDataSlots(data);
    }

    private static int fromSplitShort(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

    public int getCurrentHP10() {
        return data.get(0);
    }

    public int getMaxHP10() {
        return data.get(1);
    }

    public boolean isShieldActive() {
        return data.get(2) == 1;
    }

    public boolean isDuplicate() {
        return data.get(2) == -1;
    }

    public boolean isRegenStalled() {
        return data.get(7) == 1;
    }

    public int getEnergyStored() {
        return fromSplitShort(data.get(3), data.get(4));
    }

    public int getMaxEnergy() {
        return fromSplitShort(data.get(5), data.get(6));
    }

    public long getShipId() {
        return shipId;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public double getCurrentHP() {
        return data.get(0) / 10.0;
    }

    public double getMaxHP() {
        return data.get(1) / 10.0;
    }

    public double getHPPercent() {
        int max = data.get(1);
        return max > 0 ? (double) data.get(0) / max : 0;
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
