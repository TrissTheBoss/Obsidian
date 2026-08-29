package dev.obsidian.render.terrain;

import java.util.Arrays;

/**
 * P3.9 dev20 bounded caller-origin tracer for tracked-scene-relevant
 * LevelExtractor section-dirty events. Diagnostic only: no admission or
 * invalidation decision is changed by this class.
 */
public final class PartialRemeshSectionDirtyOriginDiagnostics {
    private static final System.Logger LOG = System.getLogger("Obsidian/PartialRemeshSectionDirtyOriginDiagnostics");

    public static final int ORIGIN_NONE = 0;
    public static final int ORIGIN_EXACT_BLOCK = 1;
    public static final int ORIGIN_BLOCK_RANGE = 2;
    public static final int ORIGIN_NEIGHBOR_RANGE = 3;
    public static final int ORIGIN_SECTION_RANGE = 4;
    public static final int ORIGIN_SINGLE_SECTION = 5;
    public static final int ORIGIN_UNCLASSIFIED = 6;
    private static final int ORIGIN_COUNT = 7;

    private static final int FALLBACK_EXACT_ONLY = 1;
    private static final int FALLBACK_SINGLE_ONLY = 2;
    private static final int FALLBACK_RANGE_ONLY = 3;
    private static final int FALLBACK_MIXED = 4;
    private static final int FALLBACK_NO_RELEVANT = 5;
    private static final int FALLBACK_UNCLASSIFIED = 6;

    private static boolean armed;

    private static final long[] relevant = new long[ORIGIN_COUNT];
    private static final long[] fromPlayer = new long[ORIGIN_COUNT];
    private static final long[] capturedTotals = new long[ORIGIN_COUNT];
    private static final long[] lastDrainCounts = new long[ORIGIN_COUNT];

    private static final boolean[] firstObserved = new boolean[ORIGIN_COUNT];
    private static final int[] firstX = new int[ORIGIN_COUNT];
    private static final int[] firstY = new int[ORIGIN_COUNT];
    private static final int[] firstZ = new int[ORIGIN_COUNT];
    private static final boolean[] firstPlayer = new boolean[ORIGIN_COUNT];

    private static final boolean[] intervalFirstObserved = new boolean[ORIGIN_COUNT];
    private static final int[] intervalFirstX = new int[ORIGIN_COUNT];
    private static final int[] intervalFirstY = new int[ORIGIN_COUNT];
    private static final int[] intervalFirstZ = new int[ORIGIN_COUNT];
    private static final boolean[] intervalFirstPlayer = new boolean[ORIGIN_COUNT];

    private static final boolean[] lastDrainFirstObserved = new boolean[ORIGIN_COUNT];
    private static final int[] lastDrainFirstX = new int[ORIGIN_COUNT];
    private static final int[] lastDrainFirstY = new int[ORIGIN_COUNT];
    private static final int[] lastDrainFirstZ = new int[ORIGIN_COUNT];
    private static final boolean[] lastDrainFirstPlayer = new boolean[ORIGIN_COUNT];

    private static int lastOriginMask;
    private static long lastSectionDirtyTotal;
    private static int lastLifecycleRelevantEventCount;

    private static long fallbackClassified;
    private static long fallbackExactOnly;
    private static long fallbackSingleOnly;
    private static long fallbackRangeOnly;
    private static long fallbackMixed;
    private static long fallbackNoRelevant;
    private static long fallbackUnclassified;

    private static boolean firstFallbackRetained;
    private static long firstFallbackIndex;
    private static int firstFallbackOriginMask;
    private static long firstFallbackSectionDirtyTotal;
    private static int firstFallbackLifecycleRelevantEventCount;
    private static int firstFallbackProvenanceDrainCount;
    private static int firstFallbackProvenanceFlags;
    private static int firstFallbackSceneStateOrdinal = -1;
    private static boolean firstFallbackCenterKnown;
    private static boolean firstFallbackPendingEpisode;
    private static final long[] firstFallbackCounts = new long[ORIGIN_COUNT];
    private static int firstFallbackFixtureOrigin;
    private static int firstFallbackFixtureX;
    private static int firstFallbackFixtureY;
    private static int firstFallbackFixtureZ;
    private static boolean firstFallbackFixturePlayer;

