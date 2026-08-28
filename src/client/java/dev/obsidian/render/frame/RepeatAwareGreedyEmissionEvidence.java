package dev.obsidian.render.frame;

/** Runtime aggregation/gate logic for the P3.4 dev11 repeat-aware GPU-emission canary. */
final class RepeatAwareGreedyEmissionEvidence {
    private static final long INDIRECT_CLASSES_PER_DRAW = 4L;

    private RepeatAwareGreedyEmissionEvidence() { }

    static Snapshot capture(
            boolean priorGateReady,
            boolean productionWorkerIntegrationReady,
            boolean localSceneReady,
            long recordInstalls,
            long sceneWorkerInstalls,
            long sceneWorkerCompleted,
            long drawSubmissions,
            long indirectCalls,
            long resourceEpochChecks,
            RepeatAwareTransportEvidence.Snapshot transport,
            boolean workersClean,
            boolean stagingClean,
            boolean arenaClean,
            boolean resourcesClean) {
        long expectedIndirectCalls;
        try {
            expectedIndirectCalls = Math.multiplyExact(drawSubmissions, INDIRECT_CLASSES_PER_DRAW);
        } catch (ArithmeticException overflow) {
            return Snapshot.empty();
        }

        long transportRecords = transport == null ? 0L : transport.records();
        long transportCoveredFaces = transport == null ? 0L : transport.coveredFaces();
        long transportFacesSaved = transport == null ? 0L : transport.facesSaved();
        boolean installValidationPassed = recordInstalls > 0L
                && sceneWorkerInstalls == recordInstalls
                && sceneWorkerCompleted >= sceneWorkerInstalls;
        boolean fixedFourClassDrawContract = drawSubmissions > 0L
                && indirectCalls == expectedIndirectCalls;

        boolean ready = priorGateReady
                && productionWorkerIntegrationReady
                && localSceneReady
                && installValidationPassed
                && fixedFourClassDrawContract
                && resourceEpochChecks >= drawSubmissions
                && transportRecords > 0L
                && transportCoveredFaces > transportRecords
                && transportFacesSaved > 0L
                && transportFacesSaved == transportCoveredFaces - transportRecords
                && workersClean && stagingClean && arenaClean && resourcesClean;

        return new Snapshot(
                ready,
                recordInstalls,
                sceneWorkerInstalls,
                sceneWorkerCompleted,
                drawSubmissions,
                indirectCalls,
                expectedIndirectCalls,
                resourceEpochChecks,
                transportRecords,
                transportCoveredFaces,
                transportFacesSaved,
                installValidationPassed,
                fixedFourClassDrawContract);
    }

    record Snapshot(
            boolean ready,
            long recordInstalls,
            long sceneWorkerInstalls,
            long sceneWorkerCompleted,
            long drawSubmissions,
            long indirectCalls,
            long expectedIndirectCalls,
            long resourceEpochChecks,
            long transportRecords,
            long transportCoveredFaces,
            long transportFacesSaved,
            boolean installValidationPassed,
            boolean fixedFourClassDrawContract) {

        static Snapshot empty() {
            return new Snapshot(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false);
        }

        void appendTo(StringBuilder out) {
            out.append(", repeatAwareGreedyGpuEmission=true")
                    .append(", repeatAwareGreedyInstalledRecords=").append(recordInstalls)
                    .append(", repeatAwareGreedySceneWorkerInstalls=").append(sceneWorkerInstalls)
                    .append(", repeatAwareGreedySceneWorkerCompleted=").append(sceneWorkerCompleted)
                    .append(", repeatAwareGreedyDrawSubmissions=").append(drawSubmissions)
                    .append(", repeatAwareGreedyIndirectCalls=").append(indirectCalls)
                    .append(", repeatAwareGreedyExpectedIndirectCalls=").append(expectedIndirectCalls)
                    .append(", repeatAwareGreedyIndirectClassesPerDraw=").append(INDIRECT_CLASSES_PER_DRAW)
                    .append(", repeatAwareGreedyResourceEpochChecks=").append(resourceEpochChecks)
                    .append(", repeatAwareGreedyTransportRecords=").append(transportRecords)
                    .append(", repeatAwareGreedyTransportCoveredFaces=").append(transportCoveredFaces)
                    .append(", repeatAwareGreedyTransportFacesSaved=").append(transportFacesSaved)
                    .append(", repeatAwareGreedyInstallValidationPassed=").append(installValidationPassed)
                    .append(", repeatAwareGreedyFixedFourClassDrawContract=").append(fixedFourClassDrawContract)
                    .append(", repeatAwareGreedyVisualValidationRequired=true")
                    .append(", repeatAwareGreedyVisualValidationAutomated=false");
        }
    }
}
