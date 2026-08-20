package dev.obsidian.render.graph;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.upload.StagingUploadArena;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/** One-shot non-visual validation of graph ordering and integrated profiling. */
public final class FrameGraphProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameGraphProbe");
    private static final Supplier<String> DESTINATION_LABEL =
            () -> "Obsidian Phase 1 frame-graph validation buffer";

    private static final int PASS_UPLOAD = 0;
    private static final int PASS_DEPENDENT_COPY = 1;
    private static final int PAYLOAD_BYTES = 256;
    private static final int SOURCE_OFFSET = 0;
    private static final int COPY_OFFSET = 512;
    private static final int DESTINATION_BYTES = 1024;

    public enum State {
        ARMED,
        SUBMITTED,
        VERIFIED,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final FixedFrameGraph graph = new FixedFrameGraph();
    private final GpuTimestampProfiler profiler;
    private final FrameGraphCommandStream stream;

    private GpuBuffer destination;
    private State state = State.ARMED;
    private long submittedFrame = -1L;
    private long verifiedFrame = -1L;

    public FrameGraphProbe(GpuDevice device, StagingUploadArena staging) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.staging = staging;

        graph.definePass(PASS_UPLOAD, "validation-upload", 0L);
        graph.definePass(PASS_DEPENDENT_COPY, "validation-dependent-copy", 1L << PASS_UPLOAD);
        profiler = new GpuTimestampProfiler(device, graph.passCount());
        stream = new FrameGraphCommandStream(device, staging, graph, profiler);
    }

    public void submit(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.ARMED) {
            return;
        }

        try {
            if (destination == null) {
                destination = device.createBuffer(
                        DESTINATION_LABEL,
                        GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC,
                        DESTINATION_BYTES);
            }

            if (!stream.begin()) {
                return;
            }

            ByteBuffer pattern = patternBuffer(PAYLOAD_BYTES, 0x6D);

            stream.beginPass(PASS_UPLOAD);
            if (!stream.stageCopy(pattern, destination, SOURCE_OFFSET)) {
                throw new IllegalStateException("Dev5 validation upload unexpectedly hit staging backpressure");
            }
            stream.endPass(PASS_UPLOAD);

            stream.beginPass(PASS_DEPENDENT_COPY);
            GpuBufferSlice source = destination.slice(SOURCE_OFFSET, PAYLOAD_BYTES);
            GpuBufferSlice copy = destination.slice(COPY_OFFSET, PAYLOAD_BYTES);
            stream.copy(source, copy);
            stream.endPass(PASS_DEPENDENT_COPY);

            stream.submit();
            submittedFrame = frameSerial;
            state = State.SUBMITTED;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 frame graph submitted on frame {0}: passes={1}, dependencies=1, usefulSubmissions={2}, stagingPayloadBytes={3}, profilerOnlySubmissions=0, timestampPeriodNs={4}.",
                    frameSerial,
                    graph.passCount(),
                    stream.submissionCount(),
                    PAYLOAD_BYTES,
                    profiler.timestampPeriodNs());
        } catch (RuntimeException e) {
            stream.abort();
            state = State.FAILED;
            closeDestinationIfSafe();
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 frame-graph validation submission failed; Minecraft will continue for diagnosis.",
                    e);
        }
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.SUBMITTED || destination == null) {
            return;
        }
        if (!stream.isSubmissionComplete()) {
            return;
        }
        if (!stream.pollProfiler()) {
            return;
        }

        try {
            try (GpuBufferSlice.MappedView mapped = destination.map(true, false)) {
                ByteBuffer data = mapped.data();
                verifyPattern(data, SOURCE_OFFSET, PAYLOAD_BYTES, 0x6D);
                verifyPattern(data, COPY_OFFSET, PAYLOAD_BYTES, 0x6D);
            }

            destination.close();
            destination = null;
            verifiedFrame = frameSerial;
            state = State.VERIFIED;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 frame graph verified on frame {0} after {1} frame(s): executedMask={2}, pass0CpuNs={3}, pass1CpuNs={4}, pass0GpuNs={5}, pass1GpuNs={6}, totalGpuNs={7}, queryPolls={8}, unavailablePolls={9}, usefulSubmissions={10}, profilerOnlySubmissions=0, copiedBytes={11}.",
                    verifiedFrame,
                    verifiedFrame - submittedFrame,
                    Long.toUnsignedString(graph.executedMask()),
                    graph.lastCpuNs(PASS_UPLOAD),
                    graph.lastCpuNs(PASS_DEPENDENT_COPY),
                    profiler.passGpuNs(PASS_UPLOAD),
                    profiler.passGpuNs(PASS_DEPENDENT_COPY),
                    profiler.totalGpuNs(),
                    profiler.pollCount(),
                    profiler.unavailablePolls(),
                    stream.submissionCount(),
                    PAYLOAD_BYTES * 2);
        } catch (RuntimeException e) {
            state = State.FAILED;
            closeDestinationIfSafe();
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 frame graph completed but timestamp/data verification failed.",
                    e);
        }
    }

    public State state() {
        return state;
    }

    public FixedFrameGraph graph() {
        return graph;
    }

    public GpuTimestampProfiler profiler() {
        return profiler;
    }

    public FrameGraphCommandStream stream() {
        return stream;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) {
            return;
        }

        boolean inFlight = state == State.SUBMITTED && !stream.isSubmissionComplete();
        if (inFlight) {
            LOG.log(System.Logger.Level.WARNING,
                    "Dev5 frame-graph resources are still referenced by GPU work; leaving validation buffer/query pool for Minecraft device shutdown rather than destroying them in flight.");
            destination = null;
            state = State.CLOSED;
            return;
        }

        closeDestinationIfSafe();
        profiler.close();
        state = State.CLOSED;
    }

    private void closeDestinationIfSafe() {
        if (destination != null) {
            try {
                destination.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to close dev5 frame-graph validation buffer.", e);
            }
            destination = null;
        }
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
                        "Frame-graph validation mismatch at destination byte " + (offset + i)
                                + ": expected=" + (expected & 0xFF)
                                + ", actual=" + (actual & 0xFF));
            }
        }
    }

    private static byte patternByte(int index, int seed) {
        return (byte) ((index * 29 + seed * 17) ^ (index >>> 2));
    }
}
