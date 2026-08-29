package dev.obsidian.render.terrain;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Bounded render-thread collector for the frozen A-0159 matched shadow experiment; dev17 diagnostics. */
public final class PartialRemeshExperimentTelemetry {
    public static final int CAPACITY = 512;

    public static final int FALLBACK_GLOBAL_LIFECYCLE = 1;
    public static final int FALLBACK_PROVENANCE = 1 << 1;
    public static final int FALLBACK_MULTI_SECTION = 1 << 2;
    public static final int FALLBACK_HALO_OR_BOUNDARY = 1 << 3;
    public static final int FALLBACK_ALL_SLICES = 1 << 4;
    public static final int FALLBACK_PENDING_EPISODE = 1 << 5;
    public static final int FALLBACK_NOT_LIVE = 1 << 6;
    private static final int ALL_FALLBACK_REASONS = FALLBACK_GLOBAL_LIFECYCLE | FALLBACK_PROVENANCE
            | FALLBACK_MULTI_SECTION | FALLBACK_HALO_OR_BOUNDARY | FALLBACK_ALL_SLICES
            | FALLBACK_PENDING_EPISODE | FALLBACK_NOT_LIVE;

    public record Distribution(long p50, long p95, long p99, long max, long mean) { }

    public record Snapshot(
            boolean armed, long durationNs, long observed, int retained, long overflow,
            long localizedAdmissions, long completedEpisodes, long fallbackEpisodes,
            long oneSliceEpisodes, long twoSliceEpisodes, long threeSliceEpisodes,
            long coalescedEpisodes, long exactEpisodes, long correctnessFailures,
            long unselectedChangeFailures, long determinismFailures,
            long atMostTwoSliceEpisodes, long forcedBoundarySplits,
            long selectedSourceQuads, long selectedReferenceFaces,
            long topologyFragments, long mergeCandidateFragments,
            long passthroughIdentities, long mergedIdentities, long mergedCoveredFaces,
            long outputQuads, long projectedReplacementBytes, long controlOutputBytes,
            Distribution shadowCpuNs, Distribution controlCpuNs, Distribution cpuRatioPermille,
            Distribution uploadRatioPermille, Distribution selectedCellPermille,
            Distribution inflationPermille, int maxInflationPermille, long meanInflationPermille,
            int fixedMetadataBytesPerSection, int sliceIdentities,
            int fallbackReasonMask,
            long fallbackGlobalLifecycle, long fallbackProvenance, long fallbackMultiSection,
            long fallbackHaloOrBoundary, long fallbackAllSlices, long fallbackPendingEpisode, long fallbackNotLive,
            long firstFailureEpisodeId, int firstFailureSectionX, int firstFailureSectionY, int firstFailureSectionZ,
            int firstFailureSliceMask, int firstFailureEditCount, int firstFailureCode, int firstFailureIndex,
            boolean firstFailureDeterministic,
            long gcCollectionDelta, long gcTimeDeltaMs,
            boolean collectorSelfTestPassed, boolean dirtySelfTestPassed, boolean shadowSelfTestPassed) {

        public long atMostTwoSlicePermille() {
            return completedEpisodes == 0L ? 0L : atMostTwoSliceEpisodes * 1000L / completedEpisodes;
        }

        public boolean fallbackAccountingCoherent() {
            return fallbackEpisodes == fallbackGlobalLifecycle + fallbackProvenance + fallbackMultiSection
                    + fallbackHaloOrBoundary + fallbackAllSlices + fallbackPendingEpisode + fallbackNotLive;
        }

        public String firstFailureName() { return PartialRemeshShadowResult.failureName(firstFailureCode); }

        public boolean percentileAccountingCoherent() {
            return observed == retained + overflow
                    && fallbackAccountingCoherent()
                    && retained >= 0 && retained <= CAPACITY
                    && coherent(shadowCpuNs) && coherent(controlCpuNs)
                    && coherent(cpuRatioPermille) && coherent(uploadRatioPermille)
                    && coherent(selectedCellPermille) && coherent(inflationPermille);
        }

        private static boolean coherent(Distribution d) {
            return d != null && d.p50 >= 0L && d.p95 >= d.p50 && d.p99 >= d.p95 && d.max >= d.p99;
        }

        public boolean thresholdsPassed() {
            return armed && percentileAccountingCoherent()
                    && collectorSelfTestPassed && dirtySelfTestPassed && shadowSelfTestPassed
                    && completedEpisodes >= 32L
                    && oneSliceEpisodes >= 16L
                    && twoSliceEpisodes >= 8L
                    && coalescedEpisodes >= 1L
                    && fallbackEpisodes >= 1L
                    && exactEpisodes == completedEpisodes
                    && correctnessFailures == 0L
                    && unselectedChangeFailures == 0L
                    && determinismFailures == 0L
                    && selectedCellPermille.p50 <= 500L
                    && atMostTwoSlicePermille() >= 750L
                    && cpuRatioPermille.p50 <= 600L
                    && cpuRatioPermille.p95 <= 800L
                    && uploadRatioPermille.p50 <= 600L
                    && uploadRatioPermille.p95 <= 800L
                    && fixedMetadataBytesPerSection <= 1024
                    && sliceIdentities == 4
                    && meanInflationPermille <= 50L
                    && maxInflationPermille <= 100;
        }
    }

