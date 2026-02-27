package com.mechanicalskies.vsshields.menu;

import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.item.FrequencyIDCardItem;
import com.mechanicalskies.vsshields.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Shield Generator.
 * Slots: 0 = card slot; 1–27 = player inventory; 28–36 = hotbar.
 */
public class ShieldGeneratorMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final BlockPos blockPos;
    private final long shipId;
    private final SimpleContainer cardContainer;

    // Card slot screen position (relative to leftPos/topPos)
    public static final int CARD_SLOT_X = 80;
    public static final int CARD_SLOT_Y = 94;
    // Player inventory top (relative to topPos)
    public static final int INV_TOP = 146;

    public ShieldGeneratorMenu(int containerId, Inventory playerInv, ShieldGeneratorBlockEntity be,
            ContainerData data) {
        super(ModMenus.SHIELD_GENERATOR_MENU.get(), containerId);
        this.data = data;
        this.blockPos = be.getBlockPos();
        this.shipId = be.getTrackedShipId();
        this.cardContainer = be.getCardSlot();
        addDataSlots(data);
        addSlots(playerInv);
    }

    public ShieldGeneratorMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenus.SHIELD_GENERATOR_MENU.get(), containerId);
        this.blockPos = buf.readBlockPos();
        this.shipId = buf.readLong();
        this.data = new SimpleContainerData(8);
        this.cardContainer = new SimpleContainer(1);
        addDataSlots(data);
        addSlots(playerInv);
    }

    private void addSlots(Inventory playerInv) {
        // Slot 0: card slot
        addSlot(new Slot(cardContainer, 0, CARD_SLOT_X, CARD_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FrequencyIDCardItem;
            }
        });
        // Slots 1–27: player main inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, INV_TOP + row * 18));
            }
        }
        // Slots 28–36: hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, INV_TOP + 58));
        }
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
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            // Card slot → player inventory
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof FrequencyIDCardItem) {
            // Player inventory → card slot (only if it has room)
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0;
    }
}
