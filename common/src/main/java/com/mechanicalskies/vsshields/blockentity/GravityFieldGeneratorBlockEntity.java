package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.menu.GravityFieldMenu;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.shield.GravityFieldRegistry;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class GravityFieldGeneratorBlockEntity extends BlockEntity implements ExtendedMenuProvider {

    public interface EnergyInputHook {
        void tick(Level level, BlockPos pos, GravityFieldGeneratorBlockEntity be);
    }

    private static EnergyInputHook energyInputHook = null;

    public static void setEnergyInputHook(EnergyInputHook hook) {
        energyInputHook = hook;
    }

    // Energy constants
    private static final int MAX_ENERGY  = 1_000_000;
    private static final int MAX_RECEIVE = 50_000;
    // FE/tick costs
    public static final int COST_BASE        = 100;  // base overhead
    public static final int COST_FLIGHT      = 400;  // creative flight
    public static final int COST_FALL_PROT   = 100;  // fall damage immunity

    private long trackedShipId = -1;
    private boolean duplicate = false;

    private boolean isActive = false;
    private boolean flightEnabled = true;
    private boolean fallProtectionEnabled = true;

    public int energyStored = 0;
    public final int maxEnergy = MAX_ENERGY;

    // ContainerData indices:
    // 0: isActive           1: duplicate
    // 2: flightEnabled      3: fallProtectionEnabled
    // 4: energyStored low   5: energyStored high
    // 6: maxEnergy low      7: maxEnergy high
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> isActive ? 1 : 0;
                case 1 -> duplicate ? 1 : 0;
                case 2 -> flightEnabled ? 1 : 0;
                case 3 -> fallProtectionEnabled ? 1 : 0;
                case 4 -> energyStored & 0xFFFF;
                case 5 -> (energyStored >> 16) & 0xFFFF;
                case 6 -> maxEnergy & 0xFFFF;
                case 7 -> (maxEnergy >> 16) & 0xFFFF;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 8; }
    };

    public GravityFieldGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVITY_FIELD_GENERATOR.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, GravityFieldGeneratorBlockEntity be) {
        if (level.isClientSide) return;

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            if (be.trackedShipId != -1) {
                GravityFieldRegistry.unregisterOwner(be.trackedShipId, pos);
                GravityFieldRegistry.remove(be.trackedShipId);
                be.trackedShipId = -1;
                be.duplicate = false;
            }
            return;
        }

        long shipId = ship.getId();

        if (shipId != be.trackedShipId) {
            if (be.trackedShipId != -1) {
                GravityFieldRegistry.unregisterOwner(be.trackedShipId, pos);
                GravityFieldRegistry.remove(be.trackedShipId);
            }
            boolean isOwner = GravityFieldRegistry.registerOwner(shipId, pos);
            be.trackedShipId = shipId;
            be.duplicate = !isOwner;
            be.setChanged();
        }

        if (be.duplicate) {
            GravityFieldRegistry.remove(shipId);
            return;
        }

        if (energyInputHook != null) {
            energyInputHook.tick(level, pos, be);
        }

        if (!be.isActive) {
            GravityFieldRegistry.remove(shipId);
            return;
        }

        // Calculate per-tick cost
        int cost = COST_BASE;
        if (be.flightEnabled) cost += COST_FLIGHT;
        if (be.fallProtectionEnabled) cost += COST_FALL_PROT;

        if (be.energyStored < cost) {
            be.isActive = false;
            GravityFieldRegistry.remove(shipId);
            be.setChanged();
            return;
        }

        be.energyStored -= cost;
        be.setChanged();

        // Publish state so Forge handler can apply effects to players
        AABBdc worldAABB = ship.getWorldAABB();
        if (worldAABB != null) {
            double padding = ShieldConfig.get().getGeneral().shieldPadding;
            GravityFieldRegistry.update(shipId, new GravityFieldRegistry.GravityFieldState(
                    be.flightEnabled, be.fallProtectionEnabled,
                    worldAABB.minX(), worldAABB.minY(), worldAABB.minZ(),
                    worldAABB.maxX(), worldAABB.maxY(), worldAABB.maxZ(),
                    padding));
        }
    }

    public void onBroken() {
        if (level != null && !level.isClientSide && trackedShipId != -1) {
            GravityFieldRegistry.unregisterOwner(trackedShipId, worldPosition);
            GravityFieldRegistry.remove(trackedShipId);
            trackedShipId = -1;
            duplicate = false;
        }
    }

    // --- Setters called by network handler ---

    public void setActive(boolean active) {
        this.isActive = active;
        if (!active && trackedShipId != -1) GravityFieldRegistry.remove(trackedShipId);
        setChanged();
    }

    public void setFlightEnabled(boolean enabled) {
        this.flightEnabled = enabled;
        setChanged();
    }

    public void setFallProtectionEnabled(boolean enabled) {
        this.fallProtectionEnabled = enabled;
        setChanged();
    }

    // --- Getters ---

    public boolean isActive()                { return isActive; }
    public boolean isFlightEnabled()         { return flightEnabled; }
    public boolean isFallProtectionEnabled() { return fallProtectionEnabled; }
    public boolean isDuplicate()             { return duplicate; }
    public long    getTrackedShipId()        { return trackedShipId; }

    // --- FE energy ---

    public int receiveEnergy(int amount, boolean simulate) {
        int accepted = Math.min(Math.min(amount, MAX_RECEIVE), maxEnergy - energyStored);
        if (!simulate) { energyStored += accepted; setChanged(); }
        return accepted;
    }

    public int extractEnergy(int amount, boolean simulate) {
        int extracted = Math.min(amount, energyStored);
        if (!simulate) { energyStored -= extracted; setChanged(); }
        return extracted;
    }

    public int getEnergyStored() { return energyStored; }
    public int getMaxEnergy()    { return maxEnergy; }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("IsActive", isActive);
        tag.putBoolean("FlightEnabled", flightEnabled);
        tag.putBoolean("FallProtectionEnabled", fallProtectionEnabled);
        tag.putInt("Energy", energyStored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        isActive = tag.getBoolean("IsActive");
        flightEnabled = !tag.contains("FlightEnabled") || tag.getBoolean("FlightEnabled");
        fallProtectionEnabled = !tag.contains("FallProtectionEnabled") || tag.getBoolean("FallProtectionEnabled");
        energyStored = Math.min(tag.getInt("Energy"), maxEnergy);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vs_shields.gravity_field_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new GravityFieldMenu(containerId, playerInv, this, dataAccess);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeLong(trackedShipId);
    }
}
