package com.mechanicalskies.vsshields.client;

/**
 * Precomputed truncated icosahedron mesh projected onto a unit sphere grid.
 * <p>
 * Uses a stacks×slices spherical grid but projects each vertex onto the nearest
 * face of a truncated icosahedron, creating flat faceted panels (12 pentagons + 20 hexagons).
 * <p>
 * Static arrays are computed once at class load. ShieldRenderer reads them each frame.
 */
public class TruncatedIcosahedronMesh {

    public static final int STACKS = 20;
    public static final int SLICES = 32;

    /** Projected vertex positions [stack][slice] (unit-sphere scale). */
    public static final float[][] PX = new float[STACKS + 1][SLICES];
    public static final float[][] PY = new float[STACKS + 1][SLICES];
    public static final float[][] PZ = new float[STACKS + 1][SLICES];

    /** Flat face normals [stack][slice] — same for all vertices on a face. */
    public static final float[][] FNX = new float[STACKS + 1][SLICES];
    public static final float[][] FNY = new float[STACKS + 1][SLICES];
    public static final float[][] FNZ = new float[STACKS + 1][SLICES];

    /** Edge factor [stack][slice]: 0 = face center, 1 = face edge. */
    public static final float[][] EDGE_FACTOR = new float[STACKS + 1][SLICES];

    /** Face index (0–31) per grid vertex — which truncated ico face this vertex belongs to. */
    public static final int[][] FACE_INDEX = new int[STACKS + 1][SLICES];

    /** Face adjacency graph: ADJACENCY[face] = array of neighbor face indices. */
    public static final int[][] ADJACENCY = new int[32][];

    // Golden ratio
    private static final double PHI = (1.0 + Math.sqrt(5.0)) / 2.0;

    // 12 icosahedron vertices (normalized) → pentagon face centroids
    private static final double[][] ICO_VERTS = new double[12][3];

    // 20 icosahedron face centers (normalized) → hexagon face centroids
    private static final double[][] ICO_FACES = new double[20][3];

    // All 32 face centroids + plane distances
    private static final double[][] CENTROIDS = new double[32][3];
    private static final double[] PLANE_DIST = new double[32];
    private static final double[] FACE_RADIUS = new double[32];

    static {
        computeIcosahedronVertices();
        computeIcosahedronFaceCenters();
        computeTruncatedIcoFaces();
        projectGrid();
        computeAdjacency();
    }

    /** Returns the 32 face centroids (normalized directions). [face][xyz]. */
    public static double[][] getCentroids() {
        return CENTROIDS;
    }

    private static void computeIcosahedronVertices() {
        // 12 vertices of a regular icosahedron: (0, ±1, ±φ) and cyclic permutations
        double[][] raw = {
            { 0,  1,  PHI}, { 0,  1, -PHI}, { 0, -1,  PHI}, { 0, -1, -PHI},
            { 1,  PHI, 0}, { 1, -PHI, 0}, {-1,  PHI, 0}, {-1, -PHI, 0},
            { PHI, 0,  1}, { PHI, 0, -1}, {-PHI, 0,  1}, {-PHI, 0, -1}
        };
        for (int i = 0; i < 12; i++) {
            double len = Math.sqrt(raw[i][0]*raw[i][0] + raw[i][1]*raw[i][1] + raw[i][2]*raw[i][2]);
            ICO_VERTS[i][0] = raw[i][0] / len;
            ICO_VERTS[i][1] = raw[i][1] / len;
            ICO_VERTS[i][2] = raw[i][2] / len;
        }
    }

    private static void computeIcosahedronFaceCenters() {
        // 20 triangular faces of the icosahedron — indices into ICO_VERTS
        int[][] faces = {
            {0,2,8}, {0,8,4}, {0,4,6}, {0,6,10}, {0,10,2},
            {3,1,9}, {3,9,5}, {3,5,7}, {3,7,11}, {3,11,1},
            {2,5,8}, {8,5,9}, {8,9,4}, {4,9,1}, {4,1,6},
            {6,1,11}, {6,11,10}, {10,11,7}, {10,7,2}, {2,7,5}
        };
        for (int i = 0; i < 20; i++) {
            double cx = (ICO_VERTS[faces[i][0]][0] + ICO_VERTS[faces[i][1]][0] + ICO_VERTS[faces[i][2]][0]) / 3.0;
            double cy = (ICO_VERTS[faces[i][0]][1] + ICO_VERTS[faces[i][1]][1] + ICO_VERTS[faces[i][2]][1]) / 3.0;
            double cz = (ICO_VERTS[faces[i][0]][2] + ICO_VERTS[faces[i][1]][2] + ICO_VERTS[faces[i][2]][2]) / 3.0;
            double len = Math.sqrt(cx*cx + cy*cy + cz*cz);
            ICO_FACES[i][0] = cx / len;
            ICO_FACES[i][1] = cy / len;
            ICO_FACES[i][2] = cz / len;
        }
    }

