package dev.obsidian.render.visibility;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;

/**
 * P4.2 bounded persistent chunk-column hierarchy derived from the exact P4.1
 * non-empty section membership stream.
 */
public final class PersistentColumnHierarchy {
    public static final int HARD_MAX_COLUMNS = 131_072;

    private final int capacity;
    private final int minSectionY;
    private final int maxSectionY;
    private final int sectionCount;
    private final int wordsPerColumn;

    private final int[] chunkX;
    private final int[] chunkZ;
    private final int[] identity;
    private final int[] liveSectionCount;
    private final int[] minLiveSectionY;
    private final int[] maxLiveSectionY;
    private final byte[] live;
    private final byte[] everUsed;
    private final int[] freeSlots;
    private final long[] occupancy;
    private final Long2IntOpenHashMap slotByColumn;

    private int freeTop;
    private int liveCount;
    private int highWater;
    private int totalLiveSections;
    private int nextIdentity = 1;
    private long serial = 1L;
    private long installs;
    private long removals;
    private long slotReuses;
    private long sectionMembershipAdds;
    private long sectionMembershipRemovals;
    private long capacityFailures;
    private long mutationFailures;
    private long mutationCalls;
    private long mutationNs;

    public PersistentColumnHierarchy(int capacity, int minSectionY, int maxSectionY) {
        if (capacity <= 0 || capacity > HARD_MAX_COLUMNS) {
            throw new IllegalArgumentException("P4.2 column capacity outside hard bound: " + capacity);
        }
        if (maxSectionY <= minSectionY) {
            throw new IllegalArgumentException("P4.2 invalid vertical section range");
        }
        this.capacity = capacity;
        this.minSectionY = minSectionY;
        this.maxSectionY = maxSectionY;
        this.sectionCount = Math.subtractExact(maxSectionY, minSectionY);
        this.wordsPerColumn = Math.addExact(sectionCount, Long.SIZE - 1) / Long.SIZE;
        int occupancyWords = Math.multiplyExact(capacity, wordsPerColumn);

        this.chunkX = new int[capacity];
        this.chunkZ = new int[capacity];
        this.identity = new int[capacity];
        this.liveSectionCount = new int[capacity];
        this.minLiveSectionY = new int[capacity];
        this.maxLiveSectionY = new int[capacity];
        this.live = new byte[capacity];
        this.everUsed = new byte[capacity];
        this.freeSlots = new int[capacity];
        this.occupancy = new long[occupancyWords];
        for (int i = 0; i < capacity; i++) freeSlots[i] = capacity - 1 - i;
        this.freeTop = capacity;
        this.slotByColumn = new Long2IntOpenHashMap(capacity, 0.75f);
        this.slotByColumn.defaultReturnValue(-1);
    }

    public boolean addSection(int x, int y, int z) {
        long start = System.nanoTime();
        mutationCalls++;
        try {
            int bit = sectionBit(y);
            long key = columnKey(x, z);
            int slot = slotByColumn.get(key);
            if (slot < 0) {
                if (freeTop == 0) {
                    capacityFailures++;
                    return false;
                }
                slot = freeSlots[--freeTop];
                if (everUsed[slot] != 0) slotReuses++;
                everUsed[slot] = 1;
                live[slot] = 1;
                chunkX[slot] = x;
                chunkZ[slot] = z;
                identity[slot] = allocateIdentity();
                liveSectionCount[slot] = 0;
                minLiveSectionY[slot] = y;
                maxLiveSectionY[slot] = y;
                clearOccupancy(slot);
                slotByColumn.put(key, slot);
                liveCount++;
                installs++;
                if (liveCount > highWater) highWater = liveCount;
            }

            int word = bit >>> 6;
            long mask = 1L << (bit & 63);
            int occupancyIndex = occupancyIndex(slot, word);
            if ((occupancy[occupancyIndex] & mask) != 0L) return true;

            occupancy[occupancyIndex] |= mask;
            int previousCount = liveSectionCount[slot]++;
            if (previousCount == 0) {
                minLiveSectionY[slot] = y;
                maxLiveSectionY[slot] = y;
            } else {
                if (y < minLiveSectionY[slot]) minLiveSectionY[slot] = y;
                if (y > maxLiveSectionY[slot]) maxLiveSectionY[slot] = y;
            }
            totalLiveSections++;
            sectionMembershipAdds++;
            serial++;
            return true;
        } finally {
            mutationNs += System.nanoTime() - start;
        }
    }

