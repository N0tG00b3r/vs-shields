package com.mechanicalskies.vsshields.registry;

import com.mechanicalskies.vsshields.VSShieldsMod;
import com.mechanicalskies.vsshields.block.BoardingPodCockpitBlock;
import com.mechanicalskies.vsshields.block.BoardingPodEngineBlock;
import com.mechanicalskies.vsshields.block.CloakingFieldGeneratorBlock;
import com.mechanicalskies.vsshields.block.GravityFieldGeneratorBlock;
import com.mechanicalskies.vsshields.block.ShieldBatteryCellBlock;
import com.mechanicalskies.vsshields.block.ShieldBatteryControllerBlock;
import com.mechanicalskies.vsshields.block.ShieldBatteryInputBlock;
import com.mechanicalskies.vsshields.block.ShieldCapacitorBlock;
import com.mechanicalskies.vsshields.block.ShieldEmitterBlock;
import com.mechanicalskies.vsshields.block.ShieldGeneratorBlock;
import com.mechanicalskies.vsshields.block.SolidProjectionModuleBlock;
import com.mechanicalskies.vsshields.anomaly.block.AethericStoneBlock;
import com.mechanicalskies.vsshields.anomaly.block.AethericStoneCrackedBlock;
import com.mechanicalskies.vsshields.anomaly.block.VoidMossBlock;
import com.mechanicalskies.vsshields.anomaly.block.AetherCrystalOreBlock;
import com.mechanicalskies.vsshields.anomaly.block.ResonanceClusterBlock;
import com.mechanicalskies.vsshields.anomaly.block.ConcentratedVoidDepositBlock;
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
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

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

        public static final RegistrySupplier<Block> BOARDING_POD_COCKPIT = BLOCKS.register(
                        "boarding_pod_cockpit",
                        () -> new BoardingPodCockpitBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(4.0f, 8.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static final RegistrySupplier<Block> BOARDING_POD_ENGINE = BLOCKS.register(
                        "boarding_pod_engine",
                        () -> new BoardingPodEngineBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(4.0f, 8.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        // ===== Aetheric Anomaly blocks =====

        public static final RegistrySupplier<Block> AETHERIC_STONE = BLOCKS.register(
                        "aetheric_stone",
                        () -> new AethericStoneBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(50.0f, 1200.0f)
                                                        .sound(SoundType.STONE)
                                                        .requiresCorrectToolForDrops()));

        public static final RegistrySupplier<Block> AETHERIC_STONE_CRACKED = BLOCKS.register(
                        "aetheric_stone_cracked",
                        () -> new AethericStoneCrackedBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(40.0f, 1200.0f)
                                                        .sound(SoundType.STONE)
                                                        .requiresCorrectToolForDrops()));

        public static final RegistrySupplier<Block> VOID_MOSS = BLOCKS.register(
                        "void_moss",
                        () -> new VoidMossBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(0.3f, 0.3f)
                                                        .sound(SoundType.MOSS_CARPET)
                                                        .noOcclusion()
                                                        .noLootTable()));

        public static final RegistrySupplier<Block> AETHER_CRYSTAL_ORE = BLOCKS.register(
                        "aether_crystal_ore",
                        () -> new AetherCrystalOreBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(15.0f, 15.0f)
                                                        .sound(SoundType.AMETHYST)
                                                        .requiresCorrectToolForDrops()
                                                        .lightLevel(s -> 8)));

        public static final RegistrySupplier<Block> RESONANCE_CLUSTER = BLOCKS.register(
                        "resonance_cluster",
                        () -> new ResonanceClusterBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(30.0f, 30.0f)
                                                        .sound(SoundType.AMETHYST_CLUSTER)
                                                        .requiresCorrectToolForDrops()
                                                        .lightLevel(s -> 12)));

        public static final RegistrySupplier<Block> CONCENTRATED_VOID_DEPOSIT = BLOCKS.register(
                        "concentrated_void_deposit",
                        () -> new ConcentratedVoidDepositBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(-1.0f, 3600000.0f)
                                                        .sound(SoundType.SCULK)
                                                        .lightLevel(s -> 10)
                                                        .noLootTable()));

        // ===== Phase 4: Detection blocks =====

        public static final RegistrySupplier<Block> RESONANCE_BEACON = BLOCKS.register(
                        "resonance_beacon",
                        () -> new com.mechanicalskies.vsshields.block.ResonanceBeaconBlock(
                                        BlockBehaviour.Properties.of()
                                                        .strength(5.0f, 10.0f)
                                                        .sound(SoundType.METAL)
                                                        .requiresCorrectToolForDrops()
                                                        .noOcclusion()));

        public static void register() {
                BLOCKS.register();
        }
}
