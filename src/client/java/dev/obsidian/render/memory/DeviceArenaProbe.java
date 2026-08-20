package dev.obsidian.render.memory;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.upload.StagingUploadArena;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * One-shot non-visual validation of device-arena allocation, upload, safe
 * retirement, generation protection, reuse, coalescing, and readback.
 */
public final class DeviceArenaProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/DeviceArenaProbe");
    private static final Supplier<String> READBACK_LABEL =
            () -> "Obsidian Phase 1 device arena validation readback";

    private static final int ALIGNMENT = 256;
    private static final int A_BYTES = 64 * 1024;
    private static final int B_BYTES = 96 * 1024;
    private static final int C_BYTES = 48 * 1024;
    private static final int D_BYTES = 80 * 1024;
    private static final int PATTERN_BYTES = 256;
    private static final int READBACK_BYTES = PATTERN_BYTES * 3;
    private static final long EMERGENCY_WAIT_NS = 2_000_000_000L;

    private static final int A_SEED = 0x21;
    private static final int B_SEED = 0x43;
    private static final int C_SEED = 0x65;
    private static final int D_SEED = 0x87;

    public enum State {
        ARMED,
        INITIAL_UPLOAD_SUBMITTED,
        B_RETIRE_SUBMITTED,
        REUSE_UPLOAD_SUBMITTED,
        FINAL_RETIRE_SUBMITTED,
        VERIFIED,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final DeviceGeometryArena arena;
    private final long[] retirementHandles = new long[3];

    private GpuBuffer readback;
    private GpuFence emergencyFence;

    private long allocationA;
    private long allocationB;
    private long allocationC;
    private long allocationD;
    private long oldBOffset;
    private int oldBSlot;
    private int oldBGeneration;

    private long initialUploadBatchOrdinal;
    private long reuseUploadBatchOrdinal;
    private long submittedFrame = -1L;
    private long verifiedFrame = -1L;
    private State state = State.ARMED;

    public DeviceArenaProbe(
            GpuDevice device,
            StagingUploadArena staging,
            DeviceGeometryArena arena) {
        this.device = device;
        this.staging = staging;
        this.arena = arena;
    }

    public void submit(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.ARMED) {
            return;
        }

        boolean batchStarted = false;
        try {
            allocationA = requireAllocation(arena.allocate(A_BYTES, ALIGNMENT), "A");
            allocationB = requireAllocation(arena.allocate(B_BYTES, ALIGNMENT), "B");
            allocationC = requireAllocation(arena.allocate(C_BYTES, ALIGNMENT), "C");

            oldBOffset = arena.offset(allocationB);
            oldBSlot = DeviceGeometryArena.handleSlot(allocationB);
            oldBGeneration = DeviceGeometryArena.handleGeneration(allocationB);

            long pressure = arena.allocate((int) arena.capacityBytes(), ALIGNMENT);
            if (pressure != DeviceGeometryArena.INVALID_HANDLE) {
                arena.cancelUnsubmitted(pressure);
                throw new IllegalStateException("Bounded device arena pressure allocation unexpectedly succeeded");
            }

            CommandEncoder encoder = device.createCommandEncoder();
            if (!staging.beginBatch()) {
                throw new IllegalStateException("Staging batch metadata was saturated before device-arena validation");
            }
            batchStarted = true;

            if (!stagePattern(encoder, allocationA, A_SEED)
                    || !stagePattern(encoder, allocationB, B_SEED)
                    || !stagePattern(encoder, allocationC, C_SEED)) {
                throw new IllegalStateException("Initial device-arena validation upload hit staging backpressure");
            }

            staging.submitBatch(encoder);
            batchStarted = false;
            initialUploadBatchOrdinal = staging.submittedBatches();
            submittedFrame = frameSerial;
            state = State.INITIAL_UPLOAD_SUBMITTED;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 device arena initial upload submitted on frame {0}: arenaCapacity={1}, allocations=3, usedBytes={2}, highWater={3}, allocationFailures={4}, stagingPayloadBytes={5}.",
                    frameSerial,
                    arena.capacityBytes(),
                    arena.usedBytes(),
                    arena.highWaterBytes(),
                    arena.allocationFailures(),
                    PATTERN_BYTES * 3);
        } catch (RuntimeException e) {
            if (batchStarted) {
                staging.abortBatch();
            }
            cancelIfUnsubmitted(allocationC);
            cancelIfUnsubmitted(allocationB);
            cancelIfUnsubmitted(allocationA);
            allocationA = allocationB = allocationC = DeviceGeometryArena.INVALID_HANDLE;
            fail("Initial device-arena allocation/upload validation failed", e);
        }
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        try {
            switch (state) {
                case INITIAL_UPLOAD_SUBMITTED -> pollInitialUpload(frameSerial);
                case B_RETIRE_SUBMITTED -> pollBRetirement(frameSerial);
                case REUSE_UPLOAD_SUBMITTED -> pollReuseUpload(frameSerial);
                case FINAL_RETIRE_SUBMITTED -> pollFinalRetirement(frameSerial);
                default -> {
                    // No work for terminal/unarmed states.
                }
            }
        } catch (RuntimeException e) {
            fail("Device-arena validation state machine failed", e);
        }
    }

    private void pollInitialUpload(long frameSerial) {
        if (staging.reclaimedBatches() < initialUploadBatchOrdinal) {
            return;
        }

        if (readback == null) {
            readback = device.createBuffer(
                    READBACK_LABEL,
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    READBACK_BYTES);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.copyToBuffer(
                arena.slice(allocationB).slice(0L, PATTERN_BYTES),
                readback.slice(0L, PATTERN_BYTES));
        GpuFence fence = encoder.createFence();
        encoder.submit();

        retirementHandles[0] = allocationB;
        if (!arena.retireBatch(fence, retirementHandles, 1)) {
            emergencyFence = fence;
            throw new IllegalStateException("Device-arena retirement table unexpectedly saturated for B");
        }

        state = State.B_RETIRE_SUBMITTED;
        LOG.log(System.Logger.Level.INFO,
                "Phase 1 device arena B readback/retirement submitted on frame {0}: pendingFrees={1}, pendingRetirementBatches={2}.",
                frameSerial,
                arena.pendingFreeCount(),
                arena.pendingRetirementBatches());
    }

    private void pollBRetirement(long frameSerial) {
        if (arena.isAllocated(allocationB)) {
            return;
        }

        verifyReadbackRange(0, B_SEED);

        allocationD = requireAllocation(arena.allocate(D_BYTES, ALIGNMENT), "D");
        long newOffset = arena.offset(allocationD);
        int newSlot = DeviceGeometryArena.handleSlot(allocationD);
        int newGeneration = DeviceGeometryArena.handleGeneration(allocationD);

        if (newOffset != oldBOffset) {
            throw new IllegalStateException(
                    "Best-fit arena reuse did not reuse B's freed span: old=" + oldBOffset + ", new=" + newOffset);
        }
        if (newSlot != oldBSlot || newGeneration == oldBGeneration) {
            throw new IllegalStateException(
                    "Generation-safe slot reuse invariant failed: oldSlot=" + oldBSlot
                            + ", newSlot=" + newSlot
                            + ", oldGeneration=" + oldBGeneration
                            + ", newGeneration=" + newGeneration);
        }

        boolean staleRejected = false;
        try {
            arena.slice(allocationB);
        } catch (IllegalStateException expected) {
            staleRejected = true;
        }
        if (!staleRejected) {
            throw new IllegalStateException("Stale B handle remained usable after slot reuse");
        }

        CommandEncoder encoder = device.createCommandEncoder();
        if (!staging.beginBatch()) {
            throw new IllegalStateException("Staging batch metadata saturated before D reuse upload");
        }
        boolean submitted = false;
        try {
            if (!stagePattern(encoder, allocationD, D_SEED)) {
                throw new IllegalStateException("D reuse upload unexpectedly hit staging backpressure");
            }
            staging.submitBatch(encoder);
            submitted = true;
        } finally {
            if (!submitted) {
                staging.abortBatch();
            }
        }

        reuseUploadBatchOrdinal = staging.submittedBatches();
        state = State.REUSE_UPLOAD_SUBMITTED;

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 device arena safely reused B span on frame {0}: offset={1}, slot={2}, generation={3}->{4}, usedBytes={5}, freeSpans={6}, fragmentationPermille={7}, staleHandleRejections={8}.",
                frameSerial,
                newOffset,
                newSlot,
                oldBGeneration,
                newGeneration,
                arena.usedBytes(),
                arena.freeSpanCount(),
                arena.fragmentationPermille(),
                arena.staleHandleRejections());
    }

    private void pollReuseUpload(long frameSerial) {
        if (staging.reclaimedBatches() < reuseUploadBatchOrdinal) {
            return;
        }

        CommandEncoder encoder = device.createCommandEncoder();
        encoder.copyToBuffer(
                arena.slice(allocationA).slice(0L, PATTERN_BYTES),
                readback.slice(0L, PATTERN_BYTES));
        encoder.copyToBuffer(
                arena.slice(allocationC).slice(0L, PATTERN_BYTES),
                readback.slice(PATTERN_BYTES, PATTERN_BYTES));
        encoder.copyToBuffer(
                arena.slice(allocationD).slice(0L, PATTERN_BYTES),
                readback.slice(PATTERN_BYTES * 2L, PATTERN_BYTES));
        GpuFence fence = encoder.createFence();
        encoder.submit();

        retirementHandles[0] = allocationA;
        retirementHandles[1] = allocationC;
        retirementHandles[2] = allocationD;
        if (!arena.retireBatch(fence, retirementHandles, 3)) {
            emergencyFence = fence;
            throw new IllegalStateException("Device-arena retirement table unexpectedly saturated for final batch");
        }

        state = State.FINAL_RETIRE_SUBMITTED;
        LOG.log(System.Logger.Level.INFO,
                "Phase 1 device arena final readback/retirement submitted on frame {0}: copies=3, pendingFrees={1}, pendingRetirementBatches={2}.",
                frameSerial,
                arena.pendingFreeCount(),
                arena.pendingRetirementBatches());
    }

    private void pollFinalRetirement(long frameSerial) {
        if (arena.allocatedCount() != 0 || arena.pendingRetirementBatches() != 0) {
            return;
        }

        verifyReadbackRange(0, A_SEED);
        verifyReadbackRange(PATTERN_BYTES, C_SEED);
        verifyReadbackRange(PATTERN_BYTES * 2, D_SEED);

        if (arena.usedBytes() != 0L
                || arena.freeSpanCount() != 1
                || arena.largestFreeBlockBytes() != arena.capacityBytes()
                || arena.fragmentationPermille() != 0) {
            throw new IllegalStateException(
                    "Device arena did not coalesce back to one full free span: used=" + arena.usedBytes()
                            + ", freeSpans=" + arena.freeSpanCount()
                            + ", largestFree=" + arena.largestFreeBlockBytes()
                            + ", fragmentationPermille=" + arena.fragmentationPermille());
        }

        readback.close();
        readback = null;
        verifiedFrame = frameSerial;
        state = State.VERIFIED;

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 device arena verified on frame {0} after {1} frame(s): allocations={2}, allocationFailures={3}, retired={4}, reclaimed={5}, staleHandleRejections={6}, usedBytes=0, freeSpans=1, largestFree={7}, fragmentationPermille=0.",
                verifiedFrame,
                verifiedFrame - submittedFrame,
                arena.successfulAllocations(),
                arena.allocationFailures(),
                arena.retiredAllocations(),
                arena.reclaimedAllocations(),
                arena.staleHandleRejections(),
                arena.largestFreeBlockBytes());
    }

    private boolean stagePattern(CommandEncoder encoder, long handle, int seed) {
        ByteBuffer pattern = patternBuffer(PATTERN_BYTES, seed);
        return staging.stageCopy(
                encoder,
                pattern,
                arena.slice(handle).slice(0L, PATTERN_BYTES));
    }

    private void verifyReadbackRange(int offset, int seed) {
        try (GpuBufferSlice.MappedView mapped = readback.map(true, false)) {
            ByteBuffer data = mapped.data();
            for (int i = 0; i < PATTERN_BYTES; i++) {
                byte expected = patternByte(i, seed);
                byte actual = data.get(offset + i);
                if (actual != expected) {
                    throw new IllegalStateException(
                            "Device arena readback mismatch at byte " + (offset + i)
                                    + ": expected=" + (expected & 0xFF)
                                    + ", actual=" + (actual & 0xFF));
                }
            }
        }
    }

    private void cancelIfUnsubmitted(long handle) {
        if (handle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(handle)) {
            try {
                arena.cancelUnsubmitted(handle);
            } catch (RuntimeException ignored) {
                // Preserve the original failure as the useful diagnostic.
            }
        }
    }

    private static long requireAllocation(long handle, String name) {
        if (handle == DeviceGeometryArena.INVALID_HANDLE) {
            throw new IllegalStateException("Device arena could not allocate validation span " + name);
        }
        return handle;
    }

    private void fail(String message, RuntimeException error) {
        state = State.FAILED;
        LOG.log(System.Logger.Level.ERROR, message + "; Minecraft will continue for diagnosis.", error);
    }

    public State state() {
        return state;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) {
            return;
        }

        if (emergencyFence != null) {
            try {
                emergencyFence.awaitCompletion(EMERGENCY_WAIT_NS);
                emergencyFence.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Emergency device-arena probe fence cleanup failed; leaving affected GPU resources for device shutdown.",
                        e);
            }
            emergencyFence = null;
        }

        if (readback != null) {
            if (arena.pendingRetirementBatches() == 0 && staging.pendingBatches() == 0) {
                try {
                    readback.close();
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Failed to close device-arena validation readback buffer during shutdown.", e);
                }
            } else {
                LOG.log(System.Logger.Level.WARNING,
                        "Device-arena validation readback may still be referenced by GPU work; leaving it for Minecraft device shutdown.");
            }
            readback = null;
        }
        state = State.CLOSED;
    }

    private static ByteBuffer patternBuffer(int size, int seed) {
        ByteBuffer data = ByteBuffer.allocateDirect(size);
        for (int i = 0; i < size; i++) {
            data.put(patternByte(i, seed));
        }
        data.flip();
        return data;
    }

    private static byte patternByte(int index, int seed) {
        return (byte) ((index * 53 + seed * 17) ^ (index >>> 2));
    }
}
