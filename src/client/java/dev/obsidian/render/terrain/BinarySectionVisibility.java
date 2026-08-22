package dev.obsidian.render.terrain;

import java.util.Arrays;

/**
 * Pure P3.2 six-direction visibility bitset for the conservative full-cube
 * subset represented by {@link SectionSnapshot}.
 *
 * <p>The permanent {@link ReferenceFaceMesh} remains the independent oracle.
 * This class deliberately does not emit geometry and does not consume the
 * generalized baked-quad stream. It only records the canonical visibility
 * topology that P3.3 can later use for greedy rectangle extraction.</p>
 *
 * <p>Each direction owns one bit for every interior 16^3 source cell. Bit
 * order is {@code ((y * 16) + z) * 16 + x}. Four consecutive 16-bit X rows
 * share one {@code long}, so one direction is exactly 64 words / 512 bytes and
 * all six directions are 384 words / 3,072 bytes.</p>
 */
public final class BinarySectionVisibility {
    public static final int WEST = 0;
    public static final int EAST = 1;
    public static final int DOWN = 2;
    public static final int UP = 3;
    public static final int NORTH = 4;
    public static final int SOUTH = 5;
    public static final int DIRECTION_COUNT = 6;

    public static final int WORDS_PER_DIRECTION = SectionSnapshot.INTERIOR_CELL_COUNT / Long.SIZE;
    public static final int TOTAL_WORDS = WORDS_PER_DIRECTION * DIRECTION_COUNT;
    public static final int RETAINED_BYTES = TOTAL_WORDS * Long.BYTES;

    private static final int ROWS = SectionSnapshot.INTERIOR_SIZE * SectionSnapshot.INTERIOR_SIZE;
    private static final int ROWS_PER_WORD = Long.SIZE / SectionSnapshot.INTERIOR_SIZE;
    private static final long LOCAL_ROW_MASK = (1L << SectionSnapshot.INTERIOR_SIZE) - 1L;
    private static final long STORAGE_ROW_MASK = (1L << SectionSnapshot.SIZE) - 1L;
    private static final long INTERIOR_STORAGE_MASK = LOCAL_ROW_MASK << SectionSnapshot.HALO;

    private static final int[] DX = {-1, 1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, -1, 1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, -1, 1};

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /**
     * Worker-local primitive scratch. The returned mask owns its retained
     * words; these halo row masks are reused across jobs.
     */
    public static final class BuildScratch {
        private final long[] supportedRows = new long[SectionSnapshot.SIZE * SectionSnapshot.SIZE];
        private final long[] airRows = new long[SectionSnapshot.SIZE * SectionSnapshot.SIZE];
        private long uses;
        private int highWaterSupportedRows;

        private int prepare(SectionSnapshot snapshot) {
            int populatedRows = 0;
            for (int storageY = 0; storageY < SectionSnapshot.SIZE; storageY++) {
                int localY = storageY - SectionSnapshot.HALO;
                for (int storageZ = 0; storageZ < SectionSnapshot.SIZE; storageZ++) {
                    int localZ = storageZ - SectionSnapshot.HALO;
                    long supported = 0L;
                    long air = 0L;
                    for (int storageX = 0; storageX < SectionSnapshot.SIZE; storageX++) {
                        int localX = storageX - SectionSnapshot.HALO;
                        byte classification = snapshot.classification(localX, localY, localZ);
                        long bit = 1L << storageX;
                        if (classification == SectionSnapshot.SUPPORTED_FULL_CUBE) {
                            supported |= bit;
                        } else if (classification == SectionSnapshot.AIR) {
                            air |= bit;
                        } else if (classification != SectionSnapshot.UNSUPPORTED) {
                            throw new IllegalStateException("Unknown section classification: " + classification);
                        }
                    }
                    int row = storageY * SectionSnapshot.SIZE + storageZ;
                    supportedRows[row] = supported;
                    airRows[row] = air;
                    if (supported != 0L) populatedRows++;
                }
            }
            uses++;
            highWaterSupportedRows = Math.max(highWaterSupportedRows, populatedRows);
            return populatedRows;
        }

