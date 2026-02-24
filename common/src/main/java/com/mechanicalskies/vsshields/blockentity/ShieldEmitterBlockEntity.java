package com.mechanicalskies.vsshields.blockentity;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.registry.ModBlockEntities;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public class ShieldEmitterBlockEntity extends BlockEntity {
    private static double getBonusRechargeRate() {
        return ShieldConfig.get().getBonuses().emitterRechargeRate;
    }

    private long trackedShipId = -1;
    private boolean registered = false;
    private ShieldInstance registeredShield = null;

    public ShieldEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHIELD_EMITTER.get(), pos, state);
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide())
            return;

        Ship ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        if (ship == null) {
            if (registered) {
                removeBonus();
            }
            trackedShipId = -1;
            return;
        }

        long shipId = ship.getId();

        if (trackedShipId != shipId && registered) {
            removeBonus();
        }
        trackedShipId = shipId;

        ShieldInstance currentShield = ShieldManager.getInstance().getShield(shipId);

        if (registered && currentShield != registeredShield) {
            if (registeredShield != null) {
                registeredShield.removeBonusRechargeRate(getBonusRechargeRate());
                registeredShield.removeEmitter();
            }
            registered = false;
            registeredShield = null;
        }

        if (!registered) {
            if (currentShield != null) {
                currentShield.addBonusRechargeRate(getBonusRechargeRate());
                currentShield.addEmitter();
                registered = true;
                registeredShield = currentShield;
            }
        }
    }

    public void onRemoved() {
        if (registered) {
            removeBonus();
        }
    }

    private void removeBonus() {
        if (registeredShield != null) {
            registeredShield.removeBonusRechargeRate(getBonusRechargeRate());
            registeredShield.removeEmitter();
        } else if (trackedShipId != -1) {
            ShieldInstance shield = ShieldManager.getInstance().getShield(trackedShipId);
            if (shield != null) {
                shield.removeBonusRechargeRate(getBonusRechargeRate());
                shield.removeEmitter();
            }
        }
        registered = false;
        registeredShield = null;
    }
}
