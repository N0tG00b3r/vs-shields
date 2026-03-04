package com.mechanicalskies.vsshields.anomaly;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mechanicalskies.vsshields.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Manages server-side hold-RMB extraction sessions for Concentrated Void Deposits.
 * Players start a session via Block.use(), then this system validates each tick
 * that the player is still holding RMB (facing the block within range).
 */
public class VoidDepositExtraction {

    private static final Logger LOGGER = LoggerFactory.getLogger("VoidDepositExtraction");
    private static final double MAX_REACH = 4.5;

    private final Map<UUID, ExtractionSession> sessions = new HashMap<>();

    public static class ExtractionSession {
        public final BlockPos blockPos;
        public int progressTicks;
        public int itemsDropped;

        public ExtractionSession(BlockPos blockPos) {
            this.blockPos = blockPos;
            this.progressTicks = 0;
            this.itemsDropped = 0;
        }
    }

    /**
     * Start or continue an extraction session for a player on a void deposit block.
     * Called from ConcentratedVoidDepositBlock.use().
     */
    public void startSession(ServerPlayer player, BlockPos pos) {
        ExtractionSession existing = sessions.get(player.getUUID());
        if (existing != null && existing.blockPos.equals(pos)) {
            return; // Already extracting this block
        }
        sessions.put(player.getUUID(), new ExtractionSession(pos));
        LOGGER.debug("[Extraction] Player {} started extraction at {}", player.getName().getString(), pos);
    }

    /**
     * Returns true if the given player has an active extraction session.
     */
    public boolean isExtracting(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Called every server tick to advance all active extraction sessions.
     */
    public void tick(ServerLevel level) {
        if (sessions.isEmpty()) return;

        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();
        int ticksPerItem = config.extractionTicksPerItem;
        int maxItems = config.extractionItemsPerDeposit;

        Iterator<Map.Entry<UUID, ExtractionSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ExtractionSession> entry = it.next();
            UUID playerId = entry.getKey();
            ExtractionSession session = entry.getValue();

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive()) {
                sendProgressCancel(player);
                it.remove();
                continue;
            }

            // Verify player is still facing the deposit block within reach
            if (!isPlayerFacingBlock(player, session.blockPos)) {
                sendProgressCancel(player);
                it.remove();
                continue;
            }

            // Verify the block still exists
            if (!(level.getBlockState(session.blockPos).getBlock()
                    instanceof com.mechanicalskies.vsshields.anomaly.block.ConcentratedVoidDepositBlock)) {
                sendProgressCancel(player);
                it.remove();
                continue;
            }

            session.progressTicks++;

            // Drop void_essence (1-2) at interval
            if (session.progressTicks >= ticksPerItem) {
                session.progressTicks = 0;
                session.itemsDropped++;

                int count = 1 + level.random.nextInt(2); // 1-2
                ItemEntity itemEntity = new ItemEntity(
                        level,
                        player.getX(), player.getY() + 0.5, player.getZ(),
                        new ItemStack(ModItems.VOID_ESSENCE.get(), count)
                );
                level.addFreshEntity(itemEntity);

                // Check if deposit is exhausted
                if (session.itemsDropped >= maxItems) {
                    it.remove();
                    sendProgressCancel(player);
                    onDepositExhausted(level, session.blockPos);
                    continue;
                }
            }

            // Send progress update
            float progress = (float) session.progressTicks / (float) ticksPerItem;
            float overallProgress = ((float) session.itemsDropped + progress) / (float) maxItems;
            ModNetwork.sendExtractionProgress(player, overallProgress, true);
        }
    }

    /**
     * Cancel all active sessions (e.g., on anomaly despawn).
     */
    public void clearAll() {
        sessions.clear();
    }

    private boolean isPlayerFacingBlock(ServerPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(MAX_REACH));

        BlockHitResult result = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        ));

        if (result.getType() != HitResult.Type.BLOCK) return false;
        return result.getBlockPos().equals(pos);
    }

    private void sendProgressCancel(ServerPlayer player) {
        if (player != null) {
            ModNetwork.sendExtractionProgress(player, 0f, false);
        }
    }

    private void onDepositExhausted(ServerLevel level, BlockPos pos) {
        LOGGER.info("[Extraction] Void deposit exhausted at {}. Triggering destabilisation + pulse.", pos);

        // Trigger destabilisation
        AnomalyManager mgr = AnomalyManager.getInstance();
        AnomalyInstance active = mgr.getActive();
        if (active != null && active.getPhase() != AnomalyInstance.Phase.WARNING
                && active.getPhase() != AnomalyInstance.Phase.DISSOLVING) {
            active.setPhase(AnomalyInstance.Phase.WARNING);
            mgr.notifyWarningStart();
        }

        // Trigger aetheric pulse
        AnomalyPulseHandler.triggerPulse(level, active);
    }
}
