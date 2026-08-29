package dev.obsidian.render.terrain;

import java.util.Arrays;

/**
 * P3.9 dev16 shadow-only fixed-slice projection. It consumes the already-built
 * production artifacts, splits production merge identities at fixed Y-slice
 * ownership boundaries, proves exact selected source/canonical coverage, and
 * never creates or mutates GPU state.
 */
public final class PartialRemeshShadowResult {
    public static final int FAILURE_NONE = 0;
    public static final int FAILURE_UNSELECTED_CHANGED = 1;
    public static final int FAILURE_REFERENCE_VISIBILITY = 2;
    public static final int FAILURE_SOURCE_DUPLICATE = 3;
    public static final int FAILURE_SOURCE_MISSING = 4;
    public static final int FAILURE_MERGED_IDENTITY = 5;
    public static final int FAILURE_ACCOUNTING = 6;
    public static final int FAILURE_EXCEPTION = 7;

    public static final class BuildScratch {
        private final boolean[] seenSource = new boolean[SectionBakedQuadSnapshot.MAX_QUADS];
        private final long[] referenceBits = new long[BinarySectionVisibility.TOTAL_WORDS];
        private long uses;
        private int highWaterSelectedQuads;
        private void begin(int sourceQuads) {
            Arrays.fill(seenSource, 0, sourceQuads, false);
            Arrays.fill(referenceBits, 0L);
            uses++;
        }
        private void observe(int selected) { highWaterSelectedQuads = Math.max(highWaterSelectedQuads, selected); }
        public long uses() { return uses; }
        public int highWaterSelectedQuads() { return highWaterSelectedQuads; }
        public int retainedScratchBytes() { return seenSource.length + referenceBits.length * Long.BYTES; }
    }

    private static final class ShadowFailure extends RuntimeException {
        final int code;
        final int index;
        ShadowFailure(int code, int index) { this.code = code; this.index = index; }
    }

    private final long episodeId;
    private final int sliceMask;
    private final int selectedSlices;
    private final int selectedCells;
    private final int selectedSourceQuads;
    private final int selectedReferenceFaces;
    private final int topologyFragments;
    private final int mergeCandidateFragments;
    private final int passthroughIdentities;
    private final int mergedIdentities;
    private final int mergedCoveredSourceFaces;
    private final int outputQuads;
    private final long outputVertexBytes;
    private final long outputIndexBytes;
    private final int forcedBoundarySplits;
    private final int assembledQuads;
    private final int productionQuads;
    private final int inflationPermille;
    private final long buildTimeNs;
    private final boolean exact;
    private final boolean unselectedStable;
    private final int failureCode;
    private final int failureIndex;

    private PartialRemeshShadowResult(
            long episodeId, int sliceMask, int selectedSlices, int selectedCells,
            int selectedSourceQuads, int selectedReferenceFaces,
            int topologyFragments, int mergeCandidateFragments,
            int passthroughIdentities, int mergedIdentities, int mergedCoveredSourceFaces,
            int outputQuads, long outputVertexBytes, long outputIndexBytes,
            int forcedBoundarySplits, int assembledQuads, int productionQuads,
            int inflationPermille, long buildTimeNs, boolean exact, boolean unselectedStable,
            int failureCode, int failureIndex) {
        this.episodeId = episodeId;
        this.sliceMask = sliceMask;
        this.selectedSlices = selectedSlices;
        this.selectedCells = selectedCells;
        this.selectedSourceQuads = selectedSourceQuads;
        this.selectedReferenceFaces = selectedReferenceFaces;
        this.topologyFragments = topologyFragments;
        this.mergeCandidateFragments = mergeCandidateFragments;
        this.passthroughIdentities = passthroughIdentities;
        this.mergedIdentities = mergedIdentities;
        this.mergedCoveredSourceFaces = mergedCoveredSourceFaces;
        this.outputQuads = outputQuads;
        this.outputVertexBytes = outputVertexBytes;
        this.outputIndexBytes = outputIndexBytes;
        this.forcedBoundarySplits = forcedBoundarySplits;
        this.assembledQuads = assembledQuads;
        this.productionQuads = productionQuads;
        this.inflationPermille = inflationPermille;
        this.buildTimeNs = buildTimeNs;
        this.exact = exact;
        this.unselectedStable = unselectedStable;
        this.failureCode = failureCode;
        this.failureIndex = failureIndex;
    }

