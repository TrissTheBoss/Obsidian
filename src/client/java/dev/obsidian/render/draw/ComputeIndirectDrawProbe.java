package dev.obsidian.render.draw;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.obsidian.render.graph.FixedFrameGraph;
import dev.obsidian.render.graph.FrameGraphCommandStream;
import dev.obsidian.render.graph.GpuTimestampProfiler;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.upload.StagingUploadArena;
import dev.obsidian.render.vulkan.VulkanComputeIndirectGenerator;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One-shot validation that GPU compute can generate native indexed-indirect
 * commands which a following public Blaze3D graphics pass consumes in the same
 * useful submission.
 */
public final class ComputeIndirectDrawProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/ComputeIndirectDrawProbe");

    private static final Supplier<String> TARGET_LABEL = () -> "Obsidian Phase 1 compute indirect target";
    private static final Supplier<String> READBACK_LABEL = () -> "Obsidian Phase 1 compute indirect readback";
    private static final Supplier<String> PASS_LABEL = () -> "Obsidian Phase 1 compute indirect render pass";
    private static final Runnable NOOP_COMPLETION = () -> {};

    private static final int PASS_UPLOAD = 0;
    private static final int PASS_COMPUTE = 1;
    private static final int PASS_INDIRECT_DRAW = 2;
    private static final int PASS_READBACK = 3;

    private static final int TARGET_WIDTH = 16;
    private static final int TARGET_HEIGHT = 16;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int READBACK_BYTES = TARGET_WIDTH * TARGET_HEIGHT * BYTES_PER_PIXEL;

    private static final int VERTEX_COUNT = 6;
    private static final int INDEX_COUNT = 6;
    private static final int INDIRECT_COMMAND_COUNT = VulkanComputeIndirectGenerator.COMMAND_COUNT;
    private static final int TRIANGLE_COUNT = 2;
    private static final int VERTEX_BYTES = VERTEX_COUNT * 3 * Float.BYTES;
    private static final int INDEX_BYTES = INDEX_COUNT * Short.BYTES;
    private static final int STAGING_PAYLOAD_BYTES = VERTEX_BYTES + INDEX_BYTES;

    private static final Vector4f CLEAR_COLOR = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    private static final String VERTEX_SHADER = """
            #version 330
            in vec3 Position;
            void main() {
                gl_Position = vec4(Position, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0, 0.0, 1.0, 1.0);
            }
            """;

    private static final ShaderSource SHADER_SOURCE = (identifier, type) -> {
        if (type == ShaderType.VERTEX) {
            return VERTEX_SHADER;
        }
        if (type == ShaderType.FRAGMENT) {
            return FRAGMENT_SHADER;
        }
        throw new IllegalArgumentException("Unsupported compute-indirect graphics shader type: " + type);
    };

    public enum State {
        ARMED,
        SUBMITTED,
        VERIFIED,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private final DeviceGeometryArena arena;
    private final FixedFrameGraph graph = new FixedFrameGraph();
    private final GpuTimestampProfiler profiler;
    private final FrameGraphCommandStream stream;
    private final RenderPipeline pipeline;
    private final long[] retirementHandles = new long[2];

    private VulkanComputeIndirectGenerator generator;
    private GpuTexture target;
    private GpuTextureView targetView;
    private GpuBuffer readbackBuffer;
    private GpuFence pendingArenaFence;

    private long vertexHandle = DeviceGeometryArena.INVALID_HANDLE;
    private long indexHandle = DeviceGeometryArena.INVALID_HANDLE;
    private long vertexOffset;
    private long indexOffset;
    private int firstIndex;

    private State state = State.ARMED;
    private long submittedFrame = -1L;
    private long verifiedFrame = -1L;
    private long computeDispatches;
    private long indirectCalls;
    private long indirectCommandsExecuted;
    private long triangles;
    private long retirementBackpressureEvents;
    private long retirementRegistrationFailures;
    private boolean pipelineValid;
    private boolean submitted;

    public ComputeIndirectDrawProbe(GpuDevice device, StagingUploadArena staging, DeviceGeometryArena arena) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.arena = arena;

        if (!device.getDeviceInfo().features().drawIndirect()) {
            throw new IllegalStateException("Dev8 requires indexed indirect drawing");
        }
        if (!device.getDeviceInfo().features().multiDrawIndirect()) {
            throw new IllegalStateException("Dev8 validation requires multi-draw indirect support");
        }

        graph.definePass(PASS_UPLOAD, "compute-indirect-upload", 0L);
        graph.definePass(PASS_COMPUTE, "compute-indirect-generate", 1L << PASS_UPLOAD);
        graph.definePass(PASS_INDIRECT_DRAW, "compute-indirect-draw", 1L << PASS_COMPUTE);
        graph.definePass(PASS_READBACK, "compute-indirect-readback", 1L << PASS_INDIRECT_DRAW);

        profiler = new GpuTimestampProfiler(device, graph.passCount());
        stream = new FrameGraphCommandStream(device, staging, graph, profiler);
        generator = new VulkanComputeIndirectGenerator(device);

        pipeline = RenderPipeline.builder()
                .withLocation("obsidian_compute_indirect_draw")
                .withVertexShader("obsidian_compute_indirect_draw")
                .withFragmentShader("obsidian_compute_indirect_draw")
                .withCull(false)
                .withColorTargetState(new ColorTargetState(
                        Optional.empty(),
                        GpuFormat.RGBA8_UNORM,
                        ColorTargetState.WRITE_ALL))
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();

        CompiledRenderPipeline compiled = device.precompilePipeline(pipeline, SHADER_SOURCE);
        pipelineValid = compiled.isValid();
        if (!pipelineValid) {
            generator.close();
            generator = null;
            throw new IllegalStateException("Obsidian compute-indirect graphics pipeline failed to compile");
        }
    }

    public void submit(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.ARMED) {
            return;
        }

        GpuFence arenaFence = null;
        try {
            createResourcesAndAllocations();
            if (!stream.begin()) {
                return;
            }

            GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
            GpuBufferSlice indexSlice = arena.slice(indexHandle);

            stream.beginPass(PASS_UPLOAD);
            if (!stream.stageCopy(vertexData(), vertexSlice)
                    || !stream.stageCopy(indexData(), indexSlice)) {
                throw new IllegalStateException("Dev8 geometry upload hit staging backpressure");
            }
            stream.endPass(PASS_UPLOAD);

            stream.beginPass(PASS_COMPUTE);
            generator.dispatch(stream.backendInteropEncoder(), firstIndex);
            computeDispatches++;
            stream.endPass(PASS_COMPUTE);

            stream.beginPass(PASS_INDIRECT_DRAW);
            try (RenderPass pass = stream.createRenderPass(PASS_LABEL, targetView, Optional.of(CLEAR_COLOR))) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, vertexSlice);
                pass.setIndexBuffer(indexSlice.buffer(), IndexType.SHORT);
                pass.drawIndexedIndirect(generator.indirectSlice(), INDIRECT_COMMAND_COUNT);
                indirectCalls++;
                indirectCommandsExecuted += INDIRECT_COMMAND_COUNT;
                triangles += TRIANGLE_COUNT;
            }
            stream.endPass(PASS_INDIRECT_DRAW);

            stream.beginPass(PASS_READBACK);
            stream.copyTextureToBuffer(target, readbackBuffer, 0L, NOOP_COMPLETION, 0);
            stream.endPass(PASS_READBACK);

            arenaFence = stream.createCompletionFence();
            stream.submit();
            submitted = true;
            submittedFrame = frameSerial;
            state = State.SUBMITTED;
        } catch (RuntimeException e) {
            stream.abort();
            if (arenaFence != null) {
                try {
                    arenaFence.close();
                } catch (RuntimeException ignored) {
                    // Preserve the useful setup/submission failure.
                }
            }
            cancelUnsubmittedAllocations();
            closeLocalResourcesIfSafe();
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 compute-indirect submission failed; Minecraft will continue for diagnosis.", e);
            return;
        }

        retirementHandles[0] = vertexHandle;
        retirementHandles[1] = indexHandle;
        pendingArenaFence = arenaFence;
        tryRegisterArenaRetirement();

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 compute-generated indirect draw submitted on frame {0}: graphPasses={1}, usefulSubmissions={2}, profilerOnlySubmissions=0, computeDispatches={3}, indirectCalls={4}, indirectCommands={5}, triangles={6}, nativeComputeSeam=true, nativeGraphicsSeam=false, pipelineValid={7}, vertexArenaOffset={8}, indexArenaOffset={9}, firstIndex={10}, vertexBytes={11}, indexBytes={12}, gpuGeneratedIndirectBytes={13}, stagingPayloadBytes={14}, arenaUsedBytes={15}.",
                frameSerial,
                graph.passCount(),
                stream.submissionCount(),
                computeDispatches,
                indirectCalls,
                indirectCommandsExecuted,
                triangles,
                pipelineValid,
                vertexOffset,
                indexOffset,
                firstIndex,
                VERTEX_BYTES,
                INDEX_BYTES,
                VulkanComputeIndirectGenerator.BUFFER_BYTES,
                STAGING_PAYLOAD_BYTES,
                arena.usedBytes());
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.SUBMITTED || readbackBuffer == null) {
            return;
        }

        if (pendingArenaFence != null && !tryRegisterArenaRetirement()) {
            return;
        }
        if (!stream.isSubmissionComplete()) {
            return;
        }
        if (!stream.pollProfiler()) {
            return;
        }
        if (arena.isAllocated(vertexHandle) || arena.isAllocated(indexHandle)) {
            return;
        }

        try {
            verifyPixels();
            verifiedFrame = frameSerial;
            state = State.VERIFIED;
            closeLocalResourcesIfSafe();

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 compute-generated indirect draw verified on frame {0} after {1} frame(s): executedMask={2}, uploadCpuNs={3}, computeCpuNs={4}, drawCpuNs={5}, readbackCpuNs={6}, uploadGpuNs={7}, computeGpuNs={8}, drawGpuNs={9}, readbackGpuNs={10}, totalGpuNs={11}, queryPolls={12}, unavailablePolls={13}, usefulSubmissions={14}, profilerOnlySubmissions=0, computeDispatches={15}, indirectCalls={16}, indirectCommands={17}, triangles={18}, nativeComputeSeam=true, nativeGraphicsSeam=false, leftRGBA=255/0/255/255, rightRGBA=255/0/255/255, cornerRGBA=0/0/0/255, pixelsVerified=3, arenaRetired={19}, arenaReclaimed={20}, arenaUsedBytes={21}, arenaFreeSpans={22}, arenaFragmentationPermille={23}.",
                    verifiedFrame,
                    verifiedFrame - submittedFrame,
                    Long.toUnsignedString(graph.executedMask()),
                    graph.lastCpuNs(PASS_UPLOAD),
                    graph.lastCpuNs(PASS_COMPUTE),
                    graph.lastCpuNs(PASS_INDIRECT_DRAW),
                    graph.lastCpuNs(PASS_READBACK),
                    profiler.passGpuNs(PASS_UPLOAD),
                    profiler.passGpuNs(PASS_COMPUTE),
                    profiler.passGpuNs(PASS_INDIRECT_DRAW),
                    profiler.passGpuNs(PASS_READBACK),
                    profiler.totalGpuNs(),
                    profiler.pollCount(),
                    profiler.unavailablePolls(),
                    stream.submissionCount(),
                    computeDispatches,
                    indirectCalls,
                    indirectCommandsExecuted,
                    triangles,
                    arena.retiredAllocations(),
                    arena.reclaimedAllocations(),
                    arena.usedBytes(),
                    arena.freeSpanCount(),
                    arena.fragmentationPermille());
        } catch (RuntimeException e) {
            state = State.FAILED;
            closeLocalResourcesIfSafe();
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 compute-indirect completed but pixel/timestamp verification failed.", e);
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

    public long computeDispatches() {
        return computeDispatches;
    }

    public long indirectCalls() {
        return indirectCalls;
    }

    public long indirectCommandsExecuted() {
        return indirectCommandsExecuted;
    }

    public long triangles() {
        return triangles;
    }

    public boolean pipelineValid() {
        return pipelineValid;
    }

    public long retirementBackpressureEvents() {
        return retirementBackpressureEvents;
    }

    public long retirementRegistrationFailures() {
        return retirementRegistrationFailures;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) {
            return;
        }

        if (submitted && !stream.isSubmissionComplete()) {
            LOG.log(System.Logger.Level.WARNING,
                    "Dev8 compute/indirect resources are still referenced by GPU work; abandoning raw Vulkan validation resources rather than destroying them in flight.");
            abandonLocalResources();
            if (pendingArenaFence != null) {
                pendingArenaFence.close();
                pendingArenaFence = null;
            }
            state = State.CLOSED;
            return;
        }

        if (pendingArenaFence != null) {
            if (tryRegisterArenaRetirement()) {
                arena.pollRetirements();
            } else {
                pendingArenaFence.close();
                pendingArenaFence = null;
            }
        }

        if (!submitted) {
            cancelUnsubmittedAllocations();
        }
        closeLocalResourcesIfSafe();
        profiler.close();
        state = State.CLOSED;
    }

    private boolean tryRegisterArenaRetirement() {
        if (pendingArenaFence == null) {
            return true;
        }
        try {
            if (!arena.retireBatch(pendingArenaFence, retirementHandles, retirementHandles.length)) {
                retirementBackpressureEvents++;
                return false;
            }
            pendingArenaFence = null;
            return true;
        } catch (RuntimeException e) {
            retirementRegistrationFailures++;
            LOG.log(System.Logger.Level.ERROR,
                    "Arena retirement registration failed after the useful dev8 submission; keeping the timeline handle and allocations alive for safe retry/diagnosis.", e);
            return false;
        }
    }

    private void createResourcesAndAllocations() {
        if (target != null) {
            return;
        }

        vertexHandle = arena.allocate(VERTEX_BYTES, 16);
        if (vertexHandle == DeviceGeometryArena.INVALID_HANDLE) {
            throw new IllegalStateException("Unable to allocate dev8 vertex span from device geometry arena");
        }
        indexHandle = arena.allocate(INDEX_BYTES, 4);
        if (indexHandle == DeviceGeometryArena.INVALID_HANDLE) {
            arena.cancelUnsubmitted(vertexHandle);
            vertexHandle = DeviceGeometryArena.INVALID_HANDLE;
            throw new IllegalStateException("Unable to allocate dev8 index span from device geometry arena");
        }

        GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
        GpuBufferSlice indexSlice = arena.slice(indexHandle);
        vertexOffset = vertexSlice.offset();
        indexOffset = indexSlice.offset();
        if ((indexOffset & 1L) != 0L || indexOffset / Short.BYTES > Integer.MAX_VALUE) {
            throw new IllegalStateException("Dev8 index arena offset cannot be represented as firstIndex");
        }
        firstIndex = (int) (indexOffset / Short.BYTES);

        target = device.createTexture(
                TARGET_LABEL,
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                GpuFormat.RGBA8_UNORM,
                TARGET_WIDTH,
                TARGET_HEIGHT,
                1,
                1);
        targetView = device.createTextureView(target);
        readbackBuffer = device.createBuffer(
                READBACK_LABEL,
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                READBACK_BYTES);
    }

    private void verifyPixels() {
        try (GpuBufferSlice.MappedView mapped = readbackBuffer.map(true, false)) {
            ByteBuffer data = mapped.data();
            requirePixel(data, 4, 8, 255, 0, 255, 255, "left");
            requirePixel(data, 11, 8, 255, 0, 255, 255, "right");
            requirePixel(data, 0, 0, 0, 0, 0, 255, "corner");
        }
    }

    private static ByteBuffer vertexData() {
        ByteBuffer data = ByteBuffer.allocateDirect(VERTEX_BYTES).order(ByteOrder.nativeOrder());
        putVertex(data, -0.95f, -0.75f, 0.0f);
        putVertex(data, -0.05f, -0.75f, 0.0f);
        putVertex(data, -0.50f, 0.75f, 0.0f);
        putVertex(data, 0.05f, -0.75f, 0.0f);
        putVertex(data, 0.95f, -0.75f, 0.0f);
        putVertex(data, 0.50f, 0.75f, 0.0f);
        return data.flip();
    }

    private static ByteBuffer indexData() {
        ByteBuffer data = ByteBuffer.allocateDirect(INDEX_BYTES).order(ByteOrder.nativeOrder());
        data.putShort((short) 0);
        data.putShort((short) 1);
        data.putShort((short) 2);
        data.putShort((short) 3);
        data.putShort((short) 4);
        data.putShort((short) 5);
        return data.flip();
    }

    private static void putVertex(ByteBuffer data, float x, float y, float z) {
        data.putFloat(x);
        data.putFloat(y);
        data.putFloat(z);
    }

    private static void requirePixel(
            ByteBuffer data,
            int x,
            int y,
            int expectedR,
            int expectedG,
            int expectedB,
            int expectedA,
            String label) {
        int offset = (y * TARGET_WIDTH + x) * BYTES_PER_PIXEL;
        int r = data.get(offset) & 0xFF;
        int g = data.get(offset + 1) & 0xFF;
        int b = data.get(offset + 2) & 0xFF;
        int a = data.get(offset + 3) & 0xFF;
        if (r != expectedR || g != expectedG || b != expectedB || a != expectedA) {
            throw new IllegalStateException(
                    "Compute indirect " + label + " pixel mismatch at (" + x + "," + y + "): expected="
                            + expectedR + "/" + expectedG + "/" + expectedB + "/" + expectedA
                            + ", actual=" + r + "/" + g + "/" + b + "/" + a);
        }
    }

    private void cancelUnsubmittedAllocations() {
        if (vertexHandle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(vertexHandle)) {
            try {
                arena.cancelUnsubmitted(vertexHandle);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to cancel unsubmitted dev8 vertex allocation.", e);
            }
        }
        if (indexHandle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(indexHandle)) {
            try {
                arena.cancelUnsubmitted(indexHandle);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to cancel unsubmitted dev8 index allocation.", e);
            }
        }
        vertexHandle = DeviceGeometryArena.INVALID_HANDLE;
        indexHandle = DeviceGeometryArena.INVALID_HANDLE;
    }

    private void closeLocalResourcesIfSafe() {
        if (targetView != null) {
            try {
                targetView.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev8 target view.", e);
            }
            targetView = null;
        }
        if (target != null) {
            try {
                target.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev8 target texture.", e);
            }
            target = null;
        }
        if (readbackBuffer != null) {
            try {
                readbackBuffer.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev8 readback buffer.", e);
            }
            readbackBuffer = null;
        }
        if (generator != null) {
            try {
                generator.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev8 compute generator.", e);
            }
            generator = null;
        }
    }

    private void abandonLocalResources() {
        targetView = null;
        target = null;
        readbackBuffer = null;
        generator = null;
    }
}
