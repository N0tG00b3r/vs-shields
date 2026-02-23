package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.blockentity.CloakingFieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.blockentity.GravityFieldGeneratorBlockEntity;
import com.mechanicalskies.vsshields.blockentity.ShieldBatteryControllerBlockEntity;
import com.mechanicalskies.vsshields.blockentity.ShieldBatteryInputBlockEntity;
import com.mechanicalskies.vsshields.blockentity.ShieldCapacitorBlockEntity;
import com.mechanicalskies.vsshields.blockentity.ShieldEmitterBlockEntity;
import com.mechanicalskies.vsshields.blockentity.ShieldGeneratorBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
                        .create(VSShieldsMod.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<ShieldGeneratorBlockEntity>> SHIELD_GENERATOR = BLOCK_ENTITIES
                        .register("shield_generator", () -> BlockEntityType.Builder.of(
                                        ShieldGeneratorBlockEntity::new,
                                        ModBlocks.IRON_SHIELD_GENERATOR.get(),
                                        ModBlocks.DIAMOND_SHIELD_GENERATOR.get(),
                                        ModBlocks.NETHERITE_SHIELD_GENERATOR.get()).build(null));

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<ShieldCapacitorBlockEntity>> SHIELD_CAPACITOR = BLOCK_ENTITIES
                        .register("shield_capacitor", () -> BlockEntityType.Builder.of(
                                        ShieldCapacitorBlockEntity::new,
                                        ModBlocks.SHIELD_CAPACITOR.get()).build(null));

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<ShieldEmitterBlockEntity>> SHIELD_EMITTER = BLOCK_ENTITIES
                        .register("shield_emitter", () -> BlockEntityType.Builder.of(
                                        ShieldEmitterBlockEntity::new,
                                        ModBlocks.SHIELD_EMITTER.get()).build(null));

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<ShieldBatteryControllerBlockEntity>> SHIELD_BATTERY_CONTROLLER = BLOCK_ENTITIES
                        .register("shield_battery_controller", () -> BlockEntityType.Builder.of(
                                        ShieldBatteryControllerBlockEntity::new,
                                        ModBlocks.SHIELD_BATTERY_CONTROLLER.get()).build(null));

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<ShieldBatteryInputBlockEntity>> SHIELD_BATTERY_INPUT = BLOCK_ENTITIES
                        .register("shield_battery_input", () -> BlockEntityType.Builder.of(
                                        ShieldBatteryInputBlockEntity::new,
                                        ModBlocks.SHIELD_BATTERY_INPUT.get()).build(null));

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<CloakingFieldGeneratorBlockEntity>> CLOAKING_FIELD_GENERATOR = BLOCK_ENTITIES
                        .register("cloaking_field_generator", () -> BlockEntityType.Builder.of(
                                        CloakingFieldGeneratorBlockEntity::new,
                                        ModBlocks.CLOAKING_FIELD_GENERATOR.get()).build(null));

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<GravityFieldGeneratorBlockEntity>> GRAVITY_FIELD_GENERATOR = BLOCK_ENTITIES
                        .register("gravity_field_generator", () -> BlockEntityType.Builder.of(
                                        GravityFieldGeneratorBlockEntity::new,
                                        ModBlocks.GRAVITY_FIELD_GENERATOR.get()).build(null));

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<com.mechanicalskies.vsshields.blockentity.ShieldJammerControllerBlockEntity>> shield_jammer_CONTROLLER = BLOCK_ENTITIES
                        .register("shield_jammer_controller", () -> BlockEntityType.Builder.of(
                                        com.mechanicalskies.vsshields.blockentity.ShieldJammerControllerBlockEntity::new,
                                        ModBlocks.shield_jammer_CONTROLLER.get()).build(null));

        @SuppressWarnings("ConstantConditions")
        public static final RegistrySupplier<BlockEntityType<com.mechanicalskies.vsshields.blockentity.ShieldJammerInputBlockEntity>> shield_jammer_INPUT = BLOCK_ENTITIES
                        .register("shield_jammer_input", () -> BlockEntityType.Builder.of(
                                        com.mechanicalskies.vsshields.blockentity.ShieldJammerInputBlockEntity::new,
                                        ModBlocks.shield_jammer_INPUT.get()).build(null));

        public static void register() {
                BLOCK_ENTITIES.register();
        }
}