    private final long[] shadowNs = new long[CAPACITY];
    private final long[] controlNs = new long[CAPACITY];
    private final long[] cpuRatio = new long[CAPACITY];
    private final long[] uploadRatio = new long[CAPACITY];
    private final long[] selectedCellRatio = new long[CAPACITY];
    private final long[] inflation = new long[CAPACITY];

    private boolean armed;
    private long startNs;
    private long observed;
    private int retained;
    private long overflow;
    private long localizedAdmissions;
    private long completed;
    private long fallbacks;
    private long oneSlice;
    private long twoSlice;
    private long threeSlice;
    private long coalesced;
    private long exact;
    private long correctnessFailures;
    private long unselectedFailures;
    private long determinismFailures;
    private long atMostTwo;
    private long forcedSplits;
    private long selectedSource;
    private long selectedReference;
    private long topologyFragments;
    private long candidateFragments;
    private long passthrough;
    private long merged;
    private long mergedCovered;
    private long outputQuads;
    private long projectedBytes;
    private long controlBytes;
    private long inflationSum;
    private int inflationMax;
    private int fallbackReasonMask;
    private long fallbackGlobalLifecycle;
    private long fallbackProvenance;
    private long fallbackMultiSection;
    private long fallbackHaloOrBoundary;
    private long fallbackAllSlices;
    private long fallbackPendingEpisode;
    private long fallbackNotLive;
    private long firstFailureEpisodeId;
    private int firstFailureSectionX;
    private int firstFailureSectionY;
    private int firstFailureSectionZ;
    private int firstFailureSliceMask;
    private int firstFailureEditCount;
    private int firstFailureCode;
    private int firstFailureIndex;
    private boolean firstFailureDeterministic = true;
    private long gcStartCount;
    private long gcStartTimeMs;

    public void begin() {
        armed = true;
        startNs = System.nanoTime();
        observed = overflow = 0L;
        retained = 0;
        localizedAdmissions = completed = fallbacks = 0L;
        oneSlice = twoSlice = threeSlice = coalesced = exact = 0L;
        correctnessFailures = unselectedFailures = determinismFailures = atMostTwo = 0L;
        forcedSplits = selectedSource = selectedReference = topologyFragments = candidateFragments = 0L;
        passthrough = merged = mergedCovered = outputQuads = projectedBytes = controlBytes = 0L;
        inflationSum = 0L;
        inflationMax = 0;
        fallbackReasonMask = 0;
        fallbackGlobalLifecycle = fallbackProvenance = fallbackMultiSection = 0L;
        fallbackHaloOrBoundary = fallbackAllSlices = fallbackPendingEpisode = fallbackNotLive = 0L;
        firstFailureEpisodeId = 0L;
        firstFailureSectionX = firstFailureSectionY = firstFailureSectionZ = 0;
        firstFailureSliceMask = firstFailureEditCount = firstFailureCode = 0;
        firstFailureIndex = -1;
        firstFailureDeterministic = true;
        long[] gc = gcTotals();
        gcStartCount = gc[0];
        gcStartTimeMs = gc[1];
    }

