package dev.obsidian.render.terrain;

import net.minecraft.core.Direction;

import java.util.Arrays;

/**
 * P3.7 dev14 pure differential correctness proof for the final optimized mesh.
 *
 * <p>The optimized path is always the system under test. Canonical topology is
 * sourced from the permanent independent {@link ReferenceFaceMesh}, while
 * generalized render truth is sourced from the render-thread-frozen
 * {@link SectionBakedQuadSnapshot}. The retained optimized identities are
 * conceptually expanded back to source baked quads and must cover every source
 * quad exactly once.</p>
 */
public final class DifferentialCorrectnessProof {
    public static final int FIXTURE_NONE = 0;
    public static final int FIXTURE_REFERENCE_RECORD = 1;
    public static final int FIXTURE_REFERENCE_VISIBILITY = 2;
    public static final int FIXTURE_REFERENCE_OPTIMIZED_COVERAGE = 3;
    public static final int FIXTURE_OPTIMIZED_WITHOUT_REFERENCE = 4;
    public static final int FIXTURE_PASSTHROUGH_IDENTITY = 5;
    public static final int FIXTURE_MERGED_IDENTITY = 6;
    public static final int FIXTURE_MERGED_SOURCE_MAPPING = 7;
    public static final int FIXTURE_SOURCE_COVERAGE = 8;
    public static final int FIXTURE_MATERIAL = 9;
    public static final int FIXTURE_DIRECTION = 10;
    public static final int FIXTURE_CANONICAL_GEOMETRY = 11;
    public static final int FIXTURE_UV = 12;
    public static final int FIXTURE_COLOR = 13;
    public static final int FIXTURE_LIGHT = 14;
    public static final int FIXTURE_TRANSPORT_ACCOUNTING = 15;

    private static final int SIZE = SectionSnapshot.INTERIOR_SIZE;
    private static final int CANONICAL_SLOTS =
            BinarySectionVisibility.DIRECTION_COUNT * SectionSnapshot.INTERIOR_CELL_COUNT;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Worker-local bounded primitive scratch reused across jobs. */
    public static final class BuildScratch {
        private final byte[] sourceCoverage = new byte[SectionBakedQuadSnapshot.MAX_QUADS];
        private final byte[] referenceCoverage = new byte[CANONICAL_SLOTS];
        private final byte[] transportCandidates = new byte[RenderMergeCandidates.MAX_CANDIDATES];
        private long uses;
        private int highWaterSourceQuads;
        private int highWaterReferenceFaces;

        private void begin(int sourceQuads, int referenceFaces) {
            uses++;
            Arrays.fill(sourceCoverage, 0, sourceQuads, (byte) 0);
            Arrays.fill(referenceCoverage, (byte) 0);
            Arrays.fill(transportCandidates, (byte) 0);
            highWaterSourceQuads = Math.max(highWaterSourceQuads, sourceQuads);
            highWaterReferenceFaces = Math.max(highWaterReferenceFaces, referenceFaces);
        }

        public long uses() { return uses; }
        public int highWaterSourceQuads() { return highWaterSourceQuads; }
        public int highWaterReferenceFaces() { return highWaterReferenceFaces; }
        public int retainedScratchBytes() {
            return sourceCoverage.length + referenceCoverage.length + transportCandidates.length;
        }
    }

    /** Compact deterministic first-failure identity. */
    public record MismatchFixture(
            int type,
            int packedFace,
            int sourceQuad,
            int candidate,
            int expected,
            int actual,
            long expectedFingerprint,
            long actualFingerprint) {

        public boolean present() { return type != FIXTURE_NONE; }

        public long fingerprint() {
            long hash = FNV_OFFSET_BASIS;
            hash = hashInt(hash, type);
            hash = hashInt(hash, packedFace);
            hash = hashInt(hash, sourceQuad);
            hash = hashInt(hash, candidate);
            hash = hashInt(hash, expected);
            hash = hashInt(hash, actual);
            hash = hashLong(hash, expectedFingerprint);
            return hashLong(hash, actualFingerprint);
        }

        public String describe() {
            return "type=" + type
                    + ", packedFace=" + packedFace
                    + ", sourceQuad=" + sourceQuad
                    + ", candidate=" + candidate
                    + ", expected=" + expected
                    + ", actual=" + actual
                    + ", expectedFingerprint=" + Long.toUnsignedString(expectedFingerprint)
                    + ", actualFingerprint=" + Long.toUnsignedString(actualFingerprint);
        }
    }

