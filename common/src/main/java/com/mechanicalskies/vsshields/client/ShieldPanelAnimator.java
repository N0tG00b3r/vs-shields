package com.mechanicalskies.vsshields.client;

import com.mechanicalskies.vsshields.network.ClientShieldManager;

/**
 * Per-shield animation state for the 32 truncated icosahedron panels.
 * Updated each frame by ShieldRenderer, driven by hit events and HP state.
 *
 * Animations:
 * - H1: Hit flash with ripple to neighbor faces
 * - A1: Activation wave from generator direction
 * - L2: Low HP panel dropout (stable dead + flickering unstable)
 * - R1+R3: Recharge edge glow
 */
public class ShieldPanelAnimator {

    private static final int FACE_COUNT = 32;

    // --- H1: Hit flash ---
    private final float[] hitFlash = new float[FACE_COUNT];
    private final float[] hitFlashTemp = new float[FACE_COUNT]; // temp for propagation

    // --- A1: Activation wave ---
    private boolean activating = false;
    private float activationProgress = 0f;
    private final float[] faceActivationOrder = new float[FACE_COUNT];
    private final float[] faceActivated = new float[FACE_COUNT];

    // --- L2: Low HP dropout ---
    private final float[] faceAlive = new float[FACE_COUNT];
    private long lastDropoutRehash = 0;
    private int rehashSeed = 0;
    // Indices of unstable faces for per-frame flicker
    private final boolean[] faceUnstable = new boolean[FACE_COUNT];

    // --- R3: Recharge edge glow ---
    private float rechargeEdgeBoost = 0f;
    private double lastHP = -1;

    // --- State tracking ---
    public boolean wasActive = false;
    private boolean firstTick = true;

    public ShieldPanelAnimator() {
        java.util.Arrays.fill(faceAlive, 1.0f);
        java.util.Arrays.fill(faceActivated, 1.0f);
    }

    /**
     * Called each frame from ShieldRenderer.
     * Activation wave detection is handled here (not in renderer) to support firstTick skip.
     */
    public void tick(double hpPercent, boolean active, double currentHP,
                     float genDirX, float genDirY, float genDirZ) {
        if (firstTick) {
            firstTick = false;
            wasActive = active; // Match current state on login — don't trigger activation
        }

        // Detect real off→on transition (skipped on first tick via wasActive match above)
        if (active && !wasActive) {
            startActivation(genDirX, genDirY, genDirZ);
        }

        tickHitFlash();
        tickActivation(active);
        tickDropout(hpPercent);
        tickRechargeGlow(hpPercent, currentHP);
        wasActive = active;
    }

    // ===================== H1: Hit Flash =====================

    /**
     * Called from ShieldEffectHandler when a hit packet arrives.
     * Converts world-space hit position to nearest face.
     */
    public void onHit(double hitX, double hitY, double hitZ, ClientShieldManager.ClientShieldData data) {
        // Convert world hit to unit-sphere direction in shield ellipsoid space
        double cx = (data.worldMinX + data.worldMaxX) / 2.0;
        double cy = (data.worldMinY + data.worldMaxY) / 2.0;
        double cz = (data.worldMinZ + data.worldMaxZ) / 2.0;
        double hx = (data.worldMaxX - data.worldMinX) / 2.0;
        double hy = (data.worldMaxY - data.worldMinY) / 2.0;
        double hz = (data.worldMaxZ - data.worldMinZ) / 2.0;

        if (hx < 0.01 || hy < 0.01 || hz < 0.01) return;

        double dx = (hitX - cx) / hx;
        double dy = (hitY - cy) / hy;
        double dz = (hitZ - cz) / hz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.001) return;
        dx /= len;
        dy /= len;
        dz /= len;

        // Find nearest face centroid
        double[][] centroids = TruncatedIcosahedronMesh.getCentroids();
        int bestFace = 0;
        double bestDot = -2.0;
        for (int f = 0; f < FACE_COUNT; f++) {
            double dot = dx * centroids[f][0] + dy * centroids[f][1] + dz * centroids[f][2];
            if (dot > bestDot) {
                bestDot = dot;
                bestFace = f;
            }
        }

