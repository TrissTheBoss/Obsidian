package dev.obsidian.render.terrain;

import net.minecraft.core.Direction;

import java.util.Arrays;

/**
 * P3.4 dev8 correctness-first classifier for whether a proven dev7 merge
 * candidate can preserve its captured color/light/UV fields with one ordinary
 * four-vertex block-format rectangle under the current atlas semantics.
 *
 * <p>This is sidecar evidence only. It never emits GPU geometry.</p>
 */
public final class OrdinaryQuadEmissionSafety {
    public static final byte COLOR_INTERPOLATION_SAFE = 1 << 0;
    public static final byte LIGHT_INTERPOLATION_SAFE = 1 << 1;
    public static final byte UV_FIELD_SAFE = 1 << 2;
    public static final byte ORDINARY_ATTRIBUTE_SAFE = 1 << 3;

    public static final int BYTES_PER_CANDIDATE = Byte.BYTES;
    public static final int MAX_CANDIDATES = RenderMergeCandidates.MAX_CANDIDATES;
    public static final int MAX_RETAINED_BYTES = MAX_CANDIDATES * BYTES_PER_CANDIDATE;

    private static final int SIZE = SectionSnapshot.INTERIOR_SIZE;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Fixed primitive workspace intended to be owned and reused by one worker. */
    public static final class BuildScratch {
        private final byte[] flags = new byte[MAX_CANDIDATES];
        private long uses;
        private int highWaterCandidates;

        private void beginBuild() {
            uses++;
        }

        private void observeCandidateCount(int candidates) {
            highWaterCandidates = Math.max(highWaterCandidates, candidates);
        }

        public long uses() { return uses; }
        public int highWaterCandidates() { return highWaterCandidates; }
        public int retainedScratchBytes() { return flags.length; }
    }

    private final byte[] flags;
    private final int candidateCount;
    private final int sourceEligibleFaces;
    private final int singletonCandidates;
    private final int multiFaceCandidates;
    private final int multiFaceColorSafe;
    private final int multiFaceColorUnsafe;
    private final int multiFaceLightSafe;
    private final int multiFaceLightUnsafe;
    private final int multiFaceUvSafe;
    private final int multiFaceUvUnsafe;
    private final int multiFaceOrdinarySafe;
    private final int multiFaceOrdinaryUnsafe;
    private final int ordinarySafeCoveredFaces;
    private final int ordinarySafeFacesSaved;
    private final int[] directionOrdinarySafeCounts;
    private final int[] directionOrdinarySafeCoveredFaces;
    private final long sourceCandidateFingerprint;
    private final long sourceRenderKeyFingerprint;
    private final long sourceBakedFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private OrdinaryQuadEmissionSafety(
            byte[] flags,
            int candidateCount,
            int sourceEligibleFaces,
            int singletonCandidates,
            int multiFaceCandidates,
            int multiFaceColorSafe,
            int multiFaceColorUnsafe,
            int multiFaceLightSafe,
            int multiFaceLightUnsafe,
            int multiFaceUvSafe,
            int multiFaceUvUnsafe,
            int multiFaceOrdinarySafe,
            int multiFaceOrdinaryUnsafe,
            int ordinarySafeCoveredFaces,
            int ordinarySafeFacesSaved,
            int[] directionOrdinarySafeCounts,
            int[] directionOrdinarySafeCoveredFaces,
            long sourceCandidateFingerprint,
            long sourceRenderKeyFingerprint,
            long sourceBakedFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.flags = flags;
        this.candidateCount = candidateCount;
        this.sourceEligibleFaces = sourceEligibleFaces;
        this.singletonCandidates = singletonCandidates;
        this.multiFaceCandidates = multiFaceCandidates;
        this.multiFaceColorSafe = multiFaceColorSafe;
        this.multiFaceColorUnsafe = multiFaceColorUnsafe;
        this.multiFaceLightSafe = multiFaceLightSafe;
        this.multiFaceLightUnsafe = multiFaceLightUnsafe;
        this.multiFaceUvSafe = multiFaceUvSafe;
        this.multiFaceUvUnsafe = multiFaceUvUnsafe;
        this.multiFaceOrdinarySafe = multiFaceOrdinarySafe;
        this.multiFaceOrdinaryUnsafe = multiFaceOrdinaryUnsafe;
        this.ordinarySafeCoveredFaces = ordinarySafeCoveredFaces;
        this.ordinarySafeFacesSaved = ordinarySafeFacesSaved;
        this.directionOrdinarySafeCounts = directionOrdinarySafeCounts;
        this.directionOrdinarySafeCoveredFaces = directionOrdinarySafeCoveredFaces;
        this.sourceCandidateFingerprint = sourceCandidateFingerprint;
        this.sourceRenderKeyFingerprint = sourceRenderKeyFingerprint;
        this.sourceBakedFingerprint = sourceBakedFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static OrdinaryQuadEmissionSafety build(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked) {
        return build(candidates, renderKeys, baked, new BuildScratch());
    }

    public static OrdinaryQuadEmissionSafety build(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        validateInputs(candidates, renderKeys, baked, scratch);
        long startNs = System.nanoTime();
        scratch.beginBuild();

        int singletons = 0;
        int multi = 0;
        int colorSafe = 0;
        int colorUnsafe = 0;
        int lightSafe = 0;
        int lightUnsafe = 0;
        int uvSafe = 0;
        int uvUnsafe = 0;
        int ordinarySafe = 0;
        int ordinaryUnsafe = 0;
        int safeCovered = 0;
        int safeSaved = 0;
        int[] safeCountsByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];
        int[] safeFacesByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];

