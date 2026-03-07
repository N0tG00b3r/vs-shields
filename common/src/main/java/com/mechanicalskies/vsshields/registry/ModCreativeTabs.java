package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(VSShieldsMod.MOD_ID,
            Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> VS_SHIELDS_TAB = TABS.register(
            "vs_shields_tab",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .icon(() -> new ItemStack(ModItems.DIAMOND_SHIELD_GENERATOR.get()))
                    .title(Component.translatable("itemGroup.vs_shields"))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.IRON_SHIELD_GENERATOR.get());
                        output.accept(ModItems.DIAMOND_SHIELD_GENERATOR.get());
                        output.accept(ModItems.NETHERITE_SHIELD_GENERATOR.get());
                        output.accept(ModItems.SHIELD_CAPACITOR.get());
                        output.accept(ModItems.SHIELD_EMITTER.get());
                        output.accept(ModItems.SHIELD_BATTERY_CONTROLLER.get());
                        output.accept(ModItems.SHIELD_BATTERY_CELL.get());
                        output.accept(ModItems.SHIELD_BATTERY_INPUT.get());
                        output.accept(ModItems.shield_jammer_CONTROLLER.get());
                        output.accept(ModItems.shield_jammer_frame.get());
                        output.accept(ModItems.shield_jammer_INPUT.get());
                        output.accept(ModItems.GRAVITY_FIELD_GENERATOR.get());
                        output.accept(ModItems.SHIP_ANALYZER.get());
                        output.accept(ModItems.TACTICAL_GOGGLES.get());
                        output.accept(ModItems.GRAVITATIONAL_MINE_LAUNCHER.get());
                        output.accept(ModItems.GRAVITATIONAL_MINE_ITEM.get());
                        output.accept(ModItems.SOLID_PROJECTION_MODULE.get());
                        output.accept(ModItems.FREQUENCY_ID_CARD.get());
                        output.accept(ModItems.BOARDING_POD_COCKPIT.get());
                        output.accept(ModItems.BOARDING_POD_ENGINE.get());
                        output.accept(ModItems.CLOAKING_FIELD_GENERATOR.get());
                        // Crafting Components
                        output.accept(ModItems.CHARGED_REDSTONE_CRYSTAL.get());
                        output.accept(ModItems.COPPER_COIL.get());
                        output.accept(ModItems.INSULATED_WIRE.get());
                        output.accept(ModItems.TEMPERED_GLASS_PANE.get());
                        output.accept(ModItems.REINFORCED_PLATE.get());
                        output.accept(ModItems.SIGNAL_BOARD.get());
                        output.accept(ModItems.RESONANCE_LENS.get());
                        output.accept(ModItems.ENERGY_CELL.get());
                        output.accept(ModItems.HARDENED_CASING.get());
                        output.accept(ModItems.STABILIZED_CORE.get());
                        output.accept(ModItems.FREQUENCY_OSCILLATOR.get());
                        output.accept(ModItems.VOID_SHARD.get());
                        output.accept(ModItems.VOID_CAPACITOR.get());
                        // Aetheric Anomaly Phase 4
                        output.accept(ModItems.AETHERIC_COMPASS.get());
                        output.accept(ModItems.RESONANCE_BEACON.get());
                        output.accept(ModItems.AETHERIC_ENERGY_CELL.get());
                        output.accept(ModItems.ATTUNED_VOID_SHARD.get());
                        output.accept(ModItems.CALIBRATED_OSCILLATOR.get());
                        // Aetheric Anomaly resources
                        output.accept(ModItems.RAW_AETHER_CRYSTAL.get());
                        output.accept(ModItems.REFINED_AETHER_CRYSTAL.get());
                        output.accept(ModItems.VOID_ESSENCE.get());
                        output.accept(ModItems.RESONANCE_FRAGMENT.get());
                        // Aetheric Anomaly blocks
                        output.accept(ModItems.AETHERIC_STONE.get());
                        output.accept(ModItems.AETHERIC_STONE_CRACKED.get());
                        output.accept(ModItems.VOID_MOSS.get());
                        output.accept(ModItems.AETHER_CRYSTAL_ORE.get());
                        output.accept(ModItems.RESONANCE_CLUSTER.get());
                        output.accept(ModItems.CONCENTRATED_VOID_DEPOSIT.get());
                    })
                    .build());

    public static void register() {
        TABS.register();
    }
}
