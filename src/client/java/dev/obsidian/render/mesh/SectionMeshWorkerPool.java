package dev.obsidian.render.mesh;

import dev.obsidian.render.terrain.BakedSectionMesh;
import dev.obsidian.render.terrain.BinarySectionVisibility;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
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
 * lower relevance. P3.2 additionally builds a compact binary directional-face
 * visibility sidecar for every production job. No worker touches Minecraft
 * world/chunk/model state or GPU objects.</p>
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

    /** Validation-only pinned submit retained for the historical dev1 proof. */
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
        } while (!target.compareAndSet(index, previous, value));
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
