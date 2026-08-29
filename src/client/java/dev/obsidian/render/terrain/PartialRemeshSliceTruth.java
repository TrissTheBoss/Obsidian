package dev.obsidian.render.terrain;

import java.util.Arrays;

/** Compact immutable per-slice truth retained only for dev16 shadow comparison. */
public final class PartialRemeshSliceTruth {
    public static final int SLICE_COUNT = 4;
    public static final int METADATA_BYTES_PER_SECTION = 96;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    public static final class BuildScratch {
        private final long[] bakedHashes = new long[SLICE_COUNT];
        private final long[] referenceHashes = new long[SLICE_COUNT];
        private final int[] bakedCounts = new int[SLICE_COUNT];
        private final int[] referenceCounts = new int[SLICE_COUNT];
        private long uses;

        private void begin() {
            Arrays.fill(bakedHashes, FNV_OFFSET_BASIS);
            Arrays.fill(referenceHashes, FNV_OFFSET_BASIS);
            Arrays.fill(bakedCounts, 0);
            Arrays.fill(referenceCounts, 0);
            uses++;
        }
        public long uses() { return uses; }
        public int retainedScratchBytes() { return 2 * SLICE_COUNT * Long.BYTES + 2 * SLICE_COUNT * Integer.BYTES; }
    }

    private final long[] fingerprints;
    private final int[] bakedQuads;
    private final int[] referenceFaces;

    private PartialRemeshSliceTruth(long[] fingerprints, int[] bakedQuads, int[] referenceFaces) {
        this.fingerprints = fingerprints;
        this.bakedQuads = bakedQuads;
        this.referenceFaces = referenceFaces;
    }

    public static PartialRemeshSliceTruth build(
            SectionBakedQuadSnapshot baked, ReferenceFaceMesh reference, BuildScratch scratch) {
        if (baked == null || reference == null || scratch == null) throw new NullPointerException();
        scratch.begin();
        for (int quad = 0; quad < baked.quadCount(); quad++) {
            int packed = baked.sourceBlock(quad);
            int slice = ((packed >>> 4) & 0xF) >>> 2;
            long h = scratch.bakedHashes[slice];
            h = hashInt(h, packed);
            h = hashInt(h, baked.stateId(quad));
            h = hashInt(h, Byte.toUnsignedInt(baked.direction(quad)));
            h = hashInt(h, Byte.toUnsignedInt(baked.layer(quad)));
            SectionBakedQuadSnapshot.MaterialIdentity material = baked.material(baked.materialId(quad));
            h = hashString(h, material.atlas());
            h = hashString(h, material.sprite());
            h = hashInt(h, Byte.toUnsignedInt(material.layer()));
            h = hashInt(h, material.materialFlags());
            h = hashInt(h, material.tintIndex());
            h = hashInt(h, material.shade() ? 1 : 0);
            h = hashInt(h, material.lightEmission());
            h = hashInt(h, material.animated() ? 1 : 0);
            for (int vertex = 0; vertex < SectionBakedQuadSnapshot.VERTICES_PER_QUAD; vertex++) {
                h = hashInt(h, Float.floatToRawIntBits(baked.position(quad, vertex, 0)));
                h = hashInt(h, Float.floatToRawIntBits(baked.position(quad, vertex, 1)));
                h = hashInt(h, Float.floatToRawIntBits(baked.position(quad, vertex, 2)));
                h = hashInt(h, Float.floatToRawIntBits(baked.u(quad, vertex)));
                h = hashInt(h, Float.floatToRawIntBits(baked.v(quad, vertex)));
                h = hashInt(h, baked.exactArgbColor(quad, vertex));
                h = hashInt(h, baked.packedLight(quad, vertex));
            }
            scratch.bakedHashes[slice] = h;
            scratch.bakedCounts[slice]++;
        }
        for (int face = 0; face < reference.faceCount(); face++) {
            int packed = reference.packedFace(face);
            int slice = ((packed >>> 4) & 0xF) >>> 2;
            long h = scratch.referenceHashes[slice];
            h = hashInt(h, packed);
            h = hashInt(h, reference.stateId(face));
            scratch.referenceHashes[slice] = h;
            scratch.referenceCounts[slice]++;
        }
        long[] fingerprints = new long[SLICE_COUNT];
        for (int slice = 0; slice < SLICE_COUNT; slice++) {
            long h = FNV_OFFSET_BASIS;
            h = hashLong(h, scratch.bakedHashes[slice]);
            h = hashLong(h, scratch.referenceHashes[slice]);
            h = hashInt(h, scratch.bakedCounts[slice]);
            h = hashInt(h, scratch.referenceCounts[slice]);
            fingerprints[slice] = h;
        }
        return new PartialRemeshSliceTruth(
                fingerprints,
                Arrays.copyOf(scratch.bakedCounts, SLICE_COUNT),
                Arrays.copyOf(scratch.referenceCounts, SLICE_COUNT));
    }

    public long fingerprint(int slice) { check(slice); return fingerprints[slice]; }
    public int bakedQuads(int slice) { check(slice); return bakedQuads[slice]; }
    public int referenceFaces(int slice) { check(slice); return referenceFaces[slice]; }
    public int totalBakedQuads() { return Arrays.stream(bakedQuads).sum(); }
    public int totalReferenceFaces() { return Arrays.stream(referenceFaces).sum(); }

    public boolean contentEquals(PartialRemeshSliceTruth other) {
        return other != null && Arrays.equals(fingerprints, other.fingerprints)
                && Arrays.equals(bakedQuads, other.bakedQuads)
                && Arrays.equals(referenceFaces, other.referenceFaces);
    }

    private static void check(int slice) {
        if (slice < 0 || slice >= SLICE_COUNT) throw new IndexOutOfBoundsException(slice);
    }
    private static long hashInt(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value); return hash * FNV_PRIME;
    }
    private static long hashLong(long hash, long value) {
        hash ^= value; return hash * FNV_PRIME;
    }
    private static long hashString(long hash, String value) {
        if (value == null) return hashInt(hash, -1);
        hash = hashInt(hash, value.length());
        for (int i = 0; i < value.length(); i++) hash = hashInt(hash, value.charAt(i));
        return hash;
    }
}
