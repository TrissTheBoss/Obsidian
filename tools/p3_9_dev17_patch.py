from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(rel, old, new):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected one match, found {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(rel, old, new):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"{rel}: missing {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


# Version.
replace_once("gradle.properties", "mod_version=0.3.0-phase3-dev16", "mod_version=0.3.0-phase3-dev17")

# Correct the dev16 shadow reference assertion to the permanent P3.7 oracle semantics.
shadow = "src/client/java/dev/obsidian/render/terrain/PartialRemeshShadowResult.java"
replace_once(shadow, "P3.9 dev16 shadow-only fixed-slice projection", "P3.9 dev17 diagnostic/corrected shadow-only fixed-slice projection")
replace_once(
    shadow,
    "    public static final int FAILURE_EXCEPTION = 7;\n",
    "    public static final int FAILURE_EXCEPTION = 7;\n"
    "    public static final int FAILURE_OPTIMIZED_WITHOUT_REFERENCE = 8;\n")
replace_once(
    shadow,
    "            int selectedReference = 0;\n"
    "            int selectedVisibility = 0;\n"
    "            for (int y = 0; y < 16; y++) {\n"
    "                if (!selected(mask, y >>> 2)) continue;\n"
    "                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {\n"
    "                    int bit = ((y * 16) + z) * 16 + x;\n"
    "                    for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {\n"
    "                        boolean visible = visibility.hasFace(x, y, z, direction);\n"
    "                        boolean ref = (scratch.referenceBits[direction * BinarySectionVisibility.WORDS_PER_DIRECTION + (bit >>> 6)]\n"
    "                                & (1L << (bit & 63))) != 0L;\n"
    "                        if (visible) selectedVisibility++;\n"
    "                        if (ref) selectedReference++;\n"
    "                        if (visible != ref) fail(FAILURE_REFERENCE_VISIBILITY, bit | (direction << 12));\n"
    "                    }\n"
    "                }\n"
    "            }\n"
    "            if (selectedReference != selectedVisibility) fail(FAILURE_REFERENCE_VISIBILITY, -1);\n",
    "            int selectedReference = 0;\n"
    "            for (int face = 0; face < reference.faceCount(); face++) {\n"
    "                int packed = reference.packedFace(face);\n"
    "                int x = packed & 0xF;\n"
    "                int y = (packed >>> 4) & 0xF;\n"
    "                int z = (packed >>> 8) & 0xF;\n"
    "                int direction = (packed >>> 12) & 0x7;\n"
    "                if (!selected(mask, y >>> 2)) continue;\n"
    "                selectedReference++;\n"
    "                if (!visibility.hasFace(x, y, z, direction)) {\n"
    "                    fail(FAILURE_REFERENCE_VISIBILITY, packed);\n"
    "                }\n"
    "            }\n"
    "            for (int y = 0; y < 16; y++) {\n"
    "                if (!selected(mask, y >>> 2)) continue;\n"
    "                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {\n"
    "                    int bit = ((y * 16) + z) * 16 + x;\n"
    "                    for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {\n"
    "                        if (renderKeys.sourceQuad(x, y, z, direction) < 0) continue;\n"
    "                        boolean ref = (scratch.referenceBits[direction * BinarySectionVisibility.WORDS_PER_DIRECTION + (bit >>> 6)]\n"
    "                                & (1L << (bit & 63))) != 0L;\n"
    "                        if (!ref) fail(FAILURE_OPTIMIZED_WITHOUT_REFERENCE, packFace(x, y, z, direction));\n"
    "                    }\n"
    "                }\n"
    "            }\n")
replace_once(
    shadow,
    "    private static boolean selected(int mask, int slice) { return (mask & (1 << slice)) != 0; }\n",
    "    private static boolean selected(int mask, int slice) { return (mask & (1 << slice)) != 0; }\n"
    "    private static int packFace(int x, int y, int z, int direction) {\n"
    "        return x | (y << 4) | (z << 8) | (direction << 12);\n"
    "    }\n"
    "    private static boolean permanentReferenceContractSatisfied(boolean reference, boolean visible, boolean mapped) {\n"
    "        return (!reference || visible) && (!mapped || reference);\n"
    "    }\n")
replace_once(
    shadow,
    "                && fragmentArea(BinarySectionVisibility.NORTH, 0, 3, 5, 2, 1) == 5\n"
    "                && allFragments(BinarySectionVisibility.UP, 7, 0, 16) == 1;\n",
    "                && fragmentArea(BinarySectionVisibility.NORTH, 0, 3, 5, 2, 1) == 5\n"
    "                && allFragments(BinarySectionVisibility.UP, 7, 0, 16) == 1\n"
    "                && permanentReferenceContractSatisfied(false, true, false)\n"
    "                && permanentReferenceContractSatisfied(true, true, false)\n"
    "                && !permanentReferenceContractSatisfied(true, false, false)\n"
    "                && !permanentReferenceContractSatisfied(false, true, true)\n"
    "                && \"optimized-without-reference\".equals(failureName(FAILURE_OPTIMIZED_WITHOUT_REFERENCE));\n")
replace_once(
    shadow,
    "    public long episodeId() { return episodeId; }\n",
    "    public static String failureName(int code) {\n"
    "        return switch (code) {\n"
    "            case FAILURE_NONE -> \"none\";\n"
    "            case FAILURE_UNSELECTED_CHANGED -> \"unselected-changed\";\n"
    "            case FAILURE_REFERENCE_VISIBILITY -> \"reference-visibility\";\n"
    "            case FAILURE_SOURCE_DUPLICATE -> \"source-duplicate\";\n"
    "            case FAILURE_SOURCE_MISSING -> \"source-missing\";\n"
    "            case FAILURE_MERGED_IDENTITY -> \"merged-identity\";\n"
    "            case FAILURE_ACCOUNTING -> \"accounting\";\n"
    "            case FAILURE_EXCEPTION -> \"exception\";\n"
    "            case FAILURE_OPTIMIZED_WITHOUT_REFERENCE -> \"optimized-without-reference\";\n"
    "            default -> \"unknown-\" + code;\n"
    "        };\n"
    "    }\n\n"
    "    public long episodeId() { return episodeId; }\n")

# Retain bounded per-fallback counts and the first failing completed episode.
telemetry = "src/client/java/dev/obsidian/render/terrain/PartialRemeshExperimentTelemetry.java"
replace_once(telemetry, "frozen A-0159 matched shadow experiment", "frozen A-0159 matched shadow experiment; dev17 diagnostics")
replace_once(
    telemetry,
    "            int fixedMetadataBytesPerSection, int sliceIdentities,\n"
    "            int fallbackReasonMask, long gcCollectionDelta, long gcTimeDeltaMs,\n"
    "            boolean collectorSelfTestPassed, boolean dirtySelfTestPassed, boolean shadowSelfTestPassed) {\n",
    "            int fixedMetadataBytesPerSection, int sliceIdentities,\n"
    "            int fallbackReasonMask,\n"
    "            long fallbackGlobalLifecycle, long fallbackProvenance, long fallbackMultiSection,\n"
    "            long fallbackHaloOrBoundary, long fallbackAllSlices, long fallbackPendingEpisode, long fallbackNotLive,\n"
    "            long firstFailureEpisodeId, int firstFailureSectionX, int firstFailureSectionY, int firstFailureSectionZ,\n"
    "            int firstFailureSliceMask, int firstFailureEditCount, int firstFailureCode, int firstFailureIndex,\n"
    "            boolean firstFailureDeterministic,\n"
    "            long gcCollectionDelta, long gcTimeDeltaMs,\n"
    "            boolean collectorSelfTestPassed, boolean dirtySelfTestPassed, boolean shadowSelfTestPassed) {\n")
replace_once(
    telemetry,
    "        public boolean percentileAccountingCoherent() {\n"
    "            return observed == retained + overflow\n",
    "        public boolean fallbackAccountingCoherent() {\n"
    "            return fallbackEpisodes == fallbackGlobalLifecycle + fallbackProvenance + fallbackMultiSection\n"
    "                    + fallbackHaloOrBoundary + fallbackAllSlices + fallbackPendingEpisode + fallbackNotLive;\n"
    "        }\n\n"
    "        public String firstFailureName() { return PartialRemeshShadowResult.failureName(firstFailureCode); }\n\n"
    "        public boolean percentileAccountingCoherent() {\n"
    "            return observed == retained + overflow\n"
    "                    && fallbackAccountingCoherent()\n")
replace_once(
    telemetry,
    "    private int fallbackReasonMask;\n"
    "    private long gcStartCount;\n",
    "    private int fallbackReasonMask;\n"
    "    private long fallbackGlobalLifecycle;\n"
    "    private long fallbackProvenance;\n"
    "    private long fallbackMultiSection;\n"
    "    private long fallbackHaloOrBoundary;\n"
    "    private long fallbackAllSlices;\n"
    "    private long fallbackPendingEpisode;\n"
    "    private long fallbackNotLive;\n"
    "    private long firstFailureEpisodeId;\n"
    "    private int firstFailureSectionX;\n"
    "    private int firstFailureSectionY;\n"
    "    private int firstFailureSectionZ;\n"
    "    private int firstFailureSliceMask;\n"
    "    private int firstFailureEditCount;\n"
    "    private int firstFailureCode;\n"
    "    private int firstFailureIndex;\n"
    "    private boolean firstFailureDeterministic = true;\n"
    "    private long gcStartCount;\n")
replace_once(
    telemetry,
    "        fallbackReasonMask = 0;\n"
    "        long[] gc = gcTotals();\n",
    "        fallbackReasonMask = 0;\n"
    "        fallbackGlobalLifecycle = fallbackProvenance = fallbackMultiSection = 0L;\n"
    "        fallbackHaloOrBoundary = fallbackAllSlices = fallbackPendingEpisode = fallbackNotLive = 0L;\n"
    "        firstFailureEpisodeId = 0L;\n"
    "        firstFailureSectionX = firstFailureSectionY = firstFailureSectionZ = 0;\n"
    "        firstFailureSliceMask = firstFailureEditCount = firstFailureCode = 0;\n"
    "        firstFailureIndex = -1;\n"
    "        firstFailureDeterministic = true;\n"
    "        long[] gc = gcTotals();\n")
replace_once(
    telemetry,
    "    public void recordFallback(int reasonMask) {\n"
    "        if (!armed) return;\n"
    "        fallbacks++;\n"
    "        fallbackReasonMask |= reasonMask;\n"
    "    }\n",
    "    public void recordFallback(int reasonMask) {\n"
    "        if (!armed) return;\n"
    "        if (Integer.bitCount(reasonMask) != 1 || (reasonMask & ~ALL_FALLBACK_REASONS) != 0) {\n"
    "            throw new IllegalArgumentException(\"dev17 fallback reason must be exactly one known bit: \" + reasonMask);\n"
    "        }\n"
    "        fallbacks++;\n"
    "        fallbackReasonMask |= reasonMask;\n"
    "        switch (reasonMask) {\n"
    "            case FALLBACK_GLOBAL_LIFECYCLE -> fallbackGlobalLifecycle++;\n"
    "            case FALLBACK_PROVENANCE -> fallbackProvenance++;\n"
    "            case FALLBACK_MULTI_SECTION -> fallbackMultiSection++;\n"
    "            case FALLBACK_HALO_OR_BOUNDARY -> fallbackHaloOrBoundary++;\n"
    "            case FALLBACK_ALL_SLICES -> fallbackAllSlices++;\n"
    "            case FALLBACK_PENDING_EPISODE -> fallbackPendingEpisode++;\n"
    "            case FALLBACK_NOT_LIVE -> fallbackNotLive++;\n"
    "            default -> throw new IllegalStateException(\"unreachable fallback reason\");\n"
    "        }\n"
    "    }\n")
replace_once(
    telemetry,
    "    public void recordCompleted(\n"
    "            PartialRemeshShadowRequest request,\n",
    "    public void recordCompleted(\n"
    "            int sectionX, int sectionY, int sectionZ,\n"
    "            PartialRemeshShadowRequest request,\n")
replace_once(
    telemetry,
    "        if (result.exact() && deterministic) exact++; else correctnessFailures++;\n"
    "        if (!result.unselectedStable()) unselectedFailures++;\n",
    "        boolean completedExact = result.exact() && deterministic;\n"
    "        if (completedExact) {\n"
    "            exact++;\n"
    "        } else {\n"
    "            correctnessFailures++;\n"
    "            if (shouldRetainFirstFailure(firstFailureEpisodeId, completedExact, deterministic)) {\n"
    "                firstFailureEpisodeId = request.episodeId();\n"
    "                firstFailureSectionX = sectionX;\n"
    "                firstFailureSectionY = sectionY;\n"
    "                firstFailureSectionZ = sectionZ;\n"
    "                firstFailureSliceMask = request.sliceMask();\n"
    "                firstFailureEditCount = request.editCount();\n"
    "                firstFailureCode = result.failureCode() == PartialRemeshShadowResult.FAILURE_NONE\n"
    "                        ? PartialRemeshShadowResult.FAILURE_ACCOUNTING : result.failureCode();\n"
    "                firstFailureIndex = result.failureCode() == PartialRemeshShadowResult.FAILURE_NONE\n"
    "                        ? -2 : result.failureIndex();\n"
    "                firstFailureDeterministic = deterministic;\n"
    "            }\n"
    "        }\n"
    "        if (!result.unselectedStable()) unselectedFailures++;\n")
replace_once(
    telemetry,
    "                PartialRemeshSliceTruth.METADATA_BYTES_PER_SECTION, PartialRemeshDirtyProvenance.SLICE_COUNT,\n"
    "                fallbackReasonMask, Math.max(0L, gc[0] - gcStartCount), Math.max(0L, gc[1] - gcStartTimeMs),\n",
    "                PartialRemeshSliceTruth.METADATA_BYTES_PER_SECTION, PartialRemeshDirtyProvenance.SLICE_COUNT,\n"
    "                fallbackReasonMask,\n"
    "                fallbackGlobalLifecycle, fallbackProvenance, fallbackMultiSection,\n"
    "                fallbackHaloOrBoundary, fallbackAllSlices, fallbackPendingEpisode, fallbackNotLive,\n"
    "                firstFailureEpisodeId, firstFailureSectionX, firstFailureSectionY, firstFailureSectionZ,\n"
    "                firstFailureSliceMask, firstFailureEditCount, firstFailureCode, firstFailureIndex,\n"
    "                firstFailureDeterministic,\n"
    "                Math.max(0L, gc[0] - gcStartCount), Math.max(0L, gc[1] - gcStartTimeMs),\n")
replace_once(
    telemetry,
    "public final class PartialRemeshExperimentTelemetry {\n    public static final int CAPACITY = 512;\n",
    "public final class PartialRemeshExperimentTelemetry {\n    public static final int CAPACITY = 512;\n")
replace_once(
    telemetry,
    "    public static final int FALLBACK_NOT_LIVE = 1 << 6;\n\n",
    "    public static final int FALLBACK_NOT_LIVE = 1 << 6;\n"
    "    private static final int ALL_FALLBACK_REASONS = FALLBACK_GLOBAL_LIFECYCLE | FALLBACK_PROVENANCE\n"
    "            | FALLBACK_MULTI_SECTION | FALLBACK_HALO_OR_BOUNDARY | FALLBACK_ALL_SLICES\n"
    "            | FALLBACK_PENDING_EPISODE | FALLBACK_NOT_LIVE;\n\n")
replace_once(
    telemetry,
    "    private static boolean selfTest() {\n"
    "        long[] fixture = { 5, 1, 4, 2, 3 };\n"
    "        Distribution d = distribution(fixture, fixture.length);\n"
    "        return d.p50 == 3L && d.p95 == 5L && d.p99 == 5L && d.max == 5L\n"
    "                && PartialRemeshSliceTruth.METADATA_BYTES_PER_SECTION <= 1024;\n"
    "    }\n",
    "    private static boolean shouldRetainFirstFailure(long currentEpisodeId, boolean completedExact, boolean deterministic) {\n"
    "        return currentEpisodeId == 0L && (!completedExact || !deterministic);\n"
    "    }\n\n"
    "    private static boolean selfTest() {\n"
    "        long[] fixture = { 5, 1, 4, 2, 3 };\n"
    "        Distribution d = distribution(fixture, fixture.length);\n"
    "        int allReasons = FALLBACK_GLOBAL_LIFECYCLE | FALLBACK_PROVENANCE | FALLBACK_MULTI_SECTION\n"
    "                | FALLBACK_HALO_OR_BOUNDARY | FALLBACK_ALL_SLICES | FALLBACK_PENDING_EPISODE | FALLBACK_NOT_LIVE;\n"
    "        return d.p50 == 3L && d.p95 == 5L && d.p99 == 5L && d.max == 5L\n"
    "                && allReasons == ALL_FALLBACK_REASONS && Integer.bitCount(allReasons) == 7\n"
    "                && shouldRetainFirstFailure(0L, false, true)\n"
    "                && shouldRetainFirstFailure(0L, true, false)\n"
    "                && !shouldRetainFirstFailure(1L, false, true)\n"
    "                && !shouldRetainFirstFailure(0L, true, true)\n"
    "                && PartialRemeshSliceTruth.METADATA_BYTES_PER_SECTION <= 1024;\n"
    "    }\n")

# Pass section identity into the bounded first-failure fixture. Do not change admission policy.
scene = "src/client/java/dev/obsidian/render/terrain/AsyncMultiSectionSceneProbe.java"
replace_once(
    scene,
    "            partialRemeshTelemetry.recordCompleted(pending.request, result, probe.partialRemeshControlExecutionNs(),\n"
    "                    probe.partialRemeshControlUploadBytes(), probe.partialRemeshShadowDeterministic());\n",
    "            partialRemeshTelemetry.recordCompleted(pending.sectionX, pending.sectionY, pending.sectionZ,\n"
    "                    pending.request, result, probe.partialRemeshControlExecutionNs(),\n"
    "                    probe.partialRemeshControlUploadBytes(), probe.partialRemeshShadowDeterministic());\n")

# Dev17 runtime labels and final diagnostic closure.
frame = "src/client/java/dev/obsidian/render/frame/FrameCoordinator.java"
replace_all(frame, "dev16", "dev17")
replace_once(
    frame,
    "                            + \"; all proven P3.2-P3.7 correctness and dev11 repeat-aware greedy GPU emission remain armed unchanged. P3.8 dev15 adds only bounded production-worker benchmark telemetry: fixed primitive samples, workload identity, GC deltas and stage timing composition. Dev15 changes no geometry, shader, pipeline, vertex/index format, atlas/lightmap semantics, scheduling policy, rebuild granularity or native graphics behavior.\");\n",
    "                            + \"; all proven P3.2-P3.8 production/correctness paths remain armed unchanged. P3.9 dev17 is shadow-only: it aligns selected-slice reference auditing to the permanent P3.7 oracle and adds bounded per-fallback plus first-failure diagnostics. Production geometry, shader, pipeline, atlas/lightmap semantics, scheduling, rebuild granularity and native graphics behavior remain unchanged.\");\n")
replace_once(
    frame,
    "                    \"Phase 3 dev17 P3.9 shadow partial-remesh window armed. Production rendering remains full-section and unchanged. For localized evidence, make edits away from section X/Z edges and wait for READY between episodes: local Y rows 1/5/9/13 exercise one slice; rows 3/4, 7/8, or 11/12 exercise two-slice boundary expansion. Accumulate at least 32 localized episodes including 16 one-slice, 8 two-slice and one coalesced multi-edit burst. Also perform F3+T and one real scene recenter as explicit full-fallback episodes.\");\n",
    "                    \"Phase 3 dev17 P3.9 diagnostic/corrected shadow partial-remesh window armed. Production rendering remains full-section and unchanged. The A-0159 thresholds and four-slice admission policy are unchanged; dev17 only restores permanent P3.7 reference semantics and records exact fallback/failure diagnostics. Exercise the same localized one-slice, two-slice, coalesced, F3+T and scene-recenter workload.\");\n")
replace_once(frame, "        StringBuilder out = new StringBuilder(28672);\n", "        StringBuilder out = new StringBuilder(32768);\n")
replace_once(
    frame,
    "                .append(\", partialRemeshFallbackEpisodes=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackEpisodes())\n"
    "                .append(\", partialRemeshOneSliceEpisodes=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.oneSliceEpisodes())\n",
    "                .append(\", partialRemeshFallbackEpisodes=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackEpisodes())\n"
    "                .append(\", partialRemeshFallbackReasonMask=\").append(partialRemeshSnapshot == null ? 0 : partialRemeshSnapshot.fallbackReasonMask())\n"
    "                .append(\", partialRemeshFallbackGlobalLifecycle=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackGlobalLifecycle())\n"
    "                .append(\", partialRemeshFallbackProvenance=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackProvenance())\n"
    "                .append(\", partialRemeshFallbackMultiSection=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackMultiSection())\n"
    "                .append(\", partialRemeshFallbackHaloOrBoundary=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackHaloOrBoundary())\n"
    "                .append(\", partialRemeshFallbackAllSlices=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackAllSlices())\n"
    "                .append(\", partialRemeshFallbackPendingEpisode=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackPendingEpisode())\n"
    "                .append(\", partialRemeshFallbackNotLive=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.fallbackNotLive())\n"
    "                .append(\", partialRemeshFallbackAccountingCoherent=\").append(partialRemeshSnapshot != null && partialRemeshSnapshot.fallbackAccountingCoherent())\n"
    "                .append(\", partialRemeshOneSliceEpisodes=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.oneSliceEpisodes())\n")
replace_once(
    frame,
    "                .append(\", partialRemeshDeterminismFailures=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.determinismFailures())\n"
    "                .append(\", partialRemeshSelectedCellsP50Permille=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.selectedCellPermille().p50())\n",
    "                .append(\", partialRemeshDeterminismFailures=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.determinismFailures())\n"
    "                .append(\", partialRemeshFirstFailureEpisodeId=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.firstFailureEpisodeId())\n"
    "                .append(\", partialRemeshFirstFailureSectionX=\").append(partialRemeshSnapshot == null ? 0 : partialRemeshSnapshot.firstFailureSectionX())\n"
    "                .append(\", partialRemeshFirstFailureSectionY=\").append(partialRemeshSnapshot == null ? 0 : partialRemeshSnapshot.firstFailureSectionY())\n"
    "                .append(\", partialRemeshFirstFailureSectionZ=\").append(partialRemeshSnapshot == null ? 0 : partialRemeshSnapshot.firstFailureSectionZ())\n"
    "                .append(\", partialRemeshFirstFailureSliceMask=\").append(partialRemeshSnapshot == null ? 0 : partialRemeshSnapshot.firstFailureSliceMask())\n"
    "                .append(\", partialRemeshFirstFailureEditCount=\").append(partialRemeshSnapshot == null ? 0 : partialRemeshSnapshot.firstFailureEditCount())\n"
    "                .append(\", partialRemeshFirstFailureCode=\").append(partialRemeshSnapshot == null ? 0 : partialRemeshSnapshot.firstFailureCode())\n"
    "                .append(\", partialRemeshFirstFailureName=\").append(partialRemeshSnapshot == null ? \"none\" : partialRemeshSnapshot.firstFailureName())\n"
    "                .append(\", partialRemeshFirstFailureIndex=\").append(partialRemeshSnapshot == null ? -1 : partialRemeshSnapshot.firstFailureIndex())\n"
    "                .append(\", partialRemeshFirstFailureDeterministic=\").append(partialRemeshSnapshot == null || partialRemeshSnapshot.firstFailureDeterministic())\n"
    "                .append(\", partialRemeshSelectedCellsP50Permille=\").append(partialRemeshSnapshot == null ? 0L : partialRemeshSnapshot.selectedCellPermille().p50())\n")

# Bootstrap description reflects the actual active diagnostic slice.
bootstrap = "src/client/java/dev/obsidian/bootstrap/ObsidianBootstrap.java"
replace_once(
    bootstrap,
    "                \"Obsidian Phase 3 dev15 P3.8 meshing benchmark instrumentation armed. The fully validated P3.7 differential oracle, P3.6 T-junction policy, P3.5 border/halo path and dev11 repeat-aware greedy GPU emission remain unchanged. Dev15 adds only a fixed-capacity primitive production-worker benchmark window over the existing full-section path, with coherent queue/execution percentiles, workload/output identity, scratch high-water evidence, worker pressure and JVM GC deltas. It does not change greedy eligibility, source suppression, vertex positions, shaders, pipelines, atlas/lightmap semantics, queue policy, worker count, rebuild granularity or native graphics scope; meshingBenchmarkInstrumentation=true, benchmarkCollectorBounded=true, benchmarkGeometryChanged=false, benchmarkShaderChanged=false, benchmarkPipelineChanged=false, workerWorldReadsAfterCapture=0.\");\n",
    "                \"Obsidian Phase 3 dev17 P3.9 diagnostic/correction instrumentation armed. The validated production renderer and permanent P3.7 differential oracle remain unchanged. Dev17 corrects only the shadow selected-slice reference audit to the permanent P3.7 semantics and retains bounded per-fallback plus first-failure diagnostics; production full-section capture/mesh/upload/install/draw remains authoritative and partial GPU patching is still disabled. Frozen A-0159 thresholds, four fixed slices, provenance surface, admission policy, greedy eligibility, shaders, pipelines, atlas/lightmap semantics, worker count/backpressure and resource lifetime remain unchanged; partialRemeshGpuInstallChanged=false, partialRemeshRenderedGeometryChanged=false, workerWorldReadsAfterCapture=0.\");\n")

print("dev17 patch applied")
