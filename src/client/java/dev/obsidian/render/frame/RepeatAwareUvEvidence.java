package dev.obsidian.render.frame;

import dev.obsidian.render.mesh.SectionMeshWorkerPool;
import dev.obsidian.render.terrain.BinarySectionVisibility;
import dev.obsidian.render.terrain.RepeatAwareUvDescriptors;

/** Pure aggregation/gate logic for the P3.4 dev9 repeat-aware UV descriptor sidecar. */
final class RepeatAwareUvEvidence {
    private RepeatAwareUvEvidence() { }

    static Snapshot capture(
            SectionMeshWorkerPool workers,
            boolean priorGateReady,
            long workerCompletedJobs,
            long mergeCandidateMultiFace,
            long renderKeyEligibleFaces,
            boolean workersClean,
            boolean stagingClean,
            boolean arenaClean,
            boolean resourcesClean) {
        if (workers == null) return Snapshot.empty();

        long builds = workers.repeatAwareUvBuilds();
        long multiFace = workers.totalRepeatAwareUvMultiFace();
        long representable = workers.totalRepeatAwareUvRepresentable();
        long unrepresentable = workers.totalRepeatAwareUvUnrepresentable();
        long safe = workers.totalRepeatAwareUvFourVertexSafe();
        long unsafe = workers.totalRepeatAwareUvFourVertexUnsafe();
        long safeCoveredFaces = workers.totalRepeatAwareUvSafeCoveredFaces();
        long safeFacesSaved = workers.totalRepeatAwareUvSafeFacesSaved();
        long retainedBytes = workers.totalRepeatAwareUvRetainedBytes();
        long scratchUses = workers.repeatAwareUvScratchBuildUses();
        long classificationAudits = workers.repeatAwareUvClassificationAudits();
        long classificationMatches = workers.repeatAwareUvClassificationAuditMatches();
        long determinismAudits = workers.repeatAwareUvDeterminismAudits();
        long determinismMatches = workers.repeatAwareUvDeterminismAuditMatches();

        long representableDirectionCount = 0L;
        long safeDirectionCount = 0L;
        long safeDirectionFaces = 0L;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            representableDirectionCount += workers.repeatAwareUvRepresentable(direction);
            safeDirectionCount += workers.repeatAwareUvSafe(direction);
            safeDirectionFaces += workers.repeatAwareUvSafeCoveredFaces(direction);
        }

        long reductionPermille = renderKeyEligibleFaces == 0L ? 0L
                : safeFacesSaved * 1000L / renderKeyEligibleFaces;

        long emissionBuilds = workers.emissionSafetyBuilds();
        long emissionMultiFace = workers.totalEmissionSafetyMultiFace();
        long cancelledPrefixBuilds = emissionBuilds - builds;
        long cancelledPrefixMultiFace = emissionMultiFace - multiFace;
        boolean residualRequiresCancellation = cancelledPrefixBuilds > 0L
                || cancelledPrefixMultiFace == 0L;
        boolean cancellationAccountingExact = cancelledPrefixBuilds >= 0L
                && cancelledPrefixBuilds <= workers.cancelledJobs()
                && cancelledPrefixMultiFace >= 0L
                && residualRequiresCancellation;

        boolean ready = priorGateReady
                && builds > 0L
                && builds >= workerCompletedJobs
                && cancellationAccountingExact
                && representable + unrepresentable == multiFace
                && safe + unsafe == multiFace
                && safe <= representable
                && representableDirectionCount == representable
                && safeDirectionCount == safe
                && safeDirectionFaces == safeCoveredFaces
                && safeCoveredFaces >= safe
                && safeFacesSaved == safeCoveredFaces - safe
                && retainedBytes == representable * RepeatAwareUvDescriptors.BYTES_PER_DESCRIPTOR
                && scratchUses >= builds
                && classificationAudits == builds
                && classificationMatches == classificationAudits
                && determinismAudits > 0L
                && determinismMatches == determinismAudits
                && workersClean && stagingClean && arenaClean && resourcesClean;

