package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.block.ShieldGeneratorBlock;
import com.mechanicalskies.vsshields.menu.ShieldGeneratorMenu;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import com.mechanicalskies.vsshields.shield.ShieldTier;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShieldGeneratorBlockEntity extends BlockEntity implements ExtendedMenuProvider {
    public interface EnergyInputHook {
        void tick(Level level, BlockPos pos, ShieldGeneratorBlockEntity be);
    }

    private static EnergyInputHook energyInputHook = null;

    /** Single slot for storing a FrequencyIDCard (master key). */
    private final SimpleContainer cardSlot = new SimpleContainer(1);
    {
        cardSlot.addListener(inv -> setChanged());
    }

    public SimpleContainer getCardSlot() {
        return cardSlot;
    }

    public static void setEnergyInputHook(EnergyInputHook hook) {
        energyInputHook = hook;
    }

    private static final int REDSTONE_SIGNAL_DURATION = 20; // ticks (1 second)

    private long trackedShipId = -1;
    private boolean duplicate = false;
    private long lastKnownHitTick = 0; // mirrors ShieldInstance.lastHitTick
    private long damageSignalTick = -1; // level.getGameTime() when last hit detected

    /** Last known state of incoming redstone signal — used to detect rising/falling edge. */
    private boolean prevRedstoneSignal = false;

    private int energyStored = 0;
    private int maxEnergy = 50000;
    private int energyPerTick = 20;
    private boolean hasPower = false;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            if (duplicate) {
                return index == 2 ? -1 : 0;
            }
            ShieldInstance shield = trackedShipId != -1 ? ShieldManager.getInstance().getShield(trackedShipId) : null;
            return switch (index) {
                case 0 -> shield != null ? (int) (shield.getCurrentHP() * 10) : 0;
                case 1 -> shield != null ? (int) (shield.getMaxHP() * 10) : 0;
                case 2 -> shield != null ? (shield.isActive() ? 1 : 0) : 0;
                case 3 -> energyStored & 0xFFFF;
                case 4 -> (energyStored >> 16) & 0xFFFF;
                case 5 -> maxEnergy & 0xFFFF;
                case 6 -> (maxEnergy >> 16) & 0xFFFF;
                case 7 -> shield != null && shield.isRegenStalled() ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    public ShieldGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIELD_GENERATOR.get(), pos, state);
    }

    private void initEnergyFromTier() {
        ShieldTier tier = getShieldTier();
        this.maxEnergy = tier.getMaxEnergy();
        this.energyPerTick = tier.getEnergyPerTick();
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide())
            return;

        if (maxEnergy == 50000 && getShieldTier() != ShieldTier.IRON) {
            initEnergyFromTier();
        }

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            if (trackedShipId != -1) {
                ShieldManager.getInstance().unregisterShield(trackedShipId, pos);
                trackedShipId = -1;
                duplicate = false;
            }
            return;
        }

        long shipId = ship.getId();
        ShieldTier tier = getShieldTier();

        if (trackedShipId != shipId) {
            if (trackedShipId != -1) {
                ShieldManager.getInstance().unregisterShield(trackedShipId, pos);
            }
            boolean isOwner = ShieldManager.getInstance().registerShield(shipId, tier, pos);
            trackedShipId = shipId;
            duplicate = !isOwner;
            lastKnownHitTick = 0; // reset for the new shield instance
        }

        if (duplicate)
            return;

        if (energyInputHook != null) {
            energyInputHook.tick(level, pos, this);
        }

        ShieldInstance shield = ShieldManager.getInstance().getShield(shipId);

        // Redstone activation control — rising edge activates, falling edge deactivates
        boolean redstoneSignal = level.hasNeighborSignal(pos);
        if (redstoneSignal != prevRedstoneSignal) {
            prevRedstoneSignal = redstoneSignal;
            if (shield != null) {
                shield.setActive(redstoneSignal);
            }
        }

        if (shield != null) {
            if (shield.isActive()) {
                int baseCost = energyPerTick;
                if (shield.isRegenerating(level.getGameTime())) {
                    int emitterCost = com.mechanicalskies.vsshields.config.ShieldConfig.get()
                            .getBonuses().emitterRegenCost;
                    int regenCost = shield.getEmitterCount() * emitterCost;

                    if (energyStored >= baseCost + regenCost) {
                        energyStored -= (baseCost + regenCost);
                        hasPower = true;
                        shield.setRegenStalled(false);
                    } else if (energyStored >= baseCost) {
                        energyStored -= baseCost;
                        hasPower = true;
                        shield.setRegenStalled(true);
                    } else {
                        hasPower = false;
                        shield.setActive(false);
                        shield.setRegenStalled(false);
                    }
                } else {
                    if (energyStored >= baseCost) {
                        energyStored -= baseCost;
                        hasPower = true;
                        shield.setRegenStalled(false);
                    } else {
                        hasPower = false;
                        shield.setActive(false);
                        shield.setRegenStalled(false);
                    }
                }
            } else {
                hasPower = energyStored >= energyPerTick;
                shield.setRegenStalled(false);
            }

            com.mechanicalskies.vsshields.config.ShieldConfig.GeneralConfig gen = com.mechanicalskies.vsshields.config.ShieldConfig
                    .get().getGeneral();
            double energyPercent = maxEnergy > 0 ? (double) energyStored / maxEnergy : 0;
            double hpScale = gen.hpScaleMin + (gen.hpScaleMax - gen.hpScaleMin) * energyPercent;
            shield.setHPScale(hpScale);
            shield.setEnergyPercent(energyPercent);

            // Detect new damage by watching lastHitTick change
            long hitTick = shield.getLastHitTick();
            if (hitTick > lastKnownHitTick) {
                lastKnownHitTick = hitTick;
                damageSignalTick = level.getGameTime();
            }
        }

        // Update POWERED blockstate for redstone output
        long now = level.getGameTime();
        boolean shouldSignal = damageSignalTick >= 0 && (now - damageSignalTick) < REDSTONE_SIGNAL_DURATION;
        boolean currentPowered = state.getValue(com.mechanicalskies.vsshields.block.ShieldGeneratorBlock.POWERED);
        if (shouldSignal != currentPowered) {
            level.setBlock(pos,
                    state.setValue(com.mechanicalskies.vsshields.block.ShieldGeneratorBlock.POWERED, shouldSignal), 3);
        }
    }

    public void drainEnergyFromJammer(int amount) {
        this.energyStored = Math.max(0, this.energyStored - amount);
        this.setChanged();
        if (this.energyStored <= 0) {
            this.hasPower = false;
        }
    }

    public int getEnergyStored() {
        return energyStored;
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public boolean hasPower() {
        return hasPower;
    }

    public int receiveEnergy(int amount, boolean simulate) {
        int accepted = Math.min(amount, maxEnergy - energyStored);
        if (!simulate) {
            energyStored += accepted;
            setChanged();
        }
        return accepted;
    }

    public long getTrackedShipId() {
        return trackedShipId;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void onRemoved() {
        if (trackedShipId != -1) {
            ShieldManager.getInstance().unregisterShield(trackedShipId, getBlockPos());
            trackedShipId = -1;
            duplicate = false;
        }
    }

    private ShieldTier getShieldTier() {
        if (getBlockState().getBlock() instanceof ShieldGeneratorBlock generator) {
            return generator.getTier();
        }
        return ShieldTier.IRON;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStored);
        ItemStack card = cardSlot.getItem(0);
        if (!card.isEmpty()) {
            tag.put("CardSlot", card.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        initEnergyFromTier();
        energyStored = Math.min(tag.getInt("Energy"), maxEnergy);
        if (tag.contains("CardSlot")) {
            cardSlot.setItem(0, ItemStack.of(tag.getCompound("CardSlot")));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vs_shields.shield_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ShieldGeneratorMenu(containerId, playerInv, this, dataAccess);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(getBlockPos());
        buf.writeLong(trackedShipId);
    }

}