    public static PartialRemeshShadowResult build(
            PartialRemeshShadowRequest request,
            PartialRemeshSliceTruth currentTruth,
            SectionBakedQuadSnapshot baked,
            ReferenceFaceMesh reference,
            BinarySectionVisibility visibility,
            GreedySectionRectangles rectangles,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareTransportProof transport,
            RepeatAwareGreedyMesh production,
            BuildScratch scratch) {
        long start = System.nanoTime();
        try {
            if (request == null || currentTruth == null || baked == null || reference == null
                    || visibility == null || rectangles == null || renderKeys == null
                    || candidates == null || transport == null || production == null || scratch == null) {
                throw new NullPointerException("dev16 shadow inputs");
            }
            scratch.begin(baked.quadCount());
            for (int slice = 0; slice < PartialRemeshDirtyProvenance.SLICE_COUNT; slice++) {
                if (!request.selected(slice) && request.previousFingerprint(slice) != currentTruth.fingerprint(slice)) {
                    fail(FAILURE_UNSELECTED_CHANGED, slice);
                }
            }

            int mask = request.sliceMask();
            int selectedSlices = request.selectedSliceCount();
            int selectedCells = selectedSlices * 4 * SectionSnapshot.INTERIOR_SIZE * SectionSnapshot.INTERIOR_SIZE;

            for (int face = 0; face < reference.faceCount(); face++) {
                int packed = reference.packedFace(face);
                int x = packed & 0xF;
                int y = (packed >>> 4) & 0xF;
                int z = (packed >>> 8) & 0xF;
                int direction = (packed >>> 12) & 0x7;
                int bit = ((y * 16) + z) * 16 + x;
                scratch.referenceBits[direction * BinarySectionVisibility.WORDS_PER_DIRECTION + (bit >>> 6)] |= 1L << (bit & 63);
            }

            int selectedReference = 0;
            int selectedVisibility = 0;
            for (int y = 0; y < 16; y++) {
                if (!selected(mask, y >>> 2)) continue;
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    int bit = ((y * 16) + z) * 16 + x;
                    for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
                        boolean visible = visibility.hasFace(x, y, z, direction);
                        boolean ref = (scratch.referenceBits[direction * BinarySectionVisibility.WORDS_PER_DIRECTION + (bit >>> 6)]
                                & (1L << (bit & 63))) != 0L;
                        if (visible) selectedVisibility++;
                        if (ref) selectedReference++;
                        if (visible != ref) fail(FAILURE_REFERENCE_VISIBILITY, bit | (direction << 12));
                    }
                }
            }
            if (selectedReference != selectedVisibility) fail(FAILURE_REFERENCE_VISIBILITY, -1);

            int topologyFragments = 0;
            for (int i = 0; i < rectangles.rectangleCount(); i++) {
                int p = rectangles.packedRectangle(i);
                topologyFragments += selectedFragments(
                        GreedySectionRectangles.direction(p), GreedySectionRectangles.plane(p),
                        GreedySectionRectangles.v(p), GreedySectionRectangles.height(p), mask);
            }

