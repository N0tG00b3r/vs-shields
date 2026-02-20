package com.mechanicalskies.vsshields.shield;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import com.mechanicalskies.vsshields.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side singleton managing all active shields.
 * Keyed by VS2 ship ID (long).
 */
public class ShieldManager {
    private static final ShieldManager INSTANCE = new ShieldManager();
    private static int getSyncInterval() {
        return ShieldConfig.get().getGeneral().syncIntervalTicks;
    }

    private final Map<Long, ShieldInstance> shields = new HashMap<>();
    private final Map<Long, BlockPos> shieldOwners = new HashMap<>();
    private long currentTick = 0;
    private MinecraftServer server;

    private ShieldManager() {}

    public static ShieldManager getInstance() {
        return INSTANCE;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Register a shield for the given ship. Returns true if this BE is the owner.
     * Only the first generator on a ship can register; duplicates are rejected.
     */
    public boolean registerShield(long shipId, ShieldTier tier, BlockPos ownerPos) {
        if (shields.containsKey(shipId)) {
            // Shield already exists — only the original owner is valid
            BlockPos existing = shieldOwners.get(shipId);
            return existing != null && existing.equals(ownerPos);
        }
        shields.put(shipId, new ShieldInstance(shipId, tier));
        shieldOwners.put(shipId, ownerPos);
        return true;
    }

    /**
     * Check if the given BlockPos is the owner of the shield for this ship.
     */
    public boolean isOwner(long shipId, BlockPos pos) {
        BlockPos owner = shieldOwners.get(shipId);
        return owner != null && owner.equals(pos);
    }

    public void unregisterShield(long shipId, BlockPos ownerPos) {
        BlockPos existing = shieldOwners.get(shipId);
        if (existing != null && existing.equals(ownerPos)) {
            shields.remove(shipId);
            shieldOwners.remove(shipId);
        }
    }

    public ShieldInstance getShield(long shipId) {
        return shields.get(shipId);
    }

    public Map<Long, ShieldInstance> getAllShields() {
        return shields;
    }

    public void tick() {
        currentTick++;
        for (ShieldInstance shield : shields.values()) {
            shield.tick(currentTick);
        }

        // Sync shield data to all clients every SYNC_INTERVAL ticks
        if (server != null && currentTick % getSyncInterval() == 0) {
            ModNetwork.sendSyncToAll(server);
        }
    }

    public long getCurrentTick() {
        return currentTick;
    }

    /**
     * Called when the world unloads to clean up all shield data.
     */
    public void clear() {
        shields.clear();
        shieldOwners.clear();
        currentTick = 0;
        server = null;
    }
}
