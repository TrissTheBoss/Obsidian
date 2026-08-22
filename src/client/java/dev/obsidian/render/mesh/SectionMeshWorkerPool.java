package dev.obsidian.render.mesh;

import dev.obsidian.render.terrain.BakedSectionMesh;
import dev.obsidian.render.terrain.BinarySectionVisibility;
import dev.obsidian.render.terrain.CanonicalFaceRenderKeys;
import dev.obsidian.render.terrain.GreedySectionRectangles;
import dev.obsidian.render.terrain.OrdinaryQuadEmissionSafety;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
import dev.obsidian.render.terrain.RenderMergeCandidates;
import dev.obsidian.render.terrain.RepeatAwareUvDescriptors;
import dev.obsidian.render.terrain.SectionBakedQuadSnapshot;
import dev.obsidian.render.terrain.SectionSnapshot;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Bounded dedicated worker pool for pure section mesh construction.
 *
 * <p>Jobs contain only immutable renderer-owned Phase 2 snapshots. There are
 * three bounded priority lanes per worker and idle workers steal from peers.
 * Dev3 chooses work by priority across the whole pool before falling through to
 * lower relevance. P3.2 builds compact binary directional-face visibility,
 * P3.3 partitions that proven topology into deterministic greedy rectangles,
 * P3.4 dev6 maps conservative canonical faces to exact baked render keys,
 * dev7 partitions only those eligible faces into render-key-aware merge
 * candidates, dev8 classifies ordinary four-vertex emission safety, and dev9
 * proves exact sprite-local repeat-aware UV descriptors for multi-face
 * candidates. The existing baked mesh remains the production drawable. No
 * worker touches Minecraft world/chunk/model state or GPU objects.</p>
 */
