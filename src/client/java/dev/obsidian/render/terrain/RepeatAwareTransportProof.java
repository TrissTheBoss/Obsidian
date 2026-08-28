package dev.obsidian.render.terrain;

import java.util.Arrays;

/**
 * P3.4 dev10 proof that dev9 repeat-aware UV descriptors can be transported by
 * a four-vertex large-quad path without losing source vertex order or using
 * wrapped-coordinate implicit derivatives.
 *
 * <p>Dev11 retains the frozen dev10 proof/accounting unchanged and additionally
 * derives the validated {@link RepeatAwareGreedyMesh} on the same worker before
 * publishing this object. The transport proof records the obligations the GPU
 * path must obey: explicit gradients from unwrapped repeat coordinates, the
 * same live blocks-atlas texture/sampler, an outer-max endpoint policy, and
 * explicit raster review at internal reset boundaries.</p>
 */
public final class RepeatAwareTransportProof {
    public static final int BYTES_PER_RECORD = Short.BYTES + Byte.BYTES + Byte.BYTES;
    public static final int MAX_RECORDS = RenderMergeCandidates.MAX_CANDIDATES;
    public static final int MAX_RETAINED_BYTES = MAX_RECORDS * BYTES_PER_RECORD;

    public static final int FLAG_EXPLICIT_GRADIENT_REQUIRED = 1 << 0;
    public static final int FLAG_INTERNAL_S_RESET = 1 << 1;
    public static final int FLAG_INTERNAL_T_RESET = 1 << 2;
    public static final int FLAG_OUTER_EDGE_ENDPOINT_POLICY_REQUIRED = 1 << 3;
    public static final int FLAG_SAME_ATLAS_SAMPLER_REQUIRED = 1 << 4;
    public static final int FLAG_RASTER_BOUNDARY_REVIEW_REQUIRED = 1 << 5;
    public static final int KNOWN_FLAGS = FLAG_EXPLICIT_GRADIENT_REQUIRED
            | FLAG_INTERNAL_S_RESET
            | FLAG_INTERNAL_T_RESET
            | FLAG_OUTER_EDGE_ENDPOINT_POLICY_REQUIRED
            | FLAG_SAME_ATLAS_SAMPLER_REQUIRED
            | FLAG_RASTER_BOUNDARY_REVIEW_REQUIRED;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Bounded primitive workspace intended to be owned and reused by one worker. */
    public static final class BuildScratch {
        private final short[] candidateIndices = new short[MAX_RECORDS];
        private final byte[] sourceCornerOrders = new byte[MAX_RECORDS];
        private final byte[] obligationFlags = new byte[MAX_RECORDS];
        private final int[] directionRecordCounts = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private final int[] directionCoveredFaces = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private final int[] directionFacesSaved = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private long uses;
        private int highWaterRecords;

        private void beginBuild() {
            uses++;
            resetMetrics();
        }

        private void resetMetrics() {
            Arrays.fill(directionRecordCounts, 0);
            Arrays.fill(directionCoveredFaces, 0);
            Arrays.fill(directionFacesSaved, 0);
        }

        private void observeRecordCount(int records) {
            highWaterRecords = Math.max(highWaterRecords, records);
        }

        public long uses() { return uses; }
        public int highWaterRecords() { return highWaterRecords; }
        public int retainedScratchBytes() {
            return candidateIndices.length * Short.BYTES
                    + sourceCornerOrders.length
                    + obligationFlags.length
                    + (directionRecordCounts.length + directionCoveredFaces.length
                    + directionFacesSaved.length) * Integer.BYTES;
        }
    }

    private record Metrics(
            int records,
            int unsafe,
            int coveredFaces,
            int facesSaved,
            int explicitGradient,
            int internalS,
            int internalT,
            int internalBoth,
            int outerEdgePolicy,
            int sameAtlasSampler,
            int rasterBoundaryReview) {
    }

