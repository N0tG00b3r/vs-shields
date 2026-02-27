package com.mechanicalskies.vsshields.scanner;

import com.mechanicalskies.vsshields.entity.GravitationalMineEntity;
import com.mechanicalskies.vsshields.network.ModNetwork;
import com.mechanicalskies.vsshields.shield.ShieldInstance;
import com.mechanicalskies.vsshields.shield.ShieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.LoadedShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Server-side handler for the C2S ANALYZER_SCAN packet.
 * Performs a raycast against all loaded ships' world-space AABBs,
 * collects shield data and crew, and sends ANALYZER_DATA back to the client.
 */
public class AnalyzerScanHandler {

    private static final double RAY_LENGTH = 256.0;
    /**
     * Milliseconds before glow is removed from un-scanned entities (1.5 seconds).
     */
    private static final long GLOW_TIMEOUT_MS = 1500;

    private record ScanState(List<Integer> entityIds, long lastScanMs) {
    }

    private static final Map<UUID, ScanState> activeScanners = new ConcurrentHashMap<>();

    /**
     * Handle an incoming scan request from a client.
     * Must be called from the game thread.
     */
    public static void handle(ServerPlayer player, Vec3 eyePos, Vec3 look) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 rayEnd = eyePos.add(look.scale(RAY_LENGTH));

        // Determine player's own ship so we can skip it
        LoadedShip ownShip = VSGameUtilsKt.getShipObjectManagingPos(level, player.blockPosition());
        long ownId = ownShip != null ? ownShip.getId() : -1L;

        String dimensionId = VSGameUtilsKt.getDimensionId(level);

        LoadedServerShip best = null;
        double bestT = Double.MAX_VALUE;

        // Raycast against all loaded ships' world AABBs
        for (LoadedServerShip ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (ship.getId() == ownId)
                continue;
            if (!ship.getChunkClaimDimension().equals(dimensionId))
                continue;

            AABBdc w = ship.getWorldAABB();
            AABB mcAABB = new AABB(w.minX(), w.minY(), w.minZ(), w.maxX(), w.maxY(), w.maxZ());

            Optional<Vec3> hit = mcAABB.clip(eyePos, rayEnd);
            if (hit.isPresent()) {
                double dist = hit.get().distanceTo(eyePos);
                if (dist < bestT) {
                    bestT = dist;
                    best = ship;
                }
            }
        }

        if (best == null) {
            // No ship in view — clean up any lingering glow for this player
            cleanupGlow(player.getUUID(), level.getServer());
        }

        // --- Shield data ---
        long targetId = best != null ? best.getId() : -1L;
        ShieldInstance shield = targetId != -1 ? ShieldManager.getInstance().getShield(targetId) : null;
        double hp = shield != null ? shield.getCurrentHP() : 0;
        double maxHp = shield != null ? shield.getMaxHP() : 0;
        boolean active = shield != null && shield.isActive();
        boolean solid = shield != null && shield.isSolidMode();
        float energy = shield != null ? (float) shield.getEnergyPercent() : 0f;

        // --- Block cache ---
        Set<BlockPos> cannons = Collections.emptySet();
        Set<BlockPos> critical = Collections.emptySet();
        if (best != null) {
            AnalyzerBlockCache cache = AnalyzerBlockCache.getInstance();
            cache.ensurePopulated(best, level);
            cannons = cache.getCannons(targetId);
            critical = cache.getCritical(targetId);
        }

        // --- Crew entities ---
        List<Integer> crewIds = new ArrayList<>();
        if (best != null) {
            AABBdc w = best.getWorldAABB();
            AABB crewAABB = new AABB(w.minX(), w.minY(), w.minZ(), w.maxX(), w.maxY(), w.maxZ());
            List<Entity> crew = level.getEntities((Entity) null, crewAABB,
                    e -> (e instanceof LivingEntity) && !(e instanceof ArmorStand) && e.getId() != player.getId());
            crewIds = crew.stream().map(Entity::getId).collect(Collectors.toList());

            // Apply glow tag to crew
            crew.forEach(e -> e.setGlowingTag(true));
            activeScanners.put(player.getUUID(), new ScanState(new ArrayList<>(crewIds), System.currentTimeMillis()));
        }

        // --- Serialize shipToWorld matrix (column-major double[16]) ---
        double[] matrixArr = new double[16];
        if (best != null) {
            org.joml.Matrix4dc matrix = best.getShipToWorld();
            for (int col = 0; col < 4; col++)
                for (int row = 0; row < 4; row++)
                    matrixArr[col * 4 + row] = matrix.get(col, row);
        }

        // --- ARMED gravitational mines ---
        List<double[]> minePositions = new ArrayList<>();
        try {
            AABB mineSearchAABB;
            if (best != null) {
                // Near focused ship (70 block buffer)
                AABBdc w = best.getWorldAABB();
                mineSearchAABB = new AABB(
                        w.minX() - 70, w.minY() - 70, w.minZ() - 70,
                        w.maxX() + 70, w.maxY() + 70, w.maxZ() + 70);
            } else {
                // Broad scan around player (128 block radius)
                mineSearchAABB = new AABB(
                        player.getX() - 128, player.getY() - 128, player.getZ() - 128,
                        player.getX() + 128, player.getY() + 128, player.getZ() + 128);
            }

            for (Entity e : level.getEntities((Entity) null, mineSearchAABB,
                    e -> e instanceof GravitationalMineEntity gme &&
                            gme.getPhase() == GravitationalMineEntity.Phase.ARMED)) {
                minePositions.add(new double[] { e.getX(), e.getY(), e.getZ() });
                if (minePositions.size() >= 32)
                    break;
            }
        } catch (Exception ignored) {
        }

        ModNetwork.sendAnalyzerData(player, targetId, hp, maxHp, active, solid, energy,
                matrixArr, cannons, critical, crewIds, minePositions);
    }

    /**
     * Called from the server tick (VSShieldsModForge) to clean up glow on entities
     * when a player stops scanning (no packet received for > GLOW_TIMEOUT_MS).
     */
    public static void tickCleanup(MinecraftServer server) {
        long now = System.currentTimeMillis();
        activeScanners.entrySet().removeIf(entry -> {
            ScanState state = entry.getValue();
            if (now - state.lastScanMs() > GLOW_TIMEOUT_MS) {
                removeGlow(state.entityIds(), server);
                return true;
            }
            return false;
        });
    }

    private static void cleanupGlow(UUID playerUUID, MinecraftServer server) {
        ScanState state = activeScanners.remove(playerUUID);
        if (state != null)
            removeGlow(state.entityIds(), server);
    }

    private static void removeGlow(List<Integer> entityIds, MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (int id : entityIds) {
                Entity e = level.getEntity(id);
                if (e != null)
                    e.setGlowingTag(false);
            }
        }
    }

    /** Clear all tracking (e.g., on server stop). */
    public static void clear() {
        activeScanners.clear();
    }
}
