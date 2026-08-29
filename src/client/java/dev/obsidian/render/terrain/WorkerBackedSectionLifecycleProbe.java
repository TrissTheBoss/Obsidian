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
import dev.obsidian.render.mesh.SectionMeshWorkerPool;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Phase 3 worker-backed section record.
 *
 * <p>All live Minecraft capture remains on the render thread. Dedicated workers
 * receive only immutable section/generalized snapshots and publish both the
 * exact {@link BakedSectionMesh} oracle and the dev11 validated
 * {@link RepeatAwareGreedyMesh}. Accepted worker output returns to the render
 * thread before any GPU allocation, upload, draw encoding, installation or
 * completion-gated retirement.</p>
 */
public final class WorkerBackedSectionLifecycleProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/WorkerBackedSectionLifecycleProbe");
    private static final Supplier<String> PASSTHROUGH_SOLID_PASS_LABEL = () -> "Obsidian Phase 3 dev11 passthrough SOLID";
    private static final Supplier<String> PASSTHROUGH_CUTOUT_PASS_LABEL = () -> "Obsidian Phase 3 dev11 passthrough CUTOUT";
    private static final Supplier<String> MERGED_SOLID_PASS_LABEL = () -> "Obsidian Phase 3 dev11 merged SOLID";
    private static final Supplier<String> MERGED_CUTOUT_PASS_LABEL = () -> "Obsidian Phase 3 dev11 merged CUTOUT";
    private static final Supplier<String> INDIRECT_LABEL = () -> "Obsidian Phase 3 dev11 four-class indirect commands";
    private static final int INDIRECT_COMMAND_COUNT = 4;
    private static final long RETRY_DELAY_NS = 500_000_000L;
    private static final Identifier REPEAT_AWARE_SHADER =
            Identifier.fromNamespaceAndPath("obsidian", "core/repeat_aware_block");

    public enum State {
        WAITING_WORLD,
        WAITING_MESH,
        READY_TO_INSTALL,
        LIVE,
        RETIRING,
        RETIRED,
        STALE,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final DeviceGeometryArena arena;
    private final DeferredReleaseQueue deferredReleases;
    private final SectionMeshWorkerPool workers;
    private final int workerPriority;
    private final long[] retirementHandles = new long[3];
    private final long generation;
    private final long buildEventSequence;
    private final int requestedSectionX;
    private final int requestedSectionY;
    private final int requestedSectionZ;

    private RenderPipeline passthroughSolidPipeline;
    private RenderPipeline passthroughCutoutPipeline;
    private RenderPipeline mergedSolidPipeline;
    private RenderPipeline mergedCutoutPipeline;
    private IndexedIndirectCommandBuffer indirectCommands;
    private GpuFence pendingArenaFence;
    private GpuFence pendingResourceFence;
    private SectionMeshWorkerPool.Ticket workerTicket;

    private SectionSnapshot snapshot;
    private ReferenceFaceMesh referenceMesh;
    private SectionBakedQuadSnapshot bakedSnapshot;
    private BakedSectionMesh oracleMesh;
    private RepeatAwareGreedyMesh drawableMesh;
    private TJunctionTopologyProof tJunctionTopologyProof;

    private long passthroughVertexHandle = DeviceGeometryArena.INVALID_HANDLE;
    private long mergedVertexHandle = DeviceGeometryArena.INVALID_HANDLE;
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
    private long workerJobsSubmitted;
    private long workerJobsCompleted;
    private long workerJobsCancelled;
    private long workerCancellationRequests;
    private long staleWorkerResultDiscards;
    private long workerResultInstalls;
    private long workerQueueRejections;
    private long installAdmissionDeferrals;
    private long emittedMergedQuads;
    private long emittedSuppressedSourceQuads;
    private long emittedFacesSaved;
    private long emittedPassthroughQuads;
    private long emittedHybridQuads;
    private long emittedHybridUploadBytes;
    private long emittedSourceUploadBytes;
    private int invalidationReasons;
    private boolean waitingLayerLogged;
    private boolean preinstallInvalidated;

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

    public WorkerBackedSectionLifecycleProbe(
            GpuDevice device,
            StagingUploadArena staging,
            DeviceGeometryArena arena,
            DeferredReleaseQueue deferredReleases,
            SectionMeshWorkerPool workers,
            int workerPriority,
            long generation,
            long buildEventSequence,
            int sectionX,
            int sectionY,
            int sectionZ) {
        RenderSystem.assertOnRenderThread();
        if (workers == null) throw new NullPointerException("workers");
        if (workerPriority < SectionMeshWorkerPool.PRIORITY_HIGH
                || workerPriority > SectionMeshWorkerPool.PRIORITY_LOW) {
            throw new IllegalArgumentException("Invalid production scene worker priority");
        }
        this.device = device;
        this.staging = staging;
        this.arena = arena;
        this.deferredReleases = deferredReleases;
        this.workers = workers;
        this.workerPriority = workerPriority;
        this.generation = generation;
        this.buildEventSequence = buildEventSequence;
        this.requestedSectionX = sectionX;
        this.requestedSectionY = sectionY;
        this.requestedSectionZ = sectionZ;
        if (!device.getDeviceInfo().features().drawIndirect()) {
            throw new IllegalStateException("Phase 3 dev11 requires indexed indirect drawing");
        }
    }

    public void afterWorldRender(GameRenderer renderer, long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED || state == State.RETIRED || state == State.STALE
                || state == State.FAILED || state == State.RETIRING) {
            return;
        }
        lastFrameSerial = frameSerial;

        try {
            if (state == State.WAITING_WORLD) {
                long now = System.nanoTime();
                if (now >= nextCaptureAttemptNs) captureAndSubmit(now);
                return;
            }
            if (state == State.WAITING_MESH) {
                pollWorkerResult();
                if (state != State.READY_TO_INSTALL) return;
            }
            if (state == State.READY_TO_INSTALL) {
                tryInstallWorkerResult(renderer, frameSerial);
                return;
            }
            if (state == State.LIVE) {
                if (SectionMaterialSnapshot.currentResourceEpoch() != bakedSnapshot.resourceEpoch()) {
                    requestInvalidate(SectionLifecycleEvents.REASON_RESOURCE_RELOAD, frameSerial);
                    return;
                }
                submitDraw(renderer);
            }
        } catch (RuntimeException e) {
            validationFailure = e;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 3 dev11 worker-backed section record failed; beginning safe cleanup.", e);
            if (initialSubmissionCompleted) beginCleanupRetirement(frameSerial);
            else failBeforeGpuInstall();
        }
    }

    private void captureAndSubmit(long nowNs) {
        long sequenceBefore = SectionLifecycleEvents.latestSequence();
        if (sequenceBefore != buildEventSequence) {
            state = State.STALE;
            return;
        }

        SectionSnapshot captured = SectionSnapshot.tryCaptureSection(
                requestedSectionX, requestedSectionY, requestedSectionZ);
        if (captured == null) {
            nextCaptureAttemptNs = nowNs + RETRY_DELAY_NS;
            return;
        }

        ReferenceFaceMesh firstReference = ReferenceFaceMesh.build(captured);
        ReferenceFaceMesh secondReference = ReferenceFaceMesh.build(captured);
        if (firstReference.faceCount() <= 0 || !firstReference.contentEquals(secondReference)) {
            throw new IllegalStateException("Phase 3 dev11 permanent cube oracle is empty or nondeterministic");
        }

        SectionBakedQuadSnapshot firstBaked = SectionBakedQuadSnapshot.capture(captured);
        SectionBakedQuadSnapshot secondBaked = SectionBakedQuadSnapshot.capture(captured);
        if (!firstBaked.contentEquals(secondBaked)) {
            throw new IllegalStateException("Phase 3 dev11 generalized vanilla quad capture is nondeterministic");
        }
        if (firstBaked.solidQuads() <= 0 || firstBaked.cutoutQuads() <= 0) {
            if (!waitingLayerLogged) {
                waitingLayerLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 3 dev11 scene record needs both supported SOLID and CUTOUT quads. section=({0},{1},{2}), solidQuads={3}, cutoutQuads={4}.",
                        captured.sectionX(), captured.sectionY(), captured.sectionZ(),
                        firstBaked.solidQuads(), firstBaked.cutoutQuads());
            }
            nextCaptureAttemptNs = nowNs + RETRY_DELAY_NS;
            return;
        }
        if (SectionLifecycleEvents.latestSequence() != sequenceBefore) {
            state = State.STALE;
            return;
        }

        SectionMeshWorkerPool.Ticket ticket = workers.submit(
                generation, buildEventSequence, workerPriority, captured, firstBaked);
        if (ticket == null) {
            workerQueueRejections++;
            nextCaptureAttemptNs = nowNs + RETRY_DELAY_NS;
            return;
        }

        snapshot = captured;
        referenceMesh = firstReference;
        bakedSnapshot = firstBaked;
        workerTicket = ticket;
        workerJobsSubmitted++;
        state = State.WAITING_MESH;

        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev11 submitted production scene mesh job {0}: generation={1}, eventSequence={2}, priority={3}, section=({4},{5},{6}), generalizedQuads={7}, worldReadsAfterGeneralizedCapture=0.",
                ticket.id(), generation, buildEventSequence, workerPriority,
                captured.sectionX(), captured.sectionY(), captured.sectionZ(), firstBaked.quadCount());
    }

    private void pollWorkerResult() {
        SectionMeshWorkerPool.Ticket ticket = workerTicket;
        if (ticket == null || !ticket.terminal()) return;

        if (ticket.state() == SectionMeshWorkerPool.TicketState.FAILED) {
            Throwable cause = ticket.failure();
            throw cause instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("Phase 3 dev11 worker job failed", cause);
        }
        if (ticket.state() == SectionMeshWorkerPool.TicketState.CANCELLED) {
            workerJobsCancelled++;
            workerTicket = null;
            state = State.STALE;
            return;
        }
        if (ticket.state() != SectionMeshWorkerPool.TicketState.COMPLETED) return;

        workerJobsCompleted++;
        BakedSectionMesh exact = ticket.mesh();
        RepeatAwareTransportProof transport = ticket.repeatAwareTransport();
        TJunctionTopologyProof tJunctionProof = ticket.tJunctionTopologyProof();
        RenderMergeCandidates mergeCandidates = ticket.mergeCandidates();
        if (exact == null || transport == null || tJunctionProof == null || mergeCandidates == null) {
            throw new IllegalStateException("Completed Phase 3 dev13 worker ticket published incomplete proof/mesh output");
        }
        if (tJunctionProof.sourceCandidateFingerprint() != mergeCandidates.fingerprint()
                || tJunctionProof.sourceTransportFingerprint() != transport.fingerprint()
                || tJunctionProof.emittedCandidates() != transport.recordCount()) {
            throw new IllegalStateException("Phase 3 dev13 T-junction proof lost exact emitted-candidate identity");
        }
        RepeatAwareGreedyMesh hybrid = transport.greedyMesh();
        if (ticket.generation() != generation
                || ticket.eventSequence() != buildEventSequence
                || SectionLifecycleEvents.latestSequence() != buildEventSequence
                || SectionMaterialSnapshot.currentResourceEpoch() != bakedSnapshot.resourceEpoch()) {
            staleWorkerResultDiscards++;
            workerTicket = null;
            state = State.STALE;
            return;
        }

        exact.validateAgainst(snapshot, bakedSnapshot);
        if (exact.quadCount() != bakedSnapshot.quadCount()
                || exact.solidQuadCount() != bakedSnapshot.solidQuads()
                || exact.cutoutQuadCount() != bakedSnapshot.cutoutQuads()
                || exact.vertexCount() != exact.quadCount() * BakedSectionMesh.VERTICES_PER_QUAD
                || exact.indexCount() != exact.quadCount() * BakedSectionMesh.INDICES_PER_QUAD) {
            throw new IllegalStateException("Phase 3 dev11 exact worker mesh accounting mismatch");
        }
        if (exact.vertexBytes() + exact.indexBytes() > BakedSectionMesh.MAX_UPLOAD_BYTES) {
            throw new IllegalStateException("Phase 3 dev11 exact worker mesh exceeded bounded upload contract");
        }
        if (hybrid.sourceBakedFingerprint() != bakedSnapshot.fingerprint()
                || hybrid.sourceTransportFingerprint() != transport.fingerprint()
                || hybrid.sourceQuadCount() != exact.quadCount()
                || hybrid.hybridQuadCount() != exact.quadCount() - transport.facesSaved()
                || hybrid.mergedQuadCount() != transport.recordCount()
                || hybrid.suppressedSourceQuads() != transport.coveredFaces()
                || hybrid.facesSaved() != transport.facesSaved()
                || hybrid.totalUploadBytes() > hybrid.sourceUploadBytes()
                || hybrid.sourceUploadBytes() != exact.vertexBytes() + exact.indexBytes()) {
            throw new IllegalStateException("Phase 3 dev11 hybrid worker mesh accounting/source identity mismatch");
        }

        oracleMesh = exact;
        drawableMesh = hybrid;
        tJunctionTopologyProof = tJunctionProof;
        workerTicket = null;
        state = State.READY_TO_INSTALL;
    }

    private void tryInstallWorkerResult(GameRenderer renderer, long frameSerial) {
        if (drawableMesh == null || oracleMesh == null || snapshot == null || bakedSnapshot == null || referenceMesh == null) {
            throw new IllegalStateException("Phase 3 dev11 install-ready record is incomplete");
        }
        if (SectionLifecycleEvents.latestSequence() != buildEventSequence
                || SectionMaterialSnapshot.currentResourceEpoch() != bakedSnapshot.resourceEpoch()) {
            staleWorkerResultDiscards++;
            state = State.STALE;
            return;
        }
        if (staging.pendingBatches() != 0) {
            installAdmissionDeferrals++;
            return;
        }

        boolean batchOpen = false;
        try {
            ensurePipelines();
            passthroughVertexHandle = allocateOrPlaceholder(drawableMesh.passthroughVertexBytes(), 16);
            mergedVertexHandle = allocateOrPlaceholder(drawableMesh.mergedVertexBytes(), 16);
            indexHandle = arena.allocate(drawableMesh.indexBytes(), 4);
            if (indexHandle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device arena could not fit Phase 3 dev11 hybrid index data");
            }

            indirectCommands = new IndexedIndirectCommandBuffer(device, INDIRECT_LABEL, INDIRECT_COMMAND_COUNT);
            GpuBufferSlice passthroughVertexSlice = arena.slice(passthroughVertexHandle);
            GpuBufferSlice mergedVertexSlice = arena.slice(mergedVertexHandle);
            GpuBufferSlice indexSlice = arena.slice(indexHandle);
            int baseFirstIndex = checkedFirstIndex(indexSlice);

            if (!staging.beginBatch()) {
                throw new IllegalStateException("Phase 3 dev11 could not open bounded staging batch");
            }
            batchOpen = true;
            CommandEncoder encoder = device.createCommandEncoder();
            if (!stageIfNonEmpty(encoder, drawableMesh.passthroughVertexBuffer(), passthroughVertexSlice)
                    || !stageIfNonEmpty(encoder, drawableMesh.mergedVertexBuffer(), mergedVertexSlice)
                    || !staging.stageCopy(encoder, drawableMesh.indexBuffer(), indexSlice)
                    || !stageIndirectCommands(encoder, baseFirstIndex)) {
                throw new IllegalStateException("Phase 3 dev11 hybrid upload hit bounded staging backpressure");
            }

            encodeLiveDraw(encoder, renderer);

            if (SectionLifecycleEvents.latestSequence() != buildEventSequence
                    || SectionMaterialSnapshot.currentResourceEpoch() != bakedSnapshot.resourceEpoch()) {
                staging.abortBatch();
                batchOpen = false;
                cancelUnsubmittedAllocations();
                closeIndirectIfOwned();
                staleWorkerResultDiscards++;
                state = State.STALE;
                return;
            }

            staging.submitBatch(encoder);
            batchOpen = false;
            uploadBatchOrdinal = staging.submittedBatches();
            usefulSubmissions++;
            drawSubmissions++;
            indirectCalls += INDIRECT_COMMAND_COUNT;
            triangles += drawableMesh.indexBytes() / Integer.BYTES / 3L;
            emittedMergedQuads += drawableMesh.mergedQuadCount();
            emittedSuppressedSourceQuads += drawableMesh.suppressedSourceQuads();
            emittedFacesSaved += drawableMesh.facesSaved();
            emittedPassthroughQuads += drawableMesh.passthroughQuadCount();
            emittedHybridQuads += drawableMesh.hybridQuadCount();
            emittedHybridUploadBytes += drawableMesh.totalUploadBytes();
            emittedSourceUploadBytes += drawableMesh.sourceUploadBytes();
            initialSubmissionCompleted = true;
            workerResultInstalls++;
            state = State.LIVE;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 3 dev11 hybrid worker result installed on frame {0}: generation={1}, eventSequence={2}, priority={3}, section=({4},{5},{6}), oracleFingerprint={7}, hybridFingerprint={8}, sourceQuads={9}, suppressedSourceQuads={10}, passthroughQuads={11}, mergedQuads={12}, hybridQuads={13}, facesSaved={14}, sourceUploadBytes={15}, hybridUploadBytes={16}, synchronousSceneMeshBuilds=0, repeatAwareGreedyGpuEmission=true, renderThreadGpuOwnershipPreserved=true.",
                    frameSerial, generation, buildEventSequence, workerPriority,
                    snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(),
                    Long.toUnsignedString(oracleMesh.fingerprint()), Long.toUnsignedString(drawableMesh.fingerprint()),
                    drawableMesh.sourceQuadCount(), drawableMesh.suppressedSourceQuads(),
                    drawableMesh.passthroughQuadCount(), drawableMesh.mergedQuadCount(),
                    drawableMesh.hybridQuadCount(), drawableMesh.facesSaved(), drawableMesh.sourceUploadBytes(),
                    drawableMesh.totalUploadBytes());
        } catch (RuntimeException e) {
            if (batchOpen) staging.abortBatch();
            throw e;
        }
    }

    private long allocateOrPlaceholder(int bytes, int alignment) {
        int allocationBytes = Math.max(bytes, alignment);
        long handle = arena.allocate(allocationBytes, alignment);
        if (handle == DeviceGeometryArena.INVALID_HANDLE) {
            throw new IllegalStateException("Device arena could not fit Phase 3 dev11 hybrid vertex data");
        }
        return handle;
    }

    private boolean stageIfNonEmpty(CommandEncoder encoder, ByteBuffer data, GpuBufferSlice target) {
        return !data.hasRemaining() || staging.stageCopy(encoder, data, target);
    }

    private boolean stageIndirectCommands(CommandEncoder encoder, int baseFirstIndex) {
        int[] counts = {
                drawableMesh.passthroughSolidIndexCount(),
                drawableMesh.passthroughCutoutIndexCount(),
                drawableMesh.mergedSolidIndexCount(),
                drawableMesh.mergedCutoutIndexCount()
        };
        int[] first = {
                drawableMesh.passthroughSolidFirstLocalIndex(),
                drawableMesh.passthroughCutoutFirstLocalIndex(),
                drawableMesh.mergedSolidFirstLocalIndex(),
                drawableMesh.mergedCutoutFirstLocalIndex()
        };
        for (int command = 0; command < INDIRECT_COMMAND_COUNT; command++) {
            int firstIndex = Math.addExact(baseFirstIndex, first[command]);
            if (!staging.stageCopy(encoder, indirectCommand(counts[command], firstIndex),
                    indirectCommands.buffer(), (long) command * IndexedIndirectCommandBuffer.COMMAND_BYTES)) {
                return false;
            }
        }
        return true;
    }

    private void ensurePipelines() {
        if (passthroughSolidPipeline != null && passthroughCutoutPipeline != null
                && mergedSolidPipeline != null && mergedCutoutPipeline != null) return;
        RenderPipeline depthTemplate = RenderPipelines.DEBUG_QUADS;
        passthroughSolidPipeline = buildPassthroughPipeline(
                "obsidian_phase3_dev11_passthrough_solid", RenderPipelines.SOLID_BLOCK, depthTemplate, false);
        passthroughCutoutPipeline = buildPassthroughPipeline(
                "obsidian_phase3_dev11_passthrough_cutout", RenderPipelines.CUTOUT_BLOCK, depthTemplate, true);
        mergedSolidPipeline = buildMergedPipeline(
                "obsidian_phase3_dev11_merged_solid", RenderPipelines.SOLID_BLOCK, depthTemplate, false);
        mergedCutoutPipeline = buildMergedPipeline(
                "obsidian_phase3_dev11_merged_cutout", RenderPipelines.CUTOUT_BLOCK, depthTemplate, true);
        CompiledRenderPipeline passthroughSolidCompiled = device.precompilePipeline(passthroughSolidPipeline);
        CompiledRenderPipeline passthroughCutoutCompiled = device.precompilePipeline(passthroughCutoutPipeline);
        CompiledRenderPipeline mergedSolidCompiled = device.precompilePipeline(mergedSolidPipeline);
        CompiledRenderPipeline mergedCutoutCompiled = device.precompilePipeline(mergedCutoutPipeline);
        pipelineValid = passthroughSolidCompiled.isValid() && passthroughCutoutCompiled.isValid()
                && mergedSolidCompiled.isValid() && mergedCutoutCompiled.isValid();
        if (!pipelineValid) {
            throw new IllegalStateException("Phase 3 dev11 public passthrough/repeat-aware pipelines failed to compile");
        }
    }

    private static RenderPipeline buildPassthroughPipeline(
            String location,
            RenderPipeline template,
            RenderPipeline depthTemplate,
            boolean cutout) {
        RenderPipeline.Builder builder = basePipeline(location, template, depthTemplate)
                .withVertexBinding(0, DefaultVertexFormat.BLOCK);
        if (cutout) builder.withShaderDefine("ALPHA_CUTOUT", 0.5f);
        return builder.build();
    }

    private static RenderPipeline buildMergedPipeline(
            String location,
            RenderPipeline template,
            RenderPipeline depthTemplate,
            boolean cutout) {
        RenderPipeline.Builder builder = basePipeline(location, template, depthTemplate)
                .withVertexShader(REPEAT_AWARE_SHADER)
                .withFragmentShader(REPEAT_AWARE_SHADER)
                .withVertexBinding(0, RepeatAwareGreedyRenderFormat.MERGED);
        if (cutout) builder.withShaderDefine("ALPHA_CUTOUT", 0.5f);
        return builder.build();
    }

    private static RenderPipeline.Builder basePipeline(
            String location,
            RenderPipeline template,
            RenderPipeline depthTemplate) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(template.getVertexShader())
                .withFragmentShader(template.getFragmentShader())
                .withCull(false)
                .withColorTargetState(template.getColorTargetState())
                .withDepthStencilState(depthTemplate.getDepthStencilState())
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        for (BindGroupLayout layout : template.getBindGroupLayouts()) builder.withBindGroupLayout(layout);
        return builder;
    }

    private void submitDraw(GameRenderer renderer) {
        CommandEncoder encoder = device.createCommandEncoder();
        encodeLiveDraw(encoder, renderer);
        encoder.submit();
        usefulSubmissions++;
        drawSubmissions++;
        indirectCalls += INDIRECT_COMMAND_COUNT;
        triangles += drawableMesh.indexBytes() / Integer.BYTES / 3L;
    }

    public void requestInvalidate(int reasons, long frameSerial) {
        RenderSystem.assertOnRenderThread();
        invalidationReasons |= reasons;
        lastFrameSerial = frameSerial;
        if (state == State.WAITING_WORLD) {
            preinstallInvalidated = true;
            state = State.STALE;
            return;
        }
        if (state == State.WAITING_MESH) {
            preinstallInvalidated = true;
            SectionMeshWorkerPool.Ticket ticket = workerTicket;
            if (ticket != null && !ticket.terminal()) {
                workers.cancel(ticket);
                workerCancellationRequests++;
            }
            state = State.RETIRING;
            return;
        }
        if (state == State.READY_TO_INSTALL) {
            preinstallInvalidated = true;
            staleWorkerResultDiscards++;
            state = State.STALE;
            return;
        }
        if (state == State.LIVE) beginCleanupRetirement(frameSerial);
    }

    private void encodeLiveDraw(CommandEncoder encoder, GameRenderer renderer) {
        if (drawableMesh == null || bakedSnapshot == null || indirectCommands == null) {
            throw new IllegalStateException("Phase 3 dev11 worker-backed GPU resources are incomplete");
        }
        long currentEpoch = SectionMaterialSnapshot.currentResourceEpoch();
        resourceEpochChecks++;
        if (currentEpoch != bakedSnapshot.resourceEpoch()) {
            throw new IllegalStateException("Minecraft model/atlas resource epoch changed during Phase 3 dev11 draw");
        }

        CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || camera.pos == null || camera.viewRotationMatrix == null) {
            throw new IllegalStateException("Minecraft world camera render state is unavailable");
        }
        double relativeX = drawableMesh.originX() - camera.pos.x;
        double relativeY = drawableMesh.originY() - camera.pos.y;
        double relativeZ = drawableMesh.originZ() - camera.pos.z;
        if (!Double.isFinite(relativeX) || !Double.isFinite(relativeY) || !Double.isFinite(relativeZ)) {
            throw new IllegalStateException("Non-finite Phase 3 dev11 camera-relative section origin");
        }

        Matrix4f modelView = new Matrix4f(camera.viewRotationMatrix)
                .translate((float) relativeX, (float) relativeY, (float) relativeZ);
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(modelView);
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        GpuBufferSlice fog = RenderSystem.getShaderFog();
        if (dynamicTransforms == null || projection == null || fog == null
                || RenderSystem.getGlobalSettingsUniform() == null) {
            throw new IllegalStateException("Minecraft world uniform buffers are unavailable for Phase 3 dev11");
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
        if (target == null || !target.useDepth || target.getColorTextureView() == null
                || target.getDepthTextureView() == null) {
            throw new IllegalStateException("Minecraft main world color/depth target is unavailable");
        }
        GpuBufferSlice passthroughVertexSlice = arena.slice(passthroughVertexHandle);
        GpuBufferSlice mergedVertexSlice = arena.slice(mergedVertexHandle);
        GpuBufferSlice indexSlice = arena.slice(indexHandle);

        encodeLayerPass(encoder, target, passthroughSolidPipeline, PASSTHROUGH_SOLID_PASS_LABEL,
                dynamicTransforms, projection, fog, blocksAtlas, renderer, passthroughVertexSlice, indexSlice,
                indirectCommands.buffer().slice(0L, IndexedIndirectCommandBuffer.COMMAND_BYTES));
        encodeLayerPass(encoder, target, passthroughCutoutPipeline, PASSTHROUGH_CUTOUT_PASS_LABEL,
                dynamicTransforms, projection, fog, blocksAtlas, renderer, passthroughVertexSlice, indexSlice,
                indirectCommands.buffer().slice(IndexedIndirectCommandBuffer.COMMAND_BYTES,
                        IndexedIndirectCommandBuffer.COMMAND_BYTES));
        encodeLayerPass(encoder, target, mergedSolidPipeline, MERGED_SOLID_PASS_LABEL,
                dynamicTransforms, projection, fog, blocksAtlas, renderer, mergedVertexSlice, indexSlice,
                indirectCommands.buffer().slice(2L * IndexedIndirectCommandBuffer.COMMAND_BYTES,
                        IndexedIndirectCommandBuffer.COMMAND_BYTES));
        encodeLayerPass(encoder, target, mergedCutoutPipeline, MERGED_CUTOUT_PASS_LABEL,
                dynamicTransforms, projection, fog, blocksAtlas, renderer, mergedVertexSlice, indexSlice,
                indirectCommands.buffer().slice(3L * IndexedIndirectCommandBuffer.COMMAND_BYTES,
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

        if (!initialSubmissionCompleted) {
            pollPreinstallRetirement();
            return;
        }

        if (!tryRegisterRetirements(frameSerial)) return;
        if (staging.reclaimedBatches() < uploadBatchOrdinal) return;
        if (arena.isAllocated(passthroughVertexHandle) || arena.isAllocated(mergedVertexHandle)
                || arena.isAllocated(indexHandle)) return;
        if (resourceReleaseOrdinal > 0L && deferredReleases.releasedCount() < resourceReleaseOrdinal) return;

        if (validationFailure != null) {
            state = State.FAILED;
            return;
        }
        state = State.RETIRED;
    }

    private void pollPreinstallRetirement() {
        SectionMeshWorkerPool.Ticket ticket = workerTicket;
        if (ticket == null) {
            state = State.RETIRED;
            return;
        }
        if (!ticket.terminal()) return;

        if (ticket.state() == SectionMeshWorkerPool.TicketState.FAILED) {
            Throwable cause = ticket.failure();
            validationFailure = cause == null
                    ? new IllegalStateException("Invalidated worker ticket failed without cause")
                    : cause;
            state = State.FAILED;
            return;
        }
        if (ticket.state() == SectionMeshWorkerPool.TicketState.CANCELLED) {
            workerJobsCancelled++;
        } else if (ticket.state() == SectionMeshWorkerPool.TicketState.COMPLETED) {
            workerJobsCompleted++;
            staleWorkerResultDiscards++;
        }
        workerTicket = null;
        state = State.RETIRED;
    }

    private void beginCleanupRetirement(long frameSerial) {
        if (!initialSubmissionCompleted || state == State.RETIRING) return;
        try {
            CommandEncoder encoder = device.createCommandEncoder();
            pendingArenaFence = encoder.createFence();
            pendingResourceFence = encoder.createFence();
            encoder.submit();
            usefulSubmissions++;
            retirementHandles[0] = passthroughVertexHandle;
            retirementHandles[1] = mergedVertexHandle;
            retirementHandles[2] = indexHandle;
            state = State.RETIRING;
            tryRegisterRetirements(frameSerial);
        } catch (RuntimeException cleanupFailure) {
            validationFailure = cleanupFailure;
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 3 dev11 could not submit completion-gated cleanup; preserving ownership for shutdown.",
                    cleanupFailure);
        }
    }

    private boolean tryRegisterRetirements(long frameSerial) {
        if (pendingArenaFence != null) {
            try {
                if (!arena.retireBatch(pendingArenaFence, retirementHandles, retirementHandles.length)) {
                    retirementBackpressureEvents++;
                    return false;
                }
                pendingArenaFence = null;
            } catch (RuntimeException e) {
                retirementRegistrationFailures++;
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
                return false;
            }
        }
        return pendingArenaFence == null && pendingResourceFence == null;
    }

    private void failBeforeGpuInstall() {
        cancelWorkerTicket();
        cancelUnsubmittedAllocations();
        closeIndirectIfOwned();
        state = State.FAILED;
    }

    private void cancelWorkerTicket() {
        SectionMeshWorkerPool.Ticket ticket = workerTicket;
        if (ticket != null && !ticket.terminal()) {
            workers.cancel(ticket);
            workerCancellationRequests++;
        }
    }

    private int checkedFirstIndex(GpuBufferSlice indexSlice) {
        if ((indexSlice.offset() & (Integer.BYTES - 1L)) != 0L) {
            throw new IllegalStateException("Phase 3 dev11 index allocation is not 4-byte aligned");
        }
        long value = indexSlice.offset() / Integer.BYTES;
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException("Phase 3 dev11 firstIndex exceeds public indirect command range");
        }
        return (int) value;
    }

    private static ByteBuffer indirectCommand(int indexCount, int firstIndex) {
        if (indexCount < 0) throw new IllegalArgumentException("Phase 3 dev11 indirect index count is negative");
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
        cancelHandle(passthroughVertexHandle);
        cancelHandle(mergedVertexHandle);
        cancelHandle(indexHandle);
        passthroughVertexHandle = DeviceGeometryArena.INVALID_HANDLE;
        mergedVertexHandle = DeviceGeometryArena.INVALID_HANDLE;
        indexHandle = DeviceGeometryArena.INVALID_HANDLE;
    }

    private void cancelHandle(long handle) {
        if (handle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(handle)) arena.cancelUnsubmitted(handle);
    }

    private void closeIndirectIfOwned() {
        if (indirectCommands == null) return;
        try {
            indirectCommands.close();
        } finally {
            indirectCommands = null;
        }
    }

    public State state() { return state; }
    public SectionSnapshot snapshot() { return snapshot; }
    public ReferenceFaceMesh referenceMesh() { return referenceMesh; }
    public SectionBakedQuadSnapshot bakedSnapshot() { return bakedSnapshot; }
    public BakedSectionMesh drawableMesh() { return oracleMesh; }
    public RepeatAwareGreedyMesh repeatAwareGreedyMesh() { return drawableMesh; }
    public boolean pipelineValid() { return pipelineValid; }
    public boolean initialSubmissionCompleted() { return initialSubmissionCompleted; }
    public boolean workerBacked() { return true; }
    public boolean repeatAwareGreedyEmissionInstalled() { return initialSubmissionCompleted && drawableMesh != null && pipelineValid; }
    public long usefulSubmissions() { return usefulSubmissions; }
    public long drawSubmissions() { return drawSubmissions; }
    public long indirectCalls() { return indirectCalls; }
    public long triangles() { return triangles; }
    public long resourceEpochChecks() { return resourceEpochChecks; }
    public long retirementBackpressureEvents() { return retirementBackpressureEvents; }
    public long retirementRegistrationFailures() { return retirementRegistrationFailures; }
    public long workerJobsSubmitted() { return workerJobsSubmitted; }
    public long workerJobsCompleted() { return workerJobsCompleted; }
    public long workerJobsCancelled() { return workerJobsCancelled; }
    public long workerCancellationRequests() { return workerCancellationRequests; }
    public long staleWorkerResultDiscards() { return staleWorkerResultDiscards; }
    public long workerResultInstalls() { return workerResultInstalls; }
    public long workerQueueRejections() { return workerQueueRejections; }
    public long installAdmissionDeferrals() { return installAdmissionDeferrals; }
    public long synchronousMeshBuilds() { return 0L; }
    public boolean workerJobOutstanding() {
        SectionMeshWorkerPool.Ticket ticket = workerTicket;
        return ticket != null && !ticket.terminal();
    }
    public long emittedMergedQuads() { return emittedMergedQuads; }
    public long emittedSuppressedSourceQuads() { return emittedSuppressedSourceQuads; }
    public long emittedFacesSaved() { return emittedFacesSaved; }
    public long emittedPassthroughQuads() { return emittedPassthroughQuads; }
    public long emittedHybridQuads() { return emittedHybridQuads; }
    public long emittedHybridUploadBytes() { return emittedHybridUploadBytes; }
    public long emittedSourceUploadBytes() { return emittedSourceUploadBytes; }
    public int invalidationReasons() { return invalidationReasons; }
    public boolean preinstallInvalidated() { return preinstallInvalidated; }
    public Throwable validationFailure() { return validationFailure; }
    public long generation() { return generation; }
    public long buildEventSequence() { return buildEventSequence; }
    public int workerPriority() { return workerPriority; }
    public int sectionX() { return requestedSectionX; }
    public int sectionY() { return requestedSectionY; }
    public int sectionZ() { return requestedSectionZ; }
    public long vertexBytes() {
        return drawableMesh == null ? 0L : (long) drawableMesh.passthroughVertexBytes() + drawableMesh.mergedVertexBytes();
    }
    public long indexBytes() { return drawableMesh == null ? 0L : drawableMesh.indexBytes(); }
    public long quadCount() { return drawableMesh == null ? 0L : drawableMesh.hybridQuadCount(); }
    public TJunctionTopologyProof tJunctionTopologyProof() { return tJunctionTopologyProof; }
    public boolean transformCaptured() { return firstTransformCaptured; }
    public boolean cameraRelativeTransformEvidenceReady() {
        if (!firstTransformCaptured) return false;
        double expectedX = requestedSectionX * (double) SectionSnapshot.INTERIOR_SIZE - firstCameraX;
        double expectedY = requestedSectionY * (double) SectionSnapshot.INTERIOR_SIZE - firstCameraY;
        double expectedZ = requestedSectionZ * (double) SectionSnapshot.INTERIOR_SIZE - firstCameraZ;
        return Double.doubleToRawLongBits(expectedX) == Double.doubleToRawLongBits(firstRelativeX)
                && Double.doubleToRawLongBits(expectedY) == Double.doubleToRawLongBits(firstRelativeY)
                && Double.doubleToRawLongBits(expectedZ) == Double.doubleToRawLongBits(firstRelativeZ)
                && Float.isFinite((float) firstRelativeX)
                && Float.isFinite((float) firstRelativeY)
                && Float.isFinite((float) firstRelativeZ);
    }
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
        cancelWorkerTicket();

        if (initialSubmissionCompleted) {
            if (state != State.RETIRING && state != State.RETIRED) beginCleanupRetirement(lastFrameSerial);
            if (pendingArenaFence != null) {
                try {
                    if (arena.retireBatch(pendingArenaFence, retirementHandles, retirementHandles.length)) pendingArenaFence = null;
                } catch (RuntimeException ignored) { }
            }
            if (pendingResourceFence != null && indirectCommands != null) {
                try {
                    deferredReleases.retire(indirectCommands, pendingResourceFence, lastFrameSerial);
                    resourceReleaseOrdinal = deferredReleases.retiredCount();
                    indirectCommands = null;
                    pendingResourceFence = null;
                } catch (RuntimeException ignored) { }
            }
        } else {
            cancelUnsubmittedAllocations();
            closeIndirectIfOwned();
        }

        snapshot = null;
        referenceMesh = null;
        bakedSnapshot = null;
        oracleMesh = null;
        drawableMesh = null;
        workerTicket = null;
        state = State.CLOSED;
    }
}
