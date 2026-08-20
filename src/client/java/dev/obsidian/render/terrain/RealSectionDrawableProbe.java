package dev.obsidian.render.terrain;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.obsidian.render.draw.IndexedIndirectCommandBuffer;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Phase 2 dev2 probe: turns the permanent real-section reference oracle into
 * actual arena-backed indexed geometry and overlays it on the live vanilla
 * world for a short, bounded comparison window.
 *
 * <p>The overlay intentionally uses Minecraft's own position/color debug shader
 * contract and orientation colors rather than pretending P2.3 texture/material
 * or P2.4 light/AO semantics already exist. Vanilla terrain remains active.</p>
 */
public final class RealSectionDrawableProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/RealSectionDrawableProbe");

    private static final Supplier<String> PASS_LABEL = () -> "Obsidian Phase 2 dev2 live section comparison";
    private static final Supplier<String> INDIRECT_LABEL = () -> "Obsidian Phase 2 dev2 indexed indirect command";

    private static final int INDIRECT_COMMAND_COUNT = 1;
    private static final long COMPARISON_WINDOW_NS = 1_500_000_000L;
    private static final int MAX_COMPARISON_DRAWS = 90;

    public enum State {
        WAITING_WORLD,
        COMPARING,
        RETIRING,
        VERIFIED,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final DeviceGeometryArena arena;
    private final DeferredReleaseQueue deferredReleases;
    private final long[] retirementHandles = new long[2];

    private RenderPipeline pipeline;
    private IndexedIndirectCommandBuffer indirectCommands;
    private GpuFence pendingArenaFence;
    private GpuFence pendingResourceFence;

    private SectionSnapshot snapshot;
    private ReferenceFaceMesh referenceMesh;
    private DrawableSectionMesh drawableMesh;

    private long vertexHandle = DeviceGeometryArena.INVALID_HANDLE;
    private long indexHandle = DeviceGeometryArena.INVALID_HANDLE;
    private long uploadBatchOrdinal;
    private long resourceReleaseOrdinal;
    private long comparisonStartNs;
    private long lastFrameSerial;
    private long usefulSubmissions;
    private long drawSubmissions;
    private long indirectCalls;
    private long triangles;
    private long retirementBackpressureEvents;
    private long retirementRegistrationFailures;

    private double firstCameraX;
    private double firstCameraY;
    private double firstCameraZ;
    private double firstRelativeX;
    private double firstRelativeY;
    private double firstRelativeZ;
    private boolean firstTransformCaptured;
    private boolean pipelineValid;
    private boolean initialSubmissionCompleted;
    private Throwable validationFailure;
    private State state = State.WAITING_WORLD;

    public RealSectionDrawableProbe(
            GpuDevice device,
            StagingUploadArena staging,
            DeviceGeometryArena arena,
            DeferredReleaseQueue deferredReleases) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.staging = staging;
        this.arena = arena;
        this.deferredReleases = deferredReleases;

        if (!device.getDeviceInfo().features().drawIndirect()) {
            throw new IllegalStateException("Phase 2 dev2 requires indexed indirect drawing");
        }
    }

    /** Called immediately after vanilla LevelRenderer.render while world projection/depth are still active. */
    public void afterWorldRender(GameRenderer renderer, long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED || state == State.VERIFIED || state == State.FAILED || state == State.RETIRING) {
            return;
        }
        lastFrameSerial = frameSerial;

        if (state == State.WAITING_WORLD) {
            tryCaptureBuildUploadAndDraw(renderer, frameSerial);
            return;
        }

        long elapsed = System.nanoTime() - comparisonStartNs;
        boolean finalDraw = drawSubmissions >= MAX_COMPARISON_DRAWS || elapsed >= COMPARISON_WINDOW_NS;
        try {
            submitDraw(renderer, frameSerial, finalDraw);
        } catch (RuntimeException e) {
            validationFailure = e;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev2 live comparison draw failed; beginning completion-gated cleanup.", e);
            beginCleanupRetirement(frameSerial);
        }
    }

    private void tryCaptureBuildUploadAndDraw(GameRenderer renderer, long frameSerial) {
        SectionSnapshot captured = SectionSnapshot.tryCaptureNearPlayer();
        if (captured == null) {
            return;
        }

        boolean batchOpen = false;
        try {
            ensurePipeline();

            ReferenceFaceMesh firstReference = ReferenceFaceMesh.build(captured);
            ReferenceFaceMesh secondReference = ReferenceFaceMesh.build(captured);
            if (firstReference.faceCount() <= 0 || !firstReference.contentEquals(secondReference)) {
                throw new IllegalStateException("Phase 2 dev2 reference oracle is empty or nondeterministic");
            }

            DrawableSectionMesh firstDrawable = DrawableSectionMesh.build(captured, firstReference);
            DrawableSectionMesh secondDrawable = DrawableSectionMesh.build(captured, firstReference);
            if (!firstDrawable.contentEquals(secondDrawable)) {
                throw new IllegalStateException("Phase 2 dev2 drawable mesh is nondeterministic");
            }
            if (firstDrawable.faceCount() != firstReference.faceCount()
                    || firstDrawable.vertexCount() != firstReference.faceCount() * DrawableSectionMesh.VERTICES_PER_FACE
                    || firstDrawable.indexCount() != firstReference.faceCount() * DrawableSectionMesh.INDICES_PER_FACE) {
                throw new IllegalStateException("Phase 2 dev2 drawable/reference accounting mismatch");
            }

            long newVertexHandle = arena.allocate(firstDrawable.vertexBytes(), 16);
            if (newVertexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 2 dev2 vertex data");
            }
            vertexHandle = newVertexHandle;

            long newIndexHandle = arena.allocate(firstDrawable.indexBytes(), 4);
            if (newIndexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 2 dev2 index data");
            }
            indexHandle = newIndexHandle;

            indirectCommands = new IndexedIndirectCommandBuffer(device, INDIRECT_LABEL, INDIRECT_COMMAND_COUNT);
            GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
            GpuBufferSlice indexSlice = arena.slice(indexHandle);
            int firstIndex = checkedFirstIndex(indexSlice);

            if (!staging.beginBatch()) {
                throw new IllegalStateException("Phase 2 dev2 could not open bounded staging batch");
            }
            batchOpen = true;

            CommandEncoder encoder = device.createCommandEncoder();
            if (!staging.stageCopy(encoder, firstDrawable.vertexBuffer(), vertexSlice)
                    || !staging.stageCopy(encoder, firstDrawable.indexBuffer(), indexSlice)
                    || !staging.stageCopy(
                            encoder,
                            indirectCommand(firstDrawable.indexCount(), firstIndex),
                            indirectCommands.buffer(),
                            0L)) {
                throw new IllegalStateException("Phase 2 dev2 geometry/indirect upload hit bounded staging backpressure");
            }

            snapshot = captured;
            referenceMesh = firstReference;
            drawableMesh = firstDrawable;
            encodeLiveDraw(encoder, renderer);

            staging.submitBatch(encoder);
            batchOpen = false;
            uploadBatchOrdinal = staging.submittedBatches();
            usefulSubmissions++;
            drawSubmissions++;
            indirectCalls++;
            triangles += firstDrawable.indexCount() / 3L;
            initialSubmissionCompleted = true;
            comparisonStartNs = System.nanoTime();
            state = State.COMPARING;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev2 real section captured, drawable mesh built, and live comparison started on frame {0}: section=({1},{2},{3}), origin=({4},{5},{6}), sampledCells={7}, interiorAir={8}, interiorSupported={9}, interiorUnsupported={10}, snapshotFingerprint={11}, referenceFaces={12}, referenceFingerprint={13}, drawableFaces={14}, drawableVertices={15}, drawableIndices={16}, drawableFingerprint={17}, drawableBuildNs={18}, vertexBytes={19}, indexBytes={20}, firstIndex={21}, camera=({22},{23},{24}), cameraRelativeOrigin=({25},{26},{27}), deterministicReferenceBuilds=2, deterministicDrawableBuilds=2, worldReadsAfterSnapshot=0, usefulSubmissions={28}, profilerOnlySubmissions=0, vanillaTerrainActive=true.",
                    frameSerial,
                    captured.sectionX(),
                    captured.sectionY(),
                    captured.sectionZ(),
                    firstDrawable.originX(),
                    firstDrawable.originY(),
                    firstDrawable.originZ(),
                    captured.sampledCells(),
                    captured.interiorAirCells(),
                    captured.interiorSupportedCells(),
                    captured.interiorUnsupportedCells(),
                    Long.toUnsignedString(captured.fingerprint()),
                    firstReference.faceCount(),
                    Long.toUnsignedString(firstReference.fingerprint()),
                    firstDrawable.faceCount(),
                    firstDrawable.vertexCount(),
                    firstDrawable.indexCount(),
                    Long.toUnsignedString(firstDrawable.fingerprint()),
                    firstDrawable.buildTimeNs(),
                    firstDrawable.vertexBytes(),
                    firstDrawable.indexBytes(),
                    firstIndex,
                    firstCameraX,
                    firstCameraY,
                    firstCameraZ,
                    firstRelativeX,
                    firstRelativeY,
                    firstRelativeZ,
                    usefulSubmissions);
        } catch (RuntimeException e) {
            if (batchOpen) {
                staging.abortBatch();
            }
            validationFailure = e;
            if (initialSubmissionCompleted) {
                beginCleanupRetirement(frameSerial);
            } else {
                cancelUnsubmittedAllocations();
                closeIndirectIfOwned();
                state = State.FAILED;
            }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev2 real-section drawable setup failed; Minecraft will continue for diagnosis.", e);
        }
    }

    private void ensurePipeline() {
        if (pipeline != null) {
            return;
        }

        RenderPipeline template = RenderPipelines.DEBUG_QUADS;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation("obsidian_phase2_dev2_drawable")
                .withVertexShader(template.getVertexShader())
                .withFragmentShader(template.getFragmentShader())
                .withCull(false)
                .withColorTargetState(template.getColorTargetState())
                .withDepthStencilState(template.getDepthStencilState())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        for (BindGroupLayout layout : template.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        pipeline = builder.build();

        CompiledRenderPipeline compiled = device.precompilePipeline(pipeline);
        pipelineValid = compiled.isValid();
        if (!pipelineValid) {
            throw new IllegalStateException("Phase 2 dev2 public Blaze3D comparison pipeline failed to compile");
        }
    }

    private void submitDraw(GameRenderer renderer, long frameSerial, boolean finalDraw) {
        CommandEncoder encoder = device.createCommandEncoder();
        encodeLiveDraw(encoder, renderer);

        GpuFence arenaFence = null;
        GpuFence resourceFence = null;
        if (finalDraw) {
            arenaFence = encoder.createFence();
            resourceFence = encoder.createFence();
        }

        try {
            encoder.submit();
        } catch (RuntimeException e) {
            closeFence(arenaFence);
            closeFence(resourceFence);
            throw e;
        }

        usefulSubmissions++;
        drawSubmissions++;
        indirectCalls++;
        triangles += drawableMesh.indexCount() / 3L;

        if (finalDraw) {
            pendingArenaFence = arenaFence;
            pendingResourceFence = resourceFence;
            retirementHandles[0] = vertexHandle;
            retirementHandles[1] = indexHandle;
            state = State.RETIRING;
            tryRegisterRetirements(frameSerial);

            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev2 comparison window finished on frame {0}: comparisonDraws={1}, comparisonNs={2}, usefulSubmissions={3}, profilerOnlySubmissions=0, indirectCalls={4}, trianglesSubmitted={5}; waiting nonblockingly for completion-gated reclamation.",
                    frameSerial,
                    drawSubmissions,
                    System.nanoTime() - comparisonStartNs,
                    usefulSubmissions,
                    indirectCalls,
                    triangles);
        }
    }

    private void encodeLiveDraw(CommandEncoder encoder, GameRenderer renderer) {
        if (drawableMesh == null || indirectCommands == null) {
            throw new IllegalStateException("Phase 2 dev2 drawable resources are incomplete");
        }

        CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || camera.pos == null || camera.viewRotationMatrix == null) {
            throw new IllegalStateException("Minecraft world camera render state is unavailable");
        }

        double relativeX = drawableMesh.originX() - camera.pos.x;
        double relativeY = drawableMesh.originY() - camera.pos.y;
        double relativeZ = drawableMesh.originZ() - camera.pos.z;
        if (!Double.isFinite(relativeX) || !Double.isFinite(relativeY) || !Double.isFinite(relativeZ)) {
            throw new IllegalStateException("Non-finite Phase 2 dev2 camera-relative section origin");
        }

        Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix)
                .translate((float) relativeX, (float) relativeY, (float) relativeZ);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(modelView);
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        if (dynamicTransforms == null || projection == null || RenderSystem.getGlobalSettingsUniform() == null) {
            throw new IllegalStateException("Minecraft world uniform buffers are unavailable");
        }

        RenderTarget target = renderer.mainRenderTarget();
        if (target == null || !target.useDepth
                || target.getColorTextureView() == null
                || target.getDepthTextureView() == null) {
            throw new IllegalStateException("Minecraft main world color/depth target is unavailable");
        }

        GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
        GpuBufferSlice indexSlice = arena.slice(indexHandle);

        try (RenderPass pass = encoder.createRenderPass(
                PASS_LABEL,
                target.getColorTextureView(),
                Optional.empty(),
                target.getDepthTextureView(),
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("Projection", projection);
            pass.setVertexBuffer(0, vertexSlice);
            pass.setIndexBuffer(indexSlice.buffer(), IndexType.INT);
            pass.drawIndexedIndirect(indirectCommands.slice(INDIRECT_COMMAND_COUNT), INDIRECT_COMMAND_COUNT);
        }

        if (!firstTransformCaptured) {
            firstTransformCaptured = true;
            firstCameraX = camera.pos.x;
            firstCameraY = camera.pos.y;
            firstCameraZ = camera.pos.z;
            firstRelativeX = relativeX;
            firstRelativeY = relativeY;
            firstRelativeZ = relativeZ;
        }
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        lastFrameSerial = frameSerial;
        if (state != State.RETIRING) {
            return;
        }

        if (!tryRegisterRetirements(frameSerial)) {
            return;
        }
        if (staging.reclaimedBatches() < uploadBatchOrdinal) {
            return;
        }
        if (arena.isAllocated(vertexHandle) || arena.isAllocated(indexHandle)) {
            return;
        }
        if (resourceReleaseOrdinal > 0L && deferredReleases.releasedCount() < resourceReleaseOrdinal) {
            return;
        }

        if (validationFailure != null) {
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev2 resources reclaimed after a validation failure; see the earlier diagnostic.");
            return;
        }

        state = State.VERIFIED;
        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev2 drawable real section verified on frame {0}: section=({1},{2},{3}), snapshotFingerprint={4}, referenceFingerprint={5}, drawableFingerprint={6}, faceCount={7}, vertexCount={8}, indexCount={9}, vertexBytes={10}, indexBytes={11}, deterministicReferenceBuilds=2, deterministicDrawableBuilds=2, worldReadsAfterSnapshot=0, pipelineValid={12}, nativeGraphicsSeam=false, indexedIndirect=true, usefulSubmissions={13}, profilerOnlySubmissions=0, comparisonDraws={14}, arenaRetired={15}, arenaReclaimed={16}, arenaUsedBytes={17}, arenaFreeSpans={18}, arenaFragmentationPermille={19}, stagingSubmittedBytes={20}, stagingReclaimedBytes={21}, pendingUploadBatches={22}, pendingArenaRetirementBatches={23}, pendingResourceRetirements={24}, vanillaTerrainActive=true.",
                frameSerial,
                snapshot.sectionX(),
                snapshot.sectionY(),
                snapshot.sectionZ(),
                Long.toUnsignedString(snapshot.fingerprint()),
                Long.toUnsignedString(referenceMesh.fingerprint()),
                Long.toUnsignedString(drawableMesh.fingerprint()),
                drawableMesh.faceCount(),
                drawableMesh.vertexCount(),
                drawableMesh.indexCount(),
                drawableMesh.vertexBytes(),
                drawableMesh.indexBytes(),
                pipelineValid,
                usefulSubmissions,
                drawSubmissions,
                arena.retiredAllocations(),
                arena.reclaimedAllocations(),
                arena.usedBytes(),
                arena.freeSpanCount(),
                arena.fragmentationPermille(),
                staging.submittedBytes(),
                staging.reclaimedBytes(),
                staging.pendingBatches(),
                arena.pendingRetirementBatches(),
                deferredReleases.pendingCount());
    }

    private void beginCleanupRetirement(long frameSerial) {
        if (!initialSubmissionCompleted || state == State.RETIRING) {
            state = initialSubmissionCompleted ? State.RETIRING : State.FAILED;
            return;
        }

        try {
            CommandEncoder encoder = device.createCommandEncoder();
            pendingArenaFence = encoder.createFence();
            pendingResourceFence = encoder.createFence();
            encoder.submit();
            usefulSubmissions++;
            retirementHandles[0] = vertexHandle;
            retirementHandles[1] = indexHandle;
            state = State.RETIRING;
            tryRegisterRetirements(frameSerial);
        } catch (RuntimeException cleanupFailure) {
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev2 could not submit its completion-gated failure cleanup; resources will be left for bounded shutdown/device teardown rather than destroyed in flight.",
                    cleanupFailure);
            state = State.FAILED;
        }
    }

    private boolean tryRegisterRetirements(long frameSerial) {
        if (pendingArenaFence != null) {
            try {
                if (!arena.retireBatch(pendingArenaFence, retirementHandles, 2)) {
                    retirementBackpressureEvents++;
                    return false;
                }
                pendingArenaFence = null;
            } catch (RuntimeException e) {
                retirementRegistrationFailures++;
                LOG.log(System.Logger.Level.ERROR,
                        "Phase 2 dev2 arena retirement registration failed; preserving the completion handle for retry.", e);
                return false;
            }
        }

        if (pendingResourceFence != null && indirectCommands != null) {
            try {
                deferredReleases.retire(indirectCommands, pendingResourceFence, frameSerial);
                resourceReleaseOrdinal = deferredReleases.retiredCount();
                indirectCommands = null;
                pendingResourceFence = null;
            } catch (RuntimeException e) {
                retirementRegistrationFailures++;
                LOG.log(System.Logger.Level.ERROR,
                        "Phase 2 dev2 indirect-command retirement registration failed; preserving the completion handle for retry.", e);
                return false;
            }
        }
        return pendingArenaFence == null && pendingResourceFence == null;
    }

    private int checkedFirstIndex(GpuBufferSlice indexSlice) {
        if ((indexSlice.offset() & (Integer.BYTES - 1L)) != 0L) {
            throw new IllegalStateException("Phase 2 dev2 index allocation is not 4-byte aligned");
        }
        long value = indexSlice.offset() / Integer.BYTES;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Phase 2 dev2 firstIndex exceeds public indirect command range");
        }
        return (int) value;
    }

    private static ByteBuffer indirectCommand(int indexCount, int firstIndex) {
        ByteBuffer out = ByteBuffer.allocateDirect(IndexedIndirectCommandBuffer.COMMAND_BYTES)
                .order(ByteOrder.nativeOrder());
        out.putInt(indexCount);
        out.putInt(1); // instanceCount
        out.putInt(firstIndex);
        out.putInt(0); // vertexOffset: vertex binding is the allocation slice itself
        out.putInt(0); // firstInstance
        return out.flip();
    }

    private void cancelUnsubmittedAllocations() {
        if (vertexHandle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(vertexHandle)) {
            arena.cancelUnsubmitted(vertexHandle);
        }
        if (indexHandle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(indexHandle)) {
            arena.cancelUnsubmitted(indexHandle);
        }
        vertexHandle = DeviceGeometryArena.INVALID_HANDLE;
        indexHandle = DeviceGeometryArena.INVALID_HANDLE;
    }

    private void closeIndirectIfOwned() {
        if (indirectCommands == null) {
            return;
        }
        try {
            indirectCommands.close();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to close unsubmitted dev2 indirect command buffer.", e);
        } finally {
            indirectCommands = null;
        }
    }

    private static void closeFence(GpuFence fence) {
        if (fence == null) {
            return;
        }
        try {
            fence.close();
        } catch (RuntimeException ignored) {
            // Preserve the useful submission failure.
        }
    }

    public State state() {
        return state;
    }

    public SectionSnapshot snapshot() {
        return snapshot;
    }

    public ReferenceFaceMesh referenceMesh() {
        return referenceMesh;
    }

    public DrawableSectionMesh drawableMesh() {
        return drawableMesh;
    }

    public long usefulSubmissions() {
        return usefulSubmissions;
    }

    public long drawSubmissions() {
        return drawSubmissions;
    }

    public long retirementBackpressureEvents() {
        return retirementBackpressureEvents;
    }

    public long retirementRegistrationFailures() {
        return retirementRegistrationFailures;
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

        if (state == State.COMPARING) {
            // A bounded shutdown may occur before the comparison window ends.
            // Submit one ordering point so the last live draw is completion-gated.
            beginCleanupRetirement(lastFrameSerial);
        }
        if (state == State.RETIRING) {
            tryRegisterRetirements(lastFrameSerial);
        }
        if (!initialSubmissionCompleted) {
            cancelUnsubmittedAllocations();
            closeIndirectIfOwned();
        }

        state = State.CLOSED;
    }
}