    private final short[] candidateIndices;
    private final byte[] sourceCornerOrders;
    private final byte[] obligationFlags;
    private final int sourceMultiFaceCandidates;
    private final int sourceRepresentableCandidates;
    private final int sourceFourVertexSafeCandidates;
    private final int unsafeCandidates;
    private final int coveredFaces;
    private final int facesSaved;
    private final int explicitGradientRequired;
    private final int internalSResetCandidates;
    private final int internalTResetCandidates;
    private final int internalBothResetCandidates;
    private final int outerEdgePolicyRequired;
    private final int sameAtlasSamplerRequired;
    private final int rasterBoundaryReviewRequired;
    private final int[] directionRecordCounts;
    private final int[] directionCoveredFaces;
    private final int[] directionFacesSaved;
    private final long sourceCandidateFingerprint;
    private final long sourceSafetyFingerprint;
    private final long sourceUvFingerprint;
    private final long sourceBakedFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;
    private RepeatAwareGreedyMesh greedyMesh;

    private RepeatAwareTransportProof(
            short[] candidateIndices,
            byte[] sourceCornerOrders,
            byte[] obligationFlags,
            int sourceMultiFaceCandidates,
            int sourceRepresentableCandidates,
            int sourceFourVertexSafeCandidates,
            Metrics metrics,
            int[] directionRecordCounts,
            int[] directionCoveredFaces,
            int[] directionFacesSaved,
            long sourceCandidateFingerprint,
            long sourceSafetyFingerprint,
            long sourceUvFingerprint,
            long sourceBakedFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.candidateIndices = candidateIndices;
        this.sourceCornerOrders = sourceCornerOrders;
        this.obligationFlags = obligationFlags;
        this.sourceMultiFaceCandidates = sourceMultiFaceCandidates;
        this.sourceRepresentableCandidates = sourceRepresentableCandidates;
        this.sourceFourVertexSafeCandidates = sourceFourVertexSafeCandidates;
        this.unsafeCandidates = metrics.unsafe;
        this.coveredFaces = metrics.coveredFaces;
        this.facesSaved = metrics.facesSaved;
        this.explicitGradientRequired = metrics.explicitGradient;
        this.internalSResetCandidates = metrics.internalS;
        this.internalTResetCandidates = metrics.internalT;
        this.internalBothResetCandidates = metrics.internalBoth;
        this.outerEdgePolicyRequired = metrics.outerEdgePolicy;
        this.sameAtlasSamplerRequired = metrics.sameAtlasSampler;
        this.rasterBoundaryReviewRequired = metrics.rasterBoundaryReview;
        this.directionRecordCounts = directionRecordCounts;
        this.directionCoveredFaces = directionCoveredFaces;
        this.directionFacesSaved = directionFacesSaved;
        this.sourceCandidateFingerprint = sourceCandidateFingerprint;
        this.sourceSafetyFingerprint = sourceSafetyFingerprint;
        this.sourceUvFingerprint = sourceUvFingerprint;
        this.sourceBakedFingerprint = sourceBakedFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static RepeatAwareTransportProof build(
            RenderMergeCandidates candidates,
            OrdinaryQuadEmissionSafety safety,
            RepeatAwareUvDescriptors uv,
            SectionBakedQuadSnapshot baked) {
        return build(candidates, safety, uv, baked, new BuildScratch());
    }

    public static RepeatAwareTransportProof build(
            RenderMergeCandidates candidates,
            OrdinaryQuadEmissionSafety safety,
            RepeatAwareUvDescriptors uv,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        validateSource(candidates, safety, uv, baked);
        if (scratch == null) throw new NullPointerException("build scratch is required");
        long startNs = System.nanoTime();
        scratch.beginBuild();

        Metrics metrics = recompute(candidates, safety, uv, baked, scratch, true);
        scratch.observeRecordCount(metrics.records);

        short[] retainedCandidates = Arrays.copyOf(scratch.candidateIndices, metrics.records);
        byte[] retainedOrders = Arrays.copyOf(scratch.sourceCornerOrders, metrics.records);
        byte[] retainedFlags = Arrays.copyOf(scratch.obligationFlags, metrics.records);
        int[] retainedDirectionCounts = Arrays.copyOf(
                scratch.directionRecordCounts, scratch.directionRecordCounts.length);
        int[] retainedDirectionFaces = Arrays.copyOf(
                scratch.directionCoveredFaces, scratch.directionCoveredFaces.length);
        int[] retainedDirectionSaved = Arrays.copyOf(
                scratch.directionFacesSaved, scratch.directionFacesSaved.length);

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, candidates.fingerprint());
        hash = hashLong(hash, safety.fingerprint());
        hash = hashLong(hash, uv.fingerprint());
        hash = hashLong(hash, baked.fingerprint());
        hash = hashInt(hash, candidates.multiFaceCandidates());
        hash = hashInt(hash, uv.representableMultiFace());
        hash = hashInt(hash, uv.repeatAwareFourVertexSafe());
        hash = hashMetrics(hash, metrics);
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            hash = hashInt(hash, retainedDirectionCounts[direction]);
            hash = hashInt(hash, retainedDirectionFaces[direction]);
            hash = hashInt(hash, retainedDirectionSaved[direction]);
        }
        for (int record = 0; record < metrics.records; record++) {
            hash = hashInt(hash, Short.toUnsignedInt(retainedCandidates[record]));
            hash = hashInt(hash, Byte.toUnsignedInt(retainedOrders[record]));
            hash = hashInt(hash, Byte.toUnsignedInt(retainedFlags[record]));
        }

