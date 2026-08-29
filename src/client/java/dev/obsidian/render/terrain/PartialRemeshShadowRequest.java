package dev.obsidian.render.terrain;

/** Immutable primitive-only identity for one localized dev18 shadow episode. */
public final class PartialRemeshShadowRequest {
    private final long episodeId;
    private final int sliceMask;
    private final int editCount;
    private final long f0;
    private final long f1;
    private final long f2;
    private final long f3;

    public PartialRemeshShadowRequest(long episodeId, int sliceMask, int editCount, PartialRemeshSliceTruth previous) {
        if (previous == null) throw new IllegalArgumentException("previous truth required");
        validate(episodeId, sliceMask, editCount);
        this.episodeId = episodeId;
        this.sliceMask = sliceMask;
        this.editCount = editCount;
        f0 = previous.fingerprint(0);
        f1 = previous.fingerprint(1);
        f2 = previous.fingerprint(2);
        f3 = previous.fingerprint(3);
    }

    private PartialRemeshShadowRequest(
            long episodeId, int sliceMask, int editCount,
            long f0, long f1, long f2, long f3) {
        validate(episodeId, sliceMask, editCount);
        this.episodeId = episodeId;
        this.sliceMask = sliceMask;
        this.editCount = editCount;
        this.f0 = f0;
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
    }

    private static void validate(long episodeId, int sliceMask, int editCount) {
        if (episodeId <= 0L || editCount <= 0) throw new IllegalArgumentException("episode/edit count required");
        if ((sliceMask & ~PartialRemeshDirtyProvenance.ALL_SLICES_MASK) != 0 || sliceMask == 0
                || sliceMask == PartialRemeshDirtyProvenance.ALL_SLICES_MASK) {
            throw new IllegalArgumentException("localized slice mask must select one to three slices");
        }
    }

    public PartialRemeshShadowRequest coalesce(int additionalSliceMask, int additionalEditCount) {
        if (additionalEditCount <= 0
                || (additionalSliceMask & ~PartialRemeshDirtyProvenance.ALL_SLICES_MASK) != 0
                || additionalSliceMask == 0
                || additionalSliceMask == PartialRemeshDirtyProvenance.ALL_SLICES_MASK) {
            throw new IllegalArgumentException("localized coalesced edit required");
        }
        int combinedMask = sliceMask | additionalSliceMask;
        if (combinedMask == PartialRemeshDirtyProvenance.ALL_SLICES_MASK) {
            throw new IllegalArgumentException("coalesced episode spans all four slices");
        }
        int combinedEditCount = Math.addExact(editCount, additionalEditCount);
        return new PartialRemeshShadowRequest(
                episodeId, combinedMask, combinedEditCount, f0, f1, f2, f3);
    }

    public long episodeId() { return episodeId; }
    public int sliceMask() { return sliceMask; }
    public int editCount() { return editCount; }
    public boolean coalesced() { return editCount > 1; }
    public int selectedSliceCount() { return Integer.bitCount(sliceMask); }
    public boolean selected(int slice) { return (sliceMask & (1 << slice)) != 0; }
    public long previousFingerprint(int slice) {
        return switch (slice) {
            case 0 -> f0;
            case 1 -> f1;
            case 2 -> f2;
            case 3 -> f3;
            default -> throw new IndexOutOfBoundsException(slice);
        };
    }

    public static boolean selfTest() {
        PartialRemeshShadowRequest base = new PartialRemeshShadowRequest(7L, 1, 2, 11L, 22L, 33L, 44L);
        PartialRemeshShadowRequest same = base.coalesce(1, 3);
        PartialRemeshShadowRequest adjacent = base.coalesce(2, 1);
        boolean allRejected = false;
        try { adjacent.coalesce(12, 1); } catch (IllegalArgumentException expected) { allRejected = true; }
        boolean overflowRejected = false;
        try {
            new PartialRemeshShadowRequest(8L, 1, Integer.MAX_VALUE, 1L, 2L, 3L, 4L).coalesce(1, 1);
        } catch (ArithmeticException expected) {
            overflowRejected = true;
        }
        return same.episodeId == 7L && same.sliceMask == 1 && same.editCount == 5
                && adjacent.episodeId == 7L && adjacent.sliceMask == 3 && adjacent.editCount == 3
                && same.f0 == 11L && same.f1 == 22L && same.f2 == 33L && same.f3 == 44L
                && adjacent.f0 == 11L && adjacent.f1 == 22L && adjacent.f2 == 33L && adjacent.f3 == 44L
                && allRejected && overflowRejected;
    }
}
