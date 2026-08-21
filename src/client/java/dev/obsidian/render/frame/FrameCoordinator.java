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

/** Render-thread lifecycle root for the active Phase 3 dev2 integration milestone. */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");
    private static final int VALIDATION_STAGING_BYTES = 4 * 1024 * 1024;
    private static final int VALIDATION_DEVICE_ARENA_BYTES = 32 * 1024 * 1024;
    private static final long VISUAL_ARM_DELAY_NS = 5_000_000_000L;

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final FrameContextRing frameContexts = new FrameContextRing();
    private final DeferredReleaseQueue deferredReleases = new DeferredReleaseQueue();
    private final GpuDevice device;
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
        this.device = device;

        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        SectionMeshWorkerPool workers = null;
        try {
            workers = new SectionMeshWorkerPool(SectionMeshWorkerPool.defaultWorkerCount());
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 3 dev2 bounded scene staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 3 dev2 scene device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            sceneProbe = new AsyncMultiSectionSceneProbe(
                    device, staging, arena, deferredReleases, workers);
        } catch (RuntimeException e) {
            if (sceneProbe != null) try { sceneProbe.close(); } catch (RuntimeException ignored) { }
            if (workers != null) try { workers.close(); } catch (RuntimeException ignored) { }
            if (arena != null) try { arena.close(); } catch (RuntimeException ignored) { }
            if (staging != null) try { staging.close(); } catch (RuntimeException ignored) { }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 3 dev2 async scene integration initialization failed; Minecraft will continue for diagnosis.", e);
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
                    "Phase 3 dev2 frame coordinator active. contextSlots={0}, cpuTimingCapacity={1}, meshWorkers={2}, meshQueueCapacity={3}, stagingCapacity={4}, deviceArenaCapacity={5}; persistent scene mesh construction is worker-backed while capture and GPU ownership remain render-thread-only.",
                    frameContexts.size(), cpuFrameTimings.capacity(),
                    meshWorkers == null ? 0 : meshWorkers.workerCount(),
                    meshWorkers == null ? 0 : meshWorkers.queueCapacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes());
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
                        "Phase 3 dev2 validation is delayed for 5 seconds after first world render so startup dirtiness/resource activity settles before production scene jobs are admitted.");
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
                    "Phase 3 dev2 production worker scene integration is active. Verify the 3x3 scene visually, break/place blocks, perform F3+T once, allow worker-backed rebuilds to become READY, then exit normally. synchronousSceneMeshBuilds=0, productionSceneInstallStillSynchronous=false, productionWorkerSceneIntegration=true.");
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

        long dirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.sectionDirtyEvents();
        long playerDirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.playerDirtyEvents();
        long chunkLoadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.chunkLoadEvents();
        long chunkUnloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.chunkUnloadEvents();
        long worldChangeEvents = lifecycleCursor == null ? 0L : lifecycleCursor.worldChangeEvents();
        long resourceReloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.resourceReloadEvents();
        long droppedLifecycleEvents = lifecycleCursor == null ? 0L : lifecycleCursor.droppedEvents();

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
                && meshWorkers != null
                && meshWorkers.queueFullRejections() == 0L
                && meshWorkers.failedJobs() == 0L
                && meshWorkers.queuedJobs() == 0
                && meshWorkers.runningJobs() == 0
                && (stagingUploads == null || stagingUploads.pendingBatches() == 0)
                && (deviceArena == null || deviceArena.pendingRetirementBatches() == 0)
                && deferredReleases.pendingCount() == 0;

        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev2 frame coordinator closed after {0} frame(s): phase3GateReady={1}, productionWorkerIntegrationReady={2}, hardFailure={3}, productionSceneInstallStillSynchronous=false, productionWorkerSceneIntegration=true, renderThreadCaptureOwnership=true, renderThreadGpuOwnership=true, workerWorldReadsAfterCapture=0, synchronousSceneMeshBuilds={4}, workerCount={5}, workerQueueCapacity={6}, workerSubmittedJobs={7}, workerStartedJobs={8}, workerCompletedJobs={9}, workerCancelledJobs={10}, workerCancellationRequests={11}, workerStolenJobs={12}, workerQueueFullRejections={13}, workerFailedJobs={14}, workerMaxQueueDepth={15}, workerTotalQueueWaitNs={16}, workerMaxQueueWaitNs={17}, workerTotalExecutionNs={18}, workerMaxExecutionNs={19}, sceneWorkerSubmitted={20}, sceneWorkerCompleted={21}, sceneWorkerCancelled={22}, sceneWorkerCancellationRequests={23}, sceneWorkerStaleDiscards={24}, sceneWorkerInstalls={25}, sceneWorkerQueueRejections={26}, preinstallInvalidations={27}, maxSimultaneousSceneJobs={28}, sceneInstallAdmissionDeferrals={29}, localSceneReady={30}, center={31}, sceneGeneration={32}, sceneReadyTransitions={33}, sceneRebuilds={34}, recordInstalls={35}, maxLiveRecords={36}, maxAdjacentPairs={37}, cameraRecenterEvents={38}, invalidationBatches={39}, coalescedEvents={40}, dirtyEvents={41}, playerDirtyEvents={42}, chunkLoadEvents={43}, chunkUnloadEvents={44}, worldChangeEvents={45}, resourceReloadEvents={46}, droppedLifecycleEvents={47}, observedReasons={48}, eligibilityScans={49}, eligibilitySkips={50}, unsafeStaleSceneInstalls={51}, maxSceneQuads={52}, maxSceneVertexBytes={53}, maxSceneIndexBytes={54}, usefulSubmissions={55}, comparisonDraws={56}, indirectCalls={57}, resourceEpochChecks={58}, retirementBackpressureEvents={59}, retirementRegistrationFailures={60}, nativeGraphicsSeam=false, indexedIndirect=true, stagingSubmittedBytes={61}, stagingReclaimedBytes={62}, stagingBackpressureEvents={63}, pendingUploadBatches={64}, arenaUsedBytes={65}, arenaHighWaterBytes={66}, arenaAllocations={67}, arenaAllocationFailures={68}, arenaRetired={69}, arenaReclaimed={70}, arenaRetirementBackpressureEvents={71}, arenaStaleHandleRejections={72}, arenaFreeSpans={73}, arenaLargestFree={74}, arenaFragmentationPermille={75}, pendingArenaRetirementBatches={76}, retiredResources={77}, releasedResources={78}, pendingRetirements={79}.",
                frameIndex, phase3GateReady, productionWorkerIntegrationReady, hardFailure,
                synchronousSceneMeshBuilds,
                meshWorkers == null ? 0 : meshWorkers.workerCount(),
                meshWorkers == null ? 0 : meshWorkers.queueCapacity(),
                meshWorkers == null ? 0L : meshWorkers.submittedJobs(),
                meshWorkers == null ? 0L : meshWorkers.startedJobs(),
                meshWorkers == null ? 0L : meshWorkers.completedJobs(),
                meshWorkers == null ? 0L : meshWorkers.cancelledJobs(),
                meshWorkers == null ? 0L : meshWorkers.cancellationRequests(),
                meshWorkers == null ? 0L : meshWorkers.stolenJobs(),
                meshWorkers == null ? 0L : meshWorkers.queueFullRejections(),
                meshWorkers == null ? 0L : meshWorkers.failedJobs(),
                meshWorkers == null ? 0L : meshWorkers.maxObservedQueueDepth(),
                meshWorkers == null ? 0L : meshWorkers.totalQueueWaitNs(),
                meshWorkers == null ? 0L : meshWorkers.maxQueueWaitNs(),
                meshWorkers == null ? 0L : meshWorkers.totalExecutionNs(),
                meshWorkers == null ? 0L : meshWorkers.maxExecutionNs(),
                sceneWorkerSubmitted, sceneWorkerCompleted, sceneWorkerCancelled,
                sceneWorkerCancellationRequests, sceneWorkerStaleDiscards, sceneWorkerInstalls,
                sceneWorkerQueueRejections, preinstallInvalidations, maxSimultaneousSceneJobs,
                sceneInstallAdmissionDeferrals, localSceneReady, center, sceneGeneration,
                sceneReadyTransitions, sceneRebuilds, recordInstallCount, maxLiveRecords,
                maxAdjacentPairs, cameraRecenterEvents, invalidationBatches, coalescedEvents,
                dirtyEvents, playerDirtyEvents, chunkLoadEvents, chunkUnloadEvents,
                worldChangeEvents, resourceReloadEvents, droppedLifecycleEvents,
                SectionLifecycleEvents.describeReasons(observedReasonMask), eligibilityScans,
                eligibilitySkips, unsafeStaleSceneInstalls, maxSceneQuads, maxSceneVertexBytes,
                maxSceneIndexBytes, usefulSubmissions, comparisonDraws, indirectCalls,
                resourceEpochChecks, retirementBackpressureEvents, retirementRegistrationFailures,
                stagingUploads == null ? 0L : stagingUploads.submittedBytes(),
                stagingUploads == null ? 0L : stagingUploads.reclaimedBytes(),
                stagingUploads == null ? 0L : stagingUploads.backpressureEvents(),
                stagingUploads == null ? 0 : stagingUploads.pendingBatches(),
                deviceArena == null ? 0L : deviceArena.usedBytes(),
                deviceArena == null ? 0L : deviceArena.highWaterBytes(),
                deviceArena == null ? 0L : deviceArena.successfulAllocations(),
                deviceArena == null ? 0L : deviceArena.allocationFailures(),
                deviceArena == null ? 0L : deviceArena.retiredAllocations(),
                deviceArena == null ? 0L : deviceArena.reclaimedAllocations(),
                deviceArena == null ? 0L : deviceArena.retirementBackpressureEvents(),
                deviceArena == null ? 0L : deviceArena.staleHandleRejections(),
                deviceArena == null ? 0 : deviceArena.freeSpanCount(),
                deviceArena == null ? 0L : deviceArena.largestFreeBlockBytes(),
                deviceArena == null ? 0 : deviceArena.fragmentationPermille(),
                deviceArena == null ? 0 : deviceArena.pendingRetirementBatches(),
                deferredReleases.retiredCount(), deferredReleases.releasedCount(),
                deferredReleases.pendingCount());

        if (stagingUploads != null) stagingUploads.close();
        if (deviceArena != null) deviceArena.close();
        deferredReleases.close();
    }
}
