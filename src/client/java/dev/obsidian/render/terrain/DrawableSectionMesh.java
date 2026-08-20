package dev.obsidian.render.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Deterministic Phase 2 drawable geometry derived only from the permanent
 * {@link ReferenceFaceMesh} oracle and immutable {@link SectionSnapshot}.
 *
 * <p>The dev2 comparison vertex format is deliberately narrow and diagnostic:
 * section-local {@code float3 Position} plus one RGBA8 orientation color. The
 * color exists only to make transform/face-orientation errors obvious during
 * the P2.2 runtime test; it is not Minecraft material identity. Texture/UV,
 * tint, render-layer and lighting/AO semantics remain P2.3/P2.4 work.</p>
 *
 * <p>The captured section origin is retained separately so the same local mesh
 * can be placed in real world space without baking large world coordinates into
 * every vertex.</p>
 */
public final class DrawableSectionMesh {
    public static final int FLOATS_PER_VERTEX = 3;
    public static final int POSITION_BYTES = FLOATS_PER_VERTEX * Float.BYTES;
    public static final int COLOR_BYTES = Integer.BYTES;
    public static final int BYTES_PER_VERTEX = POSITION_BYTES + COLOR_BYTES;
    public static final int VERTICES_PER_FACE = 4;
    public static final int INDICES_PER_FACE = 6;
    public static final int BYTES_PER_INDEX = Integer.BYTES;

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
    private final int[] faceColors;
    private final int[] indices;
    private final int[] faceStateIds;
    private final int faceCount;
    private final long referenceFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private DrawableSectionMesh(
            int sectionX,
            int sectionY,
            int sectionZ,
            float[] positions,
            int[] faceColors,
            int[] indices,
            int[] faceStateIds,
            int faceCount,
            long referenceFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.originX = sectionX * SectionSnapshot.INTERIOR_SIZE;
        this.originY = sectionY * SectionSnapshot.INTERIOR_SIZE;
        this.originZ = sectionZ * SectionSnapshot.INTERIOR_SIZE;
        this.positions = positions;
        this.faceColors = faceColors;
        this.indices = indices;
        this.faceStateIds = faceStateIds;
        this.faceCount = faceCount;
        this.referenceFingerprint = referenceFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static DrawableSectionMesh build(SectionSnapshot snapshot, ReferenceFaceMesh reference) {
        if (snapshot == null || reference == null) {
            throw new NullPointerException("snapshot and reference are required");
        }
        reference.validateAgainst(snapshot);

        long startNs = System.nanoTime();
        int faceCount = reference.faceCount();
        float[] positions = new float[faceCount * VERTICES_PER_FACE * FLOATS_PER_VERTEX];
        int[] colors = new int[faceCount];
        int[] indices = new int[faceCount * INDICES_PER_FACE];
        int[] stateIds = new int[faceCount];
        long hash = FNV_OFFSET_BASIS;

        for (int face = 0; face < faceCount; face++) {
            int packed = reference.packedFace(face);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            if (direction >= 6) {
                throw new IllegalStateException("Invalid reference face direction: " + direction);
            }

            int baseVertex = face * VERTICES_PER_FACE;
            int positionOffset = baseVertex * FLOATS_PER_VERTEX;
            writeFacePositions(positions, positionOffset, x, y, z, direction);
            colors[face] = colorForDirection(direction);

            int indexOffset = face * INDICES_PER_FACE;
            indices[indexOffset] = baseVertex;
            indices[indexOffset + 1] = baseVertex + 1;
            indices[indexOffset + 2] = baseVertex + 2;
            indices[indexOffset + 3] = baseVertex;
            indices[indexOffset + 4] = baseVertex + 2;
            indices[indexOffset + 5] = baseVertex + 3;

            int stateId = reference.stateId(face);
            stateIds[face] = stateId;

            hash = hashInt(hash, packed);
            hash = hashInt(hash, stateId);
            hash = hashInt(hash, colors[face]);
            for (int i = 0; i < VERTICES_PER_FACE * FLOATS_PER_VERTEX; i++) {
                hash = hashInt(hash, Float.floatToRawIntBits(positions[positionOffset + i]));
            }
            for (int i = 0; i < INDICES_PER_FACE; i++) {
                hash = hashInt(hash, indices[indexOffset + i]);
            }
        }

        hash = hashInt(hash, snapshot.sectionX());
        hash = hashInt(hash, snapshot.sectionY());
        hash = hashInt(hash, snapshot.sectionZ());

        DrawableSectionMesh mesh = new DrawableSectionMesh(
                snapshot.sectionX(),
                snapshot.sectionY(),
                snapshot.sectionZ(),
                positions,
                colors,
                indices,
                stateIds,
                faceCount,
                reference.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        mesh.validateAgainst(snapshot, reference);
        return mesh;
    }

    /** Re-validates coverage, state identity, indices, colors and exact face corners. */
    public void validateAgainst(SectionSnapshot snapshot, ReferenceFaceMesh reference) {
        if (snapshot.sectionX() != sectionX
                || snapshot.sectionY() != sectionY
                || snapshot.sectionZ() != sectionZ) {
            throw new IllegalStateException("Drawable section coordinates do not match snapshot");
        }
        if (reference.faceCount() != faceCount || reference.fingerprint() != referenceFingerprint) {
            throw new IllegalStateException("Drawable mesh does not match its reference oracle");
        }
        if (positions.length != vertexCount() * FLOATS_PER_VERTEX
                || faceColors.length != faceCount
                || indices.length != indexCount()
                || faceStateIds.length != faceCount) {
            throw new IllegalStateException("Drawable mesh array accounting is inconsistent");
        }

        float[] expected = new float[VERTICES_PER_FACE * FLOATS_PER_VERTEX];
        for (int face = 0; face < faceCount; face++) {
            int packed = reference.packedFace(face);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            if (snapshot.classification(x, y, z) != SectionSnapshot.SUPPORTED_FULL_CUBE) {
                throw new IllegalStateException("Drawable face originated from unsupported snapshot cell");
            }
            if (faceStateIds[face] != reference.stateId(face)
                    || faceStateIds[face] != snapshot.stateId(x, y, z)) {
                throw new IllegalStateException("Drawable face state identity mismatch");
            }
            if (faceColors[face] != colorForDirection(direction)) {
                throw new IllegalStateException("Drawable diagnostic face color mismatch");
            }

            Arrays.fill(expected, 0.0f);
            writeFacePositions(expected, 0, x, y, z, direction);
            int positionOffset = face * VERTICES_PER_FACE * FLOATS_PER_VERTEX;
            for (int i = 0; i < expected.length; i++) {
                if (Float.floatToRawIntBits(positions[positionOffset + i])
                        != Float.floatToRawIntBits(expected[i])) {
                    throw new IllegalStateException("Drawable face position mismatch at face " + face);
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
                throw new IllegalStateException("Drawable index mismatch at face " + face);
            }
        }
    }

    public boolean contentEquals(DrawableSectionMesh other) {
        return other != null
                && sectionX == other.sectionX
                && sectionY == other.sectionY
                && sectionZ == other.sectionZ
                && faceCount == other.faceCount
                && referenceFingerprint == other.referenceFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(positions, other.positions)
                && Arrays.equals(faceColors, other.faceColors)
                && Arrays.equals(indices, other.indices)
                && Arrays.equals(faceStateIds, other.faceStateIds);
    }

    public ByteBuffer vertexBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(vertexBytes()).order(ByteOrder.nativeOrder());
        for (int face = 0; face < faceCount; face++) {
            int color = faceColors[face];
            int positionOffset = face * VERTICES_PER_FACE * FLOATS_PER_VERTEX;
            for (int vertex = 0; vertex < VERTICES_PER_FACE; vertex++) {
                int p = positionOffset + vertex * FLOATS_PER_VERTEX;
                out.putFloat(positions[p]);
                out.putFloat(positions[p + 1]);
                out.putFloat(positions[p + 2]);
                out.put((byte) color);
                out.put((byte) (color >>> 8));
                out.put((byte) (color >>> 16));
                out.put((byte) (color >>> 24));
            }
        }
        return out.flip();
    }

    public ByteBuffer indexBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(indexBytes()).order(ByteOrder.nativeOrder());
        for (int value : indices) {
            out.putInt(value);
        }
        return out.flip();
    }

    public int sectionX() {
        return sectionX;
    }

    public int sectionY() {
        return sectionY;
    }

    public int sectionZ() {
        return sectionZ;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public int faceCount() {
        return faceCount;
    }

    public int quadCount() {
        return faceCount;
    }

    public int vertexCount() {
        return faceCount * VERTICES_PER_FACE;
    }

    public int indexCount() {
        return faceCount * INDICES_PER_FACE;
    }

    public int vertexBytes() {
        return vertexCount() * BYTES_PER_VERTEX;
    }

    public int indexBytes() {
        return indexCount() * BYTES_PER_INDEX;
    }

    public int uploadBytes() {
        return vertexBytes() + indexBytes();
    }

    public long referenceFingerprint() {
        return referenceFingerprint;
    }

    public long fingerprint() {
        return fingerprint;
    }

    public long buildTimeNs() {
        return buildTimeNs;
    }

    private static long hashInt(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value);
        return hash * FNV_PRIME;
    }

    /**
     * Writes four CCW corners when viewed from outside the cube. Indices
     * {@code 0,1,2,0,2,3} therefore produce outward-facing triangles for all
     * six canonical directions.
     */
    private static void writeFacePositions(
            float[] out,
            int offset,
            int x,
            int y,
            int z,
            int direction) {
        float x0 = x;
        float y0 = y;
        float z0 = z;
        float x1 = x + 1.0f;
        float y1 = y + 1.0f;
        float z1 = z + 1.0f;

        switch (direction) {
            case 0 -> { // -X = red
                put(out, offset, x0, y0, z0);
                put(out, offset + 3, x0, y0, z1);
                put(out, offset + 6, x0, y1, z1);
                put(out, offset + 9, x0, y1, z0);
            }
            case 1 -> { // +X = cyan
                put(out, offset, x1, y0, z1);
                put(out, offset + 3, x1, y0, z0);
                put(out, offset + 6, x1, y1, z0);
                put(out, offset + 9, x1, y1, z1);
            }
            case 2 -> { // -Y = green
                put(out, offset, x0, y0, z1);
                put(out, offset + 3, x0, y0, z0);
                put(out, offset + 6, x1, y0, z0);
                put(out, offset + 9, x1, y0, z1);
            }
            case 3 -> { // +Y = magenta
                put(out, offset, x0, y1, z0);
                put(out, offset + 3, x0, y1, z1);
                put(out, offset + 6, x1, y1, z1);
                put(out, offset + 9, x1, y1, z0);
            }
            case 4 -> { // -Z = blue
                put(out, offset, x1, y0, z0);
                put(out, offset + 3, x0, y0, z0);
                put(out, offset + 6, x0, y1, z0);
                put(out, offset + 9, x1, y1, z0);
            }
            case 5 -> { // +Z = yellow
                put(out, offset, x0, y0, z1);
                put(out, offset + 3, x1, y0, z1);
                put(out, offset + 6, x1, y1, z1);
                put(out, offset + 9, x0, y1, z1);
            }
            default -> throw new IllegalArgumentException("Unknown face direction: " + direction);
        }
    }

    /** RGBA8 little-byte order as consumed by DefaultVertexFormat.POSITION_COLOR. */
    private static int colorForDirection(int direction) {
        return switch (direction) {
            case 0 -> rgba(255, 64, 64, 192);
            case 1 -> rgba(64, 255, 255, 192);
            case 2 -> rgba(64, 255, 64, 192);
            case 3 -> rgba(255, 64, 255, 192);
            case 4 -> rgba(64, 64, 255, 192);
            case 5 -> rgba(255, 255, 64, 192);
            default -> throw new IllegalArgumentException("Unknown face direction: " + direction);
        };
    }

    private static int rgba(int r, int g, int b, int a) {
        return (r & 0xFF)
                | ((g & 0xFF) << 8)
                | ((b & 0xFF) << 16)
                | ((a & 0xFF) << 24);
    }

    private static void put(float[] out, int offset, float x, float y, float z) {
        out[offset] = x;
        out[offset + 1] = y;
        out[offset + 2] = z;
    }
}
