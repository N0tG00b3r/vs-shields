package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.menu.CloakGeneratorMenu;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.shield.CloakManager;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class CloakingFieldGeneratorBlockEntity extends BlockEntity implements ExtendedMenuProvider {

    public interface EnergyInputHook {
        void tick(Level level, BlockPos pos, CloakingFieldGeneratorBlockEntity be);
    }

    private static EnergyInputHook energyInputHook = null;

    public static void setEnergyInputHook(EnergyInputHook hook) {
        energyInputHook = hook;
    }

    private long trackedShipId = -1;
    private boolean duplicate = false;
    private boolean isCloakingActive = false;

    public int energyStored = 0;
    public int maxEnergy = 100000;
    private int energyPerTick = 30;
    private boolean energyInitialized = false;

    // ContainerData indices:
    // 0: cloakState  (1=active+cloaked, 0=inactive, -1=duplicate)
    // 1: energyStored low 16 bits
    // 2: energyStored high 16 bits
    // 3: maxEnergy low 16 bits
    // 4: maxEnergy high 16 bits
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            if (duplicate) {
                return index == 0 ? -1 : 0;
            }
            return switch (index) {
                case 0 -> isCloakingActive ? 1 : 0;
                case 1 -> energyStored & 0xFFFF;
                case 2 -> (energyStored >> 16) & 0xFFFF;
                case 3 -> maxEnergy & 0xFFFF;
                case 4 -> (maxEnergy >> 16) & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 5;
        }
    };

    public CloakingFieldGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLOAKING_FIELD_GENERATOR.get(), pos, state);
    }

    private void initEnergy() {
        ShieldConfig.CloakConfig cfg = ShieldConfig.get().getCloak();
        this.maxEnergy = cfg.maxEnergy;
        this.energyPerTick = cfg.energyPerTick;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CloakingFieldGeneratorBlockEntity be) {
        if (level.isClientSide) return;

        if (!be.energyInitialized) {
            be.initEnergy();
            be.energyInitialized = true;
        }

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);

        if (ship == null) {
            if (be.trackedShipId != -1) {
                CloakManager.getInstance().unregisterCloakOwner(be.trackedShipId, pos);
                be.trackedShipId = -1;
                be.duplicate = false;
            }
            return;
        }

        long shipId = ship.getId();

        if (shipId != be.trackedShipId) {
            if (be.trackedShipId != -1) {
                CloakManager.getInstance().unregisterCloakOwner(be.trackedShipId, pos);
            }
            boolean isOwner = CloakManager.getInstance().registerCloakOwner(shipId, pos, level.dimension());
            be.trackedShipId = shipId;
            be.duplicate = !isOwner;
            be.setChanged();
        }

        if (be.duplicate) return;

        if (energyInputHook != null) {
            energyInputHook.tick(level, pos, be);
        }

        MinecraftServer server = level.getServer();
        boolean hasEnoughEnergy = be.energyStored >= be.energyPerTick;
        boolean shouldCloak = be.isCloakingActive && hasEnoughEnergy;

        if (shouldCloak) {
            be.energyStored -= be.energyPerTick;
            be.setChanged();
            CloakManager.getInstance().cloakShip(ship, server);
        } else {
            if (CloakManager.getInstance().isShipCloaked(shipId)) {
                CloakManager.getInstance().uncloakShip(ship, server);
            }
        }
    }

    public void setCloakingActive(boolean active) {
        if (this.isCloakingActive != active) {
            this.isCloakingActive = active;
            setChanged();
            // Actual cloak apply/remove happens in tick()
        }
    }

    public boolean isCloakingActive() {
        return isCloakingActive;
    }

    public long getTrackedShipId() {
        return trackedShipId;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public int receiveEnergy(int amount, boolean simulate) {
        int accepted = Math.min(amount, maxEnergy - energyStored);
        if (!simulate) {
            energyStored += accepted;
            setChanged();
        }
        return accepted;
    }

    public int getEnergyPerTick() {
        return energyPerTick;
    }

    public void onBroken() {
        if (level != null && !level.isClientSide && trackedShipId != -1) {
            CloakManager.getInstance().unregisterCloakOwner(trackedShipId, worldPosition);
            trackedShipId = -1;
            duplicate = false;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putBoolean("IsCloakingActive", isCloakingActive);
        // trackedShipId is NOT persisted — re-discovered on first tick.
        // Persisting it would cause registerCloakOwner() to be skipped on reload
        // (shipId == trackedShipId), leaving cloakOwners empty and toggleCloak() broken.
        nbt.putInt("Energy", energyStored);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        isCloakingActive = nbt.getBoolean("IsCloakingActive");
        // trackedShipId stays at -1 so the first tick always calls registerCloakOwner()
        initEnergy();
        energyInitialized = true;
        energyStored = Math.min(nbt.getInt("Energy"), maxEnergy);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vs_shields.cloaking_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new CloakGeneratorMenu(containerId, playerInv, this, dataAccess);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeLong(trackedShipId);
    }
}
