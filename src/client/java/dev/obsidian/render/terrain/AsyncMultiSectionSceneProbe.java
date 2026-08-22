package dev.obsidian.render.terrain;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.mesh.SectionMeshWorkerPool;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.SectionPos;

/**
 * Phase 3 dev2 persistent 3x3 scene whose drawable meshes are produced by the
 * bounded {@link SectionMeshWorkerPool} and installed only on the render thread.
 *
 * <p>The already-validated P2.7 whole-window validity model is deliberately
 * retained for this integration milestone. Live world/model/material/light
 * capture remains render-thread owned; only pure {@link BakedSectionMesh}
 * construction crosses the worker boundary.</p>
 */
public final class AsyncMultiSectionSceneProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/AsyncMultiSectionSceneProbe");
    private static final int RECORD_CAPACITY = SectionLifecycleEvents.SCENE_RECORD_CAPACITY;
    private static final int MIN_LIVE_RECORDS = 3;
    private static final int MIN_ADJACENT_PAIRS = 2;
    private static final long ELIGIBILITY_RETRY_NS = 500_000_000L;

    public enum State { WAITING_WORLD, SCANNING, BUILDING, LIVE, RETIRING, FAILED, CLOSED }
    private enum Eligibility { PENDING, ELIGIBLE, SKIPPED }

    private static final class SceneRecord {
        int sectionX;
        int sectionY;
        int sectionZ;
        Eligibility eligibility = Eligibility.PENDING;
        WorkerBackedSectionLifecycleProbe probe;
        boolean installObserved;

        void configure(int x, int y, int z) {
            sectionX = x;
            sectionY = y;
            sectionZ = z;
            eligibility = Eligibility.PENDING;
            probe = null;
            installObserved = false;
        }
    }

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final DeviceGeometryArena arena;
    private final DeferredReleaseQueue deferredReleases;
    private final SectionMeshWorkerPool workers;
    private final SectionLifecycleEvents.Cursor lifecycleCursor = new SectionLifecycleEvents.Cursor();
    private final SceneRecord[] records = new SceneRecord[RECORD_CAPACITY];

    private State state = State.WAITING_WORLD;
    private boolean hardFailure;
    private boolean closed;
    private boolean centerKnown;
    private int centerSectionX;
    private int centerSectionY;
    private int centerSectionZ;
    private long sceneGeneration = 1L;
    private long buildEventSequence;
    private long lastFrameSerial;
    private int scanCursor;
    private long nextEligibilityRetryNs;
    private boolean insufficientSceneLogged;

    private long invalidationBatches;
    private long coalescedEvents;
    private long cameraRecenterEvents;
    private long eligibilityScans;
    private long eligibilitySkips;
    private long recordInstallCount;
    private long sceneReadyTransitions;
    private long sceneRebuilds;
    private long unsafeStaleSceneInstalls;
    private int observedReasonMask;
    private int maxLiveRecords;
    private int maxAdjacentPairs;
    private long maxSceneVertexBytes;
    private long maxSceneIndexBytes;
    private long maxSceneQuads;
    private int maxSimultaneousSceneJobs;

    private long totalUsefulSubmissions;
    private long totalDrawSubmissions;
    private long totalIndirectCalls;
    private long totalResourceEpochChecks;
    private long totalRetirementBackpressureEvents;
    private long totalRetirementRegistrationFailures;
    private long totalWorkerJobsSubmitted;
    private long totalWorkerJobsCompleted;
    private long totalWorkerJobsCancelled;
    private long totalWorkerCancellationRequests;
    private long totalStaleWorkerResultDiscards;
    private long totalWorkerResultInstalls;
    private long totalWorkerQueueRejections;
    private long totalInstallAdmissionDeferrals;
    private long totalSynchronousMeshBuilds;
    private long totalPreinstallInvalidations;

    public AsyncMultiSectionSceneProbe(
            GpuDevice device,
            StagingUploadArena staging,
            DeviceGeometryArena arena,
            DeferredReleaseQueue deferredReleases,
            SectionMeshWorkerPool workers) {
        RenderSystem.assertOnRenderThread();
        if (workers == null) throw new NullPointerException("workers");
        this.device = device;
        this.staging = staging;
        this.arena = arena;
        this.deferredReleases = deferredReleases;
        this.workers = workers;
        for (int i = 0; i < records.length; i++) records[i] = new SceneRecord();
    }

    public void beginFrame(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;
        lastFrameSerial = frameSerial;

        int reasons = SectionLifecycleEvents.drain(
                lifecycleCursor, centerKnown, centerSectionX, centerSectionY, centerSectionZ);
        if (reasons == 0) return;

        observedReasonMask |= reasons;
        int relevantEvents = lifecycleCursor.lastRelevantEventCount();
        if (relevantEvents > 1) coalescedEvents += relevantEvents - 1L;
        invalidationBatches++;
        if ((reasons & SectionLifecycleEvents.REASON_WORLD_CHANGE) != 0) {
            centerKnown = false;
            SectionLifecycleEvents.bindTrackedScene(false, 0, 0, 0);
        }
        invalidateScene(reasons, frameSerial, false);

        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev2 scene invalidation on frame {0}: reasons={1}, relevantEvents={2}, generation={3}, center={4}.",
                frameSerial, SectionLifecycleEvents.describeReasons(reasons), relevantEvents,
                sceneGeneration, centerKnown ? centerString() : "unbound");
    }

    public void afterWorldRender(GameRenderer renderer, long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || state == State.RETIRING || state == State.FAILED) return;
        lastFrameSerial = frameSerial;

        if (!centerKnown) {
            if (!tryBindCenterNearPlayer()) return;
        } else if (state == State.LIVE && tryRecenterIfPlayerLeftWindow(frameSerial)) {
            return;
        }

        if (state == State.SCANNING) {
            scanOneEligibility(frameSerial);
            return;
        }

        if (state == State.BUILDING || state == State.LIVE) {
            driveRecordProbes(renderer, frameSerial);
            updateMaxSimultaneousJobs();
            if (hardFailure || state == State.RETIRING) return;
            if (state == State.BUILDING) {
                admitOneRecord(renderer, frameSerial);
                updateMaxSimultaneousJobs();
                observeSceneReadiness(frameSerial);
            }
        }
    }

    public void endFrame(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        lastFrameSerial = frameSerial;

        for (SceneRecord record : records) {
            WorkerBackedSectionLifecycleProbe probe = record.probe;
            if (probe == null) continue;
            probe.poll(frameSerial);
            WorkerBackedSectionLifecycleProbe.State probeState = probe.state();
            if (probeState == WorkerBackedSectionLifecycleProbe.State.RETIRED
                    || probeState == WorkerBackedSectionLifecycleProbe.State.STALE) {
                disposeRecordProbe(record);
            } else if (probeState == WorkerBackedSectionLifecycleProbe.State.FAILED) {
                hardFailure = true;
                state = State.FAILED;
                disposeRecordProbe(record);
                LOG.log(System.Logger.Level.ERROR,
                        "Phase 3 dev2 worker-backed section record failed; async scene validation failed.");
            }
        }
        updateMaxSimultaneousJobs();

        if (state == State.RETIRING && !hasAnyProbe()) {
            if (hardFailure) {
                state = State.FAILED;
            } else if (centerKnown) {
                configureRecordsForCenter();
                buildEventSequence = SectionLifecycleEvents.latestSequence();
                state = State.SCANNING;
            } else {
                state = State.WAITING_WORLD;
            }
        }
    }

    private boolean tryBindCenterNearPlayer() {
        SectionSnapshot center = SectionSnapshot.tryCaptureNearPlayer();
        if (center == null) return false;
        centerKnown = true;
        centerSectionX = center.sectionX();
        centerSectionY = center.sectionY();
        centerSectionZ = center.sectionZ();
        SectionLifecycleEvents.bindTrackedScene(true, centerSectionX, centerSectionY, centerSectionZ);
        buildEventSequence = SectionLifecycleEvents.latestSequence();
        configureRecordsForCenter();
        state = State.SCANNING;
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev2 bound async 3x3 scene center {0}; immutable capture stays on render thread and pure mesh construction uses {1} bounded worker(s).",
                centerString(), workers.workerCount());
        return true;
    }

    private boolean tryRecenterIfPlayerLeftWindow(long frameSerial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        SectionPos playerSection = SectionPos.of(minecraft.player.blockPosition());
        if (Math.abs(playerSection.x() - centerSectionX) <= SectionLifecycleEvents.SCENE_SECTION_RADIUS
                && Math.abs(playerSection.z() - centerSectionZ) <= SectionLifecycleEvents.SCENE_SECTION_RADIUS) {
            return false;
        }

        SectionSnapshot newCenter = SectionSnapshot.tryCaptureNearPlayer();
        if (newCenter == null) return false;
        if (newCenter.sectionX() == centerSectionX
                && newCenter.sectionY() == centerSectionY
                && newCenter.sectionZ() == centerSectionZ) return false;

        centerSectionX = newCenter.sectionX();
        centerSectionY = newCenter.sectionY();
        centerSectionZ = newCenter.sectionZ();
        cameraRecenterEvents++;
        SectionLifecycleEvents.bindTrackedScene(true, centerSectionX, centerSectionY, centerSectionZ);
        invalidateScene(SectionLifecycleEvents.REASON_SCENE_RECENTER, frameSerial, true);
        return true;
    }

    private void configureRecordsForCenter() {
        int index = 0;
        for (int dz = -SectionLifecycleEvents.SCENE_SECTION_RADIUS;
             dz <= SectionLifecycleEvents.SCENE_SECTION_RADIUS; dz++) {
            for (int dx = -SectionLifecycleEvents.SCENE_SECTION_RADIUS;
                 dx <= SectionLifecycleEvents.SCENE_SECTION_RADIUS; dx++) {
                records[index++].configure(centerSectionX + dx, centerSectionY, centerSectionZ + dz);
            }
        }
        scanCursor = 0;
        nextEligibilityRetryNs = 0L;
        insufficientSceneLogged = false;
    }

    private void resetEligibilityForRetry() {
        for (SceneRecord record : records) {
            if (record.probe == null) record.eligibility = Eligibility.PENDING;
        }
        scanCursor = 0;
        insufficientSceneLogged = false;
    }

    private void scanOneEligibility(long frameSerial) {
        long now = System.nanoTime();
        if (scanCursor >= records.length) {
            int eligible = eligibleRecordCount();
            int adjacent = adjacentEligiblePairCount();
            if (eligible >= MIN_LIVE_RECORDS && adjacent >= MIN_ADJACENT_PAIRS) {
                state = State.BUILDING;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 3 dev2 eligibility scan complete on frame {0}: eligibleRecords={1}/{2}, adjacentPairs={3}; worker job admission begins.",
                        frameSerial, eligible, records.length, adjacent);
                return;
            }
            if (!insufficientSceneLogged) {
                insufficientSceneLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 3 dev2 needs at least {0} eligible records and {1} adjacent pairs. Current eligible={2}, adjacent={3}.",
                        MIN_LIVE_RECORDS, MIN_ADJACENT_PAIRS, eligible, adjacent);
            }
            if (now >= nextEligibilityRetryNs) {
                nextEligibilityRetryNs = now + ELIGIBILITY_RETRY_NS;
                resetEligibilityForRetry();
            }
            return;
        }

        SceneRecord record = records[scanCursor++];
        if (record.eligibility != Eligibility.PENDING) return;
        long sequenceBefore = SectionLifecycleEvents.latestSequence();
        if (sequenceBefore != buildEventSequence) return;

        SectionSnapshot snapshot = SectionSnapshot.tryCaptureSection(
                record.sectionX, record.sectionY, record.sectionZ);
        eligibilityScans++;
        if (snapshot == null) {
            record.eligibility = Eligibility.SKIPPED;
            eligibilitySkips++;
            return;
        }

        try {
            SectionBakedQuadSnapshot baked = SectionBakedQuadSnapshot.capture(snapshot);
            if (SectionLifecycleEvents.latestSequence() != sequenceBefore) {
                record.eligibility = Eligibility.PENDING;
                scanCursor--;
                return;
            }
            if (baked.solidQuads() > 0 && baked.cutoutQuads() > 0) {
                record.eligibility = Eligibility.ELIGIBLE;
            } else {
                record.eligibility = Eligibility.SKIPPED;
                eligibilitySkips++;
            }
        } catch (IllegalStateException e) {
            if (SectionLifecycleEvents.latestSequence() != sequenceBefore) {
                record.eligibility = Eligibility.PENDING;
                scanCursor--;
                return;
            }
            String message = e.getMessage();
            if (message != null && message.contains("produced no supported SOLID/CUTOUT quads")) {
                record.eligibility = Eligibility.SKIPPED;
                eligibilitySkips++;
                return;
            }
            hardFailure = true;
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 3 dev2 eligibility capture failed for section (" + record.sectionX + ","
                            + record.sectionY + "," + record.sectionZ + ").", e);
        }
    }

    private void driveRecordProbes(GameRenderer renderer, long frameSerial) {
        for (SceneRecord record : records) {
            WorkerBackedSectionLifecycleProbe probe = record.probe;
            if (probe == null) continue;
            WorkerBackedSectionLifecycleProbe.State probeState = probe.state();
            if (probeState == WorkerBackedSectionLifecycleProbe.State.WAITING_WORLD
                    || probeState == WorkerBackedSectionLifecycleProbe.State.WAITING_MESH
                    || probeState == WorkerBackedSectionLifecycleProbe.State.READY_TO_INSTALL
                    || probeState == WorkerBackedSectionLifecycleProbe.State.LIVE) {
                probe.afterWorldRender(renderer, frameSerial);
            }
            observeRecordState(record, frameSerial);
            if (hardFailure || state == State.RETIRING) return;
        }
    }

    private void admitOneRecord(GameRenderer renderer, long frameSerial) {
        if (SectionLifecycleEvents.latestSequence() != buildEventSequence) return;
        for (SceneRecord record : records) {
            if (record.eligibility != Eligibility.ELIGIBLE || record.probe != null) continue;
            int priority = record.sectionX == centerSectionX && record.sectionZ == centerSectionZ
                    ? SectionMeshWorkerPool.PRIORITY_HIGH
                    : SectionMeshWorkerPool.PRIORITY_NORMAL;
            record.probe = new WorkerBackedSectionLifecycleProbe(
                    device, staging, arena, deferredReleases, workers, priority,
                    sceneGeneration, buildEventSequence,
                    record.sectionX, record.sectionY, record.sectionZ);
            record.probe.afterWorldRender(renderer, frameSerial);
            observeRecordState(record, frameSerial);
            return;
        }
    }

    private void observeRecordState(SceneRecord record, long frameSerial) {
        WorkerBackedSectionLifecycleProbe probe = record.probe;
        if (probe == null) return;
        WorkerBackedSectionLifecycleProbe.State probeState = probe.state();
        if (probeState == WorkerBackedSectionLifecycleProbe.State.LIVE && !record.installObserved) {
            if (probe.generation() != sceneGeneration
                    || probe.buildEventSequence() != buildEventSequence
                    || SectionLifecycleEvents.latestSequence() != buildEventSequence) {
                unsafeStaleSceneInstalls++;
                invalidateScene(SectionLifecycleEvents.REASON_OVERFLOW, frameSerial, false);
                return;
            }
            record.installObserved = true;
            recordInstallCount++;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 3 dev2 worker-backed scene record installed: generation={0}, section=({1},{2},{3}), installs={4}, liveRecords={5}.",
                    sceneGeneration, record.sectionX, record.sectionY, record.sectionZ,
                    recordInstallCount, liveRecordCount());
        } else if (probeState == WorkerBackedSectionLifecycleProbe.State.FAILED) {
            hardFailure = true;
            state = State.FAILED;
        }
    }

    private void observeSceneReadiness(long frameSerial) {
        int eligible = eligibleRecordCount();
        if (eligible < MIN_LIVE_RECORDS) return;
        for (SceneRecord record : records) {
            if (record.eligibility == Eligibility.ELIGIBLE
                    && (record.probe == null || record.probe.state() != WorkerBackedSectionLifecycleProbe.State.LIVE)) {
                return;
            }
        }
        if (SectionLifecycleEvents.latestSequence() != buildEventSequence) return;

        int live = liveRecordCount();
        int adjacent = adjacentLivePairCount();
        if (live < MIN_LIVE_RECORDS || adjacent < MIN_ADJACENT_PAIRS) return;

        long quads = 0L;
        long vertexBytes = 0L;
        long indexBytes = 0L;
        for (SceneRecord record : records) {
            WorkerBackedSectionLifecycleProbe probe = record.probe;
            if (probe == null || probe.state() != WorkerBackedSectionLifecycleProbe.State.LIVE) continue;
            BakedSectionMesh mesh = probe.drawableMesh();
            if (mesh != null) {
                quads += mesh.quadCount();
                vertexBytes += mesh.vertexBytes();
                indexBytes += mesh.indexBytes();
            }
        }

        maxLiveRecords = Math.max(maxLiveRecords, live);
        maxAdjacentPairs = Math.max(maxAdjacentPairs, adjacent);
        maxSceneQuads = Math.max(maxSceneQuads, quads);
        maxSceneVertexBytes = Math.max(maxSceneVertexBytes, vertexBytes);
        maxSceneIndexBytes = Math.max(maxSceneIndexBytes, indexBytes);
        sceneReadyTransitions++;
        if (sceneReadyTransitions > 1L) sceneRebuilds++;
        state = State.LIVE;

        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev2 async scene READY on frame {0}: generation={1}, center={2}, liveRecords={3}, adjacentPairs={4}, recordInstalls={5}, workerResultInstalls={6}, synchronousSceneMeshBuilds={7}, maxSceneJobs={8}.",
                frameSerial, sceneGeneration, centerString(), live, adjacent, recordInstallCount,
                workerResultInstalls(), synchronousSceneMeshBuilds(), maxSimultaneousSceneJobs);
    }

    private void invalidateScene(int reasons, long frameSerial, boolean recenter) {
        if (sceneGeneration == Long.MAX_VALUE) {
            hardFailure = true;
            state = State.FAILED;
            throw new IllegalStateException("Phase 3 dev2 scene generation exhausted");
        }
        sceneGeneration++;
        buildEventSequence = SectionLifecycleEvents.latestSequence();

        boolean anyProbe = false;
        for (SceneRecord record : records) {
            if (record.probe != null) {
                anyProbe = true;
                record.probe.requestInvalidate(reasons, frameSerial);
            }
        }
        if (anyProbe) state = State.RETIRING;
        else if (centerKnown) {
            configureRecordsForCenter();
            state = State.SCANNING;
        } else {
            state = State.WAITING_WORLD;
        }
        if (recenter) observedReasonMask |= SectionLifecycleEvents.REASON_SCENE_RECENTER;
    }

    private int eligibleRecordCount() {
        int count = 0;
        for (SceneRecord record : records) if (record.eligibility == Eligibility.ELIGIBLE) count++;
        return count;
    }

    private int liveRecordCount() {
        int count = 0;
        for (SceneRecord record : records) {
            if (record.probe != null && record.probe.state() == WorkerBackedSectionLifecycleProbe.State.LIVE) count++;
        }
        maxLiveRecords = Math.max(maxLiveRecords, count);
        return count;
    }

    private int adjacentEligiblePairCount() { return adjacentPairCount(false); }
    private int adjacentLivePairCount() {
        int count = adjacentPairCount(true);
        maxAdjacentPairs = Math.max(maxAdjacentPairs, count);
        return count;
    }

    private int adjacentPairCount(boolean requireLive) {
        int count = 0;
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                int index = z * 3 + x;
                if (!recordQualifies(records[index], requireLive)) continue;
                if (x < 2 && recordQualifies(records[index + 1], requireLive)) count++;
                if (z < 2 && recordQualifies(records[index + 3], requireLive)) count++;
            }
        }
        return count;
    }

    private static boolean recordQualifies(SceneRecord record, boolean requireLive) {
        if (record.eligibility != Eligibility.ELIGIBLE) return false;
        return !requireLive || (record.probe != null
                && record.probe.state() == WorkerBackedSectionLifecycleProbe.State.LIVE);
    }

    private boolean hasAnyProbe() {
        for (SceneRecord record : records) if (record.probe != null) return true;
        return false;
    }

    private void updateMaxSimultaneousJobs() {
        int current = 0;
        for (SceneRecord record : records) {
            if (record.probe != null && record.probe.workerJobOutstanding()) current++;
        }
        maxSimultaneousSceneJobs = Math.max(maxSimultaneousSceneJobs, current);
    }

    private void disposeRecordProbe(SceneRecord record) {
        WorkerBackedSectionLifecycleProbe probe = record.probe;
        if (probe == null) return;
        accumulateProbe(probe);
        probe.close();
        record.probe = null;
        record.installObserved = false;
    }

    private void accumulateProbe(WorkerBackedSectionLifecycleProbe probe) {
        totalUsefulSubmissions += probe.usefulSubmissions();
        totalDrawSubmissions += probe.drawSubmissions();
        totalIndirectCalls += probe.indirectCalls();
        totalResourceEpochChecks += probe.resourceEpochChecks();
        totalRetirementBackpressureEvents += probe.retirementBackpressureEvents();
        totalRetirementRegistrationFailures += probe.retirementRegistrationFailures();
        totalWorkerJobsSubmitted += probe.workerJobsSubmitted();
        totalWorkerJobsCompleted += probe.workerJobsCompleted();
        totalWorkerJobsCancelled += probe.workerJobsCancelled();
        totalWorkerCancellationRequests += probe.workerCancellationRequests();
        totalStaleWorkerResultDiscards += probe.staleWorkerResultDiscards();
        totalWorkerResultInstalls += probe.workerResultInstalls();
        totalWorkerQueueRejections += probe.workerQueueRejections();
        totalInstallAdmissionDeferrals += probe.installAdmissionDeferrals();
        totalSynchronousMeshBuilds += probe.synchronousMeshBuilds();
        if (probe.preinstallInvalidated()) totalPreinstallInvalidations++;
    }

    private long sumCurrent(java.util.function.ToLongFunction<WorkerBackedSectionLifecycleProbe> getter) {
        long total = 0L;
        for (SceneRecord record : records) if (record.probe != null) total += getter.applyAsLong(record.probe);
        return total;
    }

    private String centerString() {
        return "(" + centerSectionX + "," + centerSectionY + "," + centerSectionZ + ")";
    }

    public State state() { return state; }
    public boolean hardFailure() { return hardFailure; }
    public boolean centerKnown() { return centerKnown; }
    public int centerSectionX() { return centerSectionX; }
    public int centerSectionY() { return centerSectionY; }
    public int centerSectionZ() { return centerSectionZ; }
    public long sceneGeneration() { return sceneGeneration; }
    public long buildEventSequence() { return buildEventSequence; }
    public long invalidationBatches() { return invalidationBatches; }
    public long coalescedEvents() { return coalescedEvents; }
    public long cameraRecenterEvents() { return cameraRecenterEvents; }
    public long eligibilityScans() { return eligibilityScans; }
    public long eligibilitySkips() { return eligibilitySkips; }
    public long recordInstallCount() { return recordInstallCount; }
    public long sceneReadyTransitions() { return sceneReadyTransitions; }
    public long sceneRebuilds() { return sceneRebuilds; }
    public long unsafeStaleSceneInstalls() { return unsafeStaleSceneInstalls; }
    public int observedReasonMask() { return observedReasonMask; }
    public int maxLiveRecords() { return maxLiveRecords; }
    public int maxAdjacentPairs() { return maxAdjacentPairs; }
    public long maxSceneVertexBytes() { return maxSceneVertexBytes; }
    public long maxSceneIndexBytes() { return maxSceneIndexBytes; }
    public long maxSceneQuads() { return maxSceneQuads; }
    public int maxSimultaneousSceneJobs() { return maxSimultaneousSceneJobs; }
    public SectionLifecycleEvents.Cursor lifecycleCursor() { return lifecycleCursor; }

    public long usefulSubmissions() { return totalUsefulSubmissions + sumCurrent(WorkerBackedSectionLifecycleProbe::usefulSubmissions); }
    public long drawSubmissions() { return totalDrawSubmissions + sumCurrent(WorkerBackedSectionLifecycleProbe::drawSubmissions); }
    public long indirectCalls() { return totalIndirectCalls + sumCurrent(WorkerBackedSectionLifecycleProbe::indirectCalls); }
    public long resourceEpochChecks() { return totalResourceEpochChecks + sumCurrent(WorkerBackedSectionLifecycleProbe::resourceEpochChecks); }
    public long retirementBackpressureEvents() { return totalRetirementBackpressureEvents + sumCurrent(WorkerBackedSectionLifecycleProbe::retirementBackpressureEvents); }
    public long retirementRegistrationFailures() { return totalRetirementRegistrationFailures + sumCurrent(WorkerBackedSectionLifecycleProbe::retirementRegistrationFailures); }
    public long workerJobsSubmitted() { return totalWorkerJobsSubmitted + sumCurrent(WorkerBackedSectionLifecycleProbe::workerJobsSubmitted); }
    public long workerJobsCompleted() { return totalWorkerJobsCompleted + sumCurrent(WorkerBackedSectionLifecycleProbe::workerJobsCompleted); }
    public long workerJobsCancelled() { return totalWorkerJobsCancelled + sumCurrent(WorkerBackedSectionLifecycleProbe::workerJobsCancelled); }
    public long workerCancellationRequests() { return totalWorkerCancellationRequests + sumCurrent(WorkerBackedSectionLifecycleProbe::workerCancellationRequests); }
    public long staleWorkerResultDiscards() { return totalStaleWorkerResultDiscards + sumCurrent(WorkerBackedSectionLifecycleProbe::staleWorkerResultDiscards); }
    public long workerResultInstalls() { return totalWorkerResultInstalls + sumCurrent(WorkerBackedSectionLifecycleProbe::workerResultInstalls); }
    public long workerQueueRejections() { return totalWorkerQueueRejections + sumCurrent(WorkerBackedSectionLifecycleProbe::workerQueueRejections); }
    public long installAdmissionDeferrals() { return totalInstallAdmissionDeferrals + sumCurrent(WorkerBackedSectionLifecycleProbe::installAdmissionDeferrals); }
    public long synchronousSceneMeshBuilds() { return totalSynchronousMeshBuilds + sumCurrent(WorkerBackedSectionLifecycleProbe::synchronousMeshBuilds); }
    public long preinstallInvalidations() {
        long current = 0L;
        for (SceneRecord record : records) {
            if (record.probe != null && record.probe.preinstallInvalidated()) current++;
        }
        return totalPreinstallInvalidations + current;
    }

    public boolean localSceneReady() {
        return !hardFailure
                && sceneReadyTransitions > 0L
                && maxLiveRecords >= MIN_LIVE_RECORDS
                && maxAdjacentPairs >= MIN_ADJACENT_PAIRS
                && unsafeStaleSceneInstalls == 0L
                && lifecycleCursor.droppedEvents() == 0L;
    }

    public boolean productionWorkerIntegrationReady() {
        return localSceneReady()
                && recordInstallCount >= MIN_LIVE_RECORDS
                && workerResultInstalls() == recordInstallCount
                && synchronousSceneMeshBuilds() == 0L
                && workerQueueRejections() == 0L
                && unsafeStaleSceneInstalls == 0L;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        for (SceneRecord record : records) {
            WorkerBackedSectionLifecycleProbe probe = record.probe;
            if (probe == null) continue;
            accumulateProbe(probe);
            probe.close();
            record.probe = null;
        }
        SectionLifecycleEvents.bindTrackedScene(false, 0, 0, 0);
        state = State.CLOSED;
    }
}
