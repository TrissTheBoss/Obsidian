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
import dev.obsidian.render.vulkan.VulkanVisibilityCompactor;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One-shot dev9 oracle for GPU scene visibility and indirect compaction.
 *
 * <p>Four on-screen triangles are represented by GPU candidate records. Compute
 * keeps only the two whose centers fall inside the validation frustum, compacts
 * their commands to the front, writes count=2 and leaves two zero commands in
 * the tail. Public Blaze3D draws all four command slots; zero-tail semantics
 * keep the culled triangles invisible without widening the native graphics seam.</p>
 */
public final class VisibilityCompactionProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/VisibilityCompactionProbe");

    private static final Supplier<String> TARGET_LABEL = () -> "Obsidian Phase 1 visibility target";
    private static final Supplier<String> PIXEL_READBACK_LABEL = () -> "Obsidian Phase 1 visibility pixel readback";
    private static final Supplier<String> OUTPUT_READBACK_LABEL = () -> "Obsidian Phase 1 compacted command readback";
    private static final Supplier<String> PASS_LABEL = () -> "Obsidian Phase 1 visibility render pass";
    private static final Runnable NOOP_COMPLETION = () -> {};

    private static final int PASS_UPLOAD = 0;
    private static final int PASS_VISIBILITY = 1;
    private static final int PASS_INDIRECT_DRAW = 2;
    private static final int PASS_READBACK = 3;

    private static final int TARGET_WIDTH = 32;
    private static final int TARGET_HEIGHT = 32;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int PIXEL_READBACK_BYTES = TARGET_WIDTH * TARGET_HEIGHT * BYTES_PER_PIXEL;

    private static final int TRIANGLE_COUNT = VulkanVisibilityCompactor.CANDIDATE_COUNT;
    private static final int VERTEX_COUNT = TRIANGLE_COUNT * 3;
    private static final int INDEX_COUNT = TRIANGLE_COUNT * 3;
    private static final int VERTEX_BYTES = VERTEX_COUNT * 3 * Float.BYTES;
    private static final int INDEX_BYTES = INDEX_COUNT * Short.BYTES;
    private static final int SCENE_BYTES = VulkanVisibilityCompactor.CANDIDATE_BUFFER_BYTES;
    private static final int STAGING_PAYLOAD_BYTES = VERTEX_BYTES + INDEX_BYTES + SCENE_BYTES;

    private static final float[] CENTERS_X = {-0.75f, -0.25f, 0.25f, 0.75f};
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
        throw new IllegalArgumentException("Unsupported visibility graphics shader type: " + type);
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

    private VulkanVisibilityCompactor compactor;
    private GpuTexture target;
    private GpuTextureView targetView;
    private GpuBuffer pixelReadback;
    private GpuBuffer outputReadback;
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
    private long publicIndirectSlots;
    private int visibleCount;
    private int culledCount;
    private long retirementBackpressureEvents;
    private long retirementRegistrationFailures;
    private boolean pipelineValid;
    private boolean submitted;

    public VisibilityCompactionProbe(GpuDevice device, StagingUploadArena staging, DeviceGeometryArena arena) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.arena = arena;

        if (!device.getDeviceInfo().features().drawIndirect()) {
            throw new IllegalStateException("Dev9 requires indexed indirect drawing");
        }
        if (!device.getDeviceInfo().features().multiDrawIndirect()) {
            throw new IllegalStateException("Dev9 validation requires multi-draw indirect support");
        }

        graph.definePass(PASS_UPLOAD, "visibility-scene-upload", 0L);
        graph.definePass(PASS_VISIBILITY, "visibility-compact", 1L << PASS_UPLOAD);
        graph.definePass(PASS_INDIRECT_DRAW, "visibility-indirect-draw", 1L << PASS_VISIBILITY);
        graph.definePass(PASS_READBACK, "visibility-readback", 1L << PASS_INDIRECT_DRAW);

        profiler = new GpuTimestampProfiler(device, graph.passCount());
        stream = new FrameGraphCommandStream(device, staging, graph, profiler);
        compactor = new VulkanVisibilityCompactor(device);

        pipeline = RenderPipeline.builder()
                .withLocation("obsidian_visibility_compaction")
                .withVertexShader("obsidian_visibility_compaction")
                .withFragmentShader("obsidian_visibility_compaction")
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
            compactor.close();
            compactor = null;
            throw new IllegalStateException("Obsidian visibility graphics pipeline failed to compile");
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
                    || !stream.stageCopy(indexData(), indexSlice)
                    || !stream.stageCopy(sceneData(firstIndex), compactor.candidateBuffer(), 0L)) {
                throw new IllegalStateException("Dev9 geometry/scene upload hit staging backpressure");
            }
            stream.endPass(PASS_UPLOAD);

            stream.beginPass(PASS_VISIBILITY);
            compactor.dispatch(stream.backendInteropEncoder());
            computeDispatches++;
            stream.endPass(PASS_VISIBILITY);

            stream.beginPass(PASS_INDIRECT_DRAW);
            try (RenderPass pass = stream.createRenderPass(PASS_LABEL, targetView, Optional.of(CLEAR_COLOR))) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, vertexSlice);
                pass.setIndexBuffer(indexSlice.buffer(), IndexType.SHORT);
                pass.drawIndexedIndirect(compactor.indirectSlice(), VulkanVisibilityCompactor.CANDIDATE_COUNT);
                indirectCalls++;
                publicIndirectSlots += VulkanVisibilityCompactor.CANDIDATE_COUNT;
            }
            stream.endPass(PASS_INDIRECT_DRAW);

            stream.beginPass(PASS_READBACK);
            stream.copyTextureToBuffer(target, pixelReadback, 0L, NOOP_COMPLETION, 0);
            stream.copy(compactor.outputSlice(), outputReadback.slice(0L, VulkanVisibilityCompactor.OUTPUT_BYTES));
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
                    "Phase 1 visibility-compaction submission failed; Minecraft will continue for diagnosis.", e);
            return;
        }

        retirementHandles[0] = vertexHandle;
        retirementHandles[1] = indexHandle;
        pendingArenaFence = arenaFence;
        tryRegisterArenaRetirement();

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 visibility compaction submitted on frame {0}: graphPasses={1}, usefulSubmissions={2}, profilerOnlySubmissions=0, computeDispatches={3}, candidates={4}, expectedVisible={5}, indirectCalls={6}, publicIndirectSlots={7}, nativeComputeSeam=true, nativeGraphicsSeam=false, indirectCountConsumed=false, pipelineValid={8}, vertexArenaOffset={9}, indexArenaOffset={10}, firstIndex={11}, vertexBytes={12}, indexBytes={13}, sceneBytes={14}, gpuOutputBytes={15}, stagingPayloadBytes={16}, arenaUsedBytes={17}.",
                frameSerial,
                graph.passCount(),
                stream.submissionCount(),
                computeDispatches,
                VulkanVisibilityCompactor.CANDIDATE_COUNT,
                VulkanVisibilityCompactor.VISIBLE_COUNT_EXPECTED,
                indirectCalls,
                publicIndirectSlots,
                pipelineValid,
                vertexOffset,
                indexOffset,
                firstIndex,
                VERTEX_BYTES,
                INDEX_BYTES,
                SCENE_BYTES,
                VulkanVisibilityCompactor.OUTPUT_BYTES,
                STAGING_PAYLOAD_BYTES,
                arena.usedBytes());
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.SUBMITTED || pixelReadback == null || outputReadback == null) {
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
            verifyCompactedOutput();
            verifiedFrame = frameSerial;
            state = State.VERIFIED;
            closeLocalResourcesIfSafe();

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 visibility compaction verified on frame {0} after {1} frame(s): executedMask={2}, uploadCpuNs={3}, visibilityCpuNs={4}, drawCpuNs={5}, readbackCpuNs={6}, uploadGpuNs={7}, visibilityGpuNs={8}, drawGpuNs={9}, readbackGpuNs={10}, totalGpuNs={11}, queryPolls={12}, unavailablePolls={13}, usefulSubmissions={14}, profilerOnlySubmissions=0, computeDispatches={15}, candidates={16}, visibleCount={17}, culledCount={18}, indirectCalls={19}, publicIndirectSlots={20}, nativeComputeSeam=true, nativeGraphicsSeam=false, indirectCountConsumed=false, visibleLeftRGBA=255/0/255/255, visibleRightRGBA=255/0/255/255, culledLeftRGBA=0/0/0/255, culledRightRGBA=0/0/0/255, cornerRGBA=0/0/0/255, pixelsVerified=5, compactedCommandsVerified=4, arenaRetired={21}, arenaReclaimed={22}, arenaUsedBytes={23}, arenaFreeSpans={24}, arenaFragmentationPermille={25}.",
                    verifiedFrame,
                    verifiedFrame - submittedFrame,
                    Long.toUnsignedString(graph.executedMask()),
                    graph.lastCpuNs(PASS_UPLOAD),
                    graph.lastCpuNs(PASS_VISIBILITY),
                    graph.lastCpuNs(PASS_INDIRECT_DRAW),
                    graph.lastCpuNs(PASS_READBACK),
                    profiler.passGpuNs(PASS_UPLOAD),
                    profiler.passGpuNs(PASS_VISIBILITY),
                    profiler.passGpuNs(PASS_INDIRECT_DRAW),
                    profiler.passGpuNs(PASS_READBACK),
                    profiler.totalGpuNs(),
                    profiler.pollCount(),
                    profiler.unavailablePolls(),
                    stream.submissionCount(),
                    computeDispatches,
                    VulkanVisibilityCompactor.CANDIDATE_COUNT,
                    visibleCount,
                    culledCount,
                    indirectCalls,
                    publicIndirectSlots,
                    arena.retiredAllocations(),
                    arena.reclaimedAllocations(),
                    arena.usedBytes(),
                    arena.freeSpanCount(),
                    arena.fragmentationPermille());
        } catch (RuntimeException e) {
            state = State.FAILED;
            closeLocalResourcesIfSafe();
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 visibility compaction completed but verification failed.", e);
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

    public long publicIndirectSlots() {
        return publicIndirectSlots;
    }

    public int visibleCount() {
        return visibleCount;
    }

    public int culledCount() {
        return culledCount;
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
                    "Dev9 visibility resources are still referenced by GPU work; abandoning validation resources for Minecraft device shutdown rather than destroying them in flight.");
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
                    "Arena retirement registration failed after the useful dev9 submission; keeping the timeline handle and allocations alive for safe retry/diagnosis.", e);
            return false;
        }
    }

    private void createResourcesAndAllocations() {
        if (target != null) {
            return;
        }

        vertexHandle = arena.allocate(VERTEX_BYTES, 16);
        if (vertexHandle == DeviceGeometryArena.INVALID_HANDLE) {
            throw new IllegalStateException("Unable to allocate dev9 vertex span from device geometry arena");
        }
        indexHandle = arena.allocate(INDEX_BYTES, 4);
        if (indexHandle == DeviceGeometryArena.INVALID_HANDLE) {
            arena.cancelUnsubmitted(vertexHandle);
            vertexHandle = DeviceGeometryArena.INVALID_HANDLE;
            throw new IllegalStateException("Unable to allocate dev9 index span from device geometry arena");
        }

        GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
        GpuBufferSlice indexSlice = arena.slice(indexHandle);
        vertexOffset = vertexSlice.offset();
        indexOffset = indexSlice.offset();
        if ((indexOffset & 1L) != 0L || indexOffset / Short.BYTES > Integer.MAX_VALUE) {
            throw new IllegalStateException("Dev9 index arena offset cannot be represented as firstIndex");
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
        pixelReadback = device.createBuffer(
                PIXEL_READBACK_LABEL,
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                PIXEL_READBACK_BYTES);
        outputReadback = device.createBuffer(
                OUTPUT_READBACK_LABEL,
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                VulkanVisibilityCompactor.OUTPUT_BYTES);
    }

    private void verifyPixels() {
        try (GpuBufferSlice.MappedView mapped = pixelReadback.map(true, false)) {
            ByteBuffer data = mapped.data();
            requirePixel(data, 12, 16, 255, 0, 255, 255, "visible-left");
            requirePixel(data, 20, 16, 255, 0, 255, 255, "visible-right");
            requirePixel(data, 4, 16, 0, 0, 0, 255, "culled-left");
            requirePixel(data, 28, 16, 0, 0, 0, 255, "culled-right");
            requirePixel(data, 0, 0, 0, 0, 0, 255, "corner");
        }
    }

    private void verifyCompactedOutput() {
        try (GpuBufferSlice.MappedView mapped = outputReadback.map(true, false)) {
            ByteBuffer data = mapped.data().order(ByteOrder.nativeOrder());
            visibleCount = data.getInt(VulkanVisibilityCompactor.COUNT_OFFSET);
            culledCount = VulkanVisibilityCompactor.CANDIDATE_COUNT - visibleCount;
            if (visibleCount != VulkanVisibilityCompactor.VISIBLE_COUNT_EXPECTED) {
                throw new IllegalStateException(
                        "Visibility count mismatch: expected=" + VulkanVisibilityCompactor.VISIBLE_COUNT_EXPECTED
                                + ", actual=" + visibleCount);
            }

            int expectedA = firstIndex + 3;
            int expectedB = firstIndex + 6;
            boolean seenA = false;
            boolean seenB = false;

            for (int slot = 0; slot < VulkanVisibilityCompactor.CANDIDATE_COUNT; slot++) {
                int base = slot * VulkanVisibilityCompactor.COMMAND_BYTES;
                int indexCount = data.getInt(base);
                int instanceCount = data.getInt(base + 4);
                int commandFirstIndex = data.getInt(base + 8);
                int vertexOffsetValue = data.getInt(base + 12);
                int firstInstance = data.getInt(base + 16);

                if (slot < visibleCount) {
                    if (indexCount != 3 || instanceCount != 1 || vertexOffsetValue != 0 || firstInstance != 0) {
                        throw new IllegalStateException("Malformed compacted command in slot " + slot);
                    }
                    if (commandFirstIndex == expectedA && !seenA) {
                        seenA = true;
                    } else if (commandFirstIndex == expectedB && !seenB) {
                        seenB = true;
                    } else {
                        throw new IllegalStateException(
                                "Unexpected/duplicate compacted firstIndex " + commandFirstIndex + " in slot " + slot);
                    }
                } else if (indexCount != 0
                        || instanceCount != 0
                        || commandFirstIndex != 0
                        || vertexOffsetValue != 0
                        || firstInstance != 0) {
                    throw new IllegalStateException("Unused indirect tail slot " + slot + " was not fully zeroed");
                }
            }

            if (!seenA || !seenB) {
                throw new IllegalStateException("Compacted command set did not contain both expected visible candidates");
            }
        }
    }

    private static ByteBuffer vertexData() {
        ByteBuffer data = ByteBuffer.allocateDirect(VERTEX_BYTES).order(ByteOrder.nativeOrder());
        for (float centerX : CENTERS_X) {
            putVertex(data, centerX - 0.16f, -0.30f, 0.0f);
            putVertex(data, centerX + 0.16f, -0.30f, 0.0f);
            putVertex(data, centerX, 0.30f, 0.0f);
        }
        return data.flip();
    }

    private static ByteBuffer indexData() {
        ByteBuffer data = ByteBuffer.allocateDirect(INDEX_BYTES).order(ByteOrder.nativeOrder());
        for (short i = 0; i < INDEX_COUNT; i++) {
            data.putShort(i);
        }
        return data.flip();
    }

    private static ByteBuffer sceneData(int baseFirstIndex) {
        ByteBuffer data = ByteBuffer.allocateDirect(SCENE_BYTES).order(ByteOrder.nativeOrder());
        for (int i = 0; i < VulkanVisibilityCompactor.CANDIDATE_COUNT; i++) {
            data.putInt(baseFirstIndex + i * 3);
            data.putFloat(CENTERS_X[i]);
            data.putFloat(0.0f);
            data.putInt(0);
        }
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
                    "Visibility " + label + " pixel mismatch at (" + x + "," + y + "): expected="
                            + expectedR + "/" + expectedG + "/" + expectedB + "/" + expectedA
                            + ", actual=" + r + "/" + g + "/" + b + "/" + a);
        }
    }

    private void cancelUnsubmittedAllocations() {
        if (vertexHandle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(vertexHandle)) {
            try {
                arena.cancelUnsubmitted(vertexHandle);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to cancel unsubmitted dev9 vertex allocation.", e);
            }
        }
        if (indexHandle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(indexHandle)) {
            try {
                arena.cancelUnsubmitted(indexHandle);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to cancel unsubmitted dev9 index allocation.", e);
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
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev9 target view.", e);
            }
            targetView = null;
        }
        if (target != null) {
            try {
                target.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev9 target texture.", e);
            }
            target = null;
        }
        if (pixelReadback != null) {
            try {
                pixelReadback.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev9 pixel readback.", e);
            }
            pixelReadback = null;
        }
        if (outputReadback != null) {
            try {
                outputReadback.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev9 output readback.", e);
            }
            outputReadback = null;
        }
        if (compactor != null) {
            try {
                compactor.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev9 visibility compactor.", e);
            }
            compactor = null;
        }
    }

    private void abandonLocalResources() {
        targetView = null;
        target = null;
        pixelReadback = null;
        outputReadback = null;
        compactor = null;
    }
}