    public boolean armed() { return armed; }
    public void recordAdmission() { if (armed) localizedAdmissions++; }
    public void recordFallback(int reasonMask) {
        if (!armed) return;
        if (Integer.bitCount(reasonMask) != 1 || (reasonMask & ~ALL_FALLBACK_REASONS) != 0) {
            throw new IllegalArgumentException("dev17 fallback reason must be exactly one known bit: " + reasonMask);
        }
        fallbacks++;
        fallbackReasonMask |= reasonMask;
        switch (reasonMask) {
            case FALLBACK_GLOBAL_LIFECYCLE -> fallbackGlobalLifecycle++;
            case FALLBACK_PROVENANCE -> fallbackProvenance++;
            case FALLBACK_MULTI_SECTION -> fallbackMultiSection++;
            case FALLBACK_HALO_OR_BOUNDARY -> fallbackHaloOrBoundary++;
            case FALLBACK_ALL_SLICES -> fallbackAllSlices++;
            case FALLBACK_PENDING_EPISODE -> fallbackPendingEpisode++;
            case FALLBACK_NOT_LIVE -> fallbackNotLive++;
            default -> throw new IllegalStateException("unreachable fallback reason");
        }
    }

    public void recordCompleted(
            int sectionX, int sectionY, int sectionZ,
            PartialRemeshShadowRequest request,
            PartialRemeshShadowResult result,
            long controlExecutionNs,
            long controlUploadBytes,
            boolean deterministic) {
        if (!armed || request == null || result == null) return;
        completed++;
        int slices = request.selectedSliceCount();
        if (slices == 1) oneSlice++; else if (slices == 2) twoSlice++; else if (slices == 3) threeSlice++;
        if (slices <= 2) atMostTwo++;
        if (request.coalesced()) coalesced++;
        boolean completedExact = result.exact() && deterministic;
        if (completedExact) {
            exact++;
        } else {
            correctnessFailures++;
            if (shouldRetainFirstFailure(firstFailureEpisodeId, completedExact, deterministic)) {
                firstFailureEpisodeId = request.episodeId();
                firstFailureSectionX = sectionX;
                firstFailureSectionY = sectionY;
                firstFailureSectionZ = sectionZ;
                firstFailureSliceMask = request.sliceMask();
                firstFailureEditCount = request.editCount();
                firstFailureCode = result.failureCode() == PartialRemeshShadowResult.FAILURE_NONE
                        ? PartialRemeshShadowResult.FAILURE_ACCOUNTING : result.failureCode();
                firstFailureIndex = result.failureCode() == PartialRemeshShadowResult.FAILURE_NONE
                        ? -2 : result.failureIndex();
                firstFailureDeterministic = deterministic;
            }
        }
        if (!result.unselectedStable()) unselectedFailures++;
        if (!deterministic) determinismFailures++;
        forcedSplits += result.forcedBoundarySplits();
        selectedSource += result.selectedSourceQuads();
        selectedReference += result.selectedReferenceFaces();
        topologyFragments += result.topologyFragments();
        candidateFragments += result.mergeCandidateFragments();
        passthrough += result.passthroughIdentities();
        merged += result.mergedIdentities();
        mergedCovered += result.mergedCoveredSourceFaces();
        outputQuads += result.outputQuads();
        projectedBytes += result.outputBytes();
        controlBytes += Math.max(0L, controlUploadBytes);
        inflationSum += result.inflationPermille();
        inflationMax = Math.max(inflationMax, result.inflationPermille());

        observed++;
        if (retained >= CAPACITY) {
            overflow++;
            return;
        }
        long shadow = Math.max(0L, result.buildTimeNs());
        long control = Math.max(1L, controlExecutionNs);
        long controlUpload = Math.max(1L, controlUploadBytes);
        shadowNs[retained] = shadow;
        controlNs[retained] = control;
        cpuRatio[retained] = shadow * 1000L / control;
        uploadRatio[retained] = result.outputBytes() * 1000L / controlUpload;
        selectedCellRatio[retained] = (long) result.selectedCells() * 1000L / SectionSnapshot.INTERIOR_CELL_COUNT;
        inflation[retained] = Math.max(0, result.inflationPermille());
        retained++;
    }

