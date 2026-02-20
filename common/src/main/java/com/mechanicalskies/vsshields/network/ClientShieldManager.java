package com.mechanicalskies.vsshields.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side shield data store.
 * Receives data from server sync packets and provides it to renderers/HUD.
 */
public class ClientShieldManager {
    private static final ClientShieldManager INSTANCE = new ClientShieldManager();

    private final Map<Long, ClientShieldData> shields = new ConcurrentHashMap<>();

    private ClientShieldManager() {}

    public static ClientShieldManager getInstance() {
        return INSTANCE;
    }

    public void updateShield(long shipId, double currentHP, double maxHP, boolean active) {
        shields.compute(shipId, (id, existing) -> {
            if (!active && existing == null) return null;
            ClientShieldData data = existing != null ? existing : new ClientShieldData();
            data.shipId = shipId;
            data.currentHP = currentHP;
            data.maxHP = maxHP;
            data.active = active;
            return data;
        });
    }

    public ClientShieldData getShield(long shipId) {
        return shields.get(shipId);
    }

    public Map<Long, ClientShieldData> getAllShields() {
        return shields;
    }

    public void clear() {
        shields.clear();
    }

    /**
     * Client-side shield data received from the server.
     */
    public static class ClientShieldData {
        public long shipId;
        public double currentHP;
        public double maxHP;
        public boolean active;

        public double getHPPercent() {
            return maxHP > 0 ? currentHP / maxHP : 0;
        }
    }
}