        return new Snapshot(
                ready, builds, multiFace, representable, unrepresentable,
                safe, unsafe, safeCoveredFaces, safeFacesSaved, reductionPermille,
                retainedBytes, scratchUses, classificationAudits, classificationMatches,
                determinismAudits, determinismMatches,
                representableDirectionCount, safeDirectionCount, safeDirectionFaces,
                workers.totalRepeatAwareUvBuildNs(), workers.maxRepeatAwareUvBuildNs(),
                workers.maxRepeatAwareUvDescriptors(), workers.maxRepeatAwareUvScratchDescriptors(),
                workers.repeatAwareUvRepresentable(BinarySectionVisibility.WEST),
                workers.repeatAwareUvRepresentable(BinarySectionVisibility.EAST),
                workers.repeatAwareUvRepresentable(BinarySectionVisibility.DOWN),
                workers.repeatAwareUvRepresentable(BinarySectionVisibility.UP),
                workers.repeatAwareUvRepresentable(BinarySectionVisibility.NORTH),
                workers.repeatAwareUvRepresentable(BinarySectionVisibility.SOUTH),
                workers.repeatAwareUvSafe(BinarySectionVisibility.WEST),
                workers.repeatAwareUvSafe(BinarySectionVisibility.EAST),
                workers.repeatAwareUvSafe(BinarySectionVisibility.DOWN),
                workers.repeatAwareUvSafe(BinarySectionVisibility.UP),
                workers.repeatAwareUvSafe(BinarySectionVisibility.NORTH),
                workers.repeatAwareUvSafe(BinarySectionVisibility.SOUTH),
                workers.repeatAwareUvSafeCoveredFaces(BinarySectionVisibility.WEST),
                workers.repeatAwareUvSafeCoveredFaces(BinarySectionVisibility.EAST),
                workers.repeatAwareUvSafeCoveredFaces(BinarySectionVisibility.DOWN),
                workers.repeatAwareUvSafeCoveredFaces(BinarySectionVisibility.UP),
                workers.repeatAwareUvSafeCoveredFaces(BinarySectionVisibility.NORTH),
                workers.repeatAwareUvSafeCoveredFaces(BinarySectionVisibility.SOUTH));
    }

    record Snapshot(
            boolean ready,
            long builds,
            long multiFace,
            long representable,
            long unrepresentable,
            long fourVertexSafe,
            long fourVertexUnsafe,
            long safeCoveredFaces,
            long safeFacesSaved,
            long safeReductionPermille,
            long retainedBytes,
            long scratchUses,
            long classificationAudits,
            long classificationMatches,
            long determinismAudits,
            long determinismMatches,
            long representableDirectionCount,
            long safeDirectionCount,
            long safeDirectionFaces,
            long totalBuildNs,
            long maxBuildNs,
            long maxDescriptors,
            long maxScratchDescriptors,
            long representableWest,
            long representableEast,
            long representableDown,
            long representableUp,
            long representableNorth,
            long representableSouth,
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
                    0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0);
        }

        void appendTo(StringBuilder out) {
            out.append(", repeatAwareUvBuilds=").append(builds)
                    .append(", repeatAwareUvMultiFace=").append(multiFace)
                    .append(", repeatAwareUvRepresentable=").append(representable)
                    .append(", repeatAwareUvUnrepresentable=").append(unrepresentable)
                    .append(", repeatAwareUvFourVertexSafe=").append(fourVertexSafe)
                    .append(", repeatAwareUvFourVertexUnsafe=").append(fourVertexUnsafe)
                    .append(", repeatAwareUvSafeCoveredFaces=").append(safeCoveredFaces)
                    .append(", repeatAwareUvSafeFacesSaved=").append(safeFacesSaved)
                    .append(", repeatAwareUvSafeReductionPermille=").append(safeReductionPermille)
                    .append(", repeatAwareUvRepresentableWest=").append(representableWest)
                    .append(", repeatAwareUvRepresentableEast=").append(representableEast)
                    .append(", repeatAwareUvRepresentableDown=").append(representableDown)
                    .append(", repeatAwareUvRepresentableUp=").append(representableUp)
                    .append(", repeatAwareUvRepresentableNorth=").append(representableNorth)
                    .append(", repeatAwareUvRepresentableSouth=").append(representableSouth)
                    .append(", repeatAwareUvSafeWest=").append(safeWest)
                    .append(", repeatAwareUvSafeEast=").append(safeEast)
                    .append(", repeatAwareUvSafeDown=").append(safeDown)
                    .append(", repeatAwareUvSafeUp=").append(safeUp)
                    .append(", repeatAwareUvSafeNorth=").append(safeNorth)
                    .append(", repeatAwareUvSafeSouth=").append(safeSouth)
                    .append(", repeatAwareUvSafeWestFaces=").append(safeWestFaces)
                    .append(", repeatAwareUvSafeEastFaces=").append(safeEastFaces)
                    .append(", repeatAwareUvSafeDownFaces=").append(safeDownFaces)
                    .append(", repeatAwareUvSafeUpFaces=").append(safeUpFaces)
                    .append(", repeatAwareUvSafeNorthFaces=").append(safeNorthFaces)
                    .append(", repeatAwareUvSafeSouthFaces=").append(safeSouthFaces)
                    .append(", repeatAwareUvRetainedBytes=").append(retainedBytes)
                    .append(", repeatAwareUvBytesPerDescriptor=").append(RepeatAwareUvDescriptors.BYTES_PER_DESCRIPTOR)
                    .append(", repeatAwareUvTotalBuildNs=").append(totalBuildNs)
                    .append(", repeatAwareUvMaxBuildNs=").append(maxBuildNs)
                    .append(", repeatAwareUvMaxDescriptors=").append(maxDescriptors)
                    .append(", repeatAwareUvScratchBuildUses=").append(scratchUses)
                    .append(", repeatAwareUvMaxScratchDescriptors=").append(maxScratchDescriptors)
                    .append(", repeatAwareUvClassificationAudits=").append(classificationAudits)
                    .append(", repeatAwareUvClassificationAuditMatches=").append(classificationMatches)
                    .append(", repeatAwareUvDeterminismAudits=").append(determinismAudits)
                    .append(", repeatAwareUvDeterminismAuditMatches=").append(determinismMatches);
        }
    }
}
