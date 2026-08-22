package dev.obsidian.render.terrain;

/**
 * Allocation-free coalescing bridge from exact Minecraft lifecycle mixins into
 * the render-thread multi-section scene.
 *
 * <p>The active tracked scene is a 3x3 horizontal section window at one section
 * Y. Exact dirty events inside that window advance renderer validity. Chunk
 * load/unload events use radius two in X/Z because the union of every record's
 * one-block halo spans a 5x5 chunk footprint. World and resource changes are
 * always relevant. Counters are sticky and drained by delta, so unrelated world
 * streaming cannot overflow a queue or stale a valid scene generation.</p>
 *
 * <p>Dev3 additionally freezes the first tracked scene center as a diagnostic-only
 * lifecycle anchor. Chunk load/unload events in that anchor's 3x3 chunk halo are
 * counted even after the render scene recenters. Anchor events do not advance the
 * active scene validity sequence unless they are also relevant to the current
 * scene. This permits a later production run to prove the old P2.6 fixed-target
 * unload/return event class without coupling current rendering to stale coordinates.</p>
 */
public final class SectionLifecycleEvents {
    public static final int REASON_SECTION_DIRTY = 1;
    public static final int REASON_CHUNK_LOAD = 1 << 1;
    public static final int REASON_CHUNK_UNLOAD = 1 << 2;
    public static final int REASON_WORLD_CHANGE = 1 << 3;
    public static final int REASON_RESOURCE_RELOAD = 1 << 4;
    /** Reserved for explicit internal safety invalidation; the bridge itself cannot overflow. */
    public static final int REASON_OVERFLOW = 1 << 5;
    /** Renderer-originated scene recenter, not emitted by Minecraft lifecycle hooks. */
    public static final int REASON_SCENE_RECENTER = 1 << 6;

    public static final int SCENE_SECTION_RADIUS = 1;
    public static final int SCENE_RECORD_CAPACITY = 9;
    public static final int SCENE_HALO_CHUNK_RADIUS = SCENE_SECTION_RADIUS + 1;
    public static final int FIXED_LIFECYCLE_HALO_CHUNK_RADIUS = 1;

    private static boolean targetKnown;
    private static int targetSectionX;
    private static int targetSectionY;
    private static int targetSectionZ;

    private static boolean fixedAnchorKnown;
    private static int fixedAnchorChunkX;
    private static int fixedAnchorChunkZ;

    /** Monotonic sequence of events relevant to the currently tracked scene validity domain. */
    private static long relevantSequence;
    private static long sectionDirtyEvents;
    private static long playerDirtyEvents;
    private static long chunkLoadEvents;
    private static long chunkUnloadEvents;
    private static long worldChangeEvents;
    private static long resourceReloadEvents;
    private static long fixedAnchorChunkLoadEvents;
    private static long fixedAnchorChunkUnloadEvents;

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
        private long fixedAnchorChunkLoadEvents;
        private long fixedAnchorChunkUnloadEvents;
        private int lastRelevantEventCount;

