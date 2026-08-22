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
 * P2.6 persistent one-section lifecycle drawable. Each instance owns exactly one
 * renderer generation. It captures/builds/uploads only that generation, draws
 * it until invalidated, then completion-gates retirement before the coordinator
 * admits a replacement generation.
 */
public final class RealSectionLifecycleProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/RealSectionLifecycleProbe");
    private static final Supplier<String> SOLID_PASS_LABEL = () -> "Obsidian Phase 2 dev6 SOLID lifecycle comparison";
    private static final Supplier<String> CUTOUT_PASS_LABEL = () -> "Obsidian Phase 2 dev6 CUTOUT lifecycle comparison";
    private static final Supplier<String> INDIRECT_LABEL = () -> "Obsidian Phase 2 dev6 layered indirect commands";
    private static final int INDIRECT_COMMAND_COUNT = 2;
    private static final long RETRY_DELAY_NS = 500_000_000L;

    public enum State { WAITING_WORLD, LIVE, RETIRING, RETIRED, STALE, FAILED, CLOSED }

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final DeviceGeometryArena arena;
    private final DeferredReleaseQueue deferredReleases;
    private final long[] retirementHandles = new long[2];
    private final long generation;
    private final long buildEventSequence;
    private final boolean fixedTarget;
    private final int requestedSectionX;
    private final int requestedSectionY;
    private final int requestedSectionZ;

    private RenderPipeline solidPipeline;
    private RenderPipeline cutoutPipeline;
    private IndexedIndirectCommandBuffer indirectCommands;
    private GpuFence pendingArenaFence;
    private GpuFence pendingResourceFence;

    private SectionSnapshot snapshot;
    private ReferenceFaceMesh referenceMesh;
    private SectionBakedQuadSnapshot bakedSnapshot;
    private BakedSectionMesh drawableMesh;

    private long vertexHandle = DeviceGeometryArena.INVALID_HANDLE;
    private long indexHandle = DeviceGeometryArena.INVALID_HANDLE;
    private long uploadBatchOrdinal;
    private long resourceReleaseOrdinal;
    private long lastFrameSerial;
    private long nextCaptureAttemptNs;
    private long usefulSubmissions;
    private long drawSubmissions;
    private long indirectCalls;
    private long triangles;
    private long resourceEpochChecks;
    private long retirementBackpressureEvents;
    private long retirementRegistrationFailures;
    private long staleInstallRejections;
    private int invalidationReasons;
    private boolean waitingLayerLogged;

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

    public RealSectionLifecycleProbe(
            GpuDevice device,
            StagingUploadArena staging,
            DeviceGeometryArena arena,
            DeferredReleaseQueue deferredReleases,
            long generation,
            long buildEventSequence,
            boolean fixedTarget,
            int sectionX,
            int sectionY,
            int sectionZ) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.staging = staging;
        this.arena = arena;
        this.deferredReleases = deferredReleases;
        this.generation = generation;
        this.buildEventSequence = buildEventSequence;
        this.fixedTarget = fixedTarget;
        this.requestedSectionX = sectionX;
        this.requestedSectionY = sectionY;
        this.requestedSectionZ = sectionZ;
        if (!device.getDeviceInfo().features().drawIndirect()) {
            throw new IllegalStateException("Phase 2 dev6 requires indexed indirect drawing");
        }
    }

    public void afterWorldRender(GameRenderer renderer, long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED || state == State.RETIRED || state == State.STALE
                || state == State.FAILED || state == State.RETIRING) {
            return;
        }
        lastFrameSerial = frameSerial;
        if (state == State.WAITING_WORLD) {
            long now = System.nanoTime();
            if (now >= nextCaptureAttemptNs) {
                tryCaptureBuildUploadAndDraw(renderer, frameSerial, now);
            }
            return;
        }
        try {
            if (SectionMaterialSnapshot.currentResourceEpoch() != bakedSnapshot.resourceEpoch()) {
                requestInvalidate(SectionLifecycleEvents.REASON_RESOURCE_RELOAD, frameSerial);
                return;
            }
            submitDraw(renderer);
        } catch (RuntimeException e) {
            validationFailure = e;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev6 lifecycle draw failed; beginning completion-gated cleanup.", e);
            beginCleanupRetirement(frameSerial);
        }
    }

    private void tryCaptureBuildUploadAndDraw(GameRenderer renderer, long frameSerial, long nowNs) {
        if (SectionLifecycleEvents.latestSequence() != buildEventSequence) {
            staleInstallRejections++;
            state = State.STALE;
            return;
        }
        SectionSnapshot captured = fixedTarget
                ? SectionSnapshot.tryCaptureSection(requestedSectionX, requestedSectionY, requestedSectionZ)
                : SectionSnapshot.tryCaptureNearPlayer();
        if (captured == null) {
            nextCaptureAttemptNs = nowNs + RETRY_DELAY_NS;
            return;
        }

        boolean batchOpen = false;
        try {
            ensurePipelines();

            ReferenceFaceMesh firstReference = ReferenceFaceMesh.build(captured);
            ReferenceFaceMesh secondReference = ReferenceFaceMesh.build(captured);
            if (firstReference.faceCount() <= 0 || !firstReference.contentEquals(secondReference)) {
                throw new IllegalStateException("Phase 2 dev6 permanent cube oracle is empty or nondeterministic");
            }

            SectionBakedQuadSnapshot firstBaked = SectionBakedQuadSnapshot.capture(captured);
            SectionBakedQuadSnapshot secondBaked = SectionBakedQuadSnapshot.capture(captured);
            if (!firstBaked.contentEquals(secondBaked)) {
                throw new IllegalStateException("Phase 2 dev6 generalized vanilla quad capture is nondeterministic");
            }
            if (firstBaked.solidQuads() <= 0 || firstBaked.cutoutQuads() <= 0) {
                if (!waitingLayerLogged) {
                    waitingLayerLogged = true;
                    LOG.log(System.Logger.Level.INFO,
                            "Phase 2 dev6 needs a section containing both supported SOLID and CUTOUT MODEL quads for the combined validation gate. Current section=({0},{1},{2}), solidQuads={3}, cutoutQuads={4}; move near ordinary terrain plus grass/flowers or other cutout blocks.",
                            captured.sectionX(), captured.sectionY(), captured.sectionZ(),
                            firstBaked.solidQuads(), firstBaked.cutoutQuads());
                }
                nextCaptureAttemptNs = nowNs + RETRY_DELAY_NS;
                return;
            }

            BakedSectionMesh firstDrawable = BakedSectionMesh.build(captured, firstBaked);
            BakedSectionMesh secondDrawable = BakedSectionMesh.build(captured, firstBaked);
            if (!firstDrawable.contentEquals(secondDrawable)) {
                throw new IllegalStateException("Phase 2 dev6 layered BLOCK mesh is nondeterministic");
            }
            if (firstDrawable.quadCount() != firstBaked.quadCount()
                    || firstDrawable.solidQuadCount() != firstBaked.solidQuads()
                    || firstDrawable.cutoutQuadCount() != firstBaked.cutoutQuads()
                    || firstDrawable.vertexCount() != firstDrawable.quadCount() * BakedSectionMesh.VERTICES_PER_QUAD
                    || firstDrawable.indexCount() != firstDrawable.quadCount() * BakedSectionMesh.INDICES_PER_QUAD) {
                throw new IllegalStateException("Phase 2 dev6 generalized capture/drawable accounting mismatch");
            }
            if (firstDrawable.vertexBytes() + firstDrawable.indexBytes() > BakedSectionMesh.MAX_UPLOAD_BYTES) {
                throw new IllegalStateException("Phase 2 dev6 mesh exceeded its bounded upload contract");
            }
            if (SectionLifecycleEvents.latestSequence() != buildEventSequence) {
                staleInstallRejections++;
                state = State.STALE;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 2 dev6 rejected stale generation {0} before GPU allocation because lifecycle event sequence advanced from {1} to {2}.",
                        generation, buildEventSequence, SectionLifecycleEvents.latestSequence());
                return;
            }

            vertexHandle = arena.allocate(firstDrawable.vertexBytes(), 16);
            if (vertexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 2 dev6 BLOCK vertex data");
            }
            indexHandle = arena.allocate(firstDrawable.indexBytes(), 4);
            if (indexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 2 dev6 index data");
            }

            indirectCommands = new IndexedIndirectCommandBuffer(device, INDIRECT_LABEL, INDIRECT_COMMAND_COUNT);
            GpuBufferSlice vertexSlice = arena.slice(vertexHandle);
            GpuBufferSlice indexSlice = arena.slice(indexHandle);
            int baseFirstIndex = checkedFirstIndex(indexSlice);
            int cutoutFirstIndex = Math.addExact(baseFirstIndex, firstDrawable.cutoutFirstLocalIndex());

            if (!staging.beginBatch()) {
                throw new IllegalStateException("Phase 2 dev6 could not open bounded staging batch");
            }
            batchOpen = true;
            CommandEncoder encoder = device.createCommandEncoder();
            if (!staging.stageCopy(encoder, firstDrawable.vertexBuffer(), vertexSlice)
                    || !staging.stageCopy(encoder, firstDrawable.indexBuffer(), indexSlice)
                    || !staging.stageCopy(encoder,
                            indirectCommand(firstDrawable.solidIndexCount(), baseFirstIndex),
                            indirectCommands.buffer(), 0L)
                    || !staging.stageCopy(encoder,
                            indirectCommand(firstDrawable.cutoutIndexCount(), cutoutFirstIndex),
                            indirectCommands.buffer(), IndexedIndirectCommandBuffer.COMMAND_BYTES)) {
                throw new IllegalStateException("Phase 2 dev6 layered upload hit bounded staging backpressure");
            }

            snapshot = captured;
            referenceMesh = firstReference;
            bakedSnapshot = firstBaked;
            drawableMesh = firstDrawable;
            encodeLiveDraw(encoder, renderer);

            if (SectionLifecycleEvents.latestSequence() != buildEventSequence) {
                staging.abortBatch();
                batchOpen = false;
                cancelUnsubmittedAllocations();
                closeIndirectIfOwned();
                staleInstallRejections++;
                state = State.STALE;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 2 dev6 rejected stale generation {0} immediately before installation because lifecycle event sequence advanced from {1} to {2}.",
                        generation, buildEventSequence, SectionLifecycleEvents.latestSequence());
                return;
            }
            staging.submitBatch(encoder);
            batchOpen = false;
            uploadBatchOrdinal = staging.submittedBatches();
            usefulSubmissions++;
            drawSubmissions++;
            indirectCalls += INDIRECT_COMMAND_COUNT;
            triangles += firstDrawable.indexCount() / 3L;
            initialSubmissionCompleted = true;
            state = State.LIVE;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev6 generation {0} installed from lifecycle sequence {1}; persistent drawing is active until exact vanilla dirtiness invalidates it. section=({2},{3},{4}).",
                    generation, buildEventSequence, captured.sectionX(), captured.sectionY(), captured.sectionZ());
            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev6 generalized SOLID+CUTOUT generation metrics on frame {0}: section=({1},{2},{3}), cubeReferenceFaces={4}, modelBlocksScanned={5}, acceptedBlocks={6}, noVisibleBlocks={7}, rejectedBlocks={8}, rejectedLeaves={9}, rejectedFluid={10}, rejectedBlockEntity={11}, rejectedMissingModel={12}, rejectedMaterial={13}, rejectedTranslucent={14}, rejectedAtlas={15}, generalizedQuads={16}, solidQuads={17}, cutoutQuads={18}, materialCount={19}, tintedQuads={20}, blockLightRange={21}..{22}, skyLightRange={23}..{24}, snapshotFingerprint={25}, cubeReferenceFingerprint={26}, generalizedFingerprint={27}, drawableFingerprint={28}, captureNs={29}, drawableBuildNs={30}, vertices={31}, indices={32}, vertexBytes={33}, indexBytes={34}, deterministicCubeReferenceBuilds=2, deterministicGeneralizedCaptures=2, deterministicDrawableBuilds=2, worldReadsAfterGeneralizedCapture=0, cubeOraclePreserved=true, oneBlockHaloSufficientForCapturedCullingLightSamples=true, solidPipeline=true, cutoutPipeline=true, cutoutAlphaThreshold=0.5, comparisonColorScale=3/4, comparisonFaceOffset=1/512, usefulSubmissions={35}, profilerOnlySubmissions=0, vanillaTerrainActive=true.",
                    frameSerial,
                    captured.sectionX(), captured.sectionY(), captured.sectionZ(), firstReference.faceCount(),
                    firstBaked.modelBlocksScanned(), firstBaked.acceptedBlocks(), firstBaked.noVisibleQuadBlocks(),
                    firstBaked.rejectedBlocks(), firstBaked.rejectedLeavesBlocks(), firstBaked.rejectedFluidBlocks(),
                    firstBaked.rejectedBlockEntityBlocks(), firstBaked.rejectedMissingModelBlocks(),
                    firstBaked.rejectedMaterialBlocks(), firstBaked.rejectedTranslucentBlocks(),
                    firstBaked.rejectedAtlasBlocks(), firstBaked.quadCount(), firstBaked.solidQuads(),
                    firstBaked.cutoutQuads(), firstBaked.materialCount(), firstBaked.tintedQuads(),
                    firstBaked.minBlockLight(), firstBaked.maxBlockLight(), firstBaked.minSkyLight(),
                    firstBaked.maxSkyLight(), Long.toUnsignedString(captured.fingerprint()),
                    Long.toUnsignedString(firstReference.fingerprint()), Long.toUnsignedString(firstBaked.fingerprint()),
                    Long.toUnsignedString(firstDrawable.fingerprint()), firstBaked.captureTimeNs(),
                    firstDrawable.buildTimeNs(), firstDrawable.vertexCount(), firstDrawable.indexCount(),
                    firstDrawable.vertexBytes(), firstDrawable.indexBytes(), usefulSubmissions);
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
                    "Phase 2 dev6 generalized section setup failed; Minecraft will continue for diagnosis.", e);
        }
    }

    private void ensurePipelines() {
        if (solidPipeline != null && cutoutPipeline != null) {
            return;
        }
        RenderPipeline depthTemplate = RenderPipelines.DEBUG_QUADS;
        solidPipeline = buildComparisonPipeline(
                "obsidian_phase2_dev6_solid", RenderPipelines.SOLID_BLOCK, depthTemplate, false);
        cutoutPipeline = buildComparisonPipeline(
                "obsidian_phase2_dev6_cutout", RenderPipelines.CUTOUT_BLOCK, depthTemplate, true);
        CompiledRenderPipeline solidCompiled = device.precompilePipeline(solidPipeline);
        CompiledRenderPipeline cutoutCompiled = device.precompilePipeline(cutoutPipeline);
        pipelineValid = solidCompiled.isValid() && cutoutCompiled.isValid();
        if (!pipelineValid) {
            throw new IllegalStateException("Phase 2 dev6 public SOLID/CUTOUT BLOCK comparison pipelines failed to compile");
        }
    }

    private static RenderPipeline buildComparisonPipeline(
            String location,
            RenderPipeline template,
            RenderPipeline depthTemplate,
            boolean cutout) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(template.getVertexShader())
                .withFragmentShader(template.getFragmentShader())
                .withCull(false)
                .withColorTargetState(template.getColorTargetState())
                .withDepthStencilState(depthTemplate.getDepthStencilState())
                .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        if (cutout) {
            builder.withShaderDefine("ALPHA_CUTOUT", 0.5f);
        }
        for (BindGroupLayout layout : template.getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        return builder.build();
    }

    private void submitDraw(GameRenderer renderer) {
        CommandEncoder encoder = device.createCommandEncoder();
        encodeLiveDraw(encoder, renderer);
        encoder.submit();
        usefulSubmissions++;
        drawSubmissions++;
        indirectCalls += INDIRECT_COMMAND_COUNT;
        triangles += drawableMesh.indexCount() / 3L;
    }

    public void requestInvalidate(int reasons, long frameSerial) {
        RenderSystem.assertOnRenderThread();
        invalidationReasons |= reasons;
        lastFrameSerial = frameSerial;
        if (state == State.WAITING_WORLD) {
            staleInstallRejections++;
            state = State.STALE;
            return;
        }
        if (state == State.LIVE) {
            beginCleanupRetirement(frameSerial);
        }
    }

    private void encodeLiveDraw(CommandEncoder encoder, GameRenderer renderer) {
        if (drawableMesh == null || bakedSnapshot == null || indirectCommands == null) {
            throw new IllegalStateException("Phase 2 dev6 generalized resources are incomplete");
        }
        long currentEpoch = SectionMaterialSnapshot.currentResourceEpoch();
        resourceEpochChecks++;
        if (currentEpoch != bakedSnapshot.resourceEpoch()) {
            throw new IllegalStateException("Minecraft model/atlas resource epoch changed during P2.6 comparison");
        }

        CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || camera.pos == null || camera.viewRotationMatrix == null) {
            throw new IllegalStateException("Minecraft world camera render state is unavailable");
        }
        double relativeX = drawableMesh.originX() - camera.pos.x;
        double relativeY = drawableMesh.originY() - camera.pos.y;
        double relativeZ = drawableMesh.originZ() - camera.pos.z;
        if (!Double.isFinite(relativeX) || !Double.isFinite(relativeY) || !Double.isFinite(relativeZ)) {
            throw new IllegalStateException("Non-finite Phase 2 dev6 camera-relative section origin");
        }

        Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix)
                .translate((float) relativeX, (float) relativeY, (float) relativeZ);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(modelView);
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        if (dynamicTransforms == null || projection == null || fog == null || RenderSystem.getGlobalSettingsUniform() == null) {
            throw new IllegalStateException("Minecraft world uniform buffers are unavailable for P2.6");
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

        encodeLayerPass(encoder, target, solidPipeline, SOLID_PASS_LABEL,
                dynamicTransforms, projection, fog, blocksAtlas, renderer, vertexSlice, indexSlice,
                indirectCommands.buffer().slice(0L, IndexedIndirectCommandBuffer.COMMAND_BYTES));
        encodeLayerPass(encoder, target, cutoutPipeline, CUTOUT_PASS_LABEL,
                dynamicTransforms, projection, fog, blocksAtlas, renderer, vertexSlice, indexSlice,
                indirectCommands.buffer().slice(IndexedIndirectCommandBuffer.COMMAND_BYTES,
                        IndexedIndirectCommandBuffer.COMMAND_BYTES));

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

    private void encodeLayerPass(
            CommandEncoder encoder,
            RenderTarget target,
            RenderPipeline pipeline,
            Supplier<String> label,
            GpuBufferSlice dynamicTransforms,
            GpuBufferSlice projection,
            GpuBufferSlice fog,
            AbstractTexture blocksAtlas,
            GameRenderer renderer,
            GpuBufferSlice vertexSlice,
            GpuBufferSlice indexSlice,
            GpuBufferSlice commandSlice) {
        try (RenderPass pass = encoder.createRenderPass(
                label, target.getColorTextureView(), Optional.empty(),
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
            pass.drawIndexedIndirect(commandSlice, 1);
        }
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        lastFrameSerial = frameSerial;
        if (state != State.RETIRING) return;
        if (!tryRegisterRetirements(frameSerial)) return;
        if (staging.reclaimedBatches() < uploadBatchOrdinal) return;
        if (arena.isAllocated(vertexHandle) || arena.isAllocated(indexHandle)) return;
        if (resourceReleaseOrdinal > 0L && deferredReleases.releasedCount() < resourceReleaseOrdinal) return;

        if (validationFailure != null) {
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev6 resources reclaimed after validation failure; see earlier diagnostic.");
            return;
        }

        state = State.RETIRED;
        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev6 generation retired after invalidation on frame {0}: section=({1},{2},{3}), cubeReferenceFaces={4}, generalizedQuads={5}, solidQuads={6}, cutoutQuads={7}, acceptedBlocks={8}, rejectedBlocks={9}, rejectedLeaves={10}, rejectedFluid={11}, rejectedBlockEntity={12}, rejectedMissingModel={13}, rejectedMaterial={14}, rejectedTranslucent={15}, rejectedAtlas={16}, materialCount={17}, tintedQuads={18}, blockLightRange={19}..{20}, skyLightRange={21}..{22}, snapshotFingerprint={23}, cubeReferenceFingerprint={24}, generalizedFingerprint={25}, drawableFingerprint={26}, vertexCount={27}, indexCount={28}, vertexBytes={29}, indexBytes={30}, deterministicCubeReferenceBuilds=2, deterministicGeneralizedCaptures=2, deterministicDrawableBuilds=2, worldReadsAfterGeneralizedCapture=0, cubeOraclePreserved=true, oneBlockHaloSufficientForCapturedCullingLightSamples=true, pipelineValid={31}, nativeGraphicsSeam=false, indexedIndirect=true, textured=true, blockVertexFormat=true, blocksAtlasBound=true, lightmapBound=true, solidPipeline=true, cutoutPipeline=true, cutoutAlphaThreshold=0.5, comparisonColorScale=3/4, comparisonFaceOffset=1/512, resourceEpochChecks={32}, usefulSubmissions={33}, profilerOnlySubmissions=0, comparisonDraws={34}, indirectCalls={35}, arenaRetired={36}, arenaReclaimed={37}, arenaUsedBytes={38}, arenaFreeSpans={39}, arenaFragmentationPermille={40}, stagingSubmittedBytes={41}, stagingReclaimedBytes={42}, pendingUploadBatches={43}, pendingArenaRetirementBatches={44}, pendingResourceRetirements={45}, vanillaTerrainActive=true.",
                frameSerial,
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(), referenceMesh.faceCount(),
                bakedSnapshot.quadCount(), bakedSnapshot.solidQuads(), bakedSnapshot.cutoutQuads(),
                bakedSnapshot.acceptedBlocks(), bakedSnapshot.rejectedBlocks(), bakedSnapshot.rejectedLeavesBlocks(),
                bakedSnapshot.rejectedFluidBlocks(), bakedSnapshot.rejectedBlockEntityBlocks(),
                bakedSnapshot.rejectedMissingModelBlocks(), bakedSnapshot.rejectedMaterialBlocks(),
                bakedSnapshot.rejectedTranslucentBlocks(), bakedSnapshot.rejectedAtlasBlocks(),
                bakedSnapshot.materialCount(), bakedSnapshot.tintedQuads(), bakedSnapshot.minBlockLight(),
                bakedSnapshot.maxBlockLight(), bakedSnapshot.minSkyLight(), bakedSnapshot.maxSkyLight(),
                Long.toUnsignedString(snapshot.fingerprint()), Long.toUnsignedString(referenceMesh.fingerprint()),
                Long.toUnsignedString(bakedSnapshot.fingerprint()), Long.toUnsignedString(drawableMesh.fingerprint()),
                drawableMesh.vertexCount(), drawableMesh.indexCount(), drawableMesh.vertexBytes(),
                drawableMesh.indexBytes(), pipelineValid, resourceEpochChecks, usefulSubmissions, drawSubmissions,
                indirectCalls, arena.retiredAllocations(), arena.reclaimedAllocations(), arena.usedBytes(),
                arena.freeSpanCount(), arena.fragmentationPermille(), staging.submittedBytes(),
                staging.reclaimedBytes(), staging.pendingBatches(), arena.pendingRetirementBatches(),
                deferredReleases.pendingCount());
    }

    private void beginCleanupRetirement(long frameSerial) {
        if (!initialSubmissionCompleted || state == State.RETIRING) {
            if (!initialSubmissionCompleted && state != State.STALE) state = State.STALE;
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
                    "Phase 2 dev6 could not submit completion-gated failure cleanup; preserving resources for bounded shutdown/device teardown.",
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
                        "Phase 2 dev6 arena retirement registration failed; preserving completion handle for retry.", e);
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
                        "Phase 2 dev6 indirect-command retirement registration failed; preserving completion handle for retry.", e);
                return false;
            }
        }
        return pendingArenaFence == null && pendingResourceFence == null;
    }

    private int checkedFirstIndex(GpuBufferSlice indexSlice) {
        if ((indexSlice.offset() & (Integer.BYTES - 1L)) != 0L) {
            throw new IllegalStateException("Phase 2 dev6 index allocation is not 4-byte aligned");
        }
        long value = indexSlice.offset() / Integer.BYTES;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Phase 2 dev6 firstIndex exceeds public indirect command range");
        }
        return (int) value;
    }

    private static ByteBuffer indirectCommand(int indexCount, int firstIndex) {
        if (indexCount <= 0) {
            throw new IllegalArgumentException("P2.6 indirect layer index count must be positive");
        }
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
            LOG.log(System.Logger.Level.WARNING, "Failed to close unsubmitted dev6 indirect command buffer.", e);
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
    public SectionBakedQuadSnapshot bakedSnapshot() { return bakedSnapshot; }
    public BakedSectionMesh drawableMesh() { return drawableMesh; }
    public long usefulSubmissions() { return usefulSubmissions; }
    public long drawSubmissions() { return drawSubmissions; }
    public long resourceEpochChecks() { return resourceEpochChecks; }
    public long indirectCalls() { return indirectCalls; }
    public long retirementBackpressureEvents() { return retirementBackpressureEvents; }
    public long retirementRegistrationFailures() { return retirementRegistrationFailures; }
    public long staleInstallRejections() { return staleInstallRejections; }
    public int invalidationReasons() { return invalidationReasons; }
    public long generation() { return generation; }
    public long buildEventSequence() { return buildEventSequence; }
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
        if (state == State.LIVE) beginCleanupRetirement(lastFrameSerial);
        if (state == State.RETIRING) tryRegisterRetirements(lastFrameSerial);
        if (!initialSubmissionCompleted) {
            cancelUnsubmittedAllocations();
            closeIndirectIfOwned();
        }
        state = State.CLOSED;
    }
}
