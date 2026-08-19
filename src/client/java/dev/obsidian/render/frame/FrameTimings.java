package dev.obsidian.render.frame;

/**
 * Fixed-size render-thread timing history.
 *
 * <p>This intentionally allocates once and then only overwrites primitive
 * samples. Phase 1 uses it as the foundation for percentile/frame-pacing
 * telemetry without introducing per-frame garbage.</p>
 */
public final class FrameTimings {
    public static final int DEFAULT_CAPACITY = 2048;

    private final long[] samplesNs;
    private int cursor;
    private int size;
    private long latestNs;

    public FrameTimings() {
        this(DEFAULT_CAPACITY);
    }

    public FrameTimings(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.samplesNs = new long[capacity];
    }

    public void record(long frameTimeNs) {
        if (frameTimeNs < 0L) {
            return;
        }

        samplesNs[cursor] = frameTimeNs;
        cursor++;
        if (cursor == samplesNs.length) {
            cursor = 0;
        }
        if (size < samplesNs.length) {
            size++;
        }
        latestNs = frameTimeNs;
    }

    public long latestNs() {
        return latestNs;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return samplesNs.length;
    }

    /**
     * Copies samples oldest-to-newest into {@code destination}.
     * This is intended for explicit profiler snapshots, never the hot path.
     */
    public int copyChronological(long[] destination) {
        int count = Math.min(size, destination.length);
        int oldest = size == samplesNs.length ? cursor : 0;
        int skip = size - count;
        for (int i = 0; i < count; i++) {
            destination[i] = samplesNs[(oldest + skip + i) % samplesNs.length];
        }
        return count;
    }
}