    public boolean removeSection(int x, int y, int z) {
        long start = System.nanoTime();
        mutationCalls++;
        try {
            if (y < minSectionY || y >= maxSectionY) return false;
            int slot = slotByColumn.get(columnKey(x, z));
            if (slot < 0) return false;

            int bit = y - minSectionY;
            int word = bit >>> 6;
            long mask = 1L << (bit & 63);
            int occupancyIndex = occupancyIndex(slot, word);
            if ((occupancy[occupancyIndex] & mask) == 0L) return false;

            occupancy[occupancyIndex] &= ~mask;
            liveSectionCount[slot]--;
            totalLiveSections--;
            sectionMembershipRemovals++;
            serial++;

            if (liveSectionCount[slot] == 0) {
                removeSlot(slot);
            } else if (y == minLiveSectionY[slot] || y == maxLiveSectionY[slot]) {
                recomputeBounds(slot);
            }
            return true;
        } finally {
            mutationNs += System.nanoTime() - start;
        }
    }

    public int removeColumn(int x, int z) {
        long start = System.nanoTime();
        mutationCalls++;
        try {
            int slot = slotByColumn.get(columnKey(x, z));
            if (slot < 0) return 0;
            int removed = liveSectionCount[slot];
            totalLiveSections -= removed;
            sectionMembershipRemovals += removed;
            clearOccupancy(slot);
            serial++;
            removeSlot(slot);
            return removed;
        } finally {
            mutationNs += System.nanoTime() - start;
        }
    }

    public void clear() {
        long start = System.nanoTime();
        mutationCalls++;
        try {
            slotByColumn.clear();
            Arrays.fill(live, (byte) 0);
            Arrays.fill(identity, 0);
            Arrays.fill(liveSectionCount, 0);
            Arrays.fill(minLiveSectionY, 0);
            Arrays.fill(maxLiveSectionY, 0);
            Arrays.fill(occupancy, 0L);
            for (int i = 0; i < capacity; i++) freeSlots[i] = capacity - 1 - i;
            freeTop = capacity;
            liveCount = 0;
            totalLiveSections = 0;
            serial++;
        } finally {
            mutationNs += System.nanoTime() - start;
        }
    }

    public int columnSlot(int x, int z) {
        return slotByColumn.get(columnKey(x, z));
    }

    public boolean containsSection(int slot, int y) {
        if (!isLive(slot) || y < minSectionY || y >= maxSectionY) return false;
        int bit = y - minSectionY;
        int word = bit >>> 6;
        long mask = 1L << (bit & 63);
        return (occupancy[occupancyIndex(slot, word)] & mask) != 0L;
    }

    public boolean auditColumn(int slot) {
        if (!isLive(slot)) return true;
        int expectedCount = liveSectionCount[slot];
        if (expectedCount <= 0) return false;

        int count = 0;
        int firstBit = -1;
        int lastBit = -1;
        for (int word = 0; word < wordsPerColumn; word++) {
            long bits = occupancy[occupancyIndex(slot, word)];
            if (bits == 0L) continue;
            count += Long.bitCount(bits);
            int first = (word << 6) + Long.numberOfTrailingZeros(bits);
            int last = (word << 6) + (Long.SIZE - 1 - Long.numberOfLeadingZeros(bits));
            if (firstBit < 0 || first < firstBit) firstBit = first;
            if (last > lastBit) lastBit = last;
        }
        if (count != expectedCount || firstBit < 0 || lastBit < 0
                || firstBit >= sectionCount || lastBit >= sectionCount) {
            return false;
        }
        return minLiveSectionY[slot] == minSectionY + firstBit
                && maxLiveSectionY[slot] == minSectionY + lastBit;
    }

