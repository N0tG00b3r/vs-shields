package com.mechanicalskies.vsshields.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/**
 * Generates procedural textures for shield energy layers:
 * - RD pattern: 128x128 animated Gray-Scott reaction-diffusion labyrinth (Layers 1, 2, 4)
 * - Solid fill: 4x4 opaque white (fallback)
 *
 * Animation: each tick, small regions are erased (V→0). The surrounding labyrinth
 * grows into the gaps, forming new corridor paths. Continuous erasure + regrowth
 * creates a perpetually restructuring organic pattern.
 */
public class ShieldEnergyTexture {

    /** Animated reaction-diffusion labyrinth pattern. */
    public static final ResourceLocation RD_TEXTURE_LOCATION =
            new ResourceLocation("vs_shields", "dynamic/shield_rd_pattern");

    /** Solid white fill (alpha controlled entirely by vertex color). */
    public static final ResourceLocation FILL_TEXTURE_LOCATION =
            new ResourceLocation("vs_shields", "dynamic/shield_solid_fill");

    // --- Gray-Scott RD simulation ---
    private static final int SIZE = 128;
    private static final float BASE_FEED = 0.029f;
    private static final float KILL = 0.057f;
    private static final float DU = 0.16f;
    private static final float DV = 0.08f;
    private static final float DT = 1.0f;
    private static final int STEPS_PER_TICK = 6;
    private static final int WARMUP_STEPS = 5000;
    private static final int TICK_INTERVAL = 3;  // update every 3rd client tick

    // Erosion: each active tick, erase small patches to force regrowth
    private static final int ERODE_SPOTS_PER_TICK = 3;  // patches erased per active tick
    private static final int ERODE_RADIUS = 3;           // radius of erased circle (~7px diameter)
    // Every N active ticks, erase a larger region for more dramatic rearrangement
    private static final int BIG_ERODE_INTERVAL = 20;    // every ~3 seconds (20 active ticks × 3 tick interval)
    private static final int BIG_ERODE_RADIUS = 6;       // ~13px diameter

    private static float[][] U, V;
    private static float[][] Unext, Vnext;
    private static NativeImage rdImage;
    private static DynamicTexture rdTexture;
    private static long tickCount = 0;
    private static final Random rng = new Random();

    public static void register() {
        initRD();
        stepRD(WARMUP_STEPS);
        writeRDToImage();
        rdTexture = new DynamicTexture(rdImage);
        Minecraft.getInstance().getTextureManager()
                .register(RD_TEXTURE_LOCATION, rdTexture);

        registerSolidFill();
    }

    /** Called every client tick; only updates the simulation every TICK_INTERVAL ticks. */
    public static void tick() {
        if (rdTexture == null) return;
        tickCount++;
        if (tickCount % TICK_INTERVAL != 0) return;
        erode();
        stepRD(STEPS_PER_TICK);
        writeRDToImage();
        rdTexture.upload();
    }

    private static void initRD() {
        U = new float[SIZE][SIZE];
        V = new float[SIZE][SIZE];
        Unext = new float[SIZE][SIZE];
        Vnext = new float[SIZE][SIZE];
        rdImage = new NativeImage(SIZE, SIZE, true);

        // Dense grid of seeds above critical threshold
        Random initRng = new Random(42L);
        int spacing = 12;
        int patchRadius = 2;

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                U[x][y] = 1.0f;
                V[x][y] = 0.0f;
            }
        }

        for (int gx = 0; gx < SIZE; gx += spacing) {
            for (int gy = 0; gy < SIZE; gy += spacing) {
                int cx = (gx + initRng.nextInt(spacing / 2)) % SIZE;
                int cy = (gy + initRng.nextInt(spacing / 2)) % SIZE;
                for (int dx = -patchRadius; dx <= patchRadius; dx++) {
                    for (int dy = -patchRadius; dy <= patchRadius; dy++) {
                        int sx = (cx + dx + SIZE) % SIZE;
                        int sy = (cy + dy + SIZE) % SIZE;
                        U[sx][sy] = 0.5f;
                        V[sx][sy] = 0.25f;
                    }
                }
            }
        }
    }

    /**
     * Erase small circular patches — sets V to 0 (empty substrate).
     * The surrounding labyrinth corridors will grow into these gaps,
     * forming new paths different from the original.
     */
    private static void erode() {
        // Small erosions every tick
        for (int i = 0; i < ERODE_SPOTS_PER_TICK; i++) {
            erodeCircle(rng.nextInt(SIZE), rng.nextInt(SIZE), ERODE_RADIUS);
        }
        // Larger erosion periodically for more dramatic restructuring
        if (tickCount % BIG_ERODE_INTERVAL == 0) {
            erodeCircle(rng.nextInt(SIZE), rng.nextInt(SIZE), BIG_ERODE_RADIUS);
        }
    }

    private static void erodeCircle(int cx, int cy, int radius) {
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= r2) {
                    int sx = (cx + dx + SIZE) % SIZE;
                    int sy = (cy + dy + SIZE) % SIZE;
                    U[sx][sy] = 1.0f;
                    V[sx][sy] = 0.0f;
                }
            }
        }
    }

    private static void stepRD(int steps) {
        float feedKill = BASE_FEED + KILL;
        for (int s = 0; s < steps; s++) {
            for (int x = 0; x < SIZE; x++) {
                int xp = (x + 1) % SIZE;
                int xm = (x - 1 + SIZE) % SIZE;
                for (int y = 0; y < SIZE; y++) {
                    int yp = (y + 1) % SIZE;
                    int ym = (y - 1 + SIZE) % SIZE;

                    float u = U[x][y];
                    float v = V[x][y];

                    float lapU = U[xp][y] + U[xm][y] + U[x][yp] + U[x][ym] - 4f * u;
                    float lapV = V[xp][y] + V[xm][y] + V[x][yp] + V[x][ym] - 4f * v;

                    float uvv = u * v * v;
                    Unext[x][y] = Math.max(0f, Math.min(1f, u + DT * (DU * lapU - uvv + BASE_FEED * (1f - u))));
                    Vnext[x][y] = Math.max(0f, Math.min(1f, v + DT * (DV * lapV + uvv - feedKill * v)));
                }
            }
            float[][] tmp;
            tmp = U; U = Unext; Unext = tmp;
            tmp = V; V = Vnext; Vnext = tmp;
        }
    }

    private static void writeRDToImage() {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                float v = V[x][y];
                float brightness = smoothstep(0.08f, 0.35f, v);
                int a = (int) (brightness * 220);
                // NativeImage pixel format: ABGR
                int pixel = (a << 24) | (0xFF << 16) | (0xFF << 8) | 0xFF;
                rdImage.setPixelRGBA(x, y, pixel);
            }
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private static void registerSolidFill() {
        NativeImage image = new NativeImage(4, 4, true);
        int pixel = (0xFF << 24) | (0xFF << 16) | (0xFF << 8) | 0xFF;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.setPixelRGBA(x, y, pixel);
            }
        }
        Minecraft.getInstance().getTextureManager()
                .register(FILL_TEXTURE_LOCATION, new DynamicTexture(image));
    }
}
