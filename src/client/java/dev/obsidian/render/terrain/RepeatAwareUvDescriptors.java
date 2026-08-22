package dev.obsidian.render.terrain;

import net.minecraft.core.Direction;

import java.util.Arrays;

/**
 * P3.4 dev9 sidecar proving whether a dev7 multi-face candidate admits an
 * exact sprite-local repeating UV descriptor. This class does not emit GPU
 * geometry and does not define full-atlas sampler wrapping as repetition.
 */
public final class RepeatAwareUvDescriptors {
    public static final int BYTES_PER_DESCRIPTOR = Short.BYTES + 4 * Integer.BYTES + Byte.BYTES;
    public static final int MAX_DESCRIPTORS = RenderMergeCandidates.MAX_CANDIDATES;
    public static final int MAX_RETAINED_BYTES = MAX_DESCRIPTORS * BYTES_PER_DESCRIPTOR;

    private static final int SIZE = SectionSnapshot.INTERIOR_SIZE;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Bounded primitive workspace intended to be owned and reused by one worker. */
    public static final class BuildScratch {
        private final short[] candidateIndices = new short[MAX_DESCRIPTORS];
        private final int[] uLowBits = new int[MAX_DESCRIPTORS];
        private final int[] uHighBits = new int[MAX_DESCRIPTORS];
        private final int[] vLowBits = new int[MAX_DESCRIPTORS];
        private final int[] vHighBits = new int[MAX_DESCRIPTORS];
        private final byte[] orientationSignatures = new byte[MAX_DESCRIPTORS];
        private final int[] probe = new int[5];
        private long uses;
        private int highWaterDescriptors;

        private void beginBuild() { uses++; }
        private void observeDescriptorCount(int descriptors) {
            highWaterDescriptors = Math.max(highWaterDescriptors, descriptors);
        }

        public long uses() { return uses; }
        public int highWaterDescriptors() { return highWaterDescriptors; }
        public int retainedScratchBytes() {
            return candidateIndices.length * Short.BYTES
                    + (uLowBits.length + uHighBits.length + vLowBits.length + vHighBits.length) * Integer.BYTES
                    + orientationSignatures.length
                    + probe.length * Integer.BYTES;
        }
    }

