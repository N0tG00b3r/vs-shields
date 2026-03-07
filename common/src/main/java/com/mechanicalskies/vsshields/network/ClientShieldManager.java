package com.mechanicalskies.vsshields.network;

import com.mechanicalskies.vsshields.client.ShieldPanelAnimator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side shield data store.
 * Receives data from server sync packets and provides it to renderers/HUD.
 */
public class ClientShieldManager {
    private static final ClientShieldManager INSTANCE = new ClientShieldManager();

    private final Map<Long, ClientShieldData> shields = new ConcurrentHashMap<>();
    private final Map<Long, ShieldPanelAnimator> animators = new ConcurrentHashMap<>();

    private ClientShieldManager() {
    }

    public static ClientShieldManager getInstance() {
        return INSTANCE;
    }

    public void updateShield(long shipId, double currentHP, double maxHP, boolean active,
            double energyPercent, boolean solidMode,
            double worldMinX, double worldMinY, double worldMinZ,
            double worldMaxX, double worldMaxY, double worldMaxZ) {
        shields.compute(shipId, (id, existing) -> {
            if (!active && existing == null)
                return null;
            ClientShieldData data = existing != null ? existing : new ClientShieldData();
            data.shipId = shipId;
            data.currentHP = currentHP;
            data.maxHP = maxHP;
            data.active = active;
            data.energyPercent = energyPercent;
            data.solidMode = solidMode;
            data.worldMinX = worldMinX;
            data.worldMinY = worldMinY;
            data.worldMinZ = worldMinZ;
            data.worldMaxX = worldMaxX;
            data.worldMaxY = worldMaxY;
            data.worldMaxZ = worldMaxZ;
            return data;
        });
    }

    public ClientShieldData getShield(long shipId) {
        return shields.get(shipId);
    }

    public ShieldPanelAnimator getAnimator(long shipId) {
        return animators.get(shipId);
    }

    /** Get or create an animator for the given shield. */
    public ShieldPanelAnimator getOrCreateAnimator(long shipId) {
        return animators.computeIfAbsent(shipId, id -> new ShieldPanelAnimator());
    }

    public Map<Long, ClientShieldData> getAllShields() {
        return shields;
    }

    public void clear() {
        shields.clear();
        animators.clear();
    }

    /** Remove animators for shields that no longer exist. */
    public void retainAnimators(java.util.Set<Long> activeIds) {
        animators.keySet().retainAll(activeIds);
    }

    /**
     * Client-side shield data received from the server.
     */
    public static class ClientShieldData {
        public long shipId;
        public double currentHP;
        public double maxHP;
        public boolean active;
        public double energyPercent;
        public boolean solidMode;
        // Ship world-space AABB for client-side proximity checks
        public double worldMinX, worldMinY, worldMinZ;
        public double worldMaxX, worldMaxY, worldMaxZ;

        public double getHPPercent() {
            return maxHP > 0 ? currentHP / maxHP : 0;
        }

        /**
         * Check if a world-space position is within this shield's inflated AABB.
         */
        public boolean containsInflated(double x, double y, double z, double padding) {
            return x >= worldMinX - padding && x <= worldMaxX + padding &&
                    y >= worldMinY - padding && y <= worldMaxY + padding &&
                    z >= worldMinZ - padding && z <= worldMaxZ + padding;
        }
    }
}