            int candidateFragments = 0;
            int forcedSplits = 0;
            for (int i = 0; i < candidates.candidateCount(); i++) {
                int p = candidates.packedCandidate(i);
                int all = allFragments(RenderMergeCandidates.direction(p), RenderMergeCandidates.plane(p),
                        RenderMergeCandidates.v(p), RenderMergeCandidates.height(p));
                if (all > 1) forcedSplits++;
                candidateFragments += selectedFragments(RenderMergeCandidates.direction(p), RenderMergeCandidates.plane(p),
                        RenderMergeCandidates.v(p), RenderMergeCandidates.height(p), mask);
            }

            int passthrough = 0;
            int merged = 0;
            int mergedCovered = 0;
            int outputQuads = 0;
            long vertexBytes = 0L;
            long indexBytes = 0L;
            int selectedSource = 0;

            for (int i = 0; i < production.passthroughQuadCount(); i++) {
                int source = production.passthroughSourceQuad(i);
                int slice = sourceSlice(baked, source);
                if (!selected(mask, slice)) continue;
                selectedSource += markSource(scratch, source);
                passthrough++;
                outputQuads++;
                vertexBytes += (long) RepeatAwareGreedyMesh.VERTICES_PER_QUAD * BakedSectionMesh.BYTES_PER_VERTEX;
                indexBytes += (long) RepeatAwareGreedyMesh.INDICES_PER_QUAD * Integer.BYTES;
            }

            int splitInflation = 0;
            for (int record = 0; record < transport.recordCount(); record++) {
                int candidate = transport.candidateIndex(record);
                int p = candidates.packedCandidate(candidate);
                int direction = RenderMergeCandidates.direction(p);
                int plane = RenderMergeCandidates.plane(p);
                int u0 = RenderMergeCandidates.u(p);
                int v0 = RenderMergeCandidates.v(p);
                int width = RenderMergeCandidates.width(p);
                int height = RenderMergeCandidates.height(p);
                int fragments = allFragments(direction, plane, v0, height);
                splitInflation += fragments - 1;

                int seedX = x(direction, plane, u0, v0);
                int seedY = y(direction, plane, u0, v0);
                int seedZ = z(direction, plane, u0, v0);
                int representative = candidates.representativeSourceQuad(candidate);

                for (int slice = 0; slice < 4; slice++) {
                    int area = fragmentArea(direction, plane, v0, width, height, slice);
                    if (area == 0 || !selected(mask, slice)) continue;
                    if (area > 1) {
                        merged++;
                        mergedCovered += area;
                        vertexBytes += (long) RepeatAwareGreedyMesh.VERTICES_PER_QUAD * RepeatAwareGreedyMesh.MERGED_BYTES_PER_VERTEX;
                    } else {
                        passthrough++;
                        vertexBytes += (long) RepeatAwareGreedyMesh.VERTICES_PER_QUAD * BakedSectionMesh.BYTES_PER_VERTEX;
                    }
                    outputQuads++;
                    indexBytes += (long) RepeatAwareGreedyMesh.INDICES_PER_QUAD * Integer.BYTES;

                    for (int dv = 0; dv < height; dv++) for (int du = 0; du < width; du++) {
                        int fx = x(direction, plane, u0 + du, v0 + dv);
                        int fy = y(direction, plane, u0 + du, v0 + dv);
                        int fz = z(direction, plane, u0 + du, v0 + dv);
                        if ((fy >>> 2) != slice) continue;
                        int source = renderKeys.sourceQuad(fx, fy, fz, direction);
                        if (source < 0 || sourceSlice(baked, source) != slice
                                || baked.direction(source) != (byte) direction
                                || !renderKeys.renderEquivalent(seedX, seedY, seedZ, fx, fy, fz, direction, baked)
                                || baked.layer(source) != baked.layer(representative)) {
                            fail(FAILURE_MERGED_IDENTITY, candidate);
                        }
                        selectedSource += markSource(scratch, source);
                    }
                }
            }

