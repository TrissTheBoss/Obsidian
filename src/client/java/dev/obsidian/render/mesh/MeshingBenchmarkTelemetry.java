package dev.obsidian.render.mesh;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-capacity P3.8 benchmark window for completed production mesh tickets.
 *
 * <p>The worker hot path records only primitive values into preallocated arrays.
 * Percentile sorting and JVM management-bean inspection happen only when a
 * render-thread snapshot is requested. The ring deliberately reports observed,
 * retained and overflow counts so overwriting can never be mistaken for a full
 * history.</p>
 */
public final class MeshingBenchmarkTelemetry {
    public static final int SAMPLE_CAPACITY = 4096;
    public static final int PRIORITY_COUNT = 3;

    public record Distribution(
            long observed,
            int retained,
            long overflow,
            long meanNs,
            long p50Ns,
            long p95Ns,
            long p99Ns,
            long maxNs) {
        public boolean coherent() {
            return observed >= 0L
                    && retained >= 0
                    && retained <= SAMPLE_CAPACITY
                    && overflow == observed - retained
                    && overflow >= 0L
                    && meanNs >= 0L
                    && p50Ns >= 0L
                    && p95Ns >= p50Ns
                    && p99Ns >= p95Ns
                    && maxNs >= p99Ns;
        }
    }

    public record Snapshot(
            boolean armed,
            long startNs,
            long durationNs,
            Distribution queueWait,
            Distribution execution,
            Distribution highQueueWait,
            Distribution normalQueueWait,
            Distribution lowQueueWait,
            long completedSamples,
            long highCompleted,
            long normalCompleted,
            long lowCompleted,
            long stolenCompleted,
            int maxQueuedJobs,
            int maxRunningJobs,
            long aggregateExecutionNs,
            long sourceBakedQuads,
            long independentReferenceFaces,
            long topologyRectangles,
            long topologyCoveredFaces,
            long renderMergeCandidates,
            long passthroughIdentities,
            long mergedIdentities,
            long mergedCoveredSourceFaces,
            long facesSaved,
            long outputQuads,
            long outputVertexBytes,
            long outputIndexBytes,
            long gcCollectionDelta,
            long gcTimeDeltaMs,
            boolean collectorSelfTestPassed) {

        public long outputBytes() {
            return outputVertexBytes + outputIndexBytes;
        }

        public long reductionPermille() {
            return sourceBakedQuads == 0L ? 0L : facesSaved * 1000L / sourceBakedQuads;
        }

        public long workerBusyPermille(int workerCount) {
            if (workerCount <= 0 || durationNs <= 0L) return 0L;
            long denominator;
            try {
                denominator = Math.multiplyExact(durationNs, workerCount);
            } catch (ArithmeticException ignored) {
                denominator = Long.MAX_VALUE;
            }
            if (denominator <= 0L) return 0L;
            return Math.min(1000L, aggregateExecutionNs * 1000L / denominator);
        }

        public boolean percentileAccountingCoherent() {
            return queueWait.coherent()
                    && execution.coherent()
                    && highQueueWait.coherent()
                    && normalQueueWait.coherent()
                    && lowQueueWait.coherent()
                    && queueWait.observed() == completedSamples
                    && execution.observed() == completedSamples
                    && highCompleted + normalCompleted + lowCompleted == completedSamples
                    && highQueueWait.observed() == highCompleted
                    && normalQueueWait.observed() == normalCompleted
                    && lowQueueWait.observed() == lowCompleted;
        }
    }

    private final int capacity;
    private final long[] queueWaitNs;
    private final long[] executionNs;
    private final byte[] priorities;
    private int next;
    private int retained;
    private long observed;
    private long highObserved;
    private long normalObserved;
    private long lowObserved;
    private long stolenCompleted;
    private long aggregateExecutionNs;
    private long sourceBakedQuads;
    private long independentReferenceFaces;
    private long topologyRectangles;
    private long topologyCoveredFaces;
    private long renderMergeCandidates;
    private long passthroughIdentities;
    private long mergedIdentities;
    private long mergedCoveredSourceFaces;
    private long facesSaved;
    private long outputQuads;
    private long outputVertexBytes;
    private long outputIndexBytes;
    private long startNs;
    private long gcCollectionsAtStart;
    private long gcTimeMsAtStart;
    private final AtomicInteger maxQueuedJobs = new AtomicInteger();
    private final AtomicInteger maxRunningJobs = new AtomicInteger();

    public MeshingBenchmarkTelemetry() {
        this(SAMPLE_CAPACITY);
    }

    private MeshingBenchmarkTelemetry(int capacity) {
        if (capacity <= 0 || capacity > SAMPLE_CAPACITY) {
            throw new IllegalArgumentException("capacity must be in [1," + SAMPLE_CAPACITY + "]");
        }
        this.capacity = capacity;
        this.queueWaitNs = new long[capacity];
        this.executionNs = new long[capacity];
        this.priorities = new byte[capacity];
    }

