package com.mechanicalskies.vsshields.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ShieldConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("vs_shields");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ShieldConfig INSTANCE = null;

    // === Config data ===
    private Map<String, TierConfig> tiers;
    private BonusConfig bonuses;
    private DamageConfig damage;
    private GeneralConfig general;
    private CloakConfig cloak;

    public static class TierConfig {
        public int maxHp;
        public double rechargeRate;
        public int rechargeCooldown;
        public int maxEnergy;
        public int energyPerTick;
        public int depletionCooldown;

        public TierConfig() {
        }

        public TierConfig(int maxHp, double rechargeRate, int rechargeCooldown, int maxEnergy, int energyPerTick,
                int depletionCooldown) {
            this.maxHp = maxHp;
            this.rechargeRate = rechargeRate;
            this.rechargeCooldown = rechargeCooldown;
            this.maxEnergy = maxEnergy;
            this.energyPerTick = energyPerTick;
            this.depletionCooldown = depletionCooldown;
        }
    }

    public static class BonusConfig {
        public double capacitorMaxHp = 50.0;
        public double emitterRechargeRate = 0.5;
    }

    public static class DamageConfig {
        public double explosionPowerFactor = 5.5;
        public Map<String, Double> projectiles;
        public Map<String, Double> projectileClassPatterns;
        public double moddedProjectileDefault = 10.0;
        public double unknownProjectileDefault = 3.0;
        public double cbcSolidShot = 50.0;
        public double cbcHE = 60.0;
        public double cbcAP = 80.0;
        public double alexsCavesNukeDamage = 500.0;

        public DamageConfig() {
            projectiles = new LinkedHashMap<>();
            projectileClassPatterns = new LinkedHashMap<>();
        }
    }

    public static class GeneralConfig {
        public double hpScaleMin = 0.5;
        public double hpScaleMax = 1.0;
        public int syncIntervalTicks = 10;
        public double shieldPadding = 10.0;
    }

    public static class BatteryConfig {
        public int totalFE = 500000;
        public double passiveRatio = 0.2;
        public double hpPerFE = 0.005;
        public double emergencyThreshold = 0.25;
        public int emergencyCooldownTicks = 600;
    }

    public static class CloakConfig {
        public int maxEnergy = 100000;
        public int energyPerTick = 30;
    }

    private ShieldConfig() {
    }

    private static ShieldConfig createDefault() {
        ShieldConfig cfg = new ShieldConfig();
        cfg.tiers = new LinkedHashMap<>();
        cfg.tiers.put("iron", new TierConfig(100, 0.5, 100, 50000, 20, 200)); // 10s
        cfg.tiers.put("diamond", new TierConfig(250, 1.0, 60, 200000, 50, 140)); // 7s
        cfg.tiers.put("netherite", new TierConfig(500, 2.0, 40, 500000, 100, 100)); // 5s

        cfg.bonuses = new BonusConfig();

        cfg.damage = new DamageConfig();
        cfg.damage.projectiles.put("minecraft:snowball", 1.0);
        cfg.damage.projectiles.put("minecraft:egg", 1.0);
        cfg.damage.projectiles.put("minecraft:ender_pearl", 1.0);
        cfg.damage.projectiles.put("minecraft:arrow", 5.0);
        cfg.damage.projectiles.put("minecraft:spectral_arrow", 6.0);
        cfg.damage.projectiles.put("minecraft:shulker_bullet", 8.0);
        cfg.damage.projectiles.put("minecraft:small_fireball", 8.0);
        cfg.damage.projectiles.put("minecraft:trident", 15.0);
        cfg.damage.projectiles.put("minecraft:firework_rocket", 20.0);
        cfg.damage.projectiles.put("minecraft:wither_skull", 25.0);
        cfg.damage.projectiles.put("minecraft:large_fireball", 30.0);
        cfg.damage.projectiles.put("minecraft:dragon_fireball", 40.0);
        cfg.damage.projectiles.put("cbc_nukes:nuke_shell", 200.0);
        cfg.damage.projectiles.put("alexscaves_torpedoes:torpedo_missile", 80.0);

        cfg.damage.projectileClassPatterns.put("rocket", 40.0);
        cfg.damage.projectileClassPatterns.put("missile", 40.0);
        cfg.damage.projectileClassPatterns.put("spear", 20.0);
        cfg.damage.projectileClassPatterns.put("fireball", 15.0);
        cfg.damage.projectileClassPatterns.put("incendiary", 15.0);
        cfg.damage.projectileClassPatterns.put("nail", 10.0);

        cfg.general = new GeneralConfig();
        cfg.cloak = new CloakConfig();
        return cfg;
    }

    public static void load(Path gameDir) {
        Path configDir = gameDir.resolve("config");
        Path configFile = configDir.resolve("vs_shields.json");

        ShieldConfig defaults = createDefault();

        if (!Files.exists(configFile)) {
            try {
                Files.createDirectories(configDir);
                Files.writeString(configFile, GSON.toJson(defaults));
                LOGGER.info("Created default config at {}", configFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to write default config: {}", e.getMessage());
            }
            INSTANCE = defaults;
            return;
        }

        try {
            String json = Files.readString(configFile);
            ShieldConfig loaded = GSON.fromJson(json, ShieldConfig.class);

            if (loaded == null) {
                LOGGER.warn("Config file was empty, using defaults");
                INSTANCE = defaults;
                return;
            }

            boolean needsRewrite = merge(loaded, defaults);
            INSTANCE = loaded;

            if (needsRewrite) {
                try {
                    Files.writeString(configFile, GSON.toJson(loaded));
                    LOGGER.info("Updated config with new default fields");
                } catch (IOException e) {
                    LOGGER.warn("Failed to rewrite config: {}", e.getMessage());
                }
            }

            LOGGER.info("Loaded VS Shields config from {}", configFile);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse config, using defaults: {}", e.getMessage());
            INSTANCE = defaults;
        }
    }

    private static boolean merge(ShieldConfig loaded, ShieldConfig defaults) {
        boolean changed = false;
        if (loaded.tiers == null) {
            loaded.tiers = defaults.tiers;
            changed = true;
        }
        if (loaded.bonuses == null) {
            loaded.bonuses = defaults.bonuses;
            changed = true;
        }
        if (loaded.damage == null) {
            loaded.damage = defaults.damage;
            changed = true;
        }
        if (loaded.general == null) {
            loaded.general = defaults.general;
            changed = true;
        }
        if (loaded.cloak == null) {
            loaded.cloak = defaults.cloak;
            changed = true;
        }
        return changed;
    }

    public static ShieldConfig get() {
        if (INSTANCE == null) {
            INSTANCE = createDefault();
        }
        return INSTANCE;
    }

    public TierConfig getTierConfig(String tierName) {
        TierConfig tc = tiers.get(tierName);
        return tc != null ? tc : tiers.getOrDefault("iron", new TierConfig(100, 0.5, 100, 50000, 20, 200));
    }

    public BonusConfig getBonuses() {
        return bonuses;
    }

    public DamageConfig getDamage() {
        return damage;
    }

    public GeneralConfig getGeneral() {
        return general;
    }

    public CloakConfig getCloak() {
        return cloak != null ? cloak : new CloakConfig();
    }
}
