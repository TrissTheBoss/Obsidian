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
 * Phase 3 persistent 3x3 scene whose drawable meshes are produced by the
 * bounded {@link SectionMeshWorkerPool} and installed only on the render thread.
 *
 * <p>The already-validated P2.7 whole-window validity model is deliberately
 * retained. Live world/model/material/light capture remains render-thread owned;
 * only pure mesh/proof construction crosses the worker boundary. P3.5 adds
 * immutable installed-record border/halo proofs and exact shared-border audits
 * without changing the dev11 emitted geometry path.</p>
 */
public final class AsyncMultiSectionSceneProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/AsyncMultiSectionSceneProbe");
    private static final int RECORD_CAPACITY = SectionLifecycleEvents.SCENE_RECORD_CAPACITY;
    private static final int MIN_LIVE_RECORDS = 3;
    private static final int MIN_ADJACENT_PAIRS = 2;
    private static final int MAX_ADMISSIONS_PER_FRAME = 2;
    private static final long ELIGIBILITY_RETRY_NS = 500_000_000L;
    private static final int SHARED_BORDER_COMPARISONS_PER_PAIR =
            SectionSnapshot.INTERIOR_SIZE * SectionSnapshot.INTERIOR_SIZE * 2;

    public enum State { WAITING_WORLD, SCANNING, BUILDING, LIVE, RETIRING, FAILED, CLOSED }
    private enum Eligibility { PENDING, ELIGIBLE, SKIPPED }

    private static final class SceneRecord {
        int sectionX;
        int sectionY;
        int sectionZ;
        Eligibility eligibility = Eligibility.PENDING;
        WorkerBackedSectionLifecycleProbe probe;
        boolean installObserved;
        boolean transformObserved;

        void configure(int x, int y, int z) {
            sectionX = x;
            sectionY = y;
            sectionZ = z;
            eligibility = Eligibility.PENDING;
            probe = null;
            installObserved = false;
            transformObserved = false;
        }
    }

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final DeviceGeometryArena arena;
    private final DeferredReleaseQueue deferredReleases;
    private final SectionMeshWorkerPool workers;
    private final SectionLifecycleEvents.Cursor lifecycleCursor = new SectionLifecycleEvents.Cursor();
    private final SceneRecord[] records = new SceneRecord[RECORD_CAPACITY];
    private final BorderHaloCorrectnessProof.BuildScratch borderProofScratch =
            new BorderHaloCorrectnessProof.BuildScratch();

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
    private long schedulerAdmissionDeferrals;
    private long admittedHighPriority;
    private long admittedNormalPriority;
    private long admittedLowPriority;
    private int maxAdmissionBurst;
    private int observedReasonMask;
    private int maxLiveRecords;
    private int maxAdjacentPairs;
    private long maxSceneVertexBytes;
    private long maxSceneIndexBytes;
    private long maxSceneQuads;
    private int maxSimultaneousSceneJobs;

    private long borderProofRecords;
    private long borderProofDeterminismAudits;
    private long borderProofDeterminismMatches;
    private long borderOutwardChecks;
    private long borderVisibilityMatches;
    private long borderReferenceMatches;
    private long borderExpectedVisibleFaces;
    private long borderUnsupportedBlockedFaces;
    private long borderBakedQuads;
    private long borderLightColorSamples;
    private long sharedBorderPairAudits;
    private long sharedBorderComparisons;
    private long sharedBorderMatches;

    private long tJunctionProofRecords;
    private long tJunctionProofDeterminismAudits;
    private long tJunctionProofDeterminismMatches;
    private long tJunctionEmittedCandidates;
    private long tJunctionEmittedEdges;
    private long tJunctionStrictInteriorLatticePoints;
    private long tJunctionStrictPoints;
    private long tJunctionBoundsChecks;
    private long tJunctionBoundsMatches;
    private long tJunctionPlaneChecks;
    private long tJunctionPlaneMatches;
    private long tJunctionIntegerLatticeChecks;
    private long tJunctionIntegerLatticeMatches;
    private long cameraRelativeTransformProofRecords;
    private long junctionBearingTransformProofRecords;
    private long cameraRelativeTransformFailures;

    private long differentialProofRecords;
    private long differentialProofDeterminismAudits;
    private long differentialProofDeterminismMatches;
    private long differentialReferenceFacesChecked;
    private long differentialReferenceMappedFaces;
    private long differentialReferenceUnmappedFaces;
    private long differentialReferenceAmbiguousFaces;
    private long differentialSourceQuadsChecked;
    private long differentialPassthroughIdentitiesChecked;
    private long differentialMergedCandidatesChecked;
    private long differentialMergedExpandedFacesChecked;
    private long differentialMaterialChecks;
    private long differentialMaterialMatches;
    private long differentialDirectionChecks;
    private long differentialDirectionMatches;
    private long differentialCanonicalGeometryChecks;
    private long differentialCanonicalGeometryMatches;
    private long differentialUvChecks;
    private long differentialUvMatches;
    private long differentialColorChecks;
    private long differentialColorMatches;
    private long differentialLightChecks;
    private long differentialLightMatches;
    private long differentialMissingSourceCoverage;
    private long differentialDuplicateSourceCoverage;
    private long differentialOptimizedCanonicalWithoutReference;
    private long differentialRealMismatchCount;
    private long differentialFixtureSelfTestPasses;

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
                "Phase 3 P3.5 scene invalidation on frame {0}: reasons={1}, relevantEvents={2}, generation={3}, center={4}, coreDirty={5}, haloOnlyDirty={6}, horizontalHaloDirty={7}, verticalHaloDirty={8}.",
                frameSerial, SectionLifecycleEvents.describeReasons(reasons), relevantEvents,
                sceneGeneration, centerKnown ? centerString() : "unbound",
                lifecycleCursor.renderedCoreDirtyEvents(), lifecycleCursor.haloOnlyDirtyEvents(),
                lifecycleCursor.horizontalHaloDirtyEvents(), lifecycleCursor.verticalHaloDirtyEvents());
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
                admitReadyRecords(renderer, frameSerial);
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
                        "Phase 3 P3.5 worker-backed section record failed; async scene validation failed.");
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
                "Phase 3 P3.5 bound async 3x3 scene center {0}; immutable one-block halos participate in a conservative 5x3x5 section-dirty dependency domain and pure mesh construction uses {1} bounded worker(s).",
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
                        "Phase 3 P3.5 eligibility scan complete on frame {0}: eligibleRecords={1}/{2}, adjacentPairs={3}; bounded relevance-aware worker admission begins.",
                        frameSerial, eligible, records.length, adjacent);
                return;
            }
            if (!insufficientSceneLogged) {
                insufficientSceneLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 3 P3.5 needs at least {0} eligible records and {1} adjacent pairs. Current eligible={2}, adjacent={3}.",
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
                    "Phase 3 P3.5 eligibility capture failed for section (" + record.sectionX + ","
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

    private void admitReadyRecords(GameRenderer renderer, long frameSerial) {
        if (SectionLifecycleEvents.latestSequence() != buildEventSequence) return;
        int admissions = 0;
        int outstandingTarget = Math.max(2, workers.workerCount() * 2);
        while (admissions < MAX_ADMISSIONS_PER_FRAME) {
            if (workers.outstandingJobs() >= outstandingTarget) {
                if (hasUnadmittedEligibleRecord()) schedulerAdmissionDeferrals++;
                break;
            }
            SceneRecord record = nextUnadmittedRecordByPriority();
            if (record == null) break;
            int priority = priorityFor(record);
            record.probe = new WorkerBackedSectionLifecycleProbe(
                    device, staging, arena, deferredReleases, workers, priority,
                    sceneGeneration, buildEventSequence,
                    record.sectionX, record.sectionY, record.sectionZ);
            record.probe.afterWorldRender(renderer, frameSerial);
            observeRecordState(record, frameSerial);
            admissions++;
            if (priority == SectionMeshWorkerPool.PRIORITY_HIGH) admittedHighPriority++;
            else if (priority == SectionMeshWorkerPool.PRIORITY_NORMAL) admittedNormalPriority++;
            else admittedLowPriority++;
            updateMaxSimultaneousJobs();
            if (hardFailure || state == State.RETIRING) break;
        }
        maxAdmissionBurst = Math.max(maxAdmissionBurst, admissions);
    }

    private SceneRecord nextUnadmittedRecordByPriority() {
        for (int priority = SectionMeshWorkerPool.PRIORITY_HIGH;
             priority <= SectionMeshWorkerPool.PRIORITY_LOW; priority++) {
            for (SceneRecord record : records) {
                if (record.eligibility == Eligibility.ELIGIBLE
                        && record.probe == null
                        && priorityFor(record) == priority) {
                    return record;
                }
            }
        }
        return null;
    }

    private boolean hasUnadmittedEligibleRecord() {
        for (SceneRecord record : records) {
            if (record.eligibility == Eligibility.ELIGIBLE && record.probe == null) return true;
        }
        return false;
    }

    private int priorityFor(SceneRecord record) {
        int dx = Math.abs(record.sectionX - centerSectionX);
        int dz = Math.abs(record.sectionZ - centerSectionZ);
        if (dx == 0 && dz == 0) return SectionMeshWorkerPool.PRIORITY_HIGH;
        if (dx + dz == 1) return SectionMeshWorkerPool.PRIORITY_NORMAL;
        return SectionMeshWorkerPool.PRIORITY_LOW;
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

            try {
                SectionSnapshot snapshot = probe.snapshot();
                SectionBakedQuadSnapshot baked = probe.bakedSnapshot();
                BorderHaloCorrectnessProof first = BorderHaloCorrectnessProof.build(
                        snapshot, baked, borderProofScratch);
                BorderHaloCorrectnessProof second = BorderHaloCorrectnessProof.build(
                        snapshot, baked, borderProofScratch);
                borderProofDeterminismAudits++;
                if (!first.contentEquals(second)) {
                    throw new IllegalStateException("P3.5 installed border/halo proof was nondeterministic");
                }
                borderProofDeterminismMatches++;
                borderProofRecords++;
                borderOutwardChecks += first.outwardChecks();
                borderVisibilityMatches += first.visibilityMatches();
                borderReferenceMatches += first.referenceMatches();
                borderExpectedVisibleFaces += first.expectedVisibleFaces();
                borderUnsupportedBlockedFaces += first.unsupportedBlockedFaces();
                borderBakedQuads += first.borderBakedQuads();
                borderLightColorSamples += first.borderLightColorSamples();

                TJunctionTopologyProof tJunction = probe.tJunctionTopologyProof();
                if (tJunction == null
                        || tJunction.emittedCandidates() != probe.emittedMergedQuads()) {
                    throw new IllegalStateException("P3.6 installed record lost worker-side T-junction proof identity");
                }
                tJunctionProofRecords++;
                // Every completed dev13 ticket builds the proof twice and publishes only after exact equality.
                tJunctionProofDeterminismAudits++;
                tJunctionProofDeterminismMatches++;
                tJunctionEmittedCandidates += tJunction.emittedCandidates();
                tJunctionEmittedEdges += tJunction.emittedEdges();
                tJunctionStrictInteriorLatticePoints += tJunction.strictInteriorEdgeLatticePoints();
                tJunctionStrictPoints += tJunction.strictTJunctionPoints();
                tJunctionBoundsChecks += tJunction.boundsChecks();
                tJunctionBoundsMatches += tJunction.boundsMatches();
                tJunctionPlaneChecks += tJunction.planeDirectionChecks();
                tJunctionPlaneMatches += tJunction.planeDirectionMatches();
                tJunctionIntegerLatticeChecks += tJunction.integerLatticeChecks();
                tJunctionIntegerLatticeMatches += tJunction.integerLatticeMatches();

                DifferentialCorrectnessProof differential = probe.differentialCorrectnessProof();
                if (differential == null || !differential.exact()) {
                    throw new IllegalStateException("P3.7 installed record lost exact differential correctness proof");
                }
                differentialProofRecords++;
                differentialProofDeterminismAudits++;
                differentialProofDeterminismMatches++;
                differentialReferenceFacesChecked += differential.referenceFacesChecked();
                differentialReferenceMappedFaces += differential.referenceMappedFaces();
                differentialReferenceUnmappedFaces += differential.referenceUnmappedFaces();
                differentialReferenceAmbiguousFaces += differential.referenceAmbiguousFaces();
                differentialSourceQuadsChecked += differential.sourceQuadsChecked();
                differentialPassthroughIdentitiesChecked += differential.passthroughSourceIdentitiesChecked();
                differentialMergedCandidatesChecked += differential.mergedCandidatesChecked();
                differentialMergedExpandedFacesChecked += differential.mergedExpandedSourceFacesChecked();
                differentialMaterialChecks += differential.materialChecks();
                differentialMaterialMatches += differential.materialMatches();
                differentialDirectionChecks += differential.directionChecks();
                differentialDirectionMatches += differential.directionMatches();
                differentialCanonicalGeometryChecks += differential.canonicalGeometryChecks();
                differentialCanonicalGeometryMatches += differential.canonicalGeometryMatches();
                differentialUvChecks += differential.uvChecks();
                differentialUvMatches += differential.uvMatches();
                differentialColorChecks += differential.colorChecks();
                differentialColorMatches += differential.colorMatches();
                differentialLightChecks += differential.lightChecks();
                differentialLightMatches += differential.lightMatches();
                differentialMissingSourceCoverage += differential.missingSourceCoverage();
                differentialDuplicateSourceCoverage += differential.duplicateSourceCoverage();
                differentialOptimizedCanonicalWithoutReference += differential.optimizedCanonicalWithoutReference();
                differentialRealMismatchCount += differential.realMismatchCount();
                if (differential.fixtureSelfTestPassed()) differentialFixtureSelfTestPasses++;

                LOG.log(System.Logger.Level.INFO,
                        "Phase 3 P3.5 installed border proof: section=({0},{1},{2}), proofFingerprint={3}, outwardChecks={4}, visibilityMatches={5}, referenceMatches={6}, expectedVisible={7}, unsupportedBlockers={8}, borderBakedQuads={9}, frozenLightColorSamples={10}, workerWorldReadsAfterCapture=0.",
                        first.sectionX(), first.sectionY(), first.sectionZ(),
                        Long.toUnsignedString(first.fingerprint()), first.outwardChecks(),
                        first.visibilityMatches(), first.referenceMatches(), first.expectedVisibleFaces(),
                        first.unsupportedBlockedFaces(), first.borderBakedQuads(),
                        first.borderLightColorSamples());
                LOG.log(System.Logger.Level.INFO,
                        "Phase 3 P3.6 installed T-junction proof: section=({0},{1},{2}), emittedCandidates={3}, emittedEdges={4}, strictInteriorLatticePoints={5}, strictTJunctionPoints={6}, bounds={7}/{8}, plane={9}/{10}, integerLattice={11}/{12}, proofFingerprint={13}, geometryChanged=false.",
                        record.sectionX, record.sectionY, record.sectionZ,
                        tJunction.emittedCandidates(), tJunction.emittedEdges(),
                        tJunction.strictInteriorEdgeLatticePoints(), tJunction.strictTJunctionPoints(),
                        tJunction.boundsMatches(), tJunction.boundsChecks(),
                        tJunction.planeDirectionMatches(), tJunction.planeDirectionChecks(),
                        tJunction.integerLatticeMatches(), tJunction.integerLatticeChecks(),
                        Long.toUnsignedString(tJunction.fingerprint()));
                LOG.log(System.Logger.Level.INFO,
                        "Phase 3 P3.7 installed differential proof: section=({0},{1},{2}), referenceFaces={3}, sourceQuads={4}, passthroughIdentities={5}, mergedCandidates={6}, mergedExpandedFaces={7}, material={8}/{9}, geometry={10}/{11}, uv={12}/{13}, color={14}/{15}, light={16}/{17}, missing={18}, duplicate={19}, optimizedWithoutReference={20}, realMismatches={21}, fixtureSelfTest={22}, proofFingerprint={23}.",
                        record.sectionX, record.sectionY, record.sectionZ,
                        differential.referenceFacesChecked(), differential.sourceQuadsChecked(),
                        differential.passthroughSourceIdentitiesChecked(), differential.mergedCandidatesChecked(),
                        differential.mergedExpandedSourceFacesChecked(), differential.materialMatches(),
                        differential.materialChecks(), differential.canonicalGeometryMatches(),
                        differential.canonicalGeometryChecks(), differential.uvMatches(), differential.uvChecks(),
                        differential.colorMatches(), differential.colorChecks(), differential.lightMatches(),
                        differential.lightChecks(), differential.missingSourceCoverage(),
                        differential.duplicateSourceCoverage(), differential.optimizedCanonicalWithoutReference(),
                        differential.realMismatchCount(), differential.fixtureSelfTestPassed(),
                        Long.toUnsignedString(differential.fingerprint()));
            } catch (RuntimeException e) {
                hardFailure = true;
                state = State.FAILED;
                LOG.log(System.Logger.Level.ERROR,
                        "Phase 3 P3.5 installed border/halo proof failed.", e);
                return;
            }

            record.installObserved = true;
            recordInstallCount++;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 3 P3.5 worker-backed scene record installed: generation={0}, section=({1},{2},{3}), priority={4}, installs={5}, liveRecords={6}.",
                    sceneGeneration, record.sectionX, record.sectionY, record.sectionZ,
                    SectionMeshWorkerPool.priorityName(probe.workerPriority()), recordInstallCount, liveRecordCount());
        }

        if (probeState == WorkerBackedSectionLifecycleProbe.State.LIVE
                && record.installObserved && !record.transformObserved && probe.transformCaptured()) {
            if (!probe.cameraRelativeTransformEvidenceReady()) {
                cameraRelativeTransformFailures++;
                hardFailure = true;
                state = State.FAILED;
                LOG.log(System.Logger.Level.ERROR,
                        "Phase 3 P3.6 camera-relative transform proof failed for LIVE section ({0},{1},{2}).",
                        record.sectionX, record.sectionY, record.sectionZ);
                return;
            }
            record.transformObserved = true;
            cameraRelativeTransformProofRecords++;
            TJunctionTopologyProof proof = probe.tJunctionTopologyProof();
            if (proof != null && proof.strictTJunctionPoints() > 0) {
                junctionBearingTransformProofRecords++;
            }
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

        try {
            auditLiveSharedBorders();
        } catch (RuntimeException e) {
            hardFailure = true;
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 3 P3.5 shared-border snapshot audit failed.", e);
            return;
        }

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
                "Phase 3 P3.5 async scene READY on frame {0}: generation={1}, center={2}, liveRecords={3}, adjacentPairs={4}, recordInstalls={5}, borderProofRecords={6}, sharedPairAudits={7}, sharedComparisons={8}, sharedMatches={9}, borderHaloCorrectnessEvidenceReady={10}, synchronousSceneMeshBuilds={11}.",
                frameSerial, sceneGeneration, centerString(), live, adjacent, recordInstallCount,
                borderProofRecords, sharedBorderPairAudits, sharedBorderComparisons, sharedBorderMatches,
                borderHaloCorrectnessEvidenceReady(), synchronousSceneMeshBuilds());
    }

    private void auditLiveSharedBorders() {
        long pairs = 0L;
        long comparisons = 0L;
        for (int z = 0; z < 3; z++) {
            for (int x = 0; x < 3; x++) {
                int index = z * 3 + x;
                SceneRecord current = records[index];
                if (!recordQualifies(current, true)) continue;
                if (x < 2 && recordQualifies(records[index + 1], true)) {
                    comparisons += auditXPair(current, records[index + 1]);
                    pairs++;
                }
                if (z < 2 && recordQualifies(records[index + 3], true)) {
                    comparisons += auditZPair(current, records[index + 3]);
                    pairs++;
                }
            }
        }
        if (pairs <= 0L || comparisons != pairs * SHARED_BORDER_COMPARISONS_PER_PAIR) {
            throw new IllegalStateException("P3.5 shared-border pair accounting mismatch");
        }
        sharedBorderPairAudits += pairs;
        sharedBorderComparisons += comparisons;
        sharedBorderMatches += comparisons;
    }

    private static int auditXPair(SceneRecord left, SceneRecord right) {
        if (right.sectionX != left.sectionX + 1
                || right.sectionY != left.sectionY
                || right.sectionZ != left.sectionZ) {
            throw new IllegalStateException("P3.5 X pair coordinates are not adjacent");
        }
        SectionSnapshot a = requiredLiveSnapshot(left);
        SectionSnapshot b = requiredLiveSnapshot(right);
        int comparisons = 0;
        for (int y = 0; y < SectionSnapshot.INTERIOR_SIZE; y++) {
            for (int z = 0; z < SectionSnapshot.INTERIOR_SIZE; z++) {
                compareSnapshotCell(a, SectionSnapshot.INTERIOR_SIZE, y, z, b, 0, y, z);
                compareSnapshotCell(b, -1, y, z, a, SectionSnapshot.INTERIOR_SIZE - 1, y, z);
                comparisons += 2;
            }
        }
        return comparisons;
    }

    private static int auditZPair(SceneRecord north, SceneRecord south) {
        if (south.sectionX != north.sectionX
                || south.sectionY != north.sectionY
                || south.sectionZ != north.sectionZ + 1) {
            throw new IllegalStateException("P3.5 Z pair coordinates are not adjacent");
        }
        SectionSnapshot a = requiredLiveSnapshot(north);
        SectionSnapshot b = requiredLiveSnapshot(south);
        int comparisons = 0;
        for (int y = 0; y < SectionSnapshot.INTERIOR_SIZE; y++) {
            for (int x = 0; x < SectionSnapshot.INTERIOR_SIZE; x++) {
                compareSnapshotCell(a, x, y, SectionSnapshot.INTERIOR_SIZE, b, x, y, 0);
                compareSnapshotCell(b, x, y, -1, a, x, y, SectionSnapshot.INTERIOR_SIZE - 1);
                comparisons += 2;
            }
        }
        return comparisons;
    }

    private static SectionSnapshot requiredLiveSnapshot(SceneRecord record) {
        if (record.probe == null || record.probe.state() != WorkerBackedSectionLifecycleProbe.State.LIVE) {
            throw new IllegalStateException("P3.5 shared-border record is not LIVE");
        }
        SectionSnapshot snapshot = record.probe.snapshot();
        if (snapshot == null) throw new IllegalStateException("P3.5 LIVE record lost immutable snapshot");
        return snapshot;
    }

    private static void compareSnapshotCell(
            SectionSnapshot a, int ax, int ay, int az,
            SectionSnapshot b, int bx, int by, int bz) {
        if (a.stateId(ax, ay, az) != b.stateId(bx, by, bz)
                || a.classification(ax, ay, az) != b.classification(bx, by, bz)) {
            throw new IllegalStateException("P3.5 independently captured shared-border cells disagree");
        }
    }

    private void invalidateScene(int reasons, long frameSerial, boolean recenter) {
        if (sceneGeneration == Long.MAX_VALUE) {
            hardFailure = true;
            state = State.FAILED;
            throw new IllegalStateException("Phase 3 P3.5 scene generation exhausted");
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
        record.transformObserved = false;
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

    public WorkerBackedSectionLifecycleProbe productionReplacementProbe(int x, int y, int z) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || state != State.LIVE) return null;
        for (SceneRecord record : records) {
            if (record.sectionX != x || record.sectionY != y || record.sectionZ != z
                    || record.eligibility != Eligibility.ELIGIBLE || record.probe == null
                    || !record.installObserved || record.probe.state() != WorkerBackedSectionLifecycleProbe.State.LIVE
                    || record.probe.generation() != sceneGeneration
                    || record.probe.differentialCorrectnessProof() == null
                    || !record.probe.differentialCorrectnessProof().exact()) {
                continue;
            }
            return record.probe;
        }
        return null;
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
    public long schedulerAdmissionDeferrals() { return schedulerAdmissionDeferrals; }
    public long admittedHighPriority() { return admittedHighPriority; }
    public long admittedNormalPriority() { return admittedNormalPriority; }
    public long admittedLowPriority() { return admittedLowPriority; }
    public int maxAdmissionBurst() { return maxAdmissionBurst; }
    public int observedReasonMask() { return observedReasonMask; }
    public int maxLiveRecords() { return maxLiveRecords; }
    public int maxAdjacentPairs() { return maxAdjacentPairs; }
    public long maxSceneVertexBytes() { return maxSceneVertexBytes; }
    public long maxSceneIndexBytes() { return maxSceneIndexBytes; }
    public long maxSceneQuads() { return maxSceneQuads; }
    public int maxSimultaneousSceneJobs() { return maxSimultaneousSceneJobs; }
    public SectionLifecycleEvents.Cursor lifecycleCursor() { return lifecycleCursor; }

    public long borderProofRecords() { return borderProofRecords; }
    public long borderProofDeterminismAudits() { return borderProofDeterminismAudits; }
    public long borderProofDeterminismMatches() { return borderProofDeterminismMatches; }
    public long borderOutwardChecks() { return borderOutwardChecks; }
    public long borderVisibilityMatches() { return borderVisibilityMatches; }
    public long borderReferenceMatches() { return borderReferenceMatches; }
    public long borderExpectedVisibleFaces() { return borderExpectedVisibleFaces; }
    public long borderUnsupportedBlockedFaces() { return borderUnsupportedBlockedFaces; }
    public long borderBakedQuads() { return borderBakedQuads; }
    public long borderLightColorSamples() { return borderLightColorSamples; }
    public long sharedBorderPairAudits() { return sharedBorderPairAudits; }
    public long sharedBorderComparisons() { return sharedBorderComparisons; }
    public long sharedBorderMatches() { return sharedBorderMatches; }
    public long tJunctionProofRecords() { return tJunctionProofRecords; }
    public long tJunctionProofDeterminismAudits() { return tJunctionProofDeterminismAudits; }
    public long tJunctionProofDeterminismMatches() { return tJunctionProofDeterminismMatches; }
    public long tJunctionEmittedCandidates() { return tJunctionEmittedCandidates; }
    public long tJunctionEmittedEdges() { return tJunctionEmittedEdges; }
    public long tJunctionStrictInteriorLatticePoints() { return tJunctionStrictInteriorLatticePoints; }
    public long tJunctionStrictPoints() { return tJunctionStrictPoints; }
    public long tJunctionBoundsChecks() { return tJunctionBoundsChecks; }
    public long tJunctionBoundsMatches() { return tJunctionBoundsMatches; }
    public long tJunctionPlaneChecks() { return tJunctionPlaneChecks; }
    public long tJunctionPlaneMatches() { return tJunctionPlaneMatches; }
    public long tJunctionIntegerLatticeChecks() { return tJunctionIntegerLatticeChecks; }
    public long tJunctionIntegerLatticeMatches() { return tJunctionIntegerLatticeMatches; }
    public long cameraRelativeTransformProofRecords() { return cameraRelativeTransformProofRecords; }
    public long junctionBearingTransformProofRecords() { return junctionBearingTransformProofRecords; }
    public long cameraRelativeTransformFailures() { return cameraRelativeTransformFailures; }
    public long differentialProofRecords() { return differentialProofRecords; }
    public long differentialProofDeterminismAudits() { return differentialProofDeterminismAudits; }
    public long differentialProofDeterminismMatches() { return differentialProofDeterminismMatches; }
    public long differentialReferenceFacesChecked() { return differentialReferenceFacesChecked; }
    public long differentialReferenceMappedFaces() { return differentialReferenceMappedFaces; }
    public long differentialReferenceUnmappedFaces() { return differentialReferenceUnmappedFaces; }
    public long differentialReferenceAmbiguousFaces() { return differentialReferenceAmbiguousFaces; }
    public long differentialSourceQuadsChecked() { return differentialSourceQuadsChecked; }
    public long differentialPassthroughIdentitiesChecked() { return differentialPassthroughIdentitiesChecked; }
    public long differentialMergedCandidatesChecked() { return differentialMergedCandidatesChecked; }
    public long differentialMergedExpandedFacesChecked() { return differentialMergedExpandedFacesChecked; }
    public long differentialMaterialChecks() { return differentialMaterialChecks; }
    public long differentialMaterialMatches() { return differentialMaterialMatches; }
    public long differentialDirectionChecks() { return differentialDirectionChecks; }
    public long differentialDirectionMatches() { return differentialDirectionMatches; }
    public long differentialCanonicalGeometryChecks() { return differentialCanonicalGeometryChecks; }
    public long differentialCanonicalGeometryMatches() { return differentialCanonicalGeometryMatches; }
    public long differentialUvChecks() { return differentialUvChecks; }
    public long differentialUvMatches() { return differentialUvMatches; }
    public long differentialColorChecks() { return differentialColorChecks; }
    public long differentialColorMatches() { return differentialColorMatches; }
    public long differentialLightChecks() { return differentialLightChecks; }
    public long differentialLightMatches() { return differentialLightMatches; }
    public long differentialMissingSourceCoverage() { return differentialMissingSourceCoverage; }
    public long differentialDuplicateSourceCoverage() { return differentialDuplicateSourceCoverage; }
    public long differentialOptimizedCanonicalWithoutReference() { return differentialOptimizedCanonicalWithoutReference; }
    public long differentialRealMismatchCount() { return differentialRealMismatchCount; }
    public long differentialFixtureSelfTestPasses() { return differentialFixtureSelfTestPasses; }

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

    public boolean borderHaloCorrectnessEvidenceReady() {
        return productionWorkerIntegrationReady()
                && SectionLifecycleEvents.dirtyDependencyClassifierSelfTest()
                && borderProofRecords > 0L
                && borderProofRecords == recordInstallCount
                && borderProofDeterminismAudits == borderProofRecords
                && borderProofDeterminismMatches == borderProofDeterminismAudits
                && borderOutwardChecks == borderProofRecords * BorderHaloCorrectnessProof.OUTWARD_CHECKS_PER_BUILD
                && borderVisibilityMatches == borderOutwardChecks
                && borderReferenceMatches == borderOutwardChecks
                && borderBakedQuads > 0L
                && borderLightColorSamples > 0L
                && sharedBorderPairAudits > 0L
                && sharedBorderComparisons == sharedBorderPairAudits * SHARED_BORDER_COMPARISONS_PER_PAIR
                && sharedBorderMatches == sharedBorderComparisons
                && unsafeStaleSceneInstalls == 0L
                && lifecycleCursor.droppedEvents() == 0L;
    }

    public boolean tJunctionPolicyEvidenceReady() {
        return borderHaloCorrectnessEvidenceReady()
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
                && lifecycleCursor.droppedEvents() == 0L;
    }

    public boolean differentialCorrectnessEvidenceReady() {
        return tJunctionPolicyEvidenceReady()
                && differentialProofRecords > 0L
                && differentialProofRecords == recordInstallCount
                && differentialProofDeterminismAudits == differentialProofRecords
                && differentialProofDeterminismMatches == differentialProofDeterminismAudits
                && differentialReferenceFacesChecked > 0L
                && differentialSourceQuadsChecked > 0L
                && differentialPassthroughIdentitiesChecked > 0L
                && differentialMergedCandidatesChecked > 0L
                && differentialMergedExpandedFacesChecked > 0L
                && differentialMaterialChecks > 0L
                && differentialMaterialMatches == differentialMaterialChecks
                && differentialDirectionMatches == differentialDirectionChecks
                && differentialCanonicalGeometryChecks > 0L
                && differentialCanonicalGeometryMatches == differentialCanonicalGeometryChecks
                && differentialUvChecks > 0L
                && differentialUvMatches == differentialUvChecks
                && differentialColorChecks > 0L
                && differentialColorMatches == differentialColorChecks
                && differentialLightChecks > 0L
                && differentialLightMatches == differentialLightChecks
                && differentialMissingSourceCoverage == 0L
                && differentialDuplicateSourceCoverage == 0L
                && differentialOptimizedCanonicalWithoutReference == 0L
                && differentialRealMismatchCount == 0L
                && differentialFixtureSelfTestPasses == differentialProofRecords
                && unsafeStaleSceneInstalls == 0L
                && lifecycleCursor.droppedEvents() == 0L;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;

        boolean borderReady = borderHaloCorrectnessEvidenceReady();
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 P3.5 final border/halo evidence: borderHaloCorrectnessEvidenceReady={0}, lifecycleClassifierSelfTest={1}, borderProofRecords={2}, outwardChecks={3}, visibilityMatches={4}, referenceMatches={5}, borderBakedQuads={6}, frozenLightColorSamples={7}, sharedPairAudits={8}, sharedComparisons={9}, sharedMatches={10}, coreDirty={11}, haloOnlyDirty={12}, horizontalHaloDirty={13}, verticalHaloDirty={14}, workerWorldReadsAfterCapture=0, synchronousSceneMeshBuilds={15}, unsafeStaleSceneInstalls={16}.",
                borderReady, SectionLifecycleEvents.dirtyDependencyClassifierSelfTest(), borderProofRecords,
                borderOutwardChecks, borderVisibilityMatches, borderReferenceMatches, borderBakedQuads,
                borderLightColorSamples, sharedBorderPairAudits, sharedBorderComparisons, sharedBorderMatches,
                lifecycleCursor.renderedCoreDirtyEvents(), lifecycleCursor.haloOnlyDirtyEvents(),
                lifecycleCursor.horizontalHaloDirtyEvents(), lifecycleCursor.verticalHaloDirtyEvents(),
                synchronousSceneMeshBuilds(), unsafeStaleSceneInstalls);
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 P3.6 final T-junction evidence: tJunctionPolicyEvidenceReady={0}, proofRecords={1}, determinism={2}/{3}, emittedCandidates={4}, emittedEdges={5}, strictInteriorLatticePoints={6}, strictTJunctionPoints={7}, bounds={8}/{9}, plane={10}/{11}, integerLattice={12}/{13}, cameraRelativeTransformProofRecords={14}, junctionBearingTransformProofRecords={15}, cameraRelativeTransformFailures={16}, geometryChanged=false, shaderChanged=false, pipelineChanged=false.",
                tJunctionPolicyEvidenceReady(), tJunctionProofRecords,
                tJunctionProofDeterminismMatches, tJunctionProofDeterminismAudits,
                tJunctionEmittedCandidates, tJunctionEmittedEdges, tJunctionStrictInteriorLatticePoints,
                tJunctionStrictPoints, tJunctionBoundsMatches, tJunctionBoundsChecks,
                tJunctionPlaneMatches, tJunctionPlaneChecks,
                tJunctionIntegerLatticeMatches, tJunctionIntegerLatticeChecks,
                cameraRelativeTransformProofRecords, junctionBearingTransformProofRecords,
                cameraRelativeTransformFailures);
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 P3.7 final differential evidence: differentialCorrectnessEvidenceReady={0}, proofRecords={1}, determinism={2}/{3}, referenceFaces={4}, mapped={5}, unmapped={6}, ambiguous={7}, sourceQuads={8}, passthroughIdentities={9}, mergedCandidates={10}, mergedExpandedFaces={11}, material={12}/{13}, direction={14}/{15}, geometry={16}/{17}, uv={18}/{19}, color={20}/{21}, light={22}/{23}, missing={24}, duplicate={25}, optimizedWithoutReference={26}, realMismatches={27}, fixtureSelfTests={28}/{29}, workerWorldReadsAfterCapture=0, geometryChanged=false, shaderChanged=false, pipelineChanged=false.",
                differentialCorrectnessEvidenceReady(), differentialProofRecords,
                differentialProofDeterminismMatches, differentialProofDeterminismAudits,
                differentialReferenceFacesChecked, differentialReferenceMappedFaces,
                differentialReferenceUnmappedFaces, differentialReferenceAmbiguousFaces,
                differentialSourceQuadsChecked, differentialPassthroughIdentitiesChecked,
                differentialMergedCandidatesChecked, differentialMergedExpandedFacesChecked,
                differentialMaterialMatches, differentialMaterialChecks,
                differentialDirectionMatches, differentialDirectionChecks,
                differentialCanonicalGeometryMatches, differentialCanonicalGeometryChecks,
                differentialUvMatches, differentialUvChecks, differentialColorMatches, differentialColorChecks,
                differentialLightMatches, differentialLightChecks, differentialMissingSourceCoverage,
                differentialDuplicateSourceCoverage, differentialOptimizedCanonicalWithoutReference,
                differentialRealMismatchCount, differentialFixtureSelfTestPasses, differentialProofRecords);

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