    public synchronized long begin() {
        return beginAt(System.nanoTime(), gcCollections(), gcTimeMs());
    }

    private synchronized long beginAt(long nowNs, long gcCollections, long gcTimeMs) {
        next = 0;
        retained = 0;
        observed = 0L;
        highObserved = 0L;
        normalObserved = 0L;
        lowObserved = 0L;
        stolenCompleted = 0L;
        aggregateExecutionNs = 0L;
        sourceBakedQuads = 0L;
        independentReferenceFaces = 0L;
        topologyRectangles = 0L;
        topologyCoveredFaces = 0L;
        renderMergeCandidates = 0L;
        passthroughIdentities = 0L;
        mergedIdentities = 0L;
        mergedCoveredSourceFaces = 0L;
        facesSaved = 0L;
        outputQuads = 0L;
        outputVertexBytes = 0L;
        outputIndexBytes = 0L;
        maxQueuedJobs.set(0);
        maxRunningJobs.set(0);
        gcCollectionsAtStart = gcCollections;
        gcTimeMsAtStart = gcTimeMs;
        startNs = Math.max(1L, nowNs);
        return startNs;
    }

    public boolean armed() {
        return startNs != 0L;
    }

    public boolean accepts(long enqueueNs) {
        long start = startNs;
        return start != 0L && enqueueNs >= start;
    }

    public void observePressure(long enqueueNs, int queuedJobs, int runningJobs) {
        if (!accepts(enqueueNs)) return;
        updateMax(maxQueuedJobs, Math.max(0, queuedJobs));
        updateMax(maxRunningJobs, Math.max(0, runningJobs));
    }

    public synchronized void recordCompleted(
            long enqueueNs,
            int priority,
            boolean stolen,
            long queueWait,
            long execution,
            long sourceQuads,
            long referenceFaces,
            long rectangles,
            long rectangleCoveredFaces,
            long mergeCandidates,
            long passthrough,
            long merged,
            long mergedCoveredFaces,
            long savedFaces,
            long finalOutputQuads,
            long vertexBytes,
            long indexBytes) {
        if (!accepts(enqueueNs)) return;
        if (priority < 0 || priority >= PRIORITY_COUNT) {
            throw new IllegalArgumentException("invalid benchmark priority " + priority);
        }
        if (queueWait < 0L || execution < 0L) {
            throw new IllegalArgumentException("benchmark timings must be nonnegative");
        }

        queueWaitNs[next] = queueWait;
        executionNs[next] = execution;
        priorities[next] = (byte) priority;
        next++;
        if (next == capacity) next = 0;
        if (retained < capacity) retained++;
        observed++;
        if (priority == 0) highObserved++;
        else if (priority == 1) normalObserved++;
        else lowObserved++;
        if (stolen) stolenCompleted++;
        aggregateExecutionNs += execution;
        sourceBakedQuads += Math.max(0L, sourceQuads);
        independentReferenceFaces += Math.max(0L, referenceFaces);
        topologyRectangles += Math.max(0L, rectangles);
        topologyCoveredFaces += Math.max(0L, rectangleCoveredFaces);
        renderMergeCandidates += Math.max(0L, mergeCandidates);
        passthroughIdentities += Math.max(0L, passthrough);
        mergedIdentities += Math.max(0L, merged);
        mergedCoveredSourceFaces += Math.max(0L, mergedCoveredFaces);
        facesSaved += Math.max(0L, savedFaces);
        outputQuads += Math.max(0L, finalOutputQuads);
        outputVertexBytes += Math.max(0L, vertexBytes);
        outputIndexBytes += Math.max(0L, indexBytes);
    }

    public synchronized Snapshot snapshot() {
        return snapshotAt(System.nanoTime(), gcCollections(), gcTimeMs(), true);
    }

    private synchronized Snapshot snapshotAt(
            long nowNs, long gcCollections, long gcTimeMs, boolean includeSelfTest) {
        long start = startNs;
        boolean armed = start != 0L;
        int currentRetained = retained;
        long currentObserved = observed;

        long[] queue = Arrays.copyOf(queueWaitNs, currentRetained);
        long[] execution = Arrays.copyOf(executionNs, currentRetained);
        byte[] retainedPriorities = Arrays.copyOf(priorities, currentRetained);

        Distribution queueDistribution = distribution(queue, currentObserved);
        Distribution executionDistribution = distribution(execution, currentObserved);
        Distribution highDistribution = distributionForPriority(queue, retainedPriorities, 0, highObserved);
        Distribution normalDistribution = distributionForPriority(queue, retainedPriorities, 1, normalObserved);
        Distribution lowDistribution = distributionForPriority(queue, retainedPriorities, 2, lowObserved);

        return new Snapshot(
                armed,
                start,
                armed ? Math.max(0L, nowNs - start) : 0L,
                queueDistribution,
                executionDistribution,
                highDistribution,
                normalDistribution,
                lowDistribution,
                currentObserved,
                highObserved,
                normalObserved,
                lowObserved,
                stolenCompleted,
                maxQueuedJobs.get(),
                maxRunningJobs.get(),
                aggregateExecutionNs,
                sourceBakedQuads,
                independentReferenceFaces,
                topologyRectangles,
                topologyCoveredFaces,
                renderMergeCandidates,
                passthroughIdentities,
                mergedIdentities,
                mergedCoveredSourceFaces,
                facesSaved,
                outputQuads,
                outputVertexBytes,
                outputIndexBytes,
                armed ? Math.max(0L, gcCollections - gcCollectionsAtStart) : 0L,
                armed ? Math.max(0L, gcTimeMs - gcTimeMsAtStart) : 0L,
                includeSelfTest ? selfTest() : true);
    }

