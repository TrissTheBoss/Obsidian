package dev.obsidian.render.terrain;

/**
 * Allocation-free coalescing bridge from exact Minecraft lifecycle mixins into
 * the render-thread P2.6 one-section lifecycle proof.
 *
 * <p>The bridge intentionally does not queue every vanilla dirty notification.
 * Once the validation section is bound, only events that can affect that exact
 * section (or its 3x3 chunk halo neighborhood), plus world/resource changes,
 * advance the renderer validity sequence. Counters are sticky and drained by
 * delta, so unrelated chunk streaming cannot overflow a ring and cannot make a
 * valid tracked generation stale.</p>
 */
public final class SectionLifecycleEvents {
    public static final int REASON_SECTION_DIRTY = 1;
    public static final int REASON_CHUNK_LOAD = 1 << 1;
    public static final int REASON_CHUNK_UNLOAD = 1 << 2;
    public static final int REASON_WORLD_CHANGE = 1 << 3;
    public static final int REASON_RESOURCE_RELOAD = 1 << 4;
    /** Reserved for explicit safety invalidation; the coalescing bridge itself cannot overflow. */
    public static final int REASON_OVERFLOW = 1 << 5;

    private static boolean targetKnown;
    private static int targetSectionX;
    private static int targetSectionY;
    private static int targetSectionZ;

    /** Monotonic sequence of events relevant to the currently tracked renderer validity domain. */
    private static long relevantSequence;
    private static long sectionDirtyEvents;
    private static long playerDirtyEvents;
    private static long chunkLoadEvents;
    private static long chunkUnloadEvents;
    private static long worldChangeEvents;
    private static long resourceReloadEvents;

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
        return relevantSequence;
    }

    /**
     * Called from the exact vanilla LevelExtractor dirty sink. Vanilla already
     * performed block/light/border propagation; only the chosen section matters
     * to this one-section proof.
     */
    public static synchronized void sectionDirty(
            int sectionX,
            int sectionY,
            int sectionZ,
            boolean dirtyFromPlayer) {
        if (!targetKnown
                || sectionX != targetSectionX
                || sectionY != targetSectionY
                || sectionZ != targetSectionZ) {
            return;
        }
        relevantSequence++;
        sectionDirtyEvents++;
        if (dirtyFromPlayer) playerDirtyEvents++;
    }

    public static synchronized void chunkLoaded(int chunkX, int chunkZ) {
        if (!isTargetNeighborhoodChunk(chunkX, chunkZ)) return;
        relevantSequence++;
        chunkLoadEvents++;
    }

    public static synchronized void chunkUnloaded(int chunkX, int chunkZ) {
        if (!isTargetNeighborhoodChunk(chunkX, chunkZ)) return;
        relevantSequence++;
        chunkUnloadEvents++;
    }

    /** World replacement/teardown invalidates any previous target immediately. */
    public static synchronized void worldChanged() {
        relevantSequence++;
        worldChangeEvents++;
        targetKnown = false;
    }

    public static synchronized void resourceReloaded() {
        relevantSequence++;
        resourceReloadEvents++;
    }

    private static boolean isTargetNeighborhoodChunk(int chunkX, int chunkZ) {
        return targetKnown
                && Math.abs(chunkX - targetSectionX) <= 1
                && Math.abs(chunkZ - targetSectionZ) <= 1;
    }

    /**
     * Drains sticky counters by delta and synchronizes the bridge's target with
     * the coordinator. A pending world change wins over the coordinator's old
     * target so teardown cannot accidentally rebind stale coordinates for one
     * frame. No event payload can be overwritten, so droppedEvents remains zero.
     */
    public static synchronized int drain(
            Cursor cursor,
            boolean rendererTargetKnown,
            int sectionX,
            int sectionY,
            int sectionZ) {
        boolean worldChangePending = worldChangeEvents != cursor.worldChangeEvents;
        if (worldChangePending || !rendererTargetKnown) {
            targetKnown = false;
        } else {
            targetKnown = true;
            targetSectionX = sectionX;
            targetSectionY = sectionY;
            targetSectionZ = sectionZ;
        }

        cursor.lastRelevantEventCount = 0;
        int reasons = 0;

        long dirtyDelta = sectionDirtyEvents - cursor.sectionDirtyEvents;
        if (dirtyDelta > 0L) {
            reasons |= REASON_SECTION_DIRTY;
            cursor.sectionDirtyEvents = sectionDirtyEvents;
            cursor.lastRelevantEventCount = addRelevantCount(cursor.lastRelevantEventCount, dirtyDelta);
        }

        long playerDelta = playerDirtyEvents - cursor.playerDirtyEvents;
        if (playerDelta > 0L) {
            cursor.playerDirtyEvents = playerDirtyEvents;
        }

        long loadDelta = chunkLoadEvents - cursor.chunkLoadEvents;
        if (loadDelta > 0L) {
            reasons |= REASON_CHUNK_LOAD;
            cursor.chunkLoadEvents = chunkLoadEvents;
            cursor.lastRelevantEventCount = addRelevantCount(cursor.lastRelevantEventCount, loadDelta);
        }

        long unloadDelta = chunkUnloadEvents - cursor.chunkUnloadEvents;
        if (unloadDelta > 0L) {
            reasons |= REASON_CHUNK_UNLOAD;
            cursor.chunkUnloadEvents = chunkUnloadEvents;
            cursor.lastRelevantEventCount = addRelevantCount(cursor.lastRelevantEventCount, unloadDelta);
        }

        long worldDelta = worldChangeEvents - cursor.worldChangeEvents;
        if (worldDelta > 0L) {
            reasons |= REASON_WORLD_CHANGE;
            cursor.worldChangeEvents = worldChangeEvents;
            cursor.lastRelevantEventCount = addRelevantCount(cursor.lastRelevantEventCount, worldDelta);
        }

        long resourceDelta = resourceReloadEvents - cursor.resourceReloadEvents;
        if (resourceDelta > 0L) {
            reasons |= REASON_RESOURCE_RELOAD;
            cursor.resourceReloadEvents = resourceReloadEvents;
            cursor.lastRelevantEventCount = addRelevantCount(cursor.lastRelevantEventCount, resourceDelta);
        }

        cursor.sequence = relevantSequence;
        return reasons;
    }

    private static int addRelevantCount(int current, long delta) {
        long sum = current + delta;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    public static String describeReasons(int reasons) {
        if (reasons == 0) return "none";
        StringBuilder out = new StringBuilder();
        appendReason(out, reasons, REASON_SECTION_DIRTY, "section-dirty");
        appendReason(out, reasons, REASON_CHUNK_LOAD, "chunk-load");
        appendReason(out, reasons, REASON_CHUNK_UNLOAD, "chunk-unload");
        appendReason(out, reasons, REASON_WORLD_CHANGE, "world-change");
        appendReason(out, reasons, REASON_RESOURCE_RELOAD, "resource-reload");
        appendReason(out, reasons, REASON_OVERFLOW, "safety-invalidate");
        return out.toString();
    }

    private static void appendReason(StringBuilder out, int reasons, int mask, String name) {
        if ((reasons & mask) == 0) return;
        if (!out.isEmpty()) out.append('|');
        out.append(name);
    }
}
