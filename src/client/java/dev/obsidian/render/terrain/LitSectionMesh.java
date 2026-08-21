package dev.obsidian.render.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Pure P2.4 drawable built only from immutable section/reference/material/light captures.
 *
 * <p>Vertex layout exactly matches {@code DefaultVertexFormat.BLOCK}:
 * float3 Position, RGBA8 Color, float2 UV0, signed-short2 UV2/light = 28 bytes.
 * Exact unmodulated ARGB AO/tint/shade colors and packed light are retained;
 * only the emitted RGB is uniformly multiplied by 3/4 for human comparison.</p>
 */
public final class LitSectionMesh {
    public static final int VERTICES_PER_FACE = 4;
    public static final int INDICES_PER_FACE = 6;
    public static final int BYTES_PER_VERTEX = 28;
    public static final int BYTES_PER_INDEX = Integer.BYTES;
    public static final int COMPARISON_COLOR_NUMERATOR = 3;
    public static final int COMPARISON_COLOR_DENOMINATOR = 4;

    public static final int MAX_VERTEX_BYTES = ReferenceFaceMesh.MAX_FACES * VERTICES_PER_FACE * BYTES_PER_VERTEX;
    public static final int MAX_INDEX_BYTES = ReferenceFaceMesh.MAX_FACES * INDICES_PER_FACE * BYTES_PER_INDEX;
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
    private final int[] sourceReferenceFaces;
    private final int faceCount;
    private final int referenceFaceCount;
    private final long referenceFingerprint;
    private final long materialFingerprint;
    private final long lightingFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private LitSectionMesh(
            int sectionX,
            int sectionY,
            int sectionZ,
            float[] positions,
            float[] uvs,
            int[] comparisonRgba,
            int[] exactArgb,
            int[] packedLights,
            int[] indices,
            int[] sourceReferenceFaces,
            int faceCount,
            int referenceFaceCount,
            long referenceFingerprint,
            long materialFingerprint,
            long lightingFingerprint,
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
        this.sourceReferenceFaces = sourceReferenceFaces;
        this.faceCount = faceCount;
        this.referenceFaceCount = referenceFaceCount;
        this.referenceFingerprint = referenceFingerprint;
        this.materialFingerprint = materialFingerprint;
        this.lightingFingerprint = lightingFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static LitSectionMesh build(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionMaterialSnapshot materials,
            SectionLightingSnapshot lighting) {
        if (snapshot == null || reference == null || materials == null || lighting == null) {
            throw new NullPointerException("snapshot, reference, materials and lighting are required");
        }
        reference.validateAgainst(snapshot);
        lighting.validateAgainst(snapshot, reference, materials);

        long startNs = System.nanoTime();
        int emittedFaces = materials.supportedFaces();
        float[] positions = new float[emittedFaces * VERTICES_PER_FACE * 3];
        float[] uvs = new float[emittedFaces * VERTICES_PER_FACE * 2];
        int[] comparisonRgba = new int[emittedFaces * VERTICES_PER_FACE];
        int[] exactArgb = new int[emittedFaces * VERTICES_PER_FACE];
        int[] lights = new int[emittedFaces * VERTICES_PER_FACE];
        int[] indices = new int[emittedFaces * INDICES_PER_FACE];
        int[] sourceFaces = new int[emittedFaces];
        int outputFace = 0;
        long hash = FNV_OFFSET_BASIS;

        for (int referenceFace = 0; referenceFace < reference.faceCount(); referenceFace++) {
            if (materials.materialId(referenceFace) == SectionMaterialSnapshot.UNSUPPORTED_MATERIAL) {
                continue;
            }
            if (lighting.mode(referenceFace) == SectionLightingSnapshot.MODE_UNSUPPORTED) {
                throw new IllegalStateException("P2.4 materialized face has no lighting result");
            }

            int packed = reference.packedFace(referenceFace);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;

            int positionOffset = outputFace * VERTICES_PER_FACE * 3;
            writeFacePositions(positions, positionOffset, x, y, z, direction);
            int uvOffset = outputFace * VERTICES_PER_FACE * 2;
            int vertexOffset = outputFace * VERTICES_PER_FACE;

            for (int corner = 0; corner < VERTICES_PER_FACE; corner++) {
                uvs[uvOffset + corner * 2] = materials.u(referenceFace, corner);
                uvs[uvOffset + corner * 2 + 1] = materials.v(referenceFace, corner);
                int exactColor = lighting.exactArgbColor(referenceFace, corner);
                exactArgb[vertexOffset + corner] = exactColor;
                comparisonRgba[vertexOffset + corner] = comparisonRgba(exactColor);
                lights[vertexOffset + corner] = lighting.packedLight(referenceFace, corner);
            }

            int baseVertex = outputFace * VERTICES_PER_FACE;
            int indexOffset = outputFace * INDICES_PER_FACE;
            indices[indexOffset] = baseVertex;
            indices[indexOffset + 1] = baseVertex + 1;
            indices[indexOffset + 2] = baseVertex + 2;
            indices[indexOffset + 3] = baseVertex;
            indices[indexOffset + 4] = baseVertex + 2;
            indices[indexOffset + 5] = baseVertex + 3;
            sourceFaces[outputFace] = referenceFace;

            hash = hashInt(hash, packed);
            hash = hashInt(hash, reference.stateId(referenceFace));
            hash = hashInt(hash, referenceFace);
            hash = hashInt(hash, materials.materialId(referenceFace));
            hash = hashInt(hash, lighting.mode(referenceFace));
            for (int i = 0; i < VERTICES_PER_FACE * 3; i++) {
                hash = hashInt(hash, Float.floatToRawIntBits(positions[positionOffset + i]));
            }
            for (int corner = 0; corner < VERTICES_PER_FACE; corner++) {
                hash = hashInt(hash, Float.floatToRawIntBits(uvs[uvOffset + corner * 2]));
                hash = hashInt(hash, Float.floatToRawIntBits(uvs[uvOffset + corner * 2 + 1]));
                hash = hashInt(hash, exactArgb[vertexOffset + corner]);
                hash = hashInt(hash, lights[vertexOffset + corner]);
            }
            outputFace++;
        }

        if (outputFace != emittedFaces) {
            throw new IllegalStateException("P2.4 emitted-face accounting mismatch");
        }
        hash = hashLong(hash, reference.fingerprint());
        hash = hashLong(hash, materials.fingerprint());
        hash = hashLong(hash, lighting.fingerprint());
        hash = hashInt(hash, emittedFaces);

        LitSectionMesh mesh = new LitSectionMesh(
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(),
                positions, uvs, comparisonRgba, exactArgb, lights, indices, sourceFaces,
                emittedFaces, reference.faceCount(),
                reference.fingerprint(), materials.fingerprint(), lighting.fingerprint(),
                hash, System.nanoTime() - startNs);
        mesh.validateAgainst(snapshot, reference, materials, lighting);
        return mesh;
    }

    public void validateAgainst(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionMaterialSnapshot materials,
            SectionLightingSnapshot lighting) {
        if (sectionX != snapshot.sectionX() || sectionY != snapshot.sectionY() || sectionZ != snapshot.sectionZ()) {
            throw new IllegalStateException("P2.4 lit mesh section mismatch");
        }
        if (referenceFingerprint != reference.fingerprint() || referenceFaceCount != reference.faceCount()) {
            throw new IllegalStateException("P2.4 lit mesh reference mismatch");
        }
        if (materialFingerprint != materials.fingerprint() || lightingFingerprint != lighting.fingerprint()) {
            throw new IllegalStateException("P2.4 lit mesh material/lighting identity mismatch");
        }
        if (faceCount != materials.supportedFaces() || faceCount != lighting.supportedFaces()) {
            throw new IllegalStateException("P2.4 lit mesh supported-face mismatch");
        }
        if (positions.length != vertexCount() * 3
                || uvs.length != vertexCount() * 2
                || comparisonRgba.length != vertexCount()
                || exactArgb.length != vertexCount()
                || packedLights.length != vertexCount()
                || indices.length != indexCount()
                || sourceReferenceFaces.length != faceCount) {
            throw new IllegalStateException("P2.4 lit mesh array accounting mismatch");
        }

        for (int face = 0; face < faceCount; face++) {
            int referenceFace = sourceReferenceFaces[face];
            int vertexOffset = face * VERTICES_PER_FACE;
            int uvOffset = face * VERTICES_PER_FACE * 2;
            for (int corner = 0; corner < VERTICES_PER_FACE; corner++) {
                if (Float.floatToRawIntBits(uvs[uvOffset + corner * 2])
                                != Float.floatToRawIntBits(materials.u(referenceFace, corner))
                        || Float.floatToRawIntBits(uvs[uvOffset + corner * 2 + 1])
                                != Float.floatToRawIntBits(materials.v(referenceFace, corner))) {
                    throw new IllegalStateException("P2.4 lit mesh UV mismatch");
                }
                int exactColor = lighting.exactArgbColor(referenceFace, corner);
                if (exactArgb[vertexOffset + corner] != exactColor
                        || comparisonRgba[vertexOffset + corner] != comparisonRgba(exactColor)
                        || packedLights[vertexOffset + corner] != lighting.packedLight(referenceFace, corner)) {
                    throw new IllegalStateException("P2.4 lit mesh light/color mismatch");
                }
            }
            int base = face * VERTICES_PER_FACE;
            int indexOffset = face * INDICES_PER_FACE;
            if (indices[indexOffset] != base
                    || indices[indexOffset + 1] != base + 1
                    || indices[indexOffset + 2] != base + 2
                    || indices[indexOffset + 3] != base
                    || indices[indexOffset + 4] != base + 2
                    || indices[indexOffset + 5] != base + 3) {
                throw new IllegalStateException("P2.4 lit mesh canonical index mismatch");
            }
        }
    }

    public boolean contentEquals(LitSectionMesh other) {
        return other != null
                && sectionX == other.sectionX && sectionY == other.sectionY && sectionZ == other.sectionZ
                && faceCount == other.faceCount && referenceFaceCount == other.referenceFaceCount
                && referenceFingerprint == other.referenceFingerprint
                && materialFingerprint == other.materialFingerprint
                && lightingFingerprint == other.lightingFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(positions, other.positions)
                && Arrays.equals(uvs, other.uvs)
                && Arrays.equals(comparisonRgba, other.comparisonRgba)
                && Arrays.equals(exactArgb, other.exactArgb)
                && Arrays.equals(packedLights, other.packedLights)
                && Arrays.equals(indices, other.indices)
                && Arrays.equals(sourceReferenceFaces, other.sourceReferenceFaces);
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

    /** Convert exact ARGB to BLOCK's RGBA8 byte order while applying only uniform comparison RGB modulation. */
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

    private static void writeFacePositions(float[] out, int offset, int x, int y, int z, int direction) {
        float x0 = x, y0 = y, z0 = z;
        float x1 = x + 1.0f, y1 = y + 1.0f, z1 = z + 1.0f;
        switch (direction) {
            case 0 -> { put(out, offset, x0,y0,z0); put(out, offset+3, x0,y0,z1); put(out, offset+6, x0,y1,z1); put(out, offset+9, x0,y1,z0); }
            case 1 -> { put(out, offset, x1,y0,z1); put(out, offset+3, x1,y0,z0); put(out, offset+6, x1,y1,z0); put(out, offset+9, x1,y1,z1); }
            case 2 -> { put(out, offset, x0,y0,z1); put(out, offset+3, x0,y0,z0); put(out, offset+6, x1,y0,z0); put(out, offset+9, x1,y0,z1); }
            case 3 -> { put(out, offset, x0,y1,z0); put(out, offset+3, x0,y1,z1); put(out, offset+6, x1,y1,z1); put(out, offset+9, x1,y1,z0); }
            case 4 -> { put(out, offset, x1,y0,z0); put(out, offset+3, x0,y0,z0); put(out, offset+6, x0,y1,z0); put(out, offset+9, x1,y1,z0); }
            case 5 -> { put(out, offset, x0,y0,z1); put(out, offset+3, x1,y0,z1); put(out, offset+6, x1,y1,z1); put(out, offset+9, x0,y1,z1); }
            default -> throw new IllegalStateException("Invalid P2.4 face direction: " + direction);
        }
    }

    private static void put(float[] out, int offset, float x, float y, float z) {
        out[offset] = x; out[offset + 1] = y; out[offset + 2] = z;
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
    public int faceCount() { return faceCount; }
    public int referenceFaceCount() { return referenceFaceCount; }
    public int rejectedReferenceFaces() { return referenceFaceCount - faceCount; }
    public int vertexCount() { return faceCount * VERTICES_PER_FACE; }
    public int indexCount() { return faceCount * INDICES_PER_FACE; }
    public int vertexBytes() { return vertexCount() * BYTES_PER_VERTEX; }
    public int indexBytes() { return indexCount() * BYTES_PER_INDEX; }
    public long referenceFingerprint() { return referenceFingerprint; }
    public long materialFingerprint() { return materialFingerprint; }
    public long lightingFingerprint() { return lightingFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }
}