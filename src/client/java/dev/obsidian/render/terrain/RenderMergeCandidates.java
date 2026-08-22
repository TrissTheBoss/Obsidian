package dev.obsidian.render.terrain;

import java.util.Arrays;

/**
 * P3.4 dev7 correctness-first render-key-aware rectangle candidate sidecar.
 *
 * <p>This class does not emit GPU geometry. It partitions only canonical faces
 * already proven eligible by {@link CanonicalFaceRenderKeys}. Candidate faces
 * must all be exactly render-equivalent to one representative baked quad.
 * Generalized baked geometry and canonical faces that are unmapped/ambiguous
 * remain on the existing exact {@link BakedSectionMesh} passthrough path.</p>
 */
public final class RenderMergeCandidates {
    public static final int BYTES_PER_CANDIDATE = Integer.BYTES + Short.BYTES;
    public static final int MAX_CANDIDATES = ReferenceFaceMesh.MAX_FACES;
    public static final int MAX_RETAINED_BYTES = MAX_CANDIDATES * BYTES_PER_CANDIDATE;

    private static final int SIZE = SectionSnapshot.INTERIOR_SIZE;
    private static final int CELLS = SectionSnapshot.INTERIOR_CELL_COUNT;
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

    /** Fixed primitive workspace intended to be owned and reused by one worker. */
    public static final class BuildScratch {
        private final int[] records = new int[MAX_CANDIDATES];
        private final short[] representativeQuads = new short[MAX_CANDIDATES];
        private final int[] eligibleRows = new int[SIZE];
        private final int[] consumedRows = new int[SIZE];
        private final long[] coverageWords = new long[BinarySectionVisibility.TOTAL_WORDS];
        private final int[] directionAreas = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private long uses;
        private int highWaterCandidates;

        private void beginBuild() {
            uses++;
        }

        private void clearPlane() {
            Arrays.fill(eligibleRows, 0);
            Arrays.fill(consumedRows, 0);
        }

        private void clearCoverage() {
            Arrays.fill(coverageWords, 0L);
            Arrays.fill(directionAreas, 0);
        }

        private void observeCandidateCount(int candidates) {
            highWaterCandidates = Math.max(highWaterCandidates, candidates);
        }

        public long uses() { return uses; }
        public int highWaterCandidates() { return highWaterCandidates; }
        public int retainedScratchBytes() {
            return records.length * Integer.BYTES
                    + representativeQuads.length * Short.BYTES
                    + eligibleRows.length * Integer.BYTES
                    + consumedRows.length * Integer.BYTES
                    + coverageWords.length * Long.BYTES
                    + directionAreas.length * Integer.BYTES;
        }
    }

