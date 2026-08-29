package dev.obsidian.render.terrain;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * P3.9 dev16 bounded primitive bridge from exact block-local client dirty events
 * to the four fixed shadow slice identities. It never changes production
 * invalidation: absence, ambiguity or overflow only forces experimental fallback.
 */
public final class PartialRemeshDirtyProvenance {
    public static final int SLICE_COUNT = 4;
    public static final int ALL_SLICES_MASK = 0xF;
    public static final int CAPACITY = 16;

    public static final int FLAG_XZ_BOUNDARY = 1;
    public static final int FLAG_OFF_RENDER_THREAD = 1 << 1;
    public static final int FLAG_OVERFLOW = 1 << 2;

    private static final Accumulator ACTIVE = new Accumulator(CAPACITY);
    private static boolean targetKnown;
    private static int targetSectionX;
    private static int targetSectionY;
    private static int targetSectionZ;

    private PartialRemeshDirtyProvenance() { }

    public static final class Drain {
        private final int[] sectionX = new int[CAPACITY];
        private final int[] sectionY = new int[CAPACITY];
        private final int[] sectionZ = new int[CAPACITY];
        private final byte[] sliceMask = new byte[CAPACITY];
        private final byte[] flags = new byte[CAPACITY];
        private final int[] editCount = new int[CAPACITY];
        private int count;
        private int fallbackFlags;
        private long overflowEvents;

        public int count() { return count; }
        public int sectionX(int i) { check(i); return sectionX[i]; }
        public int sectionY(int i) { check(i); return sectionY[i]; }
        public int sectionZ(int i) { check(i); return sectionZ[i]; }
        public int sliceMask(int i) { check(i); return Byte.toUnsignedInt(sliceMask[i]); }
        public int flags(int i) { check(i); return Byte.toUnsignedInt(flags[i]); }
        public int editCount(int i) { check(i); return editCount[i]; }
        public int fallbackFlags() { return fallbackFlags; }
        public long overflowEvents() { return overflowEvents; }
        private void check(int i) {
            if (i < 0 || i >= count) throw new IndexOutOfBoundsException(i);
        }
    }

    private static final class Accumulator {
        private final int capacity;
        private final int[] sectionX;
        private final int[] sectionY;
        private final int[] sectionZ;
        private final byte[] sliceMask;
        private final byte[] flags;
        private final int[] editCount;
        private int count;
        private int fallbackFlags;
        private long overflowEvents;

        Accumulator(int capacity) {
            this.capacity = capacity;
            sectionX = new int[capacity];
            sectionY = new int[capacity];
            sectionZ = new int[capacity];
            sliceMask = new byte[capacity];
            flags = new byte[capacity];
            editCount = new int[capacity];
        }

        void record(int sx, int sy, int sz, int mask, int recordFlags) {
            for (int i = 0; i < count; i++) {
                if (sectionX[i] == sx && sectionY[i] == sy && sectionZ[i] == sz) {
                    sliceMask[i] = (byte) (Byte.toUnsignedInt(sliceMask[i]) | mask);
                    flags[i] = (byte) (Byte.toUnsignedInt(flags[i]) | recordFlags);
                    if (editCount[i] != Integer.MAX_VALUE) editCount[i]++;
                    return;
                }
            }
            if (count >= capacity) {
                fallbackFlags |= FLAG_OVERFLOW;
                overflowEvents++;
                return;
            }
            sectionX[count] = sx;
            sectionY[count] = sy;
            sectionZ[count] = sz;
            sliceMask[count] = (byte) mask;
            flags[count] = (byte) recordFlags;
            editCount[count] = 1;
            count++;
        }

        void markFallback(int flag) { fallbackFlags |= flag; }

        void drain(Drain out) {
            out.count = count;
            out.fallbackFlags = fallbackFlags;
            out.overflowEvents = overflowEvents;
            for (int i = 0; i < count; i++) {
                out.sectionX[i] = sectionX[i];
                out.sectionY[i] = sectionY[i];
                out.sectionZ[i] = sectionZ[i];
                out.sliceMask[i] = sliceMask[i];
                out.flags[i] = flags[i];
                out.editCount[i] = editCount[i];
            }
            count = 0;
            fallbackFlags = 0;
            overflowEvents = 0L;
        }
    }