    private static final MismatchFixture NO_FIXTURE =
            new MismatchFixture(FIXTURE_NONE, -1, -1, -1, 0, 0, 0L, 0L);

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final int referenceFacesChecked;
    private final int referenceMappedFaces;
    private final int referenceUnmappedFaces;
    private final int referenceAmbiguousFaces;
    private final int sourceQuadsChecked;
    private final int passthroughSourceIdentitiesChecked;
    private final int mergedCandidatesChecked;
    private final int mergedExpandedSourceFacesChecked;
    private final int materialChecks;
    private final int materialMatches;
    private final int directionChecks;
    private final int directionMatches;
    private final int canonicalGeometryChecks;
    private final int canonicalGeometryMatches;
    private final int uvChecks;
    private final int uvMatches;
    private final int colorChecks;
    private final int colorMatches;
    private final int lightChecks;
    private final int lightMatches;
    private final int missingSourceCoverage;
    private final int duplicateSourceCoverage;
    private final int optimizedCanonicalWithoutReference;
    private final int realMismatchCount;
    private final boolean fixtureSelfTestPassed;
    private final MismatchFixture firstMismatch;
    private final long sourceSnapshotFingerprint;
    private final long sourceReferenceFingerprint;
    private final long sourceBakedFingerprint;
    private final long sourceOracleFingerprint;
    private final long sourceVisibilityFingerprint;
    private final long sourceRenderKeyFingerprint;
    private final long sourceCandidateFingerprint;
    private final long sourceTransportFingerprint;
    private final long sourceOptimizedFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private DifferentialCorrectnessProof(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionBakedQuadSnapshot baked,
            BakedSectionMesh oracle,
            BinarySectionVisibility visibility,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareTransportProof transport,
            RepeatAwareGreedyMesh optimized,
            Metrics metrics,
            boolean fixtureSelfTestPassed,
            MismatchFixture firstMismatch,
            long fingerprint,
            long buildTimeNs) {
        this.sectionX = snapshot.sectionX();
        this.sectionY = snapshot.sectionY();
        this.sectionZ = snapshot.sectionZ();
        this.referenceFacesChecked = metrics.referenceFacesChecked;
        this.referenceMappedFaces = metrics.referenceMappedFaces;
        this.referenceUnmappedFaces = metrics.referenceUnmappedFaces;
        this.referenceAmbiguousFaces = metrics.referenceAmbiguousFaces;
        this.sourceQuadsChecked = metrics.sourceQuadsChecked;
        this.passthroughSourceIdentitiesChecked = metrics.passthroughSourceIdentitiesChecked;
        this.mergedCandidatesChecked = metrics.mergedCandidatesChecked;
        this.mergedExpandedSourceFacesChecked = metrics.mergedExpandedSourceFacesChecked;
        this.materialChecks = metrics.materialChecks;
        this.materialMatches = metrics.materialMatches;
        this.directionChecks = metrics.directionChecks;
        this.directionMatches = metrics.directionMatches;
        this.canonicalGeometryChecks = metrics.canonicalGeometryChecks;
        this.canonicalGeometryMatches = metrics.canonicalGeometryMatches;
        this.uvChecks = metrics.uvChecks;
        this.uvMatches = metrics.uvMatches;
        this.colorChecks = metrics.colorChecks;
        this.colorMatches = metrics.colorMatches;
        this.lightChecks = metrics.lightChecks;
        this.lightMatches = metrics.lightMatches;
        this.missingSourceCoverage = metrics.missingSourceCoverage;
        this.duplicateSourceCoverage = metrics.duplicateSourceCoverage;
        this.optimizedCanonicalWithoutReference = metrics.optimizedCanonicalWithoutReference;
        this.realMismatchCount = metrics.realMismatchCount;
        this.fixtureSelfTestPassed = fixtureSelfTestPassed;
        this.firstMismatch = firstMismatch;
        this.sourceSnapshotFingerprint = snapshot.fingerprint();
        this.sourceReferenceFingerprint = reference.fingerprint();
        this.sourceBakedFingerprint = baked.fingerprint();
        this.sourceOracleFingerprint = oracle.fingerprint();
        this.sourceVisibilityFingerprint = visibility.fingerprint();
        this.sourceRenderKeyFingerprint = renderKeys.fingerprint();
        this.sourceCandidateFingerprint = candidates.fingerprint();
        this.sourceTransportFingerprint = transport.fingerprint();
        this.sourceOptimizedFingerprint = optimized.fingerprint();
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    private static final class Metrics {
        int referenceFacesChecked;
        int referenceMappedFaces;
        int referenceUnmappedFaces;
        int referenceAmbiguousFaces;
        int sourceQuadsChecked;
        int passthroughSourceIdentitiesChecked;
        int mergedCandidatesChecked;
        int mergedExpandedSourceFacesChecked;
        int materialChecks;
        int materialMatches;
        int directionChecks;
        int directionMatches;
        int canonicalGeometryChecks;
        int canonicalGeometryMatches;
        int uvChecks;
        int uvMatches;
        int colorChecks;
        int colorMatches;
        int lightChecks;
        int lightMatches;
        int missingSourceCoverage;
        int duplicateSourceCoverage;
        int optimizedCanonicalWithoutReference;
        int realMismatchCount;
        MismatchFixture firstMismatch = NO_FIXTURE;

        void mismatch(MismatchFixture fixture) {
            realMismatchCount++;
            if (!firstMismatch.present()) firstMismatch = fixture;
        }
    }

    public static DifferentialCorrectnessProof build(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionBakedQuadSnapshot baked,
            BakedSectionMesh oracle,
            BinarySectionVisibility visibility,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareTransportProof transport,
            RepeatAwareGreedyMesh optimized,
            BuildScratch scratch) {
        validateSources(snapshot, reference, baked, oracle, visibility, renderKeys, candidates, transport, optimized);
        if (scratch == null) throw new NullPointerException("differential correctness scratch is required");
        long startNs = System.nanoTime();
        scratch.begin(baked.quadCount(), reference.faceCount());
        Metrics metrics = new Metrics();

        buildIndependentReferenceCoverage(snapshot, reference, scratch, metrics);
        markTransportCandidates(transport, candidates, scratch, metrics);
        buildOptimizedSourceCoverage(baked, renderKeys, candidates, transport, optimized, scratch, metrics);
        auditIndependentReference(reference, visibility, renderKeys, scratch, metrics);
        auditOptimizedCanonicalReferences(renderKeys, scratch, metrics);
        auditCompleteSourceCoverage(baked, scratch, metrics);

        if (metrics.mergedExpandedSourceFacesChecked != transport.coveredFaces()
                || metrics.mergedCandidatesChecked != transport.recordCount()
                || metrics.passthroughSourceIdentitiesChecked + metrics.mergedExpandedSourceFacesChecked
                != baked.quadCount()) {
            metrics.mismatch(new MismatchFixture(
                    FIXTURE_TRANSPORT_ACCOUNTING, -1, -1, -1,
                    baked.quadCount(),
                    metrics.passthroughSourceIdentitiesChecked + metrics.mergedExpandedSourceFacesChecked,
                    transport.fingerprint(), optimized.fingerprint()));
        }

        boolean fixtureSelfTestPassed = fixtureSelfTest();
        if (!fixtureSelfTestPassed) {
            throw new IllegalStateException("P3.7 deterministic mismatch fixture self-test failed");
        }

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, snapshot.fingerprint());
        hash = hashLong(hash, reference.fingerprint());
        hash = hashLong(hash, baked.fingerprint());
        hash = hashLong(hash, oracle.fingerprint());
        hash = hashLong(hash, visibility.fingerprint());
        hash = hashLong(hash, renderKeys.fingerprint());
        hash = hashLong(hash, candidates.fingerprint());
        hash = hashLong(hash, transport.fingerprint());
        hash = hashLong(hash, optimized.fingerprint());
        hash = hashMetrics(hash, metrics);
        hash = hashInt(hash, fixtureSelfTestPassed ? 1 : 0);
        hash = hashLong(hash, metrics.firstMismatch.fingerprint());

        return new DifferentialCorrectnessProof(
                snapshot, reference, baked, oracle, visibility, renderKeys, candidates, transport, optimized,
                metrics, fixtureSelfTestPassed, metrics.firstMismatch, hash, System.nanoTime() - startNs);
    }

