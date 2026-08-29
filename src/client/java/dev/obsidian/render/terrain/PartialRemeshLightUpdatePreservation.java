package dev.obsidian.render.terrain;

/**
 * P3.9 dev22 fixed primitive proof for the one permitted empty-provenance
 * exception. It never observes mutable world state and never changes production
 * invalidation; it only proves that one accepted lifecycle interval consists
 * entirely of same-section ClientChunkCache light-update notifications.
 */
public final class PartialRemeshLightUpdatePreservation {
    private static final System.Logger LOG = System.getLogger("Obsidian/PartialRemeshLightUpdatePreservation");
    private static final int SCOPE_CAPACITY = 8;

    private static boolean armed;
    private static long ownerThreadId;
    private static int scopeDepth;
    private static int overflowDepth;
    private static long crossThreadEvents;
    private static long scopeOverflowEvents;

    private static long relevantLightEvents;
    private static long capturedLightEvents;
    private static boolean intervalObserved;
    private static boolean intervalSameSection;
    private static int intervalX;
    private static int intervalY;
    private static int intervalZ;

    private static long lastLightEvents;
    private static int lastLifecycleEvents;
    private static boolean lastObserved;
    private static boolean lastSameSection;
    private static boolean lastScopeBalanced;
    private static int lastX;
    private static int lastY;
    private static int lastZ;

    private static long eligibleChecks;
    private static long preservedIntervals;
    private static long rejectedIntervals;
    private static long reflectionFailures;

    private PartialRemeshLightUpdatePreservation() { }

    public static synchronized void begin() {
        armed = true;
        ownerThreadId = 0L;
        scopeDepth = 0;
        overflowDepth = 0;
        crossThreadEvents = 0L;
        scopeOverflowEvents = 0L;
        relevantLightEvents = 0L;
        capturedLightEvents = 0L;
        intervalObserved = false;
        intervalSameSection = true;
        intervalX = intervalY = intervalZ = 0;
        lastLightEvents = 0L;
        lastLifecycleEvents = 0;
        lastObserved = false;
        lastSameSection = false;
        lastScopeBalanced = true;
        lastX = lastY = lastZ = 0;
        eligibleChecks = 0L;
        preservedIntervals = 0L;
        rejectedIntervals = 0L;
        reflectionFailures = 0L;
    }

    public static synchronized void enterLightUpdate() {
        if (!armed) return;
        long tid = Thread.currentThread().threadId();
        if (scopeDepth == 0 && overflowDepth == 0) {
            ownerThreadId = tid;
        } else if (ownerThreadId != tid) {
            crossThreadEvents++;
            return;
        }
        if (overflowDepth > 0) {
            overflowDepth++;
            return;
        }
        if (scopeDepth >= SCOPE_CAPACITY) {
            overflowDepth = 1;
            scopeOverflowEvents++;
            return;
        }
        scopeDepth++;
    }

    public static synchronized void exitLightUpdate() {
        if (!armed) return;
        long tid = Thread.currentThread().threadId();
        if (ownerThreadId != 0L && ownerThreadId != tid) {
            crossThreadEvents++;
            return;
        }
        if (overflowDepth > 0) {
            overflowDepth--;
            resetOwnerIfIdle();
            return;
        }
        if (scopeDepth <= 0) {
            crossThreadEvents++;
            ownerThreadId = 0L;
            return;
        }
        scopeDepth--;
        resetOwnerIfIdle();
    }

    /** Called only from the already-proven relevant SINGLE_SECTION observation. */
    public static synchronized void observeRelevantSingleSection(int x, int y, int z) {
        if (!armed) return;
        long tid = Thread.currentThread().threadId();
        if (scopeDepth <= 0 || overflowDepth > 0 || ownerThreadId != tid) return;
        relevantLightEvents++;
        if (!intervalObserved) {
            intervalObserved = true;
            intervalSameSection = true;
            intervalX = x;
            intervalY = y;
            intervalZ = z;
        } else if (intervalX != x || intervalY != y || intervalZ != z) {
            intervalSameSection = false;
        }
    }

    /** Snapshot one accepted lifecycle drain before P3.9 admission examines provenance. */
    public static synchronized void captureLifecycleDrain(int lifecycleRelevantEvents) {
        if (!armed) return;
        long delta = relevantLightEvents - capturedLightEvents;
        if (delta < 0L) delta = 0L;
        capturedLightEvents = relevantLightEvents;
        lastLightEvents = delta;
        lastLifecycleEvents = Math.max(0, lifecycleRelevantEvents);
        lastObserved = intervalObserved;
        lastSameSection = intervalObserved && intervalSameSection;
        lastScopeBalanced = scopeDepth == 0 && overflowDepth == 0;
        if (intervalObserved) {
            lastX = intervalX;
            lastY = intervalY;
            lastZ = intervalZ;
        }
        intervalObserved = false;
        intervalSameSection = true;
    }

