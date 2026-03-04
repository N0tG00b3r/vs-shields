package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.anomaly.AnomalyInstance;
import com.mechanicalskies.vsshields.anomaly.AnomalyManager;
import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.menu.ResonanceBeaconMenu;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModItems;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
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

public class ResonanceBeaconBlockEntity extends BlockEntity implements ExtendedMenuProvider {

    public int energyStored = 0;
    private int maxEnergy;

    // Crystal slot (1 item)
    public final SimpleContainer crystalSlot = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return stack.is(ModItems.REFINED_AETHER_CRYSTAL.get());
        }
    };

    // Scan state
    private int scanProgress = 0;    // 0 = idle, >0 = scanning
    private boolean scanning = false;
    private ServerPlayer scanRequester = null; // who started the scan

    // ContainerData: 0-1 energy, 2-3 maxEnergy, 4 scanProgress, 5 scanning
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStored & 0xFFFF;
                case 1 -> (energyStored >> 16) & 0xFFFF;
                case 2 -> maxEnergy & 0xFFFF;
                case 3 -> (maxEnergy >> 16) & 0xFFFF;
                case 4 -> scanProgress;
                case 5 -> scanning ? 1 : 0;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 6; }
    };

    public ResonanceBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONANCE_BEACON.get(), pos, state);
        this.maxEnergy = ShieldConfig.get().getAnomaly().beaconMaxEnergy;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ResonanceBeaconBlockEntity be) {
        if (level.isClientSide) return;

        be.maxEnergy = ShieldConfig.get().getAnomaly().beaconMaxEnergy;

        if (be.scanning) {
            be.scanProgress++;
            int scanDuration = ShieldConfig.get().getAnomaly().beaconScanTicks;
            if (be.scanProgress >= scanDuration) {
                be.completeScan();
            }
            be.setChanged();
        }
    }

    /**
     * Called from C2S packet when player clicks SCAN button.
     */
    public void startScan(ServerPlayer player) {
        if (scanning) return;

        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();

        // Check FE
        if (energyStored < config.beaconScanCost) return;

        // Check crystal
        if (crystalSlot.isEmpty() || !crystalSlot.getItem(0).is(ModItems.REFINED_AETHER_CRYSTAL.get())) return;

        // Start scan
        scanning = true;
        scanProgress = 0;
        scanRequester = player;

        // Consume resources
        energyStored -= config.beaconScanCost;
        crystalSlot.getItem(0).shrink(1);
        setChanged();
    }

    private void completeScan() {
        scanning = false;
        scanProgress = 0;

        if (scanRequester != null && !scanRequester.hasDisconnected()) {
            AnomalyInstance anomaly = AnomalyManager.getInstance().getActive();
            if (anomaly != null) {
                long currentTick = level.getGameTime();
                int ttlSeconds = (int) (anomaly.getGlobalTTLRemaining(currentTick,
                        ShieldConfig.get().getAnomaly().globalLifetimeTicks) / 20);
                ModNetwork.sendBeaconScanResult(scanRequester, true,
                        anomaly.getWorldX(), anomaly.getWorldY(), anomaly.getWorldZ(), ttlSeconds);
            } else {
                ModNetwork.sendBeaconScanResult(scanRequester, false, 0, 0, 0, 0);
            }
        }
        scanRequester = null;
        setChanged();
    }

    public int receiveEnergy(int amount, boolean simulate) {
        int maxInput = ShieldConfig.get().getAnomaly().beaconEnergyInput;
        int accepted = Math.min(Math.min(amount, maxInput), maxEnergy - energyStored);
        if (!simulate) { energyStored += accepted; setChanged(); }
        return accepted;
    }

    public int getMaxEnergy() { return maxEnergy; }
    public boolean isScanning() { return scanning; }
    public int getScanProgress() { return scanProgress; }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStored);
        tag.putBoolean("Scanning", scanning);
        tag.putInt("ScanProgress", scanProgress);
        if (!crystalSlot.isEmpty()) {
            tag.put("Crystal", crystalSlot.getItem(0).save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        maxEnergy = ShieldConfig.get().getAnomaly().beaconMaxEnergy;
        energyStored = Math.min(tag.getInt("Energy"), maxEnergy);
        scanning = tag.getBoolean("Scanning");
        scanProgress = tag.getInt("ScanProgress");
        if (tag.contains("Crystal")) {
            crystalSlot.setItem(0, ItemStack.of(tag.getCompound("Crystal")));
        }
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vs_shields.resonance_beacon");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ResonanceBeaconMenu(containerId, playerInv, this, dataAccess);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
    }
}
