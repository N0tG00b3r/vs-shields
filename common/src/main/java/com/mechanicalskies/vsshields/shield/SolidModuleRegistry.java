package com.mechanicalskies.vsshields.shield;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks which ship owns a Solid Projection Module (at most 1 per ship).
 * Pattern mirrors GravityFieldRegistry.
 */
public class SolidModuleRegistry {
    private static final SolidModuleRegistry INSTANCE = new SolidModuleRegistry();

    private final Map<Long, BlockPos> ownerPos = new HashMap<>();

    private SolidModuleRegistry() {}

    public static SolidModuleRegistry getInstance() { return INSTANCE; }

    /**
     * Attempt to register a block as the owner for the given ship.
     * @return true if registered as owner, false if a different owner already exists.
     */
    public synchronized boolean registerOwner(long shipId, BlockPos pos) {
        BlockPos existing = ownerPos.get(shipId);
        if (existing == null) {
            ownerPos.put(shipId, pos);
            return true;
        }
        return existing.equals(pos);
    }

    public synchronized void unregisterOwner(long shipId, BlockPos pos) {
        BlockPos existing = ownerPos.get(shipId);
        if (existing != null && existing.equals(pos)) {
            ownerPos.remove(shipId);
        }
    }

    public synchronized boolean isOwner(long shipId, BlockPos pos) {
        return pos.equals(ownerPos.get(shipId));
    }

    public synchronized void clear() {
        ownerPos.clear();
    }
}
