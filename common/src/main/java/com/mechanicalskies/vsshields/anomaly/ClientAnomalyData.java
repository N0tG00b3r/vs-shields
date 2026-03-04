package com.mechanicalskies.vsshields.anomaly;

/**
 * Client-side singleton holding current anomaly data.
 * Updated by S2C packets (ANOMALY_SPAWN / ANOMALY_DESPAWN).
 * Used by compass in Phase 4.
 */
public class ClientAnomalyData {

    private static long shipId;
    private static double worldX, worldY, worldZ;
    private static boolean exists;

    // Extraction progress (0..1), active flag
    private static float extractionProgress;
    private static boolean extractionActive;

    // Pulse visual: tick counter for screen shake
    private static int pulseShakeTicks;

    // Timer HUD data (from ANOMALY_TIMER packet)
    private static int timerSeconds;
    private static boolean timerActive;

    // Spawn beam visual: game tick when beam ends (0 = inactive)
    private static long spawnBeamEndTick;

    // Anomaly phase (ordinal from AnomalyInstance.Phase, -1 = unknown)
    private static int anomalyPhase = -1;

    private ClientAnomalyData() {}

    public static void update(long shipId, double x, double y, double z) {
        ClientAnomalyData.shipId = shipId;
        ClientAnomalyData.worldX = x;
        ClientAnomalyData.worldY = y;
        ClientAnomalyData.worldZ = z;
        ClientAnomalyData.exists = true;
        // Start spawn beam (30 seconds = 600 ticks from now)
        spawnBeamEndTick = -1; // will be resolved to gameTick + 600 on first particle tick
    }

    public static void clear() {
        shipId = 0;
        worldX = 0;
        worldY = 0;
        worldZ = 0;
        exists = false;
    }

    public static long getShipId() { return shipId; }
    public static double getWorldX() { return worldX; }
    public static double getWorldY() { return worldY; }
    public static double getWorldZ() { return worldZ; }
    public static boolean exists() { return exists; }

    // Extraction progress
    public static void setExtractionProgress(float progress, boolean active) {
        extractionProgress = progress;
        extractionActive = active;
    }

    public static float getExtractionProgress() { return extractionProgress; }
    public static boolean isExtractionActive() { return extractionActive; }

    // Pulse visual
    public static void onPulse(double x, double y, double z, double radius) {
        pulseShakeTicks = 20; // 1 second of screen shake
    }

    public static int getPulseShakeTicks() { return pulseShakeTicks; }
    public static void tickPulseShake() {
        if (pulseShakeTicks > 0) pulseShakeTicks--;
    }

    // Timer HUD
    public static void setTimerData(int seconds, boolean active, int phaseOrdinal) {
        timerSeconds = seconds;
        timerActive = active;
        anomalyPhase = phaseOrdinal;
    }

    public static int getTimerSeconds() { return timerSeconds; }
    public static boolean isTimerActive() { return timerActive; }
    public static int getAnomalyPhase() { return anomalyPhase; }
    /** Phase constants matching AnomalyInstance.Phase ordinals */
    public static final int PHASE_ACTIVE = 0;
    public static final int PHASE_EXTRACTION = 1;
    public static final int PHASE_WARNING = 2;
    public static final int PHASE_DISSOLVING = 3;

    // Spawn beam
    public static long getSpawnBeamEndTick() { return spawnBeamEndTick; }
    public static void setSpawnBeamEndTick(long tick) { spawnBeamEndTick = tick; }
    public static boolean isSpawnBeamActive(long currentTick) {
        return spawnBeamEndTick != 0 && (spawnBeamEndTick == -1 || currentTick < spawnBeamEndTick);
    }
}