    private final short[] candidateIndices;
    private final int[] uLowBits;
    private final int[] uHighBits;
    private final int[] vLowBits;
    private final int[] vHighBits;
    private final byte[] orientationSignatures;
    private final int sourceMultiFaceCandidates;
    private final int sourceEligibleFaces;
    private final int representableMultiFace;
    private final int unrepresentableMultiFace;
    private final int repeatAwareFourVertexSafe;
    private final int repeatAwareFourVertexUnsafe;
    private final int safeCoveredFaces;
    private final int safeFacesSaved;
    private final int[] directionRepresentableCounts;
    private final int[] directionSafeCounts;
    private final int[] directionSafeCoveredFaces;
    private final long sourceCandidateFingerprint;
    private final long sourceRenderKeyFingerprint;
    private final long sourceSafetyFingerprint;
    private final long sourceBakedFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private RepeatAwareUvDescriptors(
            short[] candidateIndices,
            int[] uLowBits,
            int[] uHighBits,
            int[] vLowBits,
            int[] vHighBits,
            byte[] orientationSignatures,
            int sourceMultiFaceCandidates,
            int sourceEligibleFaces,
            int representableMultiFace,
            int unrepresentableMultiFace,
            int repeatAwareFourVertexSafe,
            int repeatAwareFourVertexUnsafe,
            int safeCoveredFaces,
            int safeFacesSaved,
            int[] directionRepresentableCounts,
            int[] directionSafeCounts,
            int[] directionSafeCoveredFaces,
            long sourceCandidateFingerprint,
            long sourceRenderKeyFingerprint,
            long sourceSafetyFingerprint,
            long sourceBakedFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.candidateIndices = candidateIndices;
        this.uLowBits = uLowBits;
        this.uHighBits = uHighBits;
        this.vLowBits = vLowBits;
        this.vHighBits = vHighBits;
        this.orientationSignatures = orientationSignatures;
        this.sourceMultiFaceCandidates = sourceMultiFaceCandidates;
        this.sourceEligibleFaces = sourceEligibleFaces;
        this.representableMultiFace = representableMultiFace;
        this.unrepresentableMultiFace = unrepresentableMultiFace;
        this.repeatAwareFourVertexSafe = repeatAwareFourVertexSafe;
        this.repeatAwareFourVertexUnsafe = repeatAwareFourVertexUnsafe;
        this.safeCoveredFaces = safeCoveredFaces;
        this.safeFacesSaved = safeFacesSaved;
        this.directionRepresentableCounts = directionRepresentableCounts;
        this.directionSafeCounts = directionSafeCounts;
        this.directionSafeCoveredFaces = directionSafeCoveredFaces;
        this.sourceCandidateFingerprint = sourceCandidateFingerprint;
        this.sourceRenderKeyFingerprint = sourceRenderKeyFingerprint;
        this.sourceSafetyFingerprint = sourceSafetyFingerprint;
        this.sourceBakedFingerprint = sourceBakedFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static RepeatAwareUvDescriptors build(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            OrdinaryQuadEmissionSafety safety,
            SectionBakedQuadSnapshot baked) {
        return build(candidates, renderKeys, safety, baked, new BuildScratch());
    }

    public static RepeatAwareUvDescriptors build(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            OrdinaryQuadEmissionSafety safety,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        validateSource(candidates, renderKeys, safety, baked);
        if (scratch == null) throw new NullPointerException("build scratch is required");
        long startNs = System.nanoTime();
        scratch.beginBuild();

        int descriptors = 0;
        int unrepresentable = 0;
        int safe = 0;
        int unsafe = 0;
        int safeCovered = 0;
        int safeSaved = 0;
        int[] representableByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];
        int[] safeByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];
        int[] safeFacesByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];

        for (int candidate = 0; candidate < candidates.candidateCount(); candidate++) {
            int packed = candidates.packedCandidate(candidate);
            int width = RenderMergeCandidates.width(packed);
            int height = RenderMergeCandidates.height(packed);
            int area = width * height;
            if (area <= 1) continue;

            int direction = RenderMergeCandidates.direction(packed);
            boolean representable = classifyInto(
                    candidates.representativeSourceQuad(candidate), direction, baked, scratch.probe);
            if (representable) {
                if (candidate > 0xFFFF) {
                    throw new IllegalStateException("Dev9 candidate index exceeds unsigned short range");
                }
                scratch.candidateIndices[descriptors] = (short) candidate;
                scratch.uLowBits[descriptors] = scratch.probe[0];
                scratch.uHighBits[descriptors] = scratch.probe[1];
                scratch.vLowBits[descriptors] = scratch.probe[2];
                scratch.vHighBits[descriptors] = scratch.probe[3];
                scratch.orientationSignatures[descriptors] = (byte) scratch.probe[4];
                descriptors++;
                representableByDirection[direction]++;
            } else {
                unrepresentable++;
            }

            boolean fourVertexSafe = representable
                    && safety.colorInterpolationSafe(candidate)
                    && safety.lightInterpolationSafe(candidate);
            if (fourVertexSafe) {
                safe++;
                safeCovered += area;
                safeSaved += area - 1;
                safeByDirection[direction]++;
                safeFacesByDirection[direction] += area;
            } else {
                unsafe++;
            }
        }

        scratch.observeDescriptorCount(descriptors);
        short[] retainedCandidateIndices = Arrays.copyOf(scratch.candidateIndices, descriptors);
        int[] retainedULow = Arrays.copyOf(scratch.uLowBits, descriptors);
        int[] retainedUHigh = Arrays.copyOf(scratch.uHighBits, descriptors);
        int[] retainedVLow = Arrays.copyOf(scratch.vLowBits, descriptors);
        int[] retainedVHigh = Arrays.copyOf(scratch.vHighBits, descriptors);
        byte[] retainedOrientation = Arrays.copyOf(scratch.orientationSignatures, descriptors);

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, candidates.fingerprint());
        hash = hashLong(hash, renderKeys.fingerprint());
        hash = hashLong(hash, safety.fingerprint());
        hash = hashLong(hash, baked.fingerprint());
        hash = hashInt(hash, candidates.multiFaceCandidates());
        hash = hashInt(hash, renderKeys.eligibleFaces());
        hash = hashInt(hash, descriptors);
        hash = hashInt(hash, unrepresentable);
        hash = hashInt(hash, safe);
        hash = hashInt(hash, unsafe);
        hash = hashInt(hash, safeCovered);
        hash = hashInt(hash, safeSaved);
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            hash = hashInt(hash, representableByDirection[direction]);
            hash = hashInt(hash, safeByDirection[direction]);
            hash = hashInt(hash, safeFacesByDirection[direction]);
        }
        for (int i = 0; i < descriptors; i++) {
            hash = hashInt(hash, Short.toUnsignedInt(retainedCandidateIndices[i]));
            hash = hashInt(hash, retainedULow[i]);
            hash = hashInt(hash, retainedUHigh[i]);
            hash = hashInt(hash, retainedVLow[i]);
            hash = hashInt(hash, retainedVHigh[i]);
            hash = hashInt(hash, Byte.toUnsignedInt(retainedOrientation[i]));
        }

        RepeatAwareUvDescriptors result = new RepeatAwareUvDescriptors(
                retainedCandidateIndices,
                retainedULow,
                retainedUHigh,
                retainedVLow,
                retainedVHigh,
                retainedOrientation,
                candidates.multiFaceCandidates(),
                renderKeys.eligibleFaces(),
                descriptors,
                unrepresentable,
                safe,
                unsafe,
                safeCovered,
                safeSaved,
                representableByDirection,
                safeByDirection,
                safeFacesByDirection,
                candidates.fingerprint(),
                renderKeys.fingerprint(),
                safety.fingerprint(),
                baked.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        result.validateAgainst(candidates, renderKeys, safety, baked, scratch);
        return result;
    }

    private static void validateSource(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            OrdinaryQuadEmissionSafety safety,
            SectionBakedQuadSnapshot baked) {
        if (candidates == null || renderKeys == null || safety == null || baked == null) {
            throw new NullPointerException("candidates, render keys, safety and baked snapshot are required");
        }
        if (candidates.sourceRenderKeyFingerprint() != renderKeys.fingerprint()
                || candidates.sourceBakedFingerprint() != baked.fingerprint()
                || renderKeys.sourceBakedFingerprint() != baked.fingerprint()
                || candidates.coveredEligibleFaces() != renderKeys.eligibleFaces()
                || safety.sourceCandidateFingerprint() != candidates.fingerprint()
                || safety.sourceRenderKeyFingerprint() != renderKeys.fingerprint()
                || safety.sourceBakedFingerprint() != baked.fingerprint()
                || safety.candidateCount() != candidates.candidateCount()
                || safety.multiFaceCandidates() != candidates.multiFaceCandidates()) {
            throw new IllegalArgumentException("Dev9 repeat-aware UV source identity/accounting mismatch");
        }
    }

    public void validateAgainst(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            OrdinaryQuadEmissionSafety safety,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        validateSource(candidates, renderKeys, safety, baked);
        if (scratch == null) throw new NullPointerException("validation scratch is required");
        if (sourceCandidateFingerprint != candidates.fingerprint()
                || sourceRenderKeyFingerprint != renderKeys.fingerprint()
                || sourceSafetyFingerprint != safety.fingerprint()
                || sourceBakedFingerprint != baked.fingerprint()
                || sourceMultiFaceCandidates != candidates.multiFaceCandidates()
                || sourceEligibleFaces != renderKeys.eligibleFaces()
                || candidateIndices.length != representableMultiFace
                || uLowBits.length != representableMultiFace
                || uHighBits.length != representableMultiFace
                || vLowBits.length != representableMultiFace
                || vHighBits.length != representableMultiFace
                || orientationSignatures.length != representableMultiFace
                || retainedBytes() != descriptorCount() * BYTES_PER_DESCRIPTOR) {
            throw new IllegalStateException("Dev9 retained/source identity mismatch");
        }

        int descriptor = 0;
        int unrepresentable = 0;
        int safe = 0;
        int unsafe = 0;
        int safeCovered = 0;
        int safeSaved = 0;
        int previousCandidate = -1;
        int[] representableByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];
        int[] safeByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];
        int[] safeFacesByDirection = new int[BinarySectionVisibility.DIRECTION_COUNT];

        for (int candidate = 0; candidate < candidates.candidateCount(); candidate++) {
            int packed = candidates.packedCandidate(candidate);
            int width = RenderMergeCandidates.width(packed);
            int height = RenderMergeCandidates.height(packed);
            int area = width * height;
            if (area <= 1) continue;

            int direction = RenderMergeCandidates.direction(packed);
            boolean representable = classifyInto(
                    candidates.representativeSourceQuad(candidate), direction, baked, scratch.probe);
            if (representable) {
                if (descriptor >= descriptorCount()) {
                    throw new IllegalStateException("Dev9 missing descriptor for representable candidate");
                }
                int storedCandidate = candidateIndex(descriptor);
                if (storedCandidate != candidate || storedCandidate <= previousCandidate
                        || uLowBits[descriptor] != scratch.probe[0]
                        || uHighBits[descriptor] != scratch.probe[1]
                        || vLowBits[descriptor] != scratch.probe[2]
                        || vHighBits[descriptor] != scratch.probe[3]
                        || Byte.toUnsignedInt(orientationSignatures[descriptor]) != scratch.probe[4]) {
                    throw new IllegalStateException("Dev9 descriptor differs from exact UV recomputation");
                }
                previousCandidate = storedCandidate;
                descriptor++;
                representableByDirection[direction]++;
            } else {
                unrepresentable++;
            }

            boolean fourVertexSafe = representable
                    && safety.colorInterpolationSafe(candidate)
                    && safety.lightInterpolationSafe(candidate);
            if (fourVertexSafe) {
                safe++;
                safeCovered += area;
                safeSaved += area - 1;
                safeByDirection[direction]++;
                safeFacesByDirection[direction] += area;
            } else {
                unsafe++;
            }
        }

        if (descriptor != descriptorCount()
                || representableMultiFace != descriptor
                || unrepresentableMultiFace != unrepresentable
                || representableMultiFace + unrepresentableMultiFace != sourceMultiFaceCandidates
                || repeatAwareFourVertexSafe != safe
                || repeatAwareFourVertexUnsafe != unsafe
                || repeatAwareFourVertexSafe + repeatAwareFourVertexUnsafe != sourceMultiFaceCandidates
                || safeCoveredFaces != safeCovered
                || safeFacesSaved != safeSaved
                || !Arrays.equals(directionRepresentableCounts, representableByDirection)
                || !Arrays.equals(directionSafeCounts, safeByDirection)
                || !Arrays.equals(directionSafeCoveredFaces, safeFacesByDirection)) {
            throw new IllegalStateException("Dev9 aggregate repeat-aware UV accounting mismatch");
        }
    }

    /**
     * Writes uLowBits,uHighBits,vLowBits,vHighBits,orientationSignature to out[0..4].
     */
    private static boolean classifyInto(
            int representative,
            int direction,
            SectionBakedQuadSnapshot baked,
            int[] out) {
        if (representative < 0 || representative >= baked.quadCount()
                || direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT
                || binaryDirection(baked.direction(representative)) != direction) {
            return false;
        }

        int packedSource = baked.sourceBlock(representative);
        int x = sourceX(packedSource);
        int y = sourceY(packedSource);
        int z = sourceZ(packedSource);
        int vertex0 = vertexForCorner(baked, representative, x, y, z, direction, 0);
        int vertex1 = vertexForCorner(baked, representative, x, y, z, direction, 1);
        int vertex2 = vertexForCorner(baked, representative, x, y, z, direction, 2);
        int vertex3 = vertexForCorner(baked, representative, x, y, z, direction, 3);
        if (vertex0 < 0 || vertex1 < 0 || vertex2 < 0 || vertex3 < 0) return false;

        int u0 = Float.floatToRawIntBits(baked.u(representative, vertex0));
        int u1 = Float.floatToRawIntBits(baked.u(representative, vertex1));
        int u2 = Float.floatToRawIntBits(baked.u(representative, vertex2));
        int u3 = Float.floatToRawIntBits(baked.u(representative, vertex3));
        int v0 = Float.floatToRawIntBits(baked.v(representative, vertex0));
        int v1 = Float.floatToRawIntBits(baked.v(representative, vertex1));
        int v2 = Float.floatToRawIntBits(baked.v(representative, vertex2));
        int v3 = Float.floatToRawIntBits(baked.v(representative, vertex3));

        int uOther;
        if (u1 != u0) uOther = u1;
        else if (u2 != u0) uOther = u2;
        else if (u3 != u0) uOther = u3;
        else return false;

        int vOther;
        if (v1 != v0) vOther = v1;
        else if (v2 != v0) vOther = v2;
        else if (v3 != v0) vOther = v3;
        else return false;

        if (!onlyTwo(u0, uOther, u1, u2, u3)
                || !onlyTwo(v0, vOther, v1, v2, v3)) {
            return false;
        }

        float uA = Float.intBitsToFloat(u0);
        float uB = Float.intBitsToFloat(uOther);
        float vA = Float.intBitsToFloat(v0);
        float vB = Float.intBitsToFloat(vOther);
        if (!Float.isFinite(uA) || !Float.isFinite(uB) || !Float.isFinite(vA) || !Float.isFinite(vB)
                || Float.compare(uA, uB) == 0 || Float.compare(vA, vB) == 0) {
            return false;
        }

        int uLow = Float.compare(uA, uB) < 0 ? u0 : uOther;
        int uHigh = Float.compare(uA, uB) < 0 ? uOther : u0;
        int vLow = Float.compare(vA, vB) < 0 ? v0 : vOther;
        int vHigh = Float.compare(vA, vB) < 0 ? vOther : v0;

        int c0 = uvCornerCode(u0, v0, uLow, uHigh, vLow, vHigh);
        int c1 = uvCornerCode(u1, v1, uLow, uHigh, vLow, vHigh);
        int c2 = uvCornerCode(u2, v2, uLow, uHigh, vLow, vHigh);
        int c3 = uvCornerCode(u3, v3, uLow, uHigh, vLow, vHigh);
        if (c0 < 0 || c1 < 0 || c2 < 0 || c3 < 0) return false;
        int seen = (1 << c0) | (1 << c1) | (1 << c2) | (1 << c3);
        if (seen != 0xF) return false;

        int dU = c0 ^ c1;
        int dV = c0 ^ c2;
        if (!((dU == 1 && dV == 2) || (dU == 2 && dV == 1))
                || c3 != (c0 ^ dU ^ dV)) {
            return false;
        }

        out[0] = uLow;
        out[1] = uHigh;
        out[2] = vLow;
        out[3] = vHigh;
        out[4] = c0 | (c1 << 2) | (c2 << 4) | (c3 << 6);
        return true;
    }

    private static boolean onlyTwo(int first, int second, int a, int b, int c) {
        return (a == first || a == second)
                && (b == first || b == second)
                && (c == first || c == second);
    }

    private static int uvCornerCode(
            int u, int v, int uLow, int uHigh, int vLow, int vHigh) {
        int uBit = u == uLow ? 0 : u == uHigh ? 1 : -1;
        int vBit = v == vLow ? 0 : v == vHigh ? 1 : -1;
        return uBit < 0 || vBit < 0 ? -1 : uBit | (vBit << 1);
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

    public boolean contentEquals(RepeatAwareUvDescriptors other) {
        return other != null
                && sourceMultiFaceCandidates == other.sourceMultiFaceCandidates
                && sourceEligibleFaces == other.sourceEligibleFaces
                && representableMultiFace == other.representableMultiFace
                && unrepresentableMultiFace == other.unrepresentableMultiFace
                && repeatAwareFourVertexSafe == other.repeatAwareFourVertexSafe
                && repeatAwareFourVertexUnsafe == other.repeatAwareFourVertexUnsafe
                && safeCoveredFaces == other.safeCoveredFaces
                && safeFacesSaved == other.safeFacesSaved
                && sourceCandidateFingerprint == other.sourceCandidateFingerprint
                && sourceRenderKeyFingerprint == other.sourceRenderKeyFingerprint
                && sourceSafetyFingerprint == other.sourceSafetyFingerprint
                && sourceBakedFingerprint == other.sourceBakedFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(candidateIndices, other.candidateIndices)
                && Arrays.equals(uLowBits, other.uLowBits)
                && Arrays.equals(uHighBits, other.uHighBits)
                && Arrays.equals(vLowBits, other.vLowBits)
                && Arrays.equals(vHighBits, other.vHighBits)
                && Arrays.equals(orientationSignatures, other.orientationSignatures)
                && Arrays.equals(directionRepresentableCounts, other.directionRepresentableCounts)
                && Arrays.equals(directionSafeCounts, other.directionSafeCounts)
                && Arrays.equals(directionSafeCoveredFaces, other.directionSafeCoveredFaces);
    }

    public int descriptorCount() { return candidateIndices.length; }
    public int sourceMultiFaceCandidates() { return sourceMultiFaceCandidates; }
    public int sourceEligibleFaces() { return sourceEligibleFaces; }
    public int representableMultiFace() { return representableMultiFace; }
    public int unrepresentableMultiFace() { return unrepresentableMultiFace; }
    public int repeatAwareFourVertexSafe() { return repeatAwareFourVertexSafe; }
    public int repeatAwareFourVertexUnsafe() { return repeatAwareFourVertexUnsafe; }
    public int safeCoveredFaces() { return safeCoveredFaces; }
    public int safeFacesSaved() { return safeFacesSaved; }
    public int safeReductionPermille() {
        return sourceEligibleFaces == 0 ? 0 : (int) (((long) safeFacesSaved * 1000L) / sourceEligibleFaces);
    }
    public int retainedBytes() { return descriptorCount() * BYTES_PER_DESCRIPTOR; }
    public long sourceCandidateFingerprint() { return sourceCandidateFingerprint; }
    public long sourceRenderKeyFingerprint() { return sourceRenderKeyFingerprint; }
    public long sourceSafetyFingerprint() { return sourceSafetyFingerprint; }
    public long sourceBakedFingerprint() { return sourceBakedFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    public int directionRepresentableCount(int direction) {
        validateDirection(direction);
        return directionRepresentableCounts[direction];
    }
    public int directionSafeCount(int direction) {
        validateDirection(direction);
        return directionSafeCounts[direction];
    }
    public int directionSafeCoveredFaces(int direction) {
        validateDirection(direction);
        return directionSafeCoveredFaces[direction];
    }

    public int candidateIndex(int descriptor) {
        validateDescriptor(descriptor);
        return Short.toUnsignedInt(candidateIndices[descriptor]);
    }
    public int uLowRawBits(int descriptor) { validateDescriptor(descriptor); return uLowBits[descriptor]; }
    public int uHighRawBits(int descriptor) { validateDescriptor(descriptor); return uHighBits[descriptor]; }
    public int vLowRawBits(int descriptor) { validateDescriptor(descriptor); return vLowBits[descriptor]; }
    public int vHighRawBits(int descriptor) { validateDescriptor(descriptor); return vHighBits[descriptor]; }
    public float uLow(int descriptor) { return Float.intBitsToFloat(uLowRawBits(descriptor)); }
    public float uHigh(int descriptor) { return Float.intBitsToFloat(uHighRawBits(descriptor)); }
    public float vLow(int descriptor) { return Float.intBitsToFloat(vLowRawBits(descriptor)); }
    public float vHigh(int descriptor) { return Float.intBitsToFloat(vHighRawBits(descriptor)); }
    public int orientationSignature(int descriptor) {
        validateDescriptor(descriptor);
        return Byte.toUnsignedInt(orientationSignatures[descriptor]);
    }

    private void validateDescriptor(int descriptor) {
        if (descriptor < 0 || descriptor >= descriptorCount()) throw new IndexOutOfBoundsException(descriptor);
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
