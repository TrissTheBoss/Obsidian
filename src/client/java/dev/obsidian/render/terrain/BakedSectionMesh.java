package dev.obsidian.render.terrain;

import net.minecraft.core.Direction;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Pure P2.5 drawable generated only from an immutable
 * {@link SectionBakedQuadSnapshot}.
 *
 * <p>Quads are grouped deterministically as contiguous SOLID then CUTOUT
 * ranges. Vertex layout exactly matches {@code DefaultVertexFormat.BLOCK}:
 * float3 Position, RGBA8 Color, float2 UV0, signed-short2 UV2/light = 28
 * bytes. Exact captured color/light values remain retained; only emitted RGB
 * is uniformly multiplied by 3/4 for comparison.</p>
 */
public final class BakedSectionMesh {
    public static final int VERTICES_PER_QUAD = 4;
    public static final int INDICES_PER_QUAD = 6;
    public static final int BYTES_PER_VERTEX = 28;
    public static final int BYTES_PER_INDEX = Integer.BYTES;
    public static final int COMPARISON_COLOR_NUMERATOR = 3;
    public static final int COMPARISON_COLOR_DENOMINATOR = 4;
    public static final float COMPARISON_FACE_OFFSET = 1.0f / 512.0f;

    public static final int MAX_VERTEX_BYTES =
            SectionBakedQuadSnapshot.MAX_QUADS * VERTICES_PER_QUAD * BYTES_PER_VERTEX;
    public static final int MAX_INDEX_BYTES =
            SectionBakedQuadSnapshot.MAX_QUADS * INDICES_PER_QUAD * BYTES_PER_INDEX;
    public static final int MAX_UPLOAD_BYTES = MAX_VERTEX_BYTES + MAX_INDEX_BYTES;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final float[] positions;
    private final float[] uvs;
    private final int[] comparisonRgba;
    private final int[] exactArgb;
    private final int[] packedLights;
    private final int[] indices;
    private final int[] sourceQuads;
    private final int[] materialIds;
    private final byte[] layers;
    private final int solidQuadCount;
    private final int cutoutQuadCount;
    private final long sourceFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private BakedSectionMesh(
            int sectionX,
            int sectionY,
            int sectionZ,
            float[] positions,
            float[] uvs,
            int[] comparisonRgba,
            int[] exactArgb,
            int[] packedLights,
            int[] indices,
            int[] sourceQuads,
            int[] materialIds,
            byte[] layers,
            int solidQuadCount,
            int cutoutQuadCount,
            long sourceFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.originX = sectionX * SectionSnapshot.INTERIOR_SIZE;
        this.originY = sectionY * SectionSnapshot.INTERIOR_SIZE;
        this.originZ = sectionZ * SectionSnapshot.INTERIOR_SIZE;
        this.positions = positions;
        this.uvs = uvs;
        this.comparisonRgba = comparisonRgba;
        this.exactArgb = exactArgb;
        this.packedLights = packedLights;
        this.indices = indices;
        this.sourceQuads = sourceQuads;
        this.materialIds = materialIds;
        this.layers = layers;
        this.solidQuadCount = solidQuadCount;
        this.cutoutQuadCount = cutoutQuadCount;
        this.sourceFingerprint = sourceFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static BakedSectionMesh build(SectionSnapshot snapshot, SectionBakedQuadSnapshot source) {
        if (snapshot == null || source == null) {
            throw new NullPointerException("snapshot and generalized quad source are required");
        }
        if (source.sectionX() != snapshot.sectionX()
                || source.sectionY() != snapshot.sectionY()
                || source.sectionZ() != snapshot.sectionZ()
                || source.sourceSnapshotFingerprint() != snapshot.fingerprint()) {
            throw new IllegalStateException("P2.5 generalized quad source does not match section snapshot");
        }
        if (source.quadCount() <= 0 || source.quadCount() > SectionBakedQuadSnapshot.MAX_QUADS) {
            throw new IllegalStateException("P2.5 generalized quad count is outside bounded range");
        }

        long startNs = System.nanoTime();
        int quadCount = source.quadCount();
        float[] positions = new float[quadCount * VERTICES_PER_QUAD * 3];
        float[] uvs = new float[quadCount * VERTICES_PER_QUAD * 2];
        int[] comparison = new int[quadCount * VERTICES_PER_QUAD];
        int[] exact = new int[quadCount * VERTICES_PER_QUAD];
        int[] lights = new int[quadCount * VERTICES_PER_QUAD];
        int[] indices = new int[quadCount * INDICES_PER_QUAD];
        int[] sourceQuads = new int[quadCount];
        int[] materialIds = new int[quadCount];
        byte[] layers = new byte[quadCount];
        int outputQuad = 0;
        long hash = FNV_OFFSET_BASIS;

        for (byte targetLayer = SectionBakedQuadSnapshot.LAYER_SOLID;
             targetLayer <= SectionBakedQuadSnapshot.LAYER_CUTOUT;
             targetLayer++) {
            for (int sourceQuad = 0; sourceQuad < source.quadCount(); sourceQuad++) {
                if (source.layer(sourceQuad) != targetLayer) {
                    continue;
                }
                int out = outputQuad++;
                int positionBase = out * VERTICES_PER_QUAD * 3;
                int uvBase = out * VERTICES_PER_QUAD * 2;
                int vertexBase = out * VERTICES_PER_QUAD;

                float offsetX = 0.0f;
                float offsetY = 0.0f;
                float offsetZ = 0.0f;
                int directionOrdinal = source.direction(sourceQuad);
                if (directionOrdinal >= 0 && directionOrdinal < Direction.values().length) {
                    Direction direction = Direction.values()[directionOrdinal];
                    offsetX = direction.getStepX() * COMPARISON_FACE_OFFSET;
                    offsetY = direction.getStepY() * COMPARISON_FACE_OFFSET;
                    offsetZ = direction.getStepZ() * COMPARISON_FACE_OFFSET;
                }

                for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
                    int p = positionBase + vertex * 3;
                    positions[p] = source.position(sourceQuad, vertex, 0) + offsetX;
                    positions[p + 1] = source.position(sourceQuad, vertex, 1) + offsetY;
                    positions[p + 2] = source.position(sourceQuad, vertex, 2) + offsetZ;
                    int uv = uvBase + vertex * 2;
                    uvs[uv] = source.u(sourceQuad, vertex);
                    uvs[uv + 1] = source.v(sourceQuad, vertex);
                    int exactColor = source.exactArgbColor(sourceQuad, vertex);
                    exact[vertexBase + vertex] = exactColor;
                    comparison[vertexBase + vertex] = comparisonRgba(exactColor);
                    lights[vertexBase + vertex] = source.packedLight(sourceQuad, vertex);
                }

                int baseVertex = out * VERTICES_PER_QUAD;
                int index = out * INDICES_PER_QUAD;
                indices[index] = baseVertex;
                indices[index + 1] = baseVertex + 1;
                indices[index + 2] = baseVertex + 2;
                indices[index + 3] = baseVertex;
                indices[index + 4] = baseVertex + 2;
                indices[index + 5] = baseVertex + 3;
                sourceQuads[out] = sourceQuad;
                materialIds[out] = source.materialId(sourceQuad);
                layers[out] = targetLayer;

                hash = hashInt(hash, sourceQuad);
                hash = hashInt(hash, materialIds[out]);
                hash = hashInt(hash, Byte.toUnsignedInt(targetLayer));
                for (int i = 0; i < VERTICES_PER_QUAD * 3; i++) {
                    hash = hashInt(hash, Float.floatToRawIntBits(positions[positionBase + i]));
                }
                for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
                    hash = hashInt(hash, Float.floatToRawIntBits(uvs[uvBase + vertex * 2]));
                    hash = hashInt(hash, Float.floatToRawIntBits(uvs[uvBase + vertex * 2 + 1]));
                    hash = hashInt(hash, exact[vertexBase + vertex]);
                    hash = hashInt(hash, lights[vertexBase + vertex]);
                }
            }
        }

