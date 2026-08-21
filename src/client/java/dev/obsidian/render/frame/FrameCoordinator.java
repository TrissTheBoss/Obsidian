package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.BakedSectionMesh;
import dev.obsidian.render.terrain.RealSectionLifecycleProbe;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
import dev.obsidian.render.terrain.SectionBakedQuadSnapshot;
import dev.obsidian.render.terrain.SectionGenerationGate;
import dev.obsidian.render.terrain.SectionLifecycleEvents;
import dev.obsidian.render.terrain.SectionSnapshot;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.renderer.GameRenderer;

/** Render-thread lifecycle root for the active Obsidian milestone. */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");
    private static final int VALIDATION_STAGING_BYTES = 4 * 1024 * 1024;
    private static final int VALIDATION_DEVICE_ARENA_BYTES = 4 * 1024 * 1024;
    private static final long VISUAL_ARM_DELAY_NS = 5_000_000_000L;

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final FrameContextRing frameContexts = new FrameContextRing();
    private final DeferredReleaseQueue deferredReleases = new DeferredReleaseQueue();
    private final SectionLifecycleEvents.Cursor lifecycleCursor = new SectionLifecycleEvents.Cursor();
    private final SectionGenerationGate generationGate = new SectionGenerationGate();
    private final GpuDevice device;
    private final StagingUploadArena stagingUploads;
    private final DeviceGeometryArena deviceArena;
    private final boolean staleGenerationSelfTestPassed;

    private RealSectionLifecycleProbe sectionProbe;
    private FrameContext activeFrame;
    private long frameIndex;
    private long firstWorldRenderNs;
    private long pendingDirtySinceNs;
    private long lastInstallNs;
    private long maxRebuildLatencyNs;
    private long installCount;
    private long rebuildInstallCount;
    private long invalidationBatches;
    private long coalescedEvents;
    private long staleInstallRejections;
    private long totalUsefulSubmissions;
    private long totalDrawSubmissions;
    private long totalIndirectCalls;
    private long totalResourceEpochChecks;
    private long totalRetirementBackpressureEvents;
    private long totalRetirementRegistrationFailures;
    private int observedReasonMask;
    private long observedLiveGeneration;

    private boolean targetKnown;
    private int targetSectionX;
    private int targetSectionY;
    private int targetSectionZ;
    private SectionSnapshot lastSnapshot;
    private ReferenceFaceMesh lastReference;
    private SectionBakedQuadSnapshot lastBaked;
    private BakedSectionMesh lastDrawable;

    private boolean firstFrameLogged;
    private boolean visualDelayLogged;
    private boolean runtimeInstructionsLogged;
    private boolean hardFailure;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        this.device = device;
        staleGenerationSelfTestPassed = SectionGenerationGate.staleSelfTest();
        if (!staleGenerationSelfTestPassed) {
            throw new IllegalStateException("Phase 2 dev6 stale-generation gate self-test failed");
        }

        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        try {
            staging = new StagingUploadArena(device, () -> "Obsidian Phase 2 dev6 bounded staging ring", VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(device, () -> "Obsidian Phase 2 dev6 device geometry arena", VALIDATION_DEVICE_ARENA_BYTES);
        } catch (RuntimeException e) {
            if (arena != null) try { arena.close(); } catch (RuntimeException ignored) { }
            if (staging != null) try { staging.close(); } catch (RuntimeException ignored) { }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev6 lifecycle initialization failed; Minecraft will continue for diagnosis.", e);
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
        drainLifecycleEvents();
        if (!firstFrameLogged) {
            firstFrameLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev6 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}; exact LevelExtractor/ClientLevel/ModelManager lifecycle signals drive a generation-safe persistent one-section drawable. staleGenerationSelfTest=true.",
                    frameContexts.size(), cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes());
        }
    }

    private void drainLifecycleEvents() {
        int reasons = SectionLifecycleEvents.drain(
                lifecycleCursor,
                targetKnown,
                targetSectionX,
                targetSectionY,
                targetSectionZ);
        if (reasons == 0) return;

        observedReasonMask |= reasons;
        int relevantEvents = lifecycleCursor.lastRelevantEventCount();
        if (relevantEvents > 1) {
            coalescedEvents += relevantEvents - 1L;
        }
        invalidationBatches++;
        long newGeneration = generationGate.advance();
        long now = System.nanoTime();
        if (pendingDirtySinceNs == 0L) pendingDirtySinceNs = now;

        if ((reasons & SectionLifecycleEvents.REASON_WORLD_CHANGE) != 0) {
            targetKnown = false;
        }

        RealSectionLifecycleProbe probe = sectionProbe;
        if (probe != null) {
            probe.requestInvalidate(reasons, frameIndex);
        }

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev6 lifecycle invalidation batch on frame {0}: reasons={1}, relevantEvents={2}, generationNow={3}, trackedSection={4}, coalescedEvents={5}; stale geometry is no longer eligible to draw.",
                frameIndex, SectionLifecycleEvents.describeReasons(reasons), relevantEvents,
                newGeneration,
                targetKnown ? "(" + targetSectionX + "," + targetSectionY + "," + targetSectionZ + ")" : "unbound",
                coalescedEvents);
    }

    public void afterWorldRender(GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || stagingUploads == null || deviceArena == null) return;
        long nowNs = System.nanoTime();
        if (firstWorldRenderNs == 0L) firstWorldRenderNs = nowNs;
        if (nowNs - firstWorldRenderNs < VISUAL_ARM_DELAY_NS) {
            if (!visualDelayLogged) {
                visualDelayLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 2 dev6 lifecycle comparison is armed but intentionally delayed for 5 seconds after first world render so startup dirtiness/resource activity settles before the tracked generation is installed.");
            }
            return;
        }

        ensureProbe();
        if (sectionProbe != null) {
            sectionProbe.afterWorldRender(renderer, frameIndex);
            observeLiveInstall();
        }
    }

    private void ensureProbe() {
        if (sectionProbe != null || hardFailure) return;
        long generation = generationGate.currentGeneration();
        long eventSequence = SectionLifecycleEvents.latestSequence();
        sectionProbe = new RealSectionLifecycleProbe(
                device,
                stagingUploads,
                deviceArena,
                deferredReleases,
                generation,
                eventSequence,
                targetKnown,
                targetSectionX,
                targetSectionY,
                targetSectionZ);
    }

    private void observeLiveInstall() {
        RealSectionLifecycleProbe probe = sectionProbe;
        if (probe == null || probe.state() != RealSectionLifecycleProbe.State.LIVE
                || observedLiveGeneration == probe.generation()) {
            return;
        }

        if (!generationGate.tryInstall(probe.generation())) {
            staleInstallRejections++;
            probe.requestInvalidate(SectionLifecycleEvents.REASON_OVERFLOW, frameIndex);
            LOG.log(System.Logger.Level.WARNING,
                    "Phase 2 dev6 rejected generation {0} at the final install gate because current generation is {1}.",
                    probe.generation(), generationGate.currentGeneration());
            return;
        }

        observedLiveGeneration = probe.generation();
        installCount++;
        if (installCount > 1L) rebuildInstallCount++;

        SectionSnapshot snapshot = probe.snapshot();
        if (snapshot == null) {
            hardFailure = true;
            probe.requestInvalidate(SectionLifecycleEvents.REASON_OVERFLOW, frameIndex);
            LOG.log(System.Logger.Level.ERROR, "Phase 2 dev6 live generation has no immutable snapshot.");
            return;
        }
        targetKnown = true;
        targetSectionX = snapshot.sectionX();
        targetSectionY = snapshot.sectionY();
        targetSectionZ = snapshot.sectionZ();
        lastSnapshot = snapshot;
        lastReference = probe.referenceMesh();
        lastBaked = probe.bakedSnapshot();
        lastDrawable = probe.drawableMesh();

        long now = System.nanoTime();
        lastInstallNs = now;
        if (pendingDirtySinceNs != 0L) {
            long latency = now - pendingDirtySinceNs;
            if (latency > maxRebuildLatencyNs) maxRebuildLatencyNs = latency;
            pendingDirtySinceNs = 0L;
        }

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev6 generation installed: generation={0}, installCount={1}, rebuildInstalls={2}, section=({3},{4},{5}), blockBounds=[{6}..{7},{8}..{9},{10}..{11}], snapshotFingerprint={12}, generalizedFingerprint={13}, drawableFingerprint={14}, staleGenerationRejectedByGate={15}, generationCarriedCaptureBuildUploadInstall=true.",
                probe.generation(), installCount, rebuildInstallCount,
                targetSectionX, targetSectionY, targetSectionZ,
                targetSectionX * 16, targetSectionX * 16 + 15,
                targetSectionY * 16, targetSectionY * 16 + 15,
                targetSectionZ * 16, targetSectionZ * 16 + 15,
                Long.toUnsignedString(snapshot.fingerprint()),
                lastBaked == null ? "none" : Long.toUnsignedString(lastBaked.fingerprint()),
                lastDrawable == null ? "none" : Long.toUnsignedString(lastDrawable.fingerprint()),
                staleGenerationSelfTestPassed);

        if (!runtimeInstructionsLogged) {
            runtimeInstructionsLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev6 runtime gate: keep this tracked section visible; break/place blocks in the logged block bounds and confirm the overlay stops showing stale geometry then rebuilds. Trigger a resource reload (F3+T). Then travel far enough for the tracked chunk neighborhood to unload and return so chunk-unload/chunk-load rebuilds are observed. Exit normally after each class of event has rebuilt at least once.");
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
        if (sectionProbe != null) {
            sectionProbe.poll(frameIndex);
            observeLiveInstall();
            RealSectionLifecycleProbe.State state = sectionProbe.state();
            if (state == RealSectionLifecycleProbe.State.RETIRED
                    || state == RealSectionLifecycleProbe.State.STALE) {
                disposeFinishedProbe();
            } else if (state == RealSectionLifecycleProbe.State.FAILED) {
                hardFailure = true;
                disposeFinishedProbe();
            }
        }
    }

    private void disposeFinishedProbe() {
        RealSectionLifecycleProbe probe = sectionProbe;
        if (probe == null) return;
        totalUsefulSubmissions += probe.usefulSubmissions();
        totalDrawSubmissions += probe.drawSubmissions();
        totalIndirectCalls += probe.indirectCalls();
        totalResourceEpochChecks += probe.resourceEpochChecks();
        totalRetirementBackpressureEvents += probe.retirementBackpressureEvents();
        totalRetirementRegistrationFailures += probe.retirementRegistrationFailures();
        staleInstallRejections += probe.staleInstallRejections();
        if (probe.snapshot() != null) lastSnapshot = probe.snapshot();
        if (probe.referenceMesh() != null) lastReference = probe.referenceMesh();
        if (probe.bakedSnapshot() != null) lastBaked = probe.bakedSnapshot();
        if (probe.drawableMesh() != null) lastDrawable = probe.drawableMesh();
        probe.close();
        sectionProbe = null;
        observedLiveGeneration = 0L;
    }

    public long frameIndex() { return frameIndex; }
    public long latestCpuFrameTimeNs() { return cpuFrameTimings.latestNs(); }
    public FrameTimings cpuFrameTimings() { return cpuFrameTimings; }
    public int pendingRetirements() { return deferredReleases.pendingCount(); }
    public int pendingUploadBatches() { return stagingUploads == null ? 0 : stagingUploads.pendingBatches(); }
    public int pendingArenaRetirementBatches() { return deviceArena == null ? 0 : deviceArena.pendingRetirementBatches(); }
    public RealSectionLifecycleProbe.State sectionProbeState() {
        return sectionProbe == null ? (hardFailure ? RealSectionLifecycleProbe.State.FAILED : RealSectionLifecycleProbe.State.WAITING_WORLD) : sectionProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;

        RealSectionLifecycleProbe probe = sectionProbe;
        if (probe != null) {
            if (probe.snapshot() != null) lastSnapshot = probe.snapshot();
            if (probe.referenceMesh() != null) lastReference = probe.referenceMesh();
            if (probe.bakedSnapshot() != null) lastBaked = probe.bakedSnapshot();
            if (probe.drawableMesh() != null) lastDrawable = probe.drawableMesh();
            probe.close();
            totalUsefulSubmissions += probe.usefulSubmissions();
            totalDrawSubmissions += probe.drawSubmissions();
            totalIndirectCalls += probe.indirectCalls();
            totalResourceEpochChecks += probe.resourceEpochChecks();
            totalRetirementBackpressureEvents += probe.retirementBackpressureEvents();
            totalRetirementRegistrationFailures += probe.retirementRegistrationFailures();
            staleInstallRejections += probe.staleInstallRejections();
            sectionProbe = null;
        }

        if (stagingUploads != null) stagingUploads.close();
        if (deviceArena != null) deviceArena.close();
        deferredReleases.close();

        boolean lifecycleGateReady = !hardFailure
                && staleGenerationSelfTestPassed
                && rebuildInstallCount >= 3L
                && lifecycleCursor.sectionDirtyEvents() > 0L
                && lifecycleCursor.resourceReloadEvents() > 0L
                && lifecycleCursor.chunkUnloadEvents() > 0L
                && lifecycleCursor.chunkLoadEvents() > 0L
                && (stagingUploads == null || stagingUploads.pendingBatches() == 0)
                && (deviceArena == null || deviceArena.pendingRetirementBatches() == 0)
                && deferredReleases.pendingCount() == 0;

        long currentUseful = totalUsefulSubmissions;
        long currentDraws = totalDrawSubmissions;
        long currentIndirect = totalIndirectCalls;
        long currentResourceChecks = totalResourceEpochChecks;

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev6 frame coordinator closed after {0} frame(s): lifecycleGateReady={1}, hardFailure={2}, trackedSection={3}, currentGeneration={4}, installedGeneration={5}, generationAdvances={6}, installCount={7}, rebuildInstalls={8}, invalidationBatches={9}, coalescedEvents={10}, staleGenerationSelfTest={11}, staleInstallRejections={12}, dirtyEvents={13}, playerDirtyEvents={14}, chunkLoadEvents={15}, chunkUnloadEvents={16}, worldChangeEvents={17}, resourceReloadEvents={18}, droppedLifecycleEvents={19}, observedReasons={20}, maxRebuildLatencyNs={21}, snapshotFingerprint={22}, cubeReferenceFingerprint={23}, generalizedFingerprint={24}, drawableFingerprint={25}, generalizedQuads={26}, solidQuads={27}, cutoutQuads={28}, usefulSubmissions={29}, comparisonDraws={30}, indirectCalls={31}, resourceEpochChecks={32}, profilerOnlySubmissions=0, worldReadsAfterGeneralizedCapture=0, cubeOraclePreserved=true, generationCarriedCaptureBuildUploadInstall=true, exactVanillaDirtySink=true, immediateStaleDrawSuppression=true, completionGatedReplacement=true, nativeGraphicsSeam=false, indexedIndirect=true, stagingSubmittedBytes={33}, stagingReclaimedBytes={34}, stagingBackpressureEvents={35}, pendingUploadBatches={36}, arenaUsedBytes={37}, arenaAllocations={38}, arenaRetired={39}, arenaReclaimed={40}, arenaRetirementBackpressureEvents={41}, arenaStaleHandleRejections={42}, arenaFreeSpans={43}, arenaLargestFree={44}, arenaFragmentationPermille={45}, pendingArenaRetirementBatches={46}, retiredResources={47}, releasedResources={48}, pendingRetirements={49}.",
                frameIndex, lifecycleGateReady, hardFailure,
                targetKnown ? "(" + targetSectionX + "," + targetSectionY + "," + targetSectionZ + ")" : "unbound",
                generationGate.currentGeneration(), generationGate.installedGeneration(), generationGate.advances(),
                installCount, rebuildInstallCount, invalidationBatches, coalescedEvents,
                staleGenerationSelfTestPassed, staleInstallRejections + generationGate.rejectedInstalls(),
                lifecycleCursor.sectionDirtyEvents(), lifecycleCursor.playerDirtyEvents(),
                lifecycleCursor.chunkLoadEvents(), lifecycleCursor.chunkUnloadEvents(),
                lifecycleCursor.worldChangeEvents(), lifecycleCursor.resourceReloadEvents(), lifecycleCursor.droppedEvents(),
                SectionLifecycleEvents.describeReasons(observedReasonMask), maxRebuildLatencyNs,
                lastSnapshot == null ? "none" : Long.toUnsignedString(lastSnapshot.fingerprint()),
                lastReference == null ? "none" : Long.toUnsignedString(lastReference.fingerprint()),
                lastBaked == null ? "none" : Long.toUnsignedString(lastBaked.fingerprint()),
                lastDrawable == null ? "none" : Long.toUnsignedString(lastDrawable.fingerprint()),
                lastBaked == null ? 0 : lastBaked.quadCount(), lastBaked == null ? 0 : lastBaked.solidQuads(),
                lastBaked == null ? 0 : lastBaked.cutoutQuads(), currentUseful, currentDraws, currentIndirect, currentResourceChecks,
                stagingUploads == null ? 0L : stagingUploads.submittedBytes(), stagingUploads == null ? 0L : stagingUploads.reclaimedBytes(),
                stagingUploads == null ? 0L : stagingUploads.backpressureEvents(), stagingUploads == null ? 0 : stagingUploads.pendingBatches(),
                deviceArena == null ? 0L : deviceArena.usedBytes(), deviceArena == null ? 0L : deviceArena.successfulAllocations(),
                deviceArena == null ? 0L : deviceArena.retiredAllocations(), deviceArena == null ? 0L : deviceArena.reclaimedAllocations(),
                (deviceArena == null ? 0L : deviceArena.retirementBackpressureEvents()) + totalRetirementBackpressureEvents,
                deviceArena == null ? 0L : deviceArena.staleHandleRejections(), deviceArena == null ? 0 : deviceArena.freeSpanCount(),
                deviceArena == null ? 0L : deviceArena.largestFreeBlockBytes(), deviceArena == null ? 0 : deviceArena.fragmentationPermille(),
                deviceArena == null ? 0 : deviceArena.pendingRetirementBatches(), deferredReleases.retiredCount(),
                deferredReleases.releasedCount(), deferredReleases.pendingCount());
    }
}
