package dev.obsidian.render.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Deliberately simple, deterministic one-face-per-exposed-face oracle.
 *
 * <p>This class has no Minecraft world dependency. It consumes only the
 * primitive-only {@link SectionSnapshot}. Faces are emitted only for the
 * conservative supported-full-cube subset and only when the neighboring
 * snapshot cell is definitely air. Unsupported neighbors suppress emission and
 * are counted rather than approximated.</p>
 *
 * <p>Each canonical face is two ints / eight bytes: packed local position and
 * direction, followed by the original Minecraft BlockState ID. Phase 3 greedy
 * meshing can later differential-test coverage/material identity against this
 * stream without sharing the optimized algorithm.</p>
 */
public final class ReferenceFaceMesh {
    public static final int BYTES_PER_FACE = Integer.BYTES * 2;
    public static final int MAX_FACES = SectionSnapshot.INTERIOR_CELL_COUNT * 6;
    public static final int MAX_BYTES = MAX_FACES * BYTES_PER_FACE;

    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, -1, 1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, -1, 1};

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final int[] packedFaces;
    private final int[] stateIds;
    private final int faceCount;
    private final int blockedByUnsupportedFaces;
    private final long fingerprint;
    private final long meshTimeNs;

    private ReferenceFaceMesh(
            int[] packedFaces,
            int[] stateIds,
            int faceCount,
            int blockedByUnsupportedFaces,
            long fingerprint,
            long meshTimeNs) {
        this.packedFaces = packedFaces;
        this.stateIds = stateIds;
        this.faceCount = faceCount;
        this.blockedByUnsupportedFaces = blockedByUnsupportedFaces;
        this.fingerprint = fingerprint;
        this.meshTimeNs = meshTimeNs;
    }

    public static ReferenceFaceMesh build(SectionSnapshot snapshot) {
        long startNs = System.nanoTime();
        int[] faces = new int[MAX_FACES];
        int[] ids = new int[MAX_FACES];
        int count = 0;
        int blockedUnsupported = 0;
        long hash = FNV_OFFSET_BASIS;

        for (int y = 0; y < SectionSnapshot.INTERIOR_SIZE; y++) {
            for (int z = 0; z < SectionSnapshot.INTERIOR_SIZE; z++) {
                for (int x = 0; x < SectionSnapshot.INTERIOR_SIZE; x++) {
                    if (snapshot.classification(x, y, z) != SectionSnapshot.SUPPORTED_FULL_CUBE) {
                        continue;
                    }

                    int stateId = snapshot.stateId(x, y, z);
                    for (int direction = 0; direction < 6; direction++) {
                        byte neighbor = snapshot.classification(
                                x + DX[direction],
                                y + DY[direction],
                                z + DZ[direction]);
                        if (neighbor == SectionSnapshot.UNSUPPORTED) {
                            blockedUnsupported++;
                            continue;
                        }
                        if (neighbor != SectionSnapshot.AIR) {
                            continue;
                        }

                        int packed = packFace(x, y, z, direction);
                        faces[count] = packed;
                        ids[count] = stateId;
                        count++;

                        hash ^= Integer.toUnsignedLong(packed);
                        hash *= FNV_PRIME;
                        hash ^= Integer.toUnsignedLong(stateId);
                        hash *= FNV_PRIME;
                    }
                }
            }
        }

        return new ReferenceFaceMesh(
                Arrays.copyOf(faces, count),
                Arrays.copyOf(ids, count),
                count,
                blockedUnsupported,
                hash,
                System.nanoTime() - startNs);
    }

    /** Re-validates every emitted record against the immutable snapshot. */
    public void validateAgainst(SectionSnapshot snapshot) {
        for (int i = 0; i < faceCount; i++) {
            int packed = packedFaces[i];
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            if (direction >= 6) {
                throw new IllegalStateException("Invalid canonical face direction: " + direction);
            }
            if (snapshot.classification(x, y, z) != SectionSnapshot.SUPPORTED_FULL_CUBE) {
                throw new IllegalStateException("Reference face originated from unsupported cell");
            }
            if (snapshot.stateId(x, y, z) != stateIds[i]) {
                throw new IllegalStateException("Reference face state ID does not match snapshot");
            }
            if (snapshot.classification(
                    x + DX[direction],
                    y + DY[direction],
                    z + DZ[direction]) != SectionSnapshot.AIR) {
                throw new IllegalStateException("Reference face neighbor is not air");
            }
        }
    }

    public boolean contentEquals(ReferenceFaceMesh other) {
        return other != null
                && faceCount == other.faceCount
                && blockedByUnsupportedFaces == other.blockedByUnsupportedFaces
                && fingerprint == other.fingerprint
                && Arrays.equals(packedFaces, other.packedFaces)
                && Arrays.equals(stateIds, other.stateIds);
    }

    public ByteBuffer toByteBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(byteSize()).order(ByteOrder.nativeOrder());
        for (int i = 0; i < faceCount; i++) {
            out.putInt(packedFaces[i]);
            out.putInt(stateIds[i]);
        }
        return out.flip();
    }

    public int faceCount() {
        return faceCount;
    }

    public int quadCount() {
        return faceCount;
    }

    public int vertexCount() {
        return faceCount * 4;
    }

    public int indexCount() {
        return faceCount * 6;
    }

    public int byteSize() {
        return faceCount * BYTES_PER_FACE;
    }

    public int blockedByUnsupportedFaces() {
        return blockedByUnsupportedFaces;
    }

    public long fingerprint() {
        return fingerprint;
    }

    public long meshTimeNs() {
        return meshTimeNs;
    }

    public int packedFace(int index) {
        return packedFaces[index];
    }

    public int stateId(int index) {
        return stateIds[index];
    }

    private static int packFace(int x, int y, int z, int direction) {
        return x | (y << 4) | (z << 8) | (direction << 12);
    }
}
