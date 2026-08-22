package dev.obsidian.render.mesh;

import dev.obsidian.render.terrain.BakedSectionMesh;
import dev.obsidian.render.terrain.SectionBakedQuadSnapshot;
import dev.obsidian.render.terrain.SectionSnapshot;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded dedicated worker pool for pure section mesh construction.
 *
 * <p>Jobs contain only immutable renderer-owned Phase 2 snapshots. There are
 * three bounded priority lanes per worker and idle workers steal from peers.
 * No worker touches Minecraft world/chunk/model state or GPU objects.</p>
 */
public final class SectionMeshWorkerPool implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/SectionMeshWorkers");

    public static final int PRIORITY_HIGH = 0;
    public static final int PRIORITY_NORMAL = 1;
    public static final int PRIORITY_LOW = 2;

    private static final int MAX_WORKERS = 4;
    private static final int QUEUE_CAPACITY_PER_WORKER = 16;
    private static final long IDLE_WAIT_MS = 4L;
    private static final long SHUTDOWN_JOIN_MS = 500L;

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

        synchronized Ticket pollOwn() {
            for (ArrayDeque<Ticket> lane : lanes) {
                Ticket ticket = lane.pollFirst();
                if (ticket != null) {
                    size--;
                    return ticket;
                }
            }
            return null;
        }

        synchronized Ticket steal() {
            for (ArrayDeque<Ticket> lane : lanes) {
                Ticket ticket = lane.pollLast();
                if (ticket != null) {
                    size--;
                    return ticket;
                }
            }
            return null;
        }

        synchronized int size() { return size; }

        synchronized void cancelQueued() {
            for (ArrayDeque<Ticket> lane : lanes) {
                Ticket ticket;
                while ((ticket = lane.pollFirst()) != null) {
                    ticket.cancellationRequested = true;
                    ticket.state = TicketState.CANCELLED;
                    ticket.endNs = System.nanoTime();
                }
            }
            size = 0;
        }
    }

    private final class Worker implements Runnable {
        private final int index;
        private final WorkerQueue queue = new WorkerQueue();
        private final Thread thread;
        private long localBuilds;
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
                Ticket ticket = queue.pollOwn();
                if (ticket == null) {
                    ticket = stealFor(index);
                }
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
            updateMax(maxQueueWaitNs, ticket.queueWaitNs());
            totalQueueWaitNs.addAndGet(ticket.queueWaitNs());

            try {
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }
                BakedSectionMesh first = BakedSectionMesh.build(ticket.snapshot, ticket.bakedSnapshot);
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }
                BakedSectionMesh second = BakedSectionMesh.build(ticket.snapshot, ticket.bakedSnapshot);
                if (!first.contentEquals(second)) {
                    throw new IllegalStateException("Worker-produced section mesh was nondeterministic");
                }
                if (ticket.cancellationRequested || closed) {
                    cancelTicket(ticket);
                    return;
                }

                ticket.mesh = first;
                ticket.endNs = System.nanoTime();
                ticket.state = TicketState.COMPLETED;
                localBuilds++;
                lastFingerprint = first.fingerprint();
                completedJobs.incrementAndGet();
                long executionNs = ticket.executionNs();
                totalExecutionNs.addAndGet(executionNs);
                updateMax(maxExecutionNs, executionNs);
            } catch (Throwable t) {
                ticket.failure = t;
                ticket.endNs = System.nanoTime();
                ticket.state = TicketState.FAILED;
                failedJobs.incrementAndGet();
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

    /** Validation-only pinned submit used to make work stealing deterministic in the P3.1 proof. */
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
        ticket.cancellationRequested = true;
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
            return null;
        }
        submittedJobs.incrementAndGet();
        updateMax(maxObservedQueueDepth, totalQueuedJobs());
        synchronized (signal) { signal.notifyAll(); }
        return ticket;
    }

    private Ticket stealFor(int thiefIndex) {
        for (int offset = 1; offset < workers.length; offset++) {
            int victim = (thiefIndex + offset) % workers.length;
            Ticket ticket = workers[victim].queue.steal();
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
    }

    private static void validateJob(
            int priority,
            SectionSnapshot snapshot,
            SectionBakedQuadSnapshot bakedSnapshot) {
        if (priority < PRIORITY_HIGH || priority > PRIORITY_LOW) {
            throw new IllegalArgumentException("Invalid mesh job priority");
        }
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

    public int workerCount() { return workers.length; }
    public int queueCapacity() { return workers.length * QUEUE_CAPACITY_PER_WORKER; }
    public int queuedJobs() { return totalQueuedJobs(); }
    public int runningJobs() { return runningJobs.get(); }
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

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (Worker worker : workers) worker.queue.cancelQueued();
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
                LOG.log(System.Logger.Level.WARNING,
                        "Mesh worker {0} did not stop inside the bounded shutdown join.", worker.index);
            }
        }
    }
}
