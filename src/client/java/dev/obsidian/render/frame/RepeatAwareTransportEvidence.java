package dev.obsidian.render.frame;

import dev.obsidian.render.mesh.SectionMeshWorkerPool;
import dev.obsidian.render.terrain.BinarySectionVisibility;
import dev.obsidian.render.terrain.RepeatAwareTransportProof;

/** Pure aggregation/gate logic for the P3.4 dev10 no-emission repeat transport proof. */
final class RepeatAwareTransportEvidence {
    private RepeatAwareTransportEvidence() { }

    static Snapshot capture(
            SectionMeshWorkerPool workers,
            boolean priorGateReady,
            long workerCompletedJobs,
            long repeatAwareUvMultiFace,
            long repeatAwareUvRepresentable,
            long repeatAwareUvFourVertexSafe,
            long renderKeyEligibleFaces,
            boolean workersClean,
            boolean stagingClean,
            boolean arenaClean,
            boolean resourcesClean) {
        if (workers == null) return Snapshot.empty();

        long builds = workers.repeatAwareTransportBuilds();
        long sourceMultiFace = workers.totalRepeatAwareTransportSourceMultiFace();
        long sourceRepresentable = workers.totalRepeatAwareTransportSourceRepresentable();
        long sourceFourVertexSafe = workers.totalRepeatAwareTransportSourceFourVertexSafe();
        long records = workers.totalRepeatAwareTransportRecords();
        long unsafe = workers.totalRepeatAwareTransportUnsafe();
        long coveredFaces = workers.totalRepeatAwareTransportCoveredFaces();
        long facesSaved = workers.totalRepeatAwareTransportFacesSaved();
        long explicitGradient = workers.totalRepeatAwareTransportExplicitGradient();
        long internalS = workers.totalRepeatAwareTransportInternalS();
        long internalT = workers.totalRepeatAwareTransportInternalT();
        long internalBoth = workers.totalRepeatAwareTransportInternalBoth();
        long outerEdge = workers.totalRepeatAwareTransportOuterEdge();
        long sameSampler = workers.totalRepeatAwareTransportSameSampler();
        long rasterReview = workers.totalRepeatAwareTransportRasterReview();
        long retainedBytes = workers.totalRepeatAwareTransportRetainedBytes();
        long scratchUses = workers.repeatAwareTransportScratchBuildUses();
        long proofAudits = workers.repeatAwareTransportProofAudits();
        long proofMatches = workers.repeatAwareTransportProofAuditMatches();
        long determinismAudits = workers.repeatAwareTransportDeterminismAudits();
        long determinismMatches = workers.repeatAwareTransportDeterminismAuditMatches();

        long directionRecords = 0L;
        long directionCoveredFaces = 0L;
        long directionFacesSaved = 0L;
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            directionRecords += workers.repeatAwareTransportRecords(direction);
            directionCoveredFaces += workers.repeatAwareTransportCoveredFaces(direction);
            directionFacesSaved += workers.repeatAwareTransportFacesSaved(direction);
        }

        long reductionPermille = renderKeyEligibleFaces == 0L ? 0L
                : facesSaved * 1000L / renderKeyEligibleFaces;
        long internalResetUnion = internalS + internalT - internalBoth;

        boolean ready = priorGateReady
                && builds > 0L
                && builds >= workerCompletedJobs
                && sourceMultiFace == repeatAwareUvMultiFace
                && sourceRepresentable == repeatAwareUvRepresentable
                && sourceFourVertexSafe == repeatAwareUvFourVertexSafe
                && records == sourceFourVertexSafe
                && unsafe == sourceMultiFace - records
                && explicitGradient == records
                && outerEdge == records
                && sameSampler == records
                && rasterReview == records
                && internalResetUnion == records
                && directionRecords == records
                && directionCoveredFaces == coveredFaces
                && directionFacesSaved == facesSaved
                && coveredFaces >= records
                && facesSaved == coveredFaces - records
                && retainedBytes == records * RepeatAwareTransportProof.BYTES_PER_RECORD
                && scratchUses >= builds
                && proofAudits == builds
                && proofMatches == proofAudits
                && determinismAudits > 0L
                && determinismMatches == determinismAudits
                && workersClean && stagingClean && arenaClean && resourcesClean;