        private long supportedRow(int storageY, int storageZ) {
            return supportedRows[storageY * SectionSnapshot.SIZE + storageZ];
        }

        private long airRow(int storageY, int storageZ) {
            return airRows[storageY * SectionSnapshot.SIZE + storageZ];
        }

        private long unsupportedRow(int storageY, int storageZ) {
            return STORAGE_ROW_MASK & ~(supportedRow(storageY, storageZ) | airRow(storageY, storageZ));
        }

        public long uses() { return uses; }
        public int highWaterSupportedRows() { return highWaterSupportedRows; }
    }

    private final long[] words;
    private final int[] directionFaceCounts;
    private final int visibleFaceCount;
    private final int blockedByUnsupportedFaces;
    private final int populatedSupportedRows;
    private final long sourceSnapshotFingerprint;
    private final long fingerprint;
    private final long buildTimeNs;

    private BinarySectionVisibility(
            long[] words,
            int[] directionFaceCounts,
            int visibleFaceCount,
            int blockedByUnsupportedFaces,
            int populatedSupportedRows,
            long sourceSnapshotFingerprint,
            long fingerprint,
            long buildTimeNs) {
        this.words = words;
        this.directionFaceCounts = directionFaceCounts;
        this.visibleFaceCount = visibleFaceCount;
        this.blockedByUnsupportedFaces = blockedByUnsupportedFaces;
        this.populatedSupportedRows = populatedSupportedRows;
        this.sourceSnapshotFingerprint = sourceSnapshotFingerprint;
        this.fingerprint = fingerprint;
        this.buildTimeNs = buildTimeNs;
    }

    public static BinarySectionVisibility build(SectionSnapshot snapshot) {
        return build(snapshot, new BuildScratch());
    }

    public static BinarySectionVisibility build(SectionSnapshot snapshot, BuildScratch scratch) {
        if (snapshot == null || scratch == null) {
            throw new NullPointerException("snapshot and build scratch are required");
        }

        long startNs = System.nanoTime();
        int populatedRows = scratch.prepare(snapshot);
        long[] words = new long[TOTAL_WORDS];
        int[] counts = new int[DIRECTION_COUNT];
        int blockedUnsupported = 0;

        for (int y = 0; y < SectionSnapshot.INTERIOR_SIZE; y++) {
            int storageY = y + SectionSnapshot.HALO;
            for (int z = 0; z < SectionSnapshot.INTERIOR_SIZE; z++) {
                int storageZ = z + SectionSnapshot.HALO;
                long supported = scratch.supportedRow(storageY, storageZ);
                long airSame = scratch.airRow(storageY, storageZ);
                long unsupportedSame = scratch.unsupportedRow(storageY, storageZ);

                long west = localBits(supported & (airSame << 1));
                long east = localBits(supported & (airSame >>> 1));
                long down = localBits(supported & scratch.airRow(storageY - 1, storageZ));
                long up = localBits(supported & scratch.airRow(storageY + 1, storageZ));
                long north = localBits(supported & scratch.airRow(storageY, storageZ - 1));
                long south = localBits(supported & scratch.airRow(storageY, storageZ + 1));

                putRow(words, WEST, y, z, west);
                putRow(words, EAST, y, z, east);
                putRow(words, DOWN, y, z, down);
                putRow(words, UP, y, z, up);
                putRow(words, NORTH, y, z, north);
                putRow(words, SOUTH, y, z, south);

                counts[WEST] += Long.bitCount(west);
                counts[EAST] += Long.bitCount(east);
                counts[DOWN] += Long.bitCount(down);
                counts[UP] += Long.bitCount(up);
                counts[NORTH] += Long.bitCount(north);
                counts[SOUTH] += Long.bitCount(south);

                blockedUnsupported += Long.bitCount(localBits(supported & (unsupportedSame << 1)));
                blockedUnsupported += Long.bitCount(localBits(supported & (unsupportedSame >>> 1)));
                blockedUnsupported += Long.bitCount(localBits(
                        supported & scratch.unsupportedRow(storageY - 1, storageZ)));
                blockedUnsupported += Long.bitCount(localBits(
                        supported & scratch.unsupportedRow(storageY + 1, storageZ)));
                blockedUnsupported += Long.bitCount(localBits(
                        supported & scratch.unsupportedRow(storageY, storageZ - 1)));
                blockedUnsupported += Long.bitCount(localBits(
                        supported & scratch.unsupportedRow(storageY, storageZ + 1)));
            }
        }

        int totalFaces = 0;
        for (int count : counts) totalFaces += count;

        long hash = FNV_OFFSET_BASIS;
        hash = hashLong(hash, snapshot.fingerprint());
        hash = hashInt(hash, totalFaces);
        hash = hashInt(hash, blockedUnsupported);
        hash = hashInt(hash, populatedRows);
        for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
            hash = hashInt(hash, counts[direction]);
            int base = direction * WORDS_PER_DIRECTION;
            for (int word = 0; word < WORDS_PER_DIRECTION; word++) {
                hash = hashLong(hash, words[base + word]);
            }
        }

