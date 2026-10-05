package dev.obsidian.render.visibility;

import net.minecraft.core.SectionPos;

/**
 * Fixed-capacity primitive lifecycle ring for the Phase 4 shadow scene.
 *
 * <p>Producers may arrive from Minecraft/Fabric callbacks that are not useful
 * places to mutate renderer-owned scene state directly. The render thread
 * drains this bounded ring. Overflow is explicit and forces a conservative
 * full scene resync; events are never silently dropped.</p>
 */
public final class LargeSceneLifecycleEvents {
    public static final byte WORLD_CHANGED = 1;
    public static final byte RESOURCE_RELOADED = 2;
    public static final byte CHUNK_LOADED = 3;
    public static final byte CHUNK_UNLOADED = 4;
    public static final byte SECTION_BECAME_EMPTY = 5;
    public static final byte SECTION_BECAME_NONEMPTY = 6;

    private static final int CAPACITY = 32 * 1024;
    private static final BoundedLifecycleEventRing RING = new BoundedLifecycleEventRing(CAPACITY);

    private LargeSceneLifecycleEvents() {}

    public static void worldChanged() {
        record(WORLD_CHANGED, 0L);
    }

    public static void resourceReloaded() {
        record(RESOURCE_RELOADED, 0L);
    }

    public static void chunkLoaded(int x, int z) {
        record(CHUNK_LOADED, packChunk(x, z));
    }

    public static void chunkUnloaded(int x, int z) {
        record(CHUNK_UNLOADED, packChunk(x, z));
    }

    public static void sectionEmptinessChanged(int x, int y, int z, boolean hasOnlyAir) {
        record(hasOnlyAir ? SECTION_BECAME_EMPTY : SECTION_BECAME_NONEMPTY, SectionPos.asLong(x, y, z));
    }

    public static int drainTo(byte[] types, long[] keys, int maxEvents) {
        return RING.drainTo(types, keys, maxEvents);
    }

    public static boolean consumeOverflowed() {
        return RING.consumeOverflowed();
    }

    public static long overflowEvents() {
        return RING.overflowEvents();
    }

    public static long recordedEvents() {
        return RING.recordedEvents();
    }

    public static int chunkX(long packed) {
        return (int) (packed >> 32);
    }

    public static int chunkZ(long packed) {
        return (int) packed;
    }

    private static void record(byte type, long key) {
        RING.record(type, key);
    }

    private static long packChunk(int x, int z) {
        return ((long) x << 32) | (z & 0xffff_ffffL);
    }
}
