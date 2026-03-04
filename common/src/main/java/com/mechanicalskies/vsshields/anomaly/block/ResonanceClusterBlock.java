package com.mechanicalskies.vsshields.anomaly.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rare crystal cluster found in caves/undersides of Aetheric Anomaly islands.
 * Requires diamond+ pickaxe, hardness 30.
 * Drops 1-2 resonance_fragment.
 * Emits electric spark particles simulating energy arcs between crystal tips.
 */
public class ResonanceClusterBlock extends Block {
    public ResonanceClusterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Constant ambient sparkle — denser than crystal ore, always visible
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        // 2-3 electric sparks per tick simulating arc connections
        for (int i = 0; i < 2 + random.nextInt(2); i++) {
            double x = cx + (random.nextDouble() - 0.5) * 0.8;
            double y = cy + (random.nextDouble() - 0.5) * 0.8;
            double z = cz + (random.nextDouble() - 0.5) * 0.8;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    (random.nextDouble() - 0.5) * 0.05,
                    (random.nextDouble() - 0.5) * 0.05,
                    (random.nextDouble() - 0.5) * 0.05);
        }

        // Occasional end_rod particle for bright glow effect
        if (random.nextInt(4) == 0) {
            double x = cx + (random.nextDouble() - 0.5) * 0.6;
            double y = cy + random.nextDouble() * 0.4;
            double z = cz + (random.nextDouble() - 0.5) * 0.6;
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.01, 0);
        }
    }
}
