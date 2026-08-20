package dev.obsidian.render.upload;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Fixed-capacity, persistently mapped staging ring for Obsidian-owned uploads.
 *
 * <p>The ring uses monotonically increasing virtual cursors so alignment and
 * wrap padding are accounted for without ambiguity. Physical staging space is
 * never reused until the fence for the batch that consumed it has completed.
 * Normal polling always uses a zero timeout and therefore never intentionally
 * waits for the GPU.</p>
 */
public final class StagingUploadArena implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/StagingUploadArena");

    private static final int ALIGNMENT = 16;
    private static final int MAX_IN_FLIGHT_BATCHES = 64;
    private static final int DEFAULT_POLL_BUDGET = 64;
    private static final long SHUTDOWN_WAIT_NS = 2_000_000_000L;

    private final GpuBuffer stagingBuffer;
    private final GpuBufferSlice.MappedView mappedView;
    private final ByteBuffer mappedData;
    private final int capacityBytes;

    private final GpuFence[] batchFences = new GpuFence[MAX_IN_FLIGHT_BATCHES];
    private final long[] batchEndCursors = new long[MAX_IN_FLIGHT_BATCHES];
    private final long[] batchPayloadBytes = new long[MAX_IN_FLIGHT_BATCHES];

    private long writeCursor;
    private long reclaimCursor;
    private long openBatchStartCursor;
    private long openBatchPayloadBytes;

    private int batchHead;
    private int batchCount;
    private boolean batchOpen;
    private boolean closed;
    private boolean abandonedForDeviceShutdown;

    private long stagedBytes;
    private long submittedBytes;
    private long reclaimedBytes;
    private long highWaterBytes;
    private long backpressureEvents;
    private long submittedBatches;
    private long reclaimedBatches;

    public StagingUploadArena(GpuDevice device, Supplier<String> label, int capacityBytes) {
        RenderSystem.assertOnRenderThread();
        if (capacityBytes <= 0) {
            throw new IllegalArgumentException("Staging capacity must be positive");
        }
        if (!device.getDeviceInfo().features().persistentMapping()) {
            throw new IllegalStateException("Obsidian staging currently requires persistent GPU buffer mapping");
        }

        this.capacityBytes = capacityBytes;
        this.stagingBuffer = device.createBuffer(
                label,
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC,
                capacityBytes);
        this.mappedView = stagingBuffer.map(false, true);
        this.mappedData = mappedView.data();

        if (mappedData.capacity() < capacityBytes) {
            try {
                mappedView.close();
            } finally {
                stagingBuffer.close();
            }
            throw new IllegalStateException(
                    "Mapped staging capacity is smaller than the requested GPU buffer capacity");
        }
    }

    /** Starts one copy batch. Returns false rather than waiting if batch metadata is full. */
    public boolean beginBatch() {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        if (batchOpen) {
            throw new IllegalStateException("A staging upload batch is already open");
        }
        if (batchCount == MAX_IN_FLIGHT_BATCHES) {
            backpressureEvents++;
            return false;
        }

        batchOpen = true;
        openBatchStartCursor = writeCursor;
        openBatchPayloadBytes = 0L;
        return true;
    }

    public boolean stageCopy(
            CommandEncoder encoder,
            ByteBuffer source,
            GpuBuffer destination,
            long destinationOffset) {
        int size = source.remaining();
        if (destinationOffset < 0L || destinationOffset + size > destination.size()) {
            throw new IllegalArgumentException("Upload destination range is outside the destination buffer");
        }
        return stageCopy(encoder, source, destination.slice(destinationOffset, size));
    }

    /**
     * Copies source bytes into the mapped ring and records a buffer-to-buffer
     * copy into the supplied encoder. Returns false when bounded staging space
     * is unavailable; no fallback allocation or wait is performed.
     */
    public boolean stageCopy(
            CommandEncoder encoder,
            ByteBuffer source,
            GpuBufferSlice destination) {
        RenderSystem.assertOnRenderThread();
        ensureOpenBatch();

        int size = source.remaining();
        if (size <= 0) {
            throw new IllegalArgumentException("Upload size must be positive");
        }
        if (destination == null || destination.length() < size) {
            throw new IllegalArgumentException("Upload destination slice is smaller than the source payload");
        }

        long candidate = alignUp(writeCursor, ALIGNMENT);
        int physicalOffset = (int) (candidate % capacityBytes);
        if ((long) physicalOffset + size > capacityBytes) {
            candidate += capacityBytes - physicalOffset;
            physicalOffset = 0;
        }

        long endCursor = candidate + size;
        if (endCursor - reclaimCursor > capacityBytes) {
            backpressureEvents++;
            return false;
        }

        mappedData.put(physicalOffset, source, source.position(), size);
        encoder.copyToBuffer(
                stagingBuffer.slice(physicalOffset, size),
                destination.slice(0L, size));

        writeCursor = endCursor;
        openBatchPayloadBytes += size;
        stagedBytes += size;
        long used = usedBytes();
        if (used > highWaterBytes) {
            highWaterBytes = used;
        }
        return true;
    }

    /**
     * Creates the completion fence and submits the encoder exactly once. The
     * fence is then owned by this arena until its staging range is reclaimed.
     */
    public void submitBatch(CommandEncoder encoder) {
        RenderSystem.assertOnRenderThread();
        ensureOpenBatch();
        if (openBatchPayloadBytes == 0L) {
            throw new IllegalStateException("Cannot submit an empty staging batch");
        }
        if (batchCount == MAX_IN_FLIGHT_BATCHES) {
            throw new IllegalStateException("In-flight staging batch table unexpectedly became full");
        }

        GpuFence fence = encoder.createFence();
        try {
            encoder.submit();
        } catch (RuntimeException e) {
            try {
                fence.close();
            } catch (RuntimeException ignored) {
                // Preserve the submission failure as the useful diagnostic.
            }
            abortBatch();
            throw e;
        }

        int tail = batchHead + batchCount;
        if (tail >= MAX_IN_FLIGHT_BATCHES) {
            tail -= MAX_IN_FLIGHT_BATCHES;
        }
        batchFences[tail] = fence;
        batchEndCursors[tail] = writeCursor;
        batchPayloadBytes[tail] = openBatchPayloadBytes;
        batchCount++;

        submittedBytes += openBatchPayloadBytes;
        submittedBatches++;
        batchOpen = false;
        openBatchStartCursor = 0L;
        openBatchPayloadBytes = 0L;
    }

    /** Discards unsubmitted reservations. Mapped bytes may remain but are not GPU-visible work. */
    public void abortBatch() {
        RenderSystem.assertOnRenderThread();
        if (!batchOpen) {
            return;
        }
        writeCursor = openBatchStartCursor;
        batchOpen = false;
        openBatchStartCursor = 0L;
        openBatchPayloadBytes = 0L;
    }

    public int pollReclaims() {
        return pollReclaims(DEFAULT_POLL_BUDGET);
    }

    public int pollReclaims(int budget) {
        RenderSystem.assertOnRenderThread();
        if (closed || batchCount == 0 || budget <= 0) {
            return 0;
        }

        int reclaimed = 0;
        while (batchCount > 0 && reclaimed < budget) {
            GpuFence fence = batchFences[batchHead];
            boolean complete;
            try {
                complete = fence.awaitCompletion(0L);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR,
                        "Staging fence polling failed; leaving the oldest batch in flight.", e);
                break;
            }
            if (!complete) {
                break;
            }

            reclaimHead();
            reclaimed++;
        }
        return reclaimed;
    }

    public int capacityBytes() {
        return capacityBytes;
    }

    public long usedBytes() {
        return writeCursor - reclaimCursor;
    }

    public long availableBytes() {
        return capacityBytes - usedBytes();
    }

    public int pendingBatches() {
        return batchCount;
    }

    public long stagedBytes() {
        return stagedBytes;
    }

    public long submittedBytes() {
        return submittedBytes;
    }

    public long reclaimedBytes() {
        return reclaimedBytes;
    }

    public long highWaterBytes() {
        return highWaterBytes;
    }

    public long backpressureEvents() {
        return backpressureEvents;
    }

    public long submittedBatches() {
        return submittedBatches;
    }

    public long reclaimedBatches() {
        return reclaimedBatches;
    }

    public boolean abandonedForDeviceShutdown() {
        return abandonedForDeviceShutdown;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        if (batchOpen) {
            abortBatch();
        }

        long deadline = System.nanoTime() + SHUTDOWN_WAIT_NS;
        while (batchCount > 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                abandonForDeviceShutdown("shutdown deadline reached");
                closed = true;
                return;
            }

            GpuFence fence = batchFences[batchHead];
            boolean complete;
            try {
                complete = fence.awaitCompletion(remaining);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Staging fence wait failed during shutdown; leaving staging resources for Minecraft device shutdown.",
                        e);
                abandonForDeviceShutdown("shutdown fence failure");
                closed = true;
                return;
            }
            if (!complete) {
                abandonForDeviceShutdown("shutdown completion timeout");
                closed = true;
                return;
            }
            reclaimHead();
        }

        closed = true;
        mappedView.close();
        stagingBuffer.close();
    }

    private void reclaimHead() {
        GpuFence fence = batchFences[batchHead];
        reclaimCursor = batchEndCursors[batchHead];
        reclaimedBytes += batchPayloadBytes[batchHead];
        reclaimedBatches++;

        batchFences[batchHead] = null;
        batchEndCursors[batchHead] = 0L;
        batchPayloadBytes[batchHead] = 0L;
        batchHead++;
        if (batchHead == MAX_IN_FLIGHT_BATCHES) {
            batchHead = 0;
        }
        batchCount--;

        fence.close();
    }

    private void abandonForDeviceShutdown(String reason) {
        abandonedForDeviceShutdown = true;
        LOG.log(System.Logger.Level.WARNING,
                "Leaving {0} staging batch(es) and the mapped staging buffer for Minecraft device shutdown ({1}); in-flight memory will not be destroyed unsafely.",
                batchCount, reason);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Staging upload arena is closed");
        }
    }

    private void ensureOpenBatch() {
        ensureOpen();
        if (!batchOpen) {
            throw new IllegalStateException("No staging upload batch is open");
        }
    }

    private static long alignUp(long value, int alignment) {
        long mask = alignment - 1L;
        return (value + mask) & ~mask;
    }
}
