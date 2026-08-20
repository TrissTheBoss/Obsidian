package dev.obsidian.render.frame;

/**
 * Preallocated CPU-side metadata for one rotating frame slot.
 *
 * <p>This is not, by itself, proof that GPU work from an older use of the slot
 * has completed. GPU-owned resources must use an explicit completion primitive
 * before reuse or destruction.</p>
 */
public final class FrameContext {
    private final int slotIndex;

    private long serial;
    private long beginNs;

    FrameContext(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    void begin(long serial, long beginNs) {
        this.serial = serial;
        this.beginNs = beginNs;
    }

    long finish(long endNs) {
        long start = beginNs;
        beginNs = 0L;
        return start == 0L ? 0L : endNs - start;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public long serial() {
        return serial;
    }
}
