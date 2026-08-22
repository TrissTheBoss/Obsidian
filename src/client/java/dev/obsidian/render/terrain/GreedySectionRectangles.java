package dev.obsidian.render.terrain;

import java.util.Arrays;

/**
 * P3.3 correctness-first topology rectangle sidecar over the proven P3.2
 * {@link BinarySectionVisibility} masks.
 *
 * <p>This class deliberately does not emit GPU geometry and does not consume or
 * reinterpret generalized baked-model quads. It partitions only the canonical
 * conservative full-cube visibility faces into deterministic same-direction
 * rectangles. {@link BakedSectionMesh} remains the production drawable until a
 * later milestone supplies the complete render-correct merge key.</p>
 */
public final class GreedySectionRectangles {
    public static final int BYTES_PER_RECTANGLE = Integer.BYTES;
    public static final int MAX_RECTANGLES = ReferenceFaceMesh.MAX_FACES;
    public static final int MAX_RETAINED_BYTES = MAX_RECTANGLES * BYTES_PER_RECTANGLE;

    private static final int SIZE = SectionSnapshot.INTERIOR_SIZE;
    private static final int ROW_MASK = (1 << SIZE) - 1;

    private static final int U_SHIFT = 0;
    private static final int V_SHIFT = 4;
    private static final int WIDTH_SHIFT = 8;
    private static final int HEIGHT_SHIFT = 12;
    private static final int PLANE_SHIFT = 16;
    private static final int DIRECTION_SHIFT = 20;
    private static final int NIBBLE_MASK = 0xF;
    private static final int DIRECTION_MASK = 0x7;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Fixed worker-local primitive scratch reused across jobs and audits. */
    public static final class BuildScratch {
        private final int[] records = new int[MAX_RECTANGLES];
        private final int[] planeRows = new int[SIZE];
        private final long[] coverageWords = new long[BinarySectionVisibility.TOTAL_WORDS];
        private final int[] directionAreas = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private long uses;
        private int highWaterRectangles;

        private void beginBuild() {
            uses++;
        }

        private void observeRectangleCount(int rectangles) {
            highWaterRectangles = Math.max(highWaterRectangles, rectangles);
        }

        private void clearCoverage() {
            Arrays.fill(coverageWords, 0L);
            Arrays.fill(directionAreas, 0);
        }

        public long uses() { return uses; }
        public int highWaterRectangles() { return highWaterRectangles; }
        public int retainedScratchBytes() {
            return records.length * Integer.BYTES
                    + planeRows.length * Integer.BYTES
                    + coverageWords.length * Long.BYTES
                    + directionAreas.length * Integer.BYTES;
        }
    }

    private final int[] records;
    private final int[] directionRectangleCounts;
    private final int[] directionCoveredFaces;
    private final int rectangleCount;
    private final int coveredFaceCount;
    private final long sourceVisibilityFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private GreedySectionRectangles(
            int[] records,
            int[] directionRectangleCounts,
            int[] directionCoveredFaces,
            int rectangleCount,
            int coveredFaceCount,
            long sourceVisibilityFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.records = records;
        this.directionRectangleCounts = directionRectangleCounts;
        this.directionCoveredFaces = directionCoveredFaces;
        this.rectangleCount = rectangleCount;
        this.coveredFaceCount = coveredFaceCount;
        this.sourceVisibilityFingerprint = sourceVisibilityFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static GreedySectionRectangles build(BinarySectionVisibility visibility) {
        return build(visibility, new BuildScratch());
    }

    public static GreedySectionRectangles build(
            BinarySectionVisibility visibility,
            BuildScratch scratch) {
        if (visibility == null || scratch == null) {
            throw new NullPointerException("visibility and build scratch are required");
        }

        long startNs = System.nanoTime();
        scratch.beginBuild();
        int out = 0;
        int coveredFaces = 0;
        int[] rectangleCounts = new int[BinarySectionVisibility.DIRECTION_COUNT];
        int[] coveredByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];

        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            for (int plane = 0; plane < SIZE; plane++) {
                fillPlaneRows(visibility, direction, plane, scratch.planeRows);
                for (int v = 0; v < SIZE; v++) {
                    while ((scratch.planeRows[v] & ROW_MASK) != 0) {
                        int rowBits = scratch.planeRows[v] & ROW_MASK;
                        int u = Integer.numberOfTrailingZeros(rowBits);
                        int shifted = rowBits >>> u;
                        int width = 0;
                        while (width < SIZE - u && (shifted & 1) != 0) {
                            width++;
                            shifted >>>= 1;
                        }
                        int runMask = ((1 << width) - 1) << u;
                        int height = 1;
                        while (v + height < SIZE
                                && (scratch.planeRows[v + height] & runMask) == runMask) {
                            height++;
                        }

                        if (out >= MAX_RECTANGLES) {
                            throw new IllegalStateException("Greedy rectangle output exceeded bounded maximum");
                        }
                        scratch.records[out++] = pack(direction, plane, u, v, width, height);
                        rectangleCounts[direction]++;
                        int area = width * height;
                        coveredByDirection[direction] += area;
                        coveredFaces += area;

                        int clearMask = ~runMask;
                        for (int row = v; row < v + height; row++) {
                            scratch.planeRows[row] &= clearMask;
                        }
                    }
                }
            }
        }

