package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(VSShieldsMod.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> IRON_SHIELD_GENERATOR = ITEMS.register(
            "iron_shield_generator",
            () -> new BlockItem(ModBlocks.IRON_SHIELD_GENERATOR.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> DIAMOND_SHIELD_GENERATOR = ITEMS.register(
            "diamond_shield_generator",
            () -> new BlockItem(ModBlocks.DIAMOND_SHIELD_GENERATOR.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> NETHERITE_SHIELD_GENERATOR = ITEMS.register(
            "netherite_shield_generator",
            () -> new BlockItem(ModBlocks.NETHERITE_SHIELD_GENERATOR.get(), new Item.Properties().fireResistant())
    );

    public static final RegistrySupplier<Item> SHIELD_CAPACITOR = ITEMS.register(
            "shield_capacitor",
            () -> new BlockItem(ModBlocks.SHIELD_CAPACITOR.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> SHIELD_EMITTER = ITEMS.register(
            "shield_emitter",
            () -> new BlockItem(ModBlocks.SHIELD_EMITTER.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> SHIELD_BATTERY_CONTROLLER = ITEMS.register(
            "shield_battery_controller",
            () -> new BlockItem(ModBlocks.SHIELD_BATTERY_CONTROLLER.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> SHIELD_BATTERY_CELL = ITEMS.register(
            "shield_battery_cell",
            () -> new BlockItem(ModBlocks.SHIELD_BATTERY_CELL.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> SHIELD_BATTERY_INPUT = ITEMS.register(
            "shield_battery_input",
            () -> new BlockItem(ModBlocks.SHIELD_BATTERY_INPUT.get(), new Item.Properties())
    );

    public static final RegistrySupplier<Item> CLOAKING_FIELD_GENERATOR = ITEMS.register(
            "cloaking_field_generator",
            () -> new BlockItem(ModBlocks.CLOAKING_FIELD_GENERATOR.get(), new Item.Properties())
    );

    public static void register() {
        ITEMS.register();
    }
}
