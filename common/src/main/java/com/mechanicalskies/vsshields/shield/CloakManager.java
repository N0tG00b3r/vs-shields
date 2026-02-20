package com.mechanicalskies.vsshields.shield;

import com.mechanicalskies.vsshields.network.VSShieldsNetworking;
import com.mechanicalskies.vsshields.network.packets.CloakStatusPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.valkyrienskies.core.api.ships.Ship;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages the cloaking status of ships on the server side.
 * Tracks which CloakingFieldGeneratorBE owns the cloak for each ship (one per ship).
 */
public class CloakManager {
    private static final CloakManager INSTANCE = new CloakManager();

    private final Set<Long> cloakedShips;
    // shipId → owner block position (shipyard coords)
    private final Map<Long, BlockPos> cloakOwners;
    // shipId → dimension key of the owner BE
    private final Map<Long, ResourceKey<Level>> cloakDimensions;

    private MinecraftServer server;

    private CloakManager() {
        this.cloakedShips = Collections.synchronizedSet(new HashSet<>());
        this.cloakOwners = Collections.synchronizedMap(new HashMap<>());
        this.cloakDimensions = Collections.synchronizedMap(new HashMap<>());
    }

    public static CloakManager getInstance() {
        return INSTANCE;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Register a cloaking generator as the owner for its ship.
     * @return true if this BE is now the owner, false if another BE already owns this ship (duplicate)
     */
    public boolean registerCloakOwner(long shipId, BlockPos pos, ResourceKey<Level> dimension) {
        BlockPos existing = cloakOwners.get(shipId);
        if (existing == null) {
            cloakOwners.put(shipId, pos);
            cloakDimensions.put(shipId, dimension);
            return true;
        }
        return existing.equals(pos);
    }

    /**
     * Unregister a cloaking generator. Also immediately uncloaks the ship if it was cloaked.
     */
    public void unregisterCloakOwner(long shipId, BlockPos pos) {
        if (cloakOwners.remove(shipId, pos)) {
            cloakDimensions.remove(shipId);
            if (server != null && cloakedShips.remove(shipId)) {
                sendCloakStatusToAllClients(shipId, false, server);
            }
        }
    }

    public void cloakShip(Ship ship, MinecraftServer server) {
        if (ship == null) return;
        long shipId = ship.getId();
        if (cloakedShips.add(shipId)) {
            sendCloakStatusToAllClients(shipId, true, server);
        }
    }

    public void uncloakShip(Ship ship, MinecraftServer server) {
        if (ship == null) return;
        long shipId = ship.getId();
        if (cloakedShips.remove(shipId)) {
            sendCloakStatusToAllClients(shipId, false, server);
        }
    }

    /** Cloak by ID, using the stored server reference. */
    public void cloakById(long shipId) {
        if (server == null) return;
        if (cloakedShips.add(shipId)) {
            sendCloakStatusToAllClients(shipId, true, server);
        }
    }

    /** Uncloak by ID, using the stored server reference. */
    public void uncloakById(long shipId) {
        if (server == null) return;
        if (cloakedShips.remove(shipId)) {
            sendCloakStatusToAllClients(shipId, false, server);
        }
    }

    public boolean isShipCloaked(long shipId) {
        return cloakedShips.contains(shipId);
    }

    public BlockPos getCloakOwnerPos(long shipId) {
        return cloakOwners.get(shipId);
    }

    public ResourceKey<Level> getCloakDimension(long shipId) {
        return cloakDimensions.get(shipId);
    }

    /**
     * Toggle cloaking active state on the owning BlockEntity.
     * Used by C2S toggle packet handler.
     */
    public void toggleCloak(long shipId, boolean active) {
        if (server == null) return;
        BlockPos pos = cloakOwners.get(shipId);
        ResourceKey<Level> dim = cloakDimensions.get(shipId);
        if (pos == null || dim == null) return;
        ServerLevel level = server.getLevel(dim);
        if (level == null) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof com.mechanicalskies.vsshields.blockentity.CloakingFieldGeneratorBlockEntity cloak) {
            cloak.setCloakingActive(active);
        }
    }

    private void sendCloakStatusToAllClients(long shipId, boolean isCloaked, MinecraftServer server) {
        CloakStatusPacket packet = new CloakStatusPacket(shipId, isCloaked);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            VSShieldsNetworking.sendToClient(player, packet);
        }
    }

    public void sendAllCloakedShipsToClient(ServerPlayer player) {
        for (Long shipId : cloakedShips) {
            VSShieldsNetworking.sendToClient(player, new CloakStatusPacket(shipId, true));
        }
    }

    /** Call on server stop / world unload to reset state. */
    public void clear() {
        cloakedShips.clear();
        cloakOwners.clear();
        cloakDimensions.clear();
        server = null;
    }
}