        hitFlash[bestFace] = 1.0f;
    }

    private void tickHitFlash() {
        // Propagate to neighbors first (using temp to avoid read-during-write)
        System.arraycopy(hitFlash, 0, hitFlashTemp, 0, FACE_COUNT);

        for (int f = 0; f < FACE_COUNT; f++) {
            if (hitFlashTemp[f] > 0.25f) {
                int[] neighbors = TruncatedIcosahedronMesh.ADJACENCY[f];
                for (int n : neighbors) {
                    float propagated = hitFlashTemp[f] * 0.55f;
                    if (propagated > hitFlash[n]) {
                        hitFlash[n] = propagated;
                    }
                }
            }
        }

        // Decay
        for (int f = 0; f < FACE_COUNT; f++) {
            hitFlash[f] *= 0.82f;
            if (hitFlash[f] < 0.01f) hitFlash[f] = 0f;
        }
    }

    // ===================== A1: Activation Wave =====================

    /**
     * Start the activation wave. genDir = direction from ship center to generator (shipyard space, normalized).
     */
    public void startActivation(float genDirX, float genDirY, float genDirZ) {
        activating = true;
        activationProgress = 0f;

        double[][] centroids = TruncatedIcosahedronMesh.getCentroids();
        for (int f = 0; f < FACE_COUNT; f++) {
            double dot = centroids[f][0] * genDirX + centroids[f][1] * genDirY + centroids[f][2] * genDirZ;
            // Faces near generator → order ~0 (first), opposite → order ~1 (last)
            faceActivationOrder[f] = (float) (0.5 - 0.5 * dot);
            faceActivated[f] = 0f;
        }
    }

    private void tickActivation(boolean active) {
        if (!activating) return;

        activationProgress += 0.015f; // ~67 frames = ~1.1 seconds

        for (int f = 0; f < FACE_COUNT; f++) {
            faceActivated[f] = smoothstep(
                    faceActivationOrder[f] - 0.1f,
                    faceActivationOrder[f] + 0.1f,
                    activationProgress);
        }

        if (activationProgress >= 1.2f) {
            activating = false;
            java.util.Arrays.fill(faceActivated, 1.0f);
        }
    }

    // ===================== L2: Low HP Dropout =====================

    private void tickDropout(double hpPercent) {
        // No dropout above 80% HP
        if (hpPercent > 0.80) {
            java.util.Arrays.fill(faceAlive, 1.0f);
            java.util.Arrays.fill(faceUnstable, false);
            return;
        }

        long now = System.currentTimeMillis();

        // Rehash every ~2 seconds — which faces are dead/unstable/alive
        if (now - lastDropoutRehash > 2000) {
            lastDropoutRehash = now;
            rehashSeed++;

            float deadThreshold = (float) ((1.0 - hpPercent) * 0.75);
            float flickerThreshold = deadThreshold + 0.15f;

            for (int f = 0; f < FACE_COUNT; f++) {
                float hash = fract((float) (Math.sin(f * 127.1 + rehashSeed * 311.7) * 43758.5));

                if (hash < deadThreshold) {
                    faceAlive[f] = 0.05f; // dead
                    faceUnstable[f] = false;
                } else if (hash < flickerThreshold) {
                    faceUnstable[f] = true; // will flicker each frame
                } else {
                    faceAlive[f] = 1.0f; // alive
                    faceUnstable[f] = false;
                }
            }
        }

        // Per-frame flicker for unstable faces
        float time = (now % 10000) / 1000f;
        for (int f = 0; f < FACE_COUNT; f++) {
            if (faceUnstable[f]) {
                float flicker = (float) (Math.sin(time * 47.3 + f * 13.7) * 0.5 + 0.5);
                faceAlive[f] = 0.3f + 0.7f * flicker;
            }
        }
    }

    // ===================== R3: Recharge Edge Glow =====================

    private void tickRechargeGlow(double hpPercent, double currentHP) {
        if (lastHP >= 0 && currentHP > lastHP && hpPercent < 0.95) {
            // HP increasing → recharging
            rechargeEdgeBoost = Math.min(0.5f, rechargeEdgeBoost + 0.02f);
        } else {
            rechargeEdgeBoost *= 0.95f;
            if (rechargeEdgeBoost < 0.01f) rechargeEdgeBoost = 0f;
        }
        lastHP = currentHP;
    }

    // ===================== Getters =====================

    /**
     * Returns combined alpha multiplier for a face (0–1+).
     * Incorporates: dropout, activation wave, hit flash.
     */
    public float getFaceMultiplier(int faceIndex) {
        if (faceIndex < 0 || faceIndex >= FACE_COUNT) return 1.0f;

        float m = faceAlive[faceIndex];

        // Activation wave (only during activation)
        if (activating) {
            m *= faceActivated[faceIndex];
        }

        // Hit flash overrides — additive, can push above normal
        float flash = hitFlash[faceIndex];
        if (flash > m) {
            m = flash;
        }

        return m;
    }

    /** Returns edge brightness boost during recharge (0–0.5). */
    public float getRechargeEdgeBoost() {
        return rechargeEdgeBoost;
    }

    // ===================== Util =====================

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0 + 0.0001f)));
        return t * t * (3f - 2f * t);
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }
}
