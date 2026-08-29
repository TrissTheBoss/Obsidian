package dev.obsidian.render.terrain;

import java.util.Arrays;

/**
 * Pure immutable P3.5 proof for one section's six outward boundaries.
 *
 * <p>The proof consumes only renderer-owned snapshots. It rebuilds the compact
 * binary visibility sidecar and the deliberately independent scalar reference
 * oracle, then checks every one of the 1,536 outward boundary source cells
 * against the captured one-block halo. It also fingerprints the exact packed
 * light and ARGB payload already frozen by vanilla generalized-quad capture for
 * source blocks that lie on a section boundary.</p>
 */
public final class BorderHaloCorrectnessProof {
    public static final int OUTWARD_CHECKS_PER_BUILD = 6
            * SectionSnapshot.INTERIOR_SIZE
            * SectionSnapshot.INTERIOR_SIZE;

    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, -1, 1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, -1, 1};
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Reusable primitive workspace; retained proof output is summary-only. */
    public static final class BuildScratch {
        private final BinarySectionVisibility.BuildScratch visibilityScratch =
                new BinarySectionVisibility.BuildScratch();
        private final boolean[] referenceFaces =
                new boolean[BinarySectionVisibility.DIRECTION_COUNT * SectionSnapshot.INTERIOR_CELL_COUNT];
        private long uses;

        private void begin() {
            Arrays.fill(referenceFaces, false);
            uses++;
        }

        public long uses() { return uses; }
    }

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final long sourceSnapshotFingerprint;
    private final long sourceBakedFingerprint;
    private final int outwardChecks;
    private final int visibilityMatches;
    private final int referenceMatches;
    private final int expectedVisibleFaces;
    private final int unsupportedBlockedFaces;
    private final int borderBakedQuads;
    private final int borderLightColorSamples;
    private final long borderSemanticHash;
    private final long fingerprint;
    private final long buildTimeNs;

    private BorderHaloCorrectnessProof(
            int sectionX,
            int sectionY,
            int sectionZ,
            long sourceSnapshotFingerprint,
            long sourceBakedFingerprint,
            int outwardChecks,
            int visibilityMatches,
            int referenceMatches,
            int expectedVisibleFaces,
            int unsupportedBlockedFaces,
            int borderBakedQuads,
            int borderLightColorSamples,
            long borderSemanticHash,
            long fingerprint,
            long buildTimeNs) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.sourceSnapshotFingerprint = sourceSnapshotFingerprint;
        this.sourceBakedFingerprint = sourceBakedFingerprint;
        this.outwardChecks = outwardChecks;
        this.visibilityMatches = visibilityMatches;
        this.referenceMatches = referenceMatches;
        this.expectedVisibleFaces = expectedVisibleFaces;
        this.unsupportedBlockedFaces = unsupportedBlockedFaces;
        this.borderBakedQuads = borderBakedQuads;
        this.borderLightColorSamples = borderLightColorSamples;
        this.borderSemanticHash = borderSemanticHash;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static BorderHaloCorrectnessProof build(
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot baked) {
        return build(snapshot, baked, new BuildScratch());
    }

    public static BorderHaloCorrectnessProof build(
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        if (snapshot == null || baked == null || scratch == null) {
            throw new NullPointerException("snapshot, baked snapshot and scratch are required");
        }
        if (snapshot.sectionX() != baked.sectionX()
                || snapshot.sectionY() != baked.sectionY()
                || snapshot.sectionZ() != baked.sectionZ()
                || snapshot.fingerprint() != baked.sourceSnapshotFingerprint()) {
            throw new IllegalStateException("P3.5 border proof source snapshots do not match");
        }

        long startNs = System.nanoTime();
        scratch.begin();

        BinarySectionVisibility visibility = BinarySectionVisibility.build(snapshot, scratch.visibilityScratch);
        ReferenceFaceMesh reference = ReferenceFaceMesh.build(snapshot);
        reference.validateAgainst(snapshot);
        visibility.validateAgainst(reference);

        for (int i = 0; i < reference.faceCount(); i++) {
            int packed = reference.packedFace(i);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            scratch.referenceFaces[referenceIndex(x, y, z, direction)] = true;
        }

        int checks = 0;
        int visibilityMatches = 0;
        int referenceMatches = 0;
        int visible = 0;
        int unsupported = 0;

        for (int a = 0; a < SectionSnapshot.INTERIOR_SIZE; a++) {
            for (int b = 0; b < SectionSnapshot.INTERIOR_SIZE; b++) {
                int[] result;
                result = checkBoundary(snapshot, visibility, scratch, 0, 0, a, b);
                checks += result[0]; visibilityMatches += result[1]; referenceMatches += result[2]; visible += result[3]; unsupported += result[4];
                result = checkBoundary(snapshot, visibility, scratch, 1, 15, a, b);
                checks += result[0]; visibilityMatches += result[1]; referenceMatches += result[2]; visible += result[3]; unsupported += result[4];
                result = checkBoundary(snapshot, visibility, scratch, 2, a, 0, b);
                checks += result[0]; visibilityMatches += result[1]; referenceMatches += result[2]; visible += result[3]; unsupported += result[4];
                result = checkBoundary(snapshot, visibility, scratch, 3, a, 15, b);
                checks += result[0]; visibilityMatches += result[1]; referenceMatches += result[2]; visible += result[3]; unsupported += result[4];
                result = checkBoundary(snapshot, visibility, scratch, 4, a, b, 0);
                checks += result[0]; visibilityMatches += result[1]; referenceMatches += result[2]; visible += result[3]; unsupported += result[4];
                result = checkBoundary(snapshot, visibility, scratch, 5, a, b, 15);
                checks += result[0]; visibilityMatches += result[1]; referenceMatches += result[2]; visible += result[3]; unsupported += result[4];
            }
        }

        if (checks != OUTWARD_CHECKS_PER_BUILD
                || visibilityMatches != checks
                || referenceMatches != checks) {
            throw new IllegalStateException("P3.5 outward border proof accounting mismatch");
        }

        int borderQuads = 0;
        int borderSamples = 0;
        long semanticHash = FNV_OFFSET_BASIS;
        for (int quad = 0; quad < baked.quadCount(); quad++) {
            int source = baked.sourceBlock(quad);
            int x = source & 0xF;
            int y = (source >>> 4) & 0xF;
            int z = (source >>> 8) & 0xF;
            if (!isBoundaryCoordinate(x, y, z)) continue;
            borderQuads++;
            semanticHash = hashInt(semanticHash, source);
            semanticHash = hashInt(semanticHash, baked.stateId(quad));
            semanticHash = hashInt(semanticHash, baked.materialId(quad));
            semanticHash = hashInt(semanticHash, Byte.toUnsignedInt(baked.direction(quad)));
            semanticHash = hashInt(semanticHash, Byte.toUnsignedInt(baked.layer(quad)));
            for (int vertex = 0; vertex < SectionBakedQuadSnapshot.VERTICES_PER_QUAD; vertex++) {
                semanticHash = hashInt(semanticHash, baked.packedLight(quad, vertex));
                semanticHash = hashInt(semanticHash, baked.exactArgbColor(quad, vertex));
                borderSamples++;
            }
        }

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, snapshot.fingerprint());
        hash = hashLong(hash, baked.fingerprint());
        hash = hashInt(hash, checks);
        hash = hashInt(hash, visibilityMatches);
        hash = hashInt(hash, referenceMatches);
        hash = hashInt(hash, visible);
        hash = hashInt(hash, unsupported);
        hash = hashInt(hash, borderQuads);
        hash = hashInt(hash, borderSamples);
        hash = hashLong(hash, semanticHash);

        return new BorderHaloCorrectnessProof(
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(),
                snapshot.fingerprint(), baked.fingerprint(),
                checks, visibilityMatches, referenceMatches, visible, unsupported,
                borderQuads, borderSamples, semanticHash, hash,
                System.nanoTime() - startNs);
    }

    private static int[] checkBoundary(
            SectionSnapshot snapshot,
            BinarySectionVisibility visibility,
            BuildScratch scratch,
            int direction,
            int x,
            int y,
            int z) {
        byte source = snapshot.classification(x, y, z);
        byte neighbor = snapshot.classification(
                x + DX[direction], y + DY[direction], z + DZ[direction]);
        boolean expected = source == SectionSnapshot.SUPPORTED_FULL_CUBE && neighbor == SectionSnapshot.AIR;
        boolean expectedUnsupported = source == SectionSnapshot.SUPPORTED_FULL_CUBE
                && neighbor == SectionSnapshot.UNSUPPORTED;
        boolean optimized = visibility.hasFace(x, y, z, direction);
        boolean reference = scratch.referenceFaces[referenceIndex(x, y, z, direction)];
        if (optimized != expected) {
            throw new IllegalStateException("P3.5 binary visibility border mismatch at ("
                    + x + "," + y + "," + z + ") direction=" + direction);
        }
        if (reference != expected) {
            throw new IllegalStateException("P3.5 independent reference border mismatch at ("
                    + x + "," + y + "," + z + ") direction=" + direction);
        }
        return new int[] {1, 1, 1, expected ? 1 : 0, expectedUnsupported ? 1 : 0};
    }

    private static int referenceIndex(int x, int y, int z, int direction) {
        int cell = ((y * SectionSnapshot.INTERIOR_SIZE) + z) * SectionSnapshot.INTERIOR_SIZE + x;
        return direction * SectionSnapshot.INTERIOR_CELL_COUNT + cell;
    }

    private static boolean isBoundaryCoordinate(int x, int y, int z) {
        int max = SectionSnapshot.INTERIOR_SIZE - 1;
        return x == 0 || x == max || y == 0 || y == max || z == 0 || z == max;
    }

    public boolean contentEquals(BorderHaloCorrectnessProof other) {
        return other != null
                && sectionX == other.sectionX
                && sectionY == other.sectionY
                && sectionZ == other.sectionZ
                && sourceSnapshotFingerprint == other.sourceSnapshotFingerprint
                && sourceBakedFingerprint == other.sourceBakedFingerprint
                && outwardChecks == other.outwardChecks
                && visibilityMatches == other.visibilityMatches
                && referenceMatches == other.referenceMatches
                && expectedVisibleFaces == other.expectedVisibleFaces
                && unsupportedBlockedFaces == other.unsupportedBlockedFaces
                && borderBakedQuads == other.borderBakedQuads
                && borderLightColorSamples == other.borderLightColorSamples
                && borderSemanticHash == other.borderSemanticHash
                && fingerprint == other.fingerprint;
    }

    public int sectionX() { return sectionX; }
    public int sectionY() { return sectionY; }
    public int sectionZ() { return sectionZ; }
    public long sourceSnapshotFingerprint() { return sourceSnapshotFingerprint; }
    public long sourceBakedFingerprint() { return sourceBakedFingerprint; }
    public int outwardChecks() { return outwardChecks; }
    public int visibilityMatches() { return visibilityMatches; }
    public int referenceMatches() { return referenceMatches; }
    public int expectedVisibleFaces() { return expectedVisibleFaces; }
    public int unsupportedBlockedFaces() { return unsupportedBlockedFaces; }
    public int borderBakedQuads() { return borderBakedQuads; }
    public int borderLightColorSamples() { return borderLightColorSamples; }
    public long borderSemanticHash() { return borderSemanticHash; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    private static long hashInt(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value);
        return hash * FNV_PRIME;
    }

    private static long hashLong(long hash, long value) {
        hash = hashInt(hash, (int) value);
        return hashInt(hash, (int) (value >>> 32));
    }
}
