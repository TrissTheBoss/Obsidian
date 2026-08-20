package dev.obsidian.render.memory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.function.Supplier;

/**
 * Fixed-capacity, device-preferred GPU geometry arena with generation-safe
 * allocation handles and completion-gated reuse.
 *
 * <p>The backing buffer intentionally has no MAP_READ/MAP_WRITE usage. On the
 * Minecraft 26.2 Vulkan backend this keeps VMA on its device-preferred path;
 * data reaches the arena through Obsidian's separate staging upload system.</p>
 *
 * <p>Allocation metadata is preallocated. Free spans are kept sorted by GPU
 * offset and are coalesced on release. Allocation uses best-fit selection to
 * limit external fragmentation during this Phase 1 foundation.</p>
 */
public final class DeviceGeometryArena implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/DeviceGeometryArena");

    public static final long INVALID_HANDLE = 0L;

    private static final byte SLOT_FREE = 0;
    private static final byte SLOT_LIVE = 1;
    private static final byte SLOT_PENDING = 2;

    private static final int MAX_ALLOCATIONS = 4096;
    private static final int MAX_FREE_SPANS = MAX_ALLOCATIONS + 1;
    private static final int MAX_RETIREMENT_BATCHES = 64;
    private static final int MAX_RETIREMENTS_PER_BATCH = 64;
    private static final int DEFAULT_POLL_BUDGET = 64;
    private static final long SHUTDOWN_WAIT_NS = 2_000_000_000L;

    private final GpuBuffer buffer;
    private final long capacityBytes;

    private final long[] slotOffsets = new long[MAX_ALLOCATIONS];
    private final int[] slotSizes = new int[MAX_ALLOCATIONS];
    private final int[] slotGenerations = new int[MAX_ALLOCATIONS];
    private final byte[] slotStates = new byte[MAX_ALLOCATIONS];
    private final int[] freeSlots = new int[MAX_ALLOCATIONS];
    private int freeSlotCount = MAX_ALLOCATIONS;

    private final long[] freeOffsets = new long[MAX_FREE_SPANS];
    private final long[] freeSizes = new long[MAX_FREE_SPANS];
    private int freeSpanCount = 1;

    private final GpuFence[] retirementFences = new GpuFence[MAX_RETIREMENT_BATCHES];
    private final int[] retirementCounts = new int[MAX_RETIREMENT_BATCHES];
    private final long[] retirementHandles =
            new long[MAX_RETIREMENT_BATCHES * MAX_RETIREMENTS_PER_BATCH];
    private int retirementHead;
    private int retirementBatchCount;

    private long usedBytes;
    private long highWaterBytes;
    private int allocatedCount;
    private int pendingFreeCount;

    private long successfulAllocations;
    private long allocationFailures;
    private long cancelledAllocations;
    private long retiredAllocations;
    private long reclaimedAllocations;
    private long retirementBackpressureEvents;
    private long staleHandleRejections;

    private boolean closed;
    private boolean abandonedForDeviceShutdown;

    public DeviceGeometryArena(GpuDevice device, Supplier<String> label, long capacityBytes) {
        RenderSystem.assertOnRenderThread();
        if (capacityBytes <= 0L) {
            throw new IllegalArgumentException("Device arena capacity must be positive");
        }

        this.capacityBytes = capacityBytes;
        this.buffer = device.createBuffer(
                label,
                GpuBuffer.USAGE_COPY_DST
                        | GpuBuffer.USAGE_COPY_SRC
                        | GpuBuffer.USAGE_VERTEX
                        | GpuBuffer.USAGE_INDEX,
                capacityBytes);

        freeOffsets[0] = 0L;
        freeSizes[0] = capacityBytes;
        for (int i = 0; i < MAX_ALLOCATIONS; i++) {
            freeSlots[i] = MAX_ALLOCATIONS - 1 - i;
        }
    }

    /**
     * Allocates one aligned span. Returns {@link #INVALID_HANDLE} rather than
     * growing the arena or allocating a fallback buffer when capacity or
     * metadata is exhausted.
     */
    public long allocate(int sizeBytes, int alignment) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Allocation size must be positive");
        }
        if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("Allocation alignment must be a positive power of two");
        }
        if (freeSlotCount == 0) {
            allocationFailures++;
            return INVALID_HANDLE;
        }

        int bestIndex = -1;
        long bestAlignedOffset = 0L;
        long bestWaste = Long.MAX_VALUE;

        for (int i = 0; i < freeSpanCount; i++) {
            long spanOffset = freeOffsets[i];
            long spanSize = freeSizes[i];
            long alignedOffset = alignUp(spanOffset, alignment);
            long end = alignedOffset + sizeBytes;
            long spanEnd = spanOffset + spanSize;
            if (end < alignedOffset || end > spanEnd) {
                continue;
            }

            long waste = spanSize - sizeBytes;
            if (waste < bestWaste) {
                bestIndex = i;
                bestAlignedOffset = alignedOffset;
                bestWaste = waste;
            }
        }

        if (bestIndex < 0) {
            allocationFailures++;
            return INVALID_HANDLE;
        }

        long spanOffset = freeOffsets[bestIndex];
        long spanEnd = spanOffset + freeSizes[bestIndex];
        long allocationEnd = bestAlignedOffset + sizeBytes;
        long prefix = bestAlignedOffset - spanOffset;
        long suffix = spanEnd - allocationEnd;

        if (prefix > 0L && suffix > 0L) {
            freeSizes[bestIndex] = prefix;
            insertFreeSpan(bestIndex + 1, allocationEnd, suffix);
        } else if (prefix > 0L) {
            freeSizes[bestIndex] = prefix;
        } else if (suffix > 0L) {
            freeOffsets[bestIndex] = allocationEnd;
            freeSizes[bestIndex] = suffix;
        } else {
            removeFreeSpan(bestIndex);
        }

        int slot = freeSlots[--freeSlotCount];
        int generation = nextGeneration(slotGenerations[slot]);
        slotGenerations[slot] = generation;
        slotOffsets[slot] = bestAlignedOffset;
        slotSizes[slot] = sizeBytes;
        slotStates[slot] = SLOT_LIVE;

        allocatedCount++;
        usedBytes += sizeBytes;
        successfulAllocations++;
        if (usedBytes > highWaterBytes) {
            highWaterBytes = usedBytes;
        }

        return packHandle(slot, generation);
    }

    /**
     * Releases an allocation that has never been referenced by submitted GPU
     * work. This is intended for upload/admission rollback only.
     */
    public void cancelUnsubmitted(long handle) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        int slot = requireHandleState(handle, SLOT_LIVE);
        releaseSlot(slot);
        cancelledAllocations++;
    }

    /**
     * Transfers ownership of a completed-use fence and a batch of live
     * allocations to the arena. The allocations become unavailable
     * immediately and their spans are only returned to the free list after the
     * fence reports completion.
     *
     * <p>On false, ownership of {@code fence} remains with the caller and no
     * allocation state changes. Validation and steady-state retirement do not
     * allocate temporary Java objects.</p>
     */
    public boolean retireBatch(GpuFence fence, long[] handles, int count) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        if (fence == null || handles == null) {
            throw new NullPointerException("fence and handles are required");
        }
        if (count <= 0 || count > MAX_RETIREMENTS_PER_BATCH || count > handles.length) {
            throw new IllegalArgumentException("Invalid retirement batch size: " + count);
        }
        if (retirementBatchCount == MAX_RETIREMENT_BATCHES) {
            retirementBackpressureEvents++;
            return false;
        }

        for (int i = 0; i < count; i++) {
            long handle = handles[i];
            requireHandleState(handle, SLOT_LIVE);
            for (int j = 0; j < i; j++) {
                if (handles[j] == handle) {
                    throw new IllegalArgumentException("Duplicate allocation in retirement batch");
                }
            }
        }

        int tail = retirementHead + retirementBatchCount;
        if (tail >= MAX_RETIREMENT_BATCHES) {
            tail -= MAX_RETIREMENT_BATCHES;
        }
        int base = tail * MAX_RETIREMENTS_PER_BATCH;

        for (int i = 0; i < count; i++) {
            long handle = handles[i];
            retirementHandles[base + i] = handle;
            slotStates[decodeSlot(handle)] = SLOT_PENDING;
        }
        retirementFences[tail] = fence;
        retirementCounts[tail] = count;
        retirementBatchCount++;
        pendingFreeCount += count;
        retiredAllocations += count;
        return true;
    }

    public int pollRetirements() {
        return pollRetirements(DEFAULT_POLL_BUDGET);
    }

    public int pollRetirements(int budget) {
        RenderSystem.assertOnRenderThread();
        if (closed || retirementBatchCount == 0 || budget <= 0) {
            return 0;
        }

        int reclaimedBatches = 0;
        while (retirementBatchCount > 0 && reclaimedBatches < budget) {
            GpuFence fence = retirementFences[retirementHead];
            boolean complete;
            try {
                complete = fence.awaitCompletion(0L);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR,
                        "Device arena retirement fence polling failed; leaving the oldest batch pending.",
                        e);
                break;
            }
            if (!complete) {
                break;
            }

            reclaimRetirementHead();
            reclaimedBatches++;
        }
        return reclaimedBatches;
    }

    /** Returns a validated slice for a live allocation. Pending/stale handles are rejected. */
    public GpuBufferSlice slice(long handle) {
        RenderSystem.assertOnRenderThread();
        int slot = requireHandleState(handle, SLOT_LIVE);
        return buffer.slice(slotOffsets[slot], slotSizes[slot]);
    }

    public boolean isAllocated(long handle) {
        int slot = decodeSlot(handle);
        if (slot < 0 || slot >= MAX_ALLOCATIONS) {
            return false;
        }
        return slotGenerations[slot] == decodeGeneration(handle) && slotStates[slot] != SLOT_FREE;
    }

    public boolean isLive(long handle) {
        int slot = decodeSlot(handle);
        if (slot < 0 || slot >= MAX_ALLOCATIONS) {
            return false;
        }
        return slotGenerations[slot] == decodeGeneration(handle) && slotStates[slot] == SLOT_LIVE;
    }

    public long offset(long handle) {
        int slot = requireHandleState(handle, SLOT_LIVE);
        return slotOffsets[slot];
    }

    public int size(long handle) {
        int slot = requireHandleState(handle, SLOT_LIVE);
        return slotSizes[slot];
    }

    public long capacityBytes() {
        return capacityBytes;
    }

    public long usedBytes() {
        return usedBytes;
    }

    public long freeBytes() {
        return capacityBytes - usedBytes;
    }

    public long highWaterBytes() {
        return highWaterBytes;
    }

    public int allocatedCount() {
        return allocatedCount;
    }

    public int liveCount() {
        return allocatedCount - pendingFreeCount;
    }

    public int pendingFreeCount() {
        return pendingFreeCount;
    }

    public int pendingRetirementBatches() {
        return retirementBatchCount;
    }

    public int freeSpanCount() {
        return freeSpanCount;
    }

    public long largestFreeBlockBytes() {
        long largest = 0L;
        for (int i = 0; i < freeSpanCount; i++) {
            if (freeSizes[i] > largest) {
                largest = freeSizes[i];
            }
        }
        return largest;
    }

    /** External-fragmentation estimate in thousandths: 0 = one contiguous free span. */
    public int fragmentationPermille() {
        long free = freeBytes();
        if (free <= 0L) {
            return 0;
        }
        long fragmented = free - largestFreeBlockBytes();
        return (int) ((fragmented * 1000L) / free);
    }

    public long successfulAllocations() {
        return successfulAllocations;
    }

    public long allocationFailures() {
        return allocationFailures;
    }

    public long cancelledAllocations() {
        return cancelledAllocations;
    }

    public long retiredAllocations() {
        return retiredAllocations;
    }

    public long reclaimedAllocations() {
        return reclaimedAllocations;
    }

    public long retirementBackpressureEvents() {
        return retirementBackpressureEvents;
    }

    public long staleHandleRejections() {
        return staleHandleRejections;
    }

    public boolean abandonedForDeviceShutdown() {
        return abandonedForDeviceShutdown;
    }

    public static int handleSlot(long handle) {
        return decodeSlot(handle);
    }

    public static int handleGeneration(long handle) {
        return decodeGeneration(handle);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }

        long deadline = System.nanoTime() + SHUTDOWN_WAIT_NS;
        while (retirementBatchCount > 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                abandonForDeviceShutdown("retirement shutdown deadline reached");
                closed = true;
                return;
            }

            GpuFence fence = retirementFences[retirementHead];
            boolean complete;
            try {
                complete = fence.awaitCompletion(remaining);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Device arena retirement wait failed during shutdown; leaving the arena for Minecraft device shutdown.",
                        e);
                abandonForDeviceShutdown("retirement fence failure");
                closed = true;
                return;
            }
            if (!complete) {
                abandonForDeviceShutdown("retirement completion timeout");
                closed = true;
                return;
            }
            reclaimRetirementHead();
        }

        if (allocatedCount != 0) {
            abandonForDeviceShutdown("live allocations remain without completion proof");
            closed = true;
            return;
        }

        closed = true;
        buffer.close();
    }

    private void reclaimRetirementHead() {
        int count = retirementCounts[retirementHead];
        int base = retirementHead * MAX_RETIREMENTS_PER_BATCH;
        GpuFence fence = retirementFences[retirementHead];

        for (int i = 0; i < count; i++) {
            long handle = retirementHandles[base + i];
            int slot = decodeSlot(handle);
            if (slot < 0
                    || slot >= MAX_ALLOCATIONS
                    || slotGenerations[slot] != decodeGeneration(handle)
                    || slotStates[slot] != SLOT_PENDING) {
                throw new IllegalStateException("Retirement metadata became inconsistent for handle " + handle);
            }
            retirementHandles[base + i] = INVALID_HANDLE;
            releaseSlot(slot);
            pendingFreeCount--;
            reclaimedAllocations++;
        }

        retirementFences[retirementHead] = null;
        retirementCounts[retirementHead] = 0;
        retirementHead++;
        if (retirementHead == MAX_RETIREMENT_BATCHES) {
            retirementHead = 0;
        }
        retirementBatchCount--;
        fence.close();
    }

    private void releaseSlot(int slot) {
        long offset = slotOffsets[slot];
        int size = slotSizes[slot];

        slotOffsets[slot] = 0L;
        slotSizes[slot] = 0;
        slotStates[slot] = SLOT_FREE;
        freeSlots[freeSlotCount++] = slot;
        allocatedCount--;
        usedBytes -= size;

        addAndCoalesceFreeSpan(offset, size);
    }

    private void addAndCoalesceFreeSpan(long offset, long size) {
        int insert = 0;
        while (insert < freeSpanCount && freeOffsets[insert] < offset) {
            insert++;
        }
        insertFreeSpan(insert, offset, size);

        int index = insert;
        if (index > 0 && freeOffsets[index - 1] + freeSizes[index - 1] == freeOffsets[index]) {
            freeSizes[index - 1] += freeSizes[index];
            removeFreeSpan(index);
            index--;
        }
        if (index + 1 < freeSpanCount
                && freeOffsets[index] + freeSizes[index] == freeOffsets[index + 1]) {
            freeSizes[index] += freeSizes[index + 1];
            removeFreeSpan(index + 1);
        }
    }

    private void insertFreeSpan(int index, long offset, long size) {
        if (freeSpanCount == MAX_FREE_SPANS) {
            throw new IllegalStateException("Device arena free-span metadata exhausted");
        }
        for (int i = freeSpanCount; i > index; i--) {
            freeOffsets[i] = freeOffsets[i - 1];
            freeSizes[i] = freeSizes[i - 1];
        }
        freeOffsets[index] = offset;
        freeSizes[index] = size;
        freeSpanCount++;
    }

    private void removeFreeSpan(int index) {
        for (int i = index; i + 1 < freeSpanCount; i++) {
            freeOffsets[i] = freeOffsets[i + 1];
            freeSizes[i] = freeSizes[i + 1];
        }
        freeSpanCount--;
        freeOffsets[freeSpanCount] = 0L;
        freeSizes[freeSpanCount] = 0L;
    }

    private int requireHandleState(long handle, byte requiredState) {
        int slot = decodeSlot(handle);
        int generation = decodeGeneration(handle);
        if (slot < 0
                || slot >= MAX_ALLOCATIONS
                || generation == 0
                || slotGenerations[slot] != generation
                || slotStates[slot] != requiredState) {
            staleHandleRejections++;
            throw new IllegalStateException("Stale or invalid device-arena allocation handle: " + handle);
        }
        return slot;
    }

    private void abandonForDeviceShutdown(String reason) {
        abandonedForDeviceShutdown = true;
        LOG.log(System.Logger.Level.WARNING,
                "Leaving device geometry arena for Minecraft device shutdown ({0}); allocated={1}, pendingFrees={2}, pendingBatches={3}.",
                reason, allocatedCount, pendingFreeCount, retirementBatchCount);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Device geometry arena is closed");
        }
    }

    private static long packHandle(int slot, int generation) {
        return ((long) generation << 32) | ((slot + 1L) & 0xFFFF_FFFFL);
    }

    private static int decodeSlot(long handle) {
        long low = handle & 0xFFFF_FFFFL;
        if (low == 0L || low > MAX_ALLOCATIONS) {
            return -1;
        }
        return (int) low - 1;
    }

    private static int decodeGeneration(long handle) {
        return (int) (handle >>> 32);
    }

    private static int nextGeneration(int current) {
        return current == Integer.MAX_VALUE ? 1 : current + 1;
    }

    private static long alignUp(long value, int alignment) {
        long mask = alignment - 1L;
        return (value + mask) & ~mask;
    }
}
