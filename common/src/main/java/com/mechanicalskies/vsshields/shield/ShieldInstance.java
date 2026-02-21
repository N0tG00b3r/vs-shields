package com.mechanicalskies.vsshields.shield;

public class ShieldInstance {

    /**
     * Listener notified after the shield absorbs damage.
     * Used by Shield Battery to provide passive/emergency regen.
     */
    public interface DamageListener {
        void onShieldDamaged(ShieldInstance shield, double absorbed, long tick);
    }

    private final long shipId;
    private double currentHP;
    private double baseMaxHP;
    private double bonusMaxHP;
    private double baseRechargeRate;
    private double bonusRechargeRate;
    private int rechargeCooldown;
    private int depletionCooldown; // Cooldown after reaching 0 HP
    private long lastHitTick;
    private long depletionTick; // Tick when shield hit 0 HP
    private boolean active;
    private double hpScale = 1.0; // Energy-based HP scaling (0.5 to 1.0)
    private double energyPercent = 1.0; // Current energy level as 0.0–1.0
    private DamageListener damageListener;

    public ShieldInstance(long shipId, ShieldTier tier) {
        this.shipId = shipId;
        this.baseMaxHP = tier.getMaxHP();
        this.bonusMaxHP = 0;
        this.currentHP = getMaxHP();
        this.baseRechargeRate = tier.getRechargeRate();
        this.bonusRechargeRate = 0;
        this.rechargeCooldown = tier.getRechargeCooldown();
        this.depletionCooldown = tier.getDepletionCooldown();
        this.lastHitTick = 0;
        this.depletionTick = -99999;
        this.active = true;
    }

    public long getShipId() {
        return shipId;
    }

    public double getCurrentHP() {
        return currentHP;
    }

    public double getMaxHP() {
        return (baseMaxHP + bonusMaxHP) * hpScale;
    }

    public double getRechargeRate() {
        return baseRechargeRate + bonusRechargeRate;
    }

    public int getRechargeCooldown() {
        return rechargeCooldown;
    }

    public long getLastHitTick() {
        return lastHitTick;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Apply damage to the shield. Returns the amount of damage that was absorbed.
     */
    public double damage(double amount, long currentTick) {
        if (!active || currentHP <= 0)
            return 0;
        double absorbed = Math.min(currentHP, amount);
        currentHP -= absorbed;
        lastHitTick = currentTick;

        if (currentHP <= 0) {
            depletionTick = currentTick;
        }

        if (absorbed > 0 && damageListener != null) {
            damageListener.onShieldDamaged(this, absorbed, currentTick);
        }
        return absorbed;
    }

    public void restoreHP(double amount) {
        currentHP = Math.min(getMaxHP(), currentHP + amount);
    }

    public void setDamageListener(DamageListener listener) {
        this.damageListener = listener;
    }

    public DamageListener getDamageListener() {
        return damageListener;
    }

    /**
     * Tick the shield: recharge HP if cooldown has passed.
     */
    public void tick(long currentTick) {
        if (!active)
            return;
        double max = getMaxHP();
        if (currentHP >= max)
            return;

        if (currentHP <= 0) {
            if (currentTick - depletionTick >= depletionCooldown) {
                currentHP = Math.min(max, currentHP + getRechargeRate());
            }
        } else {
            if (currentTick - lastHitTick >= rechargeCooldown) {
                currentHP = Math.min(max, currentHP + getRechargeRate());
            }
        }
    }

    public void addBonusMaxHP(double amount) {
        this.bonusMaxHP += amount;
    }

    public void removeBonusMaxHP(double amount) {
        this.bonusMaxHP = Math.max(0, this.bonusMaxHP - amount);
        double max = getMaxHP();
        if (currentHP > max)
            currentHP = max;
    }

    public void addBonusRechargeRate(double amount) {
        this.bonusRechargeRate += amount;
    }

    public void removeBonusRechargeRate(double amount) {
        this.bonusRechargeRate = Math.max(0, this.bonusRechargeRate - amount);
    }

    public void setHPScale(double scale) {
        com.mechanicalskies.vsshields.config.ShieldConfig.GeneralConfig gen = com.mechanicalskies.vsshields.config.ShieldConfig
                .get().getGeneral();
        this.hpScale = Math.max(gen.hpScaleMin, Math.min(gen.hpScaleMax, scale));
        double max = getMaxHP();
        if (currentHP > max)
            currentHP = max;
    }

    public double getHPPercent() {
        double max = getMaxHP();
        return max > 0 ? currentHP / max : 0;
    }

    public double getEnergyPercent() {
        return energyPercent;
    }

    public void setEnergyPercent(double percent) {
        this.energyPercent = Math.max(0.0, Math.min(1.0, percent));
    }
}
