package dev.obsidian.render.terrain;

/** Immutable primitive-only identity for one localized dev16 shadow episode. */
public final class PartialRemeshShadowRequest {
    private final long episodeId;
    private final int sliceMask;
    private final int editCount;
    private final long f0;
    private final long f1;
    private final long f2;
    private final long f3;

    public PartialRemeshShadowRequest(long episodeId, int sliceMask, int editCount, PartialRemeshSliceTruth previous) {
        if (episodeId <= 0L || previous == null) throw new IllegalArgumentException("episode/previous truth required");
        if ((sliceMask & ~PartialRemeshDirtyProvenance.ALL_SLICES_MASK) != 0 || sliceMask == 0
                || sliceMask == PartialRemeshDirtyProvenance.ALL_SLICES_MASK) {
            throw new IllegalArgumentException("localized slice mask must select one to three slices");
        }
        this.episodeId = episodeId;
        this.sliceMask = sliceMask;
        this.editCount = Math.max(1, editCount);
        f0 = previous.fingerprint(0);
        f1 = previous.fingerprint(1);
        f2 = previous.fingerprint(2);
        f3 = previous.fingerprint(3);
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
}