        for (int i = 0; i < candidates.candidateCount(); i++) {
            int packed = candidates.packedCandidate(i);
            int width = RenderMergeCandidates.width(packed);
            int height = RenderMergeCandidates.height(packed);
            int area = width * height;
            int representative = candidates.representativeSourceQuad(i);
            byte classified = classify(representative, RenderMergeCandidates.direction(packed), width, height, baked);
            scratch.flags[i] = classified;

            if (area == 1) {
                singletons++;
                if ((classified & ORDINARY_ATTRIBUTE_SAFE) == 0) {
                    throw new IllegalStateException("Dev8 singleton candidate is not ordinary-attribute-safe");
                }
                continue;
            }

            multi++;
            if ((classified & COLOR_INTERPOLATION_SAFE) != 0) colorSafe++; else colorUnsafe++;
            if ((classified & LIGHT_INTERPOLATION_SAFE) != 0) lightSafe++; else lightUnsafe++;
            if ((classified & UV_FIELD_SAFE) != 0) uvSafe++; else uvUnsafe++;
            if ((classified & ORDINARY_ATTRIBUTE_SAFE) != 0) {
                ordinarySafe++;
                safeCovered += area;
                safeSaved += area - 1;
                int direction = RenderMergeCandidates.direction(packed);
                safeCountsByDirection[direction]++;
                safeFacesByDirection[direction] += area;
            } else {
                ordinaryUnsafe++;
            }
        }

