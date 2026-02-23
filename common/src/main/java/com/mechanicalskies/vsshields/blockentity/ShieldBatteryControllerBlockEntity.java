package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.menu.ShieldBatteryMenu;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModBlocks;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Shield Battery Controller — center of one face of a 3x3x3 multiblock.
 */
public class ShieldBatteryControllerBlockEntity extends BlockEntity
        implements ExtendedMenuProvider, ShieldInstance.DamageListener {

    public static final int STATUS_INCOMPLETE = 0;
    public static final int STATUS_NO_SHIELD = 1;
    public static final int STATUS_READY = 2;
    public static final int STATUS_DEPLETED = 3;

    private long trackedShipId = -1;
    private int energyStored = 0;
    private int maxEnergy = 200000;
    private int cellCount = 0;
    private int storageCellCount = 0;
    private boolean formed = false;
    private boolean duplicate = false;
    private long lastEmergencyTick = -99999;
    private int status = STATUS_INCOMPLETE;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> cellCount;
                case 1 -> energyStored & 0xFFFF;
                case 2 -> (energyStored >> 16) & 0xFFFF;
                case 3 -> maxEnergy & 0xFFFF;
                case 4 -> (maxEnergy >> 16) & 0xFFFF;
                case 5 -> status;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public ShieldBatteryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIELD_BATTERY_CONTROLLER.get(), pos, state);
    }

    public void onLoad() {
        if (level != null && !level.isClientSide()) {
            ShieldBatteryTracker.addBattery(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            ShieldBatteryTracker.removeBattery(this);
        }
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide())
            return;

        scanCells(level, pos, state);
        formed = (cellCount == 26);

        if (!formed) {
            maxEnergy = 0;
            unregisterListener();
            status = STATUS_INCOMPLETE;
            return;
        }

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            unregisterListener();
            status = STATUS_NO_SHIELD;
            return;
        }

        long shipId = ship.getId();

        // Check for duplicates globally
        if (trackedShipId != shipId) {
            unregisterListener();
            duplicate = false;
            for (ShieldBatteryControllerBlockEntity other : ShieldBatteryTracker.getLoadedBatteries()) {
                if (other != this && other.trackedShipId == shipId && other.isFormed() && !other.isRemoved()) {
                    duplicate = true;
                    break;
                }
            }
            trackedShipId = shipId;
        }

        if (duplicate || !formed) {
            status = STATUS_INCOMPLETE; // or another duplicate status
            return;
        }

        ShieldInstance shield = ShieldManager.getInstance().getShield(shipId);
        if (shield == null) {
            status = STATUS_NO_SHIELD;
            return;
        }

        if (shield.getDamageListener() != this) {
            shield.setDamageListener(this);
        }

        if (energyStored <= 0) {
            status = STATUS_DEPLETED;
        } else {
            status = STATUS_READY;
        }

        // Emergency regen: fires once when HP drops below 20%, dumps energy into HP
        if (shield.isActive() && shield.getHPPercent() < EMERGENCY_HP_THRESHOLD && energyStored > 0) {
            long currentTick = level.getGameTime();
            if (currentTick - lastEmergencyTick >= EMERGENCY_COOLDOWN_TICKS) {
                double maxRestoreByEnergy = energyStored / (double) FE_PER_HP_EMERGENCY;
                double hpDeficit = shield.getMaxHP() - shield.getCurrentHP();
                double restoreAmount = Math.min(maxRestoreByEnergy, hpDeficit);

                if (restoreAmount >= 1.0) {
                    int feCost = (int) Math.ceil(restoreAmount * FE_PER_HP_EMERGENCY);
                    energyStored -= Math.min(feCost, energyStored);
                    shield.restoreHP(restoreAmount);
                    lastEmergencyTick = currentTick;
                    setChanged();

                    AABBdc aabb = ship.getWorldAABB();
                    double cx = (aabb.minX() + aabb.maxX()) / 2.0;
                    double cy = (aabb.minY() + aabb.maxY()) / 2.0;
                    double cz = (aabb.minZ() + aabb.maxZ()) / 2.0;
                    ModNetwork.sendShieldRegen(((ServerLevel) level).getServer(), cx, cy, cz);
                }
            }
        }
    }

    private void scanCells(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Direction behind = facing.getOpposite();
        Direction right = facing.getClockWise();

        int total = 0;
        int storage = 0;
        for (int depth = 0; depth < 3; depth++) {
            for (int side = -1; side <= 1; side++) {
                for (int up = -1; up <= 1; up++) {
                    if (depth == 0 && side == 0 && up == 0)
                        continue;

                    BlockPos checkPos = pos
                            .relative(behind, depth)
                            .relative(right, side)
                            .relative(Direction.UP, up);

                    BlockState checkState = level.getBlockState(checkPos);
                    if (checkState.is(ModBlocks.SHIELD_BATTERY_CELL.get())) {
                        total++;
                        storage++;
                    } else if (checkState.is(ModBlocks.SHIELD_BATTERY_INPUT.get())) {
                        total++;
                    }
                }
            }
        }
        this.cellCount = total;
        this.storageCellCount = storage;
    }

    // --- Passive absorption (on every hit, no sound) ---
    // Restores 20% of absorbed damage. Costs 1500 FE per HP — "large energy" for
    // constant protection.
    private static final double PASSIVE_ABSORB_RATIO = 0.20;
    private static final int FE_PER_HP_PASSIVE = 1500;

    // --- Emergency regen (triggered in serverTick when HP < 20%, with sound) ---
    // Dumps all available energy into HP at once. 250 FE per HP — cheaper per unit
    // but fires as a burst.
    // 30-second cooldown prevents re-triggering while HP stays low.
    private static final double EMERGENCY_HP_THRESHOLD = 0.20;
    private static final int FE_PER_HP_EMERGENCY = 250;
    private static final int EMERGENCY_COOLDOWN_TICKS = 600; // 30 seconds

    /**
     * Called every time the shield absorbs damage.
     * Silently restores 20% of that damage by consuming 1500 FE/HP.
     * Only activates if current HP > 1% (prevents stalling destruction).
     * No sound — sound only plays during emergency regen (see serverTick).
     */
    @Override
    public void onShieldDamaged(ShieldInstance shield, double absorbed, long tick) {
        if (!formed || energyStored <= 0 || level == null || level.isClientSide())
            return;

        // If shield is practically broken (< 1%), don't absorb to allow shattering.
        if (shield.getHPPercent() <= 0.01)
            return;

        double restoreAmount = absorbed * PASSIVE_ABSORB_RATIO;
        int feCost = (int) Math.ceil(restoreAmount * FE_PER_HP_PASSIVE);
        if (energyStored < feCost)
            return;

        energyStored -= feCost;
        shield.restoreHP(restoreAmount);
        setChanged();
        // No sound: passive absorption is silent
    }

    public int getEnergyStored() {
        return energyStored;
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public boolean isFormed() {
        return formed;
    }

    public int receiveEnergy(int amount, boolean simulate) {
        if (!formed)
            return 0;
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

    public void onRemoved() {
        unregisterListener();
    }

    private void unregisterListener() {
        if (trackedShipId != -1) {
            ShieldInstance shield = ShieldManager.getInstance().getShield(trackedShipId);
            if (shield != null && shield.getDamageListener() == this) {
                shield.setDamageListener(null);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energyStored);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energyStored = tag.getInt("Energy");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vs_shields.shield_battery");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ShieldBatteryMenu(containerId, playerInv, this, dataAccess);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(getBlockPos());
        buf.writeLong(trackedShipId);
    }
}
