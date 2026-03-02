package com.mechanicalskies.vsshields.pod;

import com.mechanicalskies.vsshields.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.*;

/**
 * Handles assembling the Boarding Pod multiblock into a VS2 physical ship.
 *
 * Flow:
 *  1. {@link #collectPodBlocks} BFS-flood-fills all connected pod blocks (whitelist only).
 *  2. {@link #assemble} calls VS2's {@code ShipAssembler.assembleToShipFull()} which moves
 *     the blocks to shipyard space and returns a new ServerShip.
 */
public class BoardingPodAssembler {

    private static final Logger LOGGER = LoggerFactory.getLogger("vs_shields/pod_assembler");
    private static final int MAX_BLOCKS = 50;

    /**
     * Blocks that are allowed as part of a Boarding Pod structure.
     * Add future pod block types here to extend the system.
     * Lazily initialised on first use (DeferredRegister values are ready by then).
     */
    private static Set<Block> allowedPodBlocks() {
        return Set.of(
                ModBlocks.BOARDING_POD_COCKPIT.get(),
                ModBlocks.BOARDING_POD_ENGINE.get()
        );
    }

    /** Result of a pod block collection: valid pod blocks + any unrecognised adjacent vs_shields blocks. */
    public static final class CollectResult {
        public final Set<BlockPos> podBlocks;
        /** Adjacent blocks whose Block is a vs_shields block NOT in the allowed whitelist. */
        public final Set<BlockPos> invalidBlocks;

        CollectResult(Set<BlockPos> podBlocks, Set<BlockPos> invalidBlocks) {
            this.podBlocks    = podBlocks;
            this.invalidBlocks = invalidBlocks;
        }
    }

    /**
     * BFS flood-fill starting from {@code start}, collecting all connected
     * whitelisted pod blocks. Also records adjacent vs_shields blocks that are
     * NOT in the whitelist into {@link CollectResult#invalidBlocks}.
     * Stops at {@link #MAX_BLOCKS}.
     */
    public static CollectResult collectPodBlocks(ServerLevel level, BlockPos start) {
        Set<Block> allowed = allowedPodBlocks();

        Set<BlockPos> result   = new LinkedHashSet<>();
        Set<BlockPos> invalid  = new LinkedHashSet<>();
        Set<BlockPos> visited  = new HashSet<>();
        Queue<BlockPos> queue  = new ArrayDeque<>();
        queue.add(start);
        result.add(start);
        visited.add(start);

        while (!queue.isEmpty() && result.size() < MAX_BLOCKS) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos nb = cur.relative(dir);
                if (visited.contains(nb)) continue;
                visited.add(nb);
                Block b = level.getBlockState(nb).getBlock();
                if (allowed.contains(b)) {
                    result.add(nb);
                    queue.add(nb);
                } else {
                    // Flag any vs_shields block that isn't a valid pod part as invalid
                    String ns = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(b).getNamespace();
                    if ("vs_shields".equals(ns)) {
                        invalid.add(nb);
                    }
                }
            }
        }
        return new CollectResult(result, invalid);
    }

    /** Convenience overload that discards the invalid-block info (used by legacy callers). */
    public static Set<BlockPos> collectPodBlocksSimple(ServerLevel level, BlockPos start) {
        return collectPodBlocks(level, start).podBlocks;
    }

    /**
     * Assembles the given block positions into a VS2 ship.
     *
     * @return the new ship ID, or {@code Long.MIN_VALUE} on failure.
     */
    public static long assemble(ServerLevel level, Set<BlockPos> blocks) {
        if (blocks.isEmpty()) return Long.MIN_VALUE;
        try {
            // assembleToShipFull is a @JvmStatic method on the ShipAssembler Kotlin object.
            // From Java, Kotlin objects are accessed via INSTANCE or the static bridge.
            ShipAssembler.AssembleContext ctx =
                    ShipAssembler.INSTANCE.assembleToShipFull(level, blocks, 1.0);
            if (ctx == null || ctx.getShip() == null) {
                LOGGER.error("[PodAssembler] assembleToShipFull returned null context/ship");
                return Long.MIN_VALUE;
            }
            long id = ctx.getShip().getId();
            LOGGER.debug("[PodAssembler] Assembled {} blocks into ship id={}", blocks.size(), id);
            return id;
        } catch (Exception e) {
            LOGGER.error("[PodAssembler] Assembly failed", e);
            return Long.MIN_VALUE;
        }
    }
}
