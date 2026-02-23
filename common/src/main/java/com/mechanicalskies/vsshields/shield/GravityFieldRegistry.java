package com.mechanicalskies.vsshields.shield;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Server-side registry that tracks active Gravity Field Generators.
 * Provides duplicate detection and player proximity lookups for the Forge event handler.
 */
public class GravityFieldRegistry {

    public static class GravityFieldState {
        public final boolean flightEnabled;
        public final boolean fallProtectionEnabled;
        private final double minX, minY, minZ, maxX, maxY, maxZ;
        private final double padding;

        public GravityFieldState(boolean flightEnabled, boolean fallProtectionEnabled,
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ,
                double padding) {
            this.flightEnabled = flightEnabled;
            this.fallProtectionEnabled = fallProtectionEnabled;
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
            this.padding = padding;
        }

        public boolean containsPlayer(Player player) {
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            return px >= minX - padding && px <= maxX + padding
                && py >= minY - padding && py <= maxY + padding
                && pz >= minZ - padding && pz <= maxZ + padding;
        }
    }

    // shipId → owning BlockPos (for duplicate detection)
    private static final Map<Long, BlockPos> OWNERS = new ConcurrentHashMap<>();
    // shipId → active state (for player lookup)
    private static final Map<Long, GravityFieldState> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Try to register pos as the owner of shipId.
     * @return true if registration succeeded (no duplicate), false if another BE already owns this ship
     */
    public static boolean registerOwner(long shipId, BlockPos pos) {
        BlockPos existing = OWNERS.putIfAbsent(shipId, pos);
        return existing == null || existing.equals(pos);
    }

    public static void unregisterOwner(long shipId, BlockPos pos) {
        OWNERS.remove(shipId, pos);
    }

    public static void update(long shipId, GravityFieldState state) {
        ACTIVE.put(shipId, state);
    }

    public static void remove(long shipId) {
        ACTIVE.remove(shipId);
    }

    @Nullable
    public static GravityFieldState getForPlayer(Player player) {
        for (GravityFieldState state : ACTIVE.values()) {
            if (state.containsPlayer(player)) return state;
        }
        return null;
    }

    public static void clear() {
        OWNERS.clear();
        ACTIVE.clear();
    }
}
