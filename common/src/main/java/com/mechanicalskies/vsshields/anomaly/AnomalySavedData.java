package com.mechanicalskies.vsshields.anomaly;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * World-persistent data for the Aetheric Anomaly system.
 * Stores active anomaly and next spawn tick. Survives server restarts.
 */
public class AnomalySavedData extends SavedData {

    private static final String DATA_NAME = "vs_shields_anomaly";

    private AnomalyInstance active; // nullable — null means no active anomaly
    private long nextSpawnTick;

    public AnomalySavedData() {
        this.active = null;
        this.nextSpawnTick = -1;
    }

    // --- Getters ---

    public AnomalyInstance getActive() { return active; }
    public long getNextSpawnTick() { return nextSpawnTick; }

    // --- Setters (mark dirty on every mutation) ---

    public void setActive(AnomalyInstance anomaly) {
        this.active = anomaly;
        setDirty();
    }

    public void setNextSpawnTick(long tick) {
        this.nextSpawnTick = tick;
        setDirty();
    }

    // --- Serialization ---

    @Override
    @NotNull
    public CompoundTag save(@NotNull CompoundTag root) {
        root.putLong("nextSpawnTick", nextSpawnTick);
        if (active != null) {
            root.put("active", active.save());
        }
        return root;
    }

    public static AnomalySavedData load(CompoundTag root) {
        AnomalySavedData data = new AnomalySavedData();
        data.nextSpawnTick = root.getLong("nextSpawnTick");
        if (root.contains("active")) {
            data.active = AnomalyInstance.load(root.getCompound("active"));
        }
        return data;
    }

    /**
     * Get or create the AnomalySavedData for the overworld.
     */
    public static AnomalySavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                AnomalySavedData::load,
                AnomalySavedData::new,
                DATA_NAME
        );
    }
}
