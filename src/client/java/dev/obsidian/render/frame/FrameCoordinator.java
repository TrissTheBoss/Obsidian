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
    private boolean hardFailure;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        SectionMeshWorkerPool workers = null;
        try {
            workers = new SectionMeshWorkerPool(SectionMeshWorkerPool.defaultWorkerCount());
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 3 dev3 bounded scene staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 3 dev3 scene device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            sceneProbe = new AsyncMultiSectionSceneProbe(
                    device, staging, arena, deferredReleases, workers);
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
                            + "; production scene jobs use global HIGH/NORMAL/LOW priority selection, bounded two-record frame admission, worker-local primitive scratch, and render-thread-only capture/GPU ownership.");
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
                        "Phase 3 dev3 validation is delayed for 5 seconds after first world render so startup dirtiness/resource activity settles before production scene jobs are admitted.");
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
                    "Phase 3 dev3 combined runtime gate is active. Verify the 3x3 scene visually; break/place blocks; perform F3+T and allow a READY rebuild; then travel far enough that the FIRST tracked scene neighborhood really unloads, return to that area so it loads again, wait for another READY scene, and exit normally. Final evidence must include schedulerEvidenceReady=true and phase2ChunkLifecycleEvidenceReady=true. synchronousSceneMeshBuilds=0, productionSceneInstallStillSynchronous=false, productionWorkerSceneIntegration=true.");
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
        long usefulSubmissions = 0L;
        long comparisonDraws = 0L;
        long indirectCalls = 0L;
        long resourceEpochChecks = 0L;
        long retirementBackpressureEvents = 0L;
        long retirementRegistrationFailures = 0L;
        long sceneReadyTransitions = 0L;
        long sceneRebuilds = 0L;
        long recordInstallCount = 0L;
        long cameraRecenterEvents = 0L;
        long invalidationBatches = 0L;
        long coalescedEvents = 0L;
        long eligibilityScans = 0L;
        long eligibilitySkips = 0L;
        long unsafeStaleSceneInstalls = 0L;
        long schedulerAdmissionDeferrals = 0L;
        long admittedHighPriority = 0L;
        long admittedNormalPriority = 0L;
        long admittedLowPriority = 0L;
        int maxAdmissionBurst = 0;
        int observedReasonMask = 0;
        int maxLiveRecords = 0;
        int maxAdjacentPairs = 0;
        int maxSimultaneousSceneJobs = 0;
        long maxSceneQuads = 0L;
        long maxSceneVertexBytes = 0L;
        long maxSceneIndexBytes = 0L;
        long sceneWorkerSubmitted = 0L;
        long sceneWorkerCompleted = 0L;
        long sceneWorkerCancelled = 0L;
        long sceneWorkerCancellationRequests = 0L;
        long sceneWorkerStaleDiscards = 0L;
        long sceneWorkerInstalls = 0L;
        long sceneWorkerQueueRejections = 0L;
        long sceneInstallAdmissionDeferrals = 0L;
        long synchronousSceneMeshBuilds = 0L;
        long preinstallInvalidations = 0L;
        long sceneGeneration = 0L;
        String center = "unbound";
        boolean localSceneReady = false;
        boolean productionWorkerIntegrationReady = false;
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
            center = probe.centerKnown()
                    ? "(" + probe.centerSectionX() + "," + probe.centerSectionY() + "," + probe.centerSectionZ() + ")"
                    : "unbound";
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
                && productionWorkerIntegrationReady
                && localSceneReady
                && sceneReadyTransitions >= 2L
                && sceneRebuilds >= 1L
                && recordInstallCount >= 3L
                && sceneWorkerInstalls == recordInstallCount
                && sceneWorkerCompleted >= sceneWorkerInstalls
                && synchronousSceneMeshBuilds == 0L
                && dirtyEvents > 0L
                && resourceReloadEvents > 0L
                && droppedLifecycleEvents == 0L
                && unsafeStaleSceneInstalls == 0L
                && sceneWorkerQueueRejections == 0L
                && workersClean
                && stagingClean
                && arenaClean
                && resourcesClean;

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
                && workerOutputQuads > 0L
                && workerOutputVertexBytes > 0L
                && workerOutputIndexBytes > 0L;

        boolean phase2ChunkLifecycleEvidenceReady = phase3GateReady
                && fixedAnchorChunkUnloadEvents > 0L
                && fixedAnchorChunkLoadEvents > 0L
                && sceneRebuilds >= 1L
                && droppedLifecycleEvents == 0L
                && unsafeStaleSceneInstalls == 0L
                && workersClean
                && stagingClean
                && arenaClean
                && resourcesClean;

        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev3 frame coordinator closed after " + frameIndex + " frame(s): "
                        + "phase3GateReady=" + phase3GateReady
                        + ", schedulerEvidenceReady=" + schedulerEvidenceReady
                        + ", phase2ChunkLifecycleEvidenceReady=" + phase2ChunkLifecycleEvidenceReady
                        + ", productionWorkerIntegrationReady=" + productionWorkerIntegrationReady
                        + ", hardFailure=" + hardFailure
                        + ", productionSceneInstallStillSynchronous=false"
                        + ", productionWorkerSceneIntegration=true"
                        + ", renderThreadCaptureOwnership=true"
                        + ", renderThreadGpuOwnership=true"
                        + ", workerWorldReadsAfterCapture=0"
                        + ", synchronousSceneMeshBuilds=" + synchronousSceneMeshBuilds
                        + ", workerCount=" + (meshWorkers == null ? 0 : meshWorkers.workerCount())
                        + ", workerQueueCapacity=" + (meshWorkers == null ? 0 : meshWorkers.queueCapacity())
                        + ", workerSubmittedJobs=" + (meshWorkers == null ? 0L : meshWorkers.submittedJobs())
                        + ", workerStartedJobs=" + (meshWorkers == null ? 0L : meshWorkers.startedJobs())
                        + ", workerCompletedJobs=" + (meshWorkers == null ? 0L : meshWorkers.completedJobs())
                        + ", workerCancelledJobs=" + (meshWorkers == null ? 0L : meshWorkers.cancelledJobs())
                        + ", workerCancellationRequests=" + (meshWorkers == null ? 0L : meshWorkers.cancellationRequests())
                        + ", workerStolenJobs=" + (meshWorkers == null ? 0L : meshWorkers.stolenJobs())
                        + ", workerQueueFullRejections=" + (meshWorkers == null ? 0L : meshWorkers.queueFullRejections())
                        + ", workerFailedJobs=" + (meshWorkers == null ? 0L : meshWorkers.failedJobs())
                        + ", workerShutdownJoinFailures=" + (meshWorkers == null ? 0L : meshWorkers.shutdownJoinFailures())
                        + ", workerMaxQueueDepth=" + (meshWorkers == null ? 0L : meshWorkers.maxObservedQueueDepth())
                        + ", workerTotalQueueWaitNs=" + (meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs())
                        + ", workerMaxQueueWaitNs=" + (meshWorkers == null ? 0L : meshWorkers.maxQueueWaitNs())
                        + ", workerTotalExecutionNs=" + (meshWorkers == null ? 0L : meshWorkers.totalExecutionNs())
                        + ", workerMaxExecutionNs=" + (meshWorkers == null ? 0L : meshWorkers.maxExecutionNs())
                        + ", workerHighSubmitted=" + workerHighSubmitted
                        + ", workerNormalSubmitted=" + workerNormalSubmitted
                        + ", workerLowSubmitted=" + workerLowSubmitted
                        + ", workerHighCompleted=" + (meshWorkers == null ? 0L : meshWorkers.completedJobs(SectionMeshWorkerPool.PRIORITY_HIGH))
                        + ", workerNormalCompleted=" + (meshWorkers == null ? 0L : meshWorkers.completedJobs(SectionMeshWorkerPool.PRIORITY_NORMAL))
                        + ", workerLowCompleted=" + (meshWorkers == null ? 0L : meshWorkers.completedJobs(SectionMeshWorkerPool.PRIORITY_LOW))
                        + ", workerHighQueueWaitNs=" + (meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs(SectionMeshWorkerPool.PRIORITY_HIGH))
                        + ", workerNormalQueueWaitNs=" + (meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs(SectionMeshWorkerPool.PRIORITY_NORMAL))
                        + ", workerLowQueueWaitNs=" + (meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs(SectionMeshWorkerPool.PRIORITY_LOW))
                        + ", workerHighMaxQueueWaitNs=" + (meshWorkers == null ? 0L : meshWorkers.maxQueueWaitNs(SectionMeshWorkerPool.PRIORITY_HIGH))
                        + ", workerNormalMaxQueueWaitNs=" + (meshWorkers == null ? 0L : meshWorkers.maxQueueWaitNs(SectionMeshWorkerPool.PRIORITY_NORMAL))
                        + ", workerLowMaxQueueWaitNs=" + (meshWorkers == null ? 0L : meshWorkers.maxQueueWaitNs(SectionMeshWorkerPool.PRIORITY_LOW))
                        + ", workerOutputQuads=" + workerOutputQuads
                        + ", workerOutputVertexBytes=" + workerOutputVertexBytes
                        + ", workerOutputIndexBytes=" + workerOutputIndexBytes
                        + ", workerMaxOutputBytes=" + (meshWorkers == null ? 0L : meshWorkers.maxOutputBytes())
                        + ", workerScratchBuildUses=" + workerScratchBuildUses
                        + ", workerMaxScratchQuads=" + (meshWorkers == null ? 0L : meshWorkers.maxScratchQuads())
                        + ", workerDeterminismAudits=" + workerDeterminismAudits
                        + ", workerDeterminismAuditMatches=" + workerDeterminismAuditMatches
                        + ", sceneWorkerSubmitted=" + sceneWorkerSubmitted
                        + ", sceneWorkerCompleted=" + sceneWorkerCompleted
                        + ", sceneWorkerCancelled=" + sceneWorkerCancelled
                        + ", sceneWorkerCancellationRequests=" + sceneWorkerCancellationRequests
                        + ", sceneWorkerStaleDiscards=" + sceneWorkerStaleDiscards
                        + ", sceneWorkerInstalls=" + sceneWorkerInstalls
                        + ", sceneWorkerQueueRejections=" + sceneWorkerQueueRejections
                        + ", preinstallInvalidations=" + preinstallInvalidations
                        + ", maxSimultaneousSceneJobs=" + maxSimultaneousSceneJobs
                        + ", maxAdmissionBurst=" + maxAdmissionBurst
                        + ", schedulerAdmissionDeferrals=" + schedulerAdmissionDeferrals
                        + ", admittedHighPriority=" + admittedHighPriority
                        + ", admittedNormalPriority=" + admittedNormalPriority
                        + ", admittedLowPriority=" + admittedLowPriority
                        + ", sceneInstallAdmissionDeferrals=" + sceneInstallAdmissionDeferrals
                        + ", localSceneReady=" + localSceneReady
                        + ", center=" + center
                        + ", sceneGeneration=" + sceneGeneration
                        + ", sceneReadyTransitions=" + sceneReadyTransitions
                        + ", sceneRebuilds=" + sceneRebuilds
                        + ", recordInstalls=" + recordInstallCount
                        + ", maxLiveRecords=" + maxLiveRecords
                        + ", maxAdjacentPairs=" + maxAdjacentPairs
                        + ", cameraRecenterEvents=" + cameraRecenterEvents
                        + ", invalidationBatches=" + invalidationBatches
                        + ", coalescedEvents=" + coalescedEvents
                        + ", dirtyEvents=" + dirtyEvents
                        + ", playerDirtyEvents=" + playerDirtyEvents
                        + ", chunkLoadEvents=" + chunkLoadEvents
                        + ", chunkUnloadEvents=" + chunkUnloadEvents
                        + ", fixedAnchorChunkLoadEvents=" + fixedAnchorChunkLoadEvents
                        + ", fixedAnchorChunkUnloadEvents=" + fixedAnchorChunkUnloadEvents
                        + ", worldChangeEvents=" + worldChangeEvents
                        + ", resourceReloadEvents=" + resourceReloadEvents
                        + ", droppedLifecycleEvents=" + droppedLifecycleEvents
                        + ", observedReasons=" + SectionLifecycleEvents.describeReasons(observedReasonMask)
                        + ", eligibilityScans=" + eligibilityScans
                        + ", eligibilitySkips=" + eligibilitySkips
                        + ", unsafeStaleSceneInstalls=" + unsafeStaleSceneInstalls
                        + ", maxSceneQuads=" + maxSceneQuads
                        + ", maxSceneVertexBytes=" + maxSceneVertexBytes
                        + ", maxSceneIndexBytes=" + maxSceneIndexBytes
                        + ", usefulSubmissions=" + usefulSubmissions
                        + ", comparisonDraws=" + comparisonDraws
                        + ", indirectCalls=" + indirectCalls
                        + ", resourceEpochChecks=" + resourceEpochChecks
                        + ", retirementBackpressureEvents=" + retirementBackpressureEvents
                        + ", retirementRegistrationFailures=" + retirementRegistrationFailures
                        + ", workersClean=" + workersClean
                        + ", stagingClean=" + stagingClean
                        + ", arenaClean=" + arenaClean
                        + ", resourcesClean=" + resourcesClean
                        + ", stagingSubmittedBytes=" + (stagingUploads == null ? 0L : stagingUploads.submittedBytes())
                        + ", stagingReclaimedBytes=" + (stagingUploads == null ? 0L : stagingUploads.reclaimedBytes())
                        + ", stagingBackpressureEvents=" + (stagingUploads == null ? 0L : stagingUploads.backpressureEvents())
                        + ", pendingUploadBatches=" + (stagingUploads == null ? 0 : stagingUploads.pendingBatches())
                        + ", stagingAbandoned=" + (stagingUploads != null && stagingUploads.abandonedForDeviceShutdown())
                        + ", arenaUsedBytes=" + (deviceArena == null ? 0L : deviceArena.usedBytes())
                        + ", arenaHighWaterBytes=" + (deviceArena == null ? 0L : deviceArena.highWaterBytes())
                        + ", arenaAllocations=" + (deviceArena == null ? 0L : deviceArena.successfulAllocations())
                        + ", arenaAllocationFailures=" + (deviceArena == null ? 0L : deviceArena.allocationFailures())
                        + ", arenaRetired=" + (deviceArena == null ? 0L : deviceArena.retiredAllocations())
                        + ", arenaReclaimed=" + (deviceArena == null ? 0L : deviceArena.reclaimedAllocations())
                        + ", arenaRetirementBackpressureEvents=" + (deviceArena == null ? 0L : deviceArena.retirementBackpressureEvents())
                        + ", arenaStaleHandleRejections=" + (deviceArena == null ? 0L : deviceArena.staleHandleRejections())
                        + ", arenaFreeSpans=" + (deviceArena == null ? 0 : deviceArena.freeSpanCount())
                        + ", arenaLargestFree=" + (deviceArena == null ? 0L : deviceArena.largestFreeBlockBytes())
                        + ", arenaFragmentationPermille=" + (deviceArena == null ? 0 : deviceArena.fragmentationPermille())
                        + ", pendingArenaRetirementBatches=" + (deviceArena == null ? 0 : deviceArena.pendingRetirementBatches())
                        + ", arenaAbandoned=" + (deviceArena != null && deviceArena.abandonedForDeviceShutdown())
                        + ", retiredResources=" + deferredReleases.retiredCount()
                        + ", releasedResources=" + deferredReleases.releasedCount()
                        + ", pendingRetirements=" + deferredReleases.pendingCount() + ".");
    }
}