        if (outputQuad != quadCount
                || source.solidQuads() + source.cutoutQuads() != quadCount
                || source.solidQuads() < 0
                || source.cutoutQuads() < 0) {
            throw new IllegalStateException("P2.5 layer/quad accounting mismatch");
        }
        hash = hashLong(hash, source.fingerprint());
        hash = hashInt(hash, source.solidQuads());
        hash = hashInt(hash, source.cutoutQuads());
        hash = hashInt(hash, Float.floatToRawIntBits(COMPARISON_FACE_OFFSET));

        BakedSectionMesh mesh = new BakedSectionMesh(
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(),
                positions, uvs, comparison, exact, lights, indices, sourceQuads, materialIds, layers,
                source.solidQuads(), source.cutoutQuads(), source.fingerprint(), hash,
                System.nanoTime() - startNs);
        mesh.validateAgainst(snapshot, source);
        return mesh;
    }

    public void validateAgainst(SectionSnapshot snapshot, SectionBakedQuadSnapshot source) {
        if (sectionX != snapshot.sectionX() || sectionY != snapshot.sectionY() || sectionZ != snapshot.sectionZ()) {
            throw new IllegalStateException("P2.5 mesh section mismatch");
        }
        if (sourceFingerprint != source.fingerprint() || quadCount() != source.quadCount()) {
            throw new IllegalStateException("P2.5 mesh source identity mismatch");
        }
        if (solidQuadCount != source.solidQuads() || cutoutQuadCount != source.cutoutQuads()) {
            throw new IllegalStateException("P2.5 mesh layer count mismatch");
        }
        if (positions.length != vertexCount() * 3
                || uvs.length != vertexCount() * 2
                || comparisonRgba.length != vertexCount()
                || exactArgb.length != vertexCount()
                || packedLights.length != vertexCount()
                || indices.length != indexCount()
                || sourceQuads.length != quadCount()
                || materialIds.length != quadCount()
                || layers.length != quadCount()) {
            throw new IllegalStateException("P2.5 mesh array accounting mismatch");
        }
        for (int quad = 0; quad < quadCount(); quad++) {
            byte expectedLayer = quad < solidQuadCount
                    ? SectionBakedQuadSnapshot.LAYER_SOLID
                    : SectionBakedQuadSnapshot.LAYER_CUTOUT;
            if (layers[quad] != expectedLayer || source.layer(sourceQuads[quad]) != expectedLayer) {
                throw new IllegalStateException("P2.5 mesh layer ordering mismatch");
            }
            if (materialIds[quad] != source.materialId(sourceQuads[quad])) {
                throw new IllegalStateException("P2.5 mesh material identity mismatch");
            }
            int base = quad * VERTICES_PER_QUAD;
            int index = quad * INDICES_PER_QUAD;
            if (indices[index] != base
                    || indices[index + 1] != base + 1
                    || indices[index + 2] != base + 2
                    || indices[index + 3] != base
                    || indices[index + 4] != base + 2
                    || indices[index + 5] != base + 3) {
                throw new IllegalStateException("P2.5 mesh quad index mismatch");
            }
        }
    }

    public boolean contentEquals(BakedSectionMesh other) {
        return other != null
                && sectionX == other.sectionX && sectionY == other.sectionY && sectionZ == other.sectionZ
                && solidQuadCount == other.solidQuadCount && cutoutQuadCount == other.cutoutQuadCount
                && sourceFingerprint == other.sourceFingerprint && fingerprint == other.fingerprint
                && Arrays.equals(positions, other.positions)
                && Arrays.equals(uvs, other.uvs)
                && Arrays.equals(comparisonRgba, other.comparisonRgba)
                && Arrays.equals(exactArgb, other.exactArgb)
                && Arrays.equals(packedLights, other.packedLights)
                && Arrays.equals(indices, other.indices)
                && Arrays.equals(sourceQuads, other.sourceQuads)
                && Arrays.equals(materialIds, other.materialIds)
                && Arrays.equals(layers, other.layers);
    }

    public ByteBuffer vertexBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(vertexBytes()).order(ByteOrder.nativeOrder());
        for (int vertex = 0; vertex < vertexCount(); vertex++) {
            int p = vertex * 3;
            int uv = vertex * 2;
            int rgba = comparisonRgba[vertex];
            int light = packedLights[vertex];
            out.putFloat(positions[p]);
            out.putFloat(positions[p + 1]);
            out.putFloat(positions[p + 2]);
            out.put((byte) rgba);
            out.put((byte) (rgba >>> 8));
            out.put((byte) (rgba >>> 16));
            out.put((byte) (rgba >>> 24));
            out.putFloat(uvs[uv]);
            out.putFloat(uvs[uv + 1]);
            out.putShort((short) (light & 0xFFFF));
            out.putShort((short) ((light >>> 16) & 0xFFFF));
        }
        return out.flip();
    }

    public ByteBuffer indexBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(indexBytes()).order(ByteOrder.nativeOrder());
        for (int index : indices) {
            out.putInt(index);
        }
        return out.flip();
    }

    private static int comparisonRgba(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = r * COMPARISON_COLOR_NUMERATOR / COMPARISON_COLOR_DENOMINATOR;
        g = g * COMPARISON_COLOR_NUMERATOR / COMPARISON_COLOR_DENOMINATOR;
        b = b * COMPARISON_COLOR_NUMERATOR / COMPARISON_COLOR_DENOMINATOR;
        return r | (g << 8) | (b << 16) | (a << 24);
    }

    private static long hashInt(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value);
        return hash * FNV_PRIME;
    }

    private static long hashLong(long hash, long value) {
        hash = hashInt(hash, (int) value);
        return hashInt(hash, (int) (value >>> 32));
    }

    public int sectionX() { return sectionX; }
    public int sectionY() { return sectionY; }
    public int sectionZ() { return sectionZ; }
    public int originX() { return originX; }
    public int originY() { return originY; }
    public int originZ() { return originZ; }
    public int quadCount() { return solidQuadCount + cutoutQuadCount; }
    public int solidQuadCount() { return solidQuadCount; }
    public int cutoutQuadCount() { return cutoutQuadCount; }
    public int vertexCount() { return quadCount() * VERTICES_PER_QUAD; }
    public int indexCount() { return quadCount() * INDICES_PER_QUAD; }
    public int solidIndexCount() { return solidQuadCount * INDICES_PER_QUAD; }
    public int cutoutIndexCount() { return cutoutQuadCount * INDICES_PER_QUAD; }
    public int cutoutFirstLocalIndex() { return solidIndexCount(); }
    public int vertexBytes() { return vertexCount() * BYTES_PER_VERTEX; }
    public int indexBytes() { return indexCount() * BYTES_PER_INDEX; }
    public long sourceFingerprint() { return sourceFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }
    public float comparisonFaceOffset() { return COMPARISON_FACE_OFFSET; }
}
