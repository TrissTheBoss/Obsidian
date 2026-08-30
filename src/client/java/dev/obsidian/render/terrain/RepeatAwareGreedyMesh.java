package dev.obsidian.render.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * P3.10 dev24 production-coordinate hybrid terrain mesh.
 *
 * <p>Every dev10 transport record suppresses its exact canonical source baked
 * quads and emits one repeat-aware large quad. Every other baked quad retains
 * its exact frozen source geometry, ARGB, UV and packed light. Production output
 * deliberately omits the old comparison-only face offset and RGB dimming. This
 * class owns no Minecraft live state and no GPU object.</p>
 */
public final class RepeatAwareGreedyMesh {
    public static final int PASSTHROUGH_BYTES_PER_VERTEX = BakedSectionMesh.BYTES_PER_VERTEX;
    public static final int MERGED_BYTES_PER_VERTEX = 60;
    public static final int VERTICES_PER_QUAD = 4;
    public static final int INDICES_PER_QUAD = 6;
    public static final int BYTES_PER_INDEX = Integer.BYTES;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Bounded primitive workspace intended for reuse by exactly one worker. */
    public static final class BuildScratch {
        private final boolean[] suppressed = new boolean[SectionBakedQuadSnapshot.MAX_QUADS];
        private final int[] passthroughSources = new int[SectionBakedQuadSnapshot.MAX_QUADS];
        private final short[] mergedRecords = new short[RepeatAwareTransportProof.MAX_RECORDS];
        private final int[] candidateToDescriptor = new int[RenderMergeCandidates.MAX_CANDIDATES];
        private final int[] directionMerged = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private final int[] directionCovered = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private final int[] directionSaved = new int[BinarySectionVisibility.DIRECTION_COUNT];
        private long uses;
        private int highWaterSourceQuads;
        private int highWaterMergedQuads;

        private void begin(int sourceQuads, int candidateCount) {
            uses++;
            Arrays.fill(suppressed, 0, sourceQuads, false);
            Arrays.fill(candidateToDescriptor, 0, candidateCount, -1);
            Arrays.fill(directionMerged, 0);
            Arrays.fill(directionCovered, 0);
            Arrays.fill(directionSaved, 0);
            highWaterSourceQuads = Math.max(highWaterSourceQuads, sourceQuads);
        }

        private void observeMerged(int merged) {
            highWaterMergedQuads = Math.max(highWaterMergedQuads, merged);
        }

