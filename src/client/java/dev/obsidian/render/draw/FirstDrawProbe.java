package dev.obsidian.render.draw;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import dev.obsidian.render.upload.StagingUploadArena;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One-shot offscreen graphics validation for the first Obsidian-owned draw.
 *
 * <p>The probe uploads indexed triangle geometry, renders to a private 16x16
 * RGBA8 target, copies that target to a readback buffer, and verifies one
 * interior and one exterior pixel. Upload, draw, readback, timestamps, and the
 * staging completion fence all live in one useful command submission. The
 * target is never presented, so normal Minecraft output is unchanged.</p>
 */
public final class FirstDrawProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FirstDrawProbe");

    private static final Supplier<String> TARGET_LABEL = () -> "Obsidian Phase 1 first draw target";
    private static final Supplier<String> VERTEX_LABEL = () -> "Obsidian Phase 1 first draw vertex buffer";
    private static final Supplier<String> INDEX_LABEL = () -> "Obsidian Phase 1 first draw index buffer";
    private static final Supplier<String> READBACK_LABEL = () -> "Obsidian Phase 1 first draw readback";
    private static final Supplier<String> PASS_LABEL = () -> "Obsidian Phase 1 first draw render pass";
    private static final Runnable NOOP_COMPLETION = () -> {};

    private static final int PASS_GEOMETRY_UPLOAD = 0;
    private static final int PASS_OFFSCREEN_DRAW = 1;
    private static final int PASS_READBACK = 2;

    private static final int TARGET_WIDTH = 16;
    private static final int TARGET_HEIGHT = 16;
    private static final int BYTES_PER_PIXEL = 4;
    private static final int READBACK_BYTES = TARGET_WIDTH * TARGET_HEIGHT * BYTES_PER_PIXEL;
    private static final int VERTEX_COUNT = 3;
    private static final int INDEX_COUNT = 3;
    private static final int TRIANGLE_COUNT = 1;
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
        throw new IllegalArgumentException("Unsupported first-draw shader type: " + type);
    };

    public enum State {
        ARMED,
        SUBMITTED,
        VERIFIED,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private final FixedFrameGraph graph = new FixedFrameGraph();
    private final GpuTimestampProfiler profiler;
    private final FrameGraphCommandStream stream;
    private final RenderPipeline pipeline;

    private GpuTexture target;
    private GpuTextureView targetView;
    private GpuBuffer vertexBuffer;
    private GpuBuffer indexBuffer;
    private GpuBuffer readbackBuffer;

    private State state = State.ARMED;
    private long submittedFrame = -1L;
    private long verifiedFrame = -1L;
    private long drawCalls;
    private long triangles;
    private boolean pipelineValid;

    public FirstDrawProbe(GpuDevice device, StagingUploadArena staging) {
        RenderSystem.assertOnRenderThread();
        this.device = device;

        graph.definePass(PASS_GEOMETRY_UPLOAD, "first-draw-geometry-upload", 0L);
        graph.definePass(PASS_OFFSCREEN_DRAW, "first-draw-offscreen-render", 1L << PASS_GEOMETRY_UPLOAD);
        graph.definePass(PASS_READBACK, "first-draw-readback", 1L << PASS_OFFSCREEN_DRAW);

        profiler = new GpuTimestampProfiler(device, graph.passCount());
        stream = new FrameGraphCommandStream(device, staging, graph, profiler);

        pipeline = RenderPipeline.builder()
                .withLocation("obsidian_first_draw")
                .withVertexShader("obsidian_first_draw")
                .withFragmentShader("obsidian_first_draw")
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
            throw new IllegalStateException("Obsidian first-draw graphics pipeline failed to compile");
        }
    }

    public void submit(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.ARMED) {
            return;
        }

        try {
            createResources();
            if (!stream.begin()) {
                return;
            }

            stream.beginPass(PASS_GEOMETRY_UPLOAD);
            if (!stream.stageCopy(vertexData(), vertexBuffer, 0L)
                    || !stream.stageCopy(indexData(), indexBuffer, 0L)) {
                throw new IllegalStateException("Dev6 first-draw geometry upload hit staging backpressure");
            }
            stream.endPass(PASS_GEOMETRY_UPLOAD);

            stream.beginPass(PASS_OFFSCREEN_DRAW);
            try (RenderPass pass = stream.createRenderPass(
                    PASS_LABEL,
                    targetView,
                    Optional.of(CLEAR_COLOR))) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, vertexBuffer.slice(0L, VERTEX_BYTES));
                pass.setIndexBuffer(indexBuffer, IndexType.SHORT);
                // Vulkan argument order: indexCount, instanceCount, firstIndex,
                // vertexOffset, firstInstance.
                pass.drawIndexed(INDEX_COUNT, 1, 0, 0, 0);
                drawCalls++;
                triangles += TRIANGLE_COUNT;
            }
            stream.endPass(PASS_OFFSCREEN_DRAW);

            stream.beginPass(PASS_READBACK);
            stream.copyTextureToBuffer(
                    target,
                    readbackBuffer,
                    0L,
                    NOOP_COMPLETION,
                    0);
            stream.endPass(PASS_READBACK);

            stream.submit();
            submittedFrame = frameSerial;
            state = State.SUBMITTED;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 first draw submitted on frame {0}: graphPasses={1}, usefulSubmissions={2}, profilerOnlySubmissions=0, drawCalls={3}, triangles={4}, target={5}x{6} RGBA8, vertexBytes={7}, indexBytes={8}, stagingPayloadBytes={9}, pipelineValid={10}.",
                    frameSerial,
                    graph.passCount(),
                    stream.submissionCount(),
                    drawCalls,
                    triangles,
                    TARGET_WIDTH,
                    TARGET_HEIGHT,
                    VERTEX_BYTES,
                    INDEX_BYTES,
                    STAGING_PAYLOAD_BYTES,
                    pipelineValid);
        } catch (RuntimeException e) {
            stream.abort();
            state = State.FAILED;
            closeResourcesIfSafe();
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 first-draw submission failed; Minecraft will continue for diagnosis.",
                    e);
        }
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.SUBMITTED || readbackBuffer == null) {
            return;
        }
        if (!stream.isSubmissionComplete()) {
            return;
        }
        if (!stream.pollProfiler()) {
            return;
        }

        try {
            verifyPixels();
            verifiedFrame = frameSerial;
            state = State.VERIFIED;
            closeResourcesIfSafe();

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 first draw verified on frame {0} after {1} frame(s): executedMask={2}, uploadCpuNs={3}, drawCpuNs={4}, readbackCpuNs={5}, uploadGpuNs={6}, drawGpuNs={7}, readbackGpuNs={8}, totalGpuNs={9}, queryPolls={10}, unavailablePolls={11}, usefulSubmissions={12}, profilerOnlySubmissions=0, drawCalls={13}, triangles={14}, centerRGBA=255/0/255/255, cornerRGBA=0/0/0/255, pixelsVerified=2.",
                    verifiedFrame,
                    verifiedFrame - submittedFrame,
                    Long.toUnsignedString(graph.executedMask()),
                    graph.lastCpuNs(PASS_GEOMETRY_UPLOAD),
                    graph.lastCpuNs(PASS_OFFSCREEN_DRAW),
                    graph.lastCpuNs(PASS_READBACK),
                    profiler.passGpuNs(PASS_GEOMETRY_UPLOAD),
                    profiler.passGpuNs(PASS_OFFSCREEN_DRAW),
                    profiler.passGpuNs(PASS_READBACK),
                    profiler.totalGpuNs(),
                    profiler.pollCount(),
                    profiler.unavailablePolls(),
                    stream.submissionCount(),
                    drawCalls,
                    triangles);
        } catch (RuntimeException e) {
            state = State.FAILED;
            closeResourcesIfSafe();
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 first draw completed but pixel/timestamp verification failed.",
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

    public long drawCalls() {
        return drawCalls;
    }

    public long triangles() {
        return triangles;
    }

    public boolean pipelineValid() {
        return pipelineValid;
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
                    "Dev6 first-draw resources are still referenced by GPU work; leaving offscreen target/buffers/query pool for Minecraft device shutdown rather than destroying them in flight.");
            abandonResources();
            state = State.CLOSED;
            return;
        }

        closeResourcesIfSafe();
        profiler.close();
        state = State.CLOSED;
    }

    private void createResources() {
        if (target != null) {
            return;
        }

        target = device.createTexture(
                TARGET_LABEL,
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                GpuFormat.RGBA8_UNORM,
                TARGET_WIDTH,
                TARGET_HEIGHT,
                1,
                1);
        targetView = device.createTextureView(target);
        vertexBuffer = device.createBuffer(
                VERTEX_LABEL,
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_VERTEX,
                VERTEX_BYTES);
        indexBuffer = device.createBuffer(
                INDEX_LABEL,
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_INDEX,
                INDEX_BYTES);
        readbackBuffer = device.createBuffer(
                READBACK_LABEL,
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                READBACK_BYTES);
    }

    private void verifyPixels() {
        try (GpuBufferSlice.MappedView mapped = readbackBuffer.map(true, false)) {
            ByteBuffer data = mapped.data();
            requirePixel(data, TARGET_WIDTH / 2, TARGET_HEIGHT / 2, 255, 0, 255, 255, "center");
            requirePixel(data, 0, 0, 0, 0, 0, 255, "corner");
        }
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
                    "First-draw " + label + " pixel mismatch at (" + x + "," + y + "): expected="
                            + expectedR + "/" + expectedG + "/" + expectedB + "/" + expectedA
                            + ", actual=" + r + "/" + g + "/" + b + "/" + a);
        }
    }

    private static ByteBuffer vertexData() {
        ByteBuffer data = ByteBuffer.allocateDirect(VERTEX_BYTES).order(ByteOrder.nativeOrder());
        putVertex(data, -0.75f, -0.75f, 0.0f);
        putVertex(data, 0.75f, -0.75f, 0.0f);
        putVertex(data, 0.0f, 0.75f, 0.0f);
        return data.flip();
    }

    private static ByteBuffer indexData() {
        ByteBuffer data = ByteBuffer.allocateDirect(INDEX_BYTES).order(ByteOrder.nativeOrder());
        data.putShort((short) 0);
        data.putShort((short) 1);
        data.putShort((short) 2);
        return data.flip();
    }

    private static void putVertex(ByteBuffer data, float x, float y, float z) {
        data.putFloat(x);
        data.putFloat(y);
        data.putFloat(z);
    }

    private void closeResourcesIfSafe() {
        if (targetView != null) {
            try {
                targetView.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev6 target view.", e);
            }
            targetView = null;
        }
        if (target != null) {
            try {
                target.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev6 target texture.", e);
            }
            target = null;
        }
        if (vertexBuffer != null) {
            try {
                vertexBuffer.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev6 vertex buffer.", e);
            }
            vertexBuffer = null;
        }
        if (indexBuffer != null) {
            try {
                indexBuffer.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev6 index buffer.", e);
            }
            indexBuffer = null;
        }
        if (readbackBuffer != null) {
            try {
                readbackBuffer.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close dev6 readback buffer.", e);
            }
            readbackBuffer = null;
        }
    }

    private void abandonResources() {
        targetView = null;
        target = null;
        vertexBuffer = null;
        indexBuffer = null;
        readbackBuffer = null;
    }
}
