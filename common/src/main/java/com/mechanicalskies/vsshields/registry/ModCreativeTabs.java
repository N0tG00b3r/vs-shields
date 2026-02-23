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
                        // output.accept(ModItems.CLOAKING_FIELD_GENERATOR.get()); // WIP - Temporarily
                        // Disabled
                    })
                    .build());

    public static void register() {
        TABS.register();
    }
}
