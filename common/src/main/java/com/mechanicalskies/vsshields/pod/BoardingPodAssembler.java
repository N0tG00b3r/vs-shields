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
 *  1. {@link #collectPodBlocks} BFS-flood-fills all connected pod blocks (cockpit + engine).
 *  2. {@link #assemble} calls VS2's {@code ShipAssembler.assembleToShipFull()} which moves
 *     the blocks to shipyard space and returns a new ServerShip.
 */
public class BoardingPodAssembler {

    private static final Logger LOGGER = LoggerFactory.getLogger("vs_shields/pod_assembler");
    private static final int MAX_BLOCKS = 50;

    /**
     * BFS flood-fill starting from {@code start}, collecting all connected
     * boarding-pod blocks (cockpit or engine). Stops at {@link #MAX_BLOCKS}.
     */
    public static Set<BlockPos> collectPodBlocks(ServerLevel level, BlockPos start) {
        Set<BlockPos> result = new LinkedHashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        result.add(start);

        Block cockpit = ModBlocks.BOARDING_POD_COCKPIT.get();
        Block engine  = ModBlocks.BOARDING_POD_ENGINE.get();

        while (!queue.isEmpty() && result.size() < MAX_BLOCKS) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos nb = cur.relative(dir);
                if (result.contains(nb)) continue;
                Block b = level.getBlockState(nb).getBlock();
                if (b == cockpit || b == engine) {
                    result.add(nb);
                    queue.add(nb);
                }
            }
        }
        return result;
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
