package dev.obsidian.render.terrain;

import net.minecraft.core.Direction;

import java.util.Arrays;

/**
 * P3.4 correctness-first mapping from the conservative P3.2 canonical face set
 * to exact vanilla-baked SOLID/CUTOUT render truth.
 *
 * <p>This remains a sidecar. It does not emit geometry. A topology face is
 * render-key eligible only when exactly one baked quad from the same source
 * block can be proven to be the exact full unit-cube face for that direction.
 * Arbitrary model geometry, offsets, partial faces and ambiguous overlays stay
 * on the existing exact {@link BakedSectionMesh} passthrough path.</p>
 */
public final class CanonicalFaceRenderKeys {
    public static final int FACE_SLOTS =
            BinarySectionVisibility.DIRECTION_COUNT * SectionSnapshot.INTERIOR_CELL_COUNT;
    public static final int RETAINED_BYTES = FACE_SLOTS * Short.BYTES;

    private static final short UNMAPPED = 0;
    private static final short AMBIGUOUS = -1;
    private static final int SIZE = SectionSnapshot.INTERIOR_SIZE;
    private static final int CELLS = SectionSnapshot.INTERIOR_CELL_COUNT;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Bounded reusable primitive workspace owned by one worker. */
    public static final class BuildScratch {
        private final short[] faceToQuad = new short[FACE_SLOTS];
        private final byte[] candidateCounts = new byte[FACE_SLOTS];
        private long uses;
        private int highWaterEligibleFaces;

        private void beginBuild() {
            Arrays.fill(faceToQuad, UNMAPPED);
            Arrays.fill(candidateCounts, (byte) 0);
            uses++;
        }

        private void observeEligibleFaces(int eligibleFaces) {
            highWaterEligibleFaces = Math.max(highWaterEligibleFaces, eligibleFaces);
        }

        public long uses() { return uses; }
        public int highWaterEligibleFaces() { return highWaterEligibleFaces; }
        public int retainedScratchBytes() {
            return faceToQuad.length * Short.BYTES + candidateCounts.length;
        }
    }

    private record AdjacencyStats(long same, long different, long ineligible) { }