        public long uses() { return uses; }
        public int highWaterSourceQuads() { return highWaterSourceQuads; }
        public int highWaterMergedQuads() { return highWaterMergedQuads; }
        public int retainedScratchBytes() {
            return suppressed.length
                    + passthroughSources.length * Integer.BYTES
                    + mergedRecords.length * Short.BYTES
                    + candidateToDescriptor.length * Integer.BYTES
                    + (directionMerged.length + directionCovered.length + directionSaved.length) * Integer.BYTES;
        }
    }

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final int sourceQuads;
    private final int sourceSolidQuads;
    private final int sourceCutoutQuads;
    private final int suppressedSourceQuads;
    private final int suppressedSolidQuads;
    private final int suppressedCutoutQuads;
    private final int passthroughSolidQuads;
    private final int passthroughCutoutQuads;
    private final int mergedSolidQuads;
    private final int mergedCutoutQuads;
    private final int facesSaved;
    private final byte[] passthroughVertices;
    private final byte[] mergedVertices;
    private final int[] indices;
    private final int[] passthroughSourceQuads;
    private final short[] mergedCandidateIndices;
    private final int[] directionMerged;
    private final int[] directionCovered;
    private final int[] directionSaved;
    private final long sourceSnapshotFingerprint;
    private final long sourceBakedFingerprint;
    private final long sourceRenderKeyFingerprint;
    private final long sourceCandidateFingerprint;
    private final long sourceUvFingerprint;
    private final long sourceTransportFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private RepeatAwareGreedyMesh(
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot baked,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareUvDescriptors uv,
            RepeatAwareTransportProof transport,
            int suppressedSourceQuads,
            int suppressedSolidQuads,
            int suppressedCutoutQuads,
            int passthroughSolidQuads,
            int passthroughCutoutQuads,
            int mergedSolidQuads,
            int mergedCutoutQuads,
            byte[] passthroughVertices,
            byte[] mergedVertices,
            int[] indices,
            int[] passthroughSourceQuads,
            short[] mergedCandidateIndices,
            int[] directionMerged,
            int[] directionCovered,
            int[] directionSaved,
            long fingerprint,
            long buildTimeNs) {
        this.sectionX = snapshot.sectionX();
        this.sectionY = snapshot.sectionY();
        this.sectionZ = snapshot.sectionZ();
        this.sourceQuads = baked.quadCount();
        this.sourceSolidQuads = baked.solidQuads();
        this.sourceCutoutQuads = baked.cutoutQuads();
        this.suppressedSourceQuads = suppressedSourceQuads;
        this.suppressedSolidQuads = suppressedSolidQuads;
        this.suppressedCutoutQuads = suppressedCutoutQuads;
        this.passthroughSolidQuads = passthroughSolidQuads;
        this.passthroughCutoutQuads = passthroughCutoutQuads;
        this.mergedSolidQuads = mergedSolidQuads;
        this.mergedCutoutQuads = mergedCutoutQuads;
        this.facesSaved = transport.facesSaved();
        this.passthroughVertices = passthroughVertices;
        this.mergedVertices = mergedVertices;
        this.indices = indices;
        this.passthroughSourceQuads = passthroughSourceQuads;
        this.mergedCandidateIndices = mergedCandidateIndices;
        this.directionMerged = directionMerged;
        this.directionCovered = directionCovered;
        this.directionSaved = directionSaved;
        this.sourceSnapshotFingerprint = snapshot.fingerprint();
        this.sourceBakedFingerprint = baked.fingerprint();
        this.sourceRenderKeyFingerprint = renderKeys.fingerprint();
        this.sourceCandidateFingerprint = candidates.fingerprint();
        this.sourceUvFingerprint = uv.fingerprint();
        this.sourceTransportFingerprint = transport.fingerprint();
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static RepeatAwareGreedyMesh build(
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot baked,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareUvDescriptors uv,
            RepeatAwareTransportProof transport) {
        return build(snapshot, baked, renderKeys, candidates, uv, transport, new BuildScratch());
    }

    public static RepeatAwareGreedyMesh build(
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot baked,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareUvDescriptors uv,
            RepeatAwareTransportProof transport,
            BuildScratch scratch) {
        validateSources(snapshot, baked, renderKeys, candidates, uv, transport);
        if (scratch == null) throw new NullPointerException("build scratch is required");
        long startNs = System.nanoTime();
        scratch.begin(baked.quadCount(), candidates.candidateCount());

        for (int descriptor = 0; descriptor < uv.descriptorCount(); descriptor++) {
            int candidate = uv.candidateIndex(descriptor);
            if (candidate < 0 || candidate >= candidates.candidateCount()
                    || scratch.candidateToDescriptor[candidate] != -1) {
                throw new IllegalStateException("Dev11 UV descriptor candidate accounting mismatch");
            }
            scratch.candidateToDescriptor[candidate] = descriptor;
        }

        int suppressed = 0;
        int suppressedSolid = 0;
        int suppressedCutout = 0;
        int mergedSolid = 0;
        int mergedCutout = 0;

        for (int record = 0; record < transport.recordCount(); record++) {
            int candidate = transport.candidateIndex(record);
            int packed = candidates.packedCandidate(candidate);
            int direction = RenderMergeCandidates.direction(packed);
            int area = RenderMergeCandidates.width(packed) * RenderMergeCandidates.height(packed);
            int representative = candidates.representativeSourceQuad(candidate);
            int descriptor = scratch.candidateToDescriptor[candidate];
            if (descriptor < 0 || uv.candidateIndex(descriptor) != candidate || area <= 1) {
                throw new IllegalStateException("Dev11 transport record is not backed by exact dev9 descriptor");
            }
            byte layer = baked.layer(representative);
            if (layer == SectionBakedQuadSnapshot.LAYER_SOLID) mergedSolid++;
            else if (layer == SectionBakedQuadSnapshot.LAYER_CUTOUT) mergedCutout++;
            else throw new IllegalStateException("Dev11 transport record has unsupported render layer");

            int coveredThis = suppressCandidate(candidates, renderKeys, baked, candidate, packed, layer, scratch);
            if (coveredThis != area) {
                throw new IllegalStateException("Dev11 candidate source suppression area mismatch");
            }
            suppressed += coveredThis;
            if (layer == SectionBakedQuadSnapshot.LAYER_SOLID) suppressedSolid += coveredThis;
            else suppressedCutout += coveredThis;
            scratch.directionMerged[direction]++;
            scratch.directionCovered[direction] += coveredThis;
            scratch.directionSaved[direction] += coveredThis - 1;
        }

        if (suppressed != transport.coveredFaces()
                || suppressedSolid + suppressedCutout != suppressed
                || mergedSolid + mergedCutout != transport.recordCount()
                || suppressed - transport.recordCount() != transport.facesSaved()) {
            throw new IllegalStateException("Dev11 aggregate source suppression mismatch");
        }

        int passthrough = 0;
        int passthroughSolid = 0;
        int passthroughCutout = 0;
        for (byte targetLayer = SectionBakedQuadSnapshot.LAYER_SOLID;
             targetLayer <= SectionBakedQuadSnapshot.LAYER_CUTOUT;
             targetLayer++) {
            for (int sourceQuad = 0; sourceQuad < baked.quadCount(); sourceQuad++) {
                if (baked.layer(sourceQuad) != targetLayer || scratch.suppressed[sourceQuad]) continue;
                scratch.passthroughSources[passthrough++] = sourceQuad;
                if (targetLayer == SectionBakedQuadSnapshot.LAYER_SOLID) passthroughSolid++;
                else passthroughCutout++;
            }
        }

        int merged = 0;
        for (byte targetLayer = SectionBakedQuadSnapshot.LAYER_SOLID;
             targetLayer <= SectionBakedQuadSnapshot.LAYER_CUTOUT;
             targetLayer++) {
            for (int record = 0; record < transport.recordCount(); record++) {
                int candidate = transport.candidateIndex(record);
                int representative = candidates.representativeSourceQuad(candidate);
                if (baked.layer(representative) != targetLayer) continue;
                scratch.mergedRecords[merged++] = (short) record;
            }
        }
        scratch.observeMerged(merged);

        if (passthrough != baked.quadCount() - suppressed
                || passthroughSolid != baked.solidQuads() - suppressedSolid
                || passthroughCutout != baked.cutoutQuads() - suppressedCutout
                || merged != transport.recordCount()
                || mergedSolid + mergedCutout != merged) {
            throw new IllegalStateException("Dev11 layer/source output accounting mismatch");
        }

        byte[] passthroughBytes = new byte[passthrough * VERTICES_PER_QUAD * PASSTHROUGH_BYTES_PER_VERTEX];
        ByteBuffer passthroughOut = ByteBuffer.wrap(passthroughBytes).order(ByteOrder.nativeOrder());
        for (int out = 0; out < passthrough; out++) {
            writePassthroughQuad(passthroughOut, baked, scratch.passthroughSources[out]);
        }

        byte[] mergedBytes = new byte[merged * VERTICES_PER_QUAD * MERGED_BYTES_PER_VERTEX];
        ByteBuffer mergedOut = ByteBuffer.wrap(mergedBytes).order(ByteOrder.nativeOrder());
        for (int out = 0; out < merged; out++) {
            int record = Short.toUnsignedInt(scratch.mergedRecords[out]);
            writeMergedQuad(mergedOut, baked, candidates, uv, transport, scratch, record);
        }

        int[] indexData = new int[(passthrough + merged) * INDICES_PER_QUAD];
        for (int quad = 0; quad < passthrough; quad++) {
            writeQuadIndices(indexData, quad * INDICES_PER_QUAD, quad * VERTICES_PER_QUAD);
        }
        int mergedIndexBase = passthrough * INDICES_PER_QUAD;
        for (int quad = 0; quad < merged; quad++) {
            writeQuadIndices(indexData, mergedIndexBase + quad * INDICES_PER_QUAD, quad * VERTICES_PER_QUAD);
        }

        int[] retainedPassthrough = Arrays.copyOf(scratch.passthroughSources, passthrough);
        short[] retainedMerged = new short[merged];
        for (int i = 0; i < merged; i++) {
            int record = Short.toUnsignedInt(scratch.mergedRecords[i]);
            retainedMerged[i] = (short) transport.candidateIndex(record);
        }
        int[] retainedDirectionMerged = Arrays.copyOf(scratch.directionMerged, scratch.directionMerged.length);
        int[] retainedDirectionCovered = Arrays.copyOf(scratch.directionCovered, scratch.directionCovered.length);
        int[] retainedDirectionSaved = Arrays.copyOf(scratch.directionSaved, scratch.directionSaved.length);

        validateProductionPresentation(
                baked, candidates, transport, passthroughBytes, mergedBytes,
                retainedPassthrough, retainedMerged);

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, snapshot.fingerprint());
        hash = hashLong(hash, baked.fingerprint());
        hash = hashLong(hash, renderKeys.fingerprint());
        hash = hashLong(hash, candidates.fingerprint());
        hash = hashLong(hash, uv.fingerprint());
        hash = hashLong(hash, transport.fingerprint());
        hash = hashInt(hash, suppressed);
        hash = hashInt(hash, passthrough);
        hash = hashInt(hash, merged);
        hash = hashBytes(hash, passthroughBytes);
        hash = hashBytes(hash, mergedBytes);
        for (int index : indexData) hash = hashInt(hash, index);
        for (int source : retainedPassthrough) hash = hashInt(hash, source);
        for (short candidate : retainedMerged) hash = hashInt(hash, Short.toUnsignedInt(candidate));
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            hash = hashInt(hash, retainedDirectionMerged[direction]);
            hash = hashInt(hash, retainedDirectionCovered[direction]);
            hash = hashInt(hash, retainedDirectionSaved[direction]);
        }

