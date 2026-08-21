package dev.obsidian.render.terrain;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.SectionPos;

/**
 * P2.7 correctness scene: a persistent 3x3 horizontal table of renderer-owned
 * section records layered on the proven P2.6 single-generation drawable.
 *
 * <p>This milestone intentionally invalidates/rebuilds the whole validation
 * window when any relevant record changes. That is coarse but correct and keeps
 * scene ownership/lifetime evidence separate from Phase 3's production async
 * per-section scheduler. Upload admission is one record at a time and waits for
 * the bounded staging ring to reclaim the previous batch.</p>
 */
public final class RealMultiSectionSceneProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/RealMultiSectionSceneProbe");
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
        RealSectionLifecycleProbe probe;
        boolean installObserved;
        int previewSolidQuads;
        int previewCutoutQuads;
        long previewFingerprint;

        void configure(int x, int y, int z) {
            sectionX = x;
            sectionY = y;
            sectionZ = z;
            resetBuildState();
        }

        void resetBuildState() {
            eligibility = Eligibility.PENDING;
            probe = null;
            installObserved = false;
            previewSolidQuads = 0;
            previewCutoutQuads = 0;
            previewFingerprint = 0L;
        }
    }

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final DeviceGeometryArena arena;
    private final DeferredReleaseQueue deferredReleases;
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
    private long uploadAdmissionDeferrals;
    private long recordInstallCount;
    private long sceneReadyTransitions;
    private long sceneRebuilds;
    private long staleSceneRejections;
    private int observedReasonMask;
    private int maxLiveRecords;
    private int maxAdjacentPairs;
    private long maxSceneVertexBytes;
    private long maxSceneIndexBytes;
    private long maxSceneQuads;

    private long totalUsefulSubmissions;
    private long totalDrawSubmissions;
    private long totalIndirectCalls;
    private long totalResourceEpochChecks;
    private long totalRetirementBackpressureEvents;
    private long totalRetirementRegistrationFailures;
    private long totalProbeStaleInstallRejections;

    public RealMultiSectionSceneProbe(
            GpuDevice device,
            StagingUploadArena staging,
            DeviceGeometryArena arena,
            DeferredReleaseQueue deferredReleases) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.staging = staging;
        this.arena = arena;
        this.deferredReleases = deferredReleases;
        for (int i = 0; i < records.length; i++) {
            records[i] = new SceneRecord();
        }
    }

    public void beginFrame(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;
        lastFrameSerial = frameSerial;

        int reasons = SectionLifecycleEvents.drain(
                lifecycleCursor,
                centerKnown,
                centerSectionX,
                centerSectionY,
                centerSectionZ);
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
                "Phase 2 dev7 scene invalidation on frame {0}: reasons={1}, relevantEvents={2}, sceneGeneration={3}, center={4}, coalescedEvents={5}.",
                frameSerial,
                SectionLifecycleEvents.describeReasons(reasons),
                relevantEvents,
                sceneGeneration,
                centerKnown ? centerString() : "unbound",
                coalescedEvents);
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
            if (hardFailure || state == State.RETIRING) return;
            if (state == State.BUILDING) {
                admitOneRecord(renderer, frameSerial);
                observeSceneReadiness(frameSerial);
            }
        }
    }

    public void endFrame(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        lastFrameSerial = frameSerial;

        for (SceneRecord record : records) {
            RealSectionLifecycleProbe probe = record.probe;
            if (probe == null) continue;
            probe.poll(frameSerial);
            RealSectionLifecycleProbe.State probeState = probe.state();
            if (probeState == RealSectionLifecycleProbe.State.RETIRED
                    || probeState == RealSectionLifecycleProbe.State.STALE) {
                if (probeState == RealSectionLifecycleProbe.State.STALE) staleSceneRejections++;
                disposeRecordProbe(record);
            } else if (probeState == RealSectionLifecycleProbe.State.FAILED) {
                hardFailure = true;
                state = State.FAILED;
                disposeRecordProbe(record);
                LOG.log(System.Logger.Level.ERROR,
                        "Phase 2 dev7 section record failed; multi-section scene validation is failed.");
            }
        }

        if (state == State.RETIRING && !hasAnyProbe()) {
            if (hardFailure) {
                state = State.FAILED;
                return;
            }
            if (centerKnown) {
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
                "Phase 2 dev7 bound persistent 3x3 scene center {0}; rendered section window X={1}..{2}, Y={3}, Z={4}..{5}, halo chunk footprint radius={6}.",
                centerString(),
                centerSectionX - 1, centerSectionX + 1,
                centerSectionY,
                centerSectionZ - 1, centerSectionZ + 1,
                SectionLifecycleEvents.SCENE_HALO_CHUNK_RADIUS);
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
                && newCenter.sectionZ() == centerSectionZ) {
            return false;
        }

        int oldX = centerSectionX;
        int oldY = centerSectionY;
        int oldZ = centerSectionZ;
        centerSectionX = newCenter.sectionX();
        centerSectionY = newCenter.sectionY();
        centerSectionZ = newCenter.sectionZ();
        centerKnown = true;
        cameraRecenterEvents++;
        SectionLifecycleEvents.bindTrackedScene(true, centerSectionX, centerSectionY, centerSectionZ);
        invalidateScene(SectionLifecycleEvents.REASON_SCENE_RECENTER, frameSerial, true);

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev7 camera recentered scene from ({0},{1},{2}) to {3}; old records stop drawing before replacement ownership is admitted.",
                oldX, oldY, oldZ, centerString());
        return true;
    }

    private void configureRecordsForCenter() {
        int index = 0;
        for (int dz = -SectionLifecycleEvents.SCENE_SECTION_RADIUS;
             dz <= SectionLifecycleEvents.SCENE_SECTION_RADIUS; dz++) {
            for (int dx = -SectionLifecycleEvents.SCENE_SECTION_RADIUS;
                 dx <= SectionLifecycleEvents.SCENE_SECTION_RADIUS; dx++) {
                records[index++].configure(
                        centerSectionX + dx,
                        centerSectionY,
                        centerSectionZ + dz);
            }
        }
        scanCursor = 0;
        nextEligibilityRetryNs = 0L;
        insufficientSceneLogged = false;
    }

    private void resetEligibilityForRetry() {
        for (SceneRecord record : records) {
            if (record.probe == null) {
                record.eligibility = Eligibility.PENDING;
                record.previewSolidQuads = 0;
                record.previewCutoutQuads = 0;
                record.previewFingerprint = 0L;
            }
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
                        "Phase 2 dev7 eligibility scan complete on frame {0}: eligibleRecords={1}/{2}, adjacentPairs={3}; bounded record admission begins.",
                        frameSerial, eligible, records.length, adjacent);
                return;
            }
            if (!insufficientSceneLogged) {
                insufficientSceneLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 2 dev7 needs at least {0} neighboring sections with both supported SOLID and CUTOUT quads and at least {1} adjacent pair(s). Current eligibleRecords={2}, adjacentPairs={3}; move near ordinary surface terrain with vegetation.",
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
            record.previewSolidQuads = baked.solidQuads();
            record.previewCutoutQuads = baked.cutoutQuads();
            record.previewFingerprint = baked.fingerprint();
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
                    "Phase 2 dev7 eligibility capture failed for section (" + record.sectionX + ","
                            + record.sectionY + "," + record.sectionZ + ").", e);
        }
    }

    private void driveRecordProbes(GameRenderer renderer, long frameSerial) {
        for (SceneRecord record : records) {
            RealSectionLifecycleProbe probe = record.probe;
            if (probe == null) continue;
            RealSectionLifecycleProbe.State probeState = probe.state();
            if (probeState == RealSectionLifecycleProbe.State.WAITING_WORLD
                    || probeState == RealSectionLifecycleProbe.State.LIVE) {
                probe.afterWorldRender(renderer, frameSerial);
            }
            observeRecordState(record, frameSerial);
            if (hardFailure || state == State.RETIRING) return;
        }
    }

    private void admitOneRecord(GameRenderer renderer, long frameSerial) {
        if (SectionLifecycleEvents.latestSequence() != buildEventSequence) return;
        if (staging.pendingBatches() != 0) {
            uploadAdmissionDeferrals++;
            return;
        }

        for (SceneRecord record : records) {
            if (record.eligibility != Eligibility.ELIGIBLE || record.probe != null) continue;
            record.probe = new RealSectionLifecycleProbe(
                    device,
                    staging,
                    arena,
                    deferredReleases,
                    sceneGeneration,
                    buildEventSequence,
                    true,
                    record.sectionX,
                    record.sectionY,
                    record.sectionZ);
            record.probe.afterWorldRender(renderer, frameSerial);
            observeRecordState(record, frameSerial);
            return;
        }
    }

    private void observeRecordState(SceneRecord record, long frameSerial) {
        RealSectionLifecycleProbe probe = record.probe;
        if (probe == null) return;
        RealSectionLifecycleProbe.State probeState = probe.state();
        if (probeState == RealSectionLifecycleProbe.State.LIVE && !record.installObserved) {
            if (probe.generation() != sceneGeneration
                    || probe.buildEventSequence() != buildEventSequence
                    || SectionLifecycleEvents.latestSequence() != buildEventSequence) {
                staleSceneRejections++;
                invalidateScene(SectionLifecycleEvents.REASON_OVERFLOW, frameSerial, false);
                return;
            }
            record.installObserved = true;
            recordInstallCount++;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev7 scene record installed: sceneGeneration={0}, section=({1},{2},{3}), recordInstalls={4}, liveRecords={5}.",
                    sceneGeneration,
                    record.sectionX, record.sectionY, record.sectionZ,
                    recordInstallCount,
                    liveRecordCount());
        } else if (probeState == RealSectionLifecycleProbe.State.STALE) {
            staleSceneRejections++;
            invalidateScene(SectionLifecycleEvents.REASON_OVERFLOW, frameSerial, false);
        } else if (probeState == RealSectionLifecycleProbe.State.FAILED) {
            hardFailure = true;
            state = State.FAILED;
        }
    }

    private void observeSceneReadiness(long frameSerial) {
        int eligible = eligibleRecordCount();
        if (eligible < MIN_LIVE_RECORDS) return;
        for (SceneRecord record : records) {
            if (record.eligibility == Eligibility.ELIGIBLE) {
                if (record.probe == null || record.probe.state() != RealSectionLifecycleProbe.State.LIVE) return;
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
            if (record.probe == null || record.probe.state() != RealSectionLifecycleProbe.State.LIVE) continue;
            BakedSectionMesh mesh = record.probe.drawableMesh();
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
                "Phase 2 dev7 multi-section scene READY on frame {0}: sceneGeneration={1}, center={2}, liveRecords={3}, adjacentPairs={4}, quads={5}, vertexBytes={6}, indexBytes={7}, sceneReadyTransitions={8}, rebuilds={9}, boundedAdmission=true, wholeWindowInvalidation=true, vanillaTerrainActive=true.",
                frameSerial,
                sceneGeneration,
                centerString(),
                live,
                adjacent,
                quads,
                vertexBytes,
                indexBytes,
                sceneReadyTransitions,
                sceneRebuilds);
    }

    private void invalidateScene(int reasons, long frameSerial, boolean recenter) {
        if (sceneGeneration == Long.MAX_VALUE) {
            hardFailure = true;
            state = State.FAILED;
            throw new IllegalStateException("P2.7 scene generation exhausted");
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
        } else state = State.WAITING_WORLD;
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
            if (record.probe != null && record.probe.state() == RealSectionLifecycleProbe.State.LIVE) count++;
        }
        if (count > maxLiveRecords) maxLiveRecords = count;
        return count;
    }

    private int adjacentEligiblePairCount() { return adjacentPairCount(false); }

    private int adjacentLivePairCount() {
        int count = adjacentPairCount(true);
        if (count > maxAdjacentPairs) maxAdjacentPairs = count;
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
        return !requireLive
                || (record.probe != null && record.probe.state() == RealSectionLifecycleProbe.State.LIVE);
    }

    private boolean hasAnyProbe() {
        for (SceneRecord record : records) if (record.probe != null) return true;
        return false;
    }

    private void disposeRecordProbe(SceneRecord record) {
        RealSectionLifecycleProbe probe = record.probe;
        if (probe == null) return;
        accumulateProbe(probe);
        probe.close();
        record.probe = null;
        record.installObserved = false;
    }

    private void accumulateProbe(RealSectionLifecycleProbe probe) {
        totalUsefulSubmissions += probe.usefulSubmissions();
        totalDrawSubmissions += probe.drawSubmissions();
        totalIndirectCalls += probe.indirectCalls();
        totalResourceEpochChecks += probe.resourceEpochChecks();
        totalRetirementBackpressureEvents += probe.retirementBackpressureEvents();
        totalRetirementRegistrationFailures += probe.retirementRegistrationFailures();
        totalProbeStaleInstallRejections += probe.staleInstallRejections();
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
    public long uploadAdmissionDeferrals() { return uploadAdmissionDeferrals; }
    public long recordInstallCount() { return recordInstallCount; }
    public long sceneReadyTransitions() { return sceneReadyTransitions; }
    public long sceneRebuilds() { return sceneRebuilds; }
    public long staleSceneRejections() { return staleSceneRejections; }
    public int observedReasonMask() { return observedReasonMask; }
    public int maxLiveRecords() { return maxLiveRecords; }
    public int maxAdjacentPairs() { return maxAdjacentPairs; }
    public long maxSceneVertexBytes() { return maxSceneVertexBytes; }
    public long maxSceneIndexBytes() { return maxSceneIndexBytes; }
    public long maxSceneQuads() { return maxSceneQuads; }
    public SectionLifecycleEvents.Cursor lifecycleCursor() { return lifecycleCursor; }

    public long usefulSubmissions() {
        long current = totalUsefulSubmissions;
        for (SceneRecord record : records) if (record.probe != null) current += record.probe.usefulSubmissions();
        return current;
    }

    public long drawSubmissions() {
        long current = totalDrawSubmissions;
        for (SceneRecord record : records) if (record.probe != null) current += record.probe.drawSubmissions();
        return current;
    }

    public long indirectCalls() {
        long current = totalIndirectCalls;
        for (SceneRecord record : records) if (record.probe != null) current += record.probe.indirectCalls();
        return current;
    }

    public long resourceEpochChecks() {
        long current = totalResourceEpochChecks;
        for (SceneRecord record : records) if (record.probe != null) current += record.probe.resourceEpochChecks();
        return current;
    }

    public long retirementBackpressureEvents() {
        long current = totalRetirementBackpressureEvents;
        for (SceneRecord record : records) if (record.probe != null) current += record.probe.retirementBackpressureEvents();
        return current;
    }

    public long retirementRegistrationFailures() {
        long current = totalRetirementRegistrationFailures;
        for (SceneRecord record : records) if (record.probe != null) current += record.probe.retirementRegistrationFailures();
        return current;
    }

    public long probeStaleInstallRejections() {
        long current = totalProbeStaleInstallRejections;
        for (SceneRecord record : records) if (record.probe != null) current += record.probe.staleInstallRejections();
        return current;
    }

    public boolean sceneGateReady() {
        return !hardFailure
                && sceneReadyTransitions > 0L
                && maxLiveRecords >= MIN_LIVE_RECORDS
                && maxAdjacentPairs >= MIN_ADJACENT_PAIRS
                && staleSceneRejections == 0L
                && lifecycleCursor.droppedEvents() == 0L;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        for (SceneRecord record : records) {
            RealSectionLifecycleProbe probe = record.probe;
            if (probe == null) continue;
            accumulateProbe(probe);
            probe.close();
            record.probe = null;
        }
        SectionLifecycleEvents.bindTrackedScene(false, 0, 0, 0);
        state = State.CLOSED;
    }
}
