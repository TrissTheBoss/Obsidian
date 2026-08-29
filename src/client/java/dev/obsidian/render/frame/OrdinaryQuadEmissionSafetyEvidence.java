package dev.obsidian.render.frame;

import dev.obsidian.render.mesh.SectionMeshWorkerPool;
import dev.obsidian.render.terrain.BinarySectionVisibility;
import dev.obsidian.render.terrain.OrdinaryQuadEmissionSafety;

/** Pure aggregation/gate logic for the P3.4 dev8 ordinary-quad safety sidecar. */
final class OrdinaryQuadEmissionSafetyEvidence {
    private OrdinaryQuadEmissionSafetyEvidence() { }

    static Snapshot capture(
            SectionMeshWorkerPool workers,
            boolean priorGateReady,
            long workerCompletedJobs,
            long mergeCandidateCount,
            long mergeCandidateSingletons,
            long mergeCandidateMultiFace,
            long renderKeyEligibleFaces,
            boolean workersClean,
            boolean stagingClean,
            boolean arenaClean,
            boolean resourcesClean) {
        if (workers == null) return Snapshot.empty();

        long builds = workers.emissionSafetyBuilds();
        long candidates = workers.totalEmissionSafetyCandidates();
        long singletons = workers.totalEmissionSafetySingletons();
        long multiFace = workers.totalEmissionSafetyMultiFace();
        long colorSafe = workers.totalEmissionSafetyColorSafe();
        long colorUnsafe = workers.totalEmissionSafetyColorUnsafe();
        long lightSafe = workers.totalEmissionSafetyLightSafe();
        long lightUnsafe = workers.totalEmissionSafetyLightUnsafe();
        long uvSafe = workers.totalEmissionSafetyUvSafe();
        long uvUnsafe = workers.totalEmissionSafetyUvUnsafe();
        long ordinarySafe = workers.totalEmissionSafetyOrdinarySafe();
        long ordinaryUnsafe = workers.totalEmissionSafetyOrdinaryUnsafe();
        long safeCoveredFaces = workers.totalEmissionSafetyOrdinarySafeCoveredFaces();
        long safeFacesSaved = workers.totalEmissionSafetyOrdinarySafeFacesSaved();
        long retainedBytes = workers.totalEmissionSafetyRetainedBytes();
        long scratchUses = workers.emissionSafetyScratchBuildUses();
        long classificationAudits = workers.emissionSafetyClassificationAudits();
        long classificationMatches = workers.emissionSafetyClassificationAuditMatches();
        long determinismAudits = workers.emissionSafetyDeterminismAudits();
        long determinismMatches = workers.emissionSafetyDeterminismAuditMatches();

        long safeDirectionCount = 0L;
        long safeDirectionFaces = 0L;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            safeDirectionCount += workers.emissionSafetyOrdinarySafe(direction);
            safeDirectionFaces += workers.emissionSafetyOrdinarySafeCoveredFaces(direction);
        }

        long reductionPermille = renderKeyEligibleFaces == 0L ? 0L
                : safeFacesSaved * 1000L / renderKeyEligibleFaces;

        /*
         * Cancellation is checked between pure pipeline stages. A cancelled
         * ticket may have published merge-candidate telemetry without reaching
         * emission-safety. Prove the aggregate delta is exactly such a cancelled
         * prefix; when no build was skipped, every cross-stage residual must be 0.
         */
        long mergeBuilds = workers.mergeCandidateBuilds();
        long cancelledPrefixBuilds = mergeBuilds - builds;
        long cancelledPrefixCandidates = mergeCandidateCount - candidates;
        long cancelledPrefixSingletons = mergeCandidateSingletons - singletons;
        long cancelledPrefixMultiFace = mergeCandidateMultiFace - multiFace;
        boolean residualRequiresCancellation = cancelledPrefixBuilds > 0L
                || (cancelledPrefixCandidates == 0L
                    && cancelledPrefixSingletons == 0L
                    && cancelledPrefixMultiFace == 0L);
        boolean cancellationAccountingExact = cancelledPrefixBuilds >= 0L
                && cancelledPrefixBuilds <= workers.cancelledJobs()
                && cancelledPrefixCandidates >= 0L
                && cancelledPrefixSingletons >= 0L
                && cancelledPrefixMultiFace >= 0L
                && cancelledPrefixCandidates == cancelledPrefixSingletons + cancelledPrefixMultiFace
                && residualRequiresCancellation;