        RepeatAwareTransportProof result = new RepeatAwareTransportProof(
                retainedCandidates,
                retainedOrders,
                retainedFlags,
                candidates.multiFaceCandidates(),
                uv.representableMultiFace(),
                uv.repeatAwareFourVertexSafe(),
                metrics,
                retainedDirectionCounts,
                retainedDirectionFaces,
                retainedDirectionSaved,
                candidates.fingerprint(),
                safety.fingerprint(),
                uv.fingerprint(),
                baked.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        result.validateAgainst(candidates, safety, uv, baked, scratch);

        CanonicalFaceRenderKeys renderKeys = uv.sourceRenderKeys();
        SectionSnapshot snapshot = renderKeys == null ? null : renderKeys.sourceSnapshot();
        if (renderKeys == null || snapshot == null) {
            throw new IllegalStateException("Dev11 worker hybrid mesh lost retained render-key/snapshot source identity");
        }
        result.greedyMesh = RepeatAwareGreedyMesh.build(
                snapshot, baked, renderKeys, candidates, uv, result);
        return result;
    }

    private static void validateSource(
            RenderMergeCandidates candidates,
            OrdinaryQuadEmissionSafety safety,
            RepeatAwareUvDescriptors uv,
            SectionBakedQuadSnapshot baked) {
        if (candidates == null || safety == null || uv == null || baked == null) {
            throw new NullPointerException("candidates, safety, repeat-aware UV and baked snapshot are required");
        }
        if (candidates.sourceBakedFingerprint() != baked.fingerprint()
                || safety.sourceCandidateFingerprint() != candidates.fingerprint()
                || safety.sourceBakedFingerprint() != baked.fingerprint()
                || uv.sourceCandidateFingerprint() != candidates.fingerprint()
                || uv.sourceSafetyFingerprint() != safety.fingerprint()
                || uv.sourceBakedFingerprint() != baked.fingerprint()
                || safety.candidateCount() != candidates.candidateCount()
                || safety.multiFaceCandidates() != candidates.multiFaceCandidates()
                || uv.sourceMultiFaceCandidates() != candidates.multiFaceCandidates()
                || uv.representableMultiFace() + uv.unrepresentableMultiFace()
                != candidates.multiFaceCandidates()
                || uv.repeatAwareFourVertexSafe() + uv.repeatAwareFourVertexUnsafe()
                != candidates.multiFaceCandidates()) {
            throw new IllegalArgumentException("Dev10 transport-proof source identity/accounting mismatch");
        }
    }

