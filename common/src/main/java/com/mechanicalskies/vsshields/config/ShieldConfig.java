package com.mechanicalskies.vsshields.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
    private CgsConfig cgs;

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
        public double capacitorMaxHp = 100.0;
        public double emitterRechargeRate = 1.0;
        public int emitterRegenCost = 50;
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
        /** CBC Autocannon rounds — small calibre, rapid fire. */
        public double cbcAutocannon = 8.0;
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
        public double gravMineForceMagnitude = 10_000.0;
        public int solidModuleEnergyCost   = 500;
        public int solidModuleMaxEnergy    = 1_000_000;
        public int solidModuleEnergyInput  = 50_000;
        public double shipRepulsionForce   = 100_000.0;
        /** Set to false to hide the shield bubble entirely (client-side). */
        public boolean showShieldBubble       = true;
        /** Set to true to hide the bubble for crew standing inside it (back-face culling only). */
        public boolean hideShieldBubbleInside = false;
        /** Chance (0.0–1.0) for an Enderman to drop 1 Void Shard on death. */
        public float   voidShardEndermanChance = 0.05f;
        /** Minimum Void Shards dropped by the Ender Dragon. */
        public int     voidShardDragonMin      = 4;
        /** Maximum Void Shards dropped by the Ender Dragon. */
        public int     voidShardDragonMax      = 8;
        /** FE added to a Shield Generator when one Energy Cell is used on it. */
        public int     energyCellFE            = 25_000;
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

    /**
     * Per-weapon shield damage for Create Gunsmithing hitscan weapons.
     * Projectile entities (nails, rockets, etc.) are handled via the projectiles/
     * projectileClassPatterns maps in DamageConfig.
     */
    public static class CgsConfig {
        /** Shield damage per Gatling bullet (fires ~4/sec — keep low). */
        public double gatlingBullet = 4.0;
        /** Shield damage per Revolver shot. */
        public double revolverShot = 8.0;
        /** Shield damage for Flintlock ball round. */
        public double flintlockBall = 15.0;
        /** Shield damage per Shotgun trigger pull (all pellets combined). */
        public double shotgunBurst = 16.0;
        /** Set false to disable hitscan interception entirely. */
        public boolean enableHitscan = true;
    }

    private ShieldConfig() {
    }

    private static ShieldConfig createDefault() {
        ShieldConfig cfg = new ShieldConfig();
        cfg.tiers = new LinkedHashMap<>();
        cfg.tiers.put("iron", new TierConfig(200, 1.0, 100, 50000, 20, 200)); // 10s
        cfg.tiers.put("diamond", new TierConfig(500, 2.0, 60, 200000, 50, 140)); // 7s
        cfg.tiers.put("netherite", new TierConfig(1000, 4.0, 40, 500000, 100, 100)); // 5s

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
        cfg.damage.projectiles.put("cbc_nukes:nuke_shell", 500.0);
        cfg.damage.projectiles.put("alexscaves_torpedoes:torpedo_missile", 80.0);

        // Create Big Cannons — full shell roster (path-based fallback handles most,
        // but explicit entries override for shells that don't match simple patterns)
        cfg.damage.projectiles.put("createbigcannons:shrapnel_shell", 55.0); // fragmentation = HE-level
        cfg.damage.projectiles.put("createbigcannons:fluid_shell", 55.0); // fluid payload = HE-level
        cfg.damage.projectiles.put("createbigcannons:bag_of_grapeshot", 30.0); // scatter shot, lower per-hit
        cfg.damage.projectiles.put("createbigcannons:drop_mortar_shell", 40.0); // plunging fire mortar
        cfg.damage.projectiles.put("createbigcannons:mortar_stone", 20.0); // primitive stone mortar
        cfg.damage.projectiles.put("createbigcannons:smoke_shell", 10.0); // minimal kinetic, non-HE
        cfg.damage.projectiles.put("createbigcannons:machine_gun_bullet", cfg.damage.cbcAutocannon); // small-calibre MG

        cfg.damage.projectileClassPatterns.put("rocket", 40.0);
        cfg.damage.projectileClassPatterns.put("missile", 40.0);
        cfg.damage.projectileClassPatterns.put("spear", 20.0);
        cfg.damage.projectileClassPatterns.put("fireball", 15.0);
        cfg.damage.projectileClassPatterns.put("incendiary", 15.0);
        cfg.damage.projectileClassPatterns.put("nail", 10.0);

        // Create Gunsmithing projectile entities — explicit registry-name overrides
        // (lower than patterns since actual entity dmg should scale with realism)
        cfg.damage.projectiles.put("cgs:nail", 6.0);
        cfg.damage.projectiles.put("cgs:nail_steel", 8.0);
        cfg.damage.projectiles.put("cgs:rocket", 40.0);
        cfg.damage.projectiles.put("cgs:spear", 20.0);
        cfg.damage.projectiles.put("cgs:blaze_ball", 8.0);
        cfg.damage.projectiles.put("cgs:incendiary", 12.0);

        cfg.general = new GeneralConfig();
        cfg.cloak = new CloakConfig();
        cfg.cgs = new CgsConfig();
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

            JsonObject rawJson = JsonParser.parseString(json).getAsJsonObject();
            boolean needsRewrite = merge(loaded, defaults, rawJson);
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

    private static boolean merge(ShieldConfig loaded, ShieldConfig defaults, JsonObject rawJson) {
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
        } else {
            // GSON sets missing boolean primitives to false (not the declared field default),
            // so we must inspect the raw JSON to know which keys were truly absent.
            JsonObject rawGeneral = rawJson.has("general")
                    ? rawJson.getAsJsonObject("general") : new JsonObject();
            if (!rawGeneral.has("showShieldBubble")) {
                loaded.general.showShieldBubble = defaults.general.showShieldBubble;
                changed = true;
            }
            if (!rawGeneral.has("hideShieldBubbleInside")) {
                loaded.general.hideShieldBubbleInside = defaults.general.hideShieldBubbleInside;
                changed = true;
            }
            // Backfill numeric fields that default to 0 when absent (GSON primitive default)
            if (!rawGeneral.has("gravMineForceMagnitude")) {
                loaded.general.gravMineForceMagnitude = defaults.general.gravMineForceMagnitude;
                changed = true;
            }
            if (!rawGeneral.has("solidModuleEnergyCost")) {
                loaded.general.solidModuleEnergyCost = defaults.general.solidModuleEnergyCost;
                changed = true;
            }
            if (!rawGeneral.has("solidModuleMaxEnergy")) {
                loaded.general.solidModuleMaxEnergy = defaults.general.solidModuleMaxEnergy;
                changed = true;
            }
            if (!rawGeneral.has("solidModuleEnergyInput")) {
                loaded.general.solidModuleEnergyInput = defaults.general.solidModuleEnergyInput;
                changed = true;
            }
            if (!rawGeneral.has("shipRepulsionForce")) {
                loaded.general.shipRepulsionForce = defaults.general.shipRepulsionForce;
                changed = true;
            }
            if (!rawGeneral.has("voidShardEndermanChance")) {
                loaded.general.voidShardEndermanChance = defaults.general.voidShardEndermanChance;
                changed = true;
            }
            if (!rawGeneral.has("voidShardDragonMin")) {
                loaded.general.voidShardDragonMin = defaults.general.voidShardDragonMin;
                changed = true;
            }
            if (!rawGeneral.has("voidShardDragonMax")) {
                loaded.general.voidShardDragonMax = defaults.general.voidShardDragonMax;
                changed = true;
            }
            if (!rawGeneral.has("energyCellFE")) {
                loaded.general.energyCellFE = defaults.general.energyCellFE;
                changed = true;
            }
        }
        if (loaded.cloak == null) {
            loaded.cloak = defaults.cloak;
            changed = true;
        }
        if (loaded.cgs == null) {
            loaded.cgs = defaults.cgs;
            changed = true;
        }
        // Backfill any new projectile entries added in newer versions
        // (merge() only checks top-level fields, not individual map entries)
        if (loaded.damage != null && defaults.damage != null) {
            if (loaded.damage.projectiles == null) {
                loaded.damage.projectiles = defaults.damage.projectiles;
                changed = true;
            } else {
                for (Map.Entry<String, Double> entry : defaults.damage.projectiles.entrySet()) {
                    if (!loaded.damage.projectiles.containsKey(entry.getKey())) {
                        loaded.damage.projectiles.put(entry.getKey(), entry.getValue());
                        changed = true;
                    }
                }
            }
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

    public CgsConfig getCgs() {
        return cgs != null ? cgs : new CgsConfig();
    }
}