        scratch.observeRectangleCount(out);
        int[] retained = Arrays.copyOf(scratch.records, out);
        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, visibility.fingerprint());
        hash = hashInt(hash, out);
        hash = hashInt(hash, coveredFaces);
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            hash = hashInt(hash, rectangleCounts[direction]);
            hash = hashInt(hash, coveredByDirection[direction]);
        }
        for (int record : retained) hash = hashInt(hash, record);

        GreedySectionRectangles result = new GreedySectionRectangles(
                retained,
                rectangleCounts,
                coveredByDirection,
                out,
                coveredFaces,
                visibility.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        result.validateAgainst(visibility, scratch);
        return result;
    }

    /**
     * Exact P3.2 mask coverage validation using reusable primitive coverage
     * words. Rectangles must not overlap or introduce/miss any source face.
     */
    public void validateAgainst(BinarySectionVisibility visibility, BuildScratch scratch) {
        if (visibility == null || scratch == null) throw new NullPointerException();
        if (sourceVisibilityFingerprint != visibility.fingerprint()) {
            throw new IllegalStateException("Greedy rectangle source visibility fingerprint mismatch");
        }
        populateCoverage(scratch, visibility);

        if (coveredFaceCount != visibility.visibleFaceCount()) {
            throw new IllegalStateException("Greedy rectangle/source face count mismatch");
        }
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            if (directionCoveredFaces[direction] != visibility.directionFaceCount(direction)
                    || scratch.directionAreas[direction] != directionCoveredFaces[direction]) {
                throw new IllegalStateException("Greedy rectangle directional area mismatch");
            }
            int base = direction * BinarySectionVisibility.WORDS_PER_DIRECTION;
            for (int word = 0; word < BinarySectionVisibility.WORDS_PER_DIRECTION; word++) {
                if (scratch.coverageWords[base + word] != visibility.maskWord(direction, word)) {
                    throw new IllegalStateException(
                            "Greedy rectangle coverage mismatch direction=" + direction + " word=" + word);
                }
            }
        }
    }

    /** Direct exact-set validation against the permanent independent oracle. */
    public void validateAgainst(ReferenceFaceMesh reference, BuildScratch scratch) {
        if (reference == null || scratch == null) throw new NullPointerException();
        if (coveredFaceCount != reference.faceCount()) {
            throw new IllegalStateException(
                    "Greedy rectangle/reference face count mismatch: rectangles=" + coveredFaceCount
                            + ", reference=" + reference.faceCount());
        }
        populateCoverage(scratch, null);
        for (int i = 0; i < reference.faceCount(); i++) {
            int packed = reference.packedFace(i);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            if (direction >= BinarySectionVisibility.DIRECTION_COUNT) {
                throw new IllegalStateException("Reference face direction out of range");
            }
            int bitIndex = ((y * SIZE) + z) * SIZE + x;
            int wordIndex = direction * BinarySectionVisibility.WORDS_PER_DIRECTION + (bitIndex >>> 6);
            long bit = 1L << (bitIndex & 63);
            if ((scratch.coverageWords[wordIndex] & bit) == 0L) {
                throw new IllegalStateException("Greedy rectangles missed reference face index " + i);
            }
        }
    }

    private void populateCoverage(BuildScratch scratch, BinarySectionVisibility source) {
        scratch.clearCoverage();
        int expandedFaces = 0;
        for (int i = 0; i < rectangleCount; i++) {
            int record = records[i];
            int direction = direction(record);
            int plane = plane(record);
            int u = u(record);
            int v = v(record);
            int width = width(record);
            int height = height(record);
            validateRectangle(direction, plane, u, v, width, height);

            for (int dv = 0; dv < height; dv++) {
                for (int du = 0; du < width; du++) {
                    int faceU = u + du;
                    int faceV = v + dv;
                    int x;
                    int y;
                    int z;
                    switch (direction) {
                        case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST -> {
                            x = plane;
                            y = faceV;
                            z = faceU;
                        }
                        case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP -> {
                            x = faceU;
                            y = plane;
                            z = faceV;
                        }
                        case BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> {
                            x = faceU;
                            y = faceV;
                            z = plane;
                        }
                        default -> throw new IllegalStateException("Invalid rectangle direction");
                    }

                    if (source != null && !source.hasFace(x, y, z, direction)) {
                        throw new IllegalStateException("Greedy rectangle introduced a face outside source mask");
                    }
                    int bitIndex = ((y * SIZE) + z) * SIZE + x;
                    int wordIndex = direction * BinarySectionVisibility.WORDS_PER_DIRECTION + (bitIndex >>> 6);
                    long bit = 1L << (bitIndex & 63);
                    if ((scratch.coverageWords[wordIndex] & bit) != 0L) {
                        throw new IllegalStateException("Greedy rectangles overlap on a canonical face");
                    }
                    scratch.coverageWords[wordIndex] |= bit;
                    scratch.directionAreas[direction]++;
                    expandedFaces++;
                }
            }
        }
        if (expandedFaces != coveredFaceCount) {
            throw new IllegalStateException("Greedy rectangle expanded-face accounting mismatch");
        }
    }

    public boolean contentEquals(GreedySectionRectangles other) {
        return other != null
                && rectangleCount == other.rectangleCount
                && coveredFaceCount == other.coveredFaceCount
                && sourceVisibilityFingerprint == other.sourceVisibilityFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(directionRectangleCounts, other.directionRectangleCounts)
                && Arrays.equals(directionCoveredFaces, other.directionCoveredFaces)
                && Arrays.equals(records, other.records);
    }

    public int rectangleCount() { return rectangleCount; }
    public int coveredFaceCount() { return coveredFaceCount; }
    public int retainedBytes() { return rectangleCount * BYTES_PER_RECTANGLE; }
    public int facesSavedByTopologyMerging() { return coveredFaceCount - rectangleCount; }
    public int reductionPermille() {
        return coveredFaceCount == 0 ? 0
                : (int) (((long) facesSavedByTopologyMerging() * 1000L) / coveredFaceCount);
    }
    public long sourceVisibilityFingerprint() { return sourceVisibilityFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    public int directionRectangleCount(int direction) {
        validateDirection(direction);
        return directionRectangleCounts[direction];
    }

    public int directionCoveredFaceCount(int direction) {
        validateDirection(direction);
        return directionCoveredFaces[direction];
    }

    public int packedRectangle(int index) {
        if (index < 0 || index >= rectangleCount) throw new IndexOutOfBoundsException(index);
        return records[index];
    }

    public static int direction(int packed) { return (packed >>> DIRECTION_SHIFT) & DIRECTION_MASK; }
    public static int plane(int packed) { return (packed >>> PLANE_SHIFT) & NIBBLE_MASK; }
    public static int u(int packed) { return (packed >>> U_SHIFT) & NIBBLE_MASK; }
    public static int v(int packed) { return (packed >>> V_SHIFT) & NIBBLE_MASK; }
    public static int width(int packed) { return ((packed >>> WIDTH_SHIFT) & NIBBLE_MASK) + 1; }
    public static int height(int packed) { return ((packed >>> HEIGHT_SHIFT) & NIBBLE_MASK) + 1; }

    private static int pack(int direction, int plane, int u, int v, int width, int height) {
        validateRectangle(direction, plane, u, v, width, height);
        return (u << U_SHIFT)
                | (v << V_SHIFT)
                | ((width - 1) << WIDTH_SHIFT)
                | ((height - 1) << HEIGHT_SHIFT)
                | (plane << PLANE_SHIFT)
                | (direction << DIRECTION_SHIFT);
    }

    private static void fillPlaneRows(
            BinarySectionVisibility visibility,
            int direction,
            int plane,
            int[] rows) {
        Arrays.fill(rows, 0);
        switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST -> {
                int xBit = 1 << plane;
                for (int y = 0; y < SIZE; y++) {
                    int zBits = 0;
                    for (int z = 0; z < SIZE; z++) {
                        if ((visibility.rowBitsX(direction, y, z) & xBit) != 0) {
                            zBits |= 1 << z;
                        }
                    }
                    rows[y] = zBits;
                }
            }
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP -> {
                for (int z = 0; z < SIZE; z++) {
                    rows[z] = visibility.rowBitsX(direction, plane, z);
                }
            }
            case BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> {
                for (int y = 0; y < SIZE; y++) {
                    rows[y] = visibility.rowBitsX(direction, y, plane);
                }
            }
            default -> throw new IllegalArgumentException("Invalid visibility direction: " + direction);
        }
    }

    private static void validateRectangle(
            int direction, int plane, int u, int v, int width, int height) {
        validateDirection(direction);
        if (plane < 0 || plane >= SIZE || u < 0 || u >= SIZE || v < 0 || v >= SIZE
                || width <= 0 || height <= 0 || u + width > SIZE || v + height > SIZE) {
            throw new IllegalArgumentException(
                    "Invalid rectangle d=" + direction + " plane=" + plane
                            + " u=" + u + " v=" + v + " w=" + width + " h=" + height);
        }
    }

    private static void validateDirection(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid visibility direction: " + direction);
        }
    }

    private static long hashInt(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value);
        return hash * FNV_PRIME;
    }

    private static long hashLong(long hash, long value) {
        hash = hashInt(hash, (int) value);
        return hashInt(hash, (int) (value >>> 32));
    }
}
