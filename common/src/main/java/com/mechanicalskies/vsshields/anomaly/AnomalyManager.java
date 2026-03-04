package com.mechanicalskies.vsshields.anomaly;

import com.mechanicalskies.vsshields.config.ShieldConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server singleton managing the Aetheric Anomaly lifecycle.
 * Handles spawn timing, phase state machine, and delegates VS2 operations
 * to forge-side callbacks.
 */
public class AnomalyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("AnomalyManager");
    private static final AnomalyManager INSTANCE = new AnomalyManager();

    // --- Callbacks set from forge module (VS2 API access) ---

    @FunctionalInterface
    public interface SpawnCallback {
        /**
         * Begin spawning an anomaly island (generates blocks, starts incremental placement).
         * Returns true if generation succeeded and placement started, false on failure.
         */
        boolean beginSpawn(ServerLevel level, Double x, Double y, Double z, ShieldConfig.AnomalyConfig config);
    }

    @FunctionalInterface
    public interface SpawnTickCallback {
        /**
         * Continue incremental block placement. Called every tick while spawning.
         * Returns AnomalyInstance when complete, null while in progress.
         */
        AnomalyInstance tickSpawn(ServerLevel level, ShieldConfig.AnomalyConfig config);
    }

    @FunctionalInterface
    public interface IsSpawningCallback {
        /** Check if an incremental spawn is currently in progress. */
        boolean isSpawning();
    }

    @FunctionalInterface
    public interface CancelSpawnCallback {
        /** Cancel an in-progress spawn and clean up placed blocks. */
        void cancelSpawn(ServerLevel level);
    }

    @FunctionalInterface
    public interface DespawnCallback {
        /** Immediately remove the anomaly ship from the world. */
        void despawn(ServerLevel level, AnomalyInstance anomaly);
    }

    @FunctionalInterface
    public interface PhysicsCallback {
        /**
         * Manage per-tick physics state for the anomaly island.
         * Called from game thread (ServerTickEvent.END).
         *
         * Implementation (AnomalyPhysicsHandler) does NOT apply forces directly.
         * It lazy-creates an AnomalyIslandControl (ShipPhysicsListener attachment)
         * and updates its volatile flags. Actual force calculations run on the
         * physics thread at 60 Hz via the attachment's physTick().
         */
        void applyPhysics(ServerLevel level, AnomalyInstance anomaly, boolean applyTorque, ShieldConfig.AnomalyConfig config);
    }

    @FunctionalInterface
    public interface DissolveCallback {
        /**
         * Dissolve one tick's worth of blocks. Returns true when dissolution is complete.
         * First call should prepare dissolution order if not yet prepared.
         */
        boolean tickDissolve(ServerLevel level, AnomalyInstance anomaly, ShieldConfig.AnomalyConfig config);
    }

    @FunctionalInterface
    public interface ShipExistsCallback {
        /** Check if the VS2 ship still exists in the world. */
        boolean shipExists(ServerLevel level, long shipId);
    }

    @FunctionalInterface
    public interface RepulsionCallback {
        /** Apply distance-based repulsion force to nearby non-anomaly ships. */
        void tick(ServerLevel level, AnomalyInstance anomaly);
    }

    @FunctionalInterface
    public interface GuardianCallback {
        /** Tick guardian spawning and management. */
        void tick(ServerLevel level, AnomalyInstance anomaly);
    }

    @FunctionalInterface
    public interface GuardianCleanupCallback {
        /** Kill all guardians and reset spawner state. */
        void cleanup(ServerLevel level, AnomalyInstance anomaly);
    }

    private static SpawnCallback spawnCallback;
    private static SpawnTickCallback spawnTickCallback;
    private static IsSpawningCallback isSpawningCallback;
    private static CancelSpawnCallback cancelSpawnCallback;
    private static DespawnCallback despawnCallback;
    private static PhysicsCallback physicsCallback;
    private static DissolveCallback dissolveCallback;
    private static ShipExistsCallback shipExistsCallback;
    private static RepulsionCallback repulsionCallback;
    private static GuardianCallback guardianCallback;
    private static GuardianCleanupCallback guardianCleanupCallback;

    public static void setSpawnCallback(SpawnCallback cb) { spawnCallback = cb; }
    public static void setSpawnTickCallback(SpawnTickCallback cb) { spawnTickCallback = cb; }
    public static void setIsSpawningCallback(IsSpawningCallback cb) { isSpawningCallback = cb; }
    public static void setCancelSpawnCallback(CancelSpawnCallback cb) { cancelSpawnCallback = cb; }
    public static void setDespawnCallback(DespawnCallback cb) { despawnCallback = cb; }
    public static void setPhysicsCallback(PhysicsCallback cb) { physicsCallback = cb; }
    public static void setDissolveCallback(DissolveCallback cb) { dissolveCallback = cb; }
    public static void setShipExistsCallback(ShipExistsCallback cb) { shipExistsCallback = cb; }
    public static void setRepulsionCallback(RepulsionCallback cb) { repulsionCallback = cb; }
    public static void setGuardianCallback(GuardianCallback cb) { guardianCallback = cb; }
    public static void setGuardianCleanupCallback(GuardianCleanupCallback cb) { guardianCleanupCallback = cb; }

    // --- Instance fields ---

    private MinecraftServer server;
    private AnomalySavedData savedData;
    private long currentTick;
    private long warningStartTick; // when WARNING phase began (transient, not persisted)
    private long verifyShipAtTick = -1; // deferred orphan check (VS2 ships may not be loaded on init)
    private long lastPulseTick; // last time a periodic aetheric pulse was triggered
    private VoidDepositExtraction voidDepositExtraction; // set when extraction system is ready

    private AnomalyManager() {}

    public static AnomalyManager getInstance() { return INSTANCE; }

    /**
     * Initialize on server start. Load SavedData, verify existing ship, cleanup orphans.
     */
    public void init(MinecraftServer server) {
        this.server = server;
        this.currentTick = server.overworld().getGameTime();

        ServerLevel overworld = server.overworld();
        this.savedData = AnomalySavedData.get(overworld);

        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();
        if (!config.enabled) {
            LOGGER.info("[Anomaly] System disabled in config.");
            return;
        }

        AnomalyInstance active = savedData.getActive();
        if (active != null) {
            // Trust SavedData on init — VS2 ships may not be loaded yet.
            // Defer orphan verification by 100 ticks to let VS2 finish loading.
            LOGGER.info("[Anomaly] Found saved anomaly: shipId={}, phase={}. Will verify in 5s.",
                    active.getShipId(), active.getPhase());
            if (active.getPhase() == AnomalyInstance.Phase.WARNING) {
                warningStartTick = currentTick;
            }
            verifyShipAtTick = currentTick + 100;
        }

        // Initialize spawn timer if not set
        if (savedData.getNextSpawnTick() <= 0 && savedData.getActive() == null) {
            scheduleNextSpawn();
        }

        // Initialize void deposit extraction system
        this.voidDepositExtraction = new VoidDepositExtraction();

        LOGGER.info("[Anomaly] Initialized. Active: {}, NextSpawn: {}",
                savedData.getActive() != null, savedData.getNextSpawnTick());
    }

    /**
     * Called every server tick from VSShieldsModForge.
     */
    public void tick() {
        if (server == null || savedData == null) return;

        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();
        if (!config.enabled) return;

        currentTick = server.overworld().getGameTime();

        // Deferred orphan verification (VS2 needs time to load ships after restart)
        if (verifyShipAtTick > 0 && currentTick >= verifyShipAtTick) {
            verifyShipAtTick = -1;
            AnomalyInstance pending = savedData.getActive();
            if (pending != null) {
                ServerLevel level = getDimLevel(pending.getDimensionId());
                if (level != null && shipExistsCallback != null
                        && shipExistsCallback.shipExists(level, pending.getShipId())) {
                    LOGGER.info("[Anomaly] Verified ship id={} exists after restart.", pending.getShipId());
                } else {
                    LOGGER.warn("[Anomaly] Ship id={} not found after restart — clearing orphan.", pending.getShipId());
                    savedData.setActive(null);
                    scheduleNextSpawn();
                }
            }
        }

        // Tick incremental spawn if in progress
        if (isSpawningCallback != null && isSpawningCallback.isSpawning()) {
            ServerLevel overworld = server.overworld();
            AnomalyInstance result = spawnTickCallback.tickSpawn(overworld, config);
            if (result != null) {
                savedData.setActive(result);
                savedData.setNextSpawnTick(-1);
                LOGGER.info("[Anomaly] Incremental spawn complete: shipId={}", result.getShipId());
            }
            return; // don't tick anything else while spawning
        }

        AnomalyInstance active = savedData.getActive();
        if (active != null) {
            tickActiveAnomaly(active, config);
        } else {
            tickSpawnTimer(config);
        }
    }

    /**
     * Begin spawning an anomaly at random position (admin command / auto-spawn).
     * Returns true if generation succeeded and incremental placement started.
     */
    public boolean spawnAnomaly() {
        return spawnAnomalyAt(null, null, null);
    }

    /**
     * Begin spawning an anomaly at specific position (admin command).
     * Generation happens immediately (fast); block placement is spread across ticks.
     * Returns true if started, false on failure.
     */
    public boolean spawnAnomalyAt(Double x, Double y, Double z) {
        if (server == null || spawnCallback == null) return false;

        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();
        ServerLevel overworld = server.overworld();

        // Despawn existing if any
        AnomalyInstance existing = savedData.getActive();
        if (existing != null) {
            despawnImmediate();
        }

        boolean started = spawnCallback.beginSpawn(overworld, x, y, z, config);
        if (started) {
            savedData.setNextSpawnTick(-1);
            LOGGER.info("[Anomaly] Incremental spawn started.");
        }
        return started;
    }

    /**
     * Immediately despawn the active anomaly (admin command or cleanup).
     */
    public void despawnImmediate() {
        // Cancel any in-progress incremental spawn
        if (isSpawningCallback != null && isSpawningCallback.isSpawning()) {
            ServerLevel overworld = server != null ? server.overworld() : null;
            if (cancelSpawnCallback != null) {
                cancelSpawnCallback.cancelSpawn(overworld);
            }
        }

        AnomalyInstance active = savedData != null ? savedData.getActive() : null;
        if (active != null) {
            ServerLevel level = getDimLevel(active.getDimensionId());
            if (level != null) {
                // Clean up guardians before removing the island
                if (guardianCleanupCallback != null) {
                    guardianCleanupCallback.cleanup(level, active);
                }
                if (voidDepositExtraction != null) {
                    voidDepositExtraction.clearAll();
                }
                if (despawnCallback != null) {
                    despawnCallback.despawn(level, active);
                }
            }
            savedData.setActive(null);
        }

        scheduleNextSpawn();
        LOGGER.info("[Anomaly] Despawned immediately.");
    }

    /**
     * Check if the given shipId belongs to the active anomaly.
     */
    public boolean isAnomalyShip(long shipId) {
        AnomalyInstance active = savedData != null ? savedData.getActive() : null;
        return active != null && active.getShipId() == shipId;
    }

    /**
     * Get the active anomaly instance, or null.
     */
    public AnomalyInstance getActive() {
        return savedData != null ? savedData.getActive() : null;
    }

    /**
     * Override the global TTL remaining (admin command: timer set).
     */
    public void overrideGlobalTTL(int newTotalSeconds) {
        AnomalyInstance active = savedData != null ? savedData.getActive() : null;
        if (active == null) return;
        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();
        // We want: globalLifetimeTicks - (currentTick - newSpawnTick) = newTotalSeconds * 20
        // => newSpawnTick = currentTick - globalLifetimeTicks + newTotalSeconds * 20
        long newSpawnTick = currentTick - config.globalLifetimeTicks + (long) newTotalSeconds * 20;
        active.setSpawnTick(newSpawnTick);
        savedData.setDirty();
        LOGGER.info("[Anomaly] Timer overridden: {}s remaining.", newTotalSeconds);
    }

    /**
     * Called when the server stops. Release references.
     */
    public void clear() {
        this.server = null;
        this.savedData = null;
        this.currentTick = 0;
        this.warningStartTick = 0;
        this.verifyShipAtTick = -1;
        this.lastPulseTick = 0;
        if (this.voidDepositExtraction != null) {
            this.voidDepositExtraction.clearAll();
            this.voidDepositExtraction = null;
        }
    }

    /**
     * Check if an incremental spawn is currently in progress.
     */
    public boolean isSpawning() {
        return isSpawningCallback != null && isSpawningCallback.isSpawning();
    }

    public long getCurrentTick() { return currentTick; }

    public VoidDepositExtraction getVoidDepositExtraction() { return voidDepositExtraction; }
    public void setVoidDepositExtraction(VoidDepositExtraction ext) { this.voidDepositExtraction = ext; }

    /**
     * Called externally when WARNING phase is forced (e.g. void deposit interaction).
     * Sets the transient warningStartTick and marks data dirty.
     */
    public void notifyWarningStart() {
        warningStartTick = currentTick;
        if (savedData != null) savedData.setDirty();
        LOGGER.info("[Anomaly] Destabilisation triggered! Entering WARNING phase.");
    }

    // ============================
    // Private lifecycle methods
    // ============================

    private void tickActiveAnomaly(AnomalyInstance anomaly, ShieldConfig.AnomalyConfig config) {
        ServerLevel level = getDimLevel(anomaly.getDimensionId());
        if (level == null) return;

        // Apply anti-gravity every tick (keeps island hovering)
        boolean applyTorque = (anomaly.getPhase() == AnomalyInstance.Phase.WARNING);
        if (physicsCallback != null) {
            physicsCallback.applyPhysics(level, anomaly, applyTorque, config);
        }

        // Send timer HUD + phase to nearby players every 20 ticks
        if (currentTick % 20 == 0) {
            long globalRemaining = anomaly.getGlobalTTLRemaining(currentTick, config.globalLifetimeTicks);
            int seconds = (int) (globalRemaining / 20);
            boolean timerActive = (anomaly.getPhase() == AnomalyInstance.Phase.ACTIVE
                    || anomaly.getPhase() == AnomalyInstance.Phase.EXTRACTION);
            int phaseOrdinal = anomaly.getPhase().ordinal();
            for (net.minecraft.server.level.ServerPlayer sp : level.players()) {
                double dx = sp.getX() - anomaly.getWorldX();
                double dz = sp.getZ() - anomaly.getWorldZ();
                if (dx * dx + dz * dz <= 100.0 * 100.0) {
                    com.mechanicalskies.vsshields.network.ModNetwork.sendAnomalyTimer(sp, seconds, timerActive, phaseOrdinal);
                }
            }
        }

        switch (anomaly.getPhase()) {
            case ACTIVE:
                tickActive(anomaly, config, level);
                break;
            case EXTRACTION:
                tickExtraction(anomaly, config, level);
                break;
            case WARNING:
                tickWarning(anomaly, config, level);
                break;
            case DISSOLVING:
                tickDissolving(anomaly, config, level);
                break;
        }
    }

    private void tickActive(AnomalyInstance anomaly, ShieldConfig.AnomalyConfig config, ServerLevel level) {
        // Guardian spawning
        if (guardianCallback != null && config.guardiansEnabled) {
            guardianCallback.tick(level, anomaly);
        }

        // Tick void deposit extraction
        if (voidDepositExtraction != null) {
            voidDepositExtraction.tick(level);
        }

        // Periodic aetheric pulse when players are on the island
        tickPeriodicPulse(anomaly, config, level);

        // Check if players are on/near the island every 40 ticks
        if (currentTick % 40 == 0) {
            if (hasPlayersNearIsland(anomaly, level)) {
                anomaly.startExtraction(currentTick);
                savedData.setDirty();
                LOGGER.info("[Anomaly] Extraction started — player detected near island.");
                return;
            }
        }

        // Check global TTL
        if (anomaly.isExpired(currentTick, config.globalLifetimeTicks, config.extractionTimerTicks)) {
            transitionToWarning(anomaly);
        }
    }

    private void tickExtraction(AnomalyInstance anomaly, ShieldConfig.AnomalyConfig config, ServerLevel level) {
        // Guardian spawning
        if (guardianCallback != null && config.guardiansEnabled) {
            guardianCallback.tick(level, anomaly);
        }

        // Tick void deposit extraction
        if (voidDepositExtraction != null) {
            voidDepositExtraction.tick(level);
        }

        // Periodic aetheric pulse when players are on the island
        tickPeriodicPulse(anomaly, config, level);

        // Check if either timer expired
        if (anomaly.isExpired(currentTick, config.globalLifetimeTicks, config.extractionTimerTicks)) {
            transitionToWarning(anomaly);
        }
    }

    private void tickWarning(AnomalyInstance anomaly, ShieldConfig.AnomalyConfig config, ServerLevel level) {
        // Warning phase: torque applied via physicsCallback (already done above)
        long warningElapsed = currentTick - warningStartTick;
        if (warningElapsed >= config.warningPhaseTicks) {
            anomaly.setPhase(AnomalyInstance.Phase.DISSOLVING);
            savedData.setDirty();
            // Kill guardians and clear extraction sessions on dissolution
            if (guardianCleanupCallback != null) {
                guardianCleanupCallback.cleanup(level, anomaly);
            }
            if (voidDepositExtraction != null) {
                voidDepositExtraction.clearAll();
            }
            LOGGER.info("[Anomaly] Entering DISSOLVING phase.");
        }
    }

    private void tickDissolving(AnomalyInstance anomaly, ShieldConfig.AnomalyConfig config, ServerLevel level) {
        if (dissolveCallback != null) {
            boolean complete = dissolveCallback.tickDissolve(level, anomaly, config);
            if (complete) {
                // Dissolution done — cleanup
                LOGGER.info("[Anomaly] Dissolution complete. Cleaning up ship.");
                if (despawnCallback != null) {
                    despawnCallback.despawn(level, anomaly);
                }
                savedData.setActive(null);
                scheduleNextSpawn();
            }
        }
    }

    private void transitionToWarning(AnomalyInstance anomaly) {
        anomaly.setPhase(AnomalyInstance.Phase.WARNING);
        warningStartTick = currentTick;
        savedData.setDirty();
        LOGGER.info("[Anomaly] Entering WARNING phase. Island will dissolve in {}s.",
                ShieldConfig.get().getAnomaly().warningPhaseTicks / 20);
    }

    /**
     * Fire a periodic aetheric pulse if cooldown elapsed and players are on the island.
     * Knocks back entities and damages nearby shields.
     */
    private void tickPeriodicPulse(AnomalyInstance anomaly, ShieldConfig.AnomalyConfig config, ServerLevel level) {
        if (config.pulseCooldownTicks <= 0) return;
        if (currentTick - lastPulseTick < config.pulseCooldownTicks) return;
        if (!hasPlayersNearIsland(anomaly, level)) return;

        lastPulseTick = currentTick;
        AnomalyPulseHandler.triggerPulse(level, anomaly);
    }

    /**
     * Check if any player is within the island's ship AABB (or near it).
     */
    private boolean hasPlayersNearIsland(AnomalyInstance anomaly, ServerLevel level) {
        double range = 50.0; // blocks from center
        for (var player : level.players()) {
            double dx = player.getX() - anomaly.getWorldX();
            double dy = player.getY() - anomaly.getWorldY();
            double dz = player.getZ() - anomaly.getWorldZ();
            if (dx * dx + dy * dy + dz * dz < range * range) {
                return true;
            }
        }
        return false;
    }

    private void tickSpawnTimer(ShieldConfig.AnomalyConfig config) {
        long nextSpawn = savedData.getNextSpawnTick();
        if (nextSpawn < 0) return; // not scheduled

        // Check minimum player count
        if (server.getPlayerCount() < config.minPlayersOnline) return;

        if (currentTick >= nextSpawn) {
            LOGGER.info("[Anomaly] Spawn timer elapsed. Spawning new anomaly...");
            spawnAnomaly();
        }
    }

    private void scheduleNextSpawn() {
        ShieldConfig.AnomalyConfig config = ShieldConfig.get().getAnomaly();
        int base = config.spawnIntervalTicks;
        int offset = config.spawnIntervalRandomOffsetTicks;
        long randomOffset = (offset > 0) ? (long)(Math.random() * offset) : 0;
        long nextTick = currentTick + base + randomOffset;
        savedData.setNextSpawnTick(nextTick);
        LOGGER.info("[Anomaly] Next spawn scheduled at tick {} (in ~{}s).",
                nextTick, (nextTick - currentTick) / 20);
    }

    private ServerLevel getDimLevel(String dimensionId) {
        if (server == null || dimensionId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(dimensionId)) {
                return level;
            }
        }
        return server.overworld();
    }
}
