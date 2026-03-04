package com.mechanicalskies.vsshields.anomaly.block;

import com.mechanicalskies.vsshields.anomaly.AnomalyInstance;
import com.mechanicalskies.vsshields.anomaly.AnomalyManager;
import com.mechanicalskies.vsshields.anomaly.VoidDepositExtraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Rare void energy deposit at the core of Aetheric Anomaly islands.
 * RMB starts an extraction session (hold to mine resonance fragments).
 * When the deposit is exhausted, the island destabilises and an aetheric pulse triggers.
 * Any direct block destruction also triggers destabilisation.
 *
 * Visual: dark purple-black mass with corona particles and downward void drips.
 */
public class ConcentratedVoidDepositBlock extends Block {
    public ConcentratedVoidDepositBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            VoidDepositExtraction extraction = AnomalyManager.getInstance().getVoidDepositExtraction();
            if (extraction != null) {
                extraction.startSession(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        // Dark corona particles extending 0.5 blocks outward
        for (int i = 0; i < 3; i++) {
            double x = cx + (random.nextDouble() - 0.5) * 1.5;
            double y = cy + (random.nextDouble() - 0.5) * 1.5;
            double z = cz + (random.nextDouble() - 0.5) * 1.5;
            level.addParticle(ParticleTypes.PORTAL, x, y, z,
                    (cx - x) * 0.3,
                    (cy - y) * 0.3,
                    (cz - z) * 0.3);
        }

        // Void drip particles falling downward (slow, luminous)
        if (random.nextInt(3) == 0) {
            double x = cx + (random.nextDouble() - 0.5) * 0.4;
            double z = cz + (random.nextDouble() - 0.5) * 0.4;
            level.addParticle(ParticleTypes.FALLING_OBSIDIAN_TEAR,
                    x, pos.getY() - 0.1, z,
                    0, -0.04, 0);
        }

        // Ambient witch particles (purple motes)
        if (random.nextInt(2) == 0) {
            double x = cx + (random.nextDouble() - 0.5) * 0.8;
            double y = cy + (random.nextDouble() - 0.5) * 0.8;
            double z = cz + (random.nextDouble() - 0.5) * 0.8;
            level.addParticle(ParticleTypes.WITCH, x, y, z, 0, 0.02, 0);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                          BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            triggerDestabilisation();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void triggerDestabilisation() {
        AnomalyManager mgr = AnomalyManager.getInstance();
        AnomalyInstance active = mgr.getActive();
        if (active != null && active.getPhase() != AnomalyInstance.Phase.WARNING
                && active.getPhase() != AnomalyInstance.Phase.DISSOLVING) {
            active.setPhase(AnomalyInstance.Phase.WARNING);
            mgr.notifyWarningStart();
        }
    }
}