    private final int[] records;
    private final short[] representativeQuads;
    private final int[] directionCandidateCounts;
    private final int[] directionCoveredFaces;
    private final int candidateCount;
    private final int coveredEligibleFaces;
    private final int passthroughCanonicalFaces;
    private final int singletonCandidates;
    private final int multiFaceCandidates;
    private final long sourceVisibilityFingerprint;
    private final long sourceTopologyFingerprint;
    private final long sourceRenderKeyFingerprint;
    private final long sourceBakedFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private RenderMergeCandidates(
            int[] records,
            short[] representativeQuads,
            int[] directionCandidateCounts,
            int[] directionCoveredFaces,
            int candidateCount,
            int coveredEligibleFaces,
            int passthroughCanonicalFaces,
            int singletonCandidates,
            int multiFaceCandidates,
            long sourceVisibilityFingerprint,
            long sourceTopologyFingerprint,
            long sourceRenderKeyFingerprint,
            long sourceBakedFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.records = records;
        this.representativeQuads = representativeQuads;
        this.directionCandidateCounts = directionCandidateCounts;
        this.directionCoveredFaces = directionCoveredFaces;
        this.candidateCount = candidateCount;
        this.coveredEligibleFaces = coveredEligibleFaces;
        this.passthroughCanonicalFaces = passthroughCanonicalFaces;
        this.singletonCandidates = singletonCandidates;
        this.multiFaceCandidates = multiFaceCandidates;
        this.sourceVisibilityFingerprint = sourceVisibilityFingerprint;
        this.sourceTopologyFingerprint = sourceTopologyFingerprint;
        this.sourceRenderKeyFingerprint = sourceRenderKeyFingerprint;
        this.sourceBakedFingerprint = sourceBakedFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static RenderMergeCandidates build(
            BinarySectionVisibility visibility,
            GreedySectionRectangles topology,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked) {
        return build(visibility, topology, renderKeys, baked, new BuildScratch());
    }

    public static RenderMergeCandidates build(
            BinarySectionVisibility visibility,
            GreedySectionRectangles topology,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        validateInputs(visibility, topology, renderKeys, baked, scratch);
        long startNs = System.nanoTime();
        scratch.beginBuild();

        int out = 0;
        int covered = 0;
        int singletons = 0;
        int multi = 0;
        int[] candidateCounts = new int[BinarySectionVisibility.DIRECTION_COUNT];
        int[] coveredByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];

        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            for (int plane = 0; plane < SIZE; plane++) {
                fillEligibleRows(renderKeys, direction, plane, scratch);
                for (int v = 0; v < SIZE; v++) {
                    while (remainingRowBits(scratch, v) != 0) {
                        int u = Integer.numberOfTrailingZeros(remainingRowBits(scratch, v));
                        int seedX = x(direction, plane, u, v);
                        int seedY = y(direction, plane, u, v);
                        int seedZ = z(direction, plane, u, v);
                        int representative = renderKeys.sourceQuad(seedX, seedY, seedZ, direction);
                        if (representative < 0 || representative >= SectionBakedQuadSnapshot.MAX_QUADS) {
                            throw new IllegalStateException("Dev7 candidate seed has no bounded representative quad");
                        }

                        int width = 1;
                        while (u + width < SIZE
                                && faceAvailable(scratch, u + width, v)
                                && equivalentToSeed(renderKeys, baked, direction,
                                        seedX, seedY, seedZ, plane, u + width, v)) {
                            width++;
                        }

                        int height = 1;
                        while (v + height < SIZE
                                && rowRunAvailableAndEquivalent(
                                        scratch, renderKeys, baked, direction,
                                        seedX, seedY, seedZ,
                                        plane, u, v + height, width)) {
                            height++;
                        }

                        if (out >= MAX_CANDIDATES) {
                            throw new IllegalStateException("Dev7 merge-candidate output exceeded bounded maximum");
                        }
                        scratch.records[out] = pack(direction, plane, u, v, width, height);
                        scratch.representativeQuads[out] = (short) representative;
                        out++;

                        int area = width * height;
                        candidateCounts[direction]++;
                        coveredByDirection[direction] += area;
                        covered += area;
                        if (area == 1) singletons++; else multi++;

                        int runMask = (((1 << width) - 1) << u) & ROW_MASK;
                        for (int row = v; row < v + height; row++) {
                            scratch.consumedRows[row] |= runMask;
                        }
                    }
                }
            }
        }