    private PartialRemeshSectionDirtyOriginDiagnostics() { }

    public static synchronized void begin() {
        armed = true;
        Arrays.fill(relevant, 0L);
        Arrays.fill(fromPlayer, 0L);
        Arrays.fill(capturedTotals, 0L);
        Arrays.fill(lastDrainCounts, 0L);
        Arrays.fill(firstObserved, false);
        Arrays.fill(firstX, 0);
        Arrays.fill(firstY, 0);
        Arrays.fill(firstZ, 0);
        Arrays.fill(firstPlayer, false);
        Arrays.fill(intervalFirstObserved, false);
        Arrays.fill(intervalFirstX, 0);
        Arrays.fill(intervalFirstY, 0);
        Arrays.fill(intervalFirstZ, 0);
        Arrays.fill(intervalFirstPlayer, false);
        Arrays.fill(lastDrainFirstObserved, false);
        Arrays.fill(lastDrainFirstX, 0);
        Arrays.fill(lastDrainFirstY, 0);
        Arrays.fill(lastDrainFirstZ, 0);
        Arrays.fill(lastDrainFirstPlayer, false);
        Arrays.fill(firstFallbackCounts, 0L);
        lastOriginMask = 0;
        lastSectionDirtyTotal = 0L;
        lastLifecycleRelevantEventCount = 0;
        fallbackClassified = 0L;
        fallbackExactOnly = 0L;
        fallbackSingleOnly = 0L;
        fallbackRangeOnly = 0L;
        fallbackMixed = 0L;
        fallbackNoRelevant = 0L;
        fallbackUnclassified = 0L;
        firstFallbackRetained = false;
        firstFallbackIndex = 0L;
        firstFallbackOriginMask = 0;
        firstFallbackSectionDirtyTotal = 0L;
        firstFallbackLifecycleRelevantEventCount = 0;
        firstFallbackProvenanceDrainCount = 0;
        firstFallbackProvenanceFlags = 0;
        firstFallbackSceneStateOrdinal = -1;
        firstFallbackCenterKnown = false;
        firstFallbackPendingEpisode = false;
        firstFallbackFixtureOrigin = ORIGIN_NONE;
        firstFallbackFixtureX = firstFallbackFixtureY = firstFallbackFixtureZ = 0;
        firstFallbackFixturePlayer = false;
    }

    /** Called only after SectionLifecycleEvents proved this section dirty relevant. */
    public static synchronized void observeRelevantSectionDirty(
            int origin,
            int sectionX,
            int sectionY,
            int sectionZ,
            boolean dirtyFromPlayer) {
        if (!armed) return;
        int safeOrigin = sanitizeOrigin(origin);
        relevant[safeOrigin]++;
        if (dirtyFromPlayer) fromPlayer[safeOrigin]++;
        if (!firstObserved[safeOrigin]) {
            firstObserved[safeOrigin] = true;
            firstX[safeOrigin] = sectionX;
            firstY[safeOrigin] = sectionY;
            firstZ[safeOrigin] = sectionZ;
            firstPlayer[safeOrigin] = dirtyFromPlayer;
        }
        if (!intervalFirstObserved[safeOrigin]) {
            intervalFirstObserved[safeOrigin] = true;
            intervalFirstX[safeOrigin] = sectionX;
            intervalFirstY[safeOrigin] = sectionY;
            intervalFirstZ[safeOrigin] = sectionZ;
            intervalFirstPlayer[safeOrigin] = dirtyFromPlayer;
        }
    }

