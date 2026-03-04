package com.mechanicalskies.vsshields.anomaly;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class representing a single active Aetheric Anomaly (floating island as a VS2 ship).
 * Persisted via NBT through AnomalySavedData.
 */
public class AnomalyInstance {

    public enum Phase {
        ACTIVE,
        EXTRACTION,
        WARNING,
        DISSOLVING
    }

    private long shipId;
    private double worldX, worldY, worldZ;
    private volatile Phase phase;
    private long spawnTick;
    private long extractionStartTick; // -1 until triggered
    private int totalBlocks;
    private volatile int dissolvedBlocks;
    private String dimensionId;

    // Transient — recomputed on load, not persisted
    private transient List<BlockPos> dissolutionOrder;

    public AnomalyInstance(long shipId, double worldX, double worldY, double worldZ,
                           long spawnTick, int totalBlocks, String dimensionId) {
        this.shipId = shipId;
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.phase = Phase.ACTIVE;
        this.spawnTick = spawnTick;
        this.extractionStartTick = -1;
        this.totalBlocks = totalBlocks;
        this.dissolvedBlocks = 0;
        this.dimensionId = dimensionId;
        this.dissolutionOrder = new ArrayList<>();
    }

    private AnomalyInstance() {
        this.dissolutionOrder = new ArrayList<>();
    }

    // --- Getters ---

    public long getShipId() { return shipId; }
    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public double getWorldZ() { return worldZ; }
    public Phase getPhase() { return phase; }
    public long getSpawnTick() { return spawnTick; }
    public long getExtractionStartTick() { return extractionStartTick; }
    public int getTotalBlocks() { return totalBlocks; }
    public int getDissolvedBlocks() { return dissolvedBlocks; }
    public String getDimensionId() { return dimensionId; }
    public List<BlockPos> getDissolutionOrder() { return dissolutionOrder; }

    // --- Setters ---

    public void setPhase(Phase phase) { this.phase = phase; }
    public void setDissolvedBlocks(int count) { this.dissolvedBlocks = count; }
    public void setDissolutionOrder(List<BlockPos> order) { this.dissolutionOrder = order; }
    public void setSpawnTick(long tick) { this.spawnTick = tick; }

    public void startExtraction(long currentTick) {
        if (extractionStartTick < 0) {
            extractionStartTick = currentTick;
            phase = Phase.EXTRACTION;
        }
    }

    /**
     * Returns remaining ticks of global TTL, or 0 if expired.
     */
    public long getGlobalTTLRemaining(long currentTick, int globalLifetimeTicks) {
        long elapsed = currentTick - spawnTick;
        return Math.max(0, globalLifetimeTicks - elapsed);
    }

    /**
     * Returns remaining ticks of extraction timer, or -1 if not started.
     */
    public long getExtractionRemaining(long currentTick, int extractionTimerTicks) {
        if (extractionStartTick < 0) return -1;
        long elapsed = currentTick - extractionStartTick;
        return Math.max(0, extractionTimerTicks - elapsed);
    }

    /**
     * Check if either timer has expired (should transition to WARNING).
     */
    public boolean isExpired(long currentTick, int globalLifetimeTicks, int extractionTimerTicks) {
        if (getGlobalTTLRemaining(currentTick, globalLifetimeTicks) <= 0) return true;
        if (extractionStartTick >= 0 && getExtractionRemaining(currentTick, extractionTimerTicks) <= 0) return true;
        return false;
    }

    // --- NBT Serialization ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("shipId", shipId);
        tag.putDouble("worldX", worldX);
        tag.putDouble("worldY", worldY);
        tag.putDouble("worldZ", worldZ);
        tag.putString("phase", phase.name());
        tag.putLong("spawnTick", spawnTick);
        tag.putLong("extractionStartTick", extractionStartTick);
        tag.putInt("totalBlocks", totalBlocks);
        tag.putInt("dissolvedBlocks", dissolvedBlocks);
        tag.putString("dimensionId", dimensionId);
        return tag;
    }

    public static AnomalyInstance load(CompoundTag tag) {
        AnomalyInstance inst = new AnomalyInstance();
        inst.shipId = tag.getLong("shipId");
        inst.worldX = tag.getDouble("worldX");
        inst.worldY = tag.getDouble("worldY");
        inst.worldZ = tag.getDouble("worldZ");
        try {
            inst.phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            inst.phase = Phase.ACTIVE;
        }
        inst.spawnTick = tag.getLong("spawnTick");
        inst.extractionStartTick = tag.getLong("extractionStartTick");
        inst.totalBlocks = tag.getInt("totalBlocks");
        inst.dissolvedBlocks = tag.getInt("dissolvedBlocks");
        inst.dimensionId = tag.getString("dimensionId");
        return inst;
    }
}