    private static Metrics recompute(
            RenderMergeCandidates candidates,
            OrdinaryQuadEmissionSafety safety,
            RepeatAwareUvDescriptors uv,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch,
            boolean writeRecords) {
        scratch.resetMetrics();
        int records = 0;
        int unsafe = 0;
        int covered = 0;
        int saved = 0;
        int explicitGradient = 0;
        int internalS = 0;
        int internalT = 0;
        int internalBoth = 0;
        int outerEdge = 0;
        int sameSampler = 0;
        int rasterReview = 0;
        int descriptor = 0;

        for (int candidate = 0; candidate < candidates.candidateCount(); candidate++) {
            int packed = candidates.packedCandidate(candidate);
            int width = RenderMergeCandidates.width(packed);
            int height = RenderMergeCandidates.height(packed);
            int area = width * height;
            if (area <= 1) continue;

            int descriptorIndex = -1;
            if (descriptor < uv.descriptorCount() && uv.candidateIndex(descriptor) == candidate) {
                descriptorIndex = descriptor++;
            }
            boolean safe = descriptorIndex >= 0
                    && safety.colorInterpolationSafe(candidate)
                    && safety.lightInterpolationSafe(candidate);
            if (!safe) {
                unsafe++;
                continue;
            }

            if (candidate > 0xFFFF) {
                throw new IllegalStateException("Dev10 candidate index exceeds unsigned-short range");
            }
            int direction = RenderMergeCandidates.direction(packed);
            int representative = candidates.representativeSourceQuad(candidate);
            int sourceOrder = sourceCornerOrderSignature(baked, representative, direction);
            if (sourceOrder < 0 || !validateTransportDescriptor(
                    uv, descriptorIndex, baked, representative, direction)) {
                throw new IllegalStateException("Dev10 safe candidate failed frozen transport proof");
            }

            int flags = FLAG_EXPLICIT_GRADIENT_REQUIRED
                    | FLAG_OUTER_EDGE_ENDPOINT_POLICY_REQUIRED
                    | FLAG_SAME_ATLAS_SAMPLER_REQUIRED
                    | FLAG_RASTER_BOUNDARY_REVIEW_REQUIRED;
            boolean sReset = width > 1;
            boolean tReset = height > 1;
            if (sReset) flags |= FLAG_INTERNAL_S_RESET;
            if (tReset) flags |= FLAG_INTERNAL_T_RESET;
            if (!sReset && !tReset) {
                throw new IllegalStateException("Dev10 multi-face record has no internal repeat boundary");
            }

            if (writeRecords) {
                scratch.candidateIndices[records] = (short) candidate;
                scratch.sourceCornerOrders[records] = (byte) sourceOrder;
                scratch.obligationFlags[records] = (byte) flags;
            }
            records++;
            covered += area;
            saved += area - 1;
            explicitGradient++;
            outerEdge++;
            sameSampler++;
            rasterReview++;
            if (sReset) internalS++;
            if (tReset) internalT++;
            if (sReset && tReset) internalBoth++;
            scratch.directionRecordCounts[direction]++;
            scratch.directionCoveredFaces[direction] += area;
            scratch.directionFacesSaved[direction] += area - 1;
        }

        if (descriptor != uv.descriptorCount()) {
            throw new IllegalStateException("Dev10 did not consume every dev9 UV descriptor in candidate order");
        }
        return new Metrics(records, unsafe, covered, saved, explicitGradient,
                internalS, internalT, internalBoth, outerEdge, sameSampler, rasterReview);
    }

    private static boolean validateTransportDescriptor(
            RepeatAwareUvDescriptors uv,
            int descriptor,
            SectionBakedQuadSnapshot baked,
            int representative,
            int direction) {
        float uLow = uv.uLow(descriptor);
        float uHigh = uv.uHigh(descriptor);
        float vLow = uv.vLow(descriptor);
        float vHigh = uv.vHigh(descriptor);
        float uSpan = uHigh - uLow;
        float vSpan = vHigh - vLow;
        if (!Float.isFinite(uLow) || !Float.isFinite(uHigh)
                || !Float.isFinite(vLow) || !Float.isFinite(vHigh)
                || !Float.isFinite(uSpan) || !Float.isFinite(vSpan)
                || Float.compare(uLow, uHigh) >= 0 || Float.compare(vLow, vHigh) >= 0
                || Float.compare(uSpan, 0.0f) <= 0 || Float.compare(vSpan, 0.0f) <= 0) {
            return false;
        }

        int orientation = uv.orientationSignature(descriptor);
        int c0 = orientation & 0x3;
        int c1 = (orientation >>> 2) & 0x3;
        int c2 = (orientation >>> 4) & 0x3;
        int c3 = (orientation >>> 6) & 0x3;
        int seen = (1 << c0) | (1 << c1) | (1 << c2) | (1 << c3);
        int deltaS = c0 ^ c1;
        int deltaT = c0 ^ c2;
        if (seen != 0xF
                || !((deltaS == 1 && deltaT == 2) || (deltaS == 2 && deltaT == 1))
                || c3 != (c0 ^ deltaS ^ deltaT)) {
            return false;
        }

        int u00 = c0 & 1;
        int u10 = c1 & 1;
        int u01 = c2 & 1;
        int v00 = (c0 >>> 1) & 1;
        int v10 = (c1 >>> 1) & 1;
        int v01 = (c2 >>> 1) & 1;
        int determinant = (u10 - u00) * (v01 - v00) - (u01 - u00) * (v10 - v00);
        if (Math.abs(determinant) != 1) return false;

        int packedSource = baked.sourceBlock(representative);
        int x = packedSource & 0xF;
        int y = (packedSource >>> 4) & 0xF;
        int z = (packedSource >>> 8) & 0xF;
        for (int corner = 0; corner < 4; corner++) {
            int vertex = vertexForCorner(baked, representative, x, y, z, direction, corner);
            if (vertex < 0) return false;
            int uvCorner = switch (corner) {
                case 0 -> c0;
                case 1 -> c1;
                case 2 -> c2;
                case 3 -> c3;
                default -> throw new AssertionError();
            };
            int expectedU = (uvCorner & 1) == 0
                    ? uv.uLowRawBits(descriptor) : uv.uHighRawBits(descriptor);
            int expectedV = (uvCorner & 2) == 0
                    ? uv.vLowRawBits(descriptor) : uv.vHighRawBits(descriptor);
            if (Float.floatToRawIntBits(baked.u(representative, vertex)) != expectedU
                    || Float.floatToRawIntBits(baked.v(representative, vertex)) != expectedV) {
                return false;
            }
        }
        return true;
    }

