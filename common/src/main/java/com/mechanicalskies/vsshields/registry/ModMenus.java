package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.menu.CloakGeneratorMenu;
import com.mechanicalskies.vsshields.menu.GravityFieldMenu;
import com.mechanicalskies.vsshields.menu.ShieldBatteryMenu;
import com.mechanicalskies.vsshields.menu.ShieldGeneratorMenu;
import com.mechanicalskies.vsshields.menu.SolidProjectionModuleMenu;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public class ModMenus {
        public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(VSShieldsMod.MOD_ID,
                        Registries.MENU);

        public static final RegistrySupplier<MenuType<ShieldGeneratorMenu>> SHIELD_GENERATOR_MENU = MENUS
                        .register("shield_generator", () -> MenuRegistry.ofExtended(ShieldGeneratorMenu::new));

        public static final RegistrySupplier<MenuType<ShieldBatteryMenu>> SHIELD_BATTERY_MENU = MENUS
                        .register("shield_battery", () -> MenuRegistry.ofExtended(ShieldBatteryMenu::new));

        public static final RegistrySupplier<MenuType<CloakGeneratorMenu>> CLOAK_GENERATOR_MENU = MENUS
                        .register("cloak_generator", () -> MenuRegistry.ofExtended(CloakGeneratorMenu::new));

        public static final RegistrySupplier<MenuType<GravityFieldMenu>> GRAVITY_FIELD_MENU = MENUS
                        .register("gravity_field", () -> MenuRegistry.ofExtended(GravityFieldMenu::new));

        public static final RegistrySupplier<MenuType<com.mechanicalskies.vsshields.menu.ShieldJammerMenu>> shield_jammer_MENU = MENUS
                        .register("shield_jammer", () -> MenuRegistry
                                        .ofExtended(com.mechanicalskies.vsshields.menu.ShieldJammerMenu::new));

        public static final RegistrySupplier<MenuType<SolidProjectionModuleMenu>> SOLID_PROJECTION_MODULE_MENU = MENUS
                        .register("solid_projection_module",
                                        () -> MenuRegistry.ofExtended(SolidProjectionModuleMenu::new));

        public static final RegistrySupplier<MenuType<com.mechanicalskies.vsshields.menu.ResonanceBeaconMenu>> RESONANCE_BEACON_MENU = MENUS
                        .register("resonance_beacon",
                                        () -> MenuRegistry.ofExtended(com.mechanicalskies.vsshields.menu.ResonanceBeaconMenu::new));

        public static void register() {
                MENUS.register();
        }
}
