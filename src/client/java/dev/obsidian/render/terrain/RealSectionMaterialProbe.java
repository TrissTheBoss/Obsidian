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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Phase 2 dev3 correctness probe: materializes the permanent real-section
 * reference faces through exact Minecraft 26.2 baked model/sprite/tint data,
 * uploads the immutable POSITION_TEX_COLOR mesh, and overlays it on the live
 * vanilla world with the same placement/depth path proven by P2.2.
 *
 * <p>The overlay is intentionally 3/4 RGB so otherwise-correct textures remain
 * visually distinguishable from vanilla. The exact captured tint identity is
 * retained separately in {@link SectionMaterialSnapshot}. Lighting/AO remain
 * P2.4 and are not smuggled into this proof.</p>
 */
public final class RealSectionMaterialProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/RealSectionMaterialProbe");

    private static final Supplier<String> PASS_LABEL = () -> "Obsidian Phase 2 dev3 textured section comparison";
    private static final Supplier<String> INDIRECT_LABEL = () -> "Obsidian Phase 2 dev3 indexed indirect command";

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
    private SectionMaterialSnapshot materialSnapshot;
    private MaterializedSectionMesh drawableMesh;

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
    private long resourceEpochChecks;
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

    public RealSectionMaterialProbe(
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
            throw new IllegalStateException("Phase 2 dev3 requires indexed indirect drawing");
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
                    "Phase 2 dev3 textured comparison draw failed; beginning completion-gated cleanup.", e);
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
                throw new IllegalStateException("Phase 2 dev3 reference oracle is empty or nondeterministic");
            }

            SectionMaterialSnapshot firstMaterials = SectionMaterialSnapshot.capture(captured, firstReference);
            SectionMaterialSnapshot secondMaterials = SectionMaterialSnapshot.capture(captured, firstReference);
            if (!firstMaterials.contentEquals(secondMaterials)) {
                throw new IllegalStateException("Phase 2 dev3 material extraction is nondeterministic");
            }
            if (firstMaterials.supportedFaces() <= 0) {
                throw new IllegalStateException(
                        "Phase 2 dev3 found no conservative SOLID faces with exact compatible baked material identity");
            }

            MaterializedSectionMesh firstDrawable =
                    MaterializedSectionMesh.build(captured, firstReference, firstMaterials);
            MaterializedSectionMesh secondDrawable =
                    MaterializedSectionMesh.build(captured, firstReference, firstMaterials);
            if (!firstDrawable.contentEquals(secondDrawable)) {
                throw new IllegalStateException("Phase 2 dev3 materialized drawable mesh is nondeterministic");
            }
            if (firstDrawable.faceCount() != firstMaterials.supportedFaces()
                    || firstDrawable.vertexCount() != firstDrawable.faceCount() * MaterializedSectionMesh.VERTICES_PER_FACE
                    || firstDrawable.indexCount() != firstDrawable.faceCount() * MaterializedSectionMesh.INDICES_PER_FACE) {
                throw new IllegalStateException("Phase 2 dev3 materialized/reference accounting mismatch");
            }

            long newVertexHandle = arena.allocate(firstDrawable.vertexBytes(), 16);
            if (newVertexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 2 dev3 textured vertex data");
            }
            vertexHandle = newVertexHandle;

            long newIndexHandle = arena.allocate(firstDrawable.indexBytes(), 4);
            if (newIndexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 2 dev3 index data");
            }
            indexHandle = newIndexHandle;

            indirectCommands = new IndexedIndirectCommandBuffer(device, INDIRECT_LABEL, INDIRECT_COMMAND_COUNT);
            GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
            GpuBufferSlice indexSlice = arena.slice(indexHandle);
            int firstIndex = checkedFirstIndex(indexSlice);

            if (!staging.beginBatch()) {
                throw new IllegalStateException("Phase 2 dev3 could not open bounded staging batch");
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
                throw new IllegalStateException("Phase 2 dev3 geometry/indirect upload hit bounded staging backpressure");
            }

            snapshot = captured;
            referenceMesh = firstReference;
            materialSnapshot = firstMaterials;
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
                    "Phase 2 dev3 real section/materials captured and textured comparison started on frame {0}: section=({1},{2},{3}), origin=({4},{5},{6}), sampledCells={7}, interiorAir={8}, interiorSupported={9}, interiorUnsupported={10}, referenceFaces={11}, materializedFaces={12}, rejectedMaterialFaces={13}, materialCount={14}, tintedFaces={15}, tintWorldQueries={16}, rejectedMissing={17}, rejectedGeneralQuads={18}, rejectedDirectionalQuads={19}, rejectedLayer={20}, cutoutFaces={21}, translucentFaces={22}, rejectedAtlas={23}, rejectedGeometry={24}, rejectedTint={25}, snapshotFingerprint={26}, referenceFingerprint={27}, materialFingerprint={28}, drawableFingerprint={29}, resourceEpoch={30}, materialCaptureNs={31}, drawableBuildNs={32}, drawableVertices={33}, drawableIndices={34}, vertexBytes={35}, indexBytes={36}, firstIndex={37}, camera=({38},{39},{40}), cameraRelativeOrigin=({41},{42},{43}), deterministicReferenceBuilds=2, deterministicMaterialCaptures=2, deterministicDrawableBuilds=2, worldReadsAfterMaterialCapture=0, p2_4LightingAo=false, textured=true, blocksAtlasBound=true, comparisonColorScale=3/4, usefulSubmissions={44}, profilerOnlySubmissions=0, vanillaTerrainActive=true.",
                    frameSerial,
                    captured.sectionX(), captured.sectionY(), captured.sectionZ(),
                    firstDrawable.originX(), firstDrawable.originY(), firstDrawable.originZ(),
                    captured.sampledCells(), captured.interiorAirCells(), captured.interiorSupportedCells(),
                    captured.interiorUnsupportedCells(), firstReference.faceCount(), firstDrawable.faceCount(),
                    firstDrawable.rejectedReferenceFaces(), firstMaterials.materialCount(), firstMaterials.tintedFaces(),
                    firstMaterials.tintWorldQueries(), firstMaterials.rejectedMissingModelFaces(),
                    firstMaterials.rejectedGeneralQuadFaces(), firstMaterials.rejectedDirectionalQuadFaces(),
                    firstMaterials.rejectedLayerFaces(), firstMaterials.cutoutFaces(), firstMaterials.translucentFaces(),
                    firstMaterials.rejectedAtlasFaces(), firstMaterials.rejectedGeometryFaces(),
                    firstMaterials.rejectedTintFaces(), Long.toUnsignedString(captured.fingerprint()),
                    Long.toUnsignedString(firstReference.fingerprint()), Long.toUnsignedString(firstMaterials.fingerprint()),
                    Long.toUnsignedString(firstDrawable.fingerprint()), Long.toUnsignedString(firstMaterials.resourceEpoch()),
                    firstMaterials.captureTimeNs(), firstDrawable.buildTimeNs(), firstDrawable.vertexCount(),
                    firstDrawable.indexCount(), firstDrawable.vertexBytes(), firstDrawable.indexBytes(), firstIndex,
                    firstCameraX, firstCameraY, firstCameraZ, firstRelativeX, firstRelativeY, firstRelativeZ,
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
                    "Phase 2 dev3 real-section material setup failed; Minecraft will continue for diagnosis.", e);
        }
    }

    private void ensurePipeline() {
        if (pipeline != null) {
            return;
        }

        RenderPipeline textureTemplate = RenderPipelines.GUI_TEXTURED;
        RenderPipeline depthTemplate = RenderPipelines.DEBUG_QUADS;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation("obsidian_phase2_dev3_textured")
                .withVertexShader(textureTemplate.getVertexShader())
                .withFragmentShader(textureTemplate.getFragmentShader())
                .withCull(false)
                .withColorTargetState(textureTemplate.getColorTargetState())
                .withDepthStencilState(depthTemplate.getDepthStencilState())
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        for (BindGroupLayout layout : textureTemplate.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        pipeline = builder.build();

        CompiledRenderPipeline compiled = device.precompilePipeline(pipeline);
        pipelineValid = compiled.isValid();
        if (!pipelineValid) {
            throw new IllegalStateException("Phase 2 dev3 public textured Blaze3D comparison pipeline failed to compile");
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
                    "Phase 2 dev3 textured comparison window finished on frame {0}: comparisonDraws={1}, comparisonNs={2}, usefulSubmissions={3}, resourceEpochChecks={4}, profilerOnlySubmissions=0, indirectCalls={5}, trianglesSubmitted={6}; waiting nonblockingly for completion-gated reclamation.",
                    frameSerial,
                    drawSubmissions,
                    System.nanoTime() - comparisonStartNs,
                    usefulSubmissions,
                    resourceEpochChecks,
                    indirectCalls,
                    triangles);
        }
    }

    private void encodeLiveDraw(CommandEncoder encoder, GameRenderer renderer) {
        if (drawableMesh == null || materialSnapshot == null || indirectCommands == null) {
            throw new IllegalStateException("Phase 2 dev3 textured resources are incomplete");
        }

        long currentEpoch = SectionMaterialSnapshot.currentResourceEpoch();
        resourceEpochChecks++;
        if (currentEpoch != materialSnapshot.resourceEpoch()) {
            throw new IllegalStateException(
                    "Minecraft model/blocks-atlas resource epoch changed during the P2.3 comparison; stale materialized geometry will not be submitted");
        }

        CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || camera.pos == null || camera.viewRotationMatrix == null) {
            throw new IllegalStateException("Minecraft world camera render state is unavailable");
        }

        double relativeX = drawableMesh.originX() - camera.pos.x;
        double relativeY = drawableMesh.originY() - camera.pos.y;
        double relativeZ = drawableMesh.originZ() - camera.pos.z;
        if (!Double.isFinite(relativeX) || !Double.isFinite(relativeY) || !Double.isFinite(relativeZ)) {
            throw new IllegalStateException("Non-finite Phase 2 dev3 camera-relative section origin");
        }

        Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix)
                .translate((float) relativeX, (float) relativeY, (float) relativeZ);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(modelView);
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        if (dynamicTransforms == null || projection == null || RenderSystem.getGlobalSettingsUniform() == null) {
            throw new IllegalStateException("Minecraft world uniform buffers are unavailable");
        }

        Minecraft minecraft = Minecraft.getInstance();
        AbstractTexture blocksAtlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (blocksAtlas == null || blocksAtlas.getTextureView() == null || blocksAtlas.getSampler() == null) {
            throw new IllegalStateException("Minecraft live blocks atlas texture/sampler is unavailable");
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
            pass.bindTexture("Sampler0", blocksAtlas.getTextureView(), blocksAtlas.getSampler());
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
                    "Phase 2 dev3 resources reclaimed after a validation failure; see the earlier diagnostic.");
            return;
        }

        state = State.VERIFIED;
        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev3 textured real section verified on frame {0}: section=({1},{2},{3}), referenceFaces={4}, materializedFaces={5}, rejectedMaterialFaces={6}, materialCount={7}, tintedFaces={8}, tintWorldQueries={9}, snapshotFingerprint={10}, referenceFingerprint={11}, materialFingerprint={12}, drawableFingerprint={13}, resourceEpoch={14}, faceCount={15}, vertexCount={16}, indexCount={17}, vertexBytes={18}, indexBytes={19}, deterministicReferenceBuilds=2, deterministicMaterialCaptures=2, deterministicDrawableBuilds=2, worldReadsAfterMaterialCapture=0, pipelineValid={20}, nativeGraphicsSeam=false, indexedIndirect=true, textured=true, blocksAtlasBound=true, p2_4LightingAo=false, comparisonColorScale=3/4, resourceEpochChecks={21}, usefulSubmissions={22}, profilerOnlySubmissions=0, comparisonDraws={23}, arenaRetired={24}, arenaReclaimed={25}, arenaUsedBytes={26}, arenaFreeSpans={27}, arenaFragmentationPermille={28}, stagingSubmittedBytes={29}, stagingReclaimedBytes={30}, pendingUploadBatches={31}, pendingArenaRetirementBatches={32}, pendingResourceRetirements={33}, vanillaTerrainActive=true.",
                frameSerial,
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(), referenceMesh.faceCount(),
                drawableMesh.faceCount(), drawableMesh.rejectedReferenceFaces(), materialSnapshot.materialCount(),
                materialSnapshot.tintedFaces(), materialSnapshot.tintWorldQueries(),
                Long.toUnsignedString(snapshot.fingerprint()), Long.toUnsignedString(referenceMesh.fingerprint()),
                Long.toUnsignedString(materialSnapshot.fingerprint()), Long.toUnsignedString(drawableMesh.fingerprint()),
                Long.toUnsignedString(materialSnapshot.resourceEpoch()), drawableMesh.faceCount(), drawableMesh.vertexCount(),
                drawableMesh.indexCount(), drawableMesh.vertexBytes(), drawableMesh.indexBytes(), pipelineValid,
                resourceEpochChecks, usefulSubmissions, drawSubmissions, arena.retiredAllocations(), arena.reclaimedAllocations(),
                arena.usedBytes(), arena.freeSpanCount(), arena.fragmentationPermille(), staging.submittedBytes(),
                staging.reclaimedBytes(), staging.pendingBatches(), arena.pendingRetirementBatches(),
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
                    "Phase 2 dev3 could not submit its completion-gated failure cleanup; resources will be left for bounded shutdown/device teardown rather than destroyed in flight.",
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
                        "Phase 2 dev3 arena retirement registration failed; preserving the completion handle for retry.", e);
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
                        "Phase 2 dev3 indirect-command retirement registration failed; preserving the completion handle for retry.", e);
                return false;
            }
        }
        return pendingArenaFence == null && pendingResourceFence == null;
    }

    private int checkedFirstIndex(GpuBufferSlice indexSlice) {
        if ((indexSlice.offset() & (Integer.BYTES - 1L)) != 0L) {
            throw new IllegalStateException("Phase 2 dev3 index allocation is not 4-byte aligned");
        }
        long value = indexSlice.offset() / Integer.BYTES;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Phase 2 dev3 firstIndex exceeds public indirect command range");
        }
        return (int) value;
    }

    private static ByteBuffer indirectCommand(int indexCount, int firstIndex) {
        ByteBuffer out = ByteBuffer.allocateDirect(IndexedIndirectCommandBuffer.COMMAND_BYTES)
                .order(ByteOrder.nativeOrder());
        out.putInt(indexCount);
        out.putInt(1);
        out.putInt(firstIndex);
        out.putInt(0);
        out.putInt(0);
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
            LOG.log(System.Logger.Level.WARNING, "Failed to close unsubmitted dev3 indirect command buffer.", e);
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

    public State state() { return state; }
    public SectionSnapshot snapshot() { return snapshot; }
    public ReferenceFaceMesh referenceMesh() { return referenceMesh; }
    public SectionMaterialSnapshot materialSnapshot() { return materialSnapshot; }
    public MaterializedSectionMesh drawableMesh() { return drawableMesh; }
    public long usefulSubmissions() { return usefulSubmissions; }
    public long drawSubmissions() { return drawSubmissions; }
    public long resourceEpochChecks() { return resourceEpochChecks; }
    public long retirementBackpressureEvents() { return retirementBackpressureEvents; }
    public long retirementRegistrationFailures() { return retirementRegistrationFailures; }
    public boolean pipelineValid() { return pipelineValid; }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) {
            return;
        }

        if (state == State.COMPARING) {
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