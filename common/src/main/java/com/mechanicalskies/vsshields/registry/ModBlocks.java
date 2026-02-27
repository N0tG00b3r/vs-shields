package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.block.CloakingFieldGeneratorBlock;
import com.mechanicalskies.vsshields.block.GravityFieldGeneratorBlock;
import com.mechanicalskies.vsshields.block.ShieldBatteryCellBlock;
import com.mechanicalskies.vsshields.block.ShieldBatteryControllerBlock;
import com.mechanicalskies.vsshields.block.ShieldBatteryInputBlock;
import com.mechanicalskies.vsshields.block.ShieldCapacitorBlock;
import com.mechanicalskies.vsshields.block.ShieldEmitterBlock;
import com.mechanicalskies.vsshields.block.ShieldGeneratorBlock;
import com.mechanicalskies.vsshields.block.SolidProjectionModuleBlock;
import com.mechanicalskies.vsshields.shield.ShieldTier;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
        public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(VSShieldsMod.MOD_ID,
                        Registries.BLOCK);

        public static final RegistrySupplier<Block> IRON_SHIELD_GENERATOR = BLOCKS.register(
                        "iron_shield_generator",
                        () -> new ShieldGeneratorBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(3.0f, 6.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion(),
                                        ShieldTier.IRON));

        public static final RegistrySupplier<Block> DIAMOND_SHIELD_GENERATOR = BLOCKS.register(
                        "diamond_shield_generator",
                        () -> new ShieldGeneratorBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(5.0f, 8.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion(),
                                        ShieldTier.DIAMOND));

        public static final RegistrySupplier<Block> NETHERITE_SHIELD_GENERATOR = BLOCKS.register(
                        "netherite_shield_generator",
                        () -> new ShieldGeneratorBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(8.0f, 12.0f)
                                                        .sound(SoundType.NETHERITE_BLOCK)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion(),
                                        ShieldTier.NETHERITE));

        public static final RegistrySupplier<Block> SHIELD_CAPACITOR = BLOCKS.register(
                        "shield_capacitor",
                        () -> new ShieldCapacitorBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(3.0f, 6.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> SHIELD_EMITTER = BLOCKS.register(
                        "shield_emitter",
                        () -> new ShieldEmitterBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(3.0f, 6.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> SHIELD_BATTERY_CONTROLLER = BLOCKS.register(
                        "shield_battery_controller",
                        () -> new ShieldBatteryControllerBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(4.0f, 8.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> SHIELD_BATTERY_CELL = BLOCKS.register(
                        "shield_battery_cell",
                        () -> new ShieldBatteryCellBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(3.0f, 6.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> SHIELD_BATTERY_INPUT = BLOCKS.register(
                        "shield_battery_input",
                        () -> new ShieldBatteryInputBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(3.0f, 6.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> CLOAKING_FIELD_GENERATOR = BLOCKS.register(
                        "cloaking_field_generator",
                        () -> new CloakingFieldGeneratorBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(4.0f, 8.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()));

        public static final RegistrySupplier<Block> GRAVITY_FIELD_GENERATOR = BLOCKS.register(
                        "gravity_field_generator",
                        () -> new GravityFieldGeneratorBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(5.0f, 10.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> shield_jammer_CONTROLLER = BLOCKS.register(
                        "shield_jammer_controller",
                        () -> new com.mechanicalskies.vsshields.block.ShieldJammerControllerBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(5.0f, 10.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> shield_jammer_INPUT = BLOCKS.register(
                        "shield_jammer_input",
                        () -> new com.mechanicalskies.vsshields.block.ShieldJammerInputBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(5.0f, 10.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()));

        public static final RegistrySupplier<Block> shield_jammer_frame = BLOCKS.register(
                        "shield_jammer_frame",
                        () -> new com.mechanicalskies.vsshields.block.ShieldJammerFrameBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(5.0f, 10.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> SOLID_PROJECTION_MODULE = BLOCKS.register(
                        "solid_projection_module",
                        () -> new SolidProjectionModuleBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(5.0f, 10.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static void register() {
                BLOCKS.register();
        }
}
