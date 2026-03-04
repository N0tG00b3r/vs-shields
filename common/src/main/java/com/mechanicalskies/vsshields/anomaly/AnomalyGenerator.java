package com.mechanicalskies.vsshields.anomaly;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Procedural generator for Aetheric Anomaly floating islands.
 * Shape: wide flat mesa top, tapered stalactite-like bottom,
 * with overhangs, caves, and ore deposits.
 */
public class AnomalyGenerator {

    public static class GenerationResult {
        private final Map<BlockPos, BlockState> blocks;
        private final int totalBlocks;

        public GenerationResult(Map<BlockPos, BlockState> blocks, int totalBlocks) {
            this.blocks = blocks;
            this.totalBlocks = totalBlocks;
        }

        public Map<BlockPos, BlockState> getBlocks() { return blocks; }
        public int getTotalBlocks() { return totalBlocks; }
    }

    /**
     * Generate an island shape.
     * All positions are relative to (0,0,0) center.
     */
    public static GenerationResult generate(long seed, ShieldConfig.AnomalyConfig config) {
        RandomSource random = RandomSource.create(seed);
        ImprovedNoise noise1 = new ImprovedNoise(random);
        ImprovedNoise noise2 = new ImprovedNoise(RandomSource.create(seed ^ 0xDEADBEEFL));
        ImprovedNoise noise3 = new ImprovedNoise(RandomSource.create(seed ^ 0xCAFEBABEL));

        int minSize = config.minIslandSize;
        int maxSize = config.maxIslandSize;
        int height = config.islandHeight;

        int diameterX = minSize + random.nextInt(Math.max(1, maxSize - minSize + 1));
        int diameterZ = minSize + random.nextInt(Math.max(1, maxSize - minSize + 1));

        int radiusX = diameterX / 2;
        int radiusZ = diameterZ / 2;
        int topHalf = height / 3;          // top section is shorter (mesa)
        int bottomHalf = height * 2 / 3;   // bottom section extends further (stalactite)

        Map<BlockPos, BlockState> blocks = new HashMap<>();

        // ====== Phase 1: Column-based generation with per-column noise ======
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int z = -radiusZ; z <= radiusZ; z++) {
                double nx = (double) x / radiusX;
                double nz = (double) z / radiusZ;
                double horizDist = nx * nx + nz * nz;

                if (horizDist > 1.15) continue; // slight overshoot for noise to carve edges

                // 2D noise for edge variation → creates overhangs and irregular outline
                // Note: no seed offset here — noise1 is already seeded via RandomSource.create(seed)
                // Adding seed * 0.001 would overflow Mth.floor() for large seeds, producing degenerate values
                double edgeNoise = noise1.noise(x * 0.12, 0, z * 0.12);
                double edgeThreshold = 1.0 + edgeNoise * 0.25; // varies ±25%

                if (horizDist > edgeThreshold) continue;

                // Per-column stalactite depth: how far down this column extends
                // Center columns go deepest, edge columns are shorter
                double centerDist = Math.sqrt(horizDist);
                double stalactiteNoise = noise2.noise(x * 0.15, 0, z * 0.15);
                // Stalactite factor: 1.0 at center, 0.0 at edge, perturbed by noise
                double stalactiteFactor = Math.max(0, (1.0 - centerDist) + stalactiteNoise * 0.3);
                int columnBottom = -(int)(bottomHalf * stalactiteFactor);

                // Top surface: gentle rolling hills — smooth enough for mobs to walk
                double hillNoise = noise3.noise(x * 0.05, 0, z * 0.05);         // broad gentle hills
                double hillDetail = noise1.noise(x * 0.10, 0, z * 0.10) * 0.15; // very subtle bumps
                double hillHeight = (hillNoise + hillDetail) * 2.5; // ±2.5 blocks variation
                int columnTop;
                if (horizDist < 0.7) {
                    columnTop = topHalf + (int) hillHeight;
                } else {
                    // Outer area: slopes down, hills fade out
                    double slopeFactor = (1.0 - Math.sqrt(horizDist)) / (1.0 - Math.sqrt(0.7));
                    columnTop = (int)(topHalf * Math.max(0, slopeFactor) + hillHeight * slopeFactor);
                }

                // Fill this column
                for (int y = columnBottom; y <= columnTop; y++) {
                    // 3D cave carving with two noise octaves
                    double caveNoise = noise1.noise(x * 0.1, y * 0.12, z * 0.1);
                    double caveDetail = noise2.noise(x * 0.2, y * 0.25, z * 0.2);
                    double caveCombined = caveNoise * 0.7 + caveDetail * 0.3;

                    // Caves: rare, only deep inside the island
                    double vertNorm = (double)(y - columnBottom) / Math.max(1, columnTop - columnBottom);
                    double surfaceGuard = Math.min(vertNorm, 1.0 - vertNorm) * 4.0;
                    surfaceGuard = Math.min(surfaceGuard, 1.0);
                    double horizGuard = Math.min((1.0 - centerDist) * 3.0, 1.0);
                    double guard = Math.min(surfaceGuard, horizGuard);

                    // Higher threshold = fewer caves; strong surface guard keeps shell solid
                    double caveThreshold = -0.25 + (1.0 - guard) * 0.5;
                    if (caveCombined < caveThreshold) continue;

                    blocks.put(new BlockPos(x, y, z), ModBlocks.AETHERIC_STONE.get().defaultBlockState());
                }
            }
        }

        // ====== Phase 2: Surface detection ======
        // Cracked stone only on ~30% of cave ceilings (not every exposed face)
        Map<BlockPos, BlockState> surfaceOverrides = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!blocks.containsKey(pos.above())) {
                // Top surface — void moss
                surfaceOverrides.put(pos, ModBlocks.VOID_MOSS.get().defaultBlockState());
            } else if (!blocks.containsKey(pos.below()) && random.nextFloat() < 0.3f) {
                // Cave ceiling — 30% chance cracked stone
                surfaceOverrides.put(pos, ModBlocks.AETHERIC_STONE_CRACKED.get().defaultBlockState());
            }
        }
        blocks.putAll(surfaceOverrides);

        // ====== Phase 3: Ore scattering ======
        int totalSize = blocks.size();
        if (totalSize > 0) {
            // Crystal ore: 12-20 scattered in interior (avoid surfaces)
            int crystalCount = 12 + random.nextInt(9);
            scatterInteriorOre(blocks, surfaceOverrides, random,
                    ModBlocks.AETHER_CRYSTAL_ORE.get().defaultBlockState(),
                    crystalCount, -bottomHalf, topHalf);

            // Resonance cluster: 3-5 in lower half
            int clusterCount = 3 + random.nextInt(3);
            int clustersPlaced = scatterInteriorOre(blocks, surfaceOverrides, random,
                    ModBlocks.RESONANCE_CLUSTER.get().defaultBlockState(),
                    clusterCount, -bottomHalf, 0);
            // Guarantee at least 1 — fallback to any stone block
            if (clustersPlaced == 0) {
                scatterAnyStone(blocks, random,
                        ModBlocks.RESONANCE_CLUSTER.get().defaultBlockState(), 1);
            }

            // Void deposit: exactly 1 near center
            int depositsPlaced = scatterCenterOre(blocks, random,
                    ModBlocks.CONCENTRATED_VOID_DEPOSIT.get().defaultBlockState(),
                    1, radiusX / 3, topHalf / 2, radiusZ / 3);
            // Guarantee at least 1 — fallback to any stone block
            if (depositsPlaced == 0) {
                scatterAnyStone(blocks, random,
                        ModBlocks.CONCENTRATED_VOID_DEPOSIT.get().defaultBlockState(), 1);
            }
        }

        return new GenerationResult(blocks, blocks.size());
    }

    /**
     * Replace random aetheric_stone blocks (preferring interior, not surface) with ore.
     * @return number of blocks actually placed
     */
    private static int scatterInteriorOre(Map<BlockPos, BlockState> blocks,
                                           Map<BlockPos, BlockState> surfaceBlocks,
                                           RandomSource random,
                                           BlockState ore, int count, int minY, int maxY) {
        BlockState aethericStone = ModBlocks.AETHERIC_STONE.get().defaultBlockState();
        List<BlockPos> candidates = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue() == aethericStone
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && !surfaceBlocks.containsKey(pos)) {
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                if (entry.getValue() == aethericStone
                        && pos.getY() >= minY && pos.getY() <= maxY) {
                    candidates.add(pos);
                }
            }
        }
        Collections.shuffle(candidates, new Random(random.nextLong()));
        int placed = 0;
        for (BlockPos pos : candidates) {
            if (placed >= count) break;
            blocks.put(pos, ore);
            placed++;
        }
        return placed;
    }

    /**
     * Place ore near the center of the island.
     * @return number of blocks actually placed
     */
    private static int scatterCenterOre(Map<BlockPos, BlockState> blocks, RandomSource random,
                                         BlockState ore, int count,
                                         int maxDx, int maxDy, int maxDz) {
        BlockState aethericStone = ModBlocks.AETHERIC_STONE.get().defaultBlockState();
        List<BlockPos> candidates = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue() == aethericStone
                    && Math.abs(pos.getX()) <= maxDx
                    && Math.abs(pos.getY()) <= maxDy
                    && Math.abs(pos.getZ()) <= maxDz) {
                candidates.add(pos);
            }
        }
        Collections.shuffle(candidates, new Random(random.nextLong()));
        int placed = 0;
        for (BlockPos pos : candidates) {
            if (placed >= count) break;
            blocks.put(pos, ore);
            placed++;
        }
        return placed;
    }

    /**
     * Last-resort fallback: replace ANY aetheric_stone block with ore.
     */
    private static void scatterAnyStone(Map<BlockPos, BlockState> blocks, RandomSource random,
                                         BlockState ore, int count) {
        BlockState aethericStone = ModBlocks.AETHERIC_STONE.get().defaultBlockState();
        List<BlockPos> candidates = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (entry.getValue() == aethericStone) {
                candidates.add(entry.getKey());
            }
        }
        Collections.shuffle(candidates, new Random(random.nextLong()));
        int placed = 0;
        for (BlockPos pos : candidates) {
            if (placed >= count) break;
            blocks.put(pos, ore);
            placed++;
        }
    }
}