    private final short[] faceToQuad;
    private final int visibleFaces;
    private final int eligibleFaces;
    private final int unmappedFaces;
    private final int ambiguousFaces;
    private final int recognizedCanonicalBakedQuads;
    private final int ignoredNoncanonicalBakedQuads;
    private final long sameKeyAdjacentPairs;
    private final long differentKeyAdjacentPairs;
    private final long ineligibleAdjacentPairs;
    private final SectionSnapshot sourceSnapshot;
    private final long sourceSnapshotFingerprint;
    private final long sourceVisibilityFingerprint;
    private final long sourceBakedFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private CanonicalFaceRenderKeys(
            short[] faceToQuad,
            int visibleFaces,
            int eligibleFaces,
            int unmappedFaces,
            int ambiguousFaces,
            int recognizedCanonicalBakedQuads,
            int ignoredNoncanonicalBakedQuads,
            long sameKeyAdjacentPairs,
            long differentKeyAdjacentPairs,
            long ineligibleAdjacentPairs,
            SectionSnapshot sourceSnapshot,
            long sourceSnapshotFingerprint,
            long sourceVisibilityFingerprint,
            long sourceBakedFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.faceToQuad = faceToQuad;
        this.visibleFaces = visibleFaces;
        this.eligibleFaces = eligibleFaces;
        this.unmappedFaces = unmappedFaces;
        this.ambiguousFaces = ambiguousFaces;
        this.recognizedCanonicalBakedQuads = recognizedCanonicalBakedQuads;
        this.ignoredNoncanonicalBakedQuads = ignoredNoncanonicalBakedQuads;
        this.sameKeyAdjacentPairs = sameKeyAdjacentPairs;
        this.differentKeyAdjacentPairs = differentKeyAdjacentPairs;
        this.ineligibleAdjacentPairs = ineligibleAdjacentPairs;
        this.sourceSnapshot = sourceSnapshot;
        this.sourceSnapshotFingerprint = sourceSnapshotFingerprint;
        this.sourceVisibilityFingerprint = sourceVisibilityFingerprint;
        this.sourceBakedFingerprint = sourceBakedFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static CanonicalFaceRenderKeys build(
            SectionSnapshot snapshot,
            BinarySectionVisibility visibility,
            SectionBakedQuadSnapshot baked) {
        return build(snapshot, visibility, baked, new BuildScratch());
    }

    public static CanonicalFaceRenderKeys build(
            SectionSnapshot snapshot,
            BinarySectionVisibility visibility,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        validateInputs(snapshot, visibility, baked, scratch);
        long startNs = System.nanoTime();
        scratch.beginBuild();

        int recognized = 0;
        for (int quad = 0; quad < baked.quadCount(); quad++) {
            int slot = candidateSlot(visibility, baked, quad);
            if (slot < 0) continue;
            recognized++;
            int count = Byte.toUnsignedInt(scratch.candidateCounts[slot]);
            if (count < 127) scratch.candidateCounts[slot] = (byte) (count + 1);
            if (count == 0) {
                scratch.faceToQuad[slot] = (short) (quad + 1);
            } else {
                scratch.faceToQuad[slot] = AMBIGUOUS;
            }
        }

        int eligible = 0;
        int unmapped = 0;
        int ambiguous = 0;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            for (int bitIndex = 0; bitIndex < CELLS; bitIndex++) {
                if (!hasFaceBit(visibility, direction, bitIndex)) continue;
                short mapping = scratch.faceToQuad[slot(direction, bitIndex)];
                if (mapping > 0) eligible++;
                else if (mapping == UNMAPPED) unmapped++;
                else ambiguous++;
            }
        }
        int visible = visibility.visibleFaceCount();
        if (eligible + unmapped + ambiguous != visible) {
            throw new IllegalStateException("P3.4 render-key visible-face accounting mismatch");
        }
        scratch.observeEligibleFaces(eligible);

        AdjacencyStats adjacency = computeAdjacency(scratch.faceToQuad, visibility, baked);
        int ignored = baked.quadCount() - recognized;

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, snapshot.fingerprint());
        hash = hashLong(hash, visibility.fingerprint());
        hash = hashLong(hash, baked.fingerprint());
        hash = hashInt(hash, visible);
        hash = hashInt(hash, eligible);
        hash = hashInt(hash, unmapped);
        hash = hashInt(hash, ambiguous);
        hash = hashInt(hash, recognized);
        hash = hashInt(hash, ignored);
        hash = hashLong(hash, adjacency.same);
        hash = hashLong(hash, adjacency.different);
        hash = hashLong(hash, adjacency.ineligible);
        for (short mapping : scratch.faceToQuad) hash = hashInt(hash, mapping);

        CanonicalFaceRenderKeys result = new CanonicalFaceRenderKeys(
                Arrays.copyOf(scratch.faceToQuad, scratch.faceToQuad.length),
                visible,
                eligible,
                unmapped,
                ambiguous,
                recognized,
                ignored,
                adjacency.same,
                adjacency.different,
                adjacency.ineligible,
                snapshot,
                snapshot.fingerprint(),
                visibility.fingerprint(),
                baked.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        result.validateAgainst(snapshot, visibility, baked, scratch);
        return result;
    }

    private static void validateInputs(
            SectionSnapshot snapshot,
            BinarySectionVisibility visibility,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        if (snapshot == null || visibility == null || baked == null || scratch == null) {
            throw new NullPointerException("snapshot, visibility, baked snapshot and scratch are required");
        }
        if (visibility.sourceSnapshotFingerprint() != snapshot.fingerprint()
                || baked.sourceSnapshotFingerprint() != snapshot.fingerprint()
                || baked.sectionX() != snapshot.sectionX()
                || baked.sectionY() != snapshot.sectionY()
                || baked.sectionZ() != snapshot.sectionZ()) {
            throw new IllegalArgumentException("P3.4 render-key input identity mismatch");
        }
        if (baked.quadCount() <= 0 || baked.quadCount() > SectionBakedQuadSnapshot.MAX_QUADS) {
            throw new IllegalArgumentException("P3.4 baked quad count outside bounded range");
        }
    }