    private static void validateSources(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionBakedQuadSnapshot baked,
            BakedSectionMesh oracle,
            BinarySectionVisibility visibility,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareTransportProof transport,
            RepeatAwareGreedyMesh optimized) {
        if (snapshot == null || reference == null || baked == null || oracle == null || visibility == null
                || renderKeys == null || candidates == null || transport == null || optimized == null) {
            throw new NullPointerException("P3.7 differential proof requires every immutable source/result");
        }
        if (baked.sourceSnapshotFingerprint() != snapshot.fingerprint()
                || oracle.sourceFingerprint() != baked.fingerprint()
                || visibility.sourceSnapshotFingerprint() != snapshot.fingerprint()
                || renderKeys.sourceSnapshotFingerprint() != snapshot.fingerprint()
                || renderKeys.sourceVisibilityFingerprint() != visibility.fingerprint()
                || renderKeys.sourceBakedFingerprint() != baked.fingerprint()
                || candidates.sourceVisibilityFingerprint() != visibility.fingerprint()
                || candidates.sourceRenderKeyFingerprint() != renderKeys.fingerprint()
                || candidates.sourceBakedFingerprint() != baked.fingerprint()
                || transport.sourceCandidateFingerprint() != candidates.fingerprint()
                || transport.sourceBakedFingerprint() != baked.fingerprint()
                || optimized.sourceSnapshotFingerprint() != snapshot.fingerprint()
                || optimized.sourceBakedFingerprint() != baked.fingerprint()
                || optimized.sourceRenderKeyFingerprint() != renderKeys.fingerprint()
                || optimized.sourceCandidateFingerprint() != candidates.fingerprint()
                || optimized.sourceTransportFingerprint() != transport.fingerprint()
                || optimized.sourceQuadCount() != baked.quadCount()
                || optimized.mergedQuadCount() != transport.recordCount()) {
            throw new IllegalArgumentException("P3.7 differential source identity mismatch");
        }
        reference.validateAgainst(snapshot);
        oracle.validateAgainst(snapshot, baked);
    }

