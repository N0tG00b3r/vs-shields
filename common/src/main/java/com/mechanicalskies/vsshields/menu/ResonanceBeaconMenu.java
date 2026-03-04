package com.mechanicalskies.vsshields.menu;

import com.mechanicalskies.vsshields.blockentity.ResonanceBeaconBlockEntity;
import com.mechanicalskies.vsshields.registry.ModItems;
import com.mechanicalskies.vsshields.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ResonanceBeaconMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos blockPos;

    // Server-side constructor
    public ResonanceBeaconMenu(int containerId, Inventory playerInv,
            ResonanceBeaconBlockEntity be, ContainerData data) {
        super(ModMenus.RESONANCE_BEACON_MENU.get(), containerId);
        this.data = data;
        this.blockPos = be.getBlockPos();
        addDataSlots(data);

        // Crystal slot at position (80, 35) — center of GUI
        addSlot(new Slot(be.crystalSlot, 0, 80, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.REFINED_AETHER_CRYSTAL.get());
            }
        });

        // Player inventory (3 rows)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    // Client-side constructor
    public ResonanceBeaconMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenus.RESONANCE_BEACON_MENU.get(), containerId);
        this.blockPos = buf.readBlockPos();
        this.data = new SimpleContainerData(6);
        addDataSlots(data);

        // Dummy crystal slot (client-side, no BE access)
        addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 80, 35));

        // Player inventory
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    private static int fromSplitShort(int low, int high) {
        return (high << 16) | (low & 0xFFFF);
    }

    public int getEnergyStored() { return fromSplitShort(data.get(0), data.get(1)); }
    public int getMaxEnergy()    { return fromSplitShort(data.get(2), data.get(3)); }
    public int getScanProgress() { return data.get(4); }
    public boolean isScanning()  { return data.get(5) == 1; }
    public BlockPos getBlockPos(){ return blockPos; }

    public double getEnergyPercent() {
        int max = getMaxEnergy();
        return max > 0 ? (double) getEnergyStored() / max : 0;
    }

    public double getScanPercent() {
        if (!isScanning()) return 0;
        int maxTicks = com.mechanicalskies.vsshields.config.ShieldConfig.get().getAnomaly().beaconScanTicks;
        return maxTicks > 0 ? (double) getScanProgress() / maxTicks : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack current = slot.getItem();
        ItemStack copy = current.copy();

        if (index == 0) {
            // Crystal slot → player inventory
            if (!this.moveItemStackTo(current, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            // Player → crystal slot (if refined_aether_crystal)
            if (current.is(ModItems.REFINED_AETHER_CRYSTAL.get())) {
                if (!this.moveItemStackTo(current, 0, 1, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (current.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }
}
