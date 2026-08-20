package dev.obsidian.render.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Pure P2.3 mesh built only from immutable Obsidian-owned snapshots.
 *
 * <p>Vertex format: {@code POSITION_TEX_COLOR} = float3 position, float2 UV,
 * RGBA8 comparison color. The stored material/tint identity is exact; the
 * emitted RGB is deliberately multiplied by 3/4 so the otherwise identical
 * texture overlay is visibly distinguishable from vanilla during the human
 * validation window. Lighting/AO are intentionally absent until P2.4.</p>
 */
public final class MaterializedSectionMesh {
    public static final int FLOATS_PER_POSITION = 3;
    public static final int FLOATS_PER_UV = 2;
    public static final int POSITION_BYTES = FLOATS_PER_POSITION * Float.BYTES;
    public static final int UV_BYTES = FLOATS_PER_UV * Float.BYTES;
    public static final int COLOR_BYTES = Integer.BYTES;
    public static final int BYTES_PER_VERTEX = POSITION_BYTES + UV_BYTES + COLOR_BYTES;
    public static final int VERTICES_PER_FACE = 4;
    public static final int INDICES_PER_FACE = 6;
    public static final int BYTES_PER_INDEX = Integer.BYTES;
    public static final int COMPARISON_COLOR_NUMERATOR = 3;
    public static final int COMPARISON_COLOR_DENOMINATOR = 4;

