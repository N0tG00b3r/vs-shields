package com.mechanicalskies.vsshields.client;

// import org.valkyrienskies.core.api.ships.properties.ShipId; // Removed as ShipId is a Kotlin typealias for Long

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages the cloaking status of ships on the client side.
 * This class receives updates from the server and maintains a local cache
 * of which ships are currently cloaked.
 */
public class ClientCloakManager {
    private static final ClientCloakManager INSTANCE = new ClientCloakManager();

    // Store Long objects directly
    private final Set<Long> cloakedShips;

    private ClientCloakManager() {
        this.cloakedShips = Collections.synchronizedSet(new HashSet<>());
    }

    public static ClientCloakManager getInstance() {
        return INSTANCE;
    }

    /**
     * Updates the cloaking status of a ship.
     *
     * @param shipId The ID of the ship to update.
     * @param isCloaked The new cloaking status (true for cloaked, false for uncloaked).
     */
    public void updateCloakingStatus(Long shipId, boolean isCloaked) { // Parameter is now Long
        if (isCloaked) {
            cloakedShips.add(shipId);
        } else {
            cloakedShips.remove(shipId);
        }
    }

    /**
     * Checks if a ship is currently cloaked on the client.
     *
     * @param shipId The ID of the ship to check.
     * @return True if the ship is cloaked, false otherwise.
     */
    public boolean isCloaked(Long shipId) { // Parameter is now Long
        return cloakedShips.contains(shipId);
    }

    /**
     * Clears all cloaked ship data from the client.
     * This should be called when disconnecting from a server.
     */
    public void clear() {
        cloakedShips.clear();
    }
}
