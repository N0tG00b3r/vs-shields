package com.mechanicalskies.vsshields.shield;

import com.mechanicalskies.vsshields.config.ShieldConfig;

public enum ShieldTier {
    IRON("iron"),
    DIAMOND("diamond"),
    NETHERITE("netherite");

    private final String name;

    ShieldTier(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public int getMaxHP() {
        ShieldConfig.TierConfig tc = ShieldConfig.get().getTierConfig(name);
        return tc.maxHp;
    }

    public double getRechargeRate() {
        ShieldConfig.TierConfig tc = ShieldConfig.get().getTierConfig(name);
        return tc.rechargeRate;
    }

    public int getRechargeCooldown() {
        ShieldConfig.TierConfig tc = ShieldConfig.get().getTierConfig(name);
        return tc.rechargeCooldown;
    }

    public int getMaxEnergy() {
        ShieldConfig.TierConfig tc = ShieldConfig.get().getTierConfig(name);
        return tc.maxEnergy;
    }

    public int getEnergyPerTick() {
        ShieldConfig.TierConfig tc = ShieldConfig.get().getTierConfig(name);
        return tc.energyPerTick;
    }

    public int getDepletionCooldown() {
        ShieldConfig.TierConfig tc = ShieldConfig.get().getTierConfig(name);
        return tc.depletionCooldown;
    }
}
