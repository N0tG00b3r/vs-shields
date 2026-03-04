package com.mechanicalskies.vsshields.anomaly.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minable ore block found on Aetheric Anomaly islands.
 * Requires iron+ pickaxe, hardness 15.
 * Drops 1-3 raw_aether_crystal (Fortune-affected).
 * Emits sparkle particles when a player is within 3 blocks.
 */
public class AetherCrystalOreBlock extends Block {
    public AetherCrystalOreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Sparkle particles only when a player is nearby (within 3 blocks)
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0, false) == null) {
            return;
        }

        // Spawn 1-2 sparkle particles on random faces
        for (int i = 0; i < 1 + random.nextInt(2); i++) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    (random.nextDouble() - 0.5) * 0.02,
                    random.nextDouble() * 0.02,
                    (random.nextDouble() - 0.5) * 0.02);
        }
    }
}