        return new Snapshot(
                ready, builds, sourceMultiFace, sourceRepresentable, sourceFourVertexSafe,
                records, unsafe, coveredFaces, facesSaved, reductionPermille,
                explicitGradient, internalS, internalT, internalBoth, outerEdge, sameSampler,
                rasterReview, retainedBytes, scratchUses, proofAudits, proofMatches,
                determinismAudits, determinismMatches, internalResetUnion,
                directionRecords, directionCoveredFaces, directionFacesSaved,
                workers.totalRepeatAwareTransportBuildNs(), workers.maxRepeatAwareTransportBuildNs(),
                workers.maxRepeatAwareTransportRecords(), workers.maxRepeatAwareTransportScratchRecords(),
                workers.repeatAwareTransportRecords(BinarySectionVisibility.WEST),
                workers.repeatAwareTransportRecords(BinarySectionVisibility.EAST),
                workers.repeatAwareTransportRecords(BinarySectionVisibility.DOWN),
                workers.repeatAwareTransportRecords(BinarySectionVisibility.UP),
                workers.repeatAwareTransportRecords(BinarySectionVisibility.NORTH),
                workers.repeatAwareTransportRecords(BinarySectionVisibility.SOUTH),
                workers.repeatAwareTransportCoveredFaces(BinarySectionVisibility.WEST),
                workers.repeatAwareTransportCoveredFaces(BinarySectionVisibility.EAST),
                workers.repeatAwareTransportCoveredFaces(BinarySectionVisibility.DOWN),
                workers.repeatAwareTransportCoveredFaces(BinarySectionVisibility.UP),
                workers.repeatAwareTransportCoveredFaces(BinarySectionVisibility.NORTH),
                workers.repeatAwareTransportCoveredFaces(BinarySectionVisibility.SOUTH),
                workers.repeatAwareTransportFacesSaved(BinarySectionVisibility.WEST),
                workers.repeatAwareTransportFacesSaved(BinarySectionVisibility.EAST),
                workers.repeatAwareTransportFacesSaved(BinarySectionVisibility.DOWN),
                workers.repeatAwareTransportFacesSaved(BinarySectionVisibility.UP),
                workers.repeatAwareTransportFacesSaved(BinarySectionVisibility.NORTH),
                workers.repeatAwareTransportFacesSaved(BinarySectionVisibility.SOUTH));
    }

    record Snapshot(
            boolean ready,
            long builds,
            long sourceMultiFace,
            long sourceRepresentable,
            long sourceFourVertexSafe,
            long records,
            long unsafe,
            long coveredFaces,
            long facesSaved,
            long reductionPermille,
            long explicitGradientRequired,
            long internalSReset,
            long internalTReset,
            long internalBothReset,
            long outerEdgePolicyRequired,
            long sameAtlasSamplerRequired,
            long rasterBoundaryReviewRequired,
            long retainedBytes,
            long scratchUses,
            long proofAudits,
            long proofMatches,
            long determinismAudits,
            long determinismMatches,
            long internalResetUnion,
            long directionRecords,
            long directionCoveredFaces,
            long directionFacesSaved,
            long totalBuildNs,
            long maxBuildNs,
            long maxRecords,
            long maxScratchRecords,
            long west,
            long east,
            long down,
            long up,
            long north,
            long south,
            long westFaces,
            long eastFaces,
            long downFaces,
            long upFaces,
            long northFaces,
            long southFaces,
            long westSaved,
            long eastSaved,
            long downSaved,
            long upSaved,
            long northSaved,
            long southSaved) {

        static Snapshot empty() {
            return new Snapshot(false,
                    0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0);
        }

        void appendTo(StringBuilder out) {
            out.append(", repeatAwareTransportBuilds=").append(builds)
                    .append(", repeatAwareTransportSourceMultiFace=").append(sourceMultiFace)
                    .append(", repeatAwareTransportSourceRepresentable=").append(sourceRepresentable)
                    .append(", repeatAwareTransportSourceFourVertexSafe=").append(sourceFourVertexSafe)
                    .append(", repeatAwareTransportRecords=").append(records)
                    .append(", repeatAwareTransportUnsafe=").append(unsafe)
                    .append(", repeatAwareTransportCoveredFaces=").append(coveredFaces)
                    .append(", repeatAwareTransportFacesSaved=").append(facesSaved)
                    .append(", repeatAwareTransportReductionPermille=").append(reductionPermille)
                    .append(", repeatAwareTransportExplicitGradientRequired=").append(explicitGradientRequired)
                    .append(", repeatAwareTransportInternalSReset=").append(internalSReset)
                    .append(", repeatAwareTransportInternalTReset=").append(internalTReset)
                    .append(", repeatAwareTransportInternalBothReset=").append(internalBothReset)
                    .append(", repeatAwareTransportInternalResetUnion=").append(internalResetUnion)
                    .append(", repeatAwareTransportOuterEdgePolicyRequired=").append(outerEdgePolicyRequired)
                    .append(", repeatAwareTransportSameAtlasSamplerRequired=").append(sameAtlasSamplerRequired)
                    .append(", repeatAwareTransportRasterBoundaryReviewRequired=").append(rasterBoundaryReviewRequired)
                    .append(", repeatAwareTransportBoundaryRasterObligationOpen=").append(rasterBoundaryReviewRequired > 0L)
                    .append(", repeatAwareTransportWest=").append(west)
                    .append(", repeatAwareTransportEast=").append(east)
                    .append(", repeatAwareTransportDown=").append(down)
                    .append(", repeatAwareTransportUp=").append(up)
                    .append(", repeatAwareTransportNorth=").append(north)
                    .append(", repeatAwareTransportSouth=").append(south)
                    .append(", repeatAwareTransportWestFaces=").append(westFaces)
                    .append(", repeatAwareTransportEastFaces=").append(eastFaces)
                    .append(", repeatAwareTransportDownFaces=").append(downFaces)
                    .append(", repeatAwareTransportUpFaces=").append(upFaces)
                    .append(", repeatAwareTransportNorthFaces=").append(northFaces)
                    .append(", repeatAwareTransportSouthFaces=").append(southFaces)
                    .append(", repeatAwareTransportWestSaved=").append(westSaved)
                    .append(", repeatAwareTransportEastSaved=").append(eastSaved)
                    .append(", repeatAwareTransportDownSaved=").append(downSaved)
                    .append(", repeatAwareTransportUpSaved=").append(upSaved)
                    .append(", repeatAwareTransportNorthSaved=").append(northSaved)
                    .append(", repeatAwareTransportSouthSaved=").append(southSaved)
                    .append(", repeatAwareTransportRetainedBytes=").append(retainedBytes)
                    .append(", repeatAwareTransportBytesPerRecord=").append(RepeatAwareTransportProof.BYTES_PER_RECORD)
                    .append(", repeatAwareTransportTotalBuildNs=").append(totalBuildNs)
                    .append(", repeatAwareTransportMaxBuildNs=").append(maxBuildNs)
                    .append(", repeatAwareTransportMaxRecords=").append(maxRecords)
                    .append(", repeatAwareTransportScratchBuildUses=").append(scratchUses)
                    .append(", repeatAwareTransportMaxScratchRecords=").append(maxScratchRecords)
                    .append(", repeatAwareTransportProofAudits=").append(proofAudits)
                    .append(", repeatAwareTransportProofAuditMatches=").append(proofMatches)
                    .append(", repeatAwareTransportDeterminismAudits=").append(determinismAudits)
                    .append(", repeatAwareTransportDeterminismAuditMatches=").append(determinismMatches);
        }
    }
}
