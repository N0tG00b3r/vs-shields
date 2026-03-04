package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.item.FrequencyIDCardItem;
import com.mechanicalskies.vsshields.item.GravitationalMineItem;
import com.mechanicalskies.vsshields.item.GravitationalMineLauncherItem;
import com.mechanicalskies.vsshields.item.ShipAnalyzerItem;
import com.mechanicalskies.vsshields.item.TacticalGogglesItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ModItems {
        public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(VSShieldsMod.MOD_ID,
                        Registries.ITEM);

        public static final RegistrySupplier<Item> IRON_SHIELD_GENERATOR = ITEMS.register(
                        "iron_shield_generator",
                        () -> new BlockItem(ModBlocks.IRON_SHIELD_GENERATOR.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> DIAMOND_SHIELD_GENERATOR = ITEMS.register(
                        "diamond_shield_generator",
                        () -> new BlockItem(ModBlocks.DIAMOND_SHIELD_GENERATOR.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> NETHERITE_SHIELD_GENERATOR = ITEMS.register(
                        "netherite_shield_generator",
                        () -> new BlockItem(ModBlocks.NETHERITE_SHIELD_GENERATOR.get(),
                                        new Item.Properties().fireResistant()));

        public static final RegistrySupplier<Item> SHIELD_CAPACITOR = ITEMS.register(
                        "shield_capacitor",
                        () -> new BlockItem(ModBlocks.SHIELD_CAPACITOR.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> SHIELD_EMITTER = ITEMS.register(
                        "shield_emitter",
                        () -> new BlockItem(ModBlocks.SHIELD_EMITTER.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> SHIELD_BATTERY_CONTROLLER = ITEMS.register(
                        "shield_battery_controller",
                        () -> new BlockItem(ModBlocks.SHIELD_BATTERY_CONTROLLER.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> SHIELD_BATTERY_CELL = ITEMS.register(
                        "shield_battery_cell",
                        () -> new BlockItem(ModBlocks.SHIELD_BATTERY_CELL.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> SHIELD_BATTERY_INPUT = ITEMS.register(
                        "shield_battery_input",
                        () -> new BlockItem(ModBlocks.SHIELD_BATTERY_INPUT.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> CLOAKING_FIELD_GENERATOR = ITEMS.register(
                        "cloaking_field_generator",
                        () -> new BlockItem(ModBlocks.CLOAKING_FIELD_GENERATOR.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> GRAVITY_FIELD_GENERATOR = ITEMS.register(
                        "gravity_field_generator",
                        () -> new BlockItem(ModBlocks.GRAVITY_FIELD_GENERATOR.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> shield_jammer_CONTROLLER = ITEMS.register(
                        "shield_jammer_controller",
                        () -> new BlockItem(ModBlocks.shield_jammer_CONTROLLER.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> shield_jammer_frame = ITEMS.register(
                        "shield_jammer_frame",
                        () -> new BlockItem(ModBlocks.shield_jammer_frame.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> shield_jammer_INPUT = ITEMS.register(
                        "shield_jammer_input",
                        () -> new BlockItem(ModBlocks.shield_jammer_INPUT.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> SHIP_ANALYZER = ITEMS.register(
                        "ship_analyzer",
                        () -> new ShipAnalyzerItem(new Item.Properties().stacksTo(1)));

        public static final RegistrySupplier<Item> TACTICAL_GOGGLES = ITEMS.register(
                        "tactical_goggles",
                        TacticalGogglesItem::new);

        public static final RegistrySupplier<Item> GRAVITATIONAL_MINE_LAUNCHER = ITEMS.register(
                        "gravitational_mine_launcher",
                        GravitationalMineLauncherItem::new);

        public static final RegistrySupplier<Item> GRAVITATIONAL_MINE_ITEM = ITEMS.register(
                        "gravitational_mine",
                        GravitationalMineItem::new);

        public static final RegistrySupplier<Item> SOLID_PROJECTION_MODULE = ITEMS.register(
                        "solid_projection_module",
                        () -> new BlockItem(ModBlocks.SOLID_PROJECTION_MODULE.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> FREQUENCY_ID_CARD = ITEMS.register(
                        "frequency_id_card",
                        FrequencyIDCardItem::new);

        public static final RegistrySupplier<Item> BOARDING_POD_COCKPIT = ITEMS.register(
                        "boarding_pod_cockpit",
                        () -> new BlockItem(ModBlocks.BOARDING_POD_COCKPIT.get(), new Item.Properties()));

        public static final RegistrySupplier<Item> BOARDING_POD_ENGINE = ITEMS.register(
                        "boarding_pod_engine",
                        () -> new BlockItem(ModBlocks.BOARDING_POD_ENGINE.get(), new Item.Properties()));

        // === Crafting Components ===

        // Base tier
        public static final RegistrySupplier<Item> CHARGED_REDSTONE_CRYSTAL = ITEMS.register(
                        "charged_redstone_crystal", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> COPPER_COIL = ITEMS.register(
                        "copper_coil", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> INSULATED_WIRE = ITEMS.register(
                        "insulated_wire", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> TEMPERED_GLASS_PANE = ITEMS.register(
                        "tempered_glass_pane", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> REINFORCED_PLATE = ITEMS.register(
                        "reinforced_plate", () -> new Item(new Item.Properties()));

        // Mid tier
        public static final RegistrySupplier<Item> SIGNAL_BOARD = ITEMS.register(
                        "signal_board", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> RESONANCE_LENS = ITEMS.register(
                        "resonance_lens", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> ENERGY_CELL = ITEMS.register(
                        "energy_cell",
                        () -> new com.mechanicalskies.vsshields.item.EnergyCellItem(new Item.Properties()));
        public static final RegistrySupplier<Item> HARDENED_CASING = ITEMS.register(
                        "hardened_casing", () -> new Item(new Item.Properties().stacksTo(4)));

        // Advanced tier
        public static final RegistrySupplier<Item> STABILIZED_CORE = ITEMS.register(
                        "stabilized_core", () -> new Item(new Item.Properties().stacksTo(1)));
        public static final RegistrySupplier<Item> FREQUENCY_OSCILLATOR = ITEMS.register(
                        "frequency_oscillator", () -> new Item(new Item.Properties().stacksTo(16)));
        public static final RegistrySupplier<Item> VOID_SHARD = ITEMS.register(
                        "void_shard", () -> new Item(new Item.Properties().stacksTo(16)));
        public static final RegistrySupplier<Item> VOID_CAPACITOR = ITEMS.register(
                        "void_capacitor", () -> new Item(new Item.Properties().stacksTo(1)));

        // ===== Aetheric Anomaly — Phase 4 items =====
        public static final RegistrySupplier<Item> AETHERIC_COMPASS = ITEMS.register(
                        "aetheric_compass",
                        () -> new com.mechanicalskies.vsshields.item.AethericCompassItem(
                                new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE)));
        public static final RegistrySupplier<Item> AETHERIC_ENERGY_CELL = ITEMS.register(
                        "aetheric_energy_cell",
                        () -> new com.mechanicalskies.vsshields.item.AethericEnergyCellItem(new Item.Properties()));
        public static final RegistrySupplier<Item> ATTUNED_VOID_SHARD = ITEMS.register(
                        "attuned_void_shard",
                        () -> new Item(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE)));
        public static final RegistrySupplier<Item> CALIBRATED_OSCILLATOR = ITEMS.register(
                        "calibrated_oscillator",
                        () -> new Item(new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE)));

        public static final RegistrySupplier<Item> RESONANCE_BEACON = ITEMS.register(
                        "resonance_beacon",
                        () -> new BlockItem(ModBlocks.RESONANCE_BEACON.get(), new Item.Properties()));

        // ===== Aetheric Anomaly resource items =====
        public static final RegistrySupplier<Item> RAW_AETHER_CRYSTAL = ITEMS.register(
                        "raw_aether_crystal", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> REFINED_AETHER_CRYSTAL = ITEMS.register(
                        "refined_aether_crystal", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> VOID_ESSENCE = ITEMS.register(
                        "void_essence", () -> new Item(new Item.Properties()));
        public static final RegistrySupplier<Item> RESONANCE_FRAGMENT = ITEMS.register(
                        "resonance_fragment", () -> new Item(new Item.Properties()));

        // ===== Aetheric Anomaly block items =====
        public static final RegistrySupplier<Item> AETHERIC_STONE = ITEMS.register(
                        "aetheric_stone",
                        () -> new BlockItem(ModBlocks.AETHERIC_STONE.get(), new Item.Properties()));
        public static final RegistrySupplier<Item> AETHERIC_STONE_CRACKED = ITEMS.register(
                        "aetheric_stone_cracked",
                        () -> new BlockItem(ModBlocks.AETHERIC_STONE_CRACKED.get(), new Item.Properties()));
        public static final RegistrySupplier<Item> VOID_MOSS = ITEMS.register(
                        "void_moss",
                        () -> new BlockItem(ModBlocks.VOID_MOSS.get(), new Item.Properties()));
        public static final RegistrySupplier<Item> AETHER_CRYSTAL_ORE = ITEMS.register(
                        "aether_crystal_ore",
                        () -> new BlockItem(ModBlocks.AETHER_CRYSTAL_ORE.get(), new Item.Properties()));
        public static final RegistrySupplier<Item> RESONANCE_CLUSTER = ITEMS.register(
                        "resonance_cluster",
                        () -> new BlockItem(ModBlocks.RESONANCE_CLUSTER.get(), new Item.Properties()));
        public static final RegistrySupplier<Item> CONCENTRATED_VOID_DEPOSIT = ITEMS.register(
                        "concentrated_void_deposit",
                        () -> new BlockItem(ModBlocks.CONCENTRATED_VOID_DEPOSIT.get(), new Item.Properties()));

        public static void register() {
                ITEMS.register();
        }
}
