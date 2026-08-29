package dev.obsidian.render.terrain;

import java.util.Arrays;

/** P3.9 dev21 bounded caller-specific diagnostics; observational only. */
public final class PartialRemeshSingleSectionCallerDiagnostics {
    private static final System.Logger LOG = System.getLogger("Obsidian/PartialRemeshSingleSectionCallerDiagnostics");

    public static final int CALLER_NONE = 0;
    public static final int CALLER_LIGHT_UPDATE = 1;
    public static final int CALLER_BIOME_PACKET = 2;
    public static final int CALLER_OTHER = 3;
    private static final int CALLER_COUNT = 4;
    private static final int STACK_CAPACITY = 8;

    private static boolean armed;
    private static final int[] scopeStack = new int[STACK_CAPACITY];
    private static int scopeDepth;
    private static int currentCaller;
    private static int overflowDepth;
    private static int callerBeforeOverflow;
    private static long ownerThreadId;
    private static long crossThreadScopeEvents;
    private static long scopeOverflowEvents;

    private static final long[] relevant = new long[CALLER_COUNT];
    private static final long[] captured = new long[CALLER_COUNT];
    private static final long[] lastDrain = new long[CALLER_COUNT];
    private static final boolean[] firstObserved = new boolean[CALLER_COUNT];
    private static final int[] firstX = new int[CALLER_COUNT];
    private static final int[] firstY = new int[CALLER_COUNT];
    private static final int[] firstZ = new int[CALLER_COUNT];
    private static final boolean[] intervalObserved = new boolean[CALLER_COUNT];
    private static final int[] intervalX = new int[CALLER_COUNT];
    private static final int[] intervalY = new int[CALLER_COUNT];
    private static final int[] intervalZ = new int[CALLER_COUNT];
    private static final boolean[] lastDrainObserved = new boolean[CALLER_COUNT];
    private static final int[] lastDrainX = new int[CALLER_COUNT];
    private static final int[] lastDrainY = new int[CALLER_COUNT];
    private static final int[] lastDrainZ = new int[CALLER_COUNT];
    private static int lastMask;
    private static long lastTotal;
    private static int lastLifecycleRelevantEvents;

    private static long fallbackClassified;
    private static long lightOnly;
    private static long biomeOnly;
    private static long otherOnly;
    private static long mixed;
    private static long unavailable;

    private static boolean firstFallbackRetained;
    private static long firstFallbackIndex;
    private static int firstFallbackMask;
    private static long firstFallbackTotal;
    private static int firstFallbackLifecycleEvents;
    private static int firstFallbackDrainCount;
    private static int firstFallbackFlags;
    private static int firstFallbackState = -1;
    private static boolean firstFallbackCenterKnown;
    private static boolean firstFallbackPending;
    private static final long[] firstFallbackCounts = new long[CALLER_COUNT];
    private static int firstFallbackCaller;
    private static int firstFallbackX;
    private static int firstFallbackY;
    private static int firstFallbackZ;

    private PartialRemeshSingleSectionCallerDiagnostics() { }