    public Snapshot snapshot() {
        long[] gc = gcTotals();
        return new Snapshot(armed, armed ? Math.max(0L, System.nanoTime() - startNs) : 0L,
                observed, retained, overflow, localizedAdmissions, completed, fallbacks,
                oneSlice, twoSlice, threeSlice, coalesced, exact, correctnessFailures,
                unselectedFailures, determinismFailures, atMostTwo, forcedSplits,
                selectedSource, selectedReference, topologyFragments, candidateFragments,
                passthrough, merged, mergedCovered, outputQuads, projectedBytes, controlBytes,
                distribution(shadowNs, retained), distribution(controlNs, retained),
                distribution(cpuRatio, retained), distribution(uploadRatio, retained),
                distribution(selectedCellRatio, retained), distribution(inflation, retained),
                inflationMax, completed == 0L ? 0L : inflationSum / completed,
                PartialRemeshSliceTruth.METADATA_BYTES_PER_SECTION, PartialRemeshDirtyProvenance.SLICE_COUNT,
                fallbackReasonMask,
                fallbackGlobalLifecycle, fallbackProvenance, fallbackMultiSection,
                fallbackHaloOrBoundary, fallbackAllSlices, fallbackPendingEpisode, fallbackNotLive,
                firstFailureEpisodeId, firstFailureSectionX, firstFailureSectionY, firstFailureSectionZ,
                firstFailureSliceMask, firstFailureEditCount, firstFailureCode, firstFailureIndex,
                firstFailureDeterministic,
                Math.max(0L, gc[0] - gcStartCount), Math.max(0L, gc[1] - gcStartTimeMs),
                selfTest(), PartialRemeshDirtyProvenance.selfTest(), PartialRemeshShadowResult.selfTest());
    }

    private static Distribution distribution(long[] source, int count) {
        if (count <= 0) return new Distribution(0L, 0L, 0L, 0L, 0L);
        long[] copy = Arrays.copyOf(source, count);
        Arrays.sort(copy);
        long sum = 0L;
        for (long value : copy) sum += value;
        return new Distribution(percentile(copy, 50), percentile(copy, 95), percentile(copy, 99),
                copy[count - 1], sum / count);
    }

    private static long percentile(long[] sorted, int percentile) {
        int rank = (int) Math.ceil((percentile / 100.0) * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, rank))];
    }

    private static long[] gcTotals() {
        long count = 0L;
        long time = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = bean.getCollectionCount();
            long t = bean.getCollectionTime();
            if (c > 0L) count += c;
            if (t > 0L) time += t;
        }
        return new long[] { count, time };
    }

    private static boolean shouldRetainFirstFailure(long currentEpisodeId, boolean completedExact, boolean deterministic) {
        return currentEpisodeId == 0L && (!completedExact || !deterministic);
    }

    private static boolean selfTest() {
        long[] fixture = { 5, 1, 4, 2, 3 };
        Distribution d = distribution(fixture, fixture.length);
        int allReasons = FALLBACK_GLOBAL_LIFECYCLE | FALLBACK_PROVENANCE | FALLBACK_MULTI_SECTION
                | FALLBACK_HALO_OR_BOUNDARY | FALLBACK_ALL_SLICES | FALLBACK_PENDING_EPISODE | FALLBACK_NOT_LIVE;
        return d.p50 == 3L && d.p95 == 5L && d.p99 == 5L && d.max == 5L
                && allReasons == ALL_FALLBACK_REASONS && Integer.bitCount(allReasons) == 7
                && shouldRetainFirstFailure(0L, false, true)
                && shouldRetainFirstFailure(0L, true, false)
                && !shouldRetainFirstFailure(1L, false, true)
                && !shouldRetainFirstFailure(0L, true, true)
                && PartialRemeshSliceTruth.METADATA_BYTES_PER_SECTION <= 1024;
    }
}
