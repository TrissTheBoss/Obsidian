package dev.obsidian.render.visibility;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.SectionPos;

import java.util.Arrays;

/** Bounded primitive persistent section metadata database for P4.1 shadow visibility. */
public final class PersistentSectionScene {
    public static final int HARD_MAX_SLOTS = 2_500_000;

    private final int capacity;
    private final int[] sectionX;
    private final int[] sectionY;
    private final int[] sectionZ;
    private final int[] identity;
    private final byte[] live;
    private final byte[] everUsed;
    private final int[] freeSlots;
    private final Long2IntOpenHashMap slotBySection;

    private int freeTop;
    private int liveCount;
    private int highWater;
    private int nextIdentity = 1;
    private long serial = 1L;
    private long installs;
    private long removals;
    private long slotReuses;
    private long capacityFailures;

    public PersistentSectionScene(int capacity) {
        if (capacity <= 0 || capacity > HARD_MAX_SLOTS) {
            throw new IllegalArgumentException("P4.1 scene capacity outside hard bound: " + capacity);
        }
        this.capacity = capacity;
        this.sectionX = new int[capacity];
        this.sectionY = new int[capacity];
        this.sectionZ = new int[capacity];
        this.identity = new int[capacity];
        this.live = new byte[capacity];
        this.everUsed = new byte[capacity];
        this.freeSlots = new int[capacity];
        for (int i = 0; i < capacity; i++) freeSlots[i] = capacity - 1 - i;
        this.freeTop = capacity;
        this.slotBySection = new Long2IntOpenHashMap(capacity, 0.75f);
        this.slotBySection.defaultReturnValue(-1);
    }

    public boolean add(int x, int y, int z) {
        long key = SectionPos.asLong(x, y, z);
        int existing = slotBySection.get(key);
        if (existing >= 0) return true;
        if (freeTop == 0) {
            capacityFailures++;
            return false;
        }
        int slot = freeSlots[--freeTop];
        if (everUsed[slot] != 0) slotReuses++;
        everUsed[slot] = 1;
        live[slot] = 1;
        sectionX[slot] = x;
        sectionY[slot] = y;
        sectionZ[slot] = z;
        identity[slot] = allocateIdentity();
        slotBySection.put(key, slot);
        liveCount++;
        installs++;
        serial++;
        if (liveCount > highWater) highWater = liveCount;
        return true;
    }

    public boolean remove(int x, int y, int z) {
        long key = SectionPos.asLong(x, y, z);
        int slot = slotBySection.remove(key);
        if (slot < 0) return false;
        live[slot] = 0;
        sectionX[slot] = 0;
        sectionY[slot] = 0;
        sectionZ[slot] = 0;
        identity[slot] = 0;
        freeSlots[freeTop++] = slot;
        liveCount--;
        removals++;
        serial++;
        return true;
    }

    public int removeColumn(int x, int z, int minSectionY, int maxSectionY) {
        int removed = 0;
        for (int y = minSectionY; y < maxSectionY; y++) {
            if (remove(x, y, z)) removed++;
        }
        return removed;
    }

    public void clear() {
        slotBySection.clear();
        Arrays.fill(live, (byte) 0);
        Arrays.fill(identity, 0);
        for (int i = 0; i < capacity; i++) freeSlots[i] = capacity - 1 - i;
        freeTop = capacity;
        liveCount = 0;
        serial++;
    }

    public int capacity() { return capacity; }
    public int liveCount() { return liveCount; }
    public int highWater() { return highWater; }
    public long serial() { return serial; }
    public long installs() { return installs; }
    public long removals() { return removals; }
    public long slotReuses() { return slotReuses; }
    public long capacityFailures() { return capacityFailures; }
    public long metadataBytes() {
        return (long) capacity * (Integer.BYTES * 5L + 2L);
    }

    public boolean isLive(int slot) { return slot >= 0 && slot < capacity && live[slot] != 0; }
    public int sectionX(int slot) { return sectionX[slot]; }
    public int sectionY(int slot) { return sectionY[slot]; }
    public int sectionZ(int slot) { return sectionZ[slot]; }
    public int identity(int slot) { return identity[slot]; }

    private int allocateIdentity() {
        int value = nextIdentity++;
        if (value == 0) value = nextIdentity++;
        return value;
    }
}