        scratch.observeCandidateCount(candidates.candidateCount());
        byte[] retainedFlags = Arrays.copyOf(scratch.flags, candidates.candidateCount());

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, candidates.fingerprint());
        hash = hashLong(hash, renderKeys.fingerprint());
        hash = hashLong(hash, baked.fingerprint());
        hash = hashInt(hash, candidates.candidateCount());
        hash = hashInt(hash, renderKeys.eligibleFaces());
        hash = hashInt(hash, singletons);
        hash = hashInt(hash, multi);
        hash = hashInt(hash, colorSafe);
        hash = hashInt(hash, colorUnsafe);
        hash = hashInt(hash, lightSafe);
        hash = hashInt(hash, lightUnsafe);
        hash = hashInt(hash, uvSafe);
        hash = hashInt(hash, uvUnsafe);
        hash = hashInt(hash, ordinarySafe);
        hash = hashInt(hash, ordinaryUnsafe);
        hash = hashInt(hash, safeCovered);
        hash = hashInt(hash, safeSaved);
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            hash = hashInt(hash, safeCountsByDirection[direction]);
            hash = hashInt(hash, safeFacesByDirection[direction]);
        }
        for (byte flag : retainedFlags) hash = hashInt(hash, Byte.toUnsignedInt(flag));

        OrdinaryQuadEmissionSafety result = new OrdinaryQuadEmissionSafety(
                retainedFlags,
                candidates.candidateCount(),
                renderKeys.eligibleFaces(),
                singletons,
                multi,
                colorSafe,
                colorUnsafe,
                lightSafe,
                lightUnsafe,
                uvSafe,
                uvUnsafe,
                ordinarySafe,
                ordinaryUnsafe,
                safeCovered,
                safeSaved,
                safeCountsByDirection,
                safeFacesByDirection,
                candidates.fingerprint(),
                renderKeys.fingerprint(),
                baked.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        result.validateAgainst(candidates, renderKeys, baked);
        return result;
    }

    private static void validateInputs(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        if (candidates == null || renderKeys == null || baked == null || scratch == null) {
            throw new NullPointerException("merge candidates, render keys, baked snapshot and scratch are required");
        }
        if (candidates.candidateCount() < 0 || candidates.candidateCount() > MAX_CANDIDATES
                || candidates.sourceRenderKeyFingerprint() != renderKeys.fingerprint()
                || candidates.sourceBakedFingerprint() != baked.fingerprint()
                || renderKeys.sourceBakedFingerprint() != baked.fingerprint()
                || candidates.coveredEligibleFaces() != renderKeys.eligibleFaces()) {
            throw new IllegalArgumentException("Dev8 emission-safety source identity/accounting mismatch");
        }
    }

    public void validateAgainst(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked) {
        validateInputs(candidates, renderKeys, baked, new BuildScratch());
        if (sourceCandidateFingerprint != candidates.fingerprint()
                || sourceRenderKeyFingerprint != renderKeys.fingerprint()
                || sourceBakedFingerprint != baked.fingerprint()
                || candidateCount != candidates.candidateCount()
                || sourceEligibleFaces != renderKeys.eligibleFaces()
                || flags.length != candidateCount
                || retainedBytes() != candidateCount * BYTES_PER_CANDIDATE) {
            throw new IllegalStateException("Dev8 retained/source identity mismatch");
        }

        int singletons = 0;
        int multi = 0;
        int colorSafe = 0;
        int colorUnsafe = 0;
        int lightSafe = 0;
        int lightUnsafe = 0;
        int uvSafe = 0;
        int uvUnsafe = 0;
        int ordinarySafe = 0;
        int ordinaryUnsafe = 0;
        int safeCovered = 0;
        int safeSaved = 0;
        int[] safeCountsByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];
        int[] safeFacesByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];

        for (int i = 0; i < candidateCount; i++) {
            int packed = candidates.packedCandidate(i);
            int width = RenderMergeCandidates.width(packed);
            int height = RenderMergeCandidates.height(packed);
            int area = width * height;
            int representative = candidates.representativeSourceQuad(i);
            byte expected = classify(representative, RenderMergeCandidates.direction(packed), width, height, baked);
            if (flags[i] != expected) {
                throw new IllegalStateException("Dev8 stored classification differs from exact recomputation");
            }
            if (area == 1) {
                singletons++;
                if ((expected & ORDINARY_ATTRIBUTE_SAFE) == 0) {
                    throw new IllegalStateException("Dev8 singleton classification mismatch");
                }
                continue;
            }

            multi++;
            if ((expected & COLOR_INTERPOLATION_SAFE) != 0) colorSafe++; else colorUnsafe++;
            if ((expected & LIGHT_INTERPOLATION_SAFE) != 0) lightSafe++; else lightUnsafe++;
            if ((expected & UV_FIELD_SAFE) != 0) uvSafe++; else uvUnsafe++;
            if ((expected & ORDINARY_ATTRIBUTE_SAFE) != 0) {
                ordinarySafe++;
                safeCovered += area;
                safeSaved += area - 1;
                int direction = RenderMergeCandidates.direction(packed);
                safeCountsByDirection[direction]++;
                safeFacesByDirection[direction] += area;
            } else {
                ordinaryUnsafe++;
            }
        }

        if (singletonCandidates != singletons
                || singletonCandidates != candidates.singletonCandidates()
                || multiFaceCandidates != multi
                || multiFaceCandidates != candidates.multiFaceCandidates()
                || singletonCandidates + multiFaceCandidates != candidateCount
                || multiFaceColorSafe != colorSafe
                || multiFaceColorUnsafe != colorUnsafe
                || multiFaceColorSafe + multiFaceColorUnsafe != multiFaceCandidates
                || multiFaceLightSafe != lightSafe
                || multiFaceLightUnsafe != lightUnsafe
                || multiFaceLightSafe + multiFaceLightUnsafe != multiFaceCandidates
                || multiFaceUvSafe != uvSafe
                || multiFaceUvUnsafe != uvUnsafe
                || multiFaceUvSafe + multiFaceUvUnsafe != multiFaceCandidates
                || multiFaceOrdinarySafe != ordinarySafe
                || multiFaceOrdinaryUnsafe != ordinaryUnsafe
                || multiFaceOrdinarySafe + multiFaceOrdinaryUnsafe != multiFaceCandidates
                || ordinarySafeCoveredFaces != safeCovered
                || ordinarySafeFacesSaved != safeSaved
                || !Arrays.equals(directionOrdinarySafeCounts, safeCountsByDirection)
                || !Arrays.equals(directionOrdinarySafeCoveredFaces, safeFacesByDirection)) {
            throw new IllegalStateException("Dev8 aggregate emission-safety accounting mismatch");
        }
    }

    private static byte classify(
            int representative,
            int direction,
            int width,
            int height,
            SectionBakedQuadSnapshot baked) {
        if (representative < 0 || representative >= baked.quadCount()) {
            throw new IllegalStateException("Dev8 representative source quad outside baked snapshot");
        }
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT
                || binaryDirection(baked.direction(representative)) != direction
                || width <= 0 || height <= 0 || width > SIZE || height > SIZE) {
            throw new IllegalStateException("Dev8 representative direction/extent mismatch");
        }

        int packedSource = baked.sourceBlock(representative);
        int sourceX = sourceX(packedSource);
        int sourceY = sourceY(packedSource);
        int sourceZ = sourceZ(packedSource);
        int vertex0 = vertexForCorner(baked, representative, sourceX, sourceY, sourceZ, direction, 0);
        int vertex1 = vertexForCorner(baked, representative, sourceX, sourceY, sourceZ, direction, 1);
        int vertex2 = vertexForCorner(baked, representative, sourceX, sourceY, sourceZ, direction, 2);
        int vertex3 = vertexForCorner(baked, representative, sourceX, sourceY, sourceZ, direction, 3);
        if (vertex0 < 0 || vertex1 < 0 || vertex2 < 0 || vertex3 < 0) {
            throw new IllegalStateException("Dev8 representative is not an exact canonical four-corner face");
        }

        int color0 = baked.exactArgbColor(representative, vertex0);
        int color1 = baked.exactArgbColor(representative, vertex1);
        int color2 = baked.exactArgbColor(representative, vertex2);
        int color3 = baked.exactArgbColor(representative, vertex3);
        int light0 = baked.packedLight(representative, vertex0);
        int light1 = baked.packedLight(representative, vertex1);
        int light2 = baked.packedLight(representative, vertex2);
        int light3 = baked.packedLight(representative, vertex3);
        long uv0 = rawUvPair(baked, representative, vertex0);
        long uv1 = rawUvPair(baked, representative, vertex1);
        long uv2 = rawUvPair(baked, representative, vertex2);
        long uv3 = rawUvPair(baked, representative, vertex3);

        boolean colorSafe = repeatedFieldSafe(width, height, color0, color1, color2, color3);
        boolean lightSafe = repeatedFieldSafe(width, height, light0, light1, light2, light3);
        boolean uvSafe = repeatedFieldSafe(width, height, uv0, uv1, uv2, uv3);

        byte result = 0;
        if (colorSafe) result |= COLOR_INTERPOLATION_SAFE;
        if (lightSafe) result |= LIGHT_INTERPOLATION_SAFE;
        if (uvSafe) result |= UV_FIELD_SAFE;
        if (colorSafe && lightSafe && uvSafe) result |= ORDINARY_ATTRIBUTE_SAFE;
        return result;
    }

    private static boolean repeatedFieldSafe(
            int width, int height, int p0, int p1, int p2, int p3) {
        boolean uSafe = width == 1 || (p0 == p1 && p2 == p3);
        boolean vSafe = height == 1 || (p0 == p2 && p1 == p3);
        return uSafe && vSafe;
    }

    private static boolean repeatedFieldSafe(
            int width, int height, long p0, long p1, long p2, long p3) {
        boolean uSafe = width == 1 || (p0 == p1 && p2 == p3);
        boolean vSafe = height == 1 || (p0 == p2 && p1 == p3);
        return uSafe && vSafe;
    }

    private static long rawUvPair(SectionBakedQuadSnapshot baked, int quad, int vertex) {
        long u = Integer.toUnsignedLong(Float.floatToRawIntBits(baked.u(quad, vertex)));
        long v = Integer.toUnsignedLong(Float.floatToRawIntBits(baked.v(quad, vertex)));
        return (u << 32) | v;
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

    private static int sourceX(int packed) { return packed & 0xF; }
    private static int sourceY(int packed) { return (packed >>> 4) & 0xF; }
    private static int sourceZ(int packed) { return (packed >>> 8) & 0xF; }

    public boolean contentEquals(OrdinaryQuadEmissionSafety other) {
        return other != null
                && candidateCount == other.candidateCount
                && sourceEligibleFaces == other.sourceEligibleFaces
                && singletonCandidates == other.singletonCandidates
                && multiFaceCandidates == other.multiFaceCandidates
                && multiFaceColorSafe == other.multiFaceColorSafe
                && multiFaceColorUnsafe == other.multiFaceColorUnsafe
                && multiFaceLightSafe == other.multiFaceLightSafe
                && multiFaceLightUnsafe == other.multiFaceLightUnsafe
                && multiFaceUvSafe == other.multiFaceUvSafe
                && multiFaceUvUnsafe == other.multiFaceUvUnsafe
                && multiFaceOrdinarySafe == other.multiFaceOrdinarySafe
                && multiFaceOrdinaryUnsafe == other.multiFaceOrdinaryUnsafe
                && ordinarySafeCoveredFaces == other.ordinarySafeCoveredFaces
                && ordinarySafeFacesSaved == other.ordinarySafeFacesSaved
                && sourceCandidateFingerprint == other.sourceCandidateFingerprint
                && sourceRenderKeyFingerprint == other.sourceRenderKeyFingerprint
                && sourceBakedFingerprint == other.sourceBakedFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(flags, other.flags)
                && Arrays.equals(directionOrdinarySafeCounts, other.directionOrdinarySafeCounts)
                && Arrays.equals(directionOrdinarySafeCoveredFaces, other.directionOrdinarySafeCoveredFaces);
    }

    public int candidateCount() { return candidateCount; }
    public int sourceEligibleFaces() { return sourceEligibleFaces; }
    public int singletonCandidates() { return singletonCandidates; }
    public int multiFaceCandidates() { return multiFaceCandidates; }
    public int multiFaceColorSafe() { return multiFaceColorSafe; }
    public int multiFaceColorUnsafe() { return multiFaceColorUnsafe; }
    public int multiFaceLightSafe() { return multiFaceLightSafe; }
    public int multiFaceLightUnsafe() { return multiFaceLightUnsafe; }
    public int multiFaceUvSafe() { return multiFaceUvSafe; }
    public int multiFaceUvUnsafe() { return multiFaceUvUnsafe; }
    public int multiFaceOrdinarySafe() { return multiFaceOrdinarySafe; }
    public int multiFaceOrdinaryUnsafe() { return multiFaceOrdinaryUnsafe; }
    public int repeatAwareRequiredCandidates() { return multiFaceOrdinaryUnsafe; }
    public int ordinarySafeCoveredFaces() { return ordinarySafeCoveredFaces; }
    public int ordinarySafeFacesSaved() { return ordinarySafeFacesSaved; }
    public int ordinarySafeReductionPermille() {
        return sourceEligibleFaces == 0 ? 0
                : (int) (((long) ordinarySafeFacesSaved * 1000L) / sourceEligibleFaces);
    }
    public int retainedBytes() { return candidateCount * BYTES_PER_CANDIDATE; }
    public long sourceCandidateFingerprint() { return sourceCandidateFingerprint; }
    public long sourceRenderKeyFingerprint() { return sourceRenderKeyFingerprint; }
    public long sourceBakedFingerprint() { return sourceBakedFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    public int directionOrdinarySafeCount(int direction) {
        validateDirection(direction);
        return directionOrdinarySafeCounts[direction];
    }

    public int directionOrdinarySafeCoveredFaces(int direction) {
        validateDirection(direction);
        return directionOrdinarySafeCoveredFaces[direction];
    }

    public byte flags(int index) {
        if (index < 0 || index >= candidateCount) throw new IndexOutOfBoundsException(index);
        return flags[index];
    }

    public boolean colorInterpolationSafe(int index) {
        return (flags(index) & COLOR_INTERPOLATION_SAFE) != 0;
    }

    public boolean lightInterpolationSafe(int index) {
        return (flags(index) & LIGHT_INTERPOLATION_SAFE) != 0;
    }

    public boolean uvFieldSafe(int index) {
        return (flags(index) & UV_FIELD_SAFE) != 0;
    }

    public boolean ordinaryAttributeSafe(int index) {
        return (flags(index) & ORDINARY_ATTRIBUTE_SAFE) != 0;
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