    public static synchronized void begin() {
        armed = true;
        Arrays.fill(scopeStack, CALLER_NONE);
        scopeDepth = 0;
        currentCaller = CALLER_NONE;
        overflowDepth = 0;
        callerBeforeOverflow = CALLER_NONE;
        ownerThreadId = 0L;
        crossThreadScopeEvents = 0L;
        scopeOverflowEvents = 0L;
        Arrays.fill(relevant, 0L);
        Arrays.fill(captured, 0L);
        Arrays.fill(lastDrain, 0L);
        Arrays.fill(firstObserved, false);
        Arrays.fill(firstX, 0);
        Arrays.fill(firstY, 0);
        Arrays.fill(firstZ, 0);
        Arrays.fill(intervalObserved, false);
        Arrays.fill(intervalX, 0);
        Arrays.fill(intervalY, 0);
        Arrays.fill(intervalZ, 0);
        Arrays.fill(lastDrainObserved, false);
        Arrays.fill(lastDrainX, 0);
        Arrays.fill(lastDrainY, 0);
        Arrays.fill(lastDrainZ, 0);
        lastMask = 0;
        lastTotal = 0L;
        lastLifecycleRelevantEvents = 0;
        fallbackClassified = lightOnly = biomeOnly = otherOnly = mixed = unavailable = 0L;
        firstFallbackRetained = false;
        firstFallbackIndex = 0L;
        firstFallbackMask = 0;
        firstFallbackTotal = 0L;
        firstFallbackLifecycleEvents = 0;
        firstFallbackDrainCount = 0;
        firstFallbackFlags = 0;
        firstFallbackState = -1;
        firstFallbackCenterKnown = false;
        firstFallbackPending = false;
        Arrays.fill(firstFallbackCounts, 0L);
        firstFallbackCaller = CALLER_NONE;
        firstFallbackX = firstFallbackY = firstFallbackZ = 0;
    }

    public static synchronized void enterCaller(int requested) {
        int caller = sanitize(requested);
        long tid = Thread.currentThread().threadId();
        if (scopeDepth == 0 && overflowDepth == 0) ownerThreadId = tid;
        else if (ownerThreadId != tid) {
            crossThreadScopeEvents++;
            return;
        }
        if (overflowDepth > 0) {
            overflowDepth++;
            currentCaller = CALLER_OTHER;
            return;
        }
        if (scopeDepth >= scopeStack.length) {
            callerBeforeOverflow = currentCaller;
            overflowDepth = 1;
            scopeOverflowEvents++;
            currentCaller = CALLER_OTHER;
            return;
        }
        scopeStack[scopeDepth++] = currentCaller;
        currentCaller = inheritCaller(currentCaller, caller);
    }

    public static synchronized void exitCaller() {
        long tid = Thread.currentThread().threadId();
        if (ownerThreadId != 0L && ownerThreadId != tid) {
            crossThreadScopeEvents++;
            return;
        }
        if (overflowDepth > 0) {
            overflowDepth--;
            if (overflowDepth == 0) {
                currentCaller = callerBeforeOverflow;
                callerBeforeOverflow = CALLER_NONE;
            }
            resetOwnerIfIdle();
            return;
        }
        if (scopeDepth <= 0) {
            currentCaller = CALLER_NONE;
            ownerThreadId = 0L;
            return;
        }
        currentCaller = scopeStack[--scopeDepth];
        resetOwnerIfIdle();
    }

    /** Called only after lifecycle relevance and outer SINGLE_SECTION origin are proven. */
    public static synchronized void observeRelevantSingleSection(int x, int y, int z) {
        if (!armed) return;
        int caller = currentCallerForThisThread();
        relevant[caller]++;
        if (!firstObserved[caller]) {
            firstObserved[caller] = true;
            firstX[caller] = x;
            firstY[caller] = y;
            firstZ[caller] = z;
        }
        if (!intervalObserved[caller]) {
            intervalObserved[caller] = true;
            intervalX[caller] = x;
            intervalY[caller] = y;
            intervalZ[caller] = z;
        }
    }

    public static synchronized void captureLifecycleDrain(int lifecycleRelevantEvents) {
        if (!armed) return;
        lastMask = 0;
        lastTotal = 0L;
        lastLifecycleRelevantEvents = Math.max(0, lifecycleRelevantEvents);
        for (int caller = 1; caller < CALLER_COUNT; caller++) {
            long delta = relevant[caller] - captured[caller];
            if (delta < 0L) delta = 0L;
            lastDrain[caller] = delta;
            captured[caller] = relevant[caller];
            if (delta > 0L) {
                lastMask |= 1 << caller;
                lastTotal += delta;
            }
            lastDrainObserved[caller] = intervalObserved[caller];
            if (intervalObserved[caller]) {
                lastDrainX[caller] = intervalX[caller];
                lastDrainY[caller] = intervalY[caller];
                lastDrainZ[caller] = intervalZ[caller];
            }
            intervalObserved[caller] = false;
        }
    }