    /**
     * Captures the exact origin delta that preceded the already-drained
     * SectionLifecycleEvents cursor for one preparePartialRemeshEpisode call.
     */
    public static synchronized void captureLifecycleDrain(int lifecycleRelevantEventCount) {
        if (!armed) return;
        lastOriginMask = 0;
        lastSectionDirtyTotal = 0L;
        lastLifecycleRelevantEventCount = Math.max(0, lifecycleRelevantEventCount);
        for (int origin = 1; origin < ORIGIN_COUNT; origin++) {
            long delta = relevant[origin] - capturedTotals[origin];
            if (delta < 0L) delta = 0L;
            lastDrainCounts[origin] = delta;
            capturedTotals[origin] = relevant[origin];
            if (delta > 0L) {
                lastOriginMask |= 1 << origin;
                lastSectionDirtyTotal += delta;
            }
            lastDrainFirstObserved[origin] = intervalFirstObserved[origin];
            if (intervalFirstObserved[origin]) {
                lastDrainFirstX[origin] = intervalFirstX[origin];
                lastDrainFirstY[origin] = intervalFirstY[origin];
                lastDrainFirstZ[origin] = intervalFirstZ[origin];
                lastDrainFirstPlayer[origin] = intervalFirstPlayer[origin];
            }
            intervalFirstObserved[origin] = false;
        }
    }

    public static synchronized void observeProvenanceFallback(
            int provenanceDrainCount,
            int provenanceFlags,
            int sceneStateOrdinal,
            boolean centerKnown,
            boolean pendingEpisode) {
        if (!armed) return;
        fallbackClassified++;
        int category = classifyFallback(lastOriginMask, lastSectionDirtyTotal);
        switch (category) {
            case FALLBACK_EXACT_ONLY -> fallbackExactOnly++;
            case FALLBACK_SINGLE_ONLY -> fallbackSingleOnly++;
            case FALLBACK_RANGE_ONLY -> fallbackRangeOnly++;
            case FALLBACK_MIXED -> fallbackMixed++;
            case FALLBACK_NO_RELEVANT -> fallbackNoRelevant++;
            case FALLBACK_UNCLASSIFIED -> fallbackUnclassified++;
            default -> throw new IllegalStateException("Unknown diagnostic fallback category " + category);
        }

        if (!firstFallbackRetained) {
            firstFallbackRetained = true;
            firstFallbackIndex = fallbackClassified;
            firstFallbackOriginMask = lastOriginMask;
            firstFallbackSectionDirtyTotal = lastSectionDirtyTotal;
            firstFallbackLifecycleRelevantEventCount = lastLifecycleRelevantEventCount;
            firstFallbackProvenanceDrainCount = Math.max(0, provenanceDrainCount);
            firstFallbackProvenanceFlags = provenanceFlags;
            firstFallbackSceneStateOrdinal = sceneStateOrdinal;
            firstFallbackCenterKnown = centerKnown;
            firstFallbackPendingEpisode = pendingEpisode;
            System.arraycopy(lastDrainCounts, 0, firstFallbackCounts, 0, ORIGIN_COUNT);
            for (int origin = 1; origin < ORIGIN_COUNT; origin++) {
                if (lastDrainFirstObserved[origin]) {
                    firstFallbackFixtureOrigin = origin;
                    firstFallbackFixtureX = lastDrainFirstX[origin];
                    firstFallbackFixtureY = lastDrainFirstY[origin];
                    firstFallbackFixtureZ = lastDrainFirstZ[origin];
                    firstFallbackFixturePlayer = lastDrainFirstPlayer[origin];
                    break;
                }
            }
        }
    }

