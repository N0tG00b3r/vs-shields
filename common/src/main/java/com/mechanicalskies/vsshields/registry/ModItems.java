package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.item.GravitationalMineItem;
import com.mechanicalskies.vsshields.item.GravitationalMineLauncherItem;
import com.mechanicalskies.vsshields.item.ShipAnalyzerItem;
import com.mechanicalskies.vsshields.item.TacticalNetheriteHelm;
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

        public static final RegistrySupplier<Item> TACTICAL_HELM = ITEMS.register(
                        "tactical_helm",
                        TacticalNetheriteHelm::new);

        public static final RegistrySupplier<Item> GRAVITATIONAL_MINE_LAUNCHER = ITEMS.register(
                        "gravitational_mine_launcher",
                        GravitationalMineLauncherItem::new);

        public static final RegistrySupplier<Item> GRAVITATIONAL_MINE_ITEM = ITEMS.register(
                        "gravitational_mine",
                        GravitationalMineItem::new);

        public static void register() {
                ITEMS.register();
        }
}