        scratch.observeCandidateCount(out);
        int[] retainedRecords = Arrays.copyOf(scratch.records, out);
        short[] retainedRepresentatives = Arrays.copyOf(scratch.representativeQuads, out);
        int passthrough = visibility.visibleFaceCount() - renderKeys.eligibleFaces();

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, visibility.fingerprint());
        hash = hashLong(hash, topology.fingerprint());
        hash = hashLong(hash, renderKeys.fingerprint());
        hash = hashLong(hash, baked.fingerprint());
        hash = hashInt(hash, out);
        hash = hashInt(hash, covered);
        hash = hashInt(hash, passthrough);
        hash = hashInt(hash, singletons);
        hash = hashInt(hash, multi);
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            hash = hashInt(hash, candidateCounts[direction]);
            hash = hashInt(hash, coveredByDirection[direction]);
        }
        for (int i = 0; i < out; i++) {
            hash = hashInt(hash, retainedRecords[i]);
            hash = hashInt(hash, Short.toUnsignedInt(retainedRepresentatives[i]));
        }

        RenderMergeCandidates result = new RenderMergeCandidates(
                retainedRecords,
                retainedRepresentatives,
                candidateCounts,
                coveredByDirection,
                out,
                covered,
                passthrough,
                singletons,
                multi,
                visibility.fingerprint(),
                topology.fingerprint(),
                renderKeys.fingerprint(),
                baked.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        result.validateAgainst(visibility, topology, renderKeys, baked, scratch);
        return result;
    }

    private static void validateInputs(
            BinarySectionVisibility visibility,
            GreedySectionRectangles topology,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        if (visibility == null || topology == null || renderKeys == null || baked == null || scratch == null) {
            throw new NullPointerException("visibility, topology, render keys, baked snapshot and scratch are required");
        }
        if (topology.sourceVisibilityFingerprint() != visibility.fingerprint()
                || topology.coveredFaceCount() != visibility.visibleFaceCount()
                || renderKeys.sourceVisibilityFingerprint() != visibility.fingerprint()
                || renderKeys.sourceBakedFingerprint() != baked.fingerprint()
                || renderKeys.visibleFaces() != visibility.visibleFaceCount()) {
            throw new IllegalArgumentException("Dev7 merge-candidate input identity/accounting mismatch");
        }
        if (renderKeys.eligibleFaces() < 0 || renderKeys.eligibleFaces() > visibility.visibleFaceCount()) {
            throw new IllegalArgumentException("Dev7 render-key eligible-face count outside source range");
        }
    }

    /** Exact independent expansion/partition validation against dev6 eligibility. */
    public void validateAgainst(
            BinarySectionVisibility visibility,
            GreedySectionRectangles topology,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        validateInputs(visibility, topology, renderKeys, baked, scratch);
        if (sourceVisibilityFingerprint != visibility.fingerprint()
                || sourceTopologyFingerprint != topology.fingerprint()
                || sourceRenderKeyFingerprint != renderKeys.fingerprint()
                || sourceBakedFingerprint != baked.fingerprint()) {
            throw new IllegalStateException("Dev7 merge-candidate source fingerprint mismatch");
        }
        if (records.length != candidateCount || representativeQuads.length != candidateCount
                || retainedBytes() != candidateCount * BYTES_PER_CANDIDATE
                || candidateCount > MAX_CANDIDATES) {
            throw new IllegalStateException("Dev7 merge-candidate retained representation mismatch");
        }

        scratch.clearCoverage();
        int expanded = 0;
        int expectedSingletons = 0;
        int expectedMulti = 0;
        int[] expectedCandidateCounts = new int[BinarySectionVisibility.DIRECTION_COUNT];

        for (int i = 0; i < candidateCount; i++) {
            int record = records[i];
            int direction = direction(record);
            int plane = plane(record);
            int u = u(record);
            int v = v(record);
            int width = width(record);
            int height = height(record);
            validateRectangle(direction, plane, u, v, width, height);

            int seedX = x(direction, plane, u, v);
            int seedY = y(direction, plane, u, v);
            int seedZ = z(direction, plane, u, v);
            int representative = Short.toUnsignedInt(representativeQuads[i]);
            if (representative >= baked.quadCount()
                    || renderKeys.sourceQuad(seedX, seedY, seedZ, direction) != representative) {
                throw new IllegalStateException("Dev7 candidate representative does not match seed face");
            }

            int area = width * height;
            if (area == 1) expectedSingletons++; else expectedMulti++;
            expectedCandidateCounts[direction]++;

            for (int dv = 0; dv < height; dv++) {
                for (int du = 0; du < width; du++) {
                    int faceU = u + du;
                    int faceV = v + dv;
                    int faceX = x(direction, plane, faceU, faceV);
                    int faceY = y(direction, plane, faceU, faceV);
                    int faceZ = z(direction, plane, faceU, faceV);
                    if (!visibility.hasFace(faceX, faceY, faceZ, direction)
                            || !renderKeys.eligible(faceX, faceY, faceZ, direction)
                            || !renderKeys.renderEquivalent(
                                    seedX, seedY, seedZ,
                                    faceX, faceY, faceZ,
                                    direction, baked)) {
                        throw new IllegalStateException("Dev7 candidate contains non-equivalent or ineligible face");
                    }

                    int bitIndex = bitIndex(faceX, faceY, faceZ);
                    int wordIndex = direction * BinarySectionVisibility.WORDS_PER_DIRECTION + (bitIndex >>> 6);
                    long bit = 1L << (bitIndex & 63);
                    if ((scratch.coverageWords[wordIndex] & bit) != 0L) {
                        throw new IllegalStateException("Dev7 merge candidates overlap on a canonical face");
                    }
                    scratch.coverageWords[wordIndex] |= bit;
                    scratch.directionAreas[direction]++;
                    expanded++;
                }
            }
        }

        int expectedEligible = 0;
        int expectedPassthrough = 0;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            int eligibleDirection = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    for (int x = 0; x < SIZE; x++) {
                        boolean visible = visibility.hasFace(x, y, z, direction);
                        boolean eligible = renderKeys.eligible(x, y, z, direction);
                        if (eligible && !visible) {
                            throw new IllegalStateException("Dev7 render keys mark non-visible face eligible");
                        }
                        int bitIndex = bitIndex(x, y, z);
                        int wordIndex = direction * BinarySectionVisibility.WORDS_PER_DIRECTION + (bitIndex >>> 6);
                        long bit = 1L << (bitIndex & 63);
                        boolean covered = (scratch.coverageWords[wordIndex] & bit) != 0L;
                        if (eligible != covered) {
                            throw new IllegalStateException("Dev7 candidate coverage differs from eligible face set");
                        }
                        if (eligible) {
                            eligibleDirection++;
                            expectedEligible++;
                        } else if (visible) {
                            expectedPassthrough++;
                        }
                    }
                }
            }
            if (directionCoveredFaces[direction] != eligibleDirection
                    || scratch.directionAreas[direction] != eligibleDirection
                    || directionCandidateCounts[direction] != expectedCandidateCounts[direction]) {
                throw new IllegalStateException("Dev7 directional candidate accounting mismatch");
            }
        }

        if (expanded != coveredEligibleFaces
                || expectedEligible != renderKeys.eligibleFaces()
                || coveredEligibleFaces != renderKeys.eligibleFaces()
                || expectedPassthrough != passthroughCanonicalFaces
                || passthroughCanonicalFaces != visibility.visibleFaceCount() - renderKeys.eligibleFaces()
                || expectedSingletons != singletonCandidates
                || expectedMulti != multiFaceCandidates
                || singletonCandidates + multiFaceCandidates != candidateCount
                || facesSavedByCandidateMerging() != coveredEligibleFaces - candidateCount) {
            throw new IllegalStateException("Dev7 aggregate merge-candidate accounting mismatch");
        }
    }

    private static void fillEligibleRows(
            CanonicalFaceRenderKeys renderKeys,
            int direction,
            int plane,
            BuildScratch scratch) {
        scratch.clearPlane();
        for (int v = 0; v < SIZE; v++) {
            int row = 0;
            for (int u = 0; u < SIZE; u++) {
                int x = x(direction, plane, u, v);
                int y = y(direction, plane, u, v);
                int z = z(direction, plane, u, v);
                if (renderKeys.eligible(x, y, z, direction)) row |= 1 << u;
            }
            scratch.eligibleRows[v] = row;
        }
    }

    private static int remainingRowBits(BuildScratch scratch, int v) {
        return scratch.eligibleRows[v] & ~scratch.consumedRows[v] & ROW_MASK;
    }

    private static boolean faceAvailable(BuildScratch scratch, int u, int v) {
        int bit = 1 << u;
        return (scratch.eligibleRows[v] & bit) != 0 && (scratch.consumedRows[v] & bit) == 0;
    }

    private static boolean rowRunAvailableAndEquivalent(
            BuildScratch scratch,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked,
            int direction,
            int seedX,
            int seedY,
            int seedZ,
            int plane,
            int u,
            int v,
            int width) {
        int runMask = (((1 << width) - 1) << u) & ROW_MASK;
        if ((scratch.eligibleRows[v] & runMask) != runMask
                || (scratch.consumedRows[v] & runMask) != 0) {
            return false;
        }
        for (int du = 0; du < width; du++) {
            if (!equivalentToSeed(renderKeys, baked, direction,
                    seedX, seedY, seedZ, plane, u + du, v)) {
                return false;
            }
        }
        return true;
    }

    private static boolean equivalentToSeed(
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked,
            int direction,
            int seedX,
            int seedY,
            int seedZ,
            int plane,
            int u,
            int v) {
        int faceX = x(direction, plane, u, v);
        int faceY = y(direction, plane, u, v);
        int faceZ = z(direction, plane, u, v);
        return renderKeys.renderEquivalent(
                seedX, seedY, seedZ,
                faceX, faceY, faceZ,
                direction, baked);
    }

    public boolean contentEquals(RenderMergeCandidates other) {
        return other != null
                && candidateCount == other.candidateCount
                && coveredEligibleFaces == other.coveredEligibleFaces
                && passthroughCanonicalFaces == other.passthroughCanonicalFaces
                && singletonCandidates == other.singletonCandidates
                && multiFaceCandidates == other.multiFaceCandidates
                && sourceVisibilityFingerprint == other.sourceVisibilityFingerprint
                && sourceTopologyFingerprint == other.sourceTopologyFingerprint
                && sourceRenderKeyFingerprint == other.sourceRenderKeyFingerprint
                && sourceBakedFingerprint == other.sourceBakedFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(directionCandidateCounts, other.directionCandidateCounts)
                && Arrays.equals(directionCoveredFaces, other.directionCoveredFaces)
                && Arrays.equals(records, other.records)
                && Arrays.equals(representativeQuads, other.representativeQuads);
    }

    public int candidateCount() { return candidateCount; }
    public int coveredEligibleFaces() { return coveredEligibleFaces; }
    public int passthroughCanonicalFaces() { return passthroughCanonicalFaces; }
    public int singletonCandidates() { return singletonCandidates; }
    public int multiFaceCandidates() { return multiFaceCandidates; }
    public int facesSavedByCandidateMerging() { return coveredEligibleFaces - candidateCount; }
    public int reductionPermille() {
        return coveredEligibleFaces == 0 ? 0
                : (int) (((long) facesSavedByCandidateMerging() * 1000L) / coveredEligibleFaces);
    }
    public int retainedBytes() { return candidateCount * BYTES_PER_CANDIDATE; }
    public long sourceVisibilityFingerprint() { return sourceVisibilityFingerprint; }
    public long sourceTopologyFingerprint() { return sourceTopologyFingerprint; }
    public long sourceRenderKeyFingerprint() { return sourceRenderKeyFingerprint; }
    public long sourceBakedFingerprint() { return sourceBakedFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    public int directionCandidateCount(int direction) {
        validateDirection(direction);
        return directionCandidateCounts[direction];
    }

    public int directionCoveredFaceCount(int direction) {
        validateDirection(direction);
        return directionCoveredFaces[direction];
    }

    public int packedCandidate(int index) {
        if (index < 0 || index >= candidateCount) throw new IndexOutOfBoundsException(index);
        return records[index];
    }

    public int representativeSourceQuad(int index) {
        if (index < 0 || index >= candidateCount) throw new IndexOutOfBoundsException(index);
        return Short.toUnsignedInt(representativeQuads[index]);
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

    private static int x(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST -> plane;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP,
                 BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> u;
            default -> throw new IllegalArgumentException("Invalid candidate direction: " + direction);
        };
    }

    private static int y(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST,
                 BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> v;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP -> plane;
            default -> throw new IllegalArgumentException("Invalid candidate direction: " + direction);
        };
    }

    private static int z(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST -> u;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP -> v;
            case BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> plane;
            default -> throw new IllegalArgumentException("Invalid candidate direction: " + direction);
        };
    }

    private static int bitIndex(int x, int y, int z) {
        return ((y * SIZE) + z) * SIZE + x;
    }

    private static void validateRectangle(
            int direction, int plane, int u, int v, int width, int height) {
        validateDirection(direction);
        if (plane < 0 || plane >= SIZE || u < 0 || u >= SIZE || v < 0 || v >= SIZE
                || width <= 0 || height <= 0 || u + width > SIZE || v + height > SIZE) {
            throw new IllegalArgumentException(
                    "Invalid dev7 candidate d=" + direction + " plane=" + plane
                            + " u=" + u + " v=" + v + " w=" + width + " h=" + height);
        }
    }

    private static void validateDirection(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid candidate direction: " + direction);
        }
    }

    private static long hashInt(long hash, int value) {
        hash ^= value & 0xFFFFFFFFL;
        return hash * FNV_PRIME;
    }

    private static long hashLong(long hash, long value) {
        hash = hashInt(hash, (int) value);
        return hashInt(hash, (int) (value >>> 32));
    }
}