        public long sequence() { return sequence; }
        public long droppedEvents() { return droppedEvents; }
        public long sectionDirtyEvents() { return sectionDirtyEvents; }
        public long playerDirtyEvents() { return playerDirtyEvents; }
        public long chunkLoadEvents() { return chunkLoadEvents; }
        public long chunkUnloadEvents() { return chunkUnloadEvents; }
        public long worldChangeEvents() { return worldChangeEvents; }
        public long resourceReloadEvents() { return resourceReloadEvents; }
        public long fixedAnchorChunkLoadEvents() { return fixedAnchorChunkLoadEvents; }
        public long fixedAnchorChunkUnloadEvents() { return fixedAnchorChunkUnloadEvents; }
        public int lastRelevantEventCount() { return lastRelevantEventCount; }
    }

    public static synchronized long latestSequence() {
        return relevantSequence;
    }

    /** Immediately updates the renderer-owned tracked scene identity. */
    public static synchronized void bindTrackedScene(
            boolean rendererTargetKnown,
            int sectionX,
            int sectionY,
            int sectionZ) {
        targetKnown = rendererTargetKnown;
        if (rendererTargetKnown) {
            targetSectionX = sectionX;
            targetSectionY = sectionY;
            targetSectionZ = sectionZ;
            if (!fixedAnchorKnown) {
                fixedAnchorKnown = true;
                fixedAnchorChunkX = sectionX;
                fixedAnchorChunkZ = sectionZ;
            }
        }
    }

    /** Called from the exact vanilla LevelExtractor dirty sink. */
    public static synchronized void sectionDirty(
            int sectionX,
            int sectionY,
            int sectionZ,
            boolean dirtyFromPlayer) {
        if (!isRenderedSceneSection(sectionX, sectionY, sectionZ)) return;
        relevantSequence++;
        sectionDirtyEvents++;
        if (dirtyFromPlayer) playerDirtyEvents++;
    }

    public static synchronized void chunkLoaded(int chunkX, int chunkZ) {
        boolean sceneRelevant = isSceneHaloChunk(chunkX, chunkZ);
        boolean anchorRelevant = isFixedAnchorHaloChunk(chunkX, chunkZ);
        if (anchorRelevant) fixedAnchorChunkLoadEvents++;
        if (!sceneRelevant) return;
        relevantSequence++;
        chunkLoadEvents++;
    }

    public static synchronized void chunkUnloaded(int chunkX, int chunkZ) {
        boolean sceneRelevant = isSceneHaloChunk(chunkX, chunkZ);
        boolean anchorRelevant = isFixedAnchorHaloChunk(chunkX, chunkZ);
        if (anchorRelevant) fixedAnchorChunkUnloadEvents++;
        if (!sceneRelevant) return;
        relevantSequence++;
        chunkUnloadEvents++;
    }

    /** World replacement/teardown invalidates any previous scene and fixed anchor immediately. */
    public static synchronized void worldChanged() {
        relevantSequence++;
        worldChangeEvents++;
        targetKnown = false;
        fixedAnchorKnown = false;
    }

    public static synchronized void resourceReloaded() {
        relevantSequence++;
        resourceReloadEvents++;
    }

    private static boolean isRenderedSceneSection(int sectionX, int sectionY, int sectionZ) {
        return targetKnown
                && sectionY == targetSectionY
                && Math.abs(sectionX - targetSectionX) <= SCENE_SECTION_RADIUS
                && Math.abs(sectionZ - targetSectionZ) <= SCENE_SECTION_RADIUS;
    }

    private static boolean isSceneHaloChunk(int chunkX, int chunkZ) {
        return targetKnown
                && Math.abs(chunkX - targetSectionX) <= SCENE_HALO_CHUNK_RADIUS
                && Math.abs(chunkZ - targetSectionZ) <= SCENE_HALO_CHUNK_RADIUS;
    }

    private static boolean isFixedAnchorHaloChunk(int chunkX, int chunkZ) {
        return fixedAnchorKnown
                && Math.abs(chunkX - fixedAnchorChunkX) <= FIXED_LIFECYCLE_HALO_CHUNK_RADIUS
                && Math.abs(chunkZ - fixedAnchorChunkZ) <= FIXED_LIFECYCLE_HALO_CHUNK_RADIUS;
    }

    /**
     * Drains sticky counters by delta and synchronizes the bridge target with
     * the scene coordinator. A pending world change wins over the coordinator's
     * previous target so teardown cannot rebind stale coordinates for one frame.
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
        if (playerDelta > 0L) cursor.playerDirtyEvents = playerDirtyEvents;

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

        cursor.fixedAnchorChunkLoadEvents = fixedAnchorChunkLoadEvents;
        cursor.fixedAnchorChunkUnloadEvents = fixedAnchorChunkUnloadEvents;
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
        appendReason(out, reasons, REASON_SCENE_RECENTER, "scene-recenter");
        return out.toString();
    }

    private static void appendReason(StringBuilder out, int reasons, int mask, String name) {
        if ((reasons & mask) == 0) return;
        if (!out.isEmpty()) out.append('|');
        out.append(name);
    }
}
