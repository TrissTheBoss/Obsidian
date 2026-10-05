package dev.obsidian.render.visibility;

public final class BoundedLifecycleEventRing {
    private final byte[] types;
    private final long[] keys;
    private int head;
    private int count;
    private boolean overflowed;
    private long overflowEvents;
    private long recordedEvents;

    public BoundedLifecycleEventRing(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        types = new byte[capacity];
        keys = new long[capacity];
    }

    public synchronized boolean record(byte type, long key) {
        recordedEvents++;
        if (count == types.length) {
            overflowed = true;
            overflowEvents++;
            return false;
        }
        int tail = head + count;
        if (tail >= types.length) tail -= types.length;
        types[tail] = type;
        keys[tail] = key;
        count++;
        return true;
    }

    public synchronized int drainTo(byte[] targetTypes, long[] targetKeys, int maxEvents) {
        if (targetTypes == null || targetKeys == null || maxEvents <= 0) return 0;
        int drained = Math.min(count, Math.min(maxEvents, Math.min(targetTypes.length, targetKeys.length)));
        for (int i = 0; i < drained; i++) {
            targetTypes[i] = types[head];
            targetKeys[i] = keys[head];
            types[head] = 0;
            keys[head] = 0L;
            head++;
            if (head == types.length) head = 0;
        }
        count -= drained;
        return drained;
    }

    public synchronized boolean consumeOverflowed() {
        boolean value = overflowed;
        overflowed = false;
        return value;
    }

    public synchronized long overflowEvents() { return overflowEvents; }
    public synchronized long recordedEvents() { return recordedEvents; }
    public synchronized int size() { return count; }
}