    public void recordMutationFailure() {
        mutationFailures++;
    }

    public int capacity() { return capacity; }
    public int minSectionY() { return minSectionY; }
    public int maxSectionY() { return maxSectionY; }
    public int sectionCount() { return sectionCount; }
    public int wordsPerColumn() { return wordsPerColumn; }
    public int liveCount() { return liveCount; }
    public int highWater() { return highWater; }
    public int liveSectionMembership() { return totalLiveSections; }
    public long serial() { return serial; }
    public long installs() { return installs; }
    public long removals() { return removals; }
    public long slotReuses() { return slotReuses; }
    public long sectionMembershipAdds() { return sectionMembershipAdds; }
    public long sectionMembershipRemovals() { return sectionMembershipRemovals; }
    public long capacityFailures() { return capacityFailures; }
    public long mutationFailures() { return mutationFailures; }
    public long mutationCalls() { return mutationCalls; }
    public long mutationNs() { return mutationNs; }

    public long metadataBytes() {
        return (long) capacity * (Integer.BYTES * 7L + 2L);
    }

    public long occupancyBytes() {
        return (long) occupancy.length * Long.BYTES;
    }

    public boolean isLive(int slot) { return slot >= 0 && slot < capacity && live[slot] != 0; }
    public int chunkX(int slot) { return chunkX[slot]; }
    public int chunkZ(int slot) { return chunkZ[slot]; }
    public int identity(int slot) { return identity[slot]; }
    public int liveSectionCount(int slot) { return liveSectionCount[slot]; }
    public int minLiveSectionY(int slot) { return minLiveSectionY[slot]; }
    public int maxLiveSectionY(int slot) { return maxLiveSectionY[slot]; }

    private int sectionBit(int y) {
        if (y < minSectionY || y >= maxSectionY) {
            throw new IllegalArgumentException("P4.2 section Y outside configured hierarchy: " + y);
        }
        return y - minSectionY;
    }

    private int occupancyIndex(int slot, int word) {
        return Math.addExact(Math.multiplyExact(slot, wordsPerColumn), word);
    }

    private void recomputeBounds(int slot) {
        int firstBit = -1;
        int lastBit = -1;
        for (int word = 0; word < wordsPerColumn; word++) {
            long bits = occupancy[occupancyIndex(slot, word)];
            if (bits == 0L) continue;
            int first = (word << 6) + Long.numberOfTrailingZeros(bits);
            int last = (word << 6) + (Long.SIZE - 1 - Long.numberOfLeadingZeros(bits));
            if (firstBit < 0 || first < firstBit) firstBit = first;
            if (last > lastBit) lastBit = last;
        }
        if (firstBit < 0 || lastBit < 0) {
            mutationFailures++;
            return;
        }
        minLiveSectionY[slot] = minSectionY + firstBit;
        maxLiveSectionY[slot] = minSectionY + lastBit;
    }

    private void removeSlot(int slot) {
        slotByColumn.remove(columnKey(chunkX[slot], chunkZ[slot]));
        live[slot] = 0;
        chunkX[slot] = 0;
        chunkZ[slot] = 0;
        identity[slot] = 0;
        liveSectionCount[slot] = 0;
        minLiveSectionY[slot] = 0;
        maxLiveSectionY[slot] = 0;
        freeSlots[freeTop++] = slot;
        liveCount--;
        removals++;
    }

    private void clearOccupancy(int slot) {
        int base = Math.multiplyExact(slot, wordsPerColumn);
        Arrays.fill(occupancy, base, base + wordsPerColumn, 0L);
    }

    private int allocateIdentity() {
        int value = nextIdentity++;
        if (value == 0) value = nextIdentity++;
        return value;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffff_ffffL);
    }
}