    private static void computeTruncatedIcoFaces() {
        // Pentagon centroids = icosahedron vertices (12)
        for (int i = 0; i < 12; i++) {
            CENTROIDS[i][0] = ICO_VERTS[i][0];
            CENTROIDS[i][1] = ICO_VERTS[i][1];
            CENTROIDS[i][2] = ICO_VERTS[i][2];
        }
        // Hexagon centroids = icosahedron face centers (20)
        for (int i = 0; i < 20; i++) {
            CENTROIDS[12 + i][0] = ICO_FACES[i][0];
            CENTROIDS[12 + i][1] = ICO_FACES[i][1];
            CENTROIDS[12 + i][2] = ICO_FACES[i][2];
        }

        // Compute plane distances for a truncated icosahedron inscribed in a unit sphere.
        // Pentagon faces are slightly further from center than hexagon faces.
        // For a truncated icosahedron with circumradius 1:
        //   pentagon plane dist ≈ 0.887
        //   hexagon plane dist ≈ 0.934
        // These are the distances from center to each face plane along the centroid direction.
        double pentDist = 0.887;
        double hexDist = 0.934;

        for (int i = 0; i < 12; i++) {
            PLANE_DIST[i] = pentDist;
        }
        for (int i = 0; i < 20; i++) {
            PLANE_DIST[12 + i] = hexDist;
        }

        // Face radii (max distance from centroid to any point on the face, projected)
        // Pentagon inscribed radius ≈ 0.31, hexagon ≈ 0.28 (relative to circumradius 1)
        for (int i = 0; i < 12; i++) {
            FACE_RADIUS[i] = 0.31;
        }
        for (int i = 0; i < 20; i++) {
            FACE_RADIUS[12 + i] = 0.28;
        }
    }

    private static void projectGrid() {
        for (int stack = 0; stack <= STACKS; stack++) {
            double phi = Math.PI * stack / STACKS;
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);

            for (int slice = 0; slice < SLICES; slice++) {
                double theta = 2.0 * Math.PI * slice / SLICES;
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);

                // Direction on unit sphere
                float dx = sinPhi * cosTheta;
                float dy = cosPhi;
                float dz = sinPhi * sinTheta;

                // Find nearest face centroid (max dot product)
                int bestFace = 0;
                double bestDot = -2.0;
                for (int f = 0; f < 32; f++) {
                    double dot = dx * CENTROIDS[f][0] + dy * CENTROIDS[f][1] + dz * CENTROIDS[f][2];
                    if (dot > bestDot) {
                        bestDot = dot;
                        bestFace = f;
                    }
                }

                FACE_INDEX[stack][slice] = bestFace;

                // Face normal (= centroid direction, already normalized)
                double fnx = CENTROIDS[bestFace][0];
                double fny = CENTROIDS[bestFace][1];
                double fnz = CENTROIDS[bestFace][2];

                FNX[stack][slice] = (float) fnx;
                FNY[stack][slice] = (float) fny;
                FNZ[stack][slice] = (float) fnz;

                // Ray-plane intersection: t = planeDist / (dir · normal)
                double dirDotN = dx * fnx + dy * fny + dz * fnz;
                if (dirDotN < 0.001) {
                    // Fallback: keep on sphere
                    PX[stack][slice] = dx;
                    PY[stack][slice] = dy;
                    PZ[stack][slice] = dz;
                    EDGE_FACTOR[stack][slice] = 0.5f;
                    continue;
                }

                double t = PLANE_DIST[bestFace] / dirDotN;
                float px = (float) (dx * t);
                float py = (float) (dy * t);
                float pz = (float) (dz * t);

                PX[stack][slice] = px;
                PY[stack][slice] = py;
                PZ[stack][slice] = pz;

                // Edge factor: distance from face centroid (projected onto face plane)
                double centX = fnx * PLANE_DIST[bestFace];
                double centY = fny * PLANE_DIST[bestFace];
                double centZ = fnz * PLANE_DIST[bestFace];
                double distX = px - centX;
                double distY = py - centY;
                double distZ = pz - centZ;
                double distFromCenter = Math.sqrt(distX*distX + distY*distY + distZ*distZ);

                float edge = (float) Math.min(1.0, distFromCenter / FACE_RADIUS[bestFace]);
                EDGE_FACTOR[stack][slice] = edge;
            }
        }
    }

    private static void computeAdjacency() {
        // Two faces are adjacent if their centroids are within a threshold angular distance.
        // For a truncated icosahedron: adjacent faces have dot(c_i, c_j) > ~0.75
        double threshold = 0.75;

        for (int i = 0; i < 32; i++) {
            int count = 0;
            for (int j = 0; j < 32; j++) {
                if (i == j) continue;
                double dot = CENTROIDS[i][0] * CENTROIDS[j][0]
                           + CENTROIDS[i][1] * CENTROIDS[j][1]
                           + CENTROIDS[i][2] * CENTROIDS[j][2];
                if (dot > threshold) count++;
            }
            ADJACENCY[i] = new int[count];
            int idx = 0;
            for (int j = 0; j < 32; j++) {
                if (i == j) continue;
                double dot = CENTROIDS[i][0] * CENTROIDS[j][0]
                           + CENTROIDS[i][1] * CENTROIDS[j][1]
                           + CENTROIDS[i][2] * CENTROIDS[j][2];
                if (dot > threshold) {
                    ADJACENCY[i][idx++] = j;
                }
            }
        }
    }
}