    /** Called for every existing FALLBACK_PROVENANCE; compare with dev20 outer correlation at close. */
    public static synchronized void observeProvenanceFallback(
            int drainCount, int flags, int stateOrdinal, boolean centerKnown, boolean pendingEpisode) {
        if (!armed) return;
        fallbackClassified++;
        int category = classify(lastMask, lastTotal);
        switch (category) {
            case 1 -> lightOnly++;
            case 2 -> biomeOnly++;
            case 3 -> otherOnly++;
            case 4 -> mixed++;
            case 5 -> unavailable++;
            default -> throw new IllegalStateException("Unknown dev21 caller fallback category " + category);
        }
        if (!firstFallbackRetained) {
            firstFallbackRetained = true;
            firstFallbackIndex = fallbackClassified;
            firstFallbackMask = lastMask;
            firstFallbackTotal = lastTotal;
            firstFallbackLifecycleEvents = lastLifecycleRelevantEvents;
            firstFallbackDrainCount = Math.max(0, drainCount);
            firstFallbackFlags = flags;
            firstFallbackState = stateOrdinal;
            firstFallbackCenterKnown = centerKnown;
            firstFallbackPending = pendingEpisode;
            System.arraycopy(lastDrain, 0, firstFallbackCounts, 0, CALLER_COUNT);
            for (int caller = 1; caller < CALLER_COUNT; caller++) {
                if (lastDrainObserved[caller]) {
                    firstFallbackCaller = caller;
                    firstFallbackX = lastDrainX[caller];
                    firstFallbackY = lastDrainY[caller];
                    firstFallbackZ = lastDrainZ[caller];
                    break;
                }
            }
        }
    }

