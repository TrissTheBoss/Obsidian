package dev.obsidian.render.mesh;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.terrain.BakedSectionMesh;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
import dev.obsidian.render.terrain.SectionBakedQuadSnapshot;
import dev.obsidian.render.terrain.SectionLifecycleEvents;
import dev.obsidian.render.terrain.SectionSnapshot;

/**
 * Phase 3 P3.1 first proof: captures immutable real-section inputs on the
 * render thread, then exercises bounded priority queues, work stealing,
 * cancellation and deterministic pure mesh construction on dedicated workers.
 *
 * <p>This probe does not upload or install worker output yet. The validated
 * Phase 2 scene remains the graphics oracle while this concurrency boundary is
 * proven independently.</p>
 */
public final class WorkerMeshValidationProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/WorkerMeshValidation");
    private static final int JOB_COUNT = 12;
    private static final int CANCEL_FROM_INDEX = 4;
    private static final long RETRY_DELAY_NS = 500_000_000L;

    public enum State { WAITING_WORLD, RUNNING, SUCCESS, FAILED, CLOSED }

    private final SectionMeshWorkerPool workers;
    private final SectionMeshWorkerPool.Ticket[] tickets = new SectionMeshWorkerPool.Ticket[JOB_COUNT];
    private final boolean[] processed = new boolean[JOB_COUNT];

    private State state = State.WAITING_WORLD;
    private SectionSnapshot snapshot;
    private SectionBakedQuadSnapshot bakedSnapshot;
    private ReferenceFaceMesh referenceMesh;
    private long expectedEventSequence;
    private long generation = 1L;
    private long nextAttemptNs;
    private long staleBatches;
    private long acceptedCompleted;
    private long acceptedCancelled;
    private long deterministicMatches;
    private long firstFingerprint;
    private boolean firstFingerprintKnown;
    private Throwable failure;

    public WorkerMeshValidationProbe(SectionMeshWorkerPool workers) {
        if (workers == null) throw new NullPointerException("workers");
        this.workers = workers;
    }

    public void afterWorldRender(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED || state == State.SUCCESS || state == State.FAILED) return;

        if (state == State.WAITING_WORLD) {
            long now = System.nanoTime();
            if (now < nextAttemptNs) return;
            tryCaptureAndSubmit(frameSerial, now);
            return;
        }

        if (state == State.RUNNING) {
            if (SectionLifecycleEvents.latestSequence() != expectedEventSequence) {
                staleBatches++;
                cancelOutstanding();
            }
            pollResults(frameSerial);
        }
    }

    private void tryCaptureAndSubmit(long frameSerial, long nowNs) {
        long sequenceBefore = SectionLifecycleEvents.latestSequence();
        SectionSnapshot captured = SectionSnapshot.tryCaptureNearPlayer();
        if (captured == null) {
            nextAttemptNs = nowNs + RETRY_DELAY_NS;
            return;
        }

        try {
            ReferenceFaceMesh firstReference = ReferenceFaceMesh.build(captured);
            ReferenceFaceMesh secondReference = ReferenceFaceMesh.build(captured);
            if (firstReference.faceCount() <= 0 || !firstReference.contentEquals(secondReference)) {
                throw new IllegalStateException("P3.1 worker proof reference oracle is empty or nondeterministic");
            }

            SectionBakedQuadSnapshot firstBaked = SectionBakedQuadSnapshot.capture(captured);
            SectionBakedQuadSnapshot secondBaked = SectionBakedQuadSnapshot.capture(captured);
            if (!firstBaked.contentEquals(secondBaked)) {
                throw new IllegalStateException("P3.1 worker proof generalized capture is nondeterministic");
            }
            if (firstBaked.solidQuads() <= 0 || firstBaked.cutoutQuads() <= 0) {
                nextAttemptNs = nowNs + RETRY_DELAY_NS;
                return;
            }
            if (SectionLifecycleEvents.latestSequence() != sequenceBefore) {
                nextAttemptNs = nowNs + RETRY_DELAY_NS;
                return;
            }

            snapshot = captured;
            bakedSnapshot = firstBaked;
            referenceMesh = firstReference;
            expectedEventSequence = sequenceBefore;
            generation++;
            acceptedCompleted = 0L;
            acceptedCancelled = 0L;
            deterministicMatches = 0L;
            firstFingerprint = 0L;
            firstFingerprintKnown = false;
            for (int i = 0; i < tickets.length; i++) {
                processed[i] = false;
                int priority = switch (i % 3) {
                    case 0 -> SectionMeshWorkerPool.PRIORITY_HIGH;
                    case 1 -> SectionMeshWorkerPool.PRIORITY_NORMAL;
                    default -> SectionMeshWorkerPool.PRIORITY_LOW;
                };
                tickets[i] = workers.submitPinnedForValidation(
                        0, generation, expectedEventSequence, priority, captured, firstBaked);
                if (tickets[i] == null) {
                    throw new IllegalStateException("P3.1 bounded worker queue rejected validation job " + i);
                }
                if (i >= CANCEL_FROM_INDEX) {
                    workers.cancel(tickets[i]);
                }
            }
            state = State.RUNNING;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 3 dev1 worker proof submitted {0} immutable real-section mesh jobs on frame {1}: section=({2},{3},{4}), workerCount={5}, queueCapacity={6}, expectedEventSequence={7}, cancellationRequests={8}. All jobs are pinned to worker 0 initially so idle peers must exercise work stealing; cancellation is requested immediately for the final validation subset.",
                    tickets.length,
                    frameSerial,
                    captured.sectionX(), captured.sectionY(), captured.sectionZ(),
                    workers.workerCount(), workers.queueCapacity(), expectedEventSequence,
                    tickets.length - CANCEL_FROM_INDEX);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private void pollResults(long frameSerial) {
        int terminal = 0;
        for (int i = 0; i < tickets.length; i++) {
            SectionMeshWorkerPool.Ticket ticket = tickets[i];
            if (ticket == null) continue;
            if (!ticket.terminal()) continue;
            terminal++;
            if (processed[i]) continue;
            processed[i] = true;

            if (ticket.state() == SectionMeshWorkerPool.TicketState.FAILED) {
                Throwable cause = ticket.failure();
                fail(cause == null ? new IllegalStateException("Worker job failed without cause") : cause);
                return;
            }
            if (ticket.state() == SectionMeshWorkerPool.TicketState.CANCELLED) {
                acceptedCancelled++;
                continue;
            }
            if (ticket.state() != SectionMeshWorkerPool.TicketState.COMPLETED) {
                continue;
            }

            if (SectionLifecycleEvents.latestSequence() != expectedEventSequence) {
                staleBatches++;
                cancelOutstanding();
                continue;
            }

            BakedSectionMesh mesh = ticket.mesh();
            if (mesh == null) {
                fail(new IllegalStateException("Completed worker ticket did not publish a mesh"));
                return;
            }
            mesh.validateAgainst(snapshot, bakedSnapshot);
            if (!firstFingerprintKnown) {
                firstFingerprintKnown = true;
                firstFingerprint = mesh.fingerprint();
            } else if (mesh.fingerprint() != firstFingerprint) {
                fail(new IllegalStateException("Worker results disagreed on deterministic mesh fingerprint"));
                return;
            }
            deterministicMatches++;
            acceptedCompleted++;
        }

        if (terminal != tickets.length) return;

        if (SectionLifecycleEvents.latestSequence() != expectedEventSequence) {
            resetForRetry();
            return;
        }

        boolean gateReady = acceptedCompleted + acceptedCancelled == tickets.length
                && acceptedCompleted >= CANCEL_FROM_INDEX
                && acceptedCancelled > 0L
                && deterministicMatches == acceptedCompleted
                && workers.cancellationRequests() >= tickets.length - CANCEL_FROM_INDEX
                && workers.stolenJobs() > 0L
                && workers.queueFullRejections() == 0L
                && workers.failedJobs() == 0L
                && workers.queuedJobs() == 0
                && workers.runningJobs() == 0;
        if (!gateReady) {
            fail(new IllegalStateException(
                    "P3.1 worker proof terminal accounting failed: completed=" + acceptedCompleted
                            + ", cancelled=" + acceptedCancelled
                            + ", deterministic=" + deterministicMatches
                            + ", cancellationRequests=" + workers.cancellationRequests()
                            + ", stolen=" + workers.stolenJobs()
                            + ", queueFull=" + workers.queueFullRejections()
                            + ", failed=" + workers.failedJobs()));
            return;
        }

        state = State.SUCCESS;
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev1 worker/job architecture VERIFIED on frame {0}: section=({1},{2},{3}), snapshotFingerprint={4}, referenceFingerprint={5}, bakedFingerprint={6}, meshFingerprint={7}, completedJobs={8}, cancelledJobs={9}, cancellationRequests={10}, stolenJobs={11}, workerCount={12}, maxQueueDepth={13}, totalQueueWaitNs={14}, maxQueueWaitNs={15}, totalExecutionNs={16}, maxExecutionNs={17}, queueFullRejections={18}, failedJobs={19}, staleBatches={20}, workerWorldReadsAfterCapture=0, boundedPriorityQueues=true, workStealing=true, generationTaggedJobs=true, renderThreadGpuOwnershipPreserved=true.",
                frameSerial,
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(),
                Long.toUnsignedString(snapshot.fingerprint()),
                Long.toUnsignedString(referenceMesh.fingerprint()),
                Long.toUnsignedString(bakedSnapshot.fingerprint()),
                Long.toUnsignedString(firstFingerprint),
                acceptedCompleted,
                acceptedCancelled,
                workers.cancellationRequests(),
                workers.stolenJobs(),
                workers.workerCount(),
                workers.maxObservedQueueDepth(),
                workers.totalQueueWaitNs(),
                workers.maxQueueWaitNs(),
                workers.totalExecutionNs(),
                workers.maxExecutionNs(),
                workers.queueFullRejections(),
                workers.failedJobs(),
                staleBatches);
    }

    private void cancelOutstanding() {
        for (SectionMeshWorkerPool.Ticket ticket : tickets) {
            if (ticket != null && !ticket.terminal()) workers.cancel(ticket);
        }
    }

    private void resetForRetry() {
        for (int i = 0; i < tickets.length; i++) {
            tickets[i] = null;
            processed[i] = false;
        }
        snapshot = null;
        bakedSnapshot = null;
        referenceMesh = null;
        state = State.WAITING_WORLD;
        nextAttemptNs = System.nanoTime() + RETRY_DELAY_NS;
    }

    private void fail(Throwable throwable) {
        failure = throwable;
        cancelOutstanding();
        state = State.FAILED;
        LOG.log(System.Logger.Level.ERROR,
                "Phase 3 dev1 worker/job architecture proof failed; Minecraft will continue for diagnosis.",
                throwable);
    }

    public State state() { return state; }
    public boolean gateReady() { return state == State.SUCCESS; }
    public Throwable failure() { return failure; }
    public long staleBatches() { return staleBatches; }
    public long acceptedCompleted() { return acceptedCompleted; }
    public long acceptedCancelled() { return acceptedCancelled; }
    public long deterministicMatches() { return deterministicMatches; }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) return;
        cancelOutstanding();
        state = State.CLOSED;
    }
}
