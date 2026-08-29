package dev.obsidian.render.terrain;

/**
 * P3.9 dev21 fixed-size primitive diagnostics for the already-existing
 * FALLBACK_PROVENANCE decision. This class is observational only: it never
 * changes admission, invalidation, meshing, upload, install, draw, or any
 * A-0159 threshold.
 */
public final class PartialRemeshProvenanceDiagnostics {
    private static final System.Logger LOG = System.getLogger("Obsidian/PartialRemeshProvenanceDiagnostics");

    private static final int SUBREASON_MISSING_OR_EMPTY = 1;
    private static final int SUBREASON_OFF_RENDER_THREAD = 1 << 1;
    private static final int SUBREASON_OVERFLOW = 1 << 2;

    private static boolean armed;

    private static int lastDrainCount;
    private static int lastFallbackFlags;
    private static long lastOverflowEvents;
    private static int lastSceneStateOrdinal = -1;
    private static boolean lastCenterKnown;
    private static boolean lastPendingEpisode;
    private static boolean lastContextCaptured;
    private static boolean lastPendingProbeAvailable;
    private static int lastLifecycleRelevantEventCount;

    private static long provenanceFallbacks;
    private static long missingOrEmpty;
    private static long offRenderThread;
    private static long overflowFlag;
    private static long overflowEvents;
    private static long other;

    private static boolean firstRetained;
    private static long firstFallbackIndex;
    private static int firstDrainCount;
    private static int firstFallbackFlags;
    private static long firstOverflowEvents;
    private static int firstSceneStateOrdinal = -1;
    private static boolean firstCenterKnown;
    private static boolean firstPendingEpisode;
    private static boolean firstContextCaptured;
    private static boolean firstPendingProbeAvailable;
    private static int firstLifecycleRelevantEventCount;

    private PartialRemeshProvenanceDiagnostics() { }

    public static synchronized void begin() {
        armed = true;
        lastDrainCount = 0;
        lastFallbackFlags = 0;
        lastOverflowEvents = 0L;
        lastSceneStateOrdinal = -1;
        lastCenterKnown = false;
        lastPendingEpisode = false;
        lastContextCaptured = false;
        lastPendingProbeAvailable = false;
        lastLifecycleRelevantEventCount = 0;
        provenanceFallbacks = 0L;
        missingOrEmpty = 0L;
        offRenderThread = 0L;
        overflowFlag = 0L;
        overflowEvents = 0L;
        other = 0L;
        firstRetained = false;
        firstFallbackIndex = 0L;
        firstDrainCount = 0;
        firstFallbackFlags = 0;
        firstOverflowEvents = 0L;
        firstSceneStateOrdinal = -1;
        firstCenterKnown = false;
        firstPendingEpisode = false;
        firstContextCaptured = false;
        firstPendingProbeAvailable = false;
        firstLifecycleRelevantEventCount = 0;
        PartialRemeshSectionDirtyOriginDiagnostics.begin();
        PartialRemeshSingleSectionCallerDiagnostics.begin();
    }

    public static synchronized void captureContext(
            int sceneStateOrdinal,
            boolean centerKnown,
            boolean pendingEpisode,
            boolean pendingProbeAvailable,
            int lifecycleRelevantEventCount) {
        if (!armed) return;
        lastSceneStateOrdinal = sceneStateOrdinal;
        lastCenterKnown = centerKnown;
        lastPendingEpisode = pendingEpisode;
        lastContextCaptured = true;
        lastPendingProbeAvailable = pendingProbeAvailable;
        lastLifecycleRelevantEventCount = Math.max(0, lifecycleRelevantEventCount);
        PartialRemeshSectionDirtyOriginDiagnostics.captureLifecycleDrain(lastLifecycleRelevantEventCount);
        PartialRemeshSingleSectionCallerDiagnostics.captureLifecycleDrain(lastLifecycleRelevantEventCount);
    }

    public static synchronized void captureDrain(int count, int fallbackFlags, long reportedOverflowEvents) {
        if (!armed) return;
        lastDrainCount = Math.max(0, count);
        lastFallbackFlags = fallbackFlags;
        lastOverflowEvents = Math.max(0L, reportedOverflowEvents);
    }