    private void validateAgainst(
            SectionSnapshot snapshot,
            BinarySectionVisibility visibility,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        if (sourceSnapshot == null
                || sourceSnapshot.fingerprint() != sourceSnapshotFingerprint
                || sourceSnapshotFingerprint != snapshot.fingerprint()
                || sourceVisibilityFingerprint != visibility.fingerprint()
                || sourceBakedFingerprint != baked.fingerprint()) {
            throw new IllegalStateException("P3.4 render-key source fingerprint mismatch");
        }
        if (faceToQuad.length != FACE_SLOTS || retainedBytes() != RETAINED_BYTES) {
            throw new IllegalStateException("P3.4 render-key retained mapping size mismatch");
        }

        int expectedEligible = 0;
        int expectedUnmapped = 0;
        int expectedAmbiguous = 0;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            for (int bitIndex = 0; bitIndex < CELLS; bitIndex++) {
                int slot = slot(direction, bitIndex);
                boolean visible = hasFaceBit(visibility, direction, bitIndex);
                int candidates = Byte.toUnsignedInt(scratch.candidateCounts[slot]);
                short mapping = faceToQuad[slot];
                if (!visible) {
                    if (candidates != 0 || mapping != UNMAPPED) {
                        throw new IllegalStateException("P3.4 mapped a non-visible canonical face");
                    }
                    continue;
                }
                if (candidates == 0) {
                    if (mapping != UNMAPPED) throw new IllegalStateException("P3.4 unmapped status mismatch");
                    expectedUnmapped++;
                } else if (candidates == 1) {
                    if (mapping <= 0) throw new IllegalStateException("P3.4 unique mapping status mismatch");
                    int quad = mappedQuad(mapping);
                    if (quad < 0 || quad >= baked.quadCount()
                            || candidateSlot(visibility, baked, quad) != slot) {
                        throw new IllegalStateException("P3.4 unique mapping points to wrong baked quad");
                    }
                    expectedEligible++;
                } else {
                    if (mapping != AMBIGUOUS) throw new IllegalStateException("P3.4 ambiguous mapping status mismatch");
                    expectedAmbiguous++;
                }
            }
        }

        int expectedRecognized = 0;
        for (int quad = 0; quad < baked.quadCount(); quad++) {
            if (candidateSlot(visibility, baked, quad) >= 0) expectedRecognized++;
        }
        if (visibleFaces != visibility.visibleFaceCount()
                || expectedEligible != eligibleFaces
                || expectedUnmapped != unmappedFaces
                || expectedAmbiguous != ambiguousFaces
                || expectedEligible + expectedUnmapped + expectedAmbiguous != visibleFaces
                || expectedRecognized != recognizedCanonicalBakedQuads
                || baked.quadCount() - expectedRecognized != ignoredNoncanonicalBakedQuads) {
            throw new IllegalStateException("P3.4 render-key aggregate accounting mismatch");
        }

