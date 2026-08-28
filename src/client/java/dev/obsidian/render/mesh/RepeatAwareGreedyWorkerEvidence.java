package dev.obsidian.render.mesh;

import dev.obsidian.render.terrain.BinarySectionVisibility;
import dev.obsidian.render.terrain.RepeatAwareGreedyMesh;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/** Thread-safe aggregate counters for the P3.4 dev11 hybrid greedy mesh. */
final class RepeatAwareGreedyWorkerEvidence {
    private final AtomicLong builds = new AtomicLong();
    private final AtomicLong sourceQuads = new AtomicLong();
    private final AtomicLong transportRecords = new AtomicLong();
    private final AtomicLong suppressedSourceQuads = new AtomicLong();
    private final AtomicLong passthroughQuads = new AtomicLong();
    private final AtomicLong mergedQuads = new AtomicLong();
    private final AtomicLong hybridQuads = new AtomicLong();
    private final AtomicLong sourceSolid = new AtomicLong();
    private final AtomicLong sourceCutout = new AtomicLong();
    private final AtomicLong suppressedSolid = new AtomicLong();
    private final AtomicLong suppressedCutout = new AtomicLong();
    private final AtomicLong passthroughSolid = new AtomicLong();
    private final AtomicLong passthroughCutout = new AtomicLong();
    private final AtomicLong mergedSolid = new AtomicLong();
    private final AtomicLong mergedCutout = new AtomicLong();
    private final AtomicLong hybridSolid = new AtomicLong();
    private final AtomicLong hybridCutout = new AtomicLong();
    private final AtomicLong facesSaved = new AtomicLong();
    private final AtomicLong passthroughVertexBytes = new AtomicLong();
    private final AtomicLong mergedVertexBytes = new AtomicLong();
    private final AtomicLong indexBytes = new AtomicLong();
    private final AtomicLong totalUploadBytes = new AtomicLong();
    private final AtomicLong sourceUploadBytes = new AtomicLong();
    private final AtomicLong totalBuildNs = new AtomicLong();
    private final AtomicLong maxBuildNs = new AtomicLong();
    private final AtomicLong maxHybridQuads = new AtomicLong();
    private final AtomicLong maxMergedQuads = new AtomicLong();
    private final AtomicLong scratchUses = new AtomicLong();
    private final AtomicLong maxScratchSourceQuads = new AtomicLong();
    private final AtomicLong maxScratchMergedQuads = new AtomicLong();
    private final AtomicLong accountingAudits = new AtomicLong();
    private final AtomicLong accountingMatches = new AtomicLong();
    private final AtomicLong determinismAudits = new AtomicLong();
    private final AtomicLong determinismMatches = new AtomicLong();
    private final AtomicLongArray mergedByDirection = new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray coveredByDirection = new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);
    private final AtomicLongArray savedByDirection = new AtomicLongArray(BinarySectionVisibility.DIRECTION_COUNT);

    void recordPrimary(RepeatAwareGreedyMesh mesh, RepeatAwareGreedyMesh.BuildScratch scratch) {
        builds.incrementAndGet();
        sourceQuads.addAndGet(mesh.sourceQuadCount());
        transportRecords.addAndGet(mesh.mergedQuadCount());
        suppressedSourceQuads.addAndGet(mesh.suppressedSourceQuads());
        passthroughQuads.addAndGet(mesh.passthroughQuadCount());
        mergedQuads.addAndGet(mesh.mergedQuadCount());
        hybridQuads.addAndGet(mesh.hybridQuadCount());
        sourceSolid.addAndGet(mesh.sourceSolidQuadCount());
        sourceCutout.addAndGet(mesh.sourceCutoutQuadCount());
        suppressedSolid.addAndGet(mesh.suppressedSolidQuads());
        suppressedCutout.addAndGet(mesh.suppressedCutoutQuads());
        passthroughSolid.addAndGet(mesh.passthroughSolidQuadCount());
        passthroughCutout.addAndGet(mesh.passthroughCutoutQuadCount());
        mergedSolid.addAndGet(mesh.mergedSolidQuadCount());
        mergedCutout.addAndGet(mesh.mergedCutoutQuadCount());
        hybridSolid.addAndGet(mesh.hybridSolidQuads());
        hybridCutout.addAndGet(mesh.hybridCutoutQuads());
        facesSaved.addAndGet(mesh.facesSaved());
        passthroughVertexBytes.addAndGet(mesh.passthroughVertexBytes());
        mergedVertexBytes.addAndGet(mesh.mergedVertexBytes());
        indexBytes.addAndGet(mesh.indexBytes());
        totalUploadBytes.addAndGet(mesh.totalUploadBytes());
        sourceUploadBytes.addAndGet(mesh.sourceUploadBytes());
        totalBuildNs.addAndGet(mesh.buildTimeNs());
        updateMax(maxBuildNs, mesh.buildTimeNs());
        updateMax(maxHybridQuads, mesh.hybridQuadCount());
        updateMax(maxMergedQuads, mesh.mergedQuadCount());
        scratchUses.set(Math.max(scratchUses.get(), scratch.uses()));
        updateMax(maxScratchSourceQuads, scratch.highWaterSourceQuads());
        updateMax(maxScratchMergedQuads, scratch.highWaterMergedQuads());
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            mergedByDirection.addAndGet(direction, mesh.directionMerged(direction));
            coveredByDirection.addAndGet(direction, mesh.directionCoveredFaces(direction));
            savedByDirection.addAndGet(direction, mesh.directionFacesSaved(direction));
        }
        accountingAudits.incrementAndGet();
        accountingMatches.incrementAndGet();
    }

    void recordDeterminism(boolean match) {
        determinismAudits.incrementAndGet();
        if (match) determinismMatches.incrementAndGet();
    }

    Snapshot snapshot() {
        long[] mergedDirections = new long[BinarySectionVisibility.DIRECTION_COUNT];
        long[] coveredDirections = new long[BinarySectionVisibility.DIRECTION_COUNT];
        long[] savedDirections = new long[BinarySectionVisibility.DIRECTION_COUNT];
        for (int direction = 0; direction < BinarySectionVisibility.DIRECTION_COUNT; direction++) {
            mergedDirections[direction] = mergedByDirection.get(direction);
            coveredDirections[direction] = coveredByDirection.get(direction);
            savedDirections[direction] = savedByDirection.get(direction);
        }
        return new Snapshot(
                builds.get(), sourceQuads.get(), transportRecords.get(), suppressedSourceQuads.get(),
                passthroughQuads.get(), mergedQuads.get(), hybridQuads.get(),
                sourceSolid.get(), sourceCutout.get(), suppressedSolid.get(), suppressedCutout.get(),
                passthroughSolid.get(), passthroughCutout.get(), mergedSolid.get(), mergedCutout.get(),
                hybridSolid.get(), hybridCutout.get(), facesSaved.get(),
                passthroughVertexBytes.get(), mergedVertexBytes.get(), indexBytes.get(),
                totalUploadBytes.get(), sourceUploadBytes.get(), totalBuildNs.get(), maxBuildNs.get(),
                maxHybridQuads.get(), maxMergedQuads.get(), scratchUses.get(),
                maxScratchSourceQuads.get(), maxScratchMergedQuads.get(),
                accountingAudits.get(), accountingMatches.get(), determinismAudits.get(), determinismMatches.get(),
                mergedDirections, coveredDirections, savedDirections);
    }

    private static void updateMax(AtomicLong target, long value) {
        long previous;
        do {
            previous = target.get();
            if (value <= previous) return;
        } while (!target.compareAndSet(previous, value));
    }

    record Snapshot(
            long builds,
            long sourceQuads,
            long transportRecords,
            long suppressedSourceQuads,
            long passthroughQuads,
            long mergedQuads,
            long hybridQuads,
            long sourceSolid,
            long sourceCutout,
            long suppressedSolid,
            long suppressedCutout,
            long passthroughSolid,
            long passthroughCutout,
            long mergedSolid,
            long mergedCutout,
            long hybridSolid,
            long hybridCutout,
            long facesSaved,
            long passthroughVertexBytes,
            long mergedVertexBytes,
            long indexBytes,
            long totalUploadBytes,
            long sourceUploadBytes,
            long totalBuildNs,
            long maxBuildNs,
            long maxHybridQuads,
            long maxMergedQuads,
            long scratchUses,
            long maxScratchSourceQuads,
            long maxScratchMergedQuads,
            long accountingAudits,
            long accountingMatches,
            long determinismAudits,
            long determinismMatches,
            long[] mergedDirections,
            long[] coveredDirections,
            long[] savedDirections) {
        long mergedDirectionSum() { return sum(mergedDirections); }
        long coveredDirectionSum() { return sum(coveredDirections); }
        long savedDirectionSum() { return sum(savedDirections); }
        long mergedDirection(int direction) { return mergedDirections[direction]; }
        long coveredDirection(int direction) { return coveredDirections[direction]; }
        long savedDirection(int direction) { return savedDirections[direction]; }
        private static long sum(long[] values) {
            long total = 0L;
            for (long value : values) total += value;
            return total;
        }
    }
}