    public static synchronized void logFinal() {
        if (!armed) return;
        long relevantTotal = total(relevant);
        long playerTotal = total(fromPlayer);
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev20 P3.9 final section-dirty origin totals: relevant={0}, exactBlock={1}, blockRange={2}, neighborRange={3}, sectionRange={4}, singleSection={5}, unclassified={6}, dirtyFromPlayer={7}, playerExactBlock={8}, playerBlockRange={9}, playerNeighborRange={10}, playerSectionRange={11}, playerSingleSection={12}, playerUnclassified={13}, selfTest={14}, boundedPrimitiveState=true, lifecycleRelevanceChanged=false, productionRendererChanged=false, admissionPolicyChanged=false, thresholdsChanged=false.",
                relevantTotal,
                relevant[ORIGIN_EXACT_BLOCK], relevant[ORIGIN_BLOCK_RANGE], relevant[ORIGIN_NEIGHBOR_RANGE],
                relevant[ORIGIN_SECTION_RANGE], relevant[ORIGIN_SINGLE_SECTION], relevant[ORIGIN_UNCLASSIFIED],
                playerTotal,
                fromPlayer[ORIGIN_EXACT_BLOCK], fromPlayer[ORIGIN_BLOCK_RANGE], fromPlayer[ORIGIN_NEIGHBOR_RANGE],
                fromPlayer[ORIGIN_SECTION_RANGE], fromPlayer[ORIGIN_SINGLE_SECTION], fromPlayer[ORIGIN_UNCLASSIFIED],
                selfTest());
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev20 P3.9 first section-dirty origin fixtures: exactBlock={0}, blockRange={1}, neighborRange={2}, sectionRange={3}, singleSection={4}, unclassified={5}.",
                fixtureString(ORIGIN_EXACT_BLOCK), fixtureString(ORIGIN_BLOCK_RANGE),
                fixtureString(ORIGIN_NEIGHBOR_RANGE), fixtureString(ORIGIN_SECTION_RANGE),
                fixtureString(ORIGIN_SINGLE_SECTION), fixtureString(ORIGIN_UNCLASSIFIED));
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev20 P3.9 final provenance-origin correlation: provenanceFallbacksClassified={0}, exactBlockOnly={1}, singleSectionOnly={2}, rangeNeighborOnly={3}, mixed={4}, noRelevantSectionDirty={5}, unclassifiedInvolved={6}, accountingCoherent={7}, firstRetained={8}, firstFallbackIndex={9}, firstOriginMask={10}, firstOriginNames={11}, firstLifecycleRelevantEvents={12}, firstRelevantSectionDirtyEvents={13}, firstExactBlock={14}, firstBlockRange={15}, firstNeighborRange={16}, firstSectionRange={17}, firstSingleSection={18}, firstUnclassified={19}, firstProvenanceDrainCount={20}, firstProvenanceFlags={21}, firstSceneStateOrdinal={22}, firstSceneStateName={23}, firstCenterKnown={24}, firstPendingEpisode={25}, firstEventOrigin={26}, firstEventSection=({27},{28},{29}), firstEventDirtyFromPlayer={30}, boundedPrimitiveState=true, productionRendererChanged=false, admissionPolicyChanged=false, thresholdsChanged=false.",
                fallbackClassified, fallbackExactOnly, fallbackSingleOnly, fallbackRangeOnly, fallbackMixed,
                fallbackNoRelevant, fallbackUnclassified, fallbackAccountingCoherent(), firstFallbackRetained,
                firstFallbackIndex, firstFallbackOriginMask, maskName(firstFallbackOriginMask),
                firstFallbackLifecycleRelevantEventCount, firstFallbackSectionDirtyTotal,
                firstFallbackCounts[ORIGIN_EXACT_BLOCK], firstFallbackCounts[ORIGIN_BLOCK_RANGE],
                firstFallbackCounts[ORIGIN_NEIGHBOR_RANGE], firstFallbackCounts[ORIGIN_SECTION_RANGE],
                firstFallbackCounts[ORIGIN_SINGLE_SECTION], firstFallbackCounts[ORIGIN_UNCLASSIFIED],
                firstFallbackProvenanceDrainCount, firstFallbackProvenanceFlags, firstFallbackSceneStateOrdinal,
                stateName(firstFallbackSceneStateOrdinal), firstFallbackCenterKnown, firstFallbackPendingEpisode,
                originName(firstFallbackFixtureOrigin), firstFallbackFixtureX, firstFallbackFixtureY,
                firstFallbackFixtureZ, firstFallbackFixturePlayer);
    }

    /** Outermost recognized scope wins so nested helper methods preserve causal origin. */
    public static int inheritOrigin(int current, int requested) {
        int safeCurrent = current == ORIGIN_NONE ? ORIGIN_NONE : sanitizeOrigin(current);
        int safeRequested = sanitizeOrigin(requested);
        return safeCurrent == ORIGIN_NONE ? safeRequested : safeCurrent;
    }

    public static boolean selfTest() {
        int exactMask = 1 << ORIGIN_EXACT_BLOCK;
        int singleMask = 1 << ORIGIN_SINGLE_SECTION;
        int rangeMask = (1 << ORIGIN_BLOCK_RANGE) | (1 << ORIGIN_NEIGHBOR_RANGE) | (1 << ORIGIN_SECTION_RANGE);
        return inheritOrigin(ORIGIN_NONE, ORIGIN_EXACT_BLOCK) == ORIGIN_EXACT_BLOCK
                && inheritOrigin(ORIGIN_EXACT_BLOCK, ORIGIN_BLOCK_RANGE) == ORIGIN_EXACT_BLOCK
                && inheritOrigin(ORIGIN_NEIGHBOR_RANGE, ORIGIN_SECTION_RANGE) == ORIGIN_NEIGHBOR_RANGE
                && classifyFallback(exactMask, 27L) == FALLBACK_EXACT_ONLY
                && classifyFallback(singleMask, 1L) == FALLBACK_SINGLE_ONLY
                && classifyFallback(rangeMask, 3L) == FALLBACK_RANGE_ONLY
                && classifyFallback(exactMask | singleMask, 2L) == FALLBACK_MIXED
                && classifyFallback(0, 0L) == FALLBACK_NO_RELEVANT
                && classifyFallback(1 << ORIGIN_UNCLASSIFIED, 1L) == FALLBACK_UNCLASSIFIED;
    }

    private static int classifyFallback(int originMask, long sectionDirtyTotal) {
        if (sectionDirtyTotal <= 0L || originMask == 0) return FALLBACK_NO_RELEVANT;
        int unclassifiedMask = 1 << ORIGIN_UNCLASSIFIED;
        if ((originMask & unclassifiedMask) != 0) return FALLBACK_UNCLASSIFIED;
        int exactMask = 1 << ORIGIN_EXACT_BLOCK;
        int singleMask = 1 << ORIGIN_SINGLE_SECTION;
        int rangeMask = (1 << ORIGIN_BLOCK_RANGE) | (1 << ORIGIN_NEIGHBOR_RANGE) | (1 << ORIGIN_SECTION_RANGE);
        if (originMask == exactMask) return FALLBACK_EXACT_ONLY;
        if (originMask == singleMask) return FALLBACK_SINGLE_ONLY;
        if ((originMask & ~rangeMask) == 0) return FALLBACK_RANGE_ONLY;
        return FALLBACK_MIXED;
    }

    private static int sanitizeOrigin(int origin) {
        return origin >= ORIGIN_EXACT_BLOCK && origin <= ORIGIN_UNCLASSIFIED
                ? origin : ORIGIN_UNCLASSIFIED;
    }

    private static long total(long[] values) {
        long result = 0L;
        for (int origin = 1; origin < ORIGIN_COUNT; origin++) result += values[origin];
        return result;
    }

    private static boolean fallbackAccountingCoherent() {
        return fallbackClassified == fallbackExactOnly + fallbackSingleOnly + fallbackRangeOnly
                + fallbackMixed + fallbackNoRelevant + fallbackUnclassified;
    }

    private static String fixtureString(int origin) {
        if (!firstObserved[origin]) return "none";
        return "(" + firstX[origin] + "," + firstY[origin] + "," + firstZ[origin]
                + ",player=" + firstPlayer[origin] + ")";
    }

    private static String maskName(int mask) {
        if (mask == 0) return "none";
        StringBuilder result = new StringBuilder();
        for (int origin = 1; origin < ORIGIN_COUNT; origin++) {
            if ((mask & (1 << origin)) == 0) continue;
            if (!result.isEmpty()) result.append('|');
            result.append(originName(origin));
        }
        return result.toString();
    }

    private static String originName(int origin) {
        return switch (origin) {
            case ORIGIN_EXACT_BLOCK -> "EXACT_BLOCK";
            case ORIGIN_BLOCK_RANGE -> "BLOCK_RANGE";
            case ORIGIN_NEIGHBOR_RANGE -> "NEIGHBOR_RANGE";
            case ORIGIN_SECTION_RANGE -> "SECTION_RANGE";
            case ORIGIN_SINGLE_SECTION -> "SINGLE_SECTION";
            case ORIGIN_UNCLASSIFIED -> "UNCLASSIFIED";
            default -> "none";
        };
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
}