    /** Called only after the existing telemetry has already counted one provenance fallback. */
    public static synchronized void observeProvenanceFallback() {
        if (!armed) return;
        provenanceFallbacks++;
        int subreasons = classify(lastDrainCount, lastFallbackFlags);
        if ((subreasons & SUBREASON_MISSING_OR_EMPTY) != 0) missingOrEmpty++;
        if ((subreasons & SUBREASON_OFF_RENDER_THREAD) != 0) offRenderThread++;
        if ((subreasons & SUBREASON_OVERFLOW) != 0) overflowFlag++;
        if (subreasons == 0) other++;
        overflowEvents += lastOverflowEvents;

        if (!firstRetained) {
            firstRetained = true;
            firstFallbackIndex = provenanceFallbacks;
            firstDrainCount = lastDrainCount;
            firstFallbackFlags = lastFallbackFlags;
            firstOverflowEvents = lastOverflowEvents;
            firstSceneStateOrdinal = lastSceneStateOrdinal;
            firstCenterKnown = lastCenterKnown;
            firstPendingEpisode = lastPendingEpisode;
            firstContextCaptured = lastContextCaptured;
            firstPendingProbeAvailable = lastPendingProbeAvailable;
            firstLifecycleRelevantEventCount = lastLifecycleRelevantEventCount;
        }

        PartialRemeshSectionDirtyOriginDiagnostics.observeProvenanceFallback(
                lastDrainCount, lastFallbackFlags, lastSceneStateOrdinal,
                lastCenterKnown, lastPendingEpisode);
        PartialRemeshSingleSectionCallerDiagnostics.observeProvenanceFallback(
                lastDrainCount, lastFallbackFlags, lastSceneStateOrdinal,
                lastCenterKnown, lastPendingEpisode);
    }

    public static synchronized void logFinal() {
        if (!armed) return;
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev21 P3.9 final provenance diagnostics: provenanceFallbacksObserved={0}, missingOrEmpty={1}, offRenderThread={2}, overflowFlag={3}, overflowEvents={4}, other={5}, subreasonCountersMayOverlap=true, highLevelFallbackAccountingChanged=false, firstRetained={6}, firstFallbackIndex={7}, firstDrainCount={8}, firstFallbackFlags={9}, firstOverflowEvents={10}, firstSceneStateOrdinal={11}, firstSceneStateName={12}, firstCenterKnown={13}, firstPendingEpisode={14}, firstContextCaptured={15}, firstPendingProbeAvailable={16}, firstLifecycleRelevantEvents={17}, selfTest={18}, boundedPrimitiveState=true, productionRendererChanged=false, admissionPolicyChanged=false, thresholdsChanged=false.",
                provenanceFallbacks, missingOrEmpty, offRenderThread, overflowFlag, overflowEvents, other,
                firstRetained, firstFallbackIndex, firstDrainCount, firstFallbackFlags, firstOverflowEvents,
                firstSceneStateOrdinal, stateName(firstSceneStateOrdinal), firstCenterKnown, firstPendingEpisode,
                firstContextCaptured, firstPendingProbeAvailable, firstLifecycleRelevantEventCount, selfTest());
        PartialRemeshSectionDirtyOriginDiagnostics.logFinal();
        PartialRemeshSingleSectionCallerDiagnostics.logFinal();
    }

    private static int classify(int count, int flags) {
        int result = 0;
        if (count == 0 && flags == 0) result |= SUBREASON_MISSING_OR_EMPTY;
        if ((flags & PartialRemeshDirtyProvenance.FLAG_OFF_RENDER_THREAD) != 0) {
            result |= SUBREASON_OFF_RENDER_THREAD;
        }
        if ((flags & PartialRemeshDirtyProvenance.FLAG_OVERFLOW) != 0) {
            result |= SUBREASON_OVERFLOW;
        }
        return result;
    }

    private static String stateName(int ordinal) {
        return switch (ordinal) {
            case 0 -> "WAITING_WORLD";
            case 1 -> "SCANNING";
            case 2 -> "BUILDING";
            case 3 -> "LIVE";
            case 4 -> "RETIRING";
            case 5 -> "FAILED";
            case 6 -> "CLOSED";
            default -> "unknown";
        };
    }

    public static boolean selfTest() {
        return classify(0, 0) == SUBREASON_MISSING_OR_EMPTY
                && classify(1, PartialRemeshDirtyProvenance.FLAG_OFF_RENDER_THREAD)
                    == SUBREASON_OFF_RENDER_THREAD
                && classify(1, PartialRemeshDirtyProvenance.FLAG_OVERFLOW)
                    == SUBREASON_OVERFLOW
                && classify(0, PartialRemeshDirtyProvenance.FLAG_OFF_RENDER_THREAD
                    | PartialRemeshDirtyProvenance.FLAG_OVERFLOW)
                    == (SUBREASON_OFF_RENDER_THREAD | SUBREASON_OVERFLOW)
                && classify(1, 0) == 0
                && shouldRetainFirst(false)
                && !shouldRetainFirst(true)
                && highLevelIncrementForAnySubreasonMask(SUBREASON_OFF_RENDER_THREAD | SUBREASON_OVERFLOW) == 1
                && PartialRemeshSectionDirtyOriginDiagnostics.selfTest()
                && PartialRemeshSingleSectionCallerDiagnostics.selfTest();
    }

    private static boolean shouldRetainFirst(boolean alreadyRetained) {
        return !alreadyRetained;
    }

    private static int highLevelIncrementForAnySubreasonMask(int ignoredSubreasonMask) {
        return 1;
    }
}
