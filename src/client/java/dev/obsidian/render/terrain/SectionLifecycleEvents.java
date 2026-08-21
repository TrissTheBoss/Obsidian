package dev.obsidian.render.terrain;

/**
 * Allocation-free bounded bridge from exact Minecraft lifecycle mixins into the
 * render-thread P2.6 one-section lifecycle proof. No mutable Minecraft object
 * is retained; only coordinates, reason bits and a monotonic event sequence.
 */
public final class SectionLifecycleEvents {
    public static final int REASON_SECTION_DIRTY = 1;
    public static final int REASON_CHUNK_LOAD = 1 << 1;
    public static final int REASON_CHUNK_UNLOAD = 1 << 2;
    public static final int REASON_WORLD_CHANGE = 1 << 3;
    public static final int REASON_RESOURCE_RELOAD = 1 << 4;
    public static final int REASON_OVERFLOW = 1 << 5;

    private static final byte TYPE_SECTION_DIRTY = 1;
    private static final byte TYPE_CHUNK_LOAD = 2;
    private static final byte TYPE_CHUNK_UNLOAD = 3;
    private static final byte TYPE_WORLD_CHANGE = 4;
    private static final byte TYPE_RESOURCE_RELOAD = 5;
    private static final int CAPACITY = 256;

    private static final long[] sequences = new long[CAPACITY];
    private static final byte[] types = new byte[CAPACITY];
    private static final int[] xs = new int[CAPACITY];
    private static final int[] ys = new int[CAPACITY];
    private static final int[] zs = new int[CAPACITY];
    private static final boolean[] playerDirty = new boolean[CAPACITY];
    private static long latestSequence;

    private SectionLifecycleEvents() {}

    public static final class Cursor {
        private long sequence;
        private long droppedEvents;
        private long sectionDirtyEvents;
        private long playerDirtyEvents;
        private long chunkLoadEvents;
        private long chunkUnloadEvents;
        private long worldChangeEvents;
        private long resourceReloadEvents;
        private int lastRelevantEventCount;

        public long sequence() { return sequence; }
        public long droppedEvents() { return droppedEvents; }
        public long sectionDirtyEvents() { return sectionDirtyEvents; }
        public long playerDirtyEvents() { return playerDirtyEvents; }
        public long chunkLoadEvents() { return chunkLoadEvents; }
        public long chunkUnloadEvents() { return chunkUnloadEvents; }
        public long worldChangeEvents() { return worldChangeEvents; }
        public long resourceReloadEvents() { return resourceReloadEvents; }
        public int lastRelevantEventCount() { return lastRelevantEventCount; }
    }

    public static synchronized long latestSequence() {
        return latestSequence;
    }

    public static void sectionDirty(int sectionX, int sectionY, int sectionZ, boolean dirtyFromPlayer) {
        append(TYPE_SECTION_DIRTY, sectionX, sectionY, sectionZ, dirtyFromPlayer);
    }

    public static void chunkLoaded(int chunkX, int chunkZ) {
        append(TYPE_CHUNK_LOAD, chunkX, 0, chunkZ, false);
    }

    public static void chunkUnloaded(int chunkX, int chunkZ) {
        append(TYPE_CHUNK_UNLOAD, chunkX, 0, chunkZ, false);
    }

    public static void worldChanged() {
        append(TYPE_WORLD_CHANGE, 0, 0, 0, false);
    }

    public static void resourceReloaded() {
        append(TYPE_RESOURCE_RELOAD, 0, 0, 0, false);
    }

    private static synchronized void append(byte type, int x, int y, int z, boolean fromPlayer) {
        long sequence = ++latestSequence;
        int slot = (int) (sequence % CAPACITY);
        sequences[slot] = sequence;
        types[slot] = type;
        xs[slot] = x;
        ys[slot] = y;
        zs[slot] = z;
        playerDirty[slot] = fromPlayer;
    }

    public static synchronized int drain(
            Cursor cursor,
            boolean targetKnown,
            int sectionX,
            int sectionY,
            int sectionZ) {
        long newest = latestSequence;
        cursor.lastRelevantEventCount = 0;
        if (cursor.sequence == newest) {
            return 0;
        }

        int reasons = 0;
        long oldestAvailable = Math.max(1L, newest - CAPACITY + 1L);
        long first = cursor.sequence + 1L;
        if (first < oldestAvailable) {
            cursor.droppedEvents += oldestAvailable - first;
            reasons |= REASON_OVERFLOW;
            cursor.lastRelevantEventCount++;
            first = oldestAvailable;
        }

        for (long sequence = first; sequence <= newest; sequence++) {
            int slot = (int) (sequence % CAPACITY);
            if (sequences[slot] != sequence) {
                cursor.droppedEvents++;
                reasons |= REASON_OVERFLOW;
                cursor.lastRelevantEventCount++;
                continue;
            }
            byte type = types[slot];
            switch (type) {
                case TYPE_SECTION_DIRTY -> {
                    if (targetKnown && xs[slot] == sectionX && ys[slot] == sectionY && zs[slot] == sectionZ) {
                        reasons |= REASON_SECTION_DIRTY;
                        cursor.sectionDirtyEvents++;
                        if (playerDirty[slot]) cursor.playerDirtyEvents++;
                        cursor.lastRelevantEventCount++;
                    }
                }
                case TYPE_CHUNK_LOAD -> {
                    if (targetKnown && Math.abs(xs[slot] - sectionX) <= 1 && Math.abs(zs[slot] - sectionZ) <= 1) {
                        reasons |= REASON_CHUNK_LOAD;
                        cursor.chunkLoadEvents++;
                        cursor.lastRelevantEventCount++;
                    }
                }
                case TYPE_CHUNK_UNLOAD -> {
                    if (targetKnown && Math.abs(xs[slot] - sectionX) <= 1 && Math.abs(zs[slot] - sectionZ) <= 1) {
                        reasons |= REASON_CHUNK_UNLOAD;
                        cursor.chunkUnloadEvents++;
                        cursor.lastRelevantEventCount++;
                    }
                }
                case TYPE_WORLD_CHANGE -> {
                    reasons |= REASON_WORLD_CHANGE;
                    cursor.worldChangeEvents++;
                    cursor.lastRelevantEventCount++;
                }
                case TYPE_RESOURCE_RELOAD -> {
                    reasons |= REASON_RESOURCE_RELOAD;
                    cursor.resourceReloadEvents++;
                    cursor.lastRelevantEventCount++;
                }
                default -> {
                    cursor.droppedEvents++;
                    reasons |= REASON_OVERFLOW;
                    cursor.lastRelevantEventCount++;
                }
            }
        }
        cursor.sequence = newest;
        return reasons;
    }

    public static String describeReasons(int reasons) {
        if (reasons == 0) return "none";
        StringBuilder out = new StringBuilder();
        appendReason(out, reasons, REASON_SECTION_DIRTY, "section-dirty");
        appendReason(out, reasons, REASON_CHUNK_LOAD, "chunk-load");
        appendReason(out, reasons, REASON_CHUNK_UNLOAD, "chunk-unload");
        appendReason(out, reasons, REASON_WORLD_CHANGE, "world-change");
        appendReason(out, reasons, REASON_RESOURCE_RELOAD, "resource-reload");
        appendReason(out, reasons, REASON_OVERFLOW, "event-overflow");
        return out.toString();
    }

    private static void appendReason(StringBuilder out, int reasons, int mask, String name) {
        if ((reasons & mask) == 0) return;
        if (!out.isEmpty()) out.append('|');
        out.append(name);
    }
}
