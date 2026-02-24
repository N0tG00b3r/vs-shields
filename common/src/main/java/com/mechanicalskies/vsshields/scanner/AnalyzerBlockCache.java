package com.mechanicalskies.vsshields.scanner;

import com.mechanicalskies.vsshields.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side reactive cache of cannon and critical block positions (in shipyard space) per ship.
 * Populated lazily on first scan request, then updated via BlockEvent listeners in the Forge module.
 */
public class AnalyzerBlockCache {
    private static final AnalyzerBlockCache INSTANCE = new AnalyzerBlockCache();

    /** Max blocks to iterate during initial population to avoid server lag. */
    private static final int MAX_SCAN_BLOCKS = 100_000;
    /** Max cannon positions stored per ship. */
    private static final int MAX_CANNONS = 128;
    /** Max critical positions stored per ship. */
    private static final int MAX_CRITICAL = 4;

    private final Map<Long, Set<BlockPos>> cannonCache = new ConcurrentHashMap<>();
    private final Map<Long, Set<BlockPos>> criticalCache = new ConcurrentHashMap<>();
    /** Ships that have already been scanned (cache is populated). */
    private final Set<Long> populated = ConcurrentHashMap.newKeySet();

    private AnalyzerBlockCache() {}

    public static AnalyzerBlockCache getInstance() {
        return INSTANCE;
    }

    /**
     * Ensures the block cache is populated for the given ship.
     * Performs a full scan of the ship's shipyard AABB on first call.
     * Must be called from the game thread.
     */
    public void ensurePopulated(Ship ship, ServerLevel level) {
        long shipId = ship.getId();
        if (populated.contains(shipId)) return;

        AABBic aabb = ship.getShipAABB();
        if (aabb == null) {
            populated.add(shipId);
            return;
        }

        Set<BlockPos> cannons = ConcurrentHashMap.newKeySet();
        Set<BlockPos> critical = ConcurrentHashMap.newKeySet();

        // betweenClosed performs a proper 3D iteration over the entire shipyard AABB.
        // spiralAround only covered a single 2D plane (one Z-slice) and missed most blocks.
        int checked = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                aabb.minX(), aabb.minY(), aabb.minZ(),
                aabb.maxX(), aabb.maxY(), aabb.maxZ())) {
            if (checked++ >= MAX_SCAN_BLOCKS) break;

            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                if (state.is(ModTags.CANNON_BLOCKS) && cannons.size() < MAX_CANNONS) {
                    cannons.add(pos.immutable());
                }
                if (state.is(ModTags.CRITICAL_BLOCKS) && critical.size() < MAX_CRITICAL) {
                    critical.add(pos.immutable());
                }
            }
        }

        cannonCache.put(shipId, cannons);
        criticalCache.put(shipId, critical);
        populated.add(shipId);
    }

    /** Triggered by block placement event (from Forge module). */
    public void onBlockPlaced(long shipId, BlockPos pos, BlockState state) {
        if (state.is(ModTags.CANNON_BLOCKS)) {
            cannonCache.computeIfAbsent(shipId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        }
        if (state.is(ModTags.CRITICAL_BLOCKS)) {
            criticalCache.computeIfAbsent(shipId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        }
    }

    /** Triggered by block break event (from Forge module). */
    public void onBlockRemoved(long shipId, BlockPos pos) {
        Set<BlockPos> c = cannonCache.get(shipId);
        if (c != null) c.remove(pos);
        Set<BlockPos> cr = criticalCache.get(shipId);
        if (cr != null) cr.remove(pos);
    }

    public Set<BlockPos> getCannons(long shipId) {
        Set<BlockPos> c = cannonCache.get(shipId);
        return c != null ? c : Collections.emptySet();
    }

    public Set<BlockPos> getCritical(long shipId) {
        Set<BlockPos> c = criticalCache.get(shipId);
        return c != null ? c : Collections.emptySet();
    }

    /** Invalidates the cache entry for a ship (called when ship is removed/disassembled). */
    public void invalidate(long shipId) {
        cannonCache.remove(shipId);
        criticalCache.remove(shipId);
        populated.remove(shipId);
    }

    /** Clears all cached data (called on server stop). */
    public void clear() {
        cannonCache.clear();
        criticalCache.clear();
        populated.clear();
    }
}