    private static int sourceCornerOrderSignature(
            SectionBakedQuadSnapshot baked,
            int quad,
            int direction) {
        if (quad < 0 || quad >= baked.quadCount()) return -1;
        int packedSource = baked.sourceBlock(quad);
        int x = packedSource & 0xF;
        int y = (packedSource >>> 4) & 0xF;
        int z = (packedSource >>> 8) & 0xF;
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
        float fixed;
        float fixedExpected;
        float s;
        float t;
        int sLow;
        int sHigh;
        int tLow;
        int tHigh;

        switch (direction) {
            case BinarySectionVisibility.WEST -> {
                fixed = px; fixedExpected = x;
                s = pz; sLow = z; sHigh = z + 1;
                t = py; tLow = y; tHigh = y + 1;
            }
            case BinarySectionVisibility.EAST -> {
                fixed = px; fixedExpected = x + 1;
                s = pz; sLow = z; sHigh = z + 1;
                t = py; tLow = y; tHigh = y + 1;
            }
            case BinarySectionVisibility.DOWN -> {
                fixed = py; fixedExpected = y;
                s = px; sLow = x; sHigh = x + 1;
                t = pz; tLow = z; tHigh = z + 1;
            }
            case BinarySectionVisibility.UP -> {
                fixed = py; fixedExpected = y + 1;
                s = px; sLow = x; sHigh = x + 1;
                t = pz; tLow = z; tHigh = z + 1;
            }
            case BinarySectionVisibility.NORTH -> {
                fixed = pz; fixedExpected = z;
                s = px; sLow = x; sHigh = x + 1;
                t = py; tLow = y; tHigh = y + 1;
            }
            case BinarySectionVisibility.SOUTH -> {
                fixed = pz; fixedExpected = z + 1;
                s = px; sLow = x; sHigh = x + 1;
                t = py; tLow = y; tHigh = y + 1;
            }
            default -> { return -1; }
        }
        if (!rawEquals(fixed, fixedExpected)) return -1;
        int sBit = rawEquals(s, sLow) ? 0 : rawEquals(s, sHigh) ? 1 : -1;
        int tBit = rawEquals(t, tLow) ? 0 : rawEquals(t, tHigh) ? 1 : -1;
        return sBit < 0 || tBit < 0 ? -1 : sBit | (tBit << 1);
    }

    private static boolean rawEquals(float value, float expected) {
        return Float.floatToRawIntBits(value) == Float.floatToRawIntBits(expected);
    }