    public static final int MAX_VERTEX_BYTES =
            ReferenceFaceMesh.MAX_FACES * VERTICES_PER_FACE * BYTES_PER_VERTEX;
    public static final int MAX_INDEX_BYTES =
            ReferenceFaceMesh.MAX_FACES * INDICES_PER_FACE * BYTES_PER_INDEX;
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
    private final int[] colors;
    private final int[] indices;
    private final int[] sourceReferenceFaces;
    private final int[] materialIds;
    private final int[] exactTintColors;
    private final int faceCount;
    private final int referenceFaceCount;
    private final int materialCount;
    private final long referenceFingerprint;
    private final long materialFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private MaterializedSectionMesh(
            int sectionX,
            int sectionY,
            int sectionZ,
            float[] positions,
            float[] uvs,
            int[] colors,
            int[] indices,
            int[] sourceReferenceFaces,
            int[] materialIds,
            int[] exactTintColors,
            int faceCount,
            int referenceFaceCount,
            int materialCount,
            long referenceFingerprint,
            long materialFingerprint,
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
        this.colors = colors;
        this.indices = indices;
        this.sourceReferenceFaces = sourceReferenceFaces;
        this.materialIds = materialIds;
        this.exactTintColors = exactTintColors;
        this.faceCount = faceCount;
        this.referenceFaceCount = referenceFaceCount;
        this.materialCount = materialCount;
        this.referenceFingerprint = referenceFingerprint;
        this.materialFingerprint = materialFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static MaterializedSectionMesh build(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionMaterialSnapshot materials) {
        if (snapshot == null || reference == null || materials == null) {
            throw new NullPointerException("snapshot, reference and materials are required");
        }
        reference.validateAgainst(snapshot);
        if (materials.sectionX() != snapshot.sectionX()
                || materials.sectionY() != snapshot.sectionY()
                || materials.sectionZ() != snapshot.sectionZ()
                || materials.referenceFingerprint() != reference.fingerprint()
                || materials.referenceFaceCount() != reference.faceCount()) {
            throw new IllegalStateException("P2.3 material snapshot does not match reference geometry");
        }

        long startNs = System.nanoTime();
        int emittedFaces = materials.supportedFaces();
        float[] positions = new float[emittedFaces * VERTICES_PER_FACE * FLOATS_PER_POSITION];
        float[] uvs = new float[emittedFaces * VERTICES_PER_FACE * FLOATS_PER_UV];
        int[] colors = new int[emittedFaces * VERTICES_PER_FACE];
        int[] indices = new int[emittedFaces * INDICES_PER_FACE];
        int[] sourceFaces = new int[emittedFaces];
        int[] materialIds = new int[emittedFaces];
        int[] tintColors = new int[emittedFaces];
        long hash = FNV_OFFSET_BASIS;
        int outputFace = 0;

        for (int referenceFace = 0; referenceFace < reference.faceCount(); referenceFace++) {
            int materialId = materials.materialId(referenceFace);
            if (materialId == SectionMaterialSnapshot.UNSUPPORTED_MATERIAL) {
                continue;
            }
            if (outputFace >= emittedFaces) {
                throw new IllegalStateException("P2.3 material supported-face accounting overflow");
            }

            int packed = reference.packedFace(referenceFace);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;

            int positionOffset = outputFace * VERTICES_PER_FACE * FLOATS_PER_POSITION;
            writeFacePositions(positions, positionOffset, x, y, z, direction);
            int uvOffset = outputFace * VERTICES_PER_FACE * FLOATS_PER_UV;
            int colorOffset = outputFace * VERTICES_PER_FACE;
            int tintColor = materials.tintColor(referenceFace);
            int comparisonColor = comparisonColor(tintColor);

            for (int corner = 0; corner < VERTICES_PER_FACE; corner++) {
                uvs[uvOffset + corner * 2] = materials.u(referenceFace, corner);
                uvs[uvOffset + corner * 2 + 1] = materials.v(referenceFace, corner);
                colors[colorOffset + corner] = comparisonColor;
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
            materialIds[outputFace] = materialId;
            tintColors[outputFace] = tintColor;

            hash = hashInt(hash, packed);
            hash = hashInt(hash, reference.stateId(referenceFace));
            hash = hashInt(hash, referenceFace);
            hash = hashInt(hash, materialId);
            hash = hashInt(hash, tintColor);
            hash = hashInt(hash, comparisonColor);
            for (int i = 0; i < VERTICES_PER_FACE * FLOATS_PER_POSITION; i++) {
                hash = hashInt(hash, Float.floatToRawIntBits(positions[positionOffset + i]));
            }
            for (int i = 0; i < VERTICES_PER_FACE * FLOATS_PER_UV; i++) {
                hash = hashInt(hash, Float.floatToRawIntBits(uvs[uvOffset + i]));
            }
            outputFace++;
        }

        if (outputFace != emittedFaces) {
            throw new IllegalStateException("P2.3 material supported-face accounting mismatch");
        }
        hash = hashLong(hash, reference.fingerprint());
        hash = hashLong(hash, materials.fingerprint());
        hash = hashInt(hash, materials.materialCount());
        hash = hashInt(hash, emittedFaces);

        MaterializedSectionMesh mesh = new MaterializedSectionMesh(
                snapshot.sectionX(),
                snapshot.sectionY(),
                snapshot.sectionZ(),
                positions,
                uvs,
                colors,
                indices,
                sourceFaces,
                materialIds,
                tintColors,
                emittedFaces,
                reference.faceCount(),
                materials.materialCount(),
                reference.fingerprint(),
                materials.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        mesh.validateAgainst(snapshot, reference, materials);
        return mesh;
    }

    public void validateAgainst(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionMaterialSnapshot materials) {
        if (snapshot.sectionX() != sectionX || snapshot.sectionY() != sectionY || snapshot.sectionZ() != sectionZ) {
            throw new IllegalStateException("P2.3 mesh section coordinates do not match snapshot");
        }
        if (reference.fingerprint() != referenceFingerprint || reference.faceCount() != referenceFaceCount) {
            throw new IllegalStateException("P2.3 mesh reference identity mismatch");
        }
        if (materials.fingerprint() != materialFingerprint
                || materials.materialCount() != materialCount
                || materials.supportedFaces() != faceCount) {
            throw new IllegalStateException("P2.3 mesh material identity mismatch");
        }
        if (positions.length != vertexCount() * FLOATS_PER_POSITION
                || uvs.length != vertexCount() * FLOATS_PER_UV
                || colors.length != vertexCount()
                || indices.length != indexCount()
                || sourceReferenceFaces.length != faceCount
                || materialIds.length != faceCount
                || exactTintColors.length != faceCount) {
            throw new IllegalStateException("P2.3 mesh array accounting mismatch");
        }

        float[] expected = new float[VERTICES_PER_FACE * FLOATS_PER_POSITION];
        for (int face = 0; face < faceCount; face++) {
            int referenceFace = sourceReferenceFaces[face];
            int packed = reference.packedFace(referenceFace);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            if (materials.materialId(referenceFace) != materialIds[face]) {
                throw new IllegalStateException("P2.3 mesh material ID mismatch");
            }
            if (materials.tintColor(referenceFace) != exactTintColors[face]) {
                throw new IllegalStateException("P2.3 mesh tint identity mismatch");
            }

            writeFacePositions(expected, 0, x, y, z, direction);
            int positionOffset = face * VERTICES_PER_FACE * FLOATS_PER_POSITION;
            int uvOffset = face * VERTICES_PER_FACE * FLOATS_PER_UV;
            int colorOffset = face * VERTICES_PER_FACE;
            for (int i = 0; i < expected.length; i++) {
                if (Float.floatToRawIntBits(expected[i]) != Float.floatToRawIntBits(positions[positionOffset + i])) {
                    throw new IllegalStateException("P2.3 mesh position mismatch at emitted face " + face);
                }
            }
            for (int corner = 0; corner < VERTICES_PER_FACE; corner++) {
                if (Float.floatToRawIntBits(uvs[uvOffset + corner * 2])
                                != Float.floatToRawIntBits(materials.u(referenceFace, corner))
                        || Float.floatToRawIntBits(uvs[uvOffset + corner * 2 + 1])
                                != Float.floatToRawIntBits(materials.v(referenceFace, corner))) {
                    throw new IllegalStateException("P2.3 mesh UV mismatch at emitted face " + face);
                }
                if (colors[colorOffset + corner] != comparisonColor(exactTintColors[face])) {
                    throw new IllegalStateException("P2.3 mesh comparison color mismatch at emitted face " + face);
                }
            }

            int baseVertex = face * VERTICES_PER_FACE;
            int indexOffset = face * INDICES_PER_FACE;
            if (indices[indexOffset] != baseVertex
                    || indices[indexOffset + 1] != baseVertex + 1
                    || indices[indexOffset + 2] != baseVertex + 2
                    || indices[indexOffset + 3] != baseVertex
                    || indices[indexOffset + 4] != baseVertex + 2
                    || indices[indexOffset + 5] != baseVertex + 3) {
                throw new IllegalStateException("P2.3 mesh index mismatch at emitted face " + face);
            }
        }
    }

    public boolean contentEquals(MaterializedSectionMesh other) {
        return other != null
                && sectionX == other.sectionX
                && sectionY == other.sectionY
                && sectionZ == other.sectionZ
                && faceCount == other.faceCount
                && referenceFaceCount == other.referenceFaceCount
                && materialCount == other.materialCount
                && referenceFingerprint == other.referenceFingerprint
                && materialFingerprint == other.materialFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(positions, other.positions)
                && Arrays.equals(uvs, other.uvs)
                && Arrays.equals(colors, other.colors)
                && Arrays.equals(indices, other.indices)
                && Arrays.equals(sourceReferenceFaces, other.sourceReferenceFaces)
                && Arrays.equals(materialIds, other.materialIds)
                && Arrays.equals(exactTintColors, other.exactTintColors);
    }

    public ByteBuffer vertexBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(vertexBytes()).order(ByteOrder.nativeOrder());
        for (int vertex = 0; vertex < vertexCount(); vertex++) {
            int positionOffset = vertex * FLOATS_PER_POSITION;
            int uvOffset = vertex * FLOATS_PER_UV;
            int color = colors[vertex];
            out.putFloat(positions[positionOffset]);
            out.putFloat(positions[positionOffset + 1]);
            out.putFloat(positions[positionOffset + 2]);
            out.putFloat(uvs[uvOffset]);
            out.putFloat(uvs[uvOffset + 1]);
            out.put((byte) color);
            out.put((byte) (color >>> 8));
            out.put((byte) (color >>> 16));
            out.put((byte) (color >>> 24));
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

    /** RGBA8 little-byte order for DefaultVertexFormat.POSITION_TEX_COLOR. */
    private static int comparisonColor(int argbTint) {
        int red = (argbTint >>> 16) & 0xFF;
        int green = (argbTint >>> 8) & 0xFF;
        int blue = argbTint & 0xFF;
        red = red * COMPARISON_COLOR_NUMERATOR / COMPARISON_COLOR_DENOMINATOR;
        green = green * COMPARISON_COLOR_NUMERATOR / COMPARISON_COLOR_DENOMINATOR;
        blue = blue * COMPARISON_COLOR_NUMERATOR / COMPARISON_COLOR_DENOMINATOR;
        return red | (green << 8) | (blue << 16) | (0xFF << 24);
    }

    private static void writeFacePositions(float[] out, int offset, int x, int y, int z, int direction) {
        float x0 = x;
        float y0 = y;
        float z0 = z;
        float x1 = x + 1.0f;
        float y1 = y + 1.0f;
        float z1 = z + 1.0f;
        switch (direction) {
            case 0 -> {
                put(out, offset, x0, y0, z0);
                put(out, offset + 3, x0, y0, z1);
                put(out, offset + 6, x0, y1, z1);
                put(out, offset + 9, x0, y1, z0);
            }
            case 1 -> {
                put(out, offset, x1, y0, z1);
                put(out, offset + 3, x1, y0, z0);
                put(out, offset + 6, x1, y1, z0);
                put(out, offset + 9, x1, y1, z1);
            }
            case 2 -> {
                put(out, offset, x0, y0, z1);
                put(out, offset + 3, x0, y0, z0);
                put(out, offset + 6, x1, y0, z0);
                put(out, offset + 9, x1, y0, z1);
            }
            case 3 -> {
                put(out, offset, x0, y1, z0);
                put(out, offset + 3, x0, y1, z1);
                put(out, offset + 6, x1, y1, z1);
                put(out, offset + 9, x1, y1, z0);
            }
            case 4 -> {
                put(out, offset, x1, y0, z0);
                put(out, offset + 3, x0, y0, z0);
                put(out, offset + 6, x0, y1, z0);
                put(out, offset + 9, x1, y1, z0);
            }
            case 5 -> {
                put(out, offset, x0, y0, z1);
                put(out, offset + 3, x1, y0, z1);
                put(out, offset + 6, x1, y1, z1);
                put(out, offset + 9, x0, y1, z1);
            }
            default -> throw new IllegalArgumentException("Unknown face direction: " + direction);
        }
    }

    private static void put(float[] out, int offset, float x, float y, float z) {
        out[offset] = x;
        out[offset + 1] = y;
        out[offset + 2] = z;
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
    public int materialCount() { return materialCount; }
    public int vertexCount() { return faceCount * VERTICES_PER_FACE; }
    public int indexCount() { return faceCount * INDICES_PER_FACE; }
    public int vertexBytes() { return vertexCount() * BYTES_PER_VERTEX; }
    public int indexBytes() { return indexCount() * BYTES_PER_INDEX; }
    public int uploadBytes() { return vertexBytes() + indexBytes(); }
    public long referenceFingerprint() { return referenceFingerprint; }
    public long materialFingerprint() { return materialFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }
}