        AdjacencyStats adjacency = computeAdjacency(faceToQuad, visibility, baked);
        if (adjacency.same != sameKeyAdjacentPairs
                || adjacency.different != differentKeyAdjacentPairs
                || adjacency.ineligible != ineligibleAdjacentPairs) {
            throw new IllegalStateException("P3.4 render-key adjacency accounting mismatch");
        }
    }

    /** Exact output-equivalence for two uniquely mapped canonical faces. */
    public boolean renderEquivalent(
            int xA, int yA, int zA,
            int xB, int yB, int zB,
            int direction,
            SectionBakedQuadSnapshot baked) {
        validateCoordinate(xA); validateCoordinate(yA); validateCoordinate(zA);
        validateCoordinate(xB); validateCoordinate(yB); validateCoordinate(zB);
        validateDirection(direction);
        int quadA = sourceQuad(xA, yA, zA, direction);
        int quadB = sourceQuad(xB, yB, zB, direction);
        return quadA >= 0 && quadB >= 0 && renderEquivalentQuads(baked, quadA, quadB, direction);
    }

    public boolean contentEquals(CanonicalFaceRenderKeys other) {
        return other != null
                && visibleFaces == other.visibleFaces
                && eligibleFaces == other.eligibleFaces
                && unmappedFaces == other.unmappedFaces
                && ambiguousFaces == other.ambiguousFaces
                && recognizedCanonicalBakedQuads == other.recognizedCanonicalBakedQuads
                && ignoredNoncanonicalBakedQuads == other.ignoredNoncanonicalBakedQuads
                && sameKeyAdjacentPairs == other.sameKeyAdjacentPairs
                && differentKeyAdjacentPairs == other.differentKeyAdjacentPairs
                && ineligibleAdjacentPairs == other.ineligibleAdjacentPairs
                && sourceSnapshotFingerprint == other.sourceSnapshotFingerprint
                && sourceVisibilityFingerprint == other.sourceVisibilityFingerprint
                && sourceBakedFingerprint == other.sourceBakedFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(faceToQuad, other.faceToQuad);
    }

    public int sourceQuad(int x, int y, int z, int direction) {
        validateCoordinate(x); validateCoordinate(y); validateCoordinate(z); validateDirection(direction);
        short mapping = faceToQuad[slot(direction, bitIndex(x, y, z))];
        return mapping > 0 ? mappedQuad(mapping) : -1;
    }

    public boolean eligible(int x, int y, int z, int direction) {
        return sourceQuad(x, y, z, direction) >= 0;
    }

    public boolean ambiguous(int x, int y, int z, int direction) {
        validateCoordinate(x); validateCoordinate(y); validateCoordinate(z); validateDirection(direction);
        return faceToQuad[slot(direction, bitIndex(x, y, z))] == AMBIGUOUS;
    }

    public int visibleFaces() { return visibleFaces; }
    public int eligibleFaces() { return eligibleFaces; }
    public int unmappedFaces() { return unmappedFaces; }
    public int ambiguousFaces() { return ambiguousFaces; }
    public int recognizedCanonicalBakedQuads() { return recognizedCanonicalBakedQuads; }
    public int ignoredNoncanonicalBakedQuads() { return ignoredNoncanonicalBakedQuads; }
    public long sameKeyAdjacentPairs() { return sameKeyAdjacentPairs; }
    public long differentKeyAdjacentPairs() { return differentKeyAdjacentPairs; }
    public long ineligibleAdjacentPairs() { return ineligibleAdjacentPairs; }
    public int retainedBytes() { return RETAINED_BYTES; }
    public int eligiblePermille() {
        return visibleFaces == 0 ? 0 : (int) (((long) eligibleFaces * 1000L) / visibleFaces);
    }
    SectionSnapshot sourceSnapshot() { return sourceSnapshot; }
    public long sourceSnapshotFingerprint() { return sourceSnapshotFingerprint; }
    public long sourceVisibilityFingerprint() { return sourceVisibilityFingerprint; }
    public long sourceBakedFingerprint() { return sourceBakedFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    private static AdjacencyStats computeAdjacency(
            short[] mapping,
            BinarySectionVisibility visibility,
            SectionBakedQuadSnapshot baked) {
        long same = 0L;
        long different = 0L;
        long ineligible = 0L;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    for (int x = 0; x < SIZE; x++) {
                        if (!visibility.hasFace(x, y, z, direction)) continue;
                        int neighborU = uNeighborBitIndex(x, y, z, direction);
                        if (neighborU >= 0 && hasFaceBit(visibility, direction, neighborU)) {
                            long code = compareMappedPair(mapping, baked,
                                    slot(direction, bitIndex(x, y, z)), slot(direction, neighborU), direction);
                            if (code == 1L) same++; else if (code == 2L) different++; else ineligible++;
                        }
                        int neighborV = vNeighborBitIndex(x, y, z, direction);
                        if (neighborV >= 0 && hasFaceBit(visibility, direction, neighborV)) {
                            long code = compareMappedPair(mapping, baked,
                                    slot(direction, bitIndex(x, y, z)), slot(direction, neighborV), direction);
                            if (code == 1L) same++; else if (code == 2L) different++; else ineligible++;
                        }
                    }
                }
            }
        }
        return new AdjacencyStats(same, different, ineligible);
    }

    /** 1=same, 2=different, 0=ineligible. */
    private static long compareMappedPair(
            short[] mapping,
            SectionBakedQuadSnapshot baked,
            int slotA,
            int slotB,
            int direction) {
        short a = mapping[slotA];
        short b = mapping[slotB];
        if (a <= 0 || b <= 0) return 0L;
        return renderEquivalentQuads(baked, mappedQuad(a), mappedQuad(b), direction) ? 1L : 2L;
    }

    private static boolean renderEquivalentQuads(
            SectionBakedQuadSnapshot baked,
            int quadA,
            int quadB,
            int direction) {
        if (quadA == quadB) return true;
        if (binaryDirection(baked.direction(quadA)) != direction
                || binaryDirection(baked.direction(quadB)) != direction
                || baked.layer(quadA) != baked.layer(quadB)
                || !baked.material(baked.materialId(quadA)).equals(baked.material(baked.materialId(quadB)))) {
            return false;
        }

        int xA = sourceX(baked.sourceBlock(quadA));
        int yA = sourceY(baked.sourceBlock(quadA));
        int zA = sourceZ(baked.sourceBlock(quadA));
        int xB = sourceX(baked.sourceBlock(quadB));
        int yB = sourceY(baked.sourceBlock(quadB));
        int zB = sourceZ(baked.sourceBlock(quadB));
        int orderA = cornerOrderSignature(baked, quadA, xA, yA, zA, direction);
        int orderB = cornerOrderSignature(baked, quadB, xB, yB, zB, direction);
        if (orderA < 0 || orderA != orderB) return false;

        for (int corner = 0; corner < 4; corner++) {
            int vertexA = vertexForCorner(baked, quadA, xA, yA, zA, direction, corner);
            int vertexB = vertexForCorner(baked, quadB, xB, yB, zB, direction, corner);
            if (vertexA < 0 || vertexB < 0
                    || Float.floatToRawIntBits(baked.u(quadA, vertexA))
                    != Float.floatToRawIntBits(baked.u(quadB, vertexB))
                    || Float.floatToRawIntBits(baked.v(quadA, vertexA))
                    != Float.floatToRawIntBits(baked.v(quadB, vertexB))
                    || baked.exactArgbColor(quadA, vertexA) != baked.exactArgbColor(quadB, vertexB)
                    || baked.packedLight(quadA, vertexA) != baked.packedLight(quadB, vertexB)) {
                return false;
            }
        }
        return true;
    }

    private static int candidateSlot(
            BinarySectionVisibility visibility,
            SectionBakedQuadSnapshot baked,
            int quad) {
        int direction = binaryDirection(baked.direction(quad));
        if (direction < 0) return -1;
        int packedSource = baked.sourceBlock(quad);
        int x = sourceX(packedSource);
        int y = sourceY(packedSource);
        int z = sourceZ(packedSource);
        if (!visibility.hasFace(x, y, z, direction)) return -1;
        if (cornerOrderSignature(baked, quad, x, y, z, direction) < 0) return -1;
        return slot(direction, bitIndex(x, y, z));
    }

    /** Returns 2-bit corner ids in source vertex order, or -1 for noncanonical geometry. */
    private static int cornerOrderSignature(
            SectionBakedQuadSnapshot baked,
            int quad,
            int x,
            int y,
            int z,
            int direction) {
        int seen = 0;
        int signature = 0;
        for (int vertex = 0; vertex < SectionBakedQuadSnapshot.VERTICES_PER_QUAD; vertex++) {
            int corner = cornerCode(baked, quad, vertex, x, y, z, direction);
            if (corner < 0 || (seen & (1 << corner)) != 0) return -1;
            seen |= 1 << corner;
            signature |= corner << (vertex * 2);
        }
        return seen == 0xF ? signature : -1;
    }

    private static int vertexForCorner(
            SectionBakedQuadSnapshot baked,
            int quad,
            int x,
            int y,
            int z,
            int direction,
            int targetCorner) {
        int found = -1;
        for (int vertex = 0; vertex < SectionBakedQuadSnapshot.VERTICES_PER_QUAD; vertex++) {
            if (cornerCode(baked, quad, vertex, x, y, z, direction) == targetCorner) {
                if (found >= 0) return -1;
                found = vertex;
            }
        }
        return found;
    }

    private static int cornerCode(
            SectionBakedQuadSnapshot baked,
            int quad,
            int vertex,
            int x,
            int y,
            int z,
            int direction) {
        float px = baked.position(quad, vertex, 0);
        float py = baked.position(quad, vertex, 1);
        float pz = baked.position(quad, vertex, 2);
        int uLow;
        int uHigh;
        int vLow;
        int vHigh;
        float fixed;
        float fixedExpected;
        float u;
        float v;

        switch (direction) {
            case BinarySectionVisibility.WEST -> {
                fixed = px; fixedExpected = x;
                u = pz; uLow = z; uHigh = z + 1;
                v = py; vLow = y; vHigh = y + 1;
            }
            case BinarySectionVisibility.EAST -> {
                fixed = px; fixedExpected = x + 1;
                u = pz; uLow = z; uHigh = z + 1;
                v = py; vLow = y; vHigh = y + 1;
            }
            case BinarySectionVisibility.DOWN -> {
                fixed = py; fixedExpected = y;
                u = px; uLow = x; uHigh = x + 1;
                v = pz; vLow = z; vHigh = z + 1;
            }
            case BinarySectionVisibility.UP -> {
                fixed = py; fixedExpected = y + 1;
                u = px; uLow = x; uHigh = x + 1;
                v = pz; vLow = z; vHigh = z + 1;
            }
            case BinarySectionVisibility.NORTH -> {
                fixed = pz; fixedExpected = z;
                u = px; uLow = x; uHigh = x + 1;
                v = py; vLow = y; vHigh = y + 1;
            }
            case BinarySectionVisibility.SOUTH -> {
                fixed = pz; fixedExpected = z + 1;
                u = px; uLow = x; uHigh = x + 1;
                v = py; vLow = y; vHigh = y + 1;
            }
            default -> { return -1; }
        }
        if (!rawEquals(fixed, fixedExpected)) return -1;
        int uBit = rawEquals(u, uLow) ? 0 : rawEquals(u, uHigh) ? 1 : -1;
        int vBit = rawEquals(v, vLow) ? 0 : rawEquals(v, vHigh) ? 1 : -1;
        return uBit < 0 || vBit < 0 ? -1 : uBit | (vBit << 1);
    }

    private static boolean rawEquals(float value, float expected) {
        return Float.floatToRawIntBits(value) == Float.floatToRawIntBits(expected);
    }

    private static int binaryDirection(byte minecraftDirection) {
        if (minecraftDirection < 0) return -1;
        int ordinal = minecraftDirection;
        if (ordinal == Direction.WEST.ordinal()) return BinarySectionVisibility.WEST;
        if (ordinal == Direction.EAST.ordinal()) return BinarySectionVisibility.EAST;
        if (ordinal == Direction.DOWN.ordinal()) return BinarySectionVisibility.DOWN;
        if (ordinal == Direction.UP.ordinal()) return BinarySectionVisibility.UP;
        if (ordinal == Direction.NORTH.ordinal()) return BinarySectionVisibility.NORTH;
        if (ordinal == Direction.SOUTH.ordinal()) return BinarySectionVisibility.SOUTH;
        return -1;
    }

    private static int uNeighborBitIndex(int x, int y, int z, int direction) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST ->
                    z + 1 < SIZE ? bitIndex(x, y, z + 1) : -1;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP,
                    BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH ->
                    x + 1 < SIZE ? bitIndex(x + 1, y, z) : -1;
            default -> -1;
        };
    }

    private static int vNeighborBitIndex(int x, int y, int z, int direction) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST,
                    BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH ->
                    y + 1 < SIZE ? bitIndex(x, y + 1, z) : -1;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP ->
                    z + 1 < SIZE ? bitIndex(x, y, z + 1) : -1;
            default -> -1;
        };
    }

    private static boolean hasFaceBit(BinarySectionVisibility visibility, int direction, int bitIndex) {
        int word = bitIndex >>> 6;
        return (visibility.maskWord(direction, word) & (1L << (bitIndex & 63))) != 0L;
    }

    private static int slot(int direction, int bitIndex) {
        return direction * CELLS + bitIndex;
    }

    private static int bitIndex(int x, int y, int z) {
        return ((y * SIZE) + z) * SIZE + x;
    }

    private static int mappedQuad(short mapping) {
        return (mapping & 0xFFFF) - 1;
    }

    private static int sourceX(int packed) { return packed & 0xF; }
    private static int sourceY(int packed) { return (packed >>> 4) & 0xF; }
    private static int sourceZ(int packed) { return (packed >>> 8) & 0xF; }

    private static void validateCoordinate(int coordinate) {
        if (coordinate < 0 || coordinate >= SIZE) throw new IndexOutOfBoundsException(coordinate);
    }

    private static void validateDirection(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IndexOutOfBoundsException(direction);
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
