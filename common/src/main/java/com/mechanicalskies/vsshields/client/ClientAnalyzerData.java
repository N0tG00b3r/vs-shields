package com.mechanicalskies.vsshields.client;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side singleton holding the most recent ship analyzer scan result.
 * Updated by {@link com.mechanicalskies.vsshields.network.ClientNetworkHandler}
 * when an ANALYZER_DATA packet arrives from the server.
 */
public class ClientAnalyzerData {
    private static final ClientAnalyzerData INSTANCE = new ClientAnalyzerData();

    public static ClientAnalyzerData getInstance() {
        return INSTANCE;
    }

    public long targetShipId = -1;
    public double shieldHP;
    public double maxShieldHP;
    public boolean shieldActive;
    public boolean shieldSolid;
    public float energyPercent;
    /**
     * JOML Matrix4dc shipped as double[16], column-major (col0row0, col0row1, ...)
     */
    public final double[] shipToWorldMatrix = new double[16];
    public final List<BlockPos> cannonPositions = new ArrayList<>();
    public final List<BlockPos> criticalPositions = new ArrayList<>();
    public final List<Integer> crewEntityIds = new ArrayList<>();
    /** World-space positions of ARMED gravitational mines targeting this ship. */
    public final List<double[]> mineWorldPositions = new ArrayList<>();
    public long lastUpdateMs = 0;

    private ClientAnalyzerData() {
    }

    /** Returns true if data is present and fresh (< 2 seconds old). */
    public boolean isValid() {
        return lastUpdateMs != 0 && (System.currentTimeMillis() - lastUpdateMs) < 2000;
    }

    public void clear() {
        targetShipId = -1;
        cannonPositions.clear();
        criticalPositions.clear();
        crewEntityIds.clear();
        mineWorldPositions.clear();
        lastUpdateMs = 0;
    }

    public void update(long shipId, double hp, double maxHp, boolean active, boolean solid, float energy,
            double[] matrix, List<BlockPos> cannons, List<BlockPos> critical,
            List<Integer> crewIds, List<double[]> mines) {
        this.targetShipId = shipId;
        this.shieldHP = hp;
        this.maxShieldHP = maxHp;
        this.shieldActive = active;
        this.shieldSolid = solid;
        this.energyPercent = energy;
        System.arraycopy(matrix, 0, this.shipToWorldMatrix, 0, 16);
        this.cannonPositions.clear();
        this.cannonPositions.addAll(cannons);
        this.criticalPositions.clear();
        this.criticalPositions.addAll(critical);
        this.crewEntityIds.clear();
        this.crewEntityIds.addAll(crewIds);
        this.mineWorldPositions.clear();
        this.mineWorldPositions.addAll(mines);
        this.lastUpdateMs = System.currentTimeMillis();
    }
}
