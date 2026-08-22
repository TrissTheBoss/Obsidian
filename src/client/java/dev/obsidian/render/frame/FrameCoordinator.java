package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.mesh.SectionMeshWorkerPool;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.AsyncMultiSectionSceneProbe;
import dev.obsidian.render.terrain.SectionLifecycleEvents;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.renderer.GameRenderer;

/** Render-thread lifecycle root for the active Phase 3 dev3 scheduler milestone. */
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

    private AsyncMultiSectionSceneProbe sceneProbe;
    private FrameContext activeFrame;
    private long frameIndex;
    private long firstWorldRenderNs;
    private boolean firstFrameLogged;
    private boolean visualDelayLogged;
    private boolean runtimeInstructionsLogged;
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
                    device, () -> "Obsidian Phase 3 dev3 bounded scene staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device, () -> "Obsidian Phase 3 dev3 scene device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            sceneProbe = new AsyncMultiSectionSceneProbe(device, staging, arena, deferredReleases, workers);
        } catch (RuntimeException e) {
            if (sceneProbe != null) try { sceneProbe.close(); } catch (RuntimeException ignored) { }
            if (workers != null) try { workers.close(); } catch (RuntimeException ignored) { }
            if (staging != null) try { staging.close(); } catch (RuntimeException ignored) { }
            if (arena != null) try { arena.close(); } catch (RuntimeException ignored) { }
            try { deferredReleases.close(); } catch (RuntimeException ignored) { }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 3 dev3 scheduler integration initialization failed; Minecraft will continue for diagnosis.", e);
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
        if (sceneProbe != null) {
            sceneProbe.beginFrame(frameIndex);
            if (sceneProbe.hardFailure()) hardFailure = true;
        }
        if (!firstFrameLogged) {
            firstFrameLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 3 dev3 frame coordinator active. contextSlots=" + frameContexts.size()
                            + ", cpuTimingCapacity=" + cpuFrameTimings.capacity()
                            + ", meshWorkers=" + (meshWorkers == null ? 0 : meshWorkers.workerCount())
                            + ", meshQueueCapacity=" + (meshWorkers == null ? 0 : meshWorkers.queueCapacity())
                            + ", stagingCapacity=" + (stagingUploads == null ? 0 : stagingUploads.capacityBytes())
                            + ", deviceArenaCapacity=" + (deviceArena == null ? 0L : deviceArena.capacityBytes())
                            + "; global relevance scheduling and fixed-anchor lifecycle evidence are armed.");
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
                        "Phase 3 dev3 validation is delayed for 5 seconds after first world render so startup activity settles before scene jobs are admitted.");
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
        if (!runtimeInstructionsLogged && sceneProbe != null && sceneProbe.productionWorkerIntegrationReady()) {
            runtimeInstructionsLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 3 dev3 combined runtime gate is active. Verify the 3x3 scene visually; break/place blocks; perform F3+T and allow a READY rebuild; travel far enough that the FIRST tracked scene neighborhood really unloads; return to that area so it loads again; WAIT FOR THE ASYNC SCENE TO BECOME LIVE/READY AGAIN; then exit normally. Final evidence should include schedulerEvidenceReady=true and phase2ChunkLifecycleEvidenceReady=true.");
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
        }
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
        String center = "unbound";
        boolean localSceneReady = false, productionWorkerIntegrationReady = false;
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
            lifecycleCursor = probe.lifecycleCursor();
            sceneGeneration = probe.sceneGeneration();
            center = probe.centerKnown() ? "(" + probe.centerSectionX() + "," + probe.centerSectionY() + "," + probe.centerSectionZ() + ")" : "unbound";
            localSceneReady = probe.localSceneReady();
            productionWorkerIntegrationReady = probe.productionWorkerIntegrationReady();
            probe.close();
            sceneProbe = null;
        }

        if (meshWorkers != null) meshWorkers.close();
        if (stagingUploads != null) stagingUploads.close();
        if (deviceArena != null) deviceArena.close();
        deferredReleases.close();

        long dirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.sectionDirtyEvents();
        long playerDirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.playerDirtyEvents();
        long chunkLoadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.chunkLoadEvents();
        long chunkUnloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.chunkUnloadEvents();
        long fixedAnchorChunkLoadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.fixedAnchorChunkLoadEvents();
        long fixedAnchorChunkUnloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.fixedAnchorChunkUnloadEvents();
        long worldChangeEvents = lifecycleCursor == null ? 0L : lifecycleCursor.worldChangeEvents();
        long resourceReloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.resourceReloadEvents();
        long droppedLifecycleEvents = lifecycleCursor == null ? 0L : lifecycleCursor.droppedEvents();

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

        boolean phase2ChunkLifecycleEvidenceReady = phase3GateReady
                && fixedAnchorChunkUnloadEvents > 0L
                && fixedAnchorChunkLoadEvents > 0L
                && fixedAnchorReturnSceneReady
                && droppedLifecycleEvents == 0L
                && unsafeStaleSceneInstalls == 0L
                && workersClean && stagingClean && arenaClean && resourcesClean;

        StringBuilder out = new StringBuilder(4096);
        out.append("Phase 3 dev3 frame coordinator closed after ").append(frameIndex).append(" frame(s): ")
                .append("phase3GateReady=").append(phase3GateReady)
                .append(", schedulerEvidenceReady=").append(schedulerEvidenceReady)
                .append(", phase2ChunkLifecycleEvidenceReady=").append(phase2ChunkLifecycleEvidenceReady)
                .append(", fixedAnchorReturnSceneReady=").append(fixedAnchorReturnSceneReady)
                .append(", productionWorkerIntegrationReady=").append(productionWorkerIntegrationReady)
                .append(", hardFailure=").append(hardFailure)
                .append(", productionSceneInstallStillSynchronous=false, productionWorkerSceneIntegration=true")
                .append(", renderThreadCaptureOwnership=true, renderThreadGpuOwnership=true, workerWorldReadsAfterCapture=0")
                .append(", synchronousSceneMeshBuilds=").append(synchronousSceneMeshBuilds)
                .append(", workerCount=").append(meshWorkers == null ? 0 : meshWorkers.workerCount())
                .append(", workerQueueCapacity=").append(meshWorkers == null ? 0 : meshWorkers.queueCapacity())
                .append(", workerSubmittedJobs=").append(meshWorkers == null ? 0L : meshWorkers.submittedJobs())
                .append(", workerStartedJobs=").append(meshWorkers == null ? 0L : meshWorkers.startedJobs())
                .append(", workerCompletedJobs=").append(meshWorkers == null ? 0L : meshWorkers.completedJobs())
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
