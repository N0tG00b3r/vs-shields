package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.block.ShieldJammerControllerBlock;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mechanicalskies.vsshields.blockentity.ShieldJammerControllerBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.network.FriendlyByteBuf;
import com.mechanicalskies.vsshields.menu.ShieldJammerMenu;
import dev.architectury.registry.menu.ExtendedMenuProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShieldJammerControllerBlockEntity extends BlockEntity implements ExtendedMenuProvider {
    public interface EnergyInputHook {
        void tick(Level level, BlockPos pos, ShieldJammerControllerBlockEntity be);
    }

    private static EnergyInputHook energyInputHook = null;

    public static void setEnergyInputHook(EnergyInputHook hook) {
        energyInputHook = hook;
    }

    private int energyStored = 0;
    private int maxEnergy = 5000000;
    private int maxReceive = 100000;
    private int frameCountCache = 0;

    private boolean isFormed = false;
    private long trackedShipId = -1;
    private boolean duplicate = false;

    private boolean isActive = false;
    private boolean isCooldown = false;
    public int forcedCooldownTicks = 0;
    private boolean isEnabled = true;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> isActive ? 1 : 0;
                case 1 -> isCooldown ? 1 : 0;
                case 2 -> energyStored & 0xFFFF;
                case 3 -> (energyStored >> 16) & 0xFFFF;
                case 4 -> maxEnergy & 0xFFFF;
                case 5 -> (maxEnergy >> 16) & 0xFFFF;
                case 6 -> forcedCooldownTicks;
                case 7 -> duplicate ? 1 : 0;
                case 8 -> frameCountCache;
                case 9 -> isEnabled ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Read-only for client
        }

        @Override
        public int getCount() {
            return 10;
        }
    };

    public ShieldJammerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.shield_jammer_CONTROLLER.get(), pos, state);
    }

    public void onLoad() {
        if (level != null && !level.isClientSide) {
            ShieldJammerTracker.addRam(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            ShieldJammerTracker.removeRam(this);
        }
    }

    public void onRemoved() {
        if (level != null && !level.isClientSide) {
            ShieldJammerTracker.removeRam(this);
        }
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide)
            return;

        scanMultiblock(level, pos, state);

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            trackedShipId = -1;
            duplicate = false;
            return; // Only works on ships
        }

        long shipId = ship.getId();

        // Check for duplicates globally
        if (trackedShipId != shipId) {
            duplicate = false;
            for (ShieldJammerControllerBlockEntity other : ShieldJammerTracker.getLoadedRams()) {
                if (other != this && other.trackedShipId == shipId && other.isFormed && !other.isRemoved()) {
                    duplicate = true;
                    break;
                }
            }
            trackedShipId = shipId;
        }

        if (duplicate || !isFormed) {
            isActive = false;
            return;
        }

        if (energyInputHook != null) {
            energyInputHook.tick(level, pos, this);
        }

        if (forcedCooldownTicks > 0) {
            forcedCooldownTicks--;
            isActive = false;
            isCooldown = true;
            if (forcedCooldownTicks == 0) {
                isCooldown = false;
                setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            return;
        }

        if (isCooldown) {
            int targetRecovery = Math.max(2500000, maxEnergy / 2);
            if (energyStored >= targetRecovery) {
                isCooldown = false;
                setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
            return;
        }

        if (!isEnabled) {
            isActive = false;
            return;
        }

        isActive = true;

        if (isActive && !isCooldown) {
            energyStored -= 5000;
            if (energyStored <= 0) {
                energyStored = 0;
                isActive = false;
                isCooldown = true;
                if (level instanceof ServerLevel) {
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,
                            net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                return;
            }

            if (ship instanceof ServerShip serverShip) {
                Vector3d localCenter = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                Vector3d worldJammerPos = serverShip.getShipToWorld().transformPosition(localCenter, new Vector3d());

                for (Ship otherShip : VSGameUtilsKt.getAllShips(level)) {
                    if (otherShip == serverShip)
                        continue;

                    long otherShipId = otherShip.getId();
                    com.mechanicalskies.vsshields.shield.ShieldInstance enemyShield = com.mechanicalskies.vsshields.shield.ShieldManager
                            .getInstance().getShield(otherShipId);

                    if (enemyShield != null && enemyShield.isActive()) {
                        BlockPos enemyShieldPos = com.mechanicalskies.vsshields.shield.ShieldManager.getInstance()
                                .getShieldOwnerPos(otherShipId);
                        if (enemyShieldPos != null) {
                            org.joml.primitives.AABBic enemyAABB = otherShip.getShipAABB();
                            if (enemyAABB != null) {
                                Vector3d jammerInEnemyLocal = otherShip.getWorldToShip()
                                        .transformPosition(new Vector3d(worldJammerPos));

                                if (jammerInEnemyLocal.x >= enemyAABB.minX() - 20.0
                                        && jammerInEnemyLocal.x <= enemyAABB.maxX() + 20.0 &&
                                        jammerInEnemyLocal.y >= enemyAABB.minY() - 20.0
                                        && jammerInEnemyLocal.y <= enemyAABB.maxY() + 20.0 &&
                                        jammerInEnemyLocal.z >= enemyAABB.minZ() - 20.0
                                        && jammerInEnemyLocal.z <= enemyAABB.maxZ() + 20.0) {

                                    if (energyStored >= 25000) {
                                        energyStored -= 25000;

                                        BlockEntity be = level.getBlockEntity(enemyShieldPos);
                                        if (be instanceof ShieldGeneratorBlockEntity generator) {
                                            generator.drainEnergyFromJammer(5000);
                                        }
                                        enemyShield.damage(3.0, level.getGameTime());

                                        if (level instanceof ServerLevel serverLevel) {
                                            Vector3d enemyLocalPos = new Vector3d(enemyShieldPos.getX() + 0.5,
                                                    enemyShieldPos.getY() + 0.5, enemyShieldPos.getZ() + 0.5);
                                            Vector3d enemyWorldPos = otherShip.getShipToWorld()
                                                    .transformPosition(enemyLocalPos, new Vector3d());

                                            // Spawn sonic boom half-way between ships
                                            Vector3d midPoint = new Vector3d(worldJammerPos).add(enemyWorldPos)
                                                    .mul(0.5);
                                            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                                                    midPoint.x, midPoint.y, midPoint.z,
                                                    1, 0, 0, 0, 0);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        setChanged();
    }

    private void scanMultiblock(Level level, BlockPos pos, BlockState state) {
        int frameCount = 0;
        int controllerCount = 0;

        Direction facing = state.getValue(ShieldJammerControllerBlock.FACING);
        Direction behind = facing.getOpposite();
        BlockPos centerPos = pos.relative(behind);

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos p = centerPos.offset(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.is(ModBlocks.shield_jammer_frame.get()) || s.is(ModBlocks.shield_jammer_INPUT.get())) {
                        frameCount++;
                    } else if (s.is(ModBlocks.shield_jammer_CONTROLLER.get())) {
                        controllerCount++;
                    }
                }
            }
        }
        this.frameCountCache = frameCount;
        this.maxEnergy = 3000000; // Fixed 30 second capacity (5000 FE/t * 20t * 30s)
        this.isFormed = (frameCount == 26 && controllerCount == 1);
    }

    public int receiveEnergy(int amount, boolean simulate) {
        if (!isFormed || duplicate || isActive)
            return 0;
        int accepted = Math.min(Math.min(amount, maxReceive), maxEnergy - energyStored);
        if (!simulate) {
            energyStored += accepted;
            setChanged();
        }
        return accepted;
    }

    public int extractEnergy(int amount, boolean simulate) {
        if (!isFormed || duplicate)
            return 0;
        int extracted = Math.min(amount, energyStored);
        if (!simulate) {
            energyStored -= extracted;
            setChanged();
        }
        return extracted;
    }

    public int getEnergyStored() {
        return energyStored;
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public boolean isFormed() {
        return isFormed;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public long getTrackedShipId() {
        return trackedShipId;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isCooldown() {
        return isCooldown;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void disable() {
        this.isEnabled = false;
        forceCooldown();
    }

    public void enable() {
        this.isEnabled = true;
        setChanged();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("IsActive", this.isActive);
        tag.putBoolean("IsCooldown", this.isCooldown);
        tag.putInt("Energy", this.energyStored);
        tag.putInt("ForcedCooldownTicks", this.forcedCooldownTicks);
        tag.putBoolean("IsEnabled", this.isEnabled);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.isActive = tag.getBoolean("IsActive");
        this.isCooldown = tag.getBoolean("IsCooldown");
        this.energyStored = tag.getInt("Energy");
        this.forcedCooldownTicks = tag.getInt("ForcedCooldownTicks");
        this.isEnabled = !tag.contains("IsEnabled") || tag.getBoolean("IsEnabled");
        this.maxEnergy = 3000000;
    }

    public void forceCooldown() {
        this.forcedCooldownTicks = 1200; // 60 seconds
        this.isActive = false;
        this.isCooldown = true;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vs_shields.shield_jammer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new ShieldJammerMenu(containerId, playerInv, this, dataAccess);
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(getBlockPos());
        buf.writeLong(trackedShipId);
    }
}