        BinarySectionVisibility result = new BinarySectionVisibility(
                words,
                counts,
                totalFaces,
                blockedUnsupported,
                populatedRows,
                snapshot.fingerprint(),
                hash,
                System.nanoTime() - startNs);
        result.validateAgainst(snapshot);
        return result;
    }

    /** Scalar invariant check against the immutable source snapshot. */
    public void validateAgainst(SectionSnapshot snapshot) {
        if (snapshot == null) throw new NullPointerException("snapshot");
        if (sourceSnapshotFingerprint != snapshot.fingerprint()) {
            throw new IllegalStateException("Binary visibility source fingerprint mismatch");
        }

        int[] expectedByDirection = new int[DIRECTION_COUNT];
        int expectedFaces = 0;
        int expectedBlockedUnsupported = 0;
        for (int y = 0; y < SectionSnapshot.INTERIOR_SIZE; y++) {
            for (int z = 0; z < SectionSnapshot.INTERIOR_SIZE; z++) {
                for (int x = 0; x < SectionSnapshot.INTERIOR_SIZE; x++) {
                    boolean sourceSupported = snapshot.classification(x, y, z)
                            == SectionSnapshot.SUPPORTED_FULL_CUBE;
                    for (int direction = 0; direction < DIRECTION_COUNT; direction++) {
                        byte neighbor = snapshot.classification(
                                x + DX[direction],
                                y + DY[direction],
                                z + DZ[direction]);
                        boolean expected = sourceSupported && neighbor == SectionSnapshot.AIR;
                        if (hasFace(x, y, z, direction) != expected) {
                            throw new IllegalStateException(
                                    "Binary visibility mismatch at (" + x + "," + y + "," + z
                                            + ") direction=" + direction);
                        }
                        if (expected) {
                            expectedByDirection[direction]++;
                            expectedFaces++;
                        } else if (sourceSupported && neighbor == SectionSnapshot.UNSUPPORTED) {
                            expectedBlockedUnsupported++;
                        }
                    }
                }
            }
        }
        if (expectedFaces != visibleFaceCount
                || expectedBlockedUnsupported != blockedByUnsupportedFaces
                || !Arrays.equals(expectedByDirection, directionFaceCounts)) {
            throw new IllegalStateException("Binary visibility aggregate accounting mismatch");
        }
    }

    /**
     * Independent set-equivalence check against the permanent simple oracle.
     * Equal total count plus complete reference inclusion proves there are no
     * extra optimized-mask faces.
     */
    public void validateAgainst(ReferenceFaceMesh reference) {
        if (reference == null) throw new NullPointerException("reference");
        if (visibleFaceCount != reference.faceCount()) {
            throw new IllegalStateException(
                    "Binary visibility/reference face count mismatch: mask=" + visibleFaceCount
                            + ", reference=" + reference.faceCount());
        }
        if (blockedByUnsupportedFaces != reference.blockedByUnsupportedFaces()) {
            throw new IllegalStateException(
                    "Binary visibility/reference unsupported-neighbor count mismatch");
        }
        for (int i = 0; i < reference.faceCount(); i++) {
            int packed = reference.packedFace(i);
            int x = packed & 0xF;
            int y = (packed >>> 4) & 0xF;
            int z = (packed >>> 8) & 0xF;
            int direction = (packed >>> 12) & 0x7;
            if (direction >= DIRECTION_COUNT || !hasFace(x, y, z, direction)) {
                throw new IllegalStateException("Binary visibility missed reference face index " + i);
            }
        }
    }

    public boolean contentEquals(BinarySectionVisibility other) {
        return other != null
                && visibleFaceCount == other.visibleFaceCount
                && blockedByUnsupportedFaces == other.blockedByUnsupportedFaces
                && populatedSupportedRows == other.populatedSupportedRows
                && sourceSnapshotFingerprint == other.sourceSnapshotFingerprint
                && fingerprint == other.fingerprint
                && Arrays.equals(directionFaceCounts, other.directionFaceCounts)
                && Arrays.equals(words, other.words);
    }

    public boolean hasFace(int x, int y, int z, int direction) {
        validateInteriorCoordinate(x);
        validateInteriorCoordinate(y);
        validateInteriorCoordinate(z);
        validateDirection(direction);
        int bitIndex = ((y * SectionSnapshot.INTERIOR_SIZE) + z) * SectionSnapshot.INTERIOR_SIZE + x;
        int wordIndex = direction * WORDS_PER_DIRECTION + (bitIndex >>> 6);
        return (words[wordIndex] & (1L << (bitIndex & 63))) != 0L;
    }

    /** Returns the 16 X-axis visibility bits for one local (Y,Z) row. */
    public int rowBitsX(int direction, int y, int z) {
        validateDirection(direction);
        validateInteriorCoordinate(y);
        validateInteriorCoordinate(z);
        int row = y * SectionSnapshot.INTERIOR_SIZE + z;
        int word = direction * WORDS_PER_DIRECTION + row / ROWS_PER_WORD;
        int shift = (row % ROWS_PER_WORD) * SectionSnapshot.INTERIOR_SIZE;
        return (int) ((words[word] >>> shift) & LOCAL_ROW_MASK);
    }

    public long maskWord(int direction, int word) {
        validateDirection(direction);
        if (word < 0 || word >= WORDS_PER_DIRECTION) {
            throw new IndexOutOfBoundsException("Visibility word index: " + word);
        }
        return words[direction * WORDS_PER_DIRECTION + word];
    }

    public int directionFaceCount(int direction) {
        validateDirection(direction);
        return directionFaceCounts[direction];
    }

    public int visibleFaceCount() { return visibleFaceCount; }
    public int blockedByUnsupportedFaces() { return blockedByUnsupportedFaces; }
    public int populatedSupportedRows() { return populatedSupportedRows; }
    public int retainedBytes() { return RETAINED_BYTES; }
    public long sourceSnapshotFingerprint() { return sourceSnapshotFingerprint; }
    public long fingerprint() { return fingerprint; }
    public long buildTimeNs() { return buildTimeNs; }

    private static long localBits(long storageBits) {
        return (storageBits & INTERIOR_STORAGE_MASK) >>> SectionSnapshot.HALO;
    }

    private static void putRow(long[] words, int direction, int y, int z, long localBits) {
        int row = y * SectionSnapshot.INTERIOR_SIZE + z;
        int word = direction * WORDS_PER_DIRECTION + row / ROWS_PER_WORD;
        int shift = (row % ROWS_PER_WORD) * SectionSnapshot.INTERIOR_SIZE;
        words[word] |= (localBits & LOCAL_ROW_MASK) << shift;
    }

    private static void validateInteriorCoordinate(int coordinate) {
        if (coordinate < 0 || coordinate >= SectionSnapshot.INTERIOR_SIZE) {
            throw new IndexOutOfBoundsException("Interior coordinate: " + coordinate);
        }
    }

    private static void validateDirection(int direction) {
        if (direction < 0 || direction >= DIRECTION_COUNT) {
            throw new IndexOutOfBoundsException("Visibility direction: " + direction);
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