    public void validateAgainst(
            RenderMergeCandidates candidates,
            OrdinaryQuadEmissionSafety safety,
            RepeatAwareUvDescriptors uv,
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch) {
        validateSource(candidates, safety, uv, baked);
        if (scratch == null) throw new NullPointerException("validation scratch is required");
        if (sourceCandidateFingerprint != candidates.fingerprint()
                || sourceSafetyFingerprint != safety.fingerprint()
                || sourceUvFingerprint != uv.fingerprint()
                || sourceBakedFingerprint != baked.fingerprint()
                || sourceMultiFaceCandidates != candidates.multiFaceCandidates()
                || sourceRepresentableCandidates != uv.representableMultiFace()
                || sourceFourVertexSafeCandidates != uv.repeatAwareFourVertexSafe()
                || candidateIndices.length != sourceFourVertexSafeCandidates
                || sourceCornerOrders.length != candidateIndices.length
                || obligationFlags.length != candidateIndices.length
                || retainedBytes() != recordCount() * BYTES_PER_RECORD) {
            throw new IllegalStateException("Dev10 retained/source identity mismatch");
        }

        Metrics expected = recompute(candidates, safety, uv, baked, scratch, true);
        if (expected.records != recordCount()
                || expected.unsafe != unsafeCandidates
                || expected.coveredFaces != coveredFaces
                || expected.facesSaved != facesSaved
                || expected.explicitGradient != explicitGradientRequired
                || expected.internalS != internalSResetCandidates
                || expected.internalT != internalTResetCandidates
                || expected.internalBoth != internalBothResetCandidates
                || expected.outerEdgePolicy != outerEdgePolicyRequired
                || expected.sameAtlasSampler != sameAtlasSamplerRequired
                || expected.rasterBoundaryReview != rasterBoundaryReviewRequired
                || !prefixEquals(candidateIndices, scratch.candidateIndices, expected.records)
                || !prefixEquals(sourceCornerOrders, scratch.sourceCornerOrders, expected.records)
                || !prefixEquals(obligationFlags, scratch.obligationFlags, expected.records)
                || !Arrays.equals(directionRecordCounts, scratch.directionRecordCounts)
                || !Arrays.equals(directionCoveredFaces, scratch.directionCoveredFaces)
                || !Arrays.equals(directionFacesSaved, scratch.directionFacesSaved)) {
            throw new IllegalStateException("Dev10 transport-proof recomputation mismatch");
        }
        if (recordCount() != sourceFourVertexSafeCandidates
                || unsafeCandidates != sourceMultiFaceCandidates - recordCount()
                || explicitGradientRequired != recordCount()
                || outerEdgePolicyRequired != recordCount()
                || sameAtlasSamplerRequired != recordCount()
                || rasterBoundaryReviewRequired != recordCount()) {
            throw new IllegalStateException("Dev10 frozen transport accounting mismatch");
        }
    }

    public boolean contentEquals(RepeatAwareTransportProof other) {
        return other != null
                && sourceMultiFaceCandidates == other.sourceMultiFaceCandidates
                && sourceRepresentableCandidates == other.sourceRepresentableCandidates
                && sourceFourVertexSafeCandidates == other.sourceFourVertexSafeCandidates
                && unsafeCandidates == other.unsafeCandidates
                && coveredFaces == other.coveredFaces
                && facesSaved == other.facesSaved
                && explicitGradientRequired == other.explicitGradientRequired
                && internalSResetCandidates == other.internalSResetCandidates
                && internalTResetCandidates == other.internalTResetCandidates
                && internalBothResetCandidates == other.internalBothResetCandidates
                && outerEdgePolicyRequired == other.outerEdgePolicyRequired
                && sameAtlasSamplerRequired == other.sameAtlasSamplerRequired
                && rasterBoundaryReviewRequired == other.rasterBoundaryReviewRequired
                && sourceCandidateFingerprint == other.sourceCandidateFingerprint
                && sourceSafetyFingerprint == other.sourceSafetyFingerprint
                && sourceUvFingerprint == other.sourceUvFingerprint
                && sourceBakedFingerprint == other.sourceBakedFingerprint
                && fingerprint == other.fingerprint
                && greedyMesh != null
                && other.greedyMesh != null
                && greedyMesh.contentEquals(other.greedyMesh)
                && Arrays.equals(candidateIndices, other.candidateIndices)
                && Arrays.equals(sourceCornerOrders, other.sourceCornerOrders)
                && Arrays.equals(obligationFlags, other.obligationFlags)
                && Arrays.equals(directionRecordCounts, other.directionRecordCounts)
                && Arrays.equals(directionCoveredFaces, other.directionCoveredFaces)
                && Arrays.equals(directionFacesSaved, other.directionFacesSaved);
    }

