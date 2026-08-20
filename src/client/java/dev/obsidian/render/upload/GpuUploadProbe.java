package dev.obsidian.render.upload;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/** One-shot non-visual validation of Obsidian's bounded staging/copy path. */
public final class GpuUploadProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/GpuUploadProbe");
    private static final Supplier<String> DESTINATION_LABEL =
            () -> "Obsidian Phase 1 staging validation destination";

    private static final int COPY_BYTES = 128;
    private static final int FIRST_DESTINATION_OFFSET = 64;
    private static final int SECOND_DESTINATION_OFFSET = 512;
    private static final int PRESSURE_DESTINATION_OFFSET = 1024;

    public enum State {
        ARMED,
        SUBMITTED,
        VERIFIED,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private final StagingUploadArena staging;

    private GpuBuffer destination;
    private State state = State.ARMED;
    private long submittedFrame = -1L;
    private long verifiedFrame = -1L;
    private long submittedBatchOrdinal;

    public GpuUploadProbe(GpuDevice device, StagingUploadArena staging) {
        this.device = device;
        this.staging = staging;
    }

    public void submit(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.ARMED) {
            return;
        }

        GpuBuffer created = null;
        boolean batchStarted = false;
        try {
            int stagingCapacity = staging.capacityBytes();
            long destinationBytes = (long) stagingCapacity + PRESSURE_DESTINATION_OFFSET;
            created = device.createBuffer(
                    DESTINATION_LABEL,
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    destinationBytes);

            CommandEncoder encoder = device.createCommandEncoder();
            if (!staging.beginBatch()) {
                throw new IllegalStateException("Staging batch metadata was already saturated before validation");
            }
            batchStarted = true;

            ByteBuffer first = patternBuffer(COPY_BYTES, 0x31);
            ByteBuffer second = patternBuffer(COPY_BYTES, 0x57);

            if (!staging.stageCopy(encoder, first, created, FIRST_DESTINATION_OFFSET)) {
                throw new IllegalStateException("First validation copy unexpectedly hit staging backpressure");
            }
            if (!staging.stageCopy(encoder, second, created, SECOND_DESTINATION_OFFSET)) {
                throw new IllegalStateException("Second validation copy unexpectedly hit staging backpressure");
            }

            // Intentionally request a full-capacity allocation while bytes from
            // this batch are already reserved. It must be rejected instead of
            // allocating fallback staging memory or waiting for the GPU.
            ByteBuffer pressure = ByteBuffer.allocateDirect(stagingCapacity);
            boolean pressureRejected = !staging.stageCopy(
                    encoder,
                    pressure,
                    created,
                    PRESSURE_DESTINATION_OFFSET);
            if (!pressureRejected) {
                throw new IllegalStateException("Bounded staging pressure test unexpectedly succeeded");
            }

            staging.submitBatch(encoder);
            batchStarted = false;

            destination = created;
            created = null;
            submittedFrame = frameSerial;
            submittedBatchOrdinal = staging.submittedBatches();
            state = State.SUBMITTED;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 upload probe submitted on frame {0}: copies=2, payloadBytes={1}, stagingCapacity={2}, highWater={3}, backpressureEvents={4}, pendingBatches={5}.",
                    frameSerial,
                    COPY_BYTES * 2,
                    staging.capacityBytes(),
                    staging.highWaterBytes(),
                    staging.backpressureEvents(),
                    staging.pendingBatches());
        } catch (RuntimeException e) {
            if (batchStarted) {
                staging.abortBatch();
            }
            if (created != null) {
                try {
                    created.close();
                } catch (RuntimeException ignored) {
                    // Preserve the original failure as the useful diagnostic.
                }
            }
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 bounded staging/upload probe failed; Minecraft will continue for diagnosis.",
                    e);
        }
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.SUBMITTED || destination == null) {
            return;
        }
        if (staging.reclaimedBatches() < submittedBatchOrdinal) {
            return;
        }

        try {
            try (GpuBufferSlice.MappedView mapped = destination.map(true, false)) {
                ByteBuffer data = mapped.data();
                verifyPattern(data, FIRST_DESTINATION_OFFSET, COPY_BYTES, 0x31);
                verifyPattern(data, SECOND_DESTINATION_OFFSET, COPY_BYTES, 0x57);
            }

            destination.close();
            destination = null;
            verifiedFrame = frameSerial;
            state = State.VERIFIED;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 upload probe verified on frame {0} after {1} frame(s): copiedBytes={2}, reclaimedBytes={3}, pendingBatches={4}, backpressureEvents={5}.",
                    verifiedFrame,
                    verifiedFrame - submittedFrame,
                    COPY_BYTES * 2,
                    staging.reclaimedBytes(),
                    staging.pendingBatches(),
                    staging.backpressureEvents());
        } catch (RuntimeException e) {
            state = State.FAILED;
            if (destination != null) {
                try {
                    destination.close();
                } catch (RuntimeException ignored) {
                    // Preserve the verification failure.
                }
                destination = null;
            }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 upload copy completed but deterministic readback verification failed.",
                    e);
        }
    }

    public State state() {
        return state;
    }

    public long submittedFrame() {
        return submittedFrame;
    }

    public long verifiedFrame() {
        return verifiedFrame;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) {
            return;
        }

        if (destination != null) {
            if (staging.pendingBatches() == 0) {
                try {
                    destination.close();
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING,
                            "Failed to close the upload validation destination during shutdown.", e);
                }
            } else {
                LOG.log(System.Logger.Level.WARNING,
                        "Upload validation destination is still referenced by GPU work; leaving it for Minecraft device shutdown rather than destroying it in flight.");
            }
            destination = null;
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

    private static void verifyPattern(ByteBuffer data, int offset, int size, int seed) {
        for (int i = 0; i < size; i++) {
            byte expected = patternByte(i, seed);
            byte actual = data.get(offset + i);
            if (actual != expected) {
                throw new IllegalStateException(
                        "Upload verification mismatch at destination byte " + (offset + i)
                                + ": expected=" + (expected & 0xFF)
                                + ", actual=" + (actual & 0xFF));
            }
        }
    }

    private static byte patternByte(int index, int seed) {
        return (byte) ((index * 37 + seed * 13) ^ (index >>> 1));
    }
}
