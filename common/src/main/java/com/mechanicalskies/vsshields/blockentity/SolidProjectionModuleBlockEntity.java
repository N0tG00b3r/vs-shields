package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.menu.SolidProjectionModuleMenu;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import com.mechanicalskies.vsshields.shield.SolidModuleRegistry;
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
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class SolidProjectionModuleBlockEntity extends BlockEntity implements ExtendedMenuProvider {

    public interface EnergyInputHook {
        void tick(Level level, BlockPos pos, SolidProjectionModuleBlockEntity be);
    }

    private static EnergyInputHook energyInputHook = null;

    public static void setEnergyInputHook(EnergyInputHook hook) {
        energyInputHook = hook;
    }

    private boolean active = false;
    private String accessCode = "";   // up to 6 uppercase alphanumeric chars

    private long trackedShipId = -1;
    private boolean registered = false;
    private boolean duplicate = false;

    public int energyStored = 0;
    private int maxEnergy;

    // ContainerData indices:
    // 0: energyStored low   1: energyStored high
    // 2: maxEnergy low      3: maxEnergy high
    // 4: active             5: duplicate
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStored & 0xFFFF;
                case 1 -> (energyStored >> 16) & 0xFFFF;
                case 2 -> maxEnergy & 0xFFFF;
                case 3 -> (maxEnergy >> 16) & 0xFFFF;
                case 4 -> active ? 1 : 0;
                case 5 -> duplicate ? 1 : 0;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 6; }
    };

    public SolidProjectionModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLID_PROJECTION_MODULE.get(), pos, state);
        this.maxEnergy = ShieldConfig.get().getGeneral().solidModuleMaxEnergy;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SolidProjectionModuleBlockEntity be) {
        if (level.isClientSide) return;

        // Refresh max energy from config in case it changed
        be.maxEnergy = ShieldConfig.get().getGeneral().solidModuleMaxEnergy;

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            be.deactivateSolidMode();
            if (be.trackedShipId != -1) {
                SolidModuleRegistry.getInstance().unregisterOwner(be.trackedShipId, pos);
                be.trackedShipId = -1;
                be.registered = false;
                be.duplicate = false;
            }
            return;
        }

        long shipId = ship.getId();

        // Re-register if ship changed
        if (shipId != be.trackedShipId) {
            if (be.trackedShipId != -1) {
                be.deactivateSolidMode();
                SolidModuleRegistry.getInstance().unregisterOwner(be.trackedShipId, pos);
            }
            be.trackedShipId = shipId;
            be.registered = SolidModuleRegistry.getInstance().registerOwner(shipId, pos);
            be.duplicate = !be.registered;
            be.setChanged();
        }

        if (be.duplicate) {
            be.deactivateSolidMode();
            return;
        }

        // Kinetic input
        if (energyInputHook != null) {
            energyInputHook.tick(level, pos, be);
        }

        // Get shield instance for this ship
        ShieldInstance shield = ShieldManager.getInstance().getShield(shipId);
        if (shield == null) {
            // Shield not registered yet — can't enable solid mode
            be.deactivateSolidMode();
            return;
        }

        int cost = ShieldConfig.get().getGeneral().solidModuleEnergyCost;

        if (be.active && be.energyStored >= cost) {
            be.energyStored -= cost;
            be.setChanged();
            shield.setSolidMode(true, be.accessCode);
        } else {
            // Not enough energy or not active — disable solid mode
            if (shield.isSolidMode()) {
                shield.setSolidMode(false, "");
            }
        }
    }

    private void deactivateSolidMode() {
        if (trackedShipId != -1) {
            ShieldInstance shield = ShieldManager.getInstance().getShield(trackedShipId);
            if (shield != null && shield.isSolidMode()) {
                shield.setSolidMode(false, "");
            }
        }
    }

    public void onBroken() {
        deactivateSolidMode();
        if (trackedShipId != -1) {
            SolidModuleRegistry.getInstance().unregisterOwner(trackedShipId, worldPosition);
            trackedShipId = -1;
            registered = false;
            duplicate = false;
        }
    }

    // --- Setters called by network packets ---

    public void setActive(boolean newActive) {
        this.active = newActive;
        if (!newActive) deactivateSolidMode();
        setChanged();
    }

    public void setAccessCode(String code) {
        if (code == null) code = "";
        // Sanitize: keep only a-z A-Z 0-9, max 8 chars
        code = code.replaceAll("[^a-zA-Z0-9]", "");
        if (code.length() > 8) code = code.substring(0, 8);
        this.accessCode = code;
        setChanged();
    }

    // --- Getters ---

    public boolean isActive()        { return active; }
    public String  getAccessCode()   { return accessCode; }
    public boolean isDuplicate()     { return duplicate; }
    public long    getTrackedShipId(){ return trackedShipId; }
    public int     getMaxEnergy()    { return maxEnergy; }

    public int receiveEnergy(int amount, boolean simulate) {
        int maxReceive = ShieldConfig.get().getGeneral().solidModuleEnergyInput;
        int accepted = Math.min(Math.min(amount, maxReceive), maxEnergy - energyStored);
        if (!simulate) { energyStored += accepted; setChanged(); }
        return accepted;
    }

    public int extractEnergy(int amount, boolean simulate) {
        int extracted = Math.min(amount, energyStored);
        if (!simulate) { energyStored -= extracted; setChanged(); }
        return extracted;
    }

    // --- NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Active", active);
        tag.putString("AccessCode", accessCode);
        tag.putInt("Energy", energyStored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        active = tag.getBoolean("Active");
        accessCode = tag.getString("AccessCode");
        energyStored = Math.min(tag.getInt("Energy"),
                ShieldConfig.get().getGeneral().solidModuleMaxEnergy);
        maxEnergy = ShieldConfig.get().getGeneral().solidModuleMaxEnergy;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vs_shields.solid_projection_module");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SolidProjectionModuleMenu(containerId, playerInv, this, dataAccess);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeLong(trackedShipId);
        buf.writeUtf(accessCode, 8);
    }
}
