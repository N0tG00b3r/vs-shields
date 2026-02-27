package com.mechanicalskies.vsshields.client;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the IDs of ships that are currently cloaked on the client-side.
 * Used by the chunk culling mixin to skip rendering these ships.
 */
public class CloakedShipsRegistry {
    private static final CloakedShipsRegistry INSTANCE = new CloakedShipsRegistry();

    // Concurrent map used as a set for thread-safe access during rendering
    private final Set<Long> cloakedShips = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private CloakedShipsRegistry() {
    }

    public static CloakedShipsRegistry getInstance() {
        return INSTANCE;
    }

    public void addShip(long shipId) {
        cloakedShips.add(shipId);
    }

    public void removeShip(long shipId) {
        cloakedShips.remove(shipId);
    }

    public boolean isCloaked(long shipId) {
        return cloakedShips.contains(shipId);
    }

    public boolean hasAnyCloakedShips() {
        return !cloakedShips.isEmpty();
    }

    public void clear() {
        cloakedShips.clear();
    }
}
