package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderPass;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.mesh.MeshingBenchmarkTelemetry;
import dev.obsidian.render.mesh.SectionMeshWorkerPool;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.AsyncMultiSectionSceneProbe;
import dev.obsidian.render.terrain.BinarySectionVisibility;
import dev.obsidian.render.terrain.CanonicalFaceRenderKeys;
import dev.obsidian.render.terrain.RenderMergeCandidates;
import dev.obsidian.render.terrain.ProductionTerrainReplacementPlan;
import dev.obsidian.render.terrain.SectionLifecycleEvents;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

/** Render-thread lifecycle root for active Phase 3 P3.8 dev15 meshing benchmark evidence. */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");
    private static final int VALIDATION_STAGING_BYTES = 4 * 1024 * 1024;
    private static final int VALIDATION_DEVICE_ARENA_BYTES = 32 * 1024 * 1024;
    private static final long VISUAL_ARM_DELAY_NS = 5_000_000_000L;

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final FrameContextRing frameContexts = new FrameContextRing();
    private final DeferredReleaseQueue deferredReleases = new DeferredReleaseQueue();
    private final StagingUploadArena stagingUploads;
    private final DeviceGeometryArena deviceArena;
    private final SectionMeshWorkerPool meshWorkers;
    private final ProductionTerrainReplacementPlan terrainReplacement = new ProductionTerrainReplacementPlan();

    private AsyncMultiSectionSceneProbe sceneProbe;
    private FrameContext activeFrame;
    private long frameIndex;
    private long firstWorldRenderNs;
    private boolean firstFrameLogged;
    private boolean visualDelayLogged;
    private boolean runtimeInstructionsLogged;
    private boolean benchmarkWindowArmed;
    private boolean benchmarkEvidenceLogged;
    private boolean meshingBenchmarkEvidenceReady;
    private long benchmarkStartSceneReadyTransitions;
    private long benchmarkStartCameraRecenterEvents;
    private long benchmarkStartRenderedCoreDirtyEvents;
    private long benchmarkStartResourceReloadEvents;
    private long benchmarkStartWorkerCompletedJobs;
    private long benchmarkStartWorkerCancelledJobs;
    private long benchmarkStartWorkerStolenJobs;
    private long benchmarkStartWorkerQueueRejections;
    private long benchmarkStartSceneStaleDiscards;
    private boolean fixedAnchorReturnSceneReady;
    private boolean hardFailure;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        SectionMeshWorkerPool workers = null;
        try {
            workers = new SectionMeshWorkerPool(SectionMeshWorkerPool.defaultWorkerCount());
            staging = new StagingUploadArena(
                    device, () -> "Obsidian Phase 3 dev15 bounded scene staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device, () -> "Obsidian Phase 3 dev15 scene device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            sceneProbe = new AsyncMultiSectionSceneProbe(device, staging, arena, deferredReleases, workers);
        } catch (RuntimeException e) {
            if (sceneProbe != null) try { sceneProbe.close(); } catch (RuntimeException ignored) { }
            if (workers != null) try { workers.close(); } catch (RuntimeException ignored) { }
            if (staging != null) try { staging.close(); } catch (RuntimeException ignored) { }
            if (arena != null) try { arena.close(); } catch (RuntimeException ignored) { }
            try { deferredReleases.close(); } catch (RuntimeException ignored) { }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 3 dev15 P3.8 initialization failed; Minecraft will continue for diagnosis.", e);
            hardFailure = true;
        }
        meshWorkers = workers;
        stagingUploads = staging;
        deviceArena = arena;
    }

    public void beginFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        frameIndex++;
        activeFrame = frameContexts.begin(frameIndex, System.nanoTime());
        if (terrainReplacement.hardFailure()) hardFailure = true;
        if (sceneProbe != null) {
            sceneProbe.beginFrame(frameIndex);
            if (sceneProbe.hardFailure()) hardFailure = true;
        }
        if (!firstFrameLogged) {
            firstFrameLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 3 dev15 P3.8 frame coordinator active. contextSlots=" + frameContexts.size()
                            + ", cpuTimingCapacity=" + cpuFrameTimings.capacity()
                            + ", meshWorkers=" + (meshWorkers == null ? 0 : meshWorkers.workerCount())
                            + ", meshQueueCapacity=" + (meshWorkers == null ? 0 : meshWorkers.queueCapacity())
                            + ", stagingCapacity=" + (stagingUploads == null ? 0 : stagingUploads.capacityBytes())
                            + ", deviceArenaCapacity=" + (deviceArena == null ? 0L : deviceArena.capacityBytes())
                            + "; all proven P3.2-P3.7 correctness and dev11 repeat-aware greedy GPU emission remain armed unchanged. P3.8 dev15 adds only bounded production-worker benchmark telemetry: fixed primitive samples, workload identity, GC deltas and stage timing composition. Dev15 changes no geometry, shader, pipeline, vertex/index format, atlas/lightmap semantics, scheduling policy, rebuild granularity or native graphics behavior.");
        }
    }

    public void afterWorldRender(GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || stagingUploads == null || deviceArena == null || meshWorkers == null) return;
        long nowNs = System.nanoTime();
        if (firstWorldRenderNs == 0L) firstWorldRenderNs = nowNs;
        if (nowNs - firstWorldRenderNs < VISUAL_ARM_DELAY_NS) {
            if (!visualDelayLogged) {
                visualDelayLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 3 dev15 P3.8 validation is delayed for 5 seconds after first world render so startup activity settles before scene jobs are admitted.");
            }
            return;
        }
        if (sceneProbe != null) {
            sceneProbe.afterWorldRender(renderer, frameIndex);
            if (sceneProbe.hardFailure()) {
                hardFailure = true;
                return;
            }
        }
        if (!benchmarkWindowArmed
                && sceneProbe != null
                && sceneProbe.state() == AsyncMultiSectionSceneProbe.State.LIVE
                && sceneProbe.differentialCorrectnessEvidenceReady()
                && meshWorkers.outstandingJobs() == 0) {
            benchmarkWindowArmed = true;
            runtimeInstructionsLogged = true;
            SectionLifecycleEvents.Cursor cursor = sceneProbe.lifecycleCursor();
            benchmarkStartSceneReadyTransitions = sceneProbe.sceneReadyTransitions();
            benchmarkStartCameraRecenterEvents = sceneProbe.cameraRecenterEvents();
            benchmarkStartRenderedCoreDirtyEvents = cursor.renderedCoreDirtyEvents();
            benchmarkStartResourceReloadEvents = cursor.resourceReloadEvents();
            benchmarkStartWorkerCompletedJobs = meshWorkers.completedJobs();
            benchmarkStartWorkerCancelledJobs = meshWorkers.cancelledJobs();
            benchmarkStartWorkerStolenJobs = meshWorkers.stolenJobs();
            benchmarkStartWorkerQueueRejections = meshWorkers.queueFullRejections();
            benchmarkStartSceneStaleDiscards = sceneProbe.staleWorkerResultDiscards();
            long benchmarkStartNs = meshWorkers.beginBenchmarkWindow();
            LOG.log(System.Logger.Level.INFO,
                    "Phase 3 dev15 P3.8 measured benchmark window armed after settled READY at ns={0}. The window records only completed production full-section jobs enqueued after this point. Exercise multiple ordinary block break/place rebuilds with READY recovery, perform F3+T and let READY return, traverse far enough for a real scene-recenter and READY return, include a short bounded edit/traversal burst so concurrent worker pressure is observed, then exit normally. No new visual verdict is required because dev15 changes no rendering semantics.",
                    benchmarkStartNs);
        }
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        FrameContext context = activeFrame;
        activeFrame = null;
        if (context != null) {
            long duration = context.finish(System.nanoTime());
            if (duration > 0L) cpuFrameTimings.record(duration);
        }
        deferredReleases.poll();
        if (stagingUploads != null) stagingUploads.pollReclaims();
        if (deviceArena != null) deviceArena.pollRetirements();
        if (sceneProbe != null) {
            sceneProbe.endFrame(frameIndex);
            if (sceneProbe.hardFailure()) hardFailure = true;
            SectionLifecycleEvents.Cursor cursor = sceneProbe.lifecycleCursor();
            if (cursor.fixedAnchorChunkUnloadEvents() > 0L
                    && cursor.fixedAnchorChunkLoadEvents() > 0L
                    && sceneProbe.state() == AsyncMultiSectionSceneProbe.State.LIVE) {
                fixedAnchorReturnSceneReady = true;
            }
            evaluateMeshingBenchmarkEvidenceIfSettled();
        }
    }

    private void evaluateMeshingBenchmarkEvidenceIfSettled() {
        if (!benchmarkWindowArmed || benchmarkEvidenceLogged || hardFailure
                || sceneProbe == null || meshWorkers == null
                || sceneProbe.state() != AsyncMultiSectionSceneProbe.State.LIVE
                || meshWorkers.outstandingJobs() != 0) return;
        SectionLifecycleEvents.Cursor cursor = sceneProbe.lifecycleCursor();
        long readyDelta = sceneProbe.sceneReadyTransitions() - benchmarkStartSceneReadyTransitions;
        long dirtyDelta = cursor.renderedCoreDirtyEvents() - benchmarkStartRenderedCoreDirtyEvents;
        long reloadDelta = cursor.resourceReloadEvents() - benchmarkStartResourceReloadEvents;
        long recenterDelta = sceneProbe.cameraRecenterEvents() - benchmarkStartCameraRecenterEvents;
        if (readyDelta < 3L || dirtyDelta < 2L || reloadDelta < 1L || recenterDelta < 1L) return;
        MeshingBenchmarkTelemetry.Snapshot snapshot = meshWorkers.benchmarkSnapshot();
        if (!sceneProbe.differentialCorrectnessEvidenceReady()
                || !benchmarkSnapshotReady(snapshot)
                || (snapshot.maxRunningJobs() < 2 && snapshot.maxQueuedJobs() == 0)) return;
        meshingBenchmarkEvidenceReady = true;
        benchmarkEvidenceLogged = true;
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev15 P3.8 benchmark gate armed: meshingBenchmarkEvidenceReady=true, samples={0}, durationMs={1}, queueWaitNs[p50/p95/p99/max]={2}/{3}/{4}/{5}, executionNs[p50/p95/p99/max]={6}/{7}/{8}/{9}, sourceQuads={10}, referenceFaces={11}, mergedIdentities={12}, mergedCoveredFaces={13}, outputBytes={14}, workerBusyPermille={15}, gcCollections={16}, gcTimeMs={17}, readyDelta={18}, coreDirtyDelta={19}, reloadDelta={20}, recenterDelta={21}.",
                snapshot.completedSamples(), snapshot.durationNs() / 1_000_000L,
                snapshot.queueWait().p50Ns(), snapshot.queueWait().p95Ns(), snapshot.queueWait().p99Ns(), snapshot.queueWait().maxNs(),
                snapshot.execution().p50Ns(), snapshot.execution().p95Ns(), snapshot.execution().p99Ns(), snapshot.execution().maxNs(),
                snapshot.sourceBakedQuads(), snapshot.independentReferenceFaces(), snapshot.mergedIdentities(),
                snapshot.mergedCoveredSourceFaces(), snapshot.outputBytes(), snapshot.workerBusyPermille(meshWorkers.workerCount()),
                snapshot.gcCollectionDelta(), snapshot.gcTimeDeltaMs(), readyDelta, dirtyDelta, reloadDelta, recenterDelta);
    }

    private boolean benchmarkSnapshotReady(MeshingBenchmarkTelemetry.Snapshot snapshot) {
        return snapshot != null
                && snapshot.armed()
                && snapshot.durationNs() > 0L
                && snapshot.completedSamples() > 0L
                && snapshot.percentileAccountingCoherent()
                && snapshot.collectorSelfTestPassed()
                && snapshot.sourceBakedQuads() > 0L
                && snapshot.independentReferenceFaces() > 0L
                && snapshot.topologyRectangles() > 0L
                && snapshot.topologyCoveredFaces() > 0L
                && snapshot.renderMergeCandidates() > 0L
                && snapshot.passthroughIdentities() > 0L
                && snapshot.mergedIdentities() > 0L
                && snapshot.mergedCoveredSourceFaces() > 0L
                && snapshot.outputVertexBytes() > 0L
                && snapshot.outputIndexBytes() > 0L
                && meshWorkers.workerCount() > 0
                && meshWorkers.maxScratchQuads() > 0L
                && meshWorkers.maxVisibilityScratchRows() > 0L
                && meshWorkers.maxRectangleScratchRectangles() > 0L
                && meshWorkers.maxRenderKeyScratchEligibleFaces() > 0L
                && meshWorkers.maxMergeCandidateScratchCandidates() > 0L
                && meshWorkers.maxEmissionSafetyScratchCandidates() > 0L
                && meshWorkers.maxRepeatAwareUvScratchDescriptors() > 0L
                && meshWorkers.maxRepeatAwareTransportScratchRecords() > 0L;
    }

    public void beginProductionTerrainPreparation() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        terrainReplacement.beginPrepare(frameIndex);
        if (terrainReplacement.hardFailure()) hardFailure = true;
    }

    public boolean tryClaimProductionTerrainReplacement(int x, int y, int z, ChunkSectionLayer layer) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || sceneProbe == null) return false;
        boolean claimed = terrainReplacement.tryClaim(sceneProbe, x, y, z, layer);
        if (terrainReplacement.hardFailure()) hardFailure = true;
        return claimed;
    }

    public void encodeProductionTerrainReplacements(RenderPass pass, GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;
        terrainReplacement.encodeOpaque(pass, renderer);
        if (terrainReplacement.hardFailure()) hardFailure = true;
    }

    public long frameIndex() { return frameIndex; }
    public long latestCpuFrameTimeNs() { return cpuFrameTimings.latestNs(); }
    public FrameTimings cpuFrameTimings() { return cpuFrameTimings; }
    public int pendingRetirements() { return deferredReleases.pendingCount(); }
    public int pendingUploadBatches() { return stagingUploads == null ? 0 : stagingUploads.pendingBatches(); }
    public int pendingArenaRetirementBatches() {
        return deviceArena == null ? 0 : deviceArena.pendingRetirementBatches();
    }
    public AsyncMultiSectionSceneProbe.State sceneProbeState() {
        if (sceneProbe != null) return sceneProbe.state();
        return hardFailure ? AsyncMultiSectionSceneProbe.State.FAILED : AsyncMultiSectionSceneProbe.State.WAITING_WORLD;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        terrainReplacement.close();
        if (terrainReplacement.hardFailure()) hardFailure = true;

        AsyncMultiSectionSceneProbe probe = sceneProbe;
        long usefulSubmissions = 0L, comparisonDraws = 0L, indirectCalls = 0L, resourceEpochChecks = 0L;
        long retirementBackpressureEvents = 0L, retirementRegistrationFailures = 0L;
        long sceneReadyTransitions = 0L, sceneRebuilds = 0L, recordInstallCount = 0L;
        long cameraRecenterEvents = 0L, invalidationBatches = 0L, coalescedEvents = 0L;
        long eligibilityScans = 0L, eligibilitySkips = 0L, unsafeStaleSceneInstalls = 0L;
        long schedulerAdmissionDeferrals = 0L, admittedHighPriority = 0L, admittedNormalPriority = 0L, admittedLowPriority = 0L;
        int maxAdmissionBurst = 0, observedReasonMask = 0, maxLiveRecords = 0, maxAdjacentPairs = 0, maxSimultaneousSceneJobs = 0;
        long maxSceneQuads = 0L, maxSceneVertexBytes = 0L, maxSceneIndexBytes = 0L;
        long sceneWorkerSubmitted = 0L, sceneWorkerCompleted = 0L, sceneWorkerCancelled = 0L;
        long sceneWorkerCancellationRequests = 0L, sceneWorkerStaleDiscards = 0L, sceneWorkerInstalls = 0L;
        long sceneWorkerQueueRejections = 0L, sceneInstallAdmissionDeferrals = 0L, synchronousSceneMeshBuilds = 0L, preinstallInvalidations = 0L;
        long sceneGeneration = 0L;
        long borderProofRecords = 0L, borderProofDeterminismAudits = 0L, borderProofDeterminismMatches = 0L;
        long borderOutwardChecks = 0L, borderVisibilityMatches = 0L, borderReferenceMatches = 0L;
        long borderExpectedVisibleFaces = 0L, borderUnsupportedBlockedFaces = 0L;
        long borderBakedQuads = 0L, borderLightColorSamples = 0L;
        long sharedBorderPairAudits = 0L, sharedBorderComparisons = 0L, sharedBorderMatches = 0L;
        long tJunctionProofRecords = 0L, tJunctionProofDeterminismAudits = 0L, tJunctionProofDeterminismMatches = 0L;
        long tJunctionEmittedCandidates = 0L, tJunctionEmittedEdges = 0L;
        long tJunctionStrictInteriorLatticePoints = 0L, tJunctionStrictPoints = 0L;
        long tJunctionBoundsChecks = 0L, tJunctionBoundsMatches = 0L;
        long tJunctionPlaneChecks = 0L, tJunctionPlaneMatches = 0L;
        long tJunctionIntegerLatticeChecks = 0L, tJunctionIntegerLatticeMatches = 0L;
        long cameraRelativeTransformProofRecords = 0L, junctionBearingTransformProofRecords = 0L;
        long cameraRelativeTransformFailures = 0L;
        long differentialProofRecords = 0L, differentialProofDeterminismAudits = 0L, differentialProofDeterminismMatches = 0L;
        long differentialReferenceFacesChecked = 0L, differentialReferenceMappedFaces = 0L, differentialReferenceUnmappedFaces = 0L, differentialReferenceAmbiguousFaces = 0L;
        long differentialSourceQuadsChecked = 0L, differentialPassthroughIdentitiesChecked = 0L;
        long differentialMergedCandidatesChecked = 0L, differentialMergedExpandedFacesChecked = 0L;
        long differentialMaterialChecks = 0L, differentialMaterialMatches = 0L;
        long differentialDirectionChecks = 0L, differentialDirectionMatches = 0L;
        long differentialGeometryChecks = 0L, differentialGeometryMatches = 0L;
        long differentialUvChecks = 0L, differentialUvMatches = 0L;
        long differentialColorChecks = 0L, differentialColorMatches = 0L;
        long differentialLightChecks = 0L, differentialLightMatches = 0L;
        long differentialMissing = 0L, differentialDuplicate = 0L, differentialOptimizedWithoutReference = 0L;
        long differentialRealMismatches = 0L, differentialFixtureSelfTestPasses = 0L;
        String center = "unbound";
        boolean localSceneReady = false, productionWorkerIntegrationReady = false;
        boolean borderSceneEvidenceReady = false, tJunctionSceneEvidenceReady = false, differentialSceneEvidenceReady = false;
        SectionLifecycleEvents.Cursor lifecycleCursor = null;

        if (probe != null) {
            usefulSubmissions = probe.usefulSubmissions();
            comparisonDraws = probe.drawSubmissions();
            indirectCalls = probe.indirectCalls();
            resourceEpochChecks = probe.resourceEpochChecks();
            retirementBackpressureEvents = probe.retirementBackpressureEvents();
            retirementRegistrationFailures = probe.retirementRegistrationFailures();
            sceneReadyTransitions = probe.sceneReadyTransitions();
            sceneRebuilds = probe.sceneRebuilds();
            recordInstallCount = probe.recordInstallCount();
            cameraRecenterEvents = probe.cameraRecenterEvents();
            invalidationBatches = probe.invalidationBatches();
            coalescedEvents = probe.coalescedEvents();
            eligibilityScans = probe.eligibilityScans();
            eligibilitySkips = probe.eligibilitySkips();
            unsafeStaleSceneInstalls = probe.unsafeStaleSceneInstalls();
            schedulerAdmissionDeferrals = probe.schedulerAdmissionDeferrals();
            admittedHighPriority = probe.admittedHighPriority();
            admittedNormalPriority = probe.admittedNormalPriority();
            admittedLowPriority = probe.admittedLowPriority();
            maxAdmissionBurst = probe.maxAdmissionBurst();
            observedReasonMask = probe.observedReasonMask();
            maxLiveRecords = probe.maxLiveRecords();
            maxAdjacentPairs = probe.maxAdjacentPairs();
            maxSimultaneousSceneJobs = probe.maxSimultaneousSceneJobs();
            maxSceneQuads = probe.maxSceneQuads();
            maxSceneVertexBytes = probe.maxSceneVertexBytes();
            maxSceneIndexBytes = probe.maxSceneIndexBytes();
            sceneWorkerSubmitted = probe.workerJobsSubmitted();
            sceneWorkerCompleted = probe.workerJobsCompleted();
            sceneWorkerCancelled = probe.workerJobsCancelled();
            sceneWorkerCancellationRequests = probe.workerCancellationRequests();
            sceneWorkerStaleDiscards = probe.staleWorkerResultDiscards();
            sceneWorkerInstalls = probe.workerResultInstalls();
            sceneWorkerQueueRejections = probe.workerQueueRejections();
            sceneInstallAdmissionDeferrals = probe.installAdmissionDeferrals();
            synchronousSceneMeshBuilds = probe.synchronousSceneMeshBuilds();
            preinstallInvalidations = probe.preinstallInvalidations();
            borderProofRecords = probe.borderProofRecords();
            borderProofDeterminismAudits = probe.borderProofDeterminismAudits();
            borderProofDeterminismMatches = probe.borderProofDeterminismMatches();
            borderOutwardChecks = probe.borderOutwardChecks();
            borderVisibilityMatches = probe.borderVisibilityMatches();
            borderReferenceMatches = probe.borderReferenceMatches();
            borderExpectedVisibleFaces = probe.borderExpectedVisibleFaces();
            borderUnsupportedBlockedFaces = probe.borderUnsupportedBlockedFaces();
            borderBakedQuads = probe.borderBakedQuads();
            borderLightColorSamples = probe.borderLightColorSamples();
            sharedBorderPairAudits = probe.sharedBorderPairAudits();
            sharedBorderComparisons = probe.sharedBorderComparisons();
            sharedBorderMatches = probe.sharedBorderMatches();
            tJunctionProofRecords = probe.tJunctionProofRecords();
            tJunctionProofDeterminismAudits = probe.tJunctionProofDeterminismAudits();
            tJunctionProofDeterminismMatches = probe.tJunctionProofDeterminismMatches();
            tJunctionEmittedCandidates = probe.tJunctionEmittedCandidates();
            tJunctionEmittedEdges = probe.tJunctionEmittedEdges();
            tJunctionStrictInteriorLatticePoints = probe.tJunctionStrictInteriorLatticePoints();
            tJunctionStrictPoints = probe.tJunctionStrictPoints();
            tJunctionBoundsChecks = probe.tJunctionBoundsChecks();
            tJunctionBoundsMatches = probe.tJunctionBoundsMatches();
            tJunctionPlaneChecks = probe.tJunctionPlaneChecks();
            tJunctionPlaneMatches = probe.tJunctionPlaneMatches();
            tJunctionIntegerLatticeChecks = probe.tJunctionIntegerLatticeChecks();
            tJunctionIntegerLatticeMatches = probe.tJunctionIntegerLatticeMatches();
            cameraRelativeTransformProofRecords = probe.cameraRelativeTransformProofRecords();
            junctionBearingTransformProofRecords = probe.junctionBearingTransformProofRecords();
            cameraRelativeTransformFailures = probe.cameraRelativeTransformFailures();
            differentialProofRecords = probe.differentialProofRecords();
            differentialProofDeterminismAudits = probe.differentialProofDeterminismAudits();
            differentialProofDeterminismMatches = probe.differentialProofDeterminismMatches();
            differentialReferenceFacesChecked = probe.differentialReferenceFacesChecked();
            differentialReferenceMappedFaces = probe.differentialReferenceMappedFaces();
            differentialReferenceUnmappedFaces = probe.differentialReferenceUnmappedFaces();
            differentialReferenceAmbiguousFaces = probe.differentialReferenceAmbiguousFaces();
            differentialSourceQuadsChecked = probe.differentialSourceQuadsChecked();
            differentialPassthroughIdentitiesChecked = probe.differentialPassthroughIdentitiesChecked();
            differentialMergedCandidatesChecked = probe.differentialMergedCandidatesChecked();
            differentialMergedExpandedFacesChecked = probe.differentialMergedExpandedFacesChecked();
            differentialMaterialChecks = probe.differentialMaterialChecks();
            differentialMaterialMatches = probe.differentialMaterialMatches();
            differentialDirectionChecks = probe.differentialDirectionChecks();
            differentialDirectionMatches = probe.differentialDirectionMatches();
            differentialGeometryChecks = probe.differentialCanonicalGeometryChecks();
            differentialGeometryMatches = probe.differentialCanonicalGeometryMatches();
            differentialUvChecks = probe.differentialUvChecks();
            differentialUvMatches = probe.differentialUvMatches();
            differentialColorChecks = probe.differentialColorChecks();
            differentialColorMatches = probe.differentialColorMatches();
            differentialLightChecks = probe.differentialLightChecks();
            differentialLightMatches = probe.differentialLightMatches();
            differentialMissing = probe.differentialMissingSourceCoverage();
            differentialDuplicate = probe.differentialDuplicateSourceCoverage();
            differentialOptimizedWithoutReference = probe.differentialOptimizedCanonicalWithoutReference();
            differentialRealMismatches = probe.differentialRealMismatchCount();
            differentialFixtureSelfTestPasses = probe.differentialFixtureSelfTestPasses();
            lifecycleCursor = probe.lifecycleCursor();
            sceneGeneration = probe.sceneGeneration();
            center = probe.centerKnown() ? "(" + probe.centerSectionX() + "," + probe.centerSectionY() + "," + probe.centerSectionZ() + ")" : "unbound";
            localSceneReady = probe.localSceneReady();
            productionWorkerIntegrationReady = probe.productionWorkerIntegrationReady();
            borderSceneEvidenceReady = probe.borderHaloCorrectnessEvidenceReady();
            tJunctionSceneEvidenceReady = probe.tJunctionPolicyEvidenceReady();
            differentialSceneEvidenceReady = probe.differentialCorrectnessEvidenceReady();
            probe.close();
            sceneProbe = null;
        }

        if (meshWorkers != null) meshWorkers.close();
        MeshingBenchmarkTelemetry.Snapshot benchmarkSnapshot =
                meshWorkers == null ? null : meshWorkers.benchmarkSnapshot();
        if (stagingUploads != null) stagingUploads.close();
        if (deviceArena != null) deviceArena.close();
        deferredReleases.close();

        long dirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.sectionDirtyEvents();
        long renderedCoreDirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.renderedCoreDirtyEvents();
        long haloOnlyDirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.haloOnlyDirtyEvents();
        long horizontalHaloDirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.horizontalHaloDirtyEvents();
        long verticalHaloDirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.verticalHaloDirtyEvents();
        long playerDirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.playerDirtyEvents();
        long chunkLoadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.chunkLoadEvents();
        long chunkUnloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.chunkUnloadEvents();
        long fixedAnchorChunkLoadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.fixedAnchorChunkLoadEvents();
        long fixedAnchorChunkUnloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.fixedAnchorChunkUnloadEvents();
        long worldChangeEvents = lifecycleCursor == null ? 0L : lifecycleCursor.worldChangeEvents();
        long resourceReloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.resourceReloadEvents();
        long droppedLifecycleEvents = lifecycleCursor == null ? 0L : lifecycleCursor.droppedEvents();
        long benchmarkReadyDelta = sceneReadyTransitions - benchmarkStartSceneReadyTransitions;
        long benchmarkDirtyDelta = renderedCoreDirtyEvents - benchmarkStartRenderedCoreDirtyEvents;
        long benchmarkReloadDelta = resourceReloadEvents - benchmarkStartResourceReloadEvents;
        long benchmarkRecenterDelta = cameraRecenterEvents - benchmarkStartCameraRecenterEvents;
        long benchmarkWorkerCompletedDelta = meshWorkers == null ? 0L : meshWorkers.completedJobs() - benchmarkStartWorkerCompletedJobs;
        long benchmarkWorkerCancelledDelta = meshWorkers == null ? 0L : meshWorkers.cancelledJobs() - benchmarkStartWorkerCancelledJobs;
        long benchmarkWorkerStolenDelta = meshWorkers == null ? 0L : meshWorkers.stolenJobs() - benchmarkStartWorkerStolenJobs;
        long benchmarkWorkerQueueRejectionDelta = meshWorkers == null ? 0L : meshWorkers.queueFullRejections() - benchmarkStartWorkerQueueRejections;
        long benchmarkSceneStaleDiscardDelta = sceneWorkerStaleDiscards - benchmarkStartSceneStaleDiscards;

        boolean workersClean = meshWorkers != null
                && meshWorkers.queueFullRejections() == 0L
                && meshWorkers.failedJobs() == 0L
                && meshWorkers.queuedJobs() == 0
                && meshWorkers.runningJobs() == 0
                && meshWorkers.shutdownJoinFailures() == 0L;
        boolean stagingClean = stagingUploads != null
                && !stagingUploads.abandonedForDeviceShutdown()
                && stagingUploads.pendingBatches() == 0
                && stagingUploads.submittedBytes() == stagingUploads.reclaimedBytes();
        boolean arenaClean = deviceArena != null
                && !deviceArena.abandonedForDeviceShutdown()
                && deviceArena.pendingRetirementBatches() == 0
                && deviceArena.usedBytes() == 0L
                && deviceArena.retiredAllocations() == deviceArena.reclaimedAllocations();
        boolean resourcesClean = deferredReleases.pendingCount() == 0
                && deferredReleases.retiredCount() == deferredReleases.releasedCount();

        boolean phase3GateReady = !hardFailure
                && productionWorkerIntegrationReady && localSceneReady
                && sceneReadyTransitions >= 2L && sceneRebuilds >= 1L && recordInstallCount >= 3L
                && sceneWorkerInstalls == recordInstallCount && sceneWorkerCompleted >= sceneWorkerInstalls
                && synchronousSceneMeshBuilds == 0L && dirtyEvents > 0L && resourceReloadEvents > 0L
                && droppedLifecycleEvents == 0L && unsafeStaleSceneInstalls == 0L && sceneWorkerQueueRejections == 0L
                && workersClean && stagingClean && arenaClean && resourcesClean;

        long workerHighSubmitted = meshWorkers == null ? 0L : meshWorkers.submittedJobs(SectionMeshWorkerPool.PRIORITY_HIGH);
        long workerNormalSubmitted = meshWorkers == null ? 0L : meshWorkers.submittedJobs(SectionMeshWorkerPool.PRIORITY_NORMAL);
        long workerLowSubmitted = meshWorkers == null ? 0L : meshWorkers.submittedJobs(SectionMeshWorkerPool.PRIORITY_LOW);
        long workerDeterminismAudits = meshWorkers == null ? 0L : meshWorkers.determinismAudits();
        long workerDeterminismAuditMatches = meshWorkers == null ? 0L : meshWorkers.determinismAuditMatches();
        long workerScratchBuildUses = meshWorkers == null ? 0L : meshWorkers.scratchBuildUses();
        long workerOutputQuads = meshWorkers == null ? 0L : meshWorkers.totalOutputQuads();
        long workerOutputVertexBytes = meshWorkers == null ? 0L : meshWorkers.totalOutputVertexBytes();
        long workerOutputIndexBytes = meshWorkers == null ? 0L : meshWorkers.totalOutputIndexBytes();

        boolean schedulerEvidenceReady = phase3GateReady
                && maxAdmissionBurst >= 2
                && workerHighSubmitted > 0L
                && workerNormalSubmitted + workerLowSubmitted > 0L
                && workerScratchBuildUses > 0L
                && workerDeterminismAudits > 0L
                && workerDeterminismAuditMatches == workerDeterminismAudits
                && workerOutputQuads > 0L && workerOutputVertexBytes > 0L && workerOutputIndexBytes > 0L;

        long visibilityBuilds = meshWorkers == null ? 0L : meshWorkers.visibilityBuilds();
        long visibilityFaces = meshWorkers == null ? 0L : meshWorkers.totalVisibilityFaces();
        long visibilityRetainedBytes = meshWorkers == null ? 0L : meshWorkers.totalVisibilityRetainedBytes();
        long visibilityScratchUses = meshWorkers == null ? 0L : meshWorkers.visibilityScratchBuildUses();
        long visibilityDeterminismAudits = meshWorkers == null ? 0L : meshWorkers.visibilityDeterminismAudits();
        long visibilityDeterminismMatches = meshWorkers == null ? 0L : meshWorkers.visibilityDeterminismAuditMatches();
        long visibilityReferenceAudits = meshWorkers == null ? 0L : meshWorkers.visibilityReferenceAudits();
        long visibilityReferenceMatches = meshWorkers == null ? 0L : meshWorkers.visibilityReferenceAuditMatches();
        long visibilityWestFaces = meshWorkers == null ? 0L : meshWorkers.visibilityFaces(BinarySectionVisibility.WEST);
        long visibilityEastFaces = meshWorkers == null ? 0L : meshWorkers.visibilityFaces(BinarySectionVisibility.EAST);
        long visibilityDownFaces = meshWorkers == null ? 0L : meshWorkers.visibilityFaces(BinarySectionVisibility.DOWN);
        long visibilityUpFaces = meshWorkers == null ? 0L : meshWorkers.visibilityFaces(BinarySectionVisibility.UP);
        long visibilityNorthFaces = meshWorkers == null ? 0L : meshWorkers.visibilityFaces(BinarySectionVisibility.NORTH);
        long visibilitySouthFaces = meshWorkers == null ? 0L : meshWorkers.visibilityFaces(BinarySectionVisibility.SOUTH);
        long visibilityDirectionSum = visibilityWestFaces + visibilityEastFaces
                + visibilityDownFaces + visibilityUpFaces + visibilityNorthFaces + visibilitySouthFaces;
        long workerCompletedJobs = meshWorkers == null ? 0L : meshWorkers.completedJobs();

        boolean binaryVisibilityEvidenceReady = phase3GateReady
                && visibilityBuilds > 0L
                && visibilityBuilds >= workerCompletedJobs
                && visibilityFaces > 0L
                && visibilityDirectionSum == visibilityFaces
                && visibilityRetainedBytes == visibilityBuilds * BinarySectionVisibility.RETAINED_BYTES
                && visibilityScratchUses >= visibilityBuilds
                && visibilityDeterminismAudits > 0L
                && visibilityDeterminismMatches == visibilityDeterminismAudits
                && visibilityReferenceAudits > 0L
                && visibilityReferenceMatches == visibilityReferenceAudits
                && workersClean && stagingClean && arenaClean && resourcesClean;

        long rectangleBuilds = meshWorkers == null ? 0L : meshWorkers.rectangleBuilds();
        long rectangleCount = meshWorkers == null ? 0L : meshWorkers.totalRectangleCount();
        long rectangleCoveredFaces = meshWorkers == null ? 0L : meshWorkers.totalRectangleCoveredFaces();
        long rectangleRetainedBytes = meshWorkers == null ? 0L : meshWorkers.totalRectangleRetainedBytes();
        long rectangleScratchUses = meshWorkers == null ? 0L : meshWorkers.rectangleScratchBuildUses();
        long rectangleMaskAudits = meshWorkers == null ? 0L : meshWorkers.rectangleMaskCoverageAudits();
        long rectangleMaskMatches = meshWorkers == null ? 0L : meshWorkers.rectangleMaskCoverageAuditMatches();
        long rectangleDeterminismAudits = meshWorkers == null ? 0L : meshWorkers.rectangleDeterminismAudits();
        long rectangleDeterminismMatches = meshWorkers == null ? 0L : meshWorkers.rectangleDeterminismAuditMatches();
        long rectangleReferenceAudits = meshWorkers == null ? 0L : meshWorkers.rectangleReferenceAudits();
        long rectangleReferenceMatches = meshWorkers == null ? 0L : meshWorkers.rectangleReferenceAuditMatches();
        long rectangleWest = meshWorkers == null ? 0L : meshWorkers.rectangles(BinarySectionVisibility.WEST);
        long rectangleEast = meshWorkers == null ? 0L : meshWorkers.rectangles(BinarySectionVisibility.EAST);
        long rectangleDown = meshWorkers == null ? 0L : meshWorkers.rectangles(BinarySectionVisibility.DOWN);
        long rectangleUp = meshWorkers == null ? 0L : meshWorkers.rectangles(BinarySectionVisibility.UP);
        long rectangleNorth = meshWorkers == null ? 0L : meshWorkers.rectangles(BinarySectionVisibility.NORTH);
        long rectangleSouth = meshWorkers == null ? 0L : meshWorkers.rectangles(BinarySectionVisibility.SOUTH);
        long rectangleDirectionSum = rectangleWest + rectangleEast + rectangleDown + rectangleUp + rectangleNorth + rectangleSouth;
        long rectangleWestFaces = meshWorkers == null ? 0L : meshWorkers.rectangleCoveredFaces(BinarySectionVisibility.WEST);
        long rectangleEastFaces = meshWorkers == null ? 0L : meshWorkers.rectangleCoveredFaces(BinarySectionVisibility.EAST);
        long rectangleDownFaces = meshWorkers == null ? 0L : meshWorkers.rectangleCoveredFaces(BinarySectionVisibility.DOWN);
        long rectangleUpFaces = meshWorkers == null ? 0L : meshWorkers.rectangleCoveredFaces(BinarySectionVisibility.UP);
        long rectangleNorthFaces = meshWorkers == null ? 0L : meshWorkers.rectangleCoveredFaces(BinarySectionVisibility.NORTH);
        long rectangleSouthFaces = meshWorkers == null ? 0L : meshWorkers.rectangleCoveredFaces(BinarySectionVisibility.SOUTH);
        long rectangleCoveredDirectionSum = rectangleWestFaces + rectangleEastFaces + rectangleDownFaces
                + rectangleUpFaces + rectangleNorthFaces + rectangleSouthFaces;
        long rectangleFacesSaved = rectangleCoveredFaces - rectangleCount;
        long rectangleReductionPermille = rectangleCoveredFaces == 0L ? 0L
                : rectangleFacesSaved * 1000L / rectangleCoveredFaces;

        boolean greedyRectangleEvidenceReady = binaryVisibilityEvidenceReady
                && rectangleBuilds > 0L
                && rectangleBuilds >= workerCompletedJobs
                && rectangleCount > 0L
                && rectangleCoveredFaces == visibilityFaces
                && rectangleCoveredDirectionSum == rectangleCoveredFaces
                && rectangleDirectionSum == rectangleCount
                && rectangleCount > 0L && rectangleCount < rectangleCoveredFaces
                && rectangleRetainedBytes == rectangleCount * Integer.BYTES
                && rectangleScratchUses >= rectangleBuilds
                && rectangleMaskAudits == rectangleBuilds
                && rectangleMaskMatches == rectangleMaskAudits
                && rectangleDeterminismAudits > 0L
                && rectangleDeterminismMatches == rectangleDeterminismAudits
                && rectangleReferenceAudits > 0L
                && rectangleReferenceMatches == rectangleReferenceAudits
                && workersClean && stagingClean && arenaClean && resourcesClean;

        long renderKeyBuilds = meshWorkers == null ? 0L : meshWorkers.renderKeyBuilds();
        long renderKeyVisibleFaces = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyVisibleFaces();
        long renderKeyEligibleFaces = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyEligibleFaces();
        long renderKeyUnmappedFaces = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyUnmappedFaces();
        long renderKeyAmbiguousFaces = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyAmbiguousFaces();
        long renderKeyRecognizedCanonicalQuads = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyRecognizedCanonicalQuads();
        long renderKeyIgnoredNoncanonicalQuads = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyIgnoredNoncanonicalQuads();
        long renderKeySameAdjacencies = meshWorkers == null ? 0L : meshWorkers.totalRenderKeySameAdjacencies();
        long renderKeyDifferentAdjacencies = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyDifferentAdjacencies();
        long renderKeyIneligibleAdjacencies = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyIneligibleAdjacencies();
        long renderKeyRetainedBytes = meshWorkers == null ? 0L : meshWorkers.totalRenderKeyRetainedBytes();
        long renderKeyScratchUses = meshWorkers == null ? 0L : meshWorkers.renderKeyScratchBuildUses();
        long renderKeyDeterminismAudits = meshWorkers == null ? 0L : meshWorkers.renderKeyDeterminismAudits();
        long renderKeyDeterminismMatches = meshWorkers == null ? 0L : meshWorkers.renderKeyDeterminismAuditMatches();
        long renderKeyEligiblePermille = renderKeyVisibleFaces == 0L ? 0L
                : renderKeyEligibleFaces * 1000L / renderKeyVisibleFaces;

        boolean renderMergeKeyEvidenceReady = greedyRectangleEvidenceReady
                && renderKeyBuilds > 0L
                && renderKeyBuilds >= workerCompletedJobs
                && renderKeyVisibleFaces == visibilityFaces
                && renderKeyEligibleFaces + renderKeyUnmappedFaces + renderKeyAmbiguousFaces == renderKeyVisibleFaces
                && renderKeyEligibleFaces > 0L
                && renderKeyRecognizedCanonicalQuads > 0L
                && renderKeySameAdjacencies > 0L
                && renderKeyDifferentAdjacencies > 0L
                && renderKeyRetainedBytes == renderKeyBuilds * CanonicalFaceRenderKeys.RETAINED_BYTES
                && renderKeyScratchUses >= renderKeyBuilds
                && renderKeyDeterminismAudits > 0L
                && renderKeyDeterminismMatches == renderKeyDeterminismAudits
                && workersClean && stagingClean && arenaClean && resourcesClean;

        long mergeCandidateBuilds = meshWorkers == null ? 0L : meshWorkers.mergeCandidateBuilds();
        long mergeCandidateCount = meshWorkers == null ? 0L : meshWorkers.totalMergeCandidateCount();
        long mergeCandidateCoveredEligibleFaces = meshWorkers == null ? 0L : meshWorkers.totalMergeCandidateCoveredEligibleFaces();
        long mergeCandidatePassthroughCanonicalFaces = meshWorkers == null ? 0L : meshWorkers.totalMergeCandidatePassthroughCanonicalFaces();
        long mergeCandidateSingletons = meshWorkers == null ? 0L : meshWorkers.totalMergeCandidateSingletons();
        long mergeCandidateMultiFace = meshWorkers == null ? 0L : meshWorkers.totalMergeCandidateMultiFace();
        long mergeCandidateRetainedBytes = meshWorkers == null ? 0L : meshWorkers.totalMergeCandidateRetainedBytes();
        long mergeCandidateScratchUses = meshWorkers == null ? 0L : meshWorkers.mergeCandidateScratchBuildUses();
        long mergeCandidateCoverageAudits = meshWorkers == null ? 0L : meshWorkers.mergeCandidateCoverageAudits();
        long mergeCandidateCoverageMatches = meshWorkers == null ? 0L : meshWorkers.mergeCandidateCoverageAuditMatches();
        long mergeCandidateDeterminismAudits = meshWorkers == null ? 0L : meshWorkers.mergeCandidateDeterminismAudits();
        long mergeCandidateDeterminismMatches = meshWorkers == null ? 0L : meshWorkers.mergeCandidateDeterminismAuditMatches();
        long mergeCandidateWest = meshWorkers == null ? 0L : meshWorkers.mergeCandidates(BinarySectionVisibility.WEST);
        long mergeCandidateEast = meshWorkers == null ? 0L : meshWorkers.mergeCandidates(BinarySectionVisibility.EAST);
        long mergeCandidateDown = meshWorkers == null ? 0L : meshWorkers.mergeCandidates(BinarySectionVisibility.DOWN);
        long mergeCandidateUp = meshWorkers == null ? 0L : meshWorkers.mergeCandidates(BinarySectionVisibility.UP);
        long mergeCandidateNorth = meshWorkers == null ? 0L : meshWorkers.mergeCandidates(BinarySectionVisibility.NORTH);
        long mergeCandidateSouth = meshWorkers == null ? 0L : meshWorkers.mergeCandidates(BinarySectionVisibility.SOUTH);
        long mergeCandidateDirectionSum = mergeCandidateWest + mergeCandidateEast + mergeCandidateDown
                + mergeCandidateUp + mergeCandidateNorth + mergeCandidateSouth;
        long mergeCandidateWestFaces = meshWorkers == null ? 0L : meshWorkers.mergeCandidateCoveredFaces(BinarySectionVisibility.WEST);
        long mergeCandidateEastFaces = meshWorkers == null ? 0L : meshWorkers.mergeCandidateCoveredFaces(BinarySectionVisibility.EAST);
        long mergeCandidateDownFaces = meshWorkers == null ? 0L : meshWorkers.mergeCandidateCoveredFaces(BinarySectionVisibility.DOWN);
        long mergeCandidateUpFaces = meshWorkers == null ? 0L : meshWorkers.mergeCandidateCoveredFaces(BinarySectionVisibility.UP);
        long mergeCandidateNorthFaces = meshWorkers == null ? 0L : meshWorkers.mergeCandidateCoveredFaces(BinarySectionVisibility.NORTH);
        long mergeCandidateSouthFaces = meshWorkers == null ? 0L : meshWorkers.mergeCandidateCoveredFaces(BinarySectionVisibility.SOUTH);
        long mergeCandidateCoveredDirectionSum = mergeCandidateWestFaces + mergeCandidateEastFaces
                + mergeCandidateDownFaces + mergeCandidateUpFaces + mergeCandidateNorthFaces + mergeCandidateSouthFaces;
        long mergeCandidateFacesSaved = mergeCandidateCoveredEligibleFaces - mergeCandidateCount;
        long mergeCandidateReductionPermille = mergeCandidateCoveredEligibleFaces == 0L ? 0L
                : mergeCandidateFacesSaved * 1000L / mergeCandidateCoveredEligibleFaces;

        boolean renderMergeCandidateEvidenceReady = renderMergeKeyEvidenceReady
                && mergeCandidateBuilds > 0L
                && mergeCandidateBuilds >= workerCompletedJobs
                && mergeCandidateCoveredEligibleFaces == renderKeyEligibleFaces
                && mergeCandidatePassthroughCanonicalFaces == renderKeyVisibleFaces - renderKeyEligibleFaces
                && mergeCandidateCount > 0L
                && mergeCandidateCount <= mergeCandidateCoveredEligibleFaces
                && mergeCandidateSingletons + mergeCandidateMultiFace == mergeCandidateCount
                && mergeCandidateMultiFace > 0L
                && mergeCandidateFacesSaved > 0L
                && mergeCandidateDirectionSum == mergeCandidateCount
                && mergeCandidateCoveredDirectionSum == mergeCandidateCoveredEligibleFaces
                && mergeCandidateRetainedBytes == mergeCandidateCount * RenderMergeCandidates.BYTES_PER_CANDIDATE
                && mergeCandidateScratchUses >= mergeCandidateBuilds
                && mergeCandidateCoverageAudits == mergeCandidateBuilds
                && mergeCandidateCoverageMatches == mergeCandidateCoverageAudits
                && mergeCandidateDeterminismAudits > 0L
                && mergeCandidateDeterminismMatches == mergeCandidateDeterminismAudits
                && workersClean && stagingClean && arenaClean && resourcesClean;

        OrdinaryQuadEmissionSafetyEvidence.Snapshot emissionSafety =
                OrdinaryQuadEmissionSafetyEvidence.capture(
                        meshWorkers,
                        renderMergeCandidateEvidenceReady,
                        workerCompletedJobs,
                        mergeCandidateCount,
                        mergeCandidateSingletons,
                        mergeCandidateMultiFace,
                        renderKeyEligibleFaces,
                        workersClean,
                        stagingClean,
                        arenaClean,
                        resourcesClean);
        boolean ordinaryQuadEmissionSafetyEvidenceReady = emissionSafety.ready();

        RepeatAwareUvEvidence.Snapshot repeatAwareUv =
                RepeatAwareUvEvidence.capture(
                        meshWorkers,
                        ordinaryQuadEmissionSafetyEvidenceReady,
                        workerCompletedJobs,
                        mergeCandidateMultiFace,
                        renderKeyEligibleFaces,
                        workersClean,
                        stagingClean,
                        arenaClean,
                        resourcesClean);
        boolean repeatAwareUvEvidenceReady = repeatAwareUv.ready();

        RepeatAwareTransportEvidence.Snapshot repeatAwareTransport =
                RepeatAwareTransportEvidence.capture(
                        meshWorkers,
                        repeatAwareUvEvidenceReady,
                        workerCompletedJobs,
                        repeatAwareUv.multiFace(),
                        repeatAwareUv.representable(),
                        repeatAwareUv.fourVertexSafe(),
                        renderKeyEligibleFaces,
                        workersClean,
                        stagingClean,
                        arenaClean,
                        resourcesClean);
        boolean repeatAwareTransportEvidenceReady = repeatAwareTransport.ready();

        RepeatAwareGreedyEmissionEvidence.Snapshot repeatAwareGreedyEmission =
                RepeatAwareGreedyEmissionEvidence.capture(
                        repeatAwareTransportEvidenceReady,
                        productionWorkerIntegrationReady,
                        localSceneReady,
                        recordInstallCount,
                        sceneWorkerInstalls,
                        sceneWorkerCompleted,
                        comparisonDraws,
                        indirectCalls,
                        resourceEpochChecks,
                        repeatAwareTransport,
                        workersClean,
                        stagingClean,
                        arenaClean,
                        resourcesClean);
        boolean repeatAwareGreedyEmissionEvidenceReady = repeatAwareGreedyEmission.ready();

        boolean borderHaloCorrectnessEvidenceReady = repeatAwareGreedyEmissionEvidenceReady
                && borderSceneEvidenceReady
                && SectionLifecycleEvents.dirtyDependencyClassifierSelfTest()
                && borderProofRecords > 0L
                && borderProofRecords == recordInstallCount
                && borderProofDeterminismMatches == borderProofDeterminismAudits
                && borderVisibilityMatches == borderOutwardChecks
                && borderReferenceMatches == borderOutwardChecks
                && borderBakedQuads > 0L
                && borderLightColorSamples > 0L
                && sharedBorderPairAudits > 0L
                && sharedBorderMatches == sharedBorderComparisons
                && workersClean && stagingClean && arenaClean && resourcesClean;

        boolean tJunctionPolicyEvidenceReady = borderHaloCorrectnessEvidenceReady
                && tJunctionSceneEvidenceReady
                && tJunctionProofRecords > 0L
                && tJunctionProofRecords == recordInstallCount
                && tJunctionProofDeterminismAudits == tJunctionProofRecords
                && tJunctionProofDeterminismMatches == tJunctionProofDeterminismAudits
                && tJunctionEmittedCandidates > 0L
                && tJunctionEmittedEdges == tJunctionEmittedCandidates * 4L
                && tJunctionStrictInteriorLatticePoints > 0L
                && tJunctionStrictPoints > 0L
                && tJunctionBoundsMatches == tJunctionBoundsChecks
                && tJunctionPlaneMatches == tJunctionPlaneChecks
                && tJunctionIntegerLatticeMatches == tJunctionIntegerLatticeChecks
                && cameraRelativeTransformProofRecords > 0L
                && junctionBearingTransformProofRecords > 0L
                && cameraRelativeTransformFailures == 0L
                && unsafeStaleSceneInstalls == 0L
                && workersClean && stagingClean && arenaClean && resourcesClean;

        boolean differentialCorrectnessEvidenceReady = tJunctionPolicyEvidenceReady
                && differentialSceneEvidenceReady
                && differentialProofRecords > 0L
                && differentialProofRecords == recordInstallCount
                && differentialProofDeterminismAudits == differentialProofRecords
                && differentialProofDeterminismMatches == differentialProofDeterminismAudits
                && differentialReferenceFacesChecked > 0L
                && differentialSourceQuadsChecked > 0L
                && differentialPassthroughIdentitiesChecked > 0L
                && differentialMergedCandidatesChecked > 0L
                && differentialMergedExpandedFacesChecked > 0L
                && differentialMaterialMatches == differentialMaterialChecks
                && differentialDirectionMatches == differentialDirectionChecks
                && differentialGeometryMatches == differentialGeometryChecks
                && differentialUvMatches == differentialUvChecks
                && differentialColorMatches == differentialColorChecks
                && differentialLightMatches == differentialLightChecks
                && differentialMissing == 0L
                && differentialDuplicate == 0L
                && differentialOptimizedWithoutReference == 0L
                && differentialRealMismatches == 0L
                && differentialFixtureSelfTestPasses == differentialProofRecords
                && unsafeStaleSceneInstalls == 0L
                && droppedLifecycleEvents == 0L
                && workersClean && stagingClean && arenaClean && resourcesClean;

        boolean benchmarkLifecycleReady = benchmarkWindowArmed
                && benchmarkReadyDelta >= 3L
                && benchmarkDirtyDelta >= 2L
                && benchmarkReloadDelta >= 1L
                && benchmarkRecenterDelta >= 1L;
        boolean benchmarkSnapshotEvidenceReady = benchmarkSnapshotReady(benchmarkSnapshot)
                && benchmarkSnapshot != null
                && (benchmarkSnapshot.maxRunningJobs() >= 2 || benchmarkSnapshot.maxQueuedJobs() > 0);
        meshingBenchmarkEvidenceReady = differentialCorrectnessEvidenceReady
                && benchmarkLifecycleReady
                && benchmarkSnapshotEvidenceReady
                && benchmarkWorkerQueueRejectionDelta == 0L
                && workersClean && stagingClean && arenaClean && resourcesClean;

        boolean phase2ChunkLifecycleEvidenceReady = phase3GateReady
                && fixedAnchorChunkUnloadEvents > 0L
                && fixedAnchorChunkLoadEvents > 0L
                && fixedAnchorReturnSceneReady
                && droppedLifecycleEvents == 0L
                && unsafeStaleSceneInstalls == 0L
                && workersClean && stagingClean && arenaClean && resourcesClean;

        StringBuilder out = new StringBuilder(28672);
        out.append("Phase 3 dev15 P3.8 frame coordinator closed after ").append(frameIndex).append(" frame(s): ")
                .append("phase3GateReady=").append(phase3GateReady)
                .append(", schedulerEvidenceReady=").append(schedulerEvidenceReady)
                .append(", binaryVisibilityEvidenceReady=").append(binaryVisibilityEvidenceReady)
                .append(", greedyRectangleEvidenceReady=").append(greedyRectangleEvidenceReady)
                .append(", renderMergeKeyEvidenceReady=").append(renderMergeKeyEvidenceReady)
                .append(", renderMergeCandidateEvidenceReady=").append(renderMergeCandidateEvidenceReady)
                .append(", ordinaryQuadEmissionSafetyEvidenceReady=").append(ordinaryQuadEmissionSafetyEvidenceReady)
                .append(", repeatAwareUvEvidenceReady=").append(repeatAwareUvEvidenceReady)
                .append(", repeatAwareTransportEvidenceReady=").append(repeatAwareTransportEvidenceReady)
                .append(", repeatAwareGreedyEmissionEvidenceReady=").append(repeatAwareGreedyEmissionEvidenceReady)
                .append(", borderHaloCorrectnessEvidenceReady=").append(borderHaloCorrectnessEvidenceReady)
                .append(", tJunctionPolicyEvidenceReady=").append(tJunctionPolicyEvidenceReady)
                .append(", differentialCorrectnessEvidenceReady=").append(differentialCorrectnessEvidenceReady)
                .append(", meshingBenchmarkEvidenceReady=").append(meshingBenchmarkEvidenceReady)
                .append(", phase2ChunkLifecycleEvidenceReady=").append(phase2ChunkLifecycleEvidenceReady)
                .append(", fixedAnchorReturnSceneReady=").append(fixedAnchorReturnSceneReady)
                .append(", productionWorkerIntegrationReady=").append(productionWorkerIntegrationReady)
                .append(", hardFailure=").append(hardFailure)
                .append(", productionSceneInstallStillSynchronous=false, productionWorkerSceneIntegration=true")
                .append(", renderThreadCaptureOwnership=true, renderThreadGpuOwnership=true, workerWorldReadsAfterCapture=0")
                .append(", binaryVisibilitySidecarIntegrated=true, greedyRectangleSidecarIntegrated=true, renderMergeKeySidecarIntegrated=true, renderMergeCandidateSidecarIntegrated=true, ordinaryQuadEmissionSafetySidecarIntegrated=true, repeatAwareUvDescriptorSidecarIntegrated=true, repeatAwareTransportSidecarIntegrated=true, repeatAwareGreedyMeshIntegrated=true, borderHaloProofIntegrated=true, tJunctionTopologyProofIntegrated=true, differentialCorrectnessProofIntegrated=true")
                .append(", repeatAwareGreedyGpuEmission=true, borderHaloGeometryChanged=false, tJunctionGeometryChanged=false, tJunctionShaderChanged=false, tJunctionPipelineChanged=false, differentialGeometryChanged=false, differentialShaderChanged=false, differentialPipelineChanged=false, renderCorrectMergeKeyComplete=false")
                .append(", synchronousSceneMeshBuilds=").append(synchronousSceneMeshBuilds)
                .append(", workerCount=").append(meshWorkers == null ? 0 : meshWorkers.workerCount())
                .append(", workerQueueCapacity=").append(meshWorkers == null ? 0 : meshWorkers.queueCapacity())
                .append(", workerSubmittedJobs=").append(meshWorkers == null ? 0L : meshWorkers.submittedJobs())
                .append(", workerStartedJobs=").append(meshWorkers == null ? 0L : meshWorkers.startedJobs())
                .append(", workerCompletedJobs=").append(workerCompletedJobs)
                .append(", workerCancelledJobs=").append(meshWorkers == null ? 0L : meshWorkers.cancelledJobs())
                .append(", workerCancellationRequests=").append(meshWorkers == null ? 0L : meshWorkers.cancellationRequests())
                .append(", workerStolenJobs=").append(meshWorkers == null ? 0L : meshWorkers.stolenJobs())
                .append(", workerQueueFullRejections=").append(meshWorkers == null ? 0L : meshWorkers.queueFullRejections())
                .append(", workerFailedJobs=").append(meshWorkers == null ? 0L : meshWorkers.failedJobs())
                .append(", workerShutdownJoinFailures=").append(meshWorkers == null ? 0L : meshWorkers.shutdownJoinFailures())
                .append(", workerMaxQueueDepth=").append(meshWorkers == null ? 0L : meshWorkers.maxObservedQueueDepth())
                .append(", workerTotalQueueWaitNs=").append(meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs())
                .append(", workerMaxQueueWaitNs=").append(meshWorkers == null ? 0L : meshWorkers.maxQueueWaitNs())
                .append(", workerTotalExecutionNs=").append(meshWorkers == null ? 0L : meshWorkers.totalExecutionNs())
                .append(", workerMaxExecutionNs=").append(meshWorkers == null ? 0L : meshWorkers.maxExecutionNs())
                .append(", bakedMeshTotalBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.totalBakedMeshBuildNs())
                .append(", bakedMeshMaxBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.maxBakedMeshBuildNs())
                .append(", tJunctionProofPairTotalNs=").append(meshWorkers == null ? 0L : meshWorkers.totalTJunctionProofPairNs())
                .append(", tJunctionProofPairMaxNs=").append(meshWorkers == null ? 0L : meshWorkers.maxTJunctionProofPairNs())
                .append(", differentialProofPairTotalNs=").append(meshWorkers == null ? 0L : meshWorkers.totalDifferentialProofPairNs())
                .append(", differentialProofPairMaxNs=").append(meshWorkers == null ? 0L : meshWorkers.maxDifferentialProofPairNs())
                .append(", benchmarkWindowArmed=").append(benchmarkWindowArmed)
                .append(", benchmarkCollectorSelfTest=").append(benchmarkSnapshot != null && benchmarkSnapshot.collectorSelfTestPassed())
                .append(", benchmarkDurationNs=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.durationNs())
                .append(", benchmarkCompletedSamples=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.completedSamples())
                .append(", benchmarkRetainedSamples=").append(benchmarkSnapshot == null ? 0 : benchmarkSnapshot.execution().retained())
                .append(", benchmarkOverflowSamples=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.execution().overflow())
                .append(", benchmarkQueueP50Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.queueWait().p50Ns())
                .append(", benchmarkQueueP95Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.queueWait().p95Ns())
                .append(", benchmarkQueueP99Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.queueWait().p99Ns())
                .append(", benchmarkQueueMaxNs=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.queueWait().maxNs())
                .append(", benchmarkExecutionMeanNs=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.execution().meanNs())
                .append(", benchmarkExecutionP50Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.execution().p50Ns())
                .append(", benchmarkExecutionP95Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.execution().p95Ns())
                .append(", benchmarkExecutionP99Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.execution().p99Ns())
                .append(", benchmarkExecutionMaxNs=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.execution().maxNs())
                .append(", benchmarkHighCompleted=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.highCompleted())
                .append(", benchmarkNormalCompleted=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.normalCompleted())
                .append(", benchmarkLowCompleted=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.lowCompleted())
                .append(", benchmarkHighQueueP99Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.highQueueWait().p99Ns())
                .append(", benchmarkNormalQueueP99Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.normalQueueWait().p99Ns())
                .append(", benchmarkLowQueueP99Ns=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.lowQueueWait().p99Ns())
                .append(", benchmarkSourceBakedQuads=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.sourceBakedQuads())
                .append(", benchmarkReferenceFaces=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.independentReferenceFaces())
                .append(", benchmarkTopologyRectangles=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.topologyRectangles())
                .append(", benchmarkTopologyCoveredFaces=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.topologyCoveredFaces())
                .append(", benchmarkMergeCandidates=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.renderMergeCandidates())
                .append(", benchmarkPassthroughIdentities=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.passthroughIdentities())
                .append(", benchmarkMergedIdentities=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.mergedIdentities())
                .append(", benchmarkMergedCoveredFaces=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.mergedCoveredSourceFaces())
                .append(", benchmarkFacesSaved=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.facesSaved())
                .append(", benchmarkReductionPermille=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.reductionPermille())
                .append(", benchmarkOutputQuads=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.outputQuads())
                .append(", benchmarkOutputVertexBytes=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.outputVertexBytes())
                .append(", benchmarkOutputIndexBytes=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.outputIndexBytes())
                .append(", benchmarkMaxQueuedJobs=").append(benchmarkSnapshot == null ? 0 : benchmarkSnapshot.maxQueuedJobs())
                .append(", benchmarkMaxRunningJobs=").append(benchmarkSnapshot == null ? 0 : benchmarkSnapshot.maxRunningJobs())
                .append(", benchmarkStolenCompleted=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.stolenCompleted())
                .append(", benchmarkWorkerBusyPermille=").append(benchmarkSnapshot == null || meshWorkers == null ? 0L : benchmarkSnapshot.workerBusyPermille(meshWorkers.workerCount()))
                .append(", benchmarkGcCollectionDelta=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.gcCollectionDelta())
                .append(", benchmarkGcTimeDeltaMs=").append(benchmarkSnapshot == null ? 0L : benchmarkSnapshot.gcTimeDeltaMs())
                .append(", benchmarkReadyDelta=").append(benchmarkReadyDelta)
                .append(", benchmarkCoreDirtyDelta=").append(benchmarkDirtyDelta)
                .append(", benchmarkResourceReloadDelta=").append(benchmarkReloadDelta)
                .append(", benchmarkRecenterDelta=").append(benchmarkRecenterDelta)
                .append(", benchmarkWorkerCompletedDelta=").append(benchmarkWorkerCompletedDelta)
                .append(", benchmarkWorkerCancelledDelta=").append(benchmarkWorkerCancelledDelta)
                .append(", benchmarkWorkerStolenDelta=").append(benchmarkWorkerStolenDelta)
                .append(", benchmarkWorkerQueueRejectionDelta=").append(benchmarkWorkerQueueRejectionDelta)
                .append(", benchmarkSceneStaleDiscardDelta=").append(benchmarkSceneStaleDiscardDelta)
                .append(", benchmarkAllocationBytes=not-portably-measured")
                .append(", javaVersion=").append(System.getProperty("java.version"))
                .append(", os=").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(", logicalProcessors=").append(Runtime.getRuntime().availableProcessors())
                .append(", minecraftVersion=26.2, fabricLoaderVersion=0.19.3, obsidianVersion=0.3.0-phase3-dev15")
                .append(", renderDistance=not-captured, simulationDistance=not-captured")
                .append(", workerHighSubmitted=").append(workerHighSubmitted)
                .append(", workerNormalSubmitted=").append(workerNormalSubmitted)
                .append(", workerLowSubmitted=").append(workerLowSubmitted)
                .append(", workerHighCompleted=").append(meshWorkers == null ? 0L : meshWorkers.completedJobs(SectionMeshWorkerPool.PRIORITY_HIGH))
                .append(", workerNormalCompleted=").append(meshWorkers == null ? 0L : meshWorkers.completedJobs(SectionMeshWorkerPool.PRIORITY_NORMAL))
                .append(", workerLowCompleted=").append(meshWorkers == null ? 0L : meshWorkers.completedJobs(SectionMeshWorkerPool.PRIORITY_LOW))
                .append(", workerHighQueueWaitNs=").append(meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs(SectionMeshWorkerPool.PRIORITY_HIGH))
                .append(", workerNormalQueueWaitNs=").append(meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs(SectionMeshWorkerPool.PRIORITY_NORMAL))
                .append(", workerLowQueueWaitNs=").append(meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs(SectionMeshWorkerPool.PRIORITY_LOW))
                .append(", workerOutputQuads=").append(workerOutputQuads)
                .append(", workerOutputVertexBytes=").append(workerOutputVertexBytes)
                .append(", workerOutputIndexBytes=").append(workerOutputIndexBytes)
                .append(", workerMaxOutputBytes=").append(meshWorkers == null ? 0L : meshWorkers.maxOutputBytes())
                .append(", workerScratchBuildUses=").append(workerScratchBuildUses)
                .append(", workerMaxScratchQuads=").append(meshWorkers == null ? 0L : meshWorkers.maxScratchQuads())
                .append(", workerDeterminismAudits=").append(workerDeterminismAudits)
                .append(", workerDeterminismAuditMatches=").append(workerDeterminismAuditMatches)
                .append(", visibilityBuilds=").append(visibilityBuilds)
                .append(", visibilityTotalFaces=").append(visibilityFaces)
                .append(", visibilityWestFaces=").append(visibilityWestFaces)
                .append(", visibilityEastFaces=").append(visibilityEastFaces)
                .append(", visibilityDownFaces=").append(visibilityDownFaces)
                .append(", visibilityUpFaces=").append(visibilityUpFaces)
                .append(", visibilityNorthFaces=").append(visibilityNorthFaces)
                .append(", visibilitySouthFaces=").append(visibilitySouthFaces)
                .append(", visibilityRetainedBytes=").append(visibilityRetainedBytes)
                .append(", visibilityRetainedBytesPerBuild=").append(BinarySectionVisibility.RETAINED_BYTES)
                .append(", visibilityTotalBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.totalVisibilityBuildNs())
                .append(", visibilityMaxBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.maxVisibilityBuildNs())
                .append(", visibilityMaxFaces=").append(meshWorkers == null ? 0L : meshWorkers.maxVisibilityFaces())
                .append(", visibilityScratchBuildUses=").append(visibilityScratchUses)
                .append(", visibilityMaxScratchRows=").append(meshWorkers == null ? 0L : meshWorkers.maxVisibilityScratchRows())
                .append(", visibilityDeterminismAudits=").append(visibilityDeterminismAudits)
                .append(", visibilityDeterminismAuditMatches=").append(visibilityDeterminismMatches)
                .append(", visibilityReferenceAudits=").append(visibilityReferenceAudits)
                .append(", visibilityReferenceAuditMatches=").append(visibilityReferenceMatches)
                .append(", rectangleBuilds=").append(rectangleBuilds)
                .append(", rectangleCount=").append(rectangleCount)
                .append(", rectangleCoveredFaces=").append(rectangleCoveredFaces)
                .append(", rectangleFacesSaved=").append(rectangleFacesSaved)
                .append(", rectangleReductionPermille=").append(rectangleReductionPermille)
                .append(", rectangleWest=").append(rectangleWest)
                .append(", rectangleEast=").append(rectangleEast)
                .append(", rectangleDown=").append(rectangleDown)
                .append(", rectangleUp=").append(rectangleUp)
                .append(", rectangleNorth=").append(rectangleNorth)
                .append(", rectangleSouth=").append(rectangleSouth)
                .append(", rectangleWestFaces=").append(rectangleWestFaces)
                .append(", rectangleEastFaces=").append(rectangleEastFaces)
                .append(", rectangleDownFaces=").append(rectangleDownFaces)
                .append(", rectangleUpFaces=").append(rectangleUpFaces)
                .append(", rectangleNorthFaces=").append(rectangleNorthFaces)
                .append(", rectangleSouthFaces=").append(rectangleSouthFaces)
                .append(", rectangleRetainedBytes=").append(rectangleRetainedBytes)
                .append(", rectangleBytesPerRecord=").append(Integer.BYTES)
                .append(", rectangleTotalBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.totalRectangleBuildNs())
                .append(", rectangleMaxBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.maxRectangleBuildNs())
                .append(", rectangleMaxCount=").append(meshWorkers == null ? 0L : meshWorkers.maxRectangleCount())
                .append(", rectangleScratchBuildUses=").append(rectangleScratchUses)
                .append(", rectangleMaxScratchRectangles=").append(meshWorkers == null ? 0L : meshWorkers.maxRectangleScratchRectangles())
                .append(", rectangleMaskCoverageAudits=").append(rectangleMaskAudits)
                .append(", rectangleMaskCoverageAuditMatches=").append(rectangleMaskMatches)
                .append(", rectangleDeterminismAudits=").append(rectangleDeterminismAudits)
                .append(", rectangleDeterminismAuditMatches=").append(rectangleDeterminismMatches)
                .append(", rectangleReferenceAudits=").append(rectangleReferenceAudits)
                .append(", rectangleReferenceAuditMatches=").append(rectangleReferenceMatches)
                .append(", renderKeyBuilds=").append(renderKeyBuilds)
                .append(", renderKeyVisibleFaces=").append(renderKeyVisibleFaces)
                .append(", renderKeyEligibleFaces=").append(renderKeyEligibleFaces)
                .append(", renderKeyUnmappedFaces=").append(renderKeyUnmappedFaces)
                .append(", renderKeyAmbiguousFaces=").append(renderKeyAmbiguousFaces)
                .append(", renderKeyEligiblePermille=").append(renderKeyEligiblePermille)
                .append(", renderKeyRecognizedCanonicalQuads=").append(renderKeyRecognizedCanonicalQuads)
                .append(", renderKeyIgnoredNoncanonicalQuads=").append(renderKeyIgnoredNoncanonicalQuads)
                .append(", renderKeySameAdjacencies=").append(renderKeySameAdjacencies)
                .append(", renderKeyDifferentAdjacencies=").append(renderKeyDifferentAdjacencies)
                .append(", renderKeyIneligibleAdjacencies=").append(renderKeyIneligibleAdjacencies)
                .append(", renderKeyRetainedBytes=").append(renderKeyRetainedBytes)
                .append(", renderKeyRetainedBytesPerBuild=").append(CanonicalFaceRenderKeys.RETAINED_BYTES)
                .append(", renderKeyTotalBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.totalRenderKeyBuildNs())
                .append(", renderKeyMaxBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.maxRenderKeyBuildNs())
                .append(", renderKeyMaxEligibleFaces=").append(meshWorkers == null ? 0L : meshWorkers.maxRenderKeyEligibleFaces())
                .append(", renderKeyScratchBuildUses=").append(renderKeyScratchUses)
                .append(", renderKeyMaxScratchEligibleFaces=").append(meshWorkers == null ? 0L : meshWorkers.maxRenderKeyScratchEligibleFaces())
                .append(", renderKeyDeterminismAudits=").append(renderKeyDeterminismAudits)
                .append(", renderKeyDeterminismAuditMatches=").append(renderKeyDeterminismMatches)
                .append(", mergeCandidateBuilds=").append(mergeCandidateBuilds)
                .append(", mergeCandidateCount=").append(mergeCandidateCount)
                .append(", mergeCandidateCoveredEligibleFaces=").append(mergeCandidateCoveredEligibleFaces)
                .append(", mergeCandidatePassthroughCanonicalFaces=").append(mergeCandidatePassthroughCanonicalFaces)
                .append(", mergeCandidateSingletons=").append(mergeCandidateSingletons)
                .append(", mergeCandidateMultiFace=").append(mergeCandidateMultiFace)
                .append(", mergeCandidateFacesSaved=").append(mergeCandidateFacesSaved)
                .append(", mergeCandidateReductionPermille=").append(mergeCandidateReductionPermille)
                .append(", mergeCandidateWest=").append(mergeCandidateWest)
                .append(", mergeCandidateEast=").append(mergeCandidateEast)
                .append(", mergeCandidateDown=").append(mergeCandidateDown)
                .append(", mergeCandidateUp=").append(mergeCandidateUp)
                .append(", mergeCandidateNorth=").append(mergeCandidateNorth)
                .append(", mergeCandidateSouth=").append(mergeCandidateSouth)
                .append(", mergeCandidateWestFaces=").append(mergeCandidateWestFaces)
                .append(", mergeCandidateEastFaces=").append(mergeCandidateEastFaces)
                .append(", mergeCandidateDownFaces=").append(mergeCandidateDownFaces)
                .append(", mergeCandidateUpFaces=").append(mergeCandidateUpFaces)
                .append(", mergeCandidateNorthFaces=").append(mergeCandidateNorthFaces)
                .append(", mergeCandidateSouthFaces=").append(mergeCandidateSouthFaces)
                .append(", mergeCandidateRetainedBytes=").append(mergeCandidateRetainedBytes)
                .append(", mergeCandidateBytesPerRecord=").append(RenderMergeCandidates.BYTES_PER_CANDIDATE)
                .append(", mergeCandidateTotalBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.totalMergeCandidateBuildNs())
                .append(", mergeCandidateMaxBuildNs=").append(meshWorkers == null ? 0L : meshWorkers.maxMergeCandidateBuildNs())
                .append(", mergeCandidateMaxCount=").append(meshWorkers == null ? 0L : meshWorkers.maxMergeCandidateCount())
                .append(", mergeCandidateScratchBuildUses=").append(mergeCandidateScratchUses)
                .append(", mergeCandidateMaxScratchCandidates=").append(meshWorkers == null ? 0L : meshWorkers.maxMergeCandidateScratchCandidates())
                .append(", mergeCandidateCoverageAudits=").append(mergeCandidateCoverageAudits)
                .append(", mergeCandidateCoverageAuditMatches=").append(mergeCandidateCoverageMatches)
                .append(", mergeCandidateDeterminismAudits=").append(mergeCandidateDeterminismAudits)
                .append(", mergeCandidateDeterminismAuditMatches=").append(mergeCandidateDeterminismMatches);
        emissionSafety.appendTo(out);
        repeatAwareUv.appendTo(out);
        repeatAwareTransport.appendTo(out);
        repeatAwareGreedyEmission.appendTo(out);
        out.append(", borderSceneEvidenceReady=").append(borderSceneEvidenceReady)
                .append(", tJunctionSceneEvidenceReady=").append(tJunctionSceneEvidenceReady)
                .append(", tJunctionProofRecords=").append(tJunctionProofRecords)
                .append(", tJunctionProofDeterminismAudits=").append(tJunctionProofDeterminismAudits)
                .append(", tJunctionProofDeterminismMatches=").append(tJunctionProofDeterminismMatches)
                .append(", tJunctionEmittedCandidates=").append(tJunctionEmittedCandidates)
                .append(", tJunctionEmittedEdges=").append(tJunctionEmittedEdges)
                .append(", tJunctionStrictInteriorLatticePoints=").append(tJunctionStrictInteriorLatticePoints)
                .append(", tJunctionStrictPoints=").append(tJunctionStrictPoints)
                .append(", tJunctionBoundsChecks=").append(tJunctionBoundsChecks)
                .append(", tJunctionBoundsMatches=").append(tJunctionBoundsMatches)
                .append(", tJunctionPlaneChecks=").append(tJunctionPlaneChecks)
                .append(", tJunctionPlaneMatches=").append(tJunctionPlaneMatches)
                .append(", tJunctionIntegerLatticeChecks=").append(tJunctionIntegerLatticeChecks)
                .append(", tJunctionIntegerLatticeMatches=").append(tJunctionIntegerLatticeMatches)
                .append(", cameraRelativeTransformProofRecords=").append(cameraRelativeTransformProofRecords)
                .append(", junctionBearingTransformProofRecords=").append(junctionBearingTransformProofRecords)
                .append(", cameraRelativeTransformFailures=").append(cameraRelativeTransformFailures)
                .append(", differentialProofRecords=").append(differentialProofRecords)
                .append(", differentialProofDeterminism=").append(differentialProofDeterminismMatches).append('/').append(differentialProofDeterminismAudits)
                .append(", differentialReferenceFaces=").append(differentialReferenceFacesChecked)
                .append(", differentialReferenceMapped=").append(differentialReferenceMappedFaces)
                .append(", differentialReferenceUnmapped=").append(differentialReferenceUnmappedFaces)
                .append(", differentialReferenceAmbiguous=").append(differentialReferenceAmbiguousFaces)
                .append(", differentialSourceQuads=").append(differentialSourceQuadsChecked)
                .append(", differentialPassthroughIdentities=").append(differentialPassthroughIdentitiesChecked)
                .append(", differentialMergedCandidates=").append(differentialMergedCandidatesChecked)
                .append(", differentialMergedExpandedFaces=").append(differentialMergedExpandedFacesChecked)
                .append(", differentialMaterial=").append(differentialMaterialMatches).append('/').append(differentialMaterialChecks)
                .append(", differentialDirection=").append(differentialDirectionMatches).append('/').append(differentialDirectionChecks)
                .append(", differentialGeometry=").append(differentialGeometryMatches).append('/').append(differentialGeometryChecks)
                .append(", differentialUv=").append(differentialUvMatches).append('/').append(differentialUvChecks)
                .append(", differentialColor=").append(differentialColorMatches).append('/').append(differentialColorChecks)
                .append(", differentialLight=").append(differentialLightMatches).append('/').append(differentialLightChecks)
                .append(", differentialMissing=").append(differentialMissing)
                .append(", differentialDuplicate=").append(differentialDuplicate)
                .append(", differentialOptimizedWithoutReference=").append(differentialOptimizedWithoutReference)
                .append(", differentialRealMismatches=").append(differentialRealMismatches)
                .append(", differentialFixtureSelfTests=").append(differentialFixtureSelfTestPasses).append('/').append(differentialProofRecords)
                .append(", tJunctionTargetedVisualVerdictRequired=true")
                .append(", borderLifecycleClassifierSelfTest=").append(SectionLifecycleEvents.dirtyDependencyClassifierSelfTest())
                .append(", borderProofRecords=").append(borderProofRecords)
                .append(", borderProofDeterminismAudits=").append(borderProofDeterminismAudits)
                .append(", borderProofDeterminismMatches=").append(borderProofDeterminismMatches)
                .append(", borderOutwardChecks=").append(borderOutwardChecks)
                .append(", borderVisibilityMatches=").append(borderVisibilityMatches)
                .append(", borderReferenceMatches=").append(borderReferenceMatches)
                .append(", borderExpectedVisibleFaces=").append(borderExpectedVisibleFaces)
                .append(", borderUnsupportedBlockedFaces=").append(borderUnsupportedBlockedFaces)
                .append(", borderBakedQuads=").append(borderBakedQuads)
                .append(", borderLightColorSamples=").append(borderLightColorSamples)
                .append(", sharedBorderPairAudits=").append(sharedBorderPairAudits)
                .append(", sharedBorderComparisons=").append(sharedBorderComparisons)
                .append(", sharedBorderMatches=").append(sharedBorderMatches)
                .append(", sceneWorkerSubmitted=").append(sceneWorkerSubmitted)
                .append(", sceneWorkerCompleted=").append(sceneWorkerCompleted)
                .append(", sceneWorkerCancelled=").append(sceneWorkerCancelled)
                .append(", sceneWorkerCancellationRequests=").append(sceneWorkerCancellationRequests)
                .append(", sceneWorkerStaleDiscards=").append(sceneWorkerStaleDiscards)
                .append(", sceneWorkerInstalls=").append(sceneWorkerInstalls)
                .append(", sceneWorkerQueueRejections=").append(sceneWorkerQueueRejections)
                .append(", preinstallInvalidations=").append(preinstallInvalidations)
                .append(", maxSimultaneousSceneJobs=").append(maxSimultaneousSceneJobs)
                .append(", maxAdmissionBurst=").append(maxAdmissionBurst)
                .append(", schedulerAdmissionDeferrals=").append(schedulerAdmissionDeferrals)
                .append(", admittedHighPriority=").append(admittedHighPriority)
                .append(", admittedNormalPriority=").append(admittedNormalPriority)
                .append(", admittedLowPriority=").append(admittedLowPriority)
                .append(", sceneInstallAdmissionDeferrals=").append(sceneInstallAdmissionDeferrals)
                .append(", localSceneReady=").append(localSceneReady)
                .append(", center=").append(center)
                .append(", sceneGeneration=").append(sceneGeneration)
                .append(", sceneReadyTransitions=").append(sceneReadyTransitions)
                .append(", sceneRebuilds=").append(sceneRebuilds)
                .append(", recordInstalls=").append(recordInstallCount)
                .append(", maxLiveRecords=").append(maxLiveRecords)
                .append(", maxAdjacentPairs=").append(maxAdjacentPairs)
                .append(", cameraRecenterEvents=").append(cameraRecenterEvents)
                .append(", invalidationBatches=").append(invalidationBatches)
                .append(", coalescedEvents=").append(coalescedEvents)
                .append(", dirtyEvents=").append(dirtyEvents)
                .append(", renderedCoreDirtyEvents=").append(renderedCoreDirtyEvents)
                .append(", haloOnlyDirtyEvents=").append(haloOnlyDirtyEvents)
                .append(", horizontalHaloDirtyEvents=").append(horizontalHaloDirtyEvents)
                .append(", verticalHaloDirtyEvents=").append(verticalHaloDirtyEvents)
                .append(", playerDirtyEvents=").append(playerDirtyEvents)
                .append(", chunkLoadEvents=").append(chunkLoadEvents)
                .append(", chunkUnloadEvents=").append(chunkUnloadEvents)
                .append(", fixedAnchorChunkLoadEvents=").append(fixedAnchorChunkLoadEvents)
                .append(", fixedAnchorChunkUnloadEvents=").append(fixedAnchorChunkUnloadEvents)
                .append(", worldChangeEvents=").append(worldChangeEvents)
                .append(", resourceReloadEvents=").append(resourceReloadEvents)
                .append(", droppedLifecycleEvents=").append(droppedLifecycleEvents)
                .append(", observedReasons=").append(SectionLifecycleEvents.describeReasons(observedReasonMask))
                .append(", eligibilityScans=").append(eligibilityScans)
                .append(", eligibilitySkips=").append(eligibilitySkips)
                .append(", unsafeStaleSceneInstalls=").append(unsafeStaleSceneInstalls)
                .append(", maxSceneQuads=").append(maxSceneQuads)
                .append(", maxSceneVertexBytes=").append(maxSceneVertexBytes)
                .append(", maxSceneIndexBytes=").append(maxSceneIndexBytes)
                .append(", usefulSubmissions=").append(usefulSubmissions)
                .append(", comparisonDraws=").append(comparisonDraws)
                .append(", indirectCalls=").append(indirectCalls)
                .append(", resourceEpochChecks=").append(resourceEpochChecks)
                .append(", retirementBackpressureEvents=").append(retirementBackpressureEvents)
                .append(", retirementRegistrationFailures=").append(retirementRegistrationFailures)
                .append(", workersClean=").append(workersClean)
                .append(", stagingClean=").append(stagingClean)
                .append(", arenaClean=").append(arenaClean)
                .append(", resourcesClean=").append(resourcesClean)
                .append(", stagingSubmittedBytes=").append(stagingUploads == null ? 0L : stagingUploads.submittedBytes())
                .append(", stagingReclaimedBytes=").append(stagingUploads == null ? 0L : stagingUploads.reclaimedBytes())
                .append(", pendingUploadBatches=").append(stagingUploads == null ? 0 : stagingUploads.pendingBatches())
                .append(", stagingAbandoned=").append(stagingUploads != null && stagingUploads.abandonedForDeviceShutdown())
                .append(", arenaUsedBytes=").append(deviceArena == null ? 0L : deviceArena.usedBytes())
                .append(", arenaHighWaterBytes=").append(deviceArena == null ? 0L : deviceArena.highWaterBytes())
                .append(", arenaAllocations=").append(deviceArena == null ? 0L : deviceArena.successfulAllocations())
                .append(", arenaAllocationFailures=").append(deviceArena == null ? 0L : deviceArena.allocationFailures())
                .append(", arenaRetired=").append(deviceArena == null ? 0L : deviceArena.retiredAllocations())
                .append(", arenaReclaimed=").append(deviceArena == null ? 0L : deviceArena.reclaimedAllocations())
                .append(", arenaStaleHandleRejections=").append(deviceArena == null ? 0L : deviceArena.staleHandleRejections())
                .append(", arenaFragmentationPermille=").append(deviceArena == null ? 0 : deviceArena.fragmentationPermille())
                .append(", pendingArenaRetirementBatches=").append(deviceArena == null ? 0 : deviceArena.pendingRetirementBatches())
                .append(", arenaAbandoned=").append(deviceArena != null && deviceArena.abandonedForDeviceShutdown())
                .append(", retiredResources=").append(deferredReleases.retiredCount())
                .append(", releasedResources=").append(deferredReleases.releasedCount())
                .append(", pendingRetirements=").append(deferredReleases.pendingCount()).append('.');
        LOG.log(System.Logger.Level.INFO, out.toString());
    }
}