        RepeatAwareGreedyMesh result = new RepeatAwareGreedyMesh(
                snapshot, baked, renderKeys, candidates, uv, transport,
                suppressed, suppressedSolid, suppressedCutout,
                passthroughSolid, passthroughCutout, mergedSolid, mergedCutout,
                passthroughBytes, mergedBytes, indexData,
                retainedPassthrough, retainedMerged,
                retainedDirectionMerged, retainedDirectionCovered, retainedDirectionSaved,
                hash, System.nanoTime() - startNs);
        result.validateAgainst(snapshot, baked, renderKeys, candidates, uv, transport, scratch);
        return result;
    }

    private static int suppressCandidate(
            RenderMergeCandidates candidates,
            CanonicalFaceRenderKeys renderKeys,
            SectionBakedQuadSnapshot baked,
            int candidate,
            int packed,
            byte layer,
            BuildScratch scratch) {
        int direction = RenderMergeCandidates.direction(packed);
        int plane = RenderMergeCandidates.plane(packed);
        int u0 = RenderMergeCandidates.u(packed);
        int v0 = RenderMergeCandidates.v(packed);
        int width = RenderMergeCandidates.width(packed);
        int height = RenderMergeCandidates.height(packed);
        int covered = 0;
        for (int dv = 0; dv < height; dv++) {
            for (int du = 0; du < width; du++) {
                int u = u0 + du;
                int v = v0 + dv;
                int x = x(direction, plane, u, v);
                int y = y(direction, plane, u, v);
                int z = z(direction, plane, u, v);
                int sourceQuad = renderKeys.sourceQuad(x, y, z, direction);
                if (sourceQuad < 0 || sourceQuad >= baked.quadCount()
                        || baked.layer(sourceQuad) != layer
                        || scratch.suppressed[sourceQuad]) {
                    throw new IllegalStateException(
                            "Dev11 source suppression is missing, cross-layer, or duplicated for candidate " + candidate);
                }
                scratch.suppressed[sourceQuad] = true;
                covered++;
            }
        }
        return covered;
    }

    private static void writePassthroughQuad(
            ByteBuffer out,
            SectionBakedQuadSnapshot baked,
            int sourceQuad) {
        for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
            out.putFloat(baked.position(sourceQuad, vertex, 0));
            out.putFloat(baked.position(sourceQuad, vertex, 1));
            out.putFloat(baked.position(sourceQuad, vertex, 2));
            putExactColor(out, baked.exactArgbColor(sourceQuad, vertex));
            out.putFloat(baked.u(sourceQuad, vertex));
            out.putFloat(baked.v(sourceQuad, vertex));
            putLight(out, baked.packedLight(sourceQuad, vertex));
        }
    }

    private static void writeMergedQuad(
            ByteBuffer out,
            SectionBakedQuadSnapshot baked,
            RenderMergeCandidates candidates,
            RepeatAwareUvDescriptors uv,
            RepeatAwareTransportProof transport,
            BuildScratch scratch,
            int record) {
        int candidate = transport.candidateIndex(record);
        int descriptor = scratch.candidateToDescriptor[candidate];
        if (descriptor < 0 || uv.candidateIndex(descriptor) != candidate) {
            throw new IllegalStateException("Dev11 merged quad lost its dev9 descriptor identity");
        }
        int packed = candidates.packedCandidate(candidate);
        int direction = RenderMergeCandidates.direction(packed);
        int plane = RenderMergeCandidates.plane(packed);
        int u0 = RenderMergeCandidates.u(packed);
        int v0 = RenderMergeCandidates.v(packed);
        int width = RenderMergeCandidates.width(packed);
        int height = RenderMergeCandidates.height(packed);
        int representative = candidates.representativeSourceQuad(candidate);
        int sourceOrder = transport.sourceCornerOrderSignature(record);

        int orientation = uv.orientationSignature(descriptor);
        int atlasCorner0 = orientation & 0x3;
        int atlasCorner1 = (orientation >>> 2) & 0x3;
        int atlasCorner2 = (orientation >>> 4) & 0x3;
        float uLow = uv.uLow(descriptor);
        float uHigh = uv.uHigh(descriptor);
        float vLow = uv.vLow(descriptor);
        float vHigh = uv.vHigh(descriptor);
        float baseU = atlasU(atlasCorner0, uLow, uHigh);
        float baseV = atlasV(atlasCorner0, vLow, vHigh);
        float dSU = atlasU(atlasCorner1, uLow, uHigh) - baseU;
        float dSV = atlasV(atlasCorner1, vLow, vHigh) - baseV;
        float dTU = atlasU(atlasCorner2, uLow, uHigh) - baseU;
        float dTV = atlasV(atlasCorner2, vLow, vHigh) - baseV;

        for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
            int corner = (sourceOrder >>> (vertex * 2)) & 0x3;
            out.putFloat(position(direction, plane, u0, v0, width, height, corner, 0));
            out.putFloat(position(direction, plane, u0, v0, width, height, corner, 1));
            out.putFloat(position(direction, plane, u0, v0, width, height, corner, 2));
            putExactColor(out, baked.exactArgbColor(representative, vertex));
            out.putFloat((corner & 1) == 0 ? 0.0f : width);
            out.putFloat((corner & 2) == 0 ? 0.0f : height);
            putLight(out, baked.packedLight(representative, vertex));
            out.putFloat(baseU);
            out.putFloat(baseV);
            out.putFloat(dSU);
            out.putFloat(dSV);
            out.putFloat(dTU);
            out.putFloat(dTV);
            out.putFloat(width);
            out.putFloat(height);
        }
    }

    private static float atlasU(int corner, float low, float high) {
        return (corner & 1) == 0 ? low : high;
    }

    private static float atlasV(int corner, float low, float high) {
        return (corner & 2) == 0 ? low : high;
    }

    private static float position(
            int direction,
            int plane,
            int u0,
            int v0,
            int width,
            int height,
            int corner,
            int axis) {
        float u = (corner & 1) == 0 ? u0 : u0 + width;
        float v = (corner & 2) == 0 ? v0 : v0 + height;
        float fixed;
        float x;
        float y;
        float z;
        switch (direction) {
            case BinarySectionVisibility.WEST -> {
                fixed = plane;
                x = fixed; y = v; z = u;
            }
            case BinarySectionVisibility.EAST -> {
                fixed = plane + 1.0f;
                x = fixed; y = v; z = u;
            }
            case BinarySectionVisibility.DOWN -> {
                fixed = plane;
                x = u; y = fixed; z = v;
            }
            case BinarySectionVisibility.UP -> {
                fixed = plane + 1.0f;
                x = u; y = fixed; z = v;
            }
            case BinarySectionVisibility.NORTH -> {
                fixed = plane;
                x = u; y = v; z = fixed;
            }
            case BinarySectionVisibility.SOUTH -> {
                fixed = plane + 1.0f;
                x = u; y = v; z = fixed;
            }
            default -> throw new IllegalArgumentException("Invalid dev11 direction " + direction);
        }
        return switch (axis) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            default -> throw new IllegalArgumentException("Invalid position axis " + axis);
        };
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

    private static void putExactColor(ByteBuffer out, int argb) {
        out.put((byte) ((argb >>> 16) & 0xFF));
        out.put((byte) ((argb >>> 8) & 0xFF));
        out.put((byte) (argb & 0xFF));
        out.put((byte) ((argb >>> 24) & 0xFF));
    }

    private static void validateProductionPresentation(
            SectionBakedQuadSnapshot baked,
            RenderMergeCandidates candidates,
            RepeatAwareTransportProof transport,
            byte[] passthroughVertices,
            byte[] mergedVertices,
            int[] passthroughSources,
            short[] mergedCandidates) {
        ByteBuffer passthrough = ByteBuffer.wrap(passthroughVertices).order(ByteOrder.nativeOrder());
        for (int quad = 0; quad < passthroughSources.length; quad++) {
            int source = passthroughSources[quad];
            for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
                int base = (quad * VERTICES_PER_QUAD + vertex) * PASSTHROUGH_BYTES_PER_VERTEX;
                for (int axis = 0; axis < 3; axis++) {
                    int actual = Float.floatToRawIntBits(passthrough.getFloat(base + axis * Float.BYTES));
                    int expected = Float.floatToRawIntBits(baked.position(source, vertex, axis));
                    if (actual != expected) {
                        throw new IllegalStateException("P3.10 passthrough production position mismatch");
                    }
                }
                validateExactColorBytes(passthrough, base + 12, baked.exactArgbColor(source, vertex));
            }
        }

        ByteBuffer merged = ByteBuffer.wrap(mergedVertices).order(ByteOrder.nativeOrder());
        for (int quad = 0; quad < mergedCandidates.length; quad++) {
            int candidate = Short.toUnsignedInt(mergedCandidates[quad]);
            int record = -1;
            for (int i = 0; i < transport.recordCount(); i++) {
                if (transport.candidateIndex(i) == candidate) {
                    record = i;
                    break;
                }
            }
            if (record < 0) throw new IllegalStateException("P3.10 merged candidate lost transport identity");
            int packed = candidates.packedCandidate(candidate);
            int direction = RenderMergeCandidates.direction(packed);
            int plane = RenderMergeCandidates.plane(packed);
            int u0 = RenderMergeCandidates.u(packed);
            int v0 = RenderMergeCandidates.v(packed);
            int width = RenderMergeCandidates.width(packed);
            int height = RenderMergeCandidates.height(packed);
            int representative = candidates.representativeSourceQuad(candidate);
            int sourceOrder = transport.sourceCornerOrderSignature(record);
            for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
                int corner = (sourceOrder >>> (vertex * 2)) & 0x3;
                int base = (quad * VERTICES_PER_QUAD + vertex) * MERGED_BYTES_PER_VERTEX;
                for (int axis = 0; axis < 3; axis++) {
                    int actual = Float.floatToRawIntBits(merged.getFloat(base + axis * Float.BYTES));
                    int expected = Float.floatToRawIntBits(
                            position(direction, plane, u0, v0, width, height, corner, axis));
                    if (actual != expected) {
                        throw new IllegalStateException("P3.10 merged production position mismatch");
                    }
                }
                validateExactColorBytes(merged, base + 12, baked.exactArgbColor(representative, vertex));
            }
        }
    }

    private static void validateExactColorBytes(ByteBuffer data, int offset, int argb) {
        int r = Byte.toUnsignedInt(data.get(offset));
        int g = Byte.toUnsignedInt(data.get(offset + 1));
        int b = Byte.toUnsignedInt(data.get(offset + 2));
        int a = Byte.toUnsignedInt(data.get(offset + 3));
        if (r != ((argb >>> 16) & 0xFF)
                || g != ((argb >>> 8) & 0xFF)
                || b != (argb & 0xFF)
                || a != ((argb >>> 24) & 0xFF)) {
            throw new IllegalStateException("P3.10 production exact ARGB mismatch");
        }
    }

    private static void putLight(ByteBuffer out, int light) {
        out.putShort((short) (light & 0xFFFF));
        out.putShort((short) ((light >>> 16) & 0xFFFF));
    }

    private static void writeQuadIndices(int[] out, int offset, int baseVertex) {
        out[offset] = baseVertex;
        out[offset + 1] = baseVertex + 1;
        out[offset + 2] = baseVertex + 2;
        out[offset + 3] = baseVertex;
        out[offset + 4] = baseVertex + 2;
        out[offset + 5] = baseVertex + 3;
    }

    private static void validateSources(
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot baked,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareUvDescriptors uv,
            RepeatAwareTransportProof transport) {
        if (snapshot == null || baked == null || renderKeys == null || candidates == null || uv == null || transport == null) {
            throw new NullPointerException("dev11 hybrid mesh requires all immutable source sidecars");
        }
        if (baked.sectionX() != snapshot.sectionX()
                || baked.sectionY() != snapshot.sectionY()
                || baked.sectionZ() != snapshot.sectionZ()
                || baked.sourceSnapshotFingerprint() != snapshot.fingerprint()
                || renderKeys.sourceSnapshotFingerprint() != snapshot.fingerprint()
                || renderKeys.sourceBakedFingerprint() != baked.fingerprint()
                || candidates.sourceRenderKeyFingerprint() != renderKeys.fingerprint()
                || candidates.sourceBakedFingerprint() != baked.fingerprint()
                || uv.sourceCandidateFingerprint() != candidates.fingerprint()
                || uv.sourceRenderKeyFingerprint() != renderKeys.fingerprint()
                || uv.sourceBakedFingerprint() != baked.fingerprint()
                || transport.sourceCandidateFingerprint() != candidates.fingerprint()
                || transport.sourceUvFingerprint() != uv.fingerprint()
                || transport.sourceBakedFingerprint() != baked.fingerprint()
                || transport.recordCount() != uv.repeatAwareFourVertexSafe()) {
            throw new IllegalArgumentException("Dev11 hybrid source identity/accounting mismatch");
        }
    }

    public void validateAgainst(
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot baked,
            CanonicalFaceRenderKeys renderKeys,
            RenderMergeCandidates candidates,
            RepeatAwareUvDescriptors uv,
            RepeatAwareTransportProof transport,
            BuildScratch scratch) {
        validateSources(snapshot, baked, renderKeys, candidates, uv, transport);
        if (sectionX != snapshot.sectionX() || sectionY != snapshot.sectionY() || sectionZ != snapshot.sectionZ()
                || sourceSnapshotFingerprint != snapshot.fingerprint()
                || sourceBakedFingerprint != baked.fingerprint()
                || sourceRenderKeyFingerprint != renderKeys.fingerprint()
                || sourceCandidateFingerprint != candidates.fingerprint()
                || sourceUvFingerprint != uv.fingerprint()
                || sourceTransportFingerprint != transport.fingerprint()) {
            throw new IllegalStateException("Dev11 retained source identity mismatch");
        }
        if (sourceQuads != baked.quadCount()
                || sourceSolidQuads != baked.solidQuads()
                || sourceCutoutQuads != baked.cutoutQuads()
                || suppressedSourceQuads != transport.coveredFaces()
                || mergedQuadCount() != transport.recordCount()
                || passthroughQuadCount() + suppressedSourceQuads != sourceQuads
                || hybridQuadCount() != passthroughQuadCount() + mergedQuadCount()
                || hybridQuadCount() != sourceQuads - transport.facesSaved()
                || facesSaved != suppressedSourceQuads - mergedQuadCount()
                || sourceSolidQuads != passthroughSolidQuads + suppressedSolidQuads
                || sourceCutoutQuads != passthroughCutoutQuads + suppressedCutoutQuads
                || hybridSolidQuads() != passthroughSolidQuads + mergedSolidQuads
                || hybridCutoutQuads() != passthroughCutoutQuads + mergedCutoutQuads
                || passthroughVertices.length != passthroughQuadCount() * VERTICES_PER_QUAD * PASSTHROUGH_BYTES_PER_VERTEX
                || mergedVertices.length != mergedQuadCount() * VERTICES_PER_QUAD * MERGED_BYTES_PER_VERTEX
                || indices.length != hybridQuadCount() * INDICES_PER_QUAD
                || totalUploadBytes() > sourceQuads * (VERTICES_PER_QUAD * BakedSectionMesh.BYTES_PER_VERTEX
                    + INDICES_PER_QUAD * BYTES_PER_INDEX)) {
            throw new IllegalStateException("Dev11 hybrid aggregate/layer/upload accounting mismatch");
        }

        scratch.begin(baked.quadCount(), candidates.candidateCount());
        for (int descriptor = 0; descriptor < uv.descriptorCount(); descriptor++) {
            scratch.candidateToDescriptor[uv.candidateIndex(descriptor)] = descriptor;
        }
        int expectedSuppressed = 0;
        for (int record = 0; record < transport.recordCount(); record++) {
            int candidate = transport.candidateIndex(record);
            int packed = candidates.packedCandidate(candidate);
            byte layer = baked.layer(candidates.representativeSourceQuad(candidate));
            expectedSuppressed += suppressCandidate(candidates, renderKeys, baked, candidate, packed, layer, scratch);
        }
        if (expectedSuppressed != suppressedSourceQuads) {
            throw new IllegalStateException("Dev11 validation suppression count mismatch");
        }

        int passthrough = 0;
        for (byte layer = SectionBakedQuadSnapshot.LAYER_SOLID; layer <= SectionBakedQuadSnapshot.LAYER_CUTOUT; layer++) {
            for (int source = 0; source < baked.quadCount(); source++) {
                if (baked.layer(source) == layer && !scratch.suppressed[source]) {
                    if (passthrough >= passthroughSourceQuads.length || passthroughSourceQuads[passthrough] != source) {
                        throw new IllegalStateException("Dev11 passthrough source identity/order mismatch");
                    }
                    passthrough++;
                }
            }
        }
        if (passthrough != passthroughSourceQuads.length) {
            throw new IllegalStateException("Dev11 passthrough source count mismatch");
        }

        int merged = 0;
        for (byte layer = SectionBakedQuadSnapshot.LAYER_SOLID; layer <= SectionBakedQuadSnapshot.LAYER_CUTOUT; layer++) {
            for (int record = 0; record < transport.recordCount(); record++) {
                int candidate = transport.candidateIndex(record);
                if (baked.layer(candidates.representativeSourceQuad(candidate)) != layer) continue;
                if (merged >= mergedCandidateIndices.length
                        || Short.toUnsignedInt(mergedCandidateIndices[merged]) != candidate) {
                    throw new IllegalStateException("Dev11 merged candidate identity/order mismatch");
                }
                merged++;
            }
        }
        if (merged != mergedCandidateIndices.length) {
            throw new IllegalStateException("Dev11 merged candidate count mismatch");
        }

        long mergedDirection = 0L;
        long coveredDirection = 0L;
        long savedDirection = 0L;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            mergedDirection += directionMerged[direction];
            coveredDirection += directionCovered[direction];
            savedDirection += directionSaved[direction];
            if (directionMerged[direction] != transport.directionRecordCount(direction)
                    || directionCovered[direction] != transport.directionCoveredFaces(direction)
                    || directionSaved[direction] != transport.directionFacesSaved(direction)) {
                throw new IllegalStateException("Dev11 per-direction transport accounting mismatch");
            }
        }
        if (mergedDirection != mergedQuadCount()
                || coveredDirection != suppressedSourceQuads
                || savedDirection != facesSaved) {
            throw new IllegalStateException("Dev11 direction aggregate mismatch");
        }
    }

    public boolean contentEquals(RepeatAwareGreedyMesh other) {
        return other != null
                && sectionX == other.sectionX && sectionY == other.sectionY && sectionZ == other.sectionZ
                && sourceQuads == other.sourceQuads
                && sourceSolidQuads == other.sourceSolidQuads
                && sourceCutoutQuads == other.sourceCutoutQuads
                && suppressedSourceQuads == other.suppressedSourceQuads
                && suppressedSolidQuads == other.suppressedSolidQuads
                && suppressedCutoutQuads == other.suppressedCutoutQuads
                && passthroughSolidQuads == other.passthroughSolidQuads
                && passthroughCutoutQuads == other.passthroughCutoutQuads
                && mergedSolidQuads == other.mergedSolidQuads
                && mergedCutoutQuads == other.mergedCutoutQuads
                && facesSaved == other.facesSaved
                && sourceSnapshotFingerprint == other.sourceSnapshotFingerprint
                && sourceBakedFingerprint == other.sourceBakedFingerprint
                && sourceRenderKeyFingerprint == other.sourceRenderKeyFingerprint
                && sourceCandidateFingerprint == other.sourceCandidateFingerprint
                && sourceUvFingerprint == other.sourceUvFingerprint
                && sourceTransportFingerprint == other.sourceTransportFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(passthroughVertices, other.passthroughVertices)
                && Arrays.equals(mergedVertices, other.mergedVertices)
                && Arrays.equals(indices, other.indices)
                && Arrays.equals(passthroughSourceQuads, other.passthroughSourceQuads)
                && Arrays.equals(mergedCandidateIndices, other.mergedCandidateIndices)
                && Arrays.equals(directionMerged, other.directionMerged)
                && Arrays.equals(directionCovered, other.directionCovered)
                && Arrays.equals(directionSaved, other.directionSaved);
    }

    public ByteBuffer passthroughVertexBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(passthroughVertices.length).order(ByteOrder.nativeOrder());
        out.put(passthroughVertices);
        return out.flip();
    }

    public ByteBuffer mergedVertexBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(mergedVertices.length).order(ByteOrder.nativeOrder());
        out.put(mergedVertices);
        return out.flip();
    }

    public ByteBuffer indexBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(indexBytes()).order(ByteOrder.nativeOrder());
        for (int index : indices) out.putInt(index);
        return out.flip();
    }

    public int sectionX() { return sectionX; }
    public int sectionY() { return sectionY; }
    public int sectionZ() { return sectionZ; }
    public int originX() { return sectionX * SectionSnapshot.INTERIOR_SIZE; }
    public int originY() { return sectionY * SectionSnapshot.INTERIOR_SIZE; }
    public int originZ() { return sectionZ * SectionSnapshot.INTERIOR_SIZE; }
    public int sourceQuadCount() { return sourceQuads; }
    public int sourceSolidQuadCount() { return sourceSolidQuads; }
    public int sourceCutoutQuadCount() { return sourceCutoutQuads; }
    public int suppressedSourceQuads() { return suppressedSourceQuads; }
    public int suppressedSolidQuads() { return suppressedSolidQuads; }
    public int suppressedCutoutQuads() { return suppressedCutoutQuads; }
    public int passthroughQuadCount() { return passthroughSolidQuads + passthroughCutoutQuads; }
    public int passthroughSolidQuadCount() { return passthroughSolidQuads; }
    public int passthroughCutoutQuadCount() { return passthroughCutoutQuads; }
    public int mergedQuadCount() { return mergedSolidQuads + mergedCutoutQuads; }
    public int mergedSolidQuadCount() { return mergedSolidQuads; }
    public int mergedCutoutQuadCount() { return mergedCutoutQuads; }
    public int hybridQuadCount() { return passthroughQuadCount() + mergedQuadCount(); }
    public int hybridSolidQuads() { return passthroughSolidQuads + mergedSolidQuads; }
    public int hybridCutoutQuads() { return passthroughCutoutQuads + mergedCutoutQuads; }
    public int facesSaved() { return facesSaved; }
    public int passthroughVertexBytes() { return passthroughVertices.length; }
    public int mergedVertexBytes() { return mergedVertices.length; }
    public int indexBytes() { return indices.length * BYTES_PER_INDEX; }
    public int totalUploadBytes() { return passthroughVertexBytes() + mergedVertexBytes() + indexBytes(); }
    public int sourceUploadBytes() {
        return sourceQuads * (VERTICES_PER_QUAD * BakedSectionMesh.BYTES_PER_VERTEX
                + INDICES_PER_QUAD * BYTES_PER_INDEX);
    }
    public int passthroughSolidIndexCount() { return passthroughSolidQuads * INDICES_PER_QUAD; }
    public int passthroughCutoutIndexCount() { return passthroughCutoutQuads * INDICES_PER_QUAD; }
    public int mergedSolidIndexCount() { return mergedSolidQuads * INDICES_PER_QUAD; }
    public int mergedCutoutIndexCount() { return mergedCutoutQuads * INDICES_PER_QUAD; }
    public int passthroughSolidFirstLocalIndex() { return 0; }
    public int passthroughCutoutFirstLocalIndex() { return passthroughSolidIndexCount(); }
    public int mergedSolidFirstLocalIndex() { return passthroughQuadCount() * INDICES_PER_QUAD; }
    public int mergedCutoutFirstLocalIndex() { return mergedSolidFirstLocalIndex() + mergedSolidIndexCount(); }
    public int passthroughSourceQuad(int index) {
        if (index < 0 || index >= passthroughSourceQuads.length) throw new IndexOutOfBoundsException(index);
        return passthroughSourceQuads[index];
    }
    public int mergedCandidateIndex(int index) {
        if (index < 0 || index >= mergedCandidateIndices.length) throw new IndexOutOfBoundsException(index);
        return Short.toUnsignedInt(mergedCandidateIndices[index]);
    }
    public int directionMerged(int direction) { validateDirection(direction); return directionMerged[direction]; }
    public int directionCoveredFaces(int direction) { validateDirection(direction); return directionCovered[direction]; }
    public int directionFacesSaved(int direction) { validateDirection(direction); return directionSaved[direction]; }
    public long sourceSnapshotFingerprint() { return sourceSnapshotFingerprint; }
    public long sourceBakedFingerprint() { return sourceBakedFingerprint; }
    public long sourceRenderKeyFingerprint() { return sourceRenderKeyFingerprint; }
    public long sourceCandidateFingerprint() { return sourceCandidateFingerprint; }
    public long sourceUvFingerprint() { return sourceUvFingerprint; }
    public long sourceTransportFingerprint() { return sourceTransportFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    private static void validateDirection(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IndexOutOfBoundsException(direction);
        }
    }

    private static long hashBytes(long hash, byte[] bytes) {
        for (byte value : bytes) {
            hash ^= Byte.toUnsignedInt(value);
            hash *= FNV_PRIME;
        }
        return hash;
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