    /**
     * Evaluate the only A-0182 exception. A true result means the caller may
     * preserve the pending request unchanged; false means existing fallback
     * behavior must proceed.
     */
    public static synchronized boolean tryPreserveEmptyProvenanceForPending(
            int provenanceCount,
            int provenanceFlags,
            int pendingX,
            int pendingY,
            int pendingZ) {
        if (!armed) return false;
        eligibleChecks++;
        boolean eligible = eligible(
                provenanceCount,
                provenanceFlags,
                lastLifecycleEvents,
                lastLightEvents,
                lastObserved,
                lastSameSection,
                lastScopeBalanced,
                lastX,
                lastY,
                lastZ,
                pendingX,
                pendingY,
                pendingZ,
                crossThreadEvents,
                scopeOverflowEvents);
        if (eligible) preservedIntervals++;
        else rejectedIntervals++;
        return eligible;
    }

    public static synchronized void recordReflectionFailure() {
        if (!armed) return;
        eligibleChecks++;
        rejectedIntervals++;
        reflectionFailures++;
    }

    public static synchronized void logFinal() {
        if (!armed) return;
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev22 P3.9 final light-update preservation: eligibleChecks={0}, preserved={1}, rejected={2}, reflectionFailures={3}, lastLifecycleRelevantEvents={4}, lastLightUpdateEvents={5}, lastObserved={6}, lastSameSection={7}, lastScopeBalanced={8}, lastSection=({9},{10},{11}), crossThreadEvents={12}, scopeOverflowEvents={13}, accountingCoherent={14}, selfTest={15}, pendingRequestMutated=false, productionInvalidationChanged=false, partialGpuPatch=false, thresholdsChanged=false.",
                eligibleChecks, preservedIntervals, rejectedIntervals, reflectionFailures,
                lastLifecycleEvents, lastLightEvents, lastObserved, lastSameSection, lastScopeBalanced,
                lastX, lastY, lastZ, crossThreadEvents, scopeOverflowEvents,
                eligibleChecks == preservedIntervals + rejectedIntervals, selfTest());
    }

    public static boolean selfTest() {
        return eligible(0, 0, 2, 2, true, true, true,
                    5, 4, -3, 5, 4, -3, 0L, 0L)
                && !eligible(0, 0, 2, 2, true, true, true,
                    5, 4, -2, 5, 4, -3, 0L, 0L)
                && !eligible(0, 0, 2, 2, true, false, true,
                    5, 4, -3, 5, 4, -3, 0L, 0L)
                && !eligible(0, 0, 2, 1, true, true, true,
                    5, 4, -3, 5, 4, -3, 0L, 0L)
                && !eligible(0, 0, 0, 0, false, false, true,
                    0, 0, 0, 5, 4, -3, 0L, 0L)
                && !eligible(0, 0, 1, 1, true, true, false,
                    5, 4, -3, 5, 4, -3, 0L, 0L)
                && !eligible(0, 0, 1, 1, true, true, true,
                    5, 4, -3, 5, 4, -3, 1L, 0L)
                && !eligible(0, 0, 1, 1, true, true, true,
                    5, 4, -3, 5, 4, -3, 0L, 1L)
                && !eligible(1, 0, 1, 1, true, true, true,
                    5, 4, -3, 5, 4, -3, 0L, 0L)
                && !eligible(0, PartialRemeshDirtyProvenance.FLAG_OVERFLOW,
                    1, 1, true, true, true,
                    5, 4, -3, 5, 4, -3, 0L, 0L);
    }

    private static boolean eligible(
            int provenanceCount,
            int provenanceFlags,
            int lifecycleEvents,
            long lightEvents,
            boolean observed,
            boolean sameSection,
            boolean scopeBalanced,
            int lightX,
            int lightY,
            int lightZ,
            int pendingX,
            int pendingY,
            int pendingZ,
            long crossThread,
            long overflow) {
        return provenanceCount == 0
                && provenanceFlags == 0
                && lifecycleEvents > 0
                && lightEvents == lifecycleEvents
                && lightEvents > 0L
                && observed
                && sameSection
                && scopeBalanced
                && lightX == pendingX
                && lightY == pendingY
                && lightZ == pendingZ
                && crossThread == 0L
                && overflow == 0L;
    }

    private static void resetOwnerIfIdle() {
        if (scopeDepth == 0 && overflowDepth == 0) ownerThreadId = 0L;
    }
}
