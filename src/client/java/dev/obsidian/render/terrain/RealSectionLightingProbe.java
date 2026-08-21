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
import com.mojang.blaze3d.textures.FilterMode;
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
 * Phase 2 dev4 correctness probe: exact vanilla BlockModelLighter results are
 * frozen into an immutable capture, built into BLOCK-format geometry, then
 * compared against the live vanilla world with the P2.2/P2.3 placement and
 * completion-gated lifetime path.
 */
public final class RealSectionLightingProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/RealSectionLightingProbe");
    private static final Supplier<String> PASS_LABEL = () -> "Obsidian Phase 2 dev4 lit section comparison";
    private static final Supplier<String> INDIRECT_LABEL = () -> "Obsidian Phase 2 dev4 indexed indirect command";
    private static final int INDIRECT_COMMAND_COUNT = 1;
    private static final long COMPARISON_WINDOW_NS = 1_500_000_000L;
    private static final int MAX_COMPARISON_DRAWS = 90;

    public enum State { WAITING_WORLD, COMPARING, RETIRING, VERIFIED, FAILED, CLOSED }

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
    private SectionLightingSnapshot lightingSnapshot;
    private LitSectionMesh drawableMesh;

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

    public RealSectionLightingProbe(
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
            throw new IllegalStateException("Phase 2 dev4 requires indexed indirect drawing");
        }
    }

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
                    "Phase 2 dev4 lit comparison draw failed; beginning completion-gated cleanup.", e);
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
                throw new IllegalStateException("Phase 2 dev4 reference oracle is empty or nondeterministic");
            }

            SectionMaterialSnapshot firstMaterials = SectionMaterialSnapshot.capture(captured, firstReference);
            SectionMaterialSnapshot secondMaterials = SectionMaterialSnapshot.capture(captured, firstReference);
            if (!firstMaterials.contentEquals(secondMaterials) || firstMaterials.supportedFaces() <= 0) {
                throw new IllegalStateException("Phase 2 dev4 material extraction is empty or nondeterministic");
            }

            SectionLightingSnapshot firstLighting =
                    SectionLightingSnapshot.capture(captured, firstReference, firstMaterials);
            SectionLightingSnapshot secondLighting =
                    SectionLightingSnapshot.capture(captured, firstReference, firstMaterials);
            if (!firstLighting.contentEquals(secondLighting)) {
                throw new IllegalStateException("Phase 2 dev4 vanilla light/AO capture is nondeterministic");
            }

            LitSectionMesh firstDrawable = LitSectionMesh.build(captured, firstReference, firstMaterials, firstLighting);
            LitSectionMesh secondDrawable = LitSectionMesh.build(captured, firstReference, firstMaterials, firstLighting);
            if (!firstDrawable.contentEquals(secondDrawable)) {
                throw new IllegalStateException("Phase 2 dev4 lit drawable mesh is nondeterministic");
            }
            if (firstDrawable.faceCount() != firstMaterials.supportedFaces()
                    || firstDrawable.faceCount() != firstLighting.supportedFaces()
                    || firstDrawable.vertexCount() != firstDrawable.faceCount() * LitSectionMesh.VERTICES_PER_FACE
                    || firstDrawable.indexCount() != firstDrawable.faceCount() * LitSectionMesh.INDICES_PER_FACE) {
                throw new IllegalStateException("Phase 2 dev4 light/material/drawable accounting mismatch");
            }

            vertexHandle = arena.allocate(firstDrawable.vertexBytes(), 16);
            if (vertexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 2 dev4 BLOCK vertex data");
            }
            indexHandle = arena.allocate(firstDrawable.indexBytes(), 4);
            if (indexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 2 dev4 index data");
            }

            indirectCommands = new IndexedIndirectCommandBuffer(device, INDIRECT_LABEL, INDIRECT_COMMAND_COUNT);
            GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
            GpuBufferSlice indexSlice = arena.slice(indexHandle);
            int firstIndex = checkedFirstIndex(indexSlice);

            if (!staging.beginBatch()) {
                throw new IllegalStateException("Phase 2 dev4 could not open bounded staging batch");
            }
            batchOpen = true;
            CommandEncoder encoder = device.createCommandEncoder();
            if (!staging.stageCopy(encoder, firstDrawable.vertexBuffer(), vertexSlice)
                    || !staging.stageCopy(encoder, firstDrawable.indexBuffer(), indexSlice)
                    || !staging.stageCopy(encoder, indirectCommand(firstDrawable.indexCount(), firstIndex),
                            indirectCommands.buffer(), 0L)) {
                throw new IllegalStateException("Phase 2 dev4 geometry/indirect upload hit bounded staging backpressure");
            }

            snapshot = captured;
            referenceMesh = firstReference;
            materialSnapshot = firstMaterials;
            lightingSnapshot = firstLighting;
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
                    "Phase 2 dev4 real section lighting captured and live comparison started on frame {0}: section=({1},{2},{3}), referenceFaces={4}, materializedFaces={5}, rejectedMaterialFaces={6}, aoFaces={7}, flatFaces={8}, blockLightRange={9}..{10}, skyLightRange={11}..{12}, snapshotFingerprint={13}, referenceFingerprint={14}, materialFingerprint={15}, lightingFingerprint={16}, drawableFingerprint={17}, materialCaptureNs={18}, lightingCaptureNs={19}, drawableBuildNs={20}, vertices={21}, indices={22}, vertexBytes={23}, indexBytes={24}, deterministicReferenceBuilds=2, deterministicMaterialCaptures=2, deterministicLightingCaptures=2, deterministicDrawableBuilds=2, worldReadsAfterLightingCapture=0, oneBlockHaloSufficient=true, blockVertexFormat=true, lightmapBound=true, comparisonColorScale=3/4, usefulSubmissions={25}, profilerOnlySubmissions=0, vanillaTerrainActive=true.",
                    frameSerial,
                    captured.sectionX(), captured.sectionY(), captured.sectionZ(),
                    firstReference.faceCount(), firstDrawable.faceCount(), firstDrawable.rejectedReferenceFaces(),
                    firstLighting.ambientOcclusionFaces(), firstLighting.flatFaces(),
                    firstLighting.minBlockLight(), firstLighting.maxBlockLight(),
                    firstLighting.minSkyLight(), firstLighting.maxSkyLight(),
                    Long.toUnsignedString(captured.fingerprint()),
                    Long.toUnsignedString(firstReference.fingerprint()),
                    Long.toUnsignedString(firstMaterials.fingerprint()),
                    Long.toUnsignedString(firstLighting.fingerprint()),
                    Long.toUnsignedString(firstDrawable.fingerprint()),
                    firstMaterials.captureTimeNs(), firstLighting.captureTimeNs(), firstDrawable.buildTimeNs(),
                    firstDrawable.vertexCount(), firstDrawable.indexCount(), firstDrawable.vertexBytes(),
                    firstDrawable.indexBytes(), usefulSubmissions);
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
                    "Phase 2 dev4 real-section lighting setup failed; Minecraft will continue for diagnosis.", e);
        }
    }

    private void ensurePipeline() {
        if (pipeline != null) {
            return;
        }
        RenderPipeline blockTemplate = RenderPipelines.SOLID_BLOCK;
        RenderPipeline depthTemplate = RenderPipelines.DEBUG_QUADS;
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation("obsidian_phase2_dev4_lit")
                .withVertexShader(blockTemplate.getVertexShader())
                .withFragmentShader(blockTemplate.getFragmentShader())
                .withCull(false)
                .withColorTargetState(blockTemplate.getColorTargetState())
                .withDepthStencilState(depthTemplate.getDepthStencilState())
                .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        for (BindGroupLayout layout : blockTemplate.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        pipeline = builder.build();
        CompiledRenderPipeline compiled = device.precompilePipeline(pipeline);
        pipelineValid = compiled.isValid();
        if (!pipelineValid) {
            throw new IllegalStateException("Phase 2 dev4 public BLOCK/lightmap comparison pipeline failed to compile");
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
                    "Phase 2 dev4 lit comparison window finished on frame {0}: comparisonDraws={1}, comparisonNs={2}, usefulSubmissions={3}, resourceEpochChecks={4}, profilerOnlySubmissions=0, indirectCalls={5}, trianglesSubmitted={6}; waiting nonblockingly for completion-gated reclamation.",
                    frameSerial, drawSubmissions, System.nanoTime() - comparisonStartNs,
                    usefulSubmissions, resourceEpochChecks, indirectCalls, triangles);
        }
    }

    private void encodeLiveDraw(CommandEncoder encoder, GameRenderer renderer) {
        if (drawableMesh == null || materialSnapshot == null || lightingSnapshot == null || indirectCommands == null) {
            throw new IllegalStateException("Phase 2 dev4 lit resources are incomplete");
        }
        long currentEpoch = SectionMaterialSnapshot.currentResourceEpoch();
        resourceEpochChecks++;
        if (currentEpoch != materialSnapshot.resourceEpoch() || currentEpoch != lightingSnapshot.resourceEpoch()) {
            throw new IllegalStateException("Minecraft model/atlas resource epoch changed during P2.4 comparison");
        }

        CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || camera.pos == null || camera.viewRotationMatrix == null) {
            throw new IllegalStateException("Minecraft world camera render state is unavailable");
        }
        double relativeX = drawableMesh.originX() - camera.pos.x;
        double relativeY = drawableMesh.originY() - camera.pos.y;
        double relativeZ = drawableMesh.originZ() - camera.pos.z;
        if (!Double.isFinite(relativeX) || !Double.isFinite(relativeY) || !Double.isFinite(relativeZ)) {
            throw new IllegalStateException("Non-finite Phase 2 dev4 camera-relative section origin");
        }

        Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix)
                .translate((float) relativeX, (float) relativeY, (float) relativeZ);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(modelView);
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        if (dynamicTransforms == null || projection == null || fog == null || RenderSystem.getGlobalSettingsUniform() == null) {
            throw new IllegalStateException("Minecraft world uniform buffers are unavailable for P2.4");
        }

        Minecraft minecraft = Minecraft.getInstance();
        AbstractTexture blocksAtlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (blocksAtlas == null || blocksAtlas.getTextureView() == null || blocksAtlas.getSampler() == null) {
            throw new IllegalStateException("Minecraft live blocks atlas texture/sampler is unavailable");
        }
        if (renderer.levelLightmap() == null) {
            throw new IllegalStateException("Minecraft live level lightmap view is unavailable");
        }

        RenderTarget target = renderer.mainRenderTarget();
        if (target == null || !target.useDepth || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
            throw new IllegalStateException("Minecraft main world color/depth target is unavailable");
        }
        GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
        GpuBufferSlice indexSlice = arena.slice(indexHandle);

        try (RenderPass pass = encoder.createRenderPass(
                PASS_LABEL, target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("Globals", RenderSystem.getGlobalSettingsUniform());
            pass.setUniform("Fog", fog);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setUniform("Projection", projection);
            pass.bindTexture("Sampler0", blocksAtlas.getTextureView(), blocksAtlas.getSampler());
            pass.bindTexture("Sampler2", renderer.levelLightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
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
        if (!tryRegisterRetirements(frameSerial)) return;
        if (staging.reclaimedBatches() < uploadBatchOrdinal) return;
        if (arena.isAllocated(vertexHandle) || arena.isAllocated(indexHandle)) return;
        if (resourceReleaseOrdinal > 0L && deferredReleases.releasedCount() < resourceReleaseOrdinal) return;

        if (validationFailure != null) {
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev4 resources reclaimed after validation failure; see earlier diagnostic.");
            return;
        }

        state = State.VERIFIED;
        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev4 lit real section verified on frame {0}: section=({1},{2},{3}), referenceFaces={4}, materializedFaces={5}, rejectedMaterialFaces={6}, aoFaces={7}, flatFaces={8}, blockLightRange={9}..{10}, skyLightRange={11}..{12}, snapshotFingerprint={13}, referenceFingerprint={14}, materialFingerprint={15}, lightingFingerprint={16}, drawableFingerprint={17}, faceCount={18}, vertexCount={19}, indexCount={20}, vertexBytes={21}, indexBytes={22}, deterministicReferenceBuilds=2, deterministicMaterialCaptures=2, deterministicLightingCaptures=2, deterministicDrawableBuilds=2, worldReadsAfterLightingCapture=0, oneBlockHaloSufficient=true, pipelineValid={23}, nativeGraphicsSeam=false, indexedIndirect=true, textured=true, blockVertexFormat=true, blocksAtlasBound=true, lightmapBound=true, comparisonColorScale=3/4, resourceEpochChecks={24}, usefulSubmissions={25}, profilerOnlySubmissions=0, comparisonDraws={26}, arenaRetired={27}, arenaReclaimed={28}, arenaUsedBytes={29}, arenaFreeSpans={30}, arenaFragmentationPermille={31}, stagingSubmittedBytes={32}, stagingReclaimedBytes={33}, pendingUploadBatches={34}, pendingArenaRetirementBatches={35}, pendingResourceRetirements={36}, vanillaTerrainActive=true.",
                frameSerial,
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(),
                referenceMesh.faceCount(), drawableMesh.faceCount(), drawableMesh.rejectedReferenceFaces(),
                lightingSnapshot.ambientOcclusionFaces(), lightingSnapshot.flatFaces(),
                lightingSnapshot.minBlockLight(), lightingSnapshot.maxBlockLight(),
                lightingSnapshot.minSkyLight(), lightingSnapshot.maxSkyLight(),
                Long.toUnsignedString(snapshot.fingerprint()), Long.toUnsignedString(referenceMesh.fingerprint()),
                Long.toUnsignedString(materialSnapshot.fingerprint()), Long.toUnsignedString(lightingSnapshot.fingerprint()),
                Long.toUnsignedString(drawableMesh.fingerprint()), drawableMesh.faceCount(), drawableMesh.vertexCount(),
                drawableMesh.indexCount(), drawableMesh.vertexBytes(), drawableMesh.indexBytes(), pipelineValid,
                resourceEpochChecks, usefulSubmissions, drawSubmissions, arena.retiredAllocations(),
                arena.reclaimedAllocations(), arena.usedBytes(), arena.freeSpanCount(), arena.fragmentationPermille(),
                staging.submittedBytes(), staging.reclaimedBytes(), staging.pendingBatches(),
                arena.pendingRetirementBatches(), deferredReleases.pendingCount());
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
                    "Phase 2 dev4 could not submit completion-gated failure cleanup; preserving resources for bounded shutdown/device teardown.",
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
                        "Phase 2 dev4 arena retirement registration failed; preserving completion handle for retry.", e);
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
                        "Phase 2 dev4 indirect-command retirement registration failed; preserving completion handle for retry.", e);
                return false;
            }
        }
        return pendingArenaFence == null && pendingResourceFence == null;
    }

    private int checkedFirstIndex(GpuBufferSlice indexSlice) {
        if ((indexSlice.offset() & (Integer.BYTES - 1L)) != 0L) {
            throw new IllegalStateException("Phase 2 dev4 index allocation is not 4-byte aligned");
        }
        long value = indexSlice.offset() / Integer.BYTES;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Phase 2 dev4 firstIndex exceeds public indirect command range");
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
        if (indirectCommands == null) return;
        try {
            indirectCommands.close();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to close unsubmitted dev4 indirect command buffer.", e);
        } finally {
            indirectCommands = null;
        }
    }

    private static void closeFence(GpuFence fence) {
        if (fence == null) return;
        try { fence.close(); } catch (RuntimeException ignored) { }
    }

    public State state() { return state; }
    public SectionSnapshot snapshot() { return snapshot; }
    public ReferenceFaceMesh referenceMesh() { return referenceMesh; }
    public SectionMaterialSnapshot materialSnapshot() { return materialSnapshot; }
    public SectionLightingSnapshot lightingSnapshot() { return lightingSnapshot; }
    public LitSectionMesh drawableMesh() { return drawableMesh; }
    public long usefulSubmissions() { return usefulSubmissions; }
    public long drawSubmissions() { return drawSubmissions; }
    public long resourceEpochChecks() { return resourceEpochChecks; }
    public long retirementBackpressureEvents() { return retirementBackpressureEvents; }
    public long retirementRegistrationFailures() { return retirementRegistrationFailures; }
    public boolean pipelineValid() { return pipelineValid; }
    public double firstCameraX() { return firstCameraX; }
    public double firstCameraY() { return firstCameraY; }
    public double firstCameraZ() { return firstCameraZ; }
    public double firstRelativeX() { return firstRelativeX; }
    public double firstRelativeY() { return firstRelativeY; }
    public double firstRelativeZ() { return firstRelativeZ; }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) return;
        if (state == State.COMPARING) beginCleanupRetirement(lastFrameSerial);
        if (state == State.RETIRING) tryRegisterRetirements(lastFrameSerial);
        if (!initialSubmissionCompleted) {
            cancelUnsubmittedAllocations();
            closeIndirectIfOwned();
        }
        state = State.CLOSED;
    }
}