    private static Distribution distributionForPriority(
            long[] values, byte[] samplePriorities, int priority, long priorityObserved) {
        int count = 0;
        for (byte value : samplePriorities) if (value == priority) count++;
        long[] filtered = new long[count];
        int out = 0;
        for (int i = 0; i < samplePriorities.length; i++) {
            if (samplePriorities[i] == priority) filtered[out++] = values[i];
        }
        return distribution(filtered, priorityObserved);
    }

    private static Distribution distribution(long[] values, long observed) {
        if (values.length == 0) {
            return new Distribution(observed, 0, observed, 0L, 0L, 0L, 0L, 0L);
        }
        Arrays.sort(values);
        long sum = 0L;
        for (long value : values) sum += Math.max(0L, value);
        int n = values.length;
        return new Distribution(
                observed,
                n,
                observed - n,
                sum / n,
                values[nearestRankIndex(n, 50)],
                values[nearestRankIndex(n, 95)],
                values[nearestRankIndex(n, 99)],
                values[n - 1]);
    }

    private static int nearestRankIndex(int count, int percentile) {
        int rank = (count * percentile + 99) / 100;
        return Math.max(0, Math.min(count - 1, rank - 1));
    }

    private static void updateMax(AtomicInteger target, int value) {
        int previous;
        do {
            previous = target.get();
            if (value <= previous) return;
        } while (!target.compareAndSet(previous, value));
    }

    private static long gcCollections() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionCount();
            if (value > 0L) total += value;
        }
        return total;
    }

    private static long gcTimeMs() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long value = bean.getCollectionTime();
            if (value > 0L) total += value;
        }
        return total;
    }

    public static boolean selfTest() {
        MeshingBenchmarkTelemetry fixture = new MeshingBenchmarkTelemetry(8);
        fixture.beginAt(100L, 0L, 0L);
        Snapshot empty = fixture.snapshotAt(101L, 0L, 0L, false);
        if (!empty.percentileAccountingCoherent() || empty.completedSamples() != 0L) return false;

        fixture.recordCompleted(100L, 0, false, 7L, 11L,
                1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L, 2L, 3L, 4L);
        Snapshot singleton = fixture.snapshotAt(102L, 0L, 0L, false);
        if (singleton.queueWait().p50Ns() != 7L
                || singleton.queueWait().p95Ns() != 7L
                || singleton.execution().p99Ns() != 11L
                || !singleton.percentileAccountingCoherent()) return false;

        fixture.beginAt(200L, 0L, 0L);
        for (int i = 1; i <= 5; i++) {
            fixture.recordCompleted(200L, i % PRIORITY_COUNT, (i & 1) == 0,
                    i, i * 10L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L, 2L, 3L, 4L);
        }
        Snapshot ordered = fixture.snapshotAt(210L, 0L, 0L, false);
        if (ordered.queueWait().p50Ns() != 3L
                || ordered.queueWait().p95Ns() != 5L
                || ordered.queueWait().p99Ns() != 5L
                || ordered.queueWait().maxNs() != 5L
                || !ordered.percentileAccountingCoherent()) return false;

        MeshingBenchmarkTelemetry wrapped = new MeshingBenchmarkTelemetry(4);
        wrapped.beginAt(300L, 0L, 0L);
        for (int i = 1; i <= 6; i++) {
            wrapped.recordCompleted(300L, 0, false, i, i,
                    1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 0L, 2L, 3L, 4L);
        }
        Snapshot wrap = wrapped.snapshotAt(310L, 0L, 0L, false);
        if (wrap.queueWait().observed() != 6L
                || wrap.queueWait().retained() != 4
                || wrap.queueWait().overflow() != 2L
                || wrap.queueWait().p50Ns() != 4L
                || wrap.queueWait().maxNs() != 6L
                || !wrap.percentileAccountingCoherent()) return false;

        wrapped.beginAt(400L, 0L, 0L);
        Snapshot reset = wrapped.snapshotAt(401L, 0L, 0L, false);
        return reset.completedSamples() == 0L
                && reset.queueWait().retained() == 0
                && reset.execution().retained() == 0
                && reset.percentileAccountingCoherent();
    }
}