    public static synchronized void bindTrackedScene(boolean known, int sectionX, int sectionY, int sectionZ) {
        targetKnown = known;
        if (known) {
            targetSectionX = sectionX;
            targetSectionY = sectionY;
            targetSectionZ = sectionZ;
        } else {
            ACTIVE.count = 0;
            ACTIVE.fallbackFlags = 0;
            ACTIVE.overflowEvents = 0L;
        }
    }

    /** Exact ClientLevel block-dirty provenance. No live object is retained. */
    public static synchronized void blockDirty(BlockPos pos) {
        if (pos == null || !targetKnown) return;
        if (!RenderSystem.isOnRenderThread()) {
            ACTIVE.markFallback(FLAG_OFF_RENDER_THREAD);
            return;
        }
        int sx = SectionPos.blockToSectionCoord(pos.getX());
        int sy = SectionPos.blockToSectionCoord(pos.getY());
        int sz = SectionPos.blockToSectionCoord(pos.getZ());
        int dx = Math.abs(sx - targetSectionX);
        int dy = Math.abs(sy - targetSectionY);
        int dz = Math.abs(sz - targetSectionZ);
        if (dx > SectionLifecycleEvents.SCENE_HALO_SECTION_RADIUS
                || dy > SectionLifecycleEvents.SCENE_HALO_SECTION_Y_RADIUS
                || dz > SectionLifecycleEvents.SCENE_HALO_SECTION_RADIUS) return;

        int localX = Math.floorMod(pos.getX(), SectionSnapshot.INTERIOR_SIZE);
        int localY = Math.floorMod(pos.getY(), SectionSnapshot.INTERIOR_SIZE);
        int localZ = Math.floorMod(pos.getZ(), SectionSnapshot.INTERIOR_SIZE);
        int flags = (localX == 0 || localX == 15 || localZ == 0 || localZ == 15) ? FLAG_XZ_BOUNDARY : 0;
        ACTIVE.record(sx, sy, sz, expandedSliceMask(localY), flags);
    }

    public static synchronized void drainInto(Drain out) {
        if (out == null) throw new NullPointerException("drain");
        ACTIVE.drain(out);
    }

    public static int expandedSliceMask(int localY) {
        if (localY < 0 || localY >= SectionSnapshot.INTERIOR_SIZE) {
            throw new IllegalArgumentException("localY outside 0..15");
        }
        int slice = localY >>> 2;
        int mask = 1 << slice;
        int row = localY & 3;
        if (row == 0 && slice > 0) mask |= 1 << (slice - 1);
        if (row == 3 && slice + 1 < SLICE_COUNT) mask |= 1 << (slice + 1);
        return mask;
    }

    public static boolean selfTest() {
        if (expandedSliceMask(1) != 0x1 || expandedSliceMask(5) != 0x2
                || expandedSliceMask(9) != 0x4 || expandedSliceMask(13) != 0x8
                || expandedSliceMask(3) != 0x3 || expandedSliceMask(4) != 0x3
                || expandedSliceMask(7) != 0x6 || expandedSliceMask(8) != 0x6
                || expandedSliceMask(11) != 0xC || expandedSliceMask(12) != 0xC) return false;
        Accumulator test = new Accumulator(2);
        test.record(1, 2, 3, 0x1, 0);
        test.record(1, 2, 3, 0x2, FLAG_XZ_BOUNDARY);
        test.record(4, 5, 6, 0x4, 0);
        test.record(7, 8, 9, 0x8, 0);
        Drain drain = new Drain();
        test.drain(drain);
        return drain.count() == 2
                && drain.sliceMask(0) == 0x3
                && drain.editCount(0) == 2
                && (drain.flags(0) & FLAG_XZ_BOUNDARY) != 0
                && (drain.fallbackFlags() & FLAG_OVERFLOW) != 0
                && drain.overflowEvents() == 1L;
    }
}