public final class SectionMeshWorkerPool implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/SectionMeshWorkers");

    public static final int PRIORITY_HIGH = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_LOW = 2;
    public static final int PRIORITY_COUNT = 3;

    private static final int MAX_WORKERS = 4;
    private static final int QUEUE_CAPACITY_PER_WORKER = 16;
    private static final long IDLE_WAIT_MS = 4L;
    private static final long SHUTDOWN_JOIN_MS = 500L;
    private static final long DETERMINISM_AUDIT_INTERVAL = 64L;

    public enum TicketState { QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED }

    /** One immutable asynchronous build request and its terminal result. */
    public static final class Ticket {
        private final long id;
        private final long generation;
        private final long eventSequence;
        private final int priority;
        private final SectionSnapshot snapshot;
        private final SectionBakedQuadSnapshot bakedSnapshot;
        private final long enqueueNs;

        private volatile TicketState state = TicketState.QUEUED;
        private volatile boolean cancellationRequested;
        private volatile BinarySectionVisibility visibility;
        private volatile GreedySectionRectangles rectangles;
        private volatile CanonicalFaceRenderKeys renderKeys;
        private volatile RenderMergeCandidates mergeCandidates;
        private volatile OrdinaryQuadEmissionSafety emissionSafety;
        private volatile RepeatAwareUvDescriptors repeatAwareUv;
        private volatile BakedSectionMesh mesh;
        private volatile Throwable failure;
        private volatile long startNs;
        private volatile long endNs;
        private volatile boolean stolen;

        private Ticket(
                long id,
                long generation,
                long eventSequence,
                int priority,
                SectionSnapshot snapshot,
                SectionBakedQuadSnapshot bakedSnapshot) {
            this.id = id;
            this.generation = generation;
            this.eventSequence = eventSequence;
            this.priority = priority;
            this.snapshot = snapshot;
            this.bakedSnapshot = bakedSnapshot;
            this.enqueueNs = System.nanoTime();
        }

        public long id() { return id; }
        public long generation() { return generation; }
        public long eventSequence() { return eventSequence; }
        public int priority() { return priority; }
        public TicketState state() { return state; }
        public boolean terminal() {
            TicketState current = state;
            return current == TicketState.COMPLETED
                    || current == TicketState.CANCELLED
                    || current == TicketState.FAILED;
        }
        public boolean cancellationRequested() { return cancellationRequested; }
        public BinarySectionVisibility visibility() { return visibility; }
        public GreedySectionRectangles rectangles() { return rectangles; }
        public CanonicalFaceRenderKeys renderKeys() { return renderKeys; }
        public RenderMergeCandidates mergeCandidates() { return mergeCandidates; }
        public OrdinaryQuadEmissionSafety emissionSafety() { return emissionSafety; }
        public RepeatAwareUvDescriptors repeatAwareUv() { return repeatAwareUv; }
        public BakedSectionMesh mesh() { return mesh; }
        public Throwable failure() { return failure; }
        public long queueWaitNs() { return startNs == 0L ? 0L : Math.max(0L, startNs - enqueueNs); }
        public long executionNs() { return endNs == 0L || startNs == 0L ? 0L : Math.max(0L, endNs - startNs); }
        public boolean stolen() { return stolen; }
    }

    private static final class WorkerQueue {
        @SuppressWarnings("unchecked")
        private final ArrayDeque<Ticket>[] lanes = new ArrayDeque[] {
                new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>()
        };
        private int size;

        synchronized boolean offer(Ticket ticket) {
            if (size >= QUEUE_CAPACITY_PER_WORKER) return false;
            lanes[ticket.priority].addLast(ticket);
            size++;
            return true;
        }

        synchronized Ticket poll(int priority) {
            Ticket ticket = lanes[priority].pollFirst();
            if (ticket != null) size--;
            return ticket;
        }

        synchronized Ticket steal(int priority) {
            Ticket ticket = lanes[priority].pollLast();
            if (ticket != null) size--;
            return ticket;
        }

        synchronized boolean removeQueued(Ticket ticket) {
            if (ticket == null || ticket.state != TicketState.QUEUED) return false;
            if (!lanes[ticket.priority].remove(ticket)) return false;
            size--;
            return true;
        }

        synchronized int size() { return size; }

        synchronized int[] cancelQueued() {
            int[] cancelled = new int[PRIORITY_COUNT];
            for (int priority = 0; priority < PRIORITY_COUNT; priority++) {
                ArrayDeque<Ticket> lane = lanes[priority];
                Ticket ticket;
                while ((ticket = lane.pollFirst()) != null) {
                    ticket.cancellationRequested = true;
                    ticket.state = TicketState.CANCELLED;
                    ticket.endNs = System.nanoTime();
                    cancelled[priority]++;
                }
            }
            size = 0;
            return cancelled;
        }
    }

    private final class Worker implements Runnable {
        private final int index;
        private final WorkerQueue queue = new WorkerQueue();
        private final Thread thread;
        private final BakedSectionMesh.BuildScratch buildScratch = new BakedSectionMesh.BuildScratch();
        private final BinarySectionVisibility.BuildScratch visibilityScratch = new BinarySectionVisibility.BuildScratch();
        private final GreedySectionRectangles.BuildScratch rectangleScratch = new GreedySectionRectangles.BuildScratch();
        private final CanonicalFaceRenderKeys.BuildScratch renderKeyScratch = new CanonicalFaceRenderKeys.BuildScratch();
        private final RenderMergeCandidates.BuildScratch mergeCandidateScratch = new RenderMergeCandidates.BuildScratch();
        private final OrdinaryQuadEmissionSafety.BuildScratch emissionSafetyScratch = new OrdinaryQuadEmissionSafety.BuildScratch();
        private final RepeatAwareUvDescriptors.BuildScratch repeatAwareUvScratch = new RepeatAwareUvDescriptors.BuildScratch();
        private long completedLocalBuilds;
        private long lastFingerprint;

        Worker(int index) {
            this.index = index;
            this.thread = new Thread(this, "Obsidian-Mesh-" + index);
            this.thread.setDaemon(true);
        }

        void start() { thread.start(); }

        @Override
        public void run() {
            while (!closed) {
                Ticket ticket = takeNext(index);
                if (ticket == null) {
                    synchronized (signal) {
                        if (closed) break;
                        try {
                            signal.wait(IDLE_WAIT_MS);
                        } catch (InterruptedException ignored) {
                            if (closed) break;
                        }
                    }
                    continue;
                }
                execute(ticket);
            }
        }

        private void execute(Ticket ticket) {
            if (ticket.cancellationRequested || closed) {
                cancelTicket(ticket);
                return;
            }

            ticket.state = TicketState.RUNNING;
            ticket.startNs = System.nanoTime();
            runningJobs.incrementAndGet();
            startedJobs.incrementAndGet();
            startedByPriority.incrementAndGet(ticket.priority);
            long queueWait = ticket.queueWaitNs();
            updateMax(maxQueueWaitNs, queueWait);
            updateMax(maxQueueWaitByPriority, ticket.priority, queueWait);
            totalQueueWaitNs.addAndGet(queueWait);
            totalQueueWaitByPriority.addAndGet(ticket.priority, queueWait);

            try {
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                BinarySectionVisibility firstVisibility = BinarySectionVisibility.build(
                        ticket.snapshot, visibilityScratch);
                recordVisibilityScratchUse(visibilityScratch);
                visibilityBuilds.incrementAndGet();
                totalVisibilityFaces.addAndGet(firstVisibility.visibleFaceCount());
                totalVisibilityRetainedBytes.addAndGet(firstVisibility.retainedBytes());
                totalVisibilityBuildNs.addAndGet(firstVisibility.buildTimeNs());
                updateMax(maxVisibilityFaces, firstVisibility.visibleFaceCount());
                updateMax(maxVisibilityBuildNs, firstVisibility.buildTimeNs());
                for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
                    visibilityFacesByDirection.addAndGet(
                            direction, firstVisibility.directionFaceCount(direction));
                }
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                GreedySectionRectangles firstRectangles = GreedySectionRectangles.build(
                        firstVisibility, rectangleScratch);
                recordRectangleScratchUse(rectangleScratch);
                rectangleBuilds.incrementAndGet();
                totalRectangleCount.addAndGet(firstRectangles.rectangleCount());
                totalRectangleCoveredFaces.addAndGet(firstRectangles.coveredFaceCount());
                totalRectangleRetainedBytes.addAndGet(firstRectangles.retainedBytes());
                totalRectangleBuildNs.addAndGet(firstRectangles.buildTimeNs());
                updateMax(maxRectangleCount, firstRectangles.rectangleCount());
                updateMax(maxRectangleBuildNs, firstRectangles.buildTimeNs());
                rectangleMaskCoverageAudits.incrementAndGet();
                rectangleMaskCoverageAuditMatches.incrementAndGet();
                for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
                    rectangleCountsByDirection.addAndGet(
                            direction, firstRectangles.directionRectangleCount(direction));
                    rectangleCoveredFacesByDirection.addAndGet(
                            direction, firstRectangles.directionCoveredFaceCount(direction));
                }
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                CanonicalFaceRenderKeys firstRenderKeys = CanonicalFaceRenderKeys.build(
                        ticket.snapshot, firstVisibility, ticket.bakedSnapshot, renderKeyScratch);
                recordRenderKeyScratchUse(renderKeyScratch);
                renderKeyBuilds.incrementAndGet();
                totalRenderKeyVisibleFaces.addAndGet(firstRenderKeys.visibleFaces());
                totalRenderKeyEligibleFaces.addAndGet(firstRenderKeys.eligibleFaces());
                totalRenderKeyUnmappedFaces.addAndGet(firstRenderKeys.unmappedFaces());
                totalRenderKeyAmbiguousFaces.addAndGet(firstRenderKeys.ambiguousFaces());
                totalRenderKeyRecognizedCanonicalQuads.addAndGet(firstRenderKeys.recognizedCanonicalBakedQuads());
                totalRenderKeyIgnoredNoncanonicalQuads.addAndGet(firstRenderKeys.ignoredNoncanonicalBakedQuads());
                totalRenderKeySameAdjacencies.addAndGet(firstRenderKeys.sameKeyAdjacentPairs());
                totalRenderKeyDifferentAdjacencies.addAndGet(firstRenderKeys.differentKeyAdjacentPairs());
                totalRenderKeyIneligibleAdjacencies.addAndGet(firstRenderKeys.ineligibleAdjacentPairs());
                totalRenderKeyRetainedBytes.addAndGet(firstRenderKeys.retainedBytes());
                totalRenderKeyBuildNs.addAndGet(firstRenderKeys.buildTimeNs());
                updateMax(maxRenderKeyEligibleFaces, firstRenderKeys.eligibleFaces());
                updateMax(maxRenderKeyBuildNs, firstRenderKeys.buildTimeNs());
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                RenderMergeCandidates firstMergeCandidates = RenderMergeCandidates.build(
                        firstVisibility, firstRectangles, firstRenderKeys,
                        ticket.bakedSnapshot, mergeCandidateScratch);
                recordMergeCandidateScratchUse(mergeCandidateScratch);
                mergeCandidateBuilds.incrementAndGet();
                totalMergeCandidateCount.addAndGet(firstMergeCandidates.candidateCount());
                totalMergeCandidateCoveredEligibleFaces.addAndGet(firstMergeCandidates.coveredEligibleFaces());
                totalMergeCandidatePassthroughCanonicalFaces.addAndGet(firstMergeCandidates.passthroughCanonicalFaces());
                totalMergeCandidateSingletons.addAndGet(firstMergeCandidates.singletonCandidates());
                totalMergeCandidateMultiFace.addAndGet(firstMergeCandidates.multiFaceCandidates());
                totalMergeCandidateRetainedBytes.addAndGet(firstMergeCandidates.retainedBytes());
                totalMergeCandidateBuildNs.addAndGet(firstMergeCandidates.buildTimeNs());
                updateMax(maxMergeCandidateCount, firstMergeCandidates.candidateCount());
                updateMax(maxMergeCandidateBuildNs, firstMergeCandidates.buildTimeNs());
                mergeCandidateCoverageAudits.incrementAndGet();
                mergeCandidateCoverageAuditMatches.incrementAndGet();
                for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
                    mergeCandidateCountsByDirection.addAndGet(
                            direction, firstMergeCandidates.directionCandidateCount(direction));
                    mergeCandidateCoveredFacesByDirection.addAndGet(
                            direction, firstMergeCandidates.directionCoveredFaceCount(direction));
                }
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                OrdinaryQuadEmissionSafety firstEmissionSafety = OrdinaryQuadEmissionSafety.build(
                        firstMergeCandidates, firstRenderKeys, ticket.bakedSnapshot, emissionSafetyScratch);
                recordEmissionSafetyScratchUse(emissionSafetyScratch);
                emissionSafetyBuilds.incrementAndGet();
                totalEmissionSafetyCandidates.addAndGet(firstEmissionSafety.candidateCount());
                totalEmissionSafetySingletons.addAndGet(firstEmissionSafety.singletonCandidates());
                totalEmissionSafetyMultiFace.addAndGet(firstEmissionSafety.multiFaceCandidates());
                totalEmissionSafetyColorSafe.addAndGet(firstEmissionSafety.multiFaceColorSafe());
                totalEmissionSafetyColorUnsafe.addAndGet(firstEmissionSafety.multiFaceColorUnsafe());
                totalEmissionSafetyLightSafe.addAndGet(firstEmissionSafety.multiFaceLightSafe());
                totalEmissionSafetyLightUnsafe.addAndGet(firstEmissionSafety.multiFaceLightUnsafe());
                totalEmissionSafetyUvSafe.addAndGet(firstEmissionSafety.multiFaceUvSafe());
                totalEmissionSafetyUvUnsafe.addAndGet(firstEmissionSafety.multiFaceUvUnsafe());
                totalEmissionSafetyOrdinarySafe.addAndGet(firstEmissionSafety.multiFaceOrdinarySafe());
                totalEmissionSafetyOrdinaryUnsafe.addAndGet(firstEmissionSafety.multiFaceOrdinaryUnsafe());
                totalEmissionSafetyOrdinarySafeCoveredFaces.addAndGet(firstEmissionSafety.ordinarySafeCoveredFaces());
                totalEmissionSafetyOrdinarySafeFacesSaved.addAndGet(firstEmissionSafety.ordinarySafeFacesSaved());
                totalEmissionSafetyRetainedBytes.addAndGet(firstEmissionSafety.retainedBytes());
                totalEmissionSafetyBuildNs.addAndGet(firstEmissionSafety.buildTimeNs());
                updateMax(maxEmissionSafetyCandidates, firstEmissionSafety.candidateCount());
                updateMax(maxEmissionSafetyBuildNs, firstEmissionSafety.buildTimeNs());
                emissionSafetyClassificationAudits.incrementAndGet();
                emissionSafetyClassificationAuditMatches.incrementAndGet();
                for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
                    emissionSafetyOrdinarySafeByDirection.addAndGet(
                            direction, firstEmissionSafety.directionOrdinarySafeCount(direction));
                    emissionSafetyOrdinarySafeCoveredFacesByDirection.addAndGet(
                            direction, firstEmissionSafety.directionOrdinarySafeCoveredFaces(direction));
                }
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                RepeatAwareUvDescriptors firstRepeatAwareUv = RepeatAwareUvDescriptors.build(
                        firstMergeCandidates, firstRenderKeys, firstEmissionSafety,
                        ticket.bakedSnapshot, repeatAwareUvScratch);
                recordRepeatAwareUvScratchUse(repeatAwareUvScratch);
                repeatAwareUvBuilds.incrementAndGet();
                totalRepeatAwareUvMultiFace.addAndGet(firstRepeatAwareUv.sourceMultiFaceCandidates());
                totalRepeatAwareUvRepresentable.addAndGet(firstRepeatAwareUv.representableMultiFace());
                totalRepeatAwareUvUnrepresentable.addAndGet(firstRepeatAwareUv.unrepresentableMultiFace());
                totalRepeatAwareUvFourVertexSafe.addAndGet(firstRepeatAwareUv.repeatAwareFourVertexSafe());
                totalRepeatAwareUvFourVertexUnsafe.addAndGet(firstRepeatAwareUv.repeatAwareFourVertexUnsafe());
                totalRepeatAwareUvSafeCoveredFaces.addAndGet(firstRepeatAwareUv.safeCoveredFaces());
                totalRepeatAwareUvSafeFacesSaved.addAndGet(firstRepeatAwareUv.safeFacesSaved());
                totalRepeatAwareUvRetainedBytes.addAndGet(firstRepeatAwareUv.retainedBytes());
                totalRepeatAwareUvBuildNs.addAndGet(firstRepeatAwareUv.buildTimeNs());
                updateMax(maxRepeatAwareUvDescriptors, firstRepeatAwareUv.descriptorCount());
                updateMax(maxRepeatAwareUvBuildNs, firstRepeatAwareUv.buildTimeNs());
                repeatAwareUvClassificationAudits.incrementAndGet();
                repeatAwareUvClassificationAuditMatches.incrementAndGet();
                for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
                    repeatAwareUvRepresentableByDirection.addAndGet(
                            direction, firstRepeatAwareUv.directionRepresentableCount(direction));
                    repeatAwareUvSafeByDirection.addAndGet(
                            direction, firstRepeatAwareUv.directionSafeCount(direction));
                    repeatAwareUvSafeCoveredFacesByDirection.addAndGet(
                            direction, firstRepeatAwareUv.directionSafeCoveredFaces(direction));
                }
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                BakedSectionMesh first = BakedSectionMesh.build(
                        ticket.snapshot, ticket.bakedSnapshot, buildScratch);
                recordScratchUse(buildScratch);
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                boolean audit = completedLocalBuilds == 0L
                        || (completedLocalBuilds % DETERMINISM_AUDIT_INTERVAL) == 0L;
                if (audit) {
                    visibilityDeterminismAudits.incrementAndGet();
                    BinarySectionVisibility secondVisibility = BinarySectionVisibility.build(
                            ticket.snapshot, visibilityScratch);
                    recordVisibilityScratchUse(visibilityScratch);
                    if (!firstVisibility.contentEquals(secondVisibility)) {
                        throw new IllegalStateException("Worker-produced binary section visibility was nondeterministic");
                    }
                    visibilityDeterminismAuditMatches.incrementAndGet();

                    visibilityReferenceAudits.incrementAndGet();
                    ReferenceFaceMesh reference = ReferenceFaceMesh.build(ticket.snapshot);
                    reference.validateAgainst(ticket.snapshot);
                    firstVisibility.validateAgainst(reference);
                    visibilityReferenceAuditMatches.incrementAndGet();

                    rectangleDeterminismAudits.incrementAndGet();
                    GreedySectionRectangles secondRectangles = GreedySectionRectangles.build(
                            secondVisibility, rectangleScratch);
                    recordRectangleScratchUse(rectangleScratch);
                    if (!firstRectangles.contentEquals(secondRectangles)) {
                        throw new IllegalStateException("Worker-produced greedy rectangle sidecar was nondeterministic");
                    }
                    rectangleDeterminismAuditMatches.incrementAndGet();

                    rectangleReferenceAudits.incrementAndGet();
                    firstRectangles.validateAgainst(reference, rectangleScratch);
                    rectangleReferenceAuditMatches.incrementAndGet();

                    renderKeyDeterminismAudits.incrementAndGet();
                    CanonicalFaceRenderKeys secondRenderKeys = CanonicalFaceRenderKeys.build(
                            ticket.snapshot, secondVisibility, ticket.bakedSnapshot, renderKeyScratch);
                    recordRenderKeyScratchUse(renderKeyScratch);
                    if (!firstRenderKeys.contentEquals(secondRenderKeys)) {
                        throw new IllegalStateException("Worker-produced canonical render-key sidecar was nondeterministic");
                    }
                    renderKeyDeterminismAuditMatches.incrementAndGet();

                    mergeCandidateDeterminismAudits.incrementAndGet();
                    RenderMergeCandidates secondMergeCandidates = RenderMergeCandidates.build(
                            secondVisibility, secondRectangles, secondRenderKeys,
                            ticket.bakedSnapshot, mergeCandidateScratch);
                    recordMergeCandidateScratchUse(mergeCandidateScratch);
                    if (!firstMergeCandidates.contentEquals(secondMergeCandidates)) {
                        throw new IllegalStateException("Worker-produced render merge-candidate sidecar was nondeterministic");
                    }
                    mergeCandidateDeterminismAuditMatches.incrementAndGet();

                    emissionSafetyDeterminismAudits.incrementAndGet();
                    OrdinaryQuadEmissionSafety secondEmissionSafety = OrdinaryQuadEmissionSafety.build(
                            secondMergeCandidates, secondRenderKeys,
                            ticket.bakedSnapshot, emissionSafetyScratch);
                    recordEmissionSafetyScratchUse(emissionSafetyScratch);
                    if (!firstEmissionSafety.contentEquals(secondEmissionSafety)) {
                        throw new IllegalStateException("Worker-produced ordinary-quad emission-safety sidecar was nondeterministic");
                    }
                    emissionSafetyDeterminismAuditMatches.incrementAndGet();

                    repeatAwareUvDeterminismAudits.incrementAndGet();
                    RepeatAwareUvDescriptors secondRepeatAwareUv = RepeatAwareUvDescriptors.build(
                            secondMergeCandidates, secondRenderKeys, secondEmissionSafety,
                            ticket.bakedSnapshot, repeatAwareUvScratch);
                    recordRepeatAwareUvScratchUse(repeatAwareUvScratch);
                    if (!firstRepeatAwareUv.contentEquals(secondRepeatAwareUv)) {
                        throw new IllegalStateException("Worker-produced repeat-aware UV descriptor sidecar was nondeterministic");
                    }
                    repeatAwareUvDeterminismAuditMatches.incrementAndGet();

                    determinismAudits.incrementAndGet();
                    BakedSectionMesh second = BakedSectionMesh.build(
                            ticket.snapshot, ticket.bakedSnapshot, buildScratch);
                    recordScratchUse(buildScratch);
                    if (!first.contentEquals(second)) {
                        throw new IllegalStateException("Worker-produced section mesh was nondeterministic");
                    }
                    determinismAuditMatches.incrementAndGet();
                }
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                ticket.visibility = firstVisibility;
                ticket.rectangles = firstRectangles;
                ticket.renderKeys = firstRenderKeys;
                ticket.mergeCandidates = firstMergeCandidates;
                ticket.emissionSafety = firstEmissionSafety;
                ticket.repeatAwareUv = firstRepeatAwareUv;
                ticket.mesh = first;
                ticket.endNs = System.nanoTime();
                ticket.state = TicketState.COMPLETED;
                completedLocalBuilds++;
                lastFingerprint = first.fingerprint();
                completedJobs.incrementAndGet();
                completedByPriority.incrementAndGet(ticket.priority);
                totalOutputQuads.addAndGet(first.quadCount());
                totalOutputVertexBytes.addAndGet(first.vertexBytes());
                totalOutputIndexBytes.addAndGet(first.indexBytes());
                updateMax(maxOutputBytes, (long) first.vertexBytes() + first.indexBytes());
                long executionNs = ticket.executionNs();
                totalExecutionNs.addAndGet(executionNs);
                updateMax(maxExecutionNs, executionNs);
            } catch (Throwable t) {
                ticket.failure = t;
                ticket.endNs = System.nanoTime();
                ticket.state = TicketState.FAILED;
                failedJobs.incrementAndGet();
                failedByPriority.incrementAndGet(ticket.priority);
                LOG.log(System.Logger.Level.ERROR,
                        "Section mesh worker failed job " + ticket.id + ".", t);
            } finally {
                runningJobs.decrementAndGet();
            }
        }
    }

    private final Object signal = new Object();
    private final Worker[] workers;
    private final AtomicLong nextTicketId = new AtomicLong(1L);
    private final AtomicInteger submitCursor = new AtomicInteger();
    private final AtomicInteger runningJobs = new AtomicInteger();

    private final AtomicLong submittedJobs = new AtomicLong();
    private final AtomicLong queueFullRejections = new AtomicLong();
    private final AtomicLong startedJobs = new AtomicLong();
    private final AtomicLong completedJobs = new AtomicLong();
    private final AtomicLong cancelledJobs = new AtomicLong();
    private final AtomicLong failedJobs = new AtomicLong();
    private final AtomicLong stolenJobs = new AtomicLong();
    private final AtomicLong cancellationRequests = new AtomicLong();
    private final AtomicLong totalQueueWaitNs = new AtomicLong();
    private final AtomicLong maxQueueWaitNs = new AtomicLong();
    private final AtomicLong totalExecutionNs = new AtomicLong();
    private final AtomicLong maxExecutionNs = new AtomicLong();
    private final AtomicLong maxObservedQueueDepth = new AtomicLong();
    private final AtomicLong totalOutputQuads = new AtomicLong();
    private final AtomicLong totalOutputVertexBytes = new AtomicLong();
    private final AtomicLong totalOutputIndexBytes = new AtomicLong();
    private final AtomicLong maxOutputBytes = new AtomicLong();
    private final AtomicLong scratchBuildUses = new AtomicLong();
    private final AtomicLong maxScratchQuads = new AtomicLong();
    private final AtomicLong determinismAudits = new AtomicLong();
    private final AtomicLong determinismAuditMatches = new AtomicLong();

    private final AtomicLong visibilityBuilds = new AtomicLong();
    private final AtomicLong totalVisibilityFaces = new AtomicLong();
    private final AtomicLong maxVisibilityFaces = new AtomicLong();
    private final AtomicLong totalVisibilityRetainedBytes = new AtomicLong();
    private final AtomicLong totalVisibilityBuildNs = new AtomicLong();
    private final AtomicLong maxVisibilityBuildNs = new AtomicLong();
    private final AtomicLong visibilityScratchBuildUses = new AtomicLong();
    private final AtomicLong maxVisibilityScratchRows = new AtomicLong();
    private final AtomicLong visibilityDeterminismAudits = new AtomicLong();
    private final AtomicLong visibilityDeterminismAuditMatches = new AtomicLong();
    private final AtomicLong visibilityReferenceAudits = new AtomicLong();
    private final AtomicLong visibilityReferenceAuditMatches = new AtomicLong();

    private final AtomicLong rectangleBuilds = new AtomicLong();
    private final AtomicLong totalRectangleCount = new AtomicLong();
    private final AtomicLong totalRectangleCoveredFaces = new AtomicLong();
    private final AtomicLong totalRectangleRetainedBytes = new AtomicLong();
    private final AtomicLong totalRectangleBuildNs = new AtomicLong();
    private final AtomicLong maxRectangleBuildNs = new AtomicLong();
    private final AtomicLong maxRectangleCount = new AtomicLong();
    private final AtomicLong rectangleScratchBuildUses = new AtomicLong();
    private final AtomicLong maxRectangleScratchRectangles = new AtomicLong();
    private final AtomicLong rectangleMaskCoverageAudits = new AtomicLong();
    private final AtomicLong rectangleMaskCoverageAuditMatches = new AtomicLong();
    private final AtomicLong rectangleDeterminismAudits = new AtomicLong();
    private final AtomicLong rectangleDeterminismAuditMatches = new AtomicLong();
    private final AtomicLong rectangleReferenceAudits = new AtomicLong();
    private final AtomicLong rectangleReferenceAuditMatches = new AtomicLong();

    private final AtomicLong renderKeyBuilds = new AtomicLong();
    private final AtomicLong totalRenderKeyVisibleFaces = new AtomicLong();
    private final AtomicLong totalRenderKeyEligibleFaces = new AtomicLong();
    private final AtomicLong totalRenderKeyUnmappedFaces = new AtomicLong();
    private final AtomicLong totalRenderKeyAmbiguousFaces = new AtomicLong();
    private final AtomicLong totalRenderKeyRecognizedCanonicalQuads = new AtomicLong();
    private final AtomicLong totalRenderKeyIgnoredNoncanonicalQuads = new AtomicLong();
    private final AtomicLong totalRenderKeySameAdjacencies = new AtomicLong();
    private final AtomicLong totalRenderKeyDifferentAdjacencies = new AtomicLong();
    private final AtomicLong totalRenderKeyIneligibleAdjacencies = new AtomicLong();
    private final AtomicLong totalRenderKeyRetainedBytes = new AtomicLong();
    private final AtomicLong totalRenderKeyBuildNs = new AtomicLong();
    private final AtomicLong maxRenderKeyBuildNs = new AtomicLong();
    private final AtomicLong maxRenderKeyEligibleFaces = new AtomicLong();
    private final AtomicLong renderKeyScratchBuildUses = new AtomicLong();
    private final AtomicLong maxRenderKeyScratchEligibleFaces = new AtomicLong();
    private final AtomicLong renderKeyDeterminismAudits = new AtomicLong();
    private final AtomicLong renderKeyDeterminismAuditMatches = new AtomicLong();

    private final AtomicLong mergeCandidateBuilds = new AtomicLong();
    private final AtomicLong totalMergeCandidateCount = new AtomicLong();
    private final AtomicLong totalMergeCandidateCoveredEligibleFaces = new AtomicLong();
    private final AtomicLong totalMergeCandidatePassthroughCanonicalFaces = new AtomicLong();
    private final AtomicLong totalMergeCandidateSingletons = new AtomicLong();
    private final AtomicLong totalMergeCandidateMultiFace = new AtomicLong();
    private final AtomicLong totalMergeCandidateRetainedBytes = new AtomicLong();
    private final AtomicLong totalMergeCandidateBuildNs = new AtomicLong();
    private final AtomicLong maxMergeCandidateBuildNs = new AtomicLong();
    private final AtomicLong maxMergeCandidateCount = new AtomicLong();
    private final AtomicLong mergeCandidateScratchBuildUses = new AtomicLong();
    private final AtomicLong maxMergeCandidateScratchCandidates = new AtomicLong();
    private final AtomicLong mergeCandidateCoverageAudits = new AtomicLong();
    private final AtomicLong mergeCandidateCoverageAuditMatches = new AtomicLong();
    private final AtomicLong mergeCandidateDeterminismAudits = new AtomicLong();
    private final AtomicLong mergeCandidateDeterminismAuditMatches = new AtomicLong();

    private final AtomicLong emissionSafetyBuilds = new AtomicLong();
    private final AtomicLong totalEmissionSafetyCandidates = new AtomicLong();
    private final AtomicLong totalEmissionSafetySingletons = new AtomicLong();
    private final AtomicLong totalEmissionSafetyMultiFace = new AtomicLong();
    private final AtomicLong totalEmissionSafetyColorSafe = new AtomicLong();
    private final AtomicLong totalEmissionSafetyColorUnsafe = new AtomicLong();
    private final AtomicLong totalEmissionSafetyLightSafe = new AtomicLong();
    private final AtomicLong totalEmissionSafetyLightUnsafe = new AtomicLong();
    private final AtomicLong totalEmissionSafetyUvSafe = new AtomicLong();
    private final AtomicLong totalEmissionSafetyUvUnsafe = new AtomicLong();
    private final AtomicLong totalEmissionSafetyOrdinarySafe = new AtomicLong();
    private final AtomicLong totalEmissionSafetyOrdinaryUnsafe = new AtomicLong();
    private final AtomicLong totalEmissionSafetyOrdinarySafeCoveredFaces = new AtomicLong();
    private final AtomicLong totalEmissionSafetyOrdinarySafeFacesSaved = new AtomicLong();
    private final AtomicLong totalEmissionSafetyRetainedBytes = new AtomicLong();
    private final AtomicLong totalEmissionSafetyBuildNs = new AtomicLong();
    private final AtomicLong maxEmissionSafetyBuildNs = new AtomicLong();
    private final AtomicLong maxEmissionSafetyCandidates = new AtomicLong();
    private final AtomicLong emissionSafetyScratchBuildUses = new AtomicLong();
    private final AtomicLong maxEmissionSafetyScratchCandidates = new AtomicLong();
    private final AtomicLong emissionSafetyClassificationAudits = new AtomicLong();
    private final AtomicLong emissionSafetyClassificationAuditMatches = new AtomicLong();
    private final AtomicLong emissionSafetyDeterminismAudits = new AtomicLong();
    private final AtomicLong emissionSafetyDeterminismAuditMatches = new AtomicLong();

    private final AtomicLong repeatAwareUvBuilds = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvMultiFace = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvRepresentable = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvUnrepresentable = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvFourVertexSafe = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvFourVertexUnsafe = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvSafeCoveredFaces = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvSafeFacesSaved = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvRetainedBytes = new AtomicLong();
    private final AtomicLong totalRepeatAwareUvBuildNs = new AtomicLong();
    private final AtomicLong maxRepeatAwareUvBuildNs = new AtomicLong();
    private final AtomicLong maxRepeatAwareUvDescriptors = new AtomicLong();
    private final AtomicLong repeatAwareUvScratchBuildUses = new AtomicLong();
    private final AtomicLong maxRepeatAwareUvScratchDescriptors = new AtomicLong();
    private final AtomicLong repeatAwareUvClassificationAudits = new AtomicLong();
    private final AtomicLong repeatAwareUvClassificationAuditMatches = new AtomicLong();
    private final AtomicLong repeatAwareUvDeterminismAudits = new AtomicLong();
    private final AtomicLong repeatAwareUvDeterminismAuditMatches = new AtomicLong();

    private final AtomicLong shutdownJoinFailures = new AtomicLong();

    private final AtomicLongArray submittedByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray startedByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray completedByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray cancelledByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray failedByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray queueFullByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray cancellationRequestsByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray totalQueueWaitByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray maxQueueWaitByPriority = new AtomicLongArray(PRIORITY_COUNT);
    private final AtomicLongArray visibilityFacesByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray rectangleCountsByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray rectangleCoveredFacesByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray mergeCandidateCountsByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray mergeCandidateCoveredFacesByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray emissionSafetyOrdinarySafeByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray emissionSafetyOrdinarySafeCoveredFacesByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray repeatAwareUvRepresentableByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray repeatAwareUvSafeByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray repeatAwareUvSafeCoveredFacesByDirection =
            new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);

    private volatile boolean closed;

    public SectionMeshWorkerPool(int workerCount) {
        if (workerCount <= 0 || workerCount > MAX_WORKERS) {
            throw new IllegalArgumentException("workerCount must be between 1 and " + MAX_WORKERS);
        }
        workers = new Worker[workerCount];
        for (int i = 0; i < workerCount; i++) workers[i] = new Worker(i);
        for (Worker worker : workers) worker.start();
    }

    public static int defaultWorkerCount() {
        int available = Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(MAX_WORKERS, available - 2));
    }

    public Ticket submit(
            long generation,
            long eventSequence,
            int priority,
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot bakedSnapshot) {
        if (closed) throw new IllegalStateException("Section mesh worker pool is closed");
        validateJob(priority, snapshot, bakedSnapshot);

        int start = Math.floorMod(submitCursor.getAndIncrement(), workers.length);
        int chosen = -1;
        int chosenDepth = Integer.MAX_VALUE;
        for (int i = 0; i < workers.length; i++) {
            int index = (start + i) % workers.length;
            int depth = workers[index].queue.size();
            if (depth < chosenDepth) {
                chosen = index;
                chosenDepth = depth;
            }
        }
        return enqueue(chosen, generation, eventSequence, priority, snapshot, bakedSnapshot);
    }

    Ticket submitPinnedForValidation(
            int workerIndex,
            long generation,
            long eventSequence,
            int priority,
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot bakedSnapshot) {
        if (closed) throw new IllegalStateException("Section mesh worker pool is closed");
        validateJob(priority, snapshot, bakedSnapshot);
        if (workerIndex < 0 || workerIndex >= workers.length) {
            throw new IllegalArgumentException("Invalid worker index");
        }
        return enqueue(workerIndex, generation, eventSequence, priority, snapshot, bakedSnapshot);
    }

    public void cancel(Ticket ticket) {
        if (ticket == null || ticket.terminal()) return;
        cancellationRequests.incrementAndGet();
        cancellationRequestsByPriority.incrementAndGet(ticket.priority);
        ticket.cancellationRequested = true;

        for (Worker worker : workers) {
            if (worker.queue.removeQueued(ticket)) {
                ticket.endNs = System.nanoTime();
                ticket.state = TicketState.CANCELLED;
                cancelledJobs.incrementAndGet();
                cancelledByPriority.incrementAndGet(ticket.priority);
                synchronized (signal) { signal.notifyAll(); }
                return;
            }
        }
        synchronized (signal) { signal.notifyAll(); }
    }

    private Ticket enqueue(
            int workerIndex,
            long generation,
            long eventSequence,
            int priority,
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot bakedSnapshot) {
        Ticket ticket = new Ticket(
                nextTicketId.getAndIncrement(), generation, eventSequence, priority, snapshot, bakedSnapshot);
        if (!workers[workerIndex].queue.offer(ticket)) {
            queueFullRejections.incrementAndGet();
            queueFullByPriority.incrementAndGet(priority);
            return null;
        }
        submittedJobs.incrementAndGet();
        submittedByPriority.incrementAndGet(priority);
        updateMax(maxObservedQueueDepth, totalQueuedJobs());
        synchronized (signal) { signal.notifyAll(); }
        return ticket;
    }

    private Ticket takeNext(int workerIndex) {
        Worker own = workers[workerIndex];
        for (int priority = PRIORITY_HIGH; priority <= PRIORITY_LOW; priority++) {
            Ticket ticket = own.queue.poll(priority);
            if (ticket != null) return ticket;
            ticket = stealFor(workerIndex, priority);
            if (ticket != null) return ticket;
        }
        return null;
    }

    private Ticket stealFor(int thiefIndex, int priority) {
        for (int offset = 1; offset < workers.length; offset++) {
            int victim = (thiefIndex + offset) % workers.length;
            Ticket ticket = workers[victim].queue.steal(priority);
            if (ticket != null) {
                ticket.stolen = true;
                stolenJobs.incrementAndGet();
                return ticket;
            }
        }
        return null;
    }

    private void cancelTicket(Ticket ticket) {
        if (ticket.state == TicketState.CANCELLED) return;
        ticket.endNs = System.nanoTime();
        ticket.state = TicketState.CANCELLED;
        cancelledJobs.incrementAndGet();
        cancelledByPriority.incrementAndGet(ticket.priority);
    }

    private void recordScratchUse(BakedSectionMesh.BuildScratch scratch) {
        scratchBuildUses.incrementAndGet();
        updateMax(maxScratchQuads, scratch.highWaterQuads());
    }

    private void recordVisibilityScratchUse(BinarySectionVisibility.BuildScratch scratch) {
        visibilityScratchBuildUses.incrementAndGet();
        updateMax(maxVisibilityScratchRows, scratch.highWaterSupportedRows());
    }

    private void recordRectangleScratchUse(GreedySectionRectangles.BuildScratch scratch) {
        rectangleScratchBuildUses.incrementAndGet();
        updateMax(maxRectangleScratchRectangles, scratch.highWaterRectangles());
    }

    private void recordRenderKeyScratchUse(CanonicalFaceRenderKeys.BuildScratch scratch) {
        renderKeyScratchBuildUses.incrementAndGet();
        updateMax(maxRenderKeyScratchEligibleFaces, scratch.highWaterEligibleFaces());
    }

    private void recordMergeCandidateScratchUse(RenderMergeCandidates.BuildScratch scratch) {
        mergeCandidateScratchBuildUses.incrementAndGet();
        updateMax(maxMergeCandidateScratchCandidates, scratch.highWaterCandidates());
    }

    private void recordEmissionSafetyScratchUse(OrdinaryQuadEmissionSafety.BuildScratch scratch) {
        emissionSafetyScratchBuildUses.incrementAndGet();
        updateMax(maxEmissionSafetyScratchCandidates, scratch.highWaterCandidates());
    }

    private void recordRepeatAwareUvScratchUse(RepeatAwareUvDescriptors.BuildScratch scratch) {
        repeatAwareUvScratchBuildUses.incrementAndGet();
        updateMax(maxRepeatAwareUvScratchDescriptors, scratch.highWaterDescriptors());
    }

    private static void validateJob(
            int priority,
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot bakedSnapshot) {
        validatePriority(priority);
        if (snapshot == null || bakedSnapshot == null) {
            throw new NullPointerException("snapshot and bakedSnapshot are required");
        }
        if (snapshot.sectionX() != bakedSnapshot.sectionX()
                || snapshot.sectionY() != bakedSnapshot.sectionY()
                || snapshot.sectionZ() != bakedSnapshot.sectionZ()
                || snapshot.fingerprint() != bakedSnapshot.sourceSnapshotFingerprint()) {
            throw new IllegalArgumentException("Worker mesh job snapshot identity mismatch");
        }
    }

    private static void validatePriority(int priority) {
        if (priority < PRIORITY_HIGH || priority > PRIORITY_LOW) {
            throw new IllegalArgumentException("Invalid mesh job priority");
        }
    }

    private int totalQueuedJobs() {
        int total = 0;
        for (Worker worker : workers) total += worker.queue.size();
        return total;
    }

    private static void updateMax(AtomicLong target, long value) {
        long previous;
        do {
            previous = target.get();
            if (value <= previous) return;
        } while (!target.compareAndSet(previous, value));
    }

    private static void updateMax(AtomicLongArray target, int index, long value) {
        long previous;
        do {
            previous = target.get(index);
            if (value <= previous) return;
        } while (!target.compareAndSet(previous, value));
    }

    public static String priorityName(int priority) {
        return switch (priority) {
            case PRIORITY_HIGH -> "HIGH";
            case PRIORITY_NORMAL -> "NORMAL";
            case PRIORITY_LOW -> "LOW";
            default -> "UNKNOWN";
        };
    }

    public int workerCount() { return workers.length; }
    public int queueCapacity() { return workers.length * QUEUE_CAPACITY_PER_WORKER; }
    public int queuedJobs() { return totalQueuedJobs(); }
    public int runningJobs() { return runningJobs.get(); }
    public int outstandingJobs() { return queuedJobs() + runningJobs(); }
    public long submittedJobs() { return submittedJobs.get(); }
    public long queueFullRejections() { return queueFullRejections.get(); }
    public long startedJobs() { return startedJobs.get(); }
    public long completedJobs() { return completedJobs.get(); }
    public long cancelledJobs() { return cancelledJobs.get(); }
    public long failedJobs() { return failedJobs.get(); }
    public long stolenJobs() { return stolenJobs.get(); }
    public long cancellationRequests() { return cancellationRequests.get(); }
    public long totalQueueWaitNs() { return totalQueueWaitNs.get(); }
    public long maxQueueWaitNs() { return maxQueueWaitNs.get(); }
    public long totalExecutionNs() { return totalExecutionNs.get(); }
    public long maxExecutionNs() { return maxExecutionNs.get(); }
    public long maxObservedQueueDepth() { return maxObservedQueueDepth.get(); }
    public long totalOutputQuads() { return totalOutputQuads.get(); }
    public long totalOutputVertexBytes() { return totalOutputVertexBytes.get(); }
    public long totalOutputIndexBytes() { return totalOutputIndexBytes.get(); }
    public long maxOutputBytes() { return maxOutputBytes.get(); }
    public long scratchBuildUses() { return scratchBuildUses.get(); }
    public long maxScratchQuads() { return maxScratchQuads.get(); }
    public long determinismAudits() { return determinismAudits.get(); }
    public long determinismAuditMatches() { return determinismAuditMatches.get(); }

    public long visibilityBuilds() { return visibilityBuilds.get(); }
    public long totalVisibilityFaces() { return totalVisibilityFaces.get(); }
    public long maxVisibilityFaces() { return maxVisibilityFaces.get(); }
    public long totalVisibilityRetainedBytes() { return totalVisibilityRetainedBytes.get(); }
    public long totalVisibilityBuildNs() { return totalVisibilityBuildNs.get(); }
    public long maxVisibilityBuildNs() { return maxVisibilityBuildNs.get(); }
    public long visibilityScratchBuildUses() { return visibilityScratchBuildUses.get(); }
    public long maxVisibilityScratchRows() { return maxVisibilityScratchRows.get(); }
    public long visibilityDeterminismAudits() { return visibilityDeterminismAudits.get(); }
    public long visibilityDeterminismAuditMatches() { return visibilityDeterminismAuditMatches.get(); }
    public long visibilityReferenceAudits() { return visibilityReferenceAudits.get(); }
    public long visibilityReferenceAuditMatches() { return visibilityReferenceAuditMatches.get(); }
    public long visibilityFaces(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid visibility direction");
        }
        return visibilityFacesByDirection.get(direction);
    }

    public long rectangleBuilds() { return rectangleBuilds.get(); }
    public long totalRectangleCount() { return totalRectangleCount.get(); }
    public long totalRectangleCoveredFaces() { return totalRectangleCoveredFaces.get(); }
    public long totalRectangleRetainedBytes() { return totalRectangleRetainedBytes.get(); }
    public long totalRectangleBuildNs() { return totalRectangleBuildNs.get(); }
    public long maxRectangleBuildNs() { return maxRectangleBuildNs.get(); }
    public long maxRectangleCount() { return maxRectangleCount.get(); }
    public long rectangleScratchBuildUses() { return rectangleScratchBuildUses.get(); }
    public long maxRectangleScratchRectangles() { return maxRectangleScratchRectangles.get(); }
    public long rectangleMaskCoverageAudits() { return rectangleMaskCoverageAudits.get(); }
    public long rectangleMaskCoverageAuditMatches() { return rectangleMaskCoverageAuditMatches.get(); }
    public long rectangleDeterminismAudits() { return rectangleDeterminismAudits.get(); }
    public long rectangleDeterminismAuditMatches() { return rectangleDeterminismAuditMatches.get(); }
    public long rectangleReferenceAudits() { return rectangleReferenceAudits.get(); }
    public long rectangleReferenceAuditMatches() { return rectangleReferenceAuditMatches.get(); }
    public long rectangles(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid rectangle direction");
        }
        return rectangleCountsByDirection.get(direction);
    }
    public long rectangleCoveredFaces(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid rectangle direction");
        }
        return rectangleCoveredFacesByDirection.get(direction);
    }

    public long renderKeyBuilds() { return renderKeyBuilds.get(); }
    public long totalRenderKeyVisibleFaces() { return totalRenderKeyVisibleFaces.get(); }
    public long totalRenderKeyEligibleFaces() { return totalRenderKeyEligibleFaces.get(); }
    public long totalRenderKeyUnmappedFaces() { return totalRenderKeyUnmappedFaces.get(); }
    public long totalRenderKeyAmbiguousFaces() { return totalRenderKeyAmbiguousFaces.get(); }
    public long totalRenderKeyRecognizedCanonicalQuads() { return totalRenderKeyRecognizedCanonicalQuads.get(); }
    public long totalRenderKeyIgnoredNoncanonicalQuads() { return totalRenderKeyIgnoredNoncanonicalQuads.get(); }
    public long totalRenderKeySameAdjacencies() { return totalRenderKeySameAdjacencies.get(); }
    public long totalRenderKeyDifferentAdjacencies() { return totalRenderKeyDifferentAdjacencies.get(); }
    public long totalRenderKeyIneligibleAdjacencies() { return totalRenderKeyIneligibleAdjacencies.get(); }
    public long totalRenderKeyRetainedBytes() { return totalRenderKeyRetainedBytes.get(); }
    public long totalRenderKeyBuildNs() { return totalRenderKeyBuildNs.get(); }
    public long maxRenderKeyBuildNs() { return maxRenderKeyBuildNs.get(); }
    public long maxRenderKeyEligibleFaces() { return maxRenderKeyEligibleFaces.get(); }
    public long renderKeyScratchBuildUses() { return renderKeyScratchBuildUses.get(); }
    public long maxRenderKeyScratchEligibleFaces() { return maxRenderKeyScratchEligibleFaces.get(); }
    public long renderKeyDeterminismAudits() { return renderKeyDeterminismAudits.get(); }
    public long renderKeyDeterminismAuditMatches() { return renderKeyDeterminismAuditMatches.get(); }

    public long mergeCandidateBuilds() { return mergeCandidateBuilds.get(); }
    public long totalMergeCandidateCount() { return totalMergeCandidateCount.get(); }
    public long totalMergeCandidateCoveredEligibleFaces() { return totalMergeCandidateCoveredEligibleFaces.get(); }
    public long totalMergeCandidatePassthroughCanonicalFaces() { return totalMergeCandidatePassthroughCanonicalFaces.get(); }
    public long totalMergeCandidateSingletons() { return totalMergeCandidateSingletons.get(); }
    public long totalMergeCandidateMultiFace() { return totalMergeCandidateMultiFace.get(); }
    public long totalMergeCandidateRetainedBytes() { return totalMergeCandidateRetainedBytes.get(); }
    public long totalMergeCandidateBuildNs() { return totalMergeCandidateBuildNs.get(); }
    public long maxMergeCandidateBuildNs() { return maxMergeCandidateBuildNs.get(); }
    public long maxMergeCandidateCount() { return maxMergeCandidateCount.get(); }
    public long mergeCandidateScratchBuildUses() { return mergeCandidateScratchBuildUses.get(); }
    public long maxMergeCandidateScratchCandidates() { return maxMergeCandidateScratchCandidates.get(); }
    public long mergeCandidateCoverageAudits() { return mergeCandidateCoverageAudits.get(); }
    public long mergeCandidateCoverageAuditMatches() { return mergeCandidateCoverageAuditMatches.get(); }
    public long mergeCandidateDeterminismAudits() { return mergeCandidateDeterminismAudits.get(); }
    public long mergeCandidateDeterminismAuditMatches() { return mergeCandidateDeterminismAuditMatches.get(); }
    public long mergeCandidates(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid merge-candidate direction");
        }
        return mergeCandidateCountsByDirection.get(direction);
    }
    public long mergeCandidateCoveredFaces(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid merge-candidate direction");
        }
        return mergeCandidateCoveredFacesByDirection.get(direction);
    }

    public long emissionSafetyBuilds() { return emissionSafetyBuilds.get(); }
    public long totalEmissionSafetyCandidates() { return totalEmissionSafetyCandidates.get(); }
    public long totalEmissionSafetySingletons() { return totalEmissionSafetySingletons.get(); }
    public long totalEmissionSafetyMultiFace() { return totalEmissionSafetyMultiFace.get(); }
    public long totalEmissionSafetyColorSafe() { return totalEmissionSafetyColorSafe.get(); }
    public long totalEmissionSafetyColorUnsafe() { return totalEmissionSafetyColorUnsafe.get(); }
    public long totalEmissionSafetyLightSafe() { return totalEmissionSafetyLightSafe.get(); }
    public long totalEmissionSafetyLightUnsafe() { return totalEmissionSafetyLightUnsafe.get(); }
    public long totalEmissionSafetyUvSafe() { return totalEmissionSafetyUvSafe.get(); }
    public long totalEmissionSafetyUvUnsafe() { return totalEmissionSafetyUvUnsafe.get(); }
    public long totalEmissionSafetyOrdinarySafe() { return totalEmissionSafetyOrdinarySafe.get(); }
    public long totalEmissionSafetyOrdinaryUnsafe() { return totalEmissionSafetyOrdinaryUnsafe.get(); }
    public long totalEmissionSafetyOrdinarySafeCoveredFaces() { return totalEmissionSafetyOrdinarySafeCoveredFaces.get(); }
    public long totalEmissionSafetyOrdinarySafeFacesSaved() { return totalEmissionSafetyOrdinarySafeFacesSaved.get(); }
    public long totalEmissionSafetyRetainedBytes() { return totalEmissionSafetyRetainedBytes.get(); }
    public long totalEmissionSafetyBuildNs() { return totalEmissionSafetyBuildNs.get(); }
    public long maxEmissionSafetyBuildNs() { return maxEmissionSafetyBuildNs.get(); }
    public long maxEmissionSafetyCandidates() { return maxEmissionSafetyCandidates.get(); }
    public long emissionSafetyScratchBuildUses() { return emissionSafetyScratchBuildUses.get(); }
    public long maxEmissionSafetyScratchCandidates() { return maxEmissionSafetyScratchCandidates.get(); }
    public long emissionSafetyClassificationAudits() { return emissionSafetyClassificationAudits.get(); }
    public long emissionSafetyClassificationAuditMatches() { return emissionSafetyClassificationAuditMatches.get(); }
    public long emissionSafetyDeterminismAudits() { return emissionSafetyDeterminismAudits.get(); }
    public long emissionSafetyDeterminismAuditMatches() { return emissionSafetyDeterminismAuditMatches.get(); }
    public long emissionSafetyOrdinarySafe(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid emission-safety direction");
        }
        return emissionSafetyOrdinarySafeByDirection.get(direction);
    }
    public long emissionSafetyOrdinarySafeCoveredFaces(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid emission-safety direction");
        }
        return emissionSafetyOrdinarySafeCoveredFacesByDirection.get(direction);
    }

    public long repeatAwareUvBuilds() { return repeatAwareUvBuilds.get(); }
    public long totalRepeatAwareUvMultiFace() { return totalRepeatAwareUvMultiFace.get(); }
    public long totalRepeatAwareUvRepresentable() { return totalRepeatAwareUvRepresentable.get(); }
    public long totalRepeatAwareUvUnrepresentable() { return totalRepeatAwareUvUnrepresentable.get(); }
    public long totalRepeatAwareUvFourVertexSafe() { return totalRepeatAwareUvFourVertexSafe.get(); }
    public long totalRepeatAwareUvFourVertexUnsafe() { return totalRepeatAwareUvFourVertexUnsafe.get(); }
    public long totalRepeatAwareUvSafeCoveredFaces() { return totalRepeatAwareUvSafeCoveredFaces.get(); }
    public long totalRepeatAwareUvSafeFacesSaved() { return totalRepeatAwareUvSafeFacesSaved.get(); }
    public long totalRepeatAwareUvRetainedBytes() { return totalRepeatAwareUvRetainedBytes.get(); }
    public long totalRepeatAwareUvBuildNs() { return totalRepeatAwareUvBuildNs.get(); }
    public long maxRepeatAwareUvBuildNs() { return maxRepeatAwareUvBuildNs.get(); }
    public long maxRepeatAwareUvDescriptors() { return maxRepeatAwareUvDescriptors.get(); }
    public long repeatAwareUvScratchBuildUses() { return repeatAwareUvScratchBuildUses.get(); }
    public long maxRepeatAwareUvScratchDescriptors() { return maxRepeatAwareUvScratchDescriptors.get(); }
    public long repeatAwareUvClassificationAudits() { return repeatAwareUvClassificationAudits.get(); }
    public long repeatAwareUvClassificationAuditMatches() { return repeatAwareUvClassificationAuditMatches.get(); }
    public long repeatAwareUvDeterminismAudits() { return repeatAwareUvDeterminismAudits.get(); }
    public long repeatAwareUvDeterminismAuditMatches() { return repeatAwareUvDeterminismAuditMatches.get(); }
    public long repeatAwareUvRepresentable(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid repeat-aware UV direction");
        }
        return repeatAwareUvRepresentableByDirection.get(direction);
    }
    public long repeatAwareUvSafe(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid repeat-aware UV direction");
        }
        return repeatAwareUvSafeByDirection.get(direction);
    }
    public long repeatAwareUvSafeCoveredFaces(int direction) {
        if (direction < 0 || direction >= BinarySectionVisibility.DIRECTION_COUNT) {
            throw new IllegalArgumentException("Invalid repeat-aware UV direction");
        }
        return repeatAwareUvSafeCoveredFacesByDirection.get(direction);
    }

    public long shutdownJoinFailures() { return shutdownJoinFailures.get(); }

    public long submittedJobs(int priority) { validatePriority(priority); return submittedByPriority.get(priority); }
    public long startedJobs(int priority) { validatePriority(priority); return startedByPriority.get(priority); }
    public long completedJobs(int priority) { validatePriority(priority); return completedByPriority.get(priority); }
    public long cancelledJobs(int priority) { validatePriority(priority); return cancelledByPriority.get(priority); }
    public long failedJobs(int priority) { validatePriority(priority); return failedByPriority.get(priority); }
    public long queueFullRejections(int priority) { validatePriority(priority); return queueFullByPriority.get(priority); }
    public long cancellationRequests(int priority) { validatePriority(priority); return cancellationRequestsByPriority.get(priority); }
    public long totalQueueWaitNs(int priority) { validatePriority(priority); return totalQueueWaitByPriority.get(priority); }
    public long maxQueueWaitNs(int priority) { validatePriority(priority); return maxQueueWaitByPriority.get(priority); }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Worker worker : workers) {
            int[] cancelled = worker.queue.cancelQueued();
            for (int priority = 0; priority < PRIORITY_COUNT; priority++) {
                if (cancelled[priority] == 0) continue;
                cancelledJobs.addAndGet(cancelled[priority]);
                cancelledByPriority.addAndGet(priority, cancelled[priority]);
            }
        }
        synchronized (signal) { signal.notifyAll(); }
        for (Worker worker : workers) worker.thread.interrupt();
        for (Worker worker : workers) {
            try {
                worker.thread.join(SHUTDOWN_JOIN_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (worker.thread.isAlive()) {
                shutdownJoinFailures.incrementAndGet();
                LOG.log(System.Logger.Level.WARNING,
                        "Mesh worker {0} did not stop inside the bounded shutdown join.", worker.index);
            }
        }
    }
}