    private static void buildIndependentReferenceCoverage(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            BuildScratch scratch,
            Metrics metrics) {
        for (int i = 0; i < reference.faceCount(); i++) {
            metrics.referenceFacesChecked++;
            int packed = reference.packedFace(i);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT
                    || snapshot.stateId(x, y, z) != reference.stateId(i)) {
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_REFERENCE_RECORD, packed, -1, -1,
                        reference.stateId(i), snapshot.stateId(x, y, z),
                        reference.fingerprint(), snapshot.fingerprint()));
                continue;
            }
            int slot = slot(x, y, z, direction);
            if (scratch.referenceCoverage[slot] != 0) {
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_REFERENCE_RECORD, packed, -1, -1,
                        1, Byte.toUnsignedInt(scratch.referenceCoverage[slot]),
                        reference.fingerprint(), reference.fingerprint()));
            } else {
                scratch.referenceCoverage[slot] = 1;
            }
        }
    }


    private static void markTransportCandidates(
            RepeatAwareTransportProof transport,
            RenderMergeCandidates candidates,
            BuildScratch scratch,
            Metrics metrics) {
        for (int record = 0; record < transport.recordCount(); record++) {
            int candidate = transport.candidateIndex(record);
            if (candidate < 0 || candidate >= candidates.candidateCount()) {
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_TRANSPORT_ACCOUNTING, -1, -1, candidate,
                        candidates.candidateCount(), candidate,
                        candidates.fingerprint(), transport.fingerprint()));
                continue;
            }
            int seen = Byte.toUnsignedInt(scratch.transportCandidates[candidate]);
            if (seen != 0) {
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_TRANSPORT_ACCOUNTING, -1, -1, candidate,
                        0, seen, candidates.fingerprint(), transport.fingerprint()));
            } else {
                scratch.transportCandidates[candidate] = 1;
            }
        }
    }

    private static void buildOptimizedSourceCoverage(
            SectionBakedQuadSnapshot baked,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareTransportProof transport,
            RepeatAwareGreedyMesh optimized,
            BuildScratch scratch,
            Metrics metrics) {
        for (int i = 0; i < optimized.passthroughQuadCount(); i++) {
            int source = optimized.passthroughSourceQuad(i);
            metrics.passthroughSourceIdentitiesChecked++;
            if (source < 0 || source >= baked.quadCount()) {
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_PASSTHROUGH_IDENTITY, -1, source, -1,
                        baked.quadCount(), source, baked.fingerprint(), optimized.fingerprint()));
                continue;
            }
            incrementCoverage(scratch.sourceCoverage, source, -1, metrics);
        }

        for (int mergedIndex = 0; mergedIndex < optimized.mergedQuadCount(); mergedIndex++) {
            int candidate = optimized.mergedCandidateIndex(mergedIndex);
            metrics.mergedCandidatesChecked++;
            if (candidate < 0 || candidate >= candidates.candidateCount()
                    || Byte.toUnsignedInt(scratch.transportCandidates[candidate]) != 1) {
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_MERGED_IDENTITY, -1, -1, candidate,
                        1, candidate >= 0 && candidate < scratch.transportCandidates.length
                                ? Byte.toUnsignedInt(scratch.transportCandidates[candidate]) : -1,
                        transport.fingerprint(), optimized.fingerprint()));
                continue;
            }
            scratch.transportCandidates[candidate] = 2;

            int packed = candidates.packedCandidate(candidate);
            int direction = RenderMergeCandidates.direction(packed);
            int plane = RenderMergeCandidates.plane(packed);
            int u0 = RenderMergeCandidates.u(packed);
            int v0 = RenderMergeCandidates.v(packed);
            int width = RenderMergeCandidates.width(packed);
            int height = RenderMergeCandidates.height(packed);
            int representative = candidates.representativeSourceQuad(candidate);

            for (int dv = 0; dv < height; dv++) {
                for (int du = 0; du < width; du++) {
                    int u = u0 + du;
                    int v = v0 + dv;
                    int x = x(direction, plane, u, v);
                    int y = y(direction, plane, u, v);
                    int z = z(direction, plane, u, v);
                    int packedFace = packFace(x, y, z, direction);
                    int source = renderKeys.sourceQuad(x, y, z, direction);
                    metrics.mergedExpandedSourceFacesChecked++;
                    if (source < 0 || source >= baked.quadCount()) {
                        metrics.mismatch(new MismatchFixture(
                                FIXTURE_MERGED_SOURCE_MAPPING, packedFace, source, candidate,
                                1, source, renderKeys.fingerprint(), candidates.fingerprint()));
                        continue;
                    }
                    incrementCoverage(scratch.sourceCoverage, source, candidate, metrics);

                    compareMergedSourceTruth(
                            baked, representative, source, direction, x, y, z, packedFace, candidate, metrics);
                }
            }
        }
    }

    private static void compareMergedSourceTruth(
            SectionBakedQuadSnapshot baked,
            int representative,
            int source,
            int direction,
            int sourceX,
            int sourceY,
            int sourceZ,
            int packedFace,
            int candidate,
            Metrics metrics) {
        if (representative < 0 || representative >= baked.quadCount()) {
            metrics.mismatch(new MismatchFixture(
                    FIXTURE_MERGED_IDENTITY, packedFace, source, candidate,
                    baked.quadCount(), representative, baked.fingerprint(), 0L));
            return;
        }

        metrics.materialChecks++;
        boolean materialMatch = baked.layer(representative) == baked.layer(source)
                && baked.material(baked.materialId(representative)).equals(baked.material(baked.materialId(source)));
        if (materialMatch) metrics.materialMatches++;
        else metrics.mismatch(new MismatchFixture(
                FIXTURE_MATERIAL, packedFace, source, candidate,
                baked.materialId(representative), baked.materialId(source),
                materialFingerprint(baked, representative), materialFingerprint(baked, source)));

        metrics.directionChecks++;
        int representativeDirection = binaryDirection(baked.direction(representative));
        int sourceDirection = binaryDirection(baked.direction(source));
        if (representativeDirection == direction && sourceDirection == direction) metrics.directionMatches++;
        else metrics.mismatch(new MismatchFixture(
                FIXTURE_DIRECTION, packedFace, source, candidate,
                direction, sourceDirection,
                representativeDirection, sourceDirection));

        int representativePacked = baked.sourceBlock(representative);
        int repX = representativePacked & 0xF;
        int repY = (representativePacked >>> 4) & 0xF;
        int repZ = (representativePacked >>> 8) & 0xF;
        int repOrder = cornerOrderSignature(baked, representative, repX, repY, repZ, direction);
        int sourceOrder = cornerOrderSignature(baked, source, sourceX, sourceY, sourceZ, direction);
        metrics.canonicalGeometryChecks++;
        if (repOrder >= 0 && sourceOrder >= 0 && repOrder == sourceOrder) {
            metrics.canonicalGeometryMatches++;
        } else {
            metrics.mismatch(new MismatchFixture(
                    FIXTURE_CANONICAL_GEOMETRY, packedFace, source, candidate,
                    repOrder, sourceOrder,
                    baked.fingerprint(), baked.fingerprint()));
            return;
        }

        for (int corner = 0; corner < 4; corner++) {
            int repVertex = vertexForCorner(baked, representative, repX, repY, repZ, direction, corner);
            int srcVertex = vertexForCorner(baked, source, sourceX, sourceY, sourceZ, direction, corner);
            if (repVertex < 0 || srcVertex < 0) {
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_CANONICAL_GEOMETRY, packedFace, source, candidate,
                        repVertex, srcVertex, baked.fingerprint(), baked.fingerprint()));
                continue;
            }

            metrics.uvChecks++;
            int repURaw = Float.floatToRawIntBits(baked.u(representative, repVertex));
            int srcURaw = Float.floatToRawIntBits(baked.u(source, srcVertex));
            int repVRaw = Float.floatToRawIntBits(baked.v(representative, repVertex));
            int srcVRaw = Float.floatToRawIntBits(baked.v(source, srcVertex));
            if (repURaw == srcURaw && repVRaw == srcVRaw) metrics.uvMatches++;
            else metrics.mismatch(new MismatchFixture(
                    FIXTURE_UV, packedFace, source, candidate,
                    repURaw, srcURaw,
                    Integer.toUnsignedLong(repVRaw), Integer.toUnsignedLong(srcVRaw)));

            metrics.colorChecks++;
            int repColor = baked.exactArgbColor(representative, repVertex);
            int srcColor = baked.exactArgbColor(source, srcVertex);
            if (repColor == srcColor) metrics.colorMatches++;
            else metrics.mismatch(new MismatchFixture(
                    FIXTURE_COLOR, packedFace, source, candidate,
                    repColor, srcColor,
                    Integer.toUnsignedLong(repColor), Integer.toUnsignedLong(srcColor)));

            metrics.lightChecks++;
            int repLight = baked.packedLight(representative, repVertex);
            int srcLight = baked.packedLight(source, srcVertex);
            if (repLight == srcLight) metrics.lightMatches++;
            else metrics.mismatch(new MismatchFixture(
                    FIXTURE_LIGHT, packedFace, source, candidate,
                    repLight, srcLight,
                    Integer.toUnsignedLong(repLight), Integer.toUnsignedLong(srcLight)));
        }
    }

    private static void auditIndependentReference(
            ReferenceFaceMesh reference,
            BinarySectionVisibility visibility,
            CanonicalFaceRenderKeys renderKeys,
            BuildScratch scratch,
            Metrics metrics) {
        for (int i = 0; i < reference.faceCount(); i++) {
            int packed = reference.packedFace(i);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            if (direction >= BinarySectionVisibility.DIRECTION_COUNT) continue;

            if (!visibility.hasFace(x, y, z, direction)) {
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_REFERENCE_VISIBILITY, packed, -1, -1,
                        1, 0, reference.fingerprint(), visibility.fingerprint()));
            }

            int source = renderKeys.sourceQuad(x, y, z, direction);
            if (source >= 0) {
                metrics.referenceMappedFaces++;
                int coverage = source < scratch.sourceCoverage.length
                        ? Byte.toUnsignedInt(scratch.sourceCoverage[source]) : 0;
                if (coverage != 1) {
                    metrics.mismatch(new MismatchFixture(
                            FIXTURE_REFERENCE_OPTIMIZED_COVERAGE, packed, source, -1,
                            1, coverage, reference.fingerprint(), renderKeys.fingerprint()));
                }
            } else if (renderKeys.ambiguous(x, y, z, direction)) {
                metrics.referenceAmbiguousFaces++;
            } else {
                metrics.referenceUnmappedFaces++;
            }
        }
    }


    private static void auditOptimizedCanonicalReferences(
            CanonicalFaceRenderKeys renderKeys,
            BuildScratch scratch,
            Metrics metrics) {
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    for (int x = 0; x < SIZE; x++) {
                        int source = renderKeys.sourceQuad(x, y, z, direction);
                        if (source < 0) continue;
                        int slot = slot(x, y, z, direction);
                        if (scratch.referenceCoverage[slot] == 0) {
                            metrics.optimizedCanonicalWithoutReference++;
                            int packedFace = packFace(x, y, z, direction);
                            metrics.mismatch(new MismatchFixture(
                                    FIXTURE_OPTIMIZED_WITHOUT_REFERENCE, packedFace, source, -1,
                                    1, 0, renderKeys.fingerprint(), referenceSlotFingerprint(packedFace, source)));
                        }
                    }
                }
            }
        }
    }

    private static void auditCompleteSourceCoverage(
            SectionBakedQuadSnapshot baked,
            BuildScratch scratch,
            Metrics metrics) {
        for (int source = 0; source < baked.quadCount(); source++) {
            metrics.sourceQuadsChecked++;
            int coverage = Byte.toUnsignedInt(scratch.sourceCoverage[source]);
            if (coverage == 0) {
                metrics.missingSourceCoverage++;
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_SOURCE_COVERAGE, -1, source, -1,
                        1, 0, baked.fingerprint(), 0L));
            } else if (coverage > 1) {
                metrics.duplicateSourceCoverage++;
                metrics.mismatch(new MismatchFixture(
                        FIXTURE_SOURCE_COVERAGE, -1, source, -1,
                        1, coverage, baked.fingerprint(), optimizedCoverageFingerprint(source, coverage)));
            }
        }
    }

    private static void incrementCoverage(byte[] coverage, int source, int candidate, Metrics metrics) {
        int current = Byte.toUnsignedInt(coverage[source]);
        if (current == 255) {
            metrics.mismatch(new MismatchFixture(
                    FIXTURE_SOURCE_COVERAGE, -1, source, candidate,
                    1, current, 0L, optimizedCoverageFingerprint(source, current)));
            return;
        }
        coverage[source] = (byte) (current + 1);
    }

    private static boolean fixtureSelfTest() {
        MismatchFixture a = new MismatchFixture(FIXTURE_UV, 0x1234, 17, 9, 101, 202, 303L, 404L);
        MismatchFixture b = new MismatchFixture(FIXTURE_UV, 0x1234, 17, 9, 101, 202, 303L, 404L);
        MismatchFixture changed = new MismatchFixture(FIXTURE_UV, 0x1234, 17, 9, 101, 203, 303L, 404L);
        return a.equals(b)
                && a.fingerprint() == b.fingerprint()
                && a.fingerprint() != changed.fingerprint()
                && !a.describe().isEmpty();
    }

    private static int binaryDirection(byte directionOrdinal) {
        int ordinal = Byte.toUnsignedInt(directionOrdinal);
        if (ordinal >= DIRECTIONS.length) return -1;
        return switch (DIRECTIONS[ordinal]) {
            case WEST -> BinarySectionVisibility.WEST;
            case EAST -> BinarySectionVisibility.EAST;
            case DOWN -> BinarySectionVisibility.DOWN;
            case UP -> BinarySectionVisibility.UP;
            case NORTH -> BinarySectionVisibility.NORTH;
            case SOUTH -> BinarySectionVisibility.SOUTH;
        };
    }

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
        float fixed;
        float fixedExpected;
        float s;
        float sLow;
        float sHigh;
        float t;
        float tLow;
        float tHigh;
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

    private static int x(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST -> plane;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP,
                 BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> u;
            default -> throw new IllegalArgumentException("Invalid direction " + direction);
        };
    }

    private static int y(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST,
                 BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> v;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP -> plane;
            default -> throw new IllegalArgumentException("Invalid direction " + direction);
        };
    }

    private static int z(int direction, int plane, int u, int v) {
        return switch (direction) {
            case BinarySectionVisibility.WEST, BinarySectionVisibility.EAST -> u;
            case BinarySectionVisibility.DOWN, BinarySectionVisibility.UP -> v;
            case BinarySectionVisibility.NORTH, BinarySectionVisibility.SOUTH -> plane;
            default -> throw new IllegalArgumentException("Invalid direction " + direction);
        };
    }

    private static int slot(int x, int y, int z, int direction) {
        return direction * SectionSnapshot.INTERIOR_CELL_COUNT + ((y * SIZE + z) * SIZE + x);
    }

    private static int packFace(int x, int y, int z, int direction) {
        return x | (y << 4) | (z << 8) | (direction << 12);
    }

    private static long materialFingerprint(SectionBakedQuadSnapshot baked, int quad) {
        SectionBakedQuadSnapshot.MaterialIdentity material = baked.material(baked.materialId(quad));
        long hash = FNV_OFFSET_BASIS;
        hash = hashInt(hash, material.atlas().hashCode());
        hash = hashInt(hash, material.sprite().hashCode());
        hash = hashInt(hash, Byte.toUnsignedInt(material.layer()));
        hash = hashInt(hash, material.materialFlags());
        hash = hashInt(hash, material.tintIndex());
        hash = hashInt(hash, material.shade() ? 1 : 0);
        hash = hashInt(hash, material.lightEmission());
        return hashInt(hash, material.animated() ? 1 : 0);
    }

    private static long referenceSlotFingerprint(int packedFace, int source) {
        long hash = FNV_OFFSET_BASIS;
        hash = hashInt(hash, packedFace);
        return hashInt(hash, source);
    }

    private static long optimizedCoverageFingerprint(int source, int coverage) {
        long hash = FNV_OFFSET_BASIS;
        hash = hashInt(hash, source);
        return hashInt(hash, coverage);
    }

    private static long hashMetrics(long hash, Metrics m) {
        hash = hashInt(hash, m.referenceFacesChecked);
        hash = hashInt(hash, m.referenceMappedFaces);
        hash = hashInt(hash, m.referenceUnmappedFaces);
        hash = hashInt(hash, m.referenceAmbiguousFaces);
        hash = hashInt(hash, m.sourceQuadsChecked);
        hash = hashInt(hash, m.passthroughSourceIdentitiesChecked);
        hash = hashInt(hash, m.mergedCandidatesChecked);
        hash = hashInt(hash, m.mergedExpandedSourceFacesChecked);
        hash = hashInt(hash, m.materialChecks);
        hash = hashInt(hash, m.materialMatches);
        hash = hashInt(hash, m.directionChecks);
        hash = hashInt(hash, m.directionMatches);
        hash = hashInt(hash, m.canonicalGeometryChecks);
        hash = hashInt(hash, m.canonicalGeometryMatches);
        hash = hashInt(hash, m.uvChecks);
        hash = hashInt(hash, m.uvMatches);
        hash = hashInt(hash, m.colorChecks);
        hash = hashInt(hash, m.colorMatches);
        hash = hashInt(hash, m.lightChecks);
        hash = hashInt(hash, m.lightMatches);
        hash = hashInt(hash, m.missingSourceCoverage);
        hash = hashInt(hash, m.duplicateSourceCoverage);
        hash = hashInt(hash, m.optimizedCanonicalWithoutReference);
        return hashInt(hash, m.realMismatchCount);
    }

    public boolean exact() {
        return realMismatchCount == 0
                && missingSourceCoverage == 0
                && duplicateSourceCoverage == 0
                && optimizedCanonicalWithoutReference == 0
                && materialChecks == materialMatches
                && directionChecks == directionMatches
                && canonicalGeometryChecks == canonicalGeometryMatches
                && uvChecks == uvMatches
                && colorChecks == colorMatches
                && lightChecks == lightMatches
                && fixtureSelfTestPassed;
    }

    public boolean contentEquals(DifferentialCorrectnessProof other) {
        return other != null
                && sectionX == other.sectionX && sectionY == other.sectionY && sectionZ == other.sectionZ
                && referenceFacesChecked == other.referenceFacesChecked
                && referenceMappedFaces == other.referenceMappedFaces
                && referenceUnmappedFaces == other.referenceUnmappedFaces
                && referenceAmbiguousFaces == other.referenceAmbiguousFaces
                && sourceQuadsChecked == other.sourceQuadsChecked
                && passthroughSourceIdentitiesChecked == other.passthroughSourceIdentitiesChecked
                && mergedCandidatesChecked == other.mergedCandidatesChecked
                && mergedExpandedSourceFacesChecked == other.mergedExpandedSourceFacesChecked
                && materialChecks == other.materialChecks && materialMatches == other.materialMatches
                && directionChecks == other.directionChecks && directionMatches == other.directionMatches
                && canonicalGeometryChecks == other.canonicalGeometryChecks
                && canonicalGeometryMatches == other.canonicalGeometryMatches
                && uvChecks == other.uvChecks && uvMatches == other.uvMatches
                && colorChecks == other.colorChecks && colorMatches == other.colorMatches
                && lightChecks == other.lightChecks && lightMatches == other.lightMatches
                && missingSourceCoverage == other.missingSourceCoverage
                && duplicateSourceCoverage == other.duplicateSourceCoverage
                && optimizedCanonicalWithoutReference == other.optimizedCanonicalWithoutReference
                && realMismatchCount == other.realMismatchCount
                && fixtureSelfTestPassed == other.fixtureSelfTestPassed
                && firstMismatch.equals(other.firstMismatch)
                && sourceSnapshotFingerprint == other.sourceSnapshotFingerprint
                && sourceReferenceFingerprint == other.sourceReferenceFingerprint
                && sourceBakedFingerprint == other.sourceBakedFingerprint
                && sourceOracleFingerprint == other.sourceOracleFingerprint
                && sourceVisibilityFingerprint == other.sourceVisibilityFingerprint
                && sourceRenderKeyFingerprint == other.sourceRenderKeyFingerprint
                && sourceCandidateFingerprint == other.sourceCandidateFingerprint
                && sourceTransportFingerprint == other.sourceTransportFingerprint
                && sourceOptimizedFingerprint == other.sourceOptimizedFingerprint
                && fingerprint == other.fingerprint;
    }

    public int sectionX() { return sectionX; }
    public int sectionY() { return sectionY; }
    public int sectionZ() { return sectionZ; }
    public int referenceFacesChecked() { return referenceFacesChecked; }
    public int referenceMappedFaces() { return referenceMappedFaces; }
    public int referenceUnmappedFaces() { return referenceUnmappedFaces; }
    public int referenceAmbiguousFaces() { return referenceAmbiguousFaces; }
    public int sourceQuadsChecked() { return sourceQuadsChecked; }
    public int passthroughSourceIdentitiesChecked() { return passthroughSourceIdentitiesChecked; }
    public int mergedCandidatesChecked() { return mergedCandidatesChecked; }
    public int mergedExpandedSourceFacesChecked() { return mergedExpandedSourceFacesChecked; }
    public int materialChecks() { return materialChecks; }
    public int materialMatches() { return materialMatches; }
    public int directionChecks() { return directionChecks; }
    public int directionMatches() { return directionMatches; }
    public int canonicalGeometryChecks() { return canonicalGeometryChecks; }
    public int canonicalGeometryMatches() { return canonicalGeometryMatches; }
    public int uvChecks() { return uvChecks; }
    public int uvMatches() { return uvMatches; }
    public int colorChecks() { return colorChecks; }
    public int colorMatches() { return colorMatches; }
    public int lightChecks() { return lightChecks; }
    public int lightMatches() { return lightMatches; }
    public int missingSourceCoverage() { return missingSourceCoverage; }
    public int duplicateSourceCoverage() { return duplicateSourceCoverage; }
    public int optimizedCanonicalWithoutReference() { return optimizedCanonicalWithoutReference; }
    public int realMismatchCount() { return realMismatchCount; }
    public boolean fixtureSelfTestPassed() { return fixtureSelfTestPassed; }
    public MismatchFixture firstMismatch() { return firstMismatch; }
    public long sourceSnapshotFingerprint() { return sourceSnapshotFingerprint; }
    public long sourceReferenceFingerprint() { return sourceReferenceFingerprint; }
    public long sourceBakedFingerprint() { return sourceBakedFingerprint; }
    public long sourceOracleFingerprint() { return sourceOracleFingerprint; }
    public long sourceVisibilityFingerprint() { return sourceVisibilityFingerprint; }
    public long sourceRenderKeyFingerprint() { return sourceRenderKeyFingerprint; }
    public long sourceCandidateFingerprint() { return sourceCandidateFingerprint; }
    public long sourceTransportFingerprint() { return sourceTransportFingerprint; }
    public long sourceOptimizedFingerprint() { return sourceOptimizedFingerprint; }
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
