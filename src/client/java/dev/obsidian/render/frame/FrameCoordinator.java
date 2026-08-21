package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.RealMultiSectionSceneProbe;
import dev.obsidian.render.terrain.SectionLifecycleEvents;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.renderer.GameRenderer;

/** Render-thread lifecycle root for the active Obsidian milestone. */
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

    private RealMultiSectionSceneProbe sceneProbe;
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
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 2 dev7 bounded multi-section staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 2 dev7 multi-section device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            sceneProbe = new RealMultiSectionSceneProbe(device, staging, arena, deferredReleases);
        } catch (RuntimeException e) {
            if (sceneProbe != null) try { sceneProbe.close(); } catch (RuntimeException ignored) { }
            if (arena != null) try { arena.close(); } catch (RuntimeException ignored) { }
            if (staging != null) try { staging.close(); } catch (RuntimeException ignored) { }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev7 multi-section scene initialization failed; Minecraft will continue for diagnosis.", e);
            hardFailure = true;
        }
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
                    "Phase 2 dev7 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}; persistent 3x3 section scene records reuse the proven P2.6 generation-safe drawable path with whole-window invalidation and bounded one-record upload admission.",
                    frameContexts.size(),
                    cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes());
        }
    }

    public void afterWorldRender(GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || sceneProbe == null || stagingUploads == null || deviceArena == null) return;

        long nowNs = System.nanoTime();
        if (firstWorldRenderNs == 0L) firstWorldRenderNs = nowNs;
        if (nowNs - firstWorldRenderNs < VISUAL_ARM_DELAY_NS) {
            if (!visualDelayLogged) {
                visualDelayLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 2 dev7 multi-section comparison is intentionally delayed for 5 seconds after first world render so startup dirtiness/resource activity settles before scene ownership is admitted.");
            }
            return;
        }

        sceneProbe.afterWorldRender(renderer, frameIndex);
        if (sceneProbe.hardFailure()) {
            hardFailure = true;
            return;
        }

        if (!runtimeInstructionsLogged
                && sceneProbe.state() == RealMultiSectionSceneProbe.State.LIVE
                && sceneProbe.sceneReadyTransitions() > 0L) {
            runtimeInstructionsLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev7 runtime gate: inspect the simultaneous neighboring section overlay for missing/duplicate borders while moving and turning. Break/place blocks inside the visible 3x3 scene, perform F3+T, then move far enough to force at least one scene recenter. A whole-window blank interval during rebuild is allowed; stale or overlapping old geometry is not. Chunk load/unload counters remain diagnostic here because P2.6 separately owns the mandatory exact chunk-lifecycle gate.");
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
    public int pendingArenaRetirementBatches() { return deviceArena == null ? 0 : deviceArena.pendingRetirementBatches(); }
    public RealMultiSectionSceneProbe.State sceneProbeState() {
        if (sceneProbe != null) return sceneProbe.state();
        return hardFailure ? RealMultiSectionSceneProbe.State.FAILED : RealMultiSectionSceneProbe.State.WAITING_WORLD;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;

        RealMultiSectionSceneProbe probe = sceneProbe;
        long usefulSubmissions = 0L;
        long comparisonDraws = 0L;
        long indirectCalls = 0L;
        long resourceEpochChecks = 0L;
        long retirementBackpressureEvents = 0L;
        long retirementRegistrationFailures = 0L;
        long probeStaleInstallRejections = 0L;
        long sceneReadyTransitions = 0L;
        long sceneRebuilds = 0L;
        long recordInstallCount = 0L;
        long cameraRecenterEvents = 0L;
        long invalidationBatches = 0L;
        long coalescedEvents = 0L;
        long eligibilityScans = 0L;
        long eligibilitySkips = 0L;
        long uploadAdmissionDeferrals = 0L;
        long staleSceneRejections = 0L;
        int observedReasonMask = 0;
        int maxLiveRecords = 0;
        int maxAdjacentPairs = 0;
        long maxSceneQuads = 0L;
        long maxSceneVertexBytes = 0L;
        long maxSceneIndexBytes = 0L;
        SectionLifecycleEvents.Cursor lifecycleCursor = null;
        long sceneGeneration = 0L;
        String center = "unbound";
        boolean localSceneGateReady = false;

        if (probe != null) {
            usefulSubmissions = probe.usefulSubmissions();
            comparisonDraws = probe.drawSubmissions();
            indirectCalls = probe.indirectCalls();
            resourceEpochChecks = probe.resourceEpochChecks();
            retirementBackpressureEvents = probe.retirementBackpressureEvents();
            retirementRegistrationFailures = probe.retirementRegistrationFailures();
            probeStaleInstallRejections = probe.probeStaleInstallRejections();
            sceneReadyTransitions = probe.sceneReadyTransitions();
            sceneRebuilds = probe.sceneRebuilds();
            recordInstallCount = probe.recordInstallCount();
            cameraRecenterEvents = probe.cameraRecenterEvents();
            invalidationBatches = probe.invalidationBatches();
            coalescedEvents = probe.coalescedEvents();
            eligibilityScans = probe.eligibilityScans();
            eligibilitySkips = probe.eligibilitySkips();
            uploadAdmissionDeferrals = probe.uploadAdmissionDeferrals();
            staleSceneRejections = probe.staleSceneRejections();
            observedReasonMask = probe.observedReasonMask();
            maxLiveRecords = probe.maxLiveRecords();
            maxAdjacentPairs = probe.maxAdjacentPairs();
            maxSceneQuads = probe.maxSceneQuads();
            maxSceneVertexBytes = probe.maxSceneVertexBytes();
            maxSceneIndexBytes = probe.maxSceneIndexBytes();
            lifecycleCursor = probe.lifecycleCursor();
            sceneGeneration = probe.sceneGeneration();
            center = probe.centerKnown()
                    ? "(" + probe.centerSectionX() + "," + probe.centerSectionY() + "," + probe.centerSectionZ() + ")"
                    : "unbound";
            localSceneGateReady = probe.sceneGateReady();
            probe.close();
            sceneProbe = null;
        }

        if (stagingUploads != null) stagingUploads.close();
        if (deviceArena != null) deviceArena.close();
        deferredReleases.close();

        long dirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.sectionDirtyEvents();
        long playerDirtyEvents = lifecycleCursor == null ? 0L : lifecycleCursor.playerDirtyEvents();
        long chunkLoadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.chunkLoadEvents();
        long chunkUnloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.chunkUnloadEvents();
        long worldChangeEvents = lifecycleCursor == null ? 0L : lifecycleCursor.worldChangeEvents();
        long resourceReloadEvents = lifecycleCursor == null ? 0L : lifecycleCursor.resourceReloadEvents();
        long droppedLifecycleEvents = lifecycleCursor == null ? 0L : lifecycleCursor.droppedEvents();

        boolean sceneGateReady = !hardFailure
                && localSceneGateReady
                && sceneReadyTransitions >= 2L
                && sceneRebuilds >= 1L
                && cameraRecenterEvents >= 1L
                && dirtyEvents > 0L
                && resourceReloadEvents > 0L
                && droppedLifecycleEvents == 0L
                && staleSceneRejections == 0L
                && probeStaleInstallRejections == 0L
                && (stagingUploads == null || stagingUploads.pendingBatches() == 0)
                && (deviceArena == null || deviceArena.pendingRetirementBatches() == 0)
                && deferredReleases.pendingCount() == 0;

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev7 frame coordinator closed after {0} frame(s): sceneGateReady={1}, hardFailure={2}, center={3}, sceneGeneration={4}, sceneReadyTransitions={5}, sceneRebuilds={6}, recordInstalls={7}, maxLiveRecords={8}, maxAdjacentPairs={9}, cameraRecenterEvents={10}, invalidationBatches={11}, coalescedEvents={12}, dirtyEvents={13}, playerDirtyEvents={14}, chunkLoadEvents={15}, chunkUnloadEvents={16}, worldChangeEvents={17}, resourceReloadEvents={18}, droppedLifecycleEvents={19}, observedReasons={20}, eligibilityScans={21}, eligibilitySkips={22}, uploadAdmissionDeferrals={23}, staleSceneRejections={24}, probeStaleInstallRejections={25}, maxSceneQuads={26}, maxSceneVertexBytes={27}, maxSceneIndexBytes={28}, usefulSubmissions={29}, comparisonDraws={30}, indirectCalls={31}, resourceEpochChecks={32}, retirementBackpressureEvents={33}, retirementRegistrationFailures={34}, wholeWindowInvalidation=true, boundedOneRecordAdmission=true, sceneRecordCapacity=9, chunkLifecycleCountersDiagnostic=true, nativeGraphicsSeam=false, indexedIndirect=true, stagingSubmittedBytes={35}, stagingReclaimedBytes={36}, stagingBackpressureEvents={37}, pendingUploadBatches={38}, arenaUsedBytes={39}, arenaHighWaterBytes={40}, arenaAllocations={41}, arenaAllocationFailures={42}, arenaRetired={43}, arenaReclaimed={44}, arenaRetirementBackpressureEvents={45}, arenaStaleHandleRejections={46}, arenaFreeSpans={47}, arenaLargestFree={48}, arenaFragmentationPermille={49}, pendingArenaRetirementBatches={50}, retiredResources={51}, releasedResources={52}, pendingRetirements={53}.",
                frameIndex,
                sceneGateReady,
                hardFailure,
                center,
                sceneGeneration,
                sceneReadyTransitions,
                sceneRebuilds,
                recordInstallCount,
                maxLiveRecords,
                maxAdjacentPairs,
                cameraRecenterEvents,
                invalidationBatches,
                coalescedEvents,
                dirtyEvents,
                playerDirtyEvents,
                chunkLoadEvents,
                chunkUnloadEvents,
                worldChangeEvents,
                resourceReloadEvents,
                droppedLifecycleEvents,
                SectionLifecycleEvents.describeReasons(observedReasonMask),
                eligibilityScans,
                eligibilitySkips,
                uploadAdmissionDeferrals,
                staleSceneRejections,
                probeStaleInstallRejections,
                maxSceneQuads,
                maxSceneVertexBytes,
                maxSceneIndexBytes,
                usefulSubmissions,
                comparisonDraws,
                indirectCalls,
                resourceEpochChecks,
                retirementBackpressureEvents,
                retirementRegistrationFailures,
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
                deferredReleases.retiredCount(),
                deferredReleases.releasedCount(),
                deferredReleases.pendingCount());
    }
}