            int expectedSelectedSource = 0;
            for (int source = 0; source < baked.quadCount(); source++) {
                if (selected(mask, sourceSlice(baked, source))) {
                    expectedSelectedSource++;
                    if (!scratch.seenSource[source]) fail(FAILURE_SOURCE_MISSING, source);
                }
            }
            if (selectedSource != expectedSelectedSource || outputQuads != passthrough + merged) {
                fail(FAILURE_ACCOUNTING, selectedSource);
            }
            scratch.observe(selectedSource);

            int productionQuads = production.hybridQuadCount();
            int assembledQuads = productionQuads + splitInflation;
            int inflation = productionQuads == 0 ? 0
                    : (int) (((long) (assembledQuads - productionQuads) * 1000L) / productionQuads);
            return new PartialRemeshShadowResult(
                    request.episodeId(), mask, selectedSlices, selectedCells,
                    selectedSource, selectedReference, topologyFragments, candidateFragments,
                    passthrough, merged, mergedCovered, outputQuads, vertexBytes, indexBytes,
                    forcedSplits, assembledQuads, productionQuads, inflation,
                    Math.max(0L, System.nanoTime() - start), true, true, FAILURE_NONE, -1);
        } catch (ShadowFailure failure) {
            return failure(request, start, failure.code, failure.index);
        } catch (RuntimeException failure) {
            return failure(request, start, FAILURE_EXCEPTION, -1);
        }
    }

    private static PartialRemeshShadowResult failure(PartialRemeshShadowRequest request, long start, int code, int index) {
        return new PartialRemeshShadowResult(request == null ? 0L : request.episodeId(),
                request == null ? 0 : request.sliceMask(), request == null ? 0 : request.selectedSliceCount(),
                request == null ? 0 : request.selectedSliceCount() * 1024,
                0, 0, 0, 0, 0, 0, 0, 0, 0L, 0L, 0, 0, 0, 0,
                Math.max(0L, System.nanoTime() - start), false,
                code != FAILURE_UNSELECTED_CHANGED, code, index);
    }

    private static int markSource(BuildScratch scratch, int source) {
        if (scratch.seenSource[source]) fail(FAILURE_SOURCE_DUPLICATE, source);
        scratch.seenSource[source] = true;
        return 1;
    }

    private static int sourceSlice(SectionBakedQuadSnapshot baked, int source) {
        return ((baked.sourceBlock(source) >>> 4) & 0xF) >>> 2;
    }
    private static boolean selected(int mask, int slice) { return (mask & (1 << slice)) != 0; }

    private static int selectedFragments(int direction, int plane, int v, int height, int mask) {
        if (direction == BinarySectionVisibility.DOWN || direction == BinarySectionVisibility.UP) {
            return selected(mask, plane >>> 2) ? 1 : 0;
        }
        int count = 0;
        for (int slice = 0; slice < 4; slice++) if (selected(mask, slice) && overlapRows(v, height, slice) > 0) count++;
        return count;
    }
    private static int allFragments(int direction, int plane, int v, int height) {
        if (direction == BinarySectionVisibility.DOWN || direction == BinarySectionVisibility.UP) return 1;
        int count = 0;
        for (int slice = 0; slice < 4; slice++) if (overlapRows(v, height, slice) > 0) count++;
        return count;
    }
    private static int fragmentArea(int direction, int plane, int v, int width, int height, int slice) {
        if (direction == BinarySectionVisibility.DOWN || direction == BinarySectionVisibility.UP) {
            return (plane >>> 2) == slice ? width * height : 0;
        }
        return width * overlapRows(v, height, slice);
    }
    private static int overlapRows(int v, int height, int slice) {
        int lo = Math.max(v, slice * 4);
        int hi = Math.min(v + height, slice * 4 + 4);
        return Math.max(0, hi - lo);
    }
    private static int x(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST -> plane;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP,
                 BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> u;
            default -> throw new IllegalArgumentException("direction");
        };
    }
    private static int y(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST,
                 BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> v;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP -> plane;
            default -> throw new IllegalArgumentException("direction");
        };
    }
    private static int z(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST -> u;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP -> v;
            case BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> plane;
            default -> throw new IllegalArgumentException("direction");
        };
    }
    private static void fail(int code, int index) { throw new ShadowFailure(code, index); }

    public PartialRemeshShadowResult withDeterministic(boolean deterministic) {
        if (deterministic) return this;
        return new PartialRemeshShadowResult(episodeId, sliceMask, selectedSlices, selectedCells,
                selectedSourceQuads, selectedReferenceFaces, topologyFragments, mergeCandidateFragments,
                passthroughIdentities, mergedIdentities, mergedCoveredSourceFaces, outputQuads,
                outputVertexBytes, outputIndexBytes, forcedBoundarySplits, assembledQuads, productionQuads,
                inflationPermille, buildTimeNs, false, unselectedStable, FAILURE_ACCOUNTING, -2);
    }

    public boolean contentEquals(PartialRemeshShadowResult other) {
        return other != null && episodeId == other.episodeId && sliceMask == other.sliceMask
                && selectedSlices == other.selectedSlices && selectedCells == other.selectedCells
                && selectedSourceQuads == other.selectedSourceQuads && selectedReferenceFaces == other.selectedReferenceFaces
                && topologyFragments == other.topologyFragments && mergeCandidateFragments == other.mergeCandidateFragments
                && passthroughIdentities == other.passthroughIdentities && mergedIdentities == other.mergedIdentities
                && mergedCoveredSourceFaces == other.mergedCoveredSourceFaces && outputQuads == other.outputQuads
                && outputVertexBytes == other.outputVertexBytes && outputIndexBytes == other.outputIndexBytes
                && forcedBoundarySplits == other.forcedBoundarySplits && assembledQuads == other.assembledQuads
                && productionQuads == other.productionQuads && inflationPermille == other.inflationPermille
                && exact == other.exact && unselectedStable == other.unselectedStable
                && failureCode == other.failureCode && failureIndex == other.failureIndex;
    }

    public static boolean selfTest() {
        return allFragments(BinarySectionVisibility.NORTH, 0, 1, 2) == 1
                && allFragments(BinarySectionVisibility.NORTH, 0, 3, 2) == 2
                && fragmentArea(BinarySectionVisibility.NORTH, 0, 3, 5, 2, 0) == 5
                && fragmentArea(BinarySectionVisibility.NORTH, 0, 3, 5, 2, 1) == 5
                && allFragments(BinarySectionVisibility.UP, 7, 0, 16) == 1;
    }

    public long episodeId() { return episodeId; }
    public int sliceMask() { return sliceMask; }
    public int selectedSlices() { return selectedSlices; }
    public int selectedCells() { return selectedCells; }
    public int selectedSourceQuads() { return selectedSourceQuads; }
    public int selectedReferenceFaces() { return selectedReferenceFaces; }
    public int topologyFragments() { return topologyFragments; }
    public int mergeCandidateFragments() { return mergeCandidateFragments; }
    public int passthroughIdentities() { return passthroughIdentities; }
    public int mergedIdentities() { return mergedIdentities; }
    public int mergedCoveredSourceFaces() { return mergedCoveredSourceFaces; }
    public int outputQuads() { return outputQuads; }
    public long outputVertexBytes() { return outputVertexBytes; }
    public long outputIndexBytes() { return outputIndexBytes; }
    public long outputBytes() { return outputVertexBytes + outputIndexBytes; }
    public int forcedBoundarySplits() { return forcedBoundarySplits; }
    public int assembledQuads() { return assembledQuads; }
    public int productionQuads() { return productionQuads; }
    public int inflationPermille() { return inflationPermille; }
    public long buildTimeNs() { return buildTimeNs; }
    public boolean exact() { return exact; }
    public boolean unselectedStable() { return unselectedStable; }
    public int failureCode() { return failureCode; }
    public int failureIndex() { return failureIndex; }
}