    public int recordCount() { return candidateIndices.length; }
    public int sourceMultiFaceCandidates() { return sourceMultiFaceCandidates; }
    public int sourceRepresentableCandidates() { return sourceRepresentableCandidates; }
    public int sourceFourVertexSafeCandidates() { return sourceFourVertexSafeCandidates; }
    public int unsafeCandidates() { return unsafeCandidates; }
    public int coveredFaces() { return coveredFaces; }
    public int facesSaved() { return facesSaved; }
    public int reductionPermille(int sourceEligibleFaces) {
        return sourceEligibleFaces <= 0 ? 0 : (int) (((long) facesSaved * 1000L) / sourceEligibleFaces);
    }
    public int explicitGradientRequired() { return explicitGradientRequired; }
    public int internalSResetCandidates() { return internalSResetCandidates; }
    public int internalTResetCandidates() { return internalTResetCandidates; }
    public int internalBothResetCandidates() { return internalBothResetCandidates; }
    public int outerEdgePolicyRequired() { return outerEdgePolicyRequired; }
    public int sameAtlasSamplerRequired() { return sameAtlasSamplerRequired; }
    public int rasterBoundaryReviewRequired() { return rasterBoundaryReviewRequired; }
    public int retainedBytes() { return recordCount() * BYTES_PER_RECORD; }
    public long sourceCandidateFingerprint() { return sourceCandidateFingerprint; }
    public long sourceSafetyFingerprint() { return sourceSafetyFingerprint; }
    public long sourceUvFingerprint() { return sourceUvFingerprint; }
    public long sourceBakedFingerprint() { return sourceBakedFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }
    public RepeatAwareGreedyMesh greedyMesh() {
        if (greedyMesh == null) throw new IllegalStateException("Dev11 worker hybrid mesh was not published");
        return greedyMesh;
    }

    public int candidateIndex(int record) {
        validateRecord(record);
        return Short.toUnsignedInt(candidateIndices[record]);
    }
    public int sourceCornerOrderSignature(int record) {
        validateRecord(record);
        return Byte.toUnsignedInt(sourceCornerOrders[record]);
    }
    public int obligationFlags(int record) {
        validateRecord(record);
        return Byte.toUnsignedInt(obligationFlags[record]);
    }
    public boolean hasFlag(int record, int flag) {
        if ((flag & ~KNOWN_FLAGS) != 0 || Integer.bitCount(flag) != 1) {
            throw new IllegalArgumentException("Unknown or composite dev10 transport flag");
        }
        return (obligationFlags(record) & flag) != 0;
    }
    public int directionRecordCount(int direction) {
        validateDirection(direction);
        return directionRecordCounts[direction];
    }
    public int directionCoveredFaces(int direction) {
        validateDirection(direction);
        return directionCoveredFaces[direction];
    }
    public int directionFacesSaved(int direction) {
        validateDirection(direction);
        return directionFacesSaved[direction];
    }

    private void validateRecord(int record) {
        if (record < 0 || record >= recordCount()) throw new IndexOutOfBoundsException(record);
    }
    private static void validateDirection(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IndexOutOfBoundsException(direction);
        }
    }

    private static boolean prefixEquals(short[] retained, short[] scratch, int count) {
        if (retained.length != count) return false;
        for (int i = 0; i < count; i++) if (retained[i] != scratch[i]) return false;
        return true;
    }
    private static boolean prefixEquals(byte[] retained, byte[] scratch, int count) {
        if (retained.length != count) return false;
        for (int i = 0; i < count; i++) if (retained[i] != scratch[i]) return false;
        return true;
    }

    private static long hashMetrics(long hash, Metrics metrics) {
        hash = hashInt(hash, metrics.records);
        hash = hashInt(hash, metrics.unsafe);
        hash = hashInt(hash, metrics.coveredFaces);
        hash = hashInt(hash, metrics.facesSaved);
        hash = hashInt(hash, metrics.explicitGradient);
        hash = hashInt(hash, metrics.internalS);
        hash = hashInt(hash, metrics.internalT);
        hash = hashInt(hash, metrics.internalBoth);
        hash = hashInt(hash, metrics.outerEdgePolicy);
        hash = hashInt(hash, metrics.sameAtlasSampler);
        return hashInt(hash, metrics.rasterBoundaryReview);
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