        boolean ready = priorGateReady
                && builds > 0L
                && builds >= workerCompletedJobs
                && cancellationAccountingExact
                && singletons + multiFace == candidates
                && colorSafe + colorUnsafe == multiFace
                && lightSafe + lightUnsafe == multiFace
                && uvSafe + uvUnsafe == multiFace
                && ordinarySafe + ordinaryUnsafe == multiFace
                && safeDirectionCount == ordinarySafe
                && safeDirectionFaces == safeCoveredFaces
                && safeFacesSaved >= 0L
                && safeCoveredFaces >= ordinarySafe
                && retainedBytes == candidates * OrdinaryQuadEmissionSafety.BYTES_PER_CANDIDATE
                && scratchUses >= builds
                && classificationAudits == builds
                && classificationMatches == classificationAudits
                && determinismAudits > 0L
                && determinismMatches == determinismAudits
                && workersClean && stagingClean && arenaClean && resourcesClean;

        return new Snapshot(
                ready, builds, candidates, singletons, multiFace,
                colorSafe, colorUnsafe, lightSafe, lightUnsafe, uvSafe, uvUnsafe,
                ordinarySafe, ordinaryUnsafe, safeCoveredFaces, safeFacesSaved,
                reductionPermille, retainedBytes, scratchUses,
                classificationAudits, classificationMatches,
                determinismAudits, determinismMatches,
                safeDirectionCount, safeDirectionFaces,
                workers.totalEmissionSafetyBuildNs(), workers.maxEmissionSafetyBuildNs(),
                workers.maxEmissionSafetyCandidates(), workers.maxEmissionSafetyScratchCandidates(),
                workers.emissionSafetyOrdinarySafe(BinarySectionVisibility.WEST),
                workers.emissionSafetyOrdinarySafe(BinarySectionVisibility.EAST),
                workers.emissionSafetyOrdinarySafe(BinarySectionVisibility.DOWN),
                workers.emissionSafetyOrdinarySafe(BinarySectionVisibility.UP),
                workers.emissionSafetyOrdinarySafe(BinarySectionVisibility.NORTH),
                workers.emissionSafetyOrdinarySafe(BinarySectionVisibility.SOUTH),
                workers.emissionSafetyOrdinarySafeCoveredFaces(BinarySectionVisibility.WEST),
                workers.emissionSafetyOrdinarySafeCoveredFaces(BinarySectionVisibility.EAST),
                workers.emissionSafetyOrdinarySafeCoveredFaces(BinarySectionVisibility.DOWN),
                workers.emissionSafetyOrdinarySafeCoveredFaces(BinarySectionVisibility.UP),
                workers.emissionSafetyOrdinarySafeCoveredFaces(BinarySectionVisibility.NORTH),
                workers.emissionSafetyOrdinarySafeCoveredFaces(BinarySectionVisibility.SOUTH));
    }

    record Snapshot(
            boolean ready,
            long builds,
            long candidates,
            long singletons,
            long multiFace,
            long colorSafe,
            long colorUnsafe,
            long lightSafe,
            long lightUnsafe,
            long uvSafe,
            long uvUnsafe,
            long ordinarySafe,
            long ordinaryUnsafe,
            long ordinarySafeCoveredFaces,
            long ordinarySafeFacesSaved,
            long ordinarySafeReductionPermille,
            long retainedBytes,
            long scratchUses,
            long classificationAudits,
            long classificationMatches,
            long determinismAudits,
            long determinismMatches,
            long ordinarySafeDirectionCount,
            long ordinarySafeDirectionFaces,
            long totalBuildNs,
            long maxBuildNs,
            long maxCandidates,
            long maxScratchCandidates,
            long safeWest,
            long safeEast,
            long safeDown,
            long safeUp,
            long safeNorth,
            long safeSouth,
            long safeWestFaces,
            long safeEastFaces,
            long safeDownFaces,
            long safeUpFaces,
            long safeNorthFaces,
            long safeSouthFaces) {

        static Snapshot empty() {
            return new Snapshot(false,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0);
        }

        void appendTo(StringBuilder out) {
            out.append(", emissionSafetyBuilds=").append(builds)
                    .append(", emissionSafetyCandidates=").append(candidates)
                    .append(", emissionSafetySingletons=").append(singletons)
                    .append(", emissionSafetyMultiFace=").append(multiFace)
                    .append(", emissionSafetyColorSafe=").append(colorSafe)
                    .append(", emissionSafetyColorUnsafe=").append(colorUnsafe)
                    .append(", emissionSafetyLightSafe=").append(lightSafe)
                    .append(", emissionSafetyLightUnsafe=").append(lightUnsafe)
                    .append(", emissionSafetyUvSafe=").append(uvSafe)
                    .append(", emissionSafetyUvUnsafe=").append(uvUnsafe)
                    .append(", emissionSafetyOrdinarySafe=").append(ordinarySafe)
                    .append(", emissionSafetyOrdinaryUnsafe=").append(ordinaryUnsafe)
                    .append(", emissionSafetyRepeatAwareRequired=").append(ordinaryUnsafe)
                    .append(", emissionSafetyOrdinarySafeCoveredFaces=").append(ordinarySafeCoveredFaces)
                    .append(", emissionSafetyOrdinarySafeFacesSaved=").append(ordinarySafeFacesSaved)
                    .append(", emissionSafetyOrdinarySafeReductionPermille=").append(ordinarySafeReductionPermille)
                    .append(", emissionSafetySafeWest=").append(safeWest)
                    .append(", emissionSafetySafeEast=").append(safeEast)
                    .append(", emissionSafetySafeDown=").append(safeDown)
                    .append(", emissionSafetySafeUp=").append(safeUp)
                    .append(", emissionSafetySafeNorth=").append(safeNorth)
                    .append(", emissionSafetySafeSouth=").append(safeSouth)
                    .append(", emissionSafetySafeWestFaces=").append(safeWestFaces)
                    .append(", emissionSafetySafeEastFaces=").append(safeEastFaces)
                    .append(", emissionSafetySafeDownFaces=").append(safeDownFaces)
                    .append(", emissionSafetySafeUpFaces=").append(safeUpFaces)
                    .append(", emissionSafetySafeNorthFaces=").append(safeNorthFaces)
                    .append(", emissionSafetySafeSouthFaces=").append(safeSouthFaces)
                    .append(", emissionSafetyRetainedBytes=").append(retainedBytes)
                    .append(", emissionSafetyBytesPerCandidate=").append(OrdinaryQuadEmissionSafety.BYTES_PER_CANDIDATE)
                    .append(", emissionSafetyTotalBuildNs=").append(totalBuildNs)
                    .append(", emissionSafetyMaxBuildNs=").append(maxBuildNs)
                    .append(", emissionSafetyMaxCandidates=").append(maxCandidates)
                    .append(", emissionSafetyScratchBuildUses=").append(scratchUses)
                    .append(", emissionSafetyMaxScratchCandidates=").append(maxScratchCandidates)
                    .append(", emissionSafetyClassificationAudits=").append(classificationAudits)
                    .append(", emissionSafetyClassificationAuditMatches=").append(classificationMatches)
                    .append(", emissionSafetyDeterminismAudits=").append(determinismAudits)
                    .append(", emissionSafetyDeterminismAuditMatches=").append(determinismMatches);
        }
    }
}