    public static synchronized void logFinal() {
        if (!armed) return;
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev21 P3.9 final single-section caller totals: relevant={0}, lightUpdate={1}, biomePacket={2}, other={3}, scopeCrossThreadEvents={4}, scopeOverflowEvents={5}, selfTest={6}, boundedPrimitiveState=true, lifecycleRelevanceChanged=false, productionRendererChanged=false, admissionPolicyChanged=false, thresholdsChanged=false.",
                total(relevant), relevant[CALLER_LIGHT_UPDATE], relevant[CALLER_BIOME_PACKET], relevant[CALLER_OTHER],
                crossThreadScopeEvents, scopeOverflowEvents, selfTest());
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev21 P3.9 first single-section caller fixtures: lightUpdate={0}, biomePacket={1}, other={2}.",
                fixture(CALLER_LIGHT_UPDATE), fixture(CALLER_BIOME_PACKET), fixture(CALLER_OTHER));
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev21 P3.9 final provenance-caller correlation: provenanceFallbacksClassified={0}, lightUpdateOnly={1}, biomePacketOnly={2}, otherOnly={3}, mixed={4}, unavailable={5}, accountingCoherent={6}, firstRetained={7}, firstFallbackIndex={8}, firstCallerMask={9}, firstCallerNames={10}, firstLifecycleRelevantEvents={11}, firstRelevantSingleSectionEvents={12}, firstLightUpdate={13}, firstBiomePacket={14}, firstOther={15}, firstProvenanceDrainCount={16}, firstProvenanceFlags={17}, firstSceneStateOrdinal={18}, firstSceneStateName={19}, firstCenterKnown={20}, firstPendingEpisode={21}, firstEventCaller={22}, firstEventSection=({23},{24},{25}), boundedPrimitiveState=true, productionRendererChanged=false, admissionPolicyChanged=false, thresholdsChanged=false.",
                fallbackClassified, lightOnly, biomeOnly, otherOnly, mixed, unavailable, accountingCoherent(),
                firstFallbackRetained, firstFallbackIndex, firstFallbackMask, maskName(firstFallbackMask),
                firstFallbackLifecycleEvents, firstFallbackTotal, firstFallbackCounts[CALLER_LIGHT_UPDATE],
                firstFallbackCounts[CALLER_BIOME_PACKET], firstFallbackCounts[CALLER_OTHER],
                firstFallbackDrainCount, firstFallbackFlags, firstFallbackState, stateName(firstFallbackState),
                firstFallbackCenterKnown, firstFallbackPending, callerName(firstFallbackCaller),
                firstFallbackX, firstFallbackY, firstFallbackZ);
    }

    public static int inheritCaller(int current, int requested) {
        int safeCurrent = current == CALLER_NONE ? CALLER_NONE : sanitize(current);
        int safeRequested = sanitize(requested);
        return safeCurrent == CALLER_NONE ? safeRequested : safeCurrent;
    }

    public static boolean selfTest() {
        int light = 1 << CALLER_LIGHT_UPDATE;
        int biome = 1 << CALLER_BIOME_PACKET;
        int other = 1 << CALLER_OTHER;
        return inheritCaller(CALLER_NONE, CALLER_LIGHT_UPDATE) == CALLER_LIGHT_UPDATE
                && inheritCaller(CALLER_LIGHT_UPDATE, CALLER_BIOME_PACKET) == CALLER_LIGHT_UPDATE
                && classify(light, 1L) == 1
                && classify(biome, 1L) == 2
                && classify(other, 1L) == 3
                && classify(light | biome, 2L) == 4
                && classify(0, 0L) == 5;
    }

    private static int currentCallerForThisThread() {
        long tid = Thread.currentThread().threadId();
        if (scopeDepth <= 0 || ownerThreadId != tid || currentCaller == CALLER_NONE) return CALLER_OTHER;
        return sanitize(currentCaller);
    }

    private static void resetOwnerIfIdle() {
        if (scopeDepth == 0 && overflowDepth == 0) {
            ownerThreadId = 0L;
            currentCaller = CALLER_NONE;
        }
    }

    private static int classify(int mask, long count) {
        if (count <= 0L || mask == 0) return 5;
        if (mask == (1 << CALLER_LIGHT_UPDATE)) return 1;
        if (mask == (1 << CALLER_BIOME_PACKET)) return 2;
        if (mask == (1 << CALLER_OTHER)) return 3;
        return 4;
    }

    private static int sanitize(int caller) {
        return caller >= CALLER_LIGHT_UPDATE && caller <= CALLER_OTHER ? caller : CALLER_OTHER;
    }

    private static long total(long[] values) {
        long result = 0L;
        for (int i = 1; i < values.length; i++) result += values[i];
        return result;
    }

    private static boolean accountingCoherent() {
        return fallbackClassified == lightOnly + biomeOnly + otherOnly + mixed + unavailable;
    }

    private static String fixture(int caller) {
        if (!firstObserved[caller]) return "none";
        return "(" + firstX[caller] + "," + firstY[caller] + "," + firstZ[caller] + ")";
    }

    private static String callerName(int caller) {
        return switch (caller) {
            case CALLER_LIGHT_UPDATE -> "LIGHT_UPDATE";
            case CALLER_BIOME_PACKET -> "BIOME_PACKET";
            case CALLER_OTHER -> "OTHER_SINGLE_SECTION";
            default -> "none";
        };
    }

    private static String maskName(int mask) {
        if (mask == 0) return "none";
        StringBuilder value = new StringBuilder();
        for (int caller = 1; caller < CALLER_COUNT; caller++) {
            if ((mask & (1 << caller)) == 0) continue;
            if (!value.isEmpty()) value.append('|');
            value.append(callerName(caller));
        }
        return value.toString();
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
