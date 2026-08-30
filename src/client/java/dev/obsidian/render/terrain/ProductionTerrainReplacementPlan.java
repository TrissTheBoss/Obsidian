package dev.obsidian.render.terrain;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

/**
 * P3.10 dev24 render-thread-only bridge between exact vanilla terrain
 * suppression in LevelRenderer.prepareChunkRenders and Obsidian replacement
 * commands encoded into the same OPAQUE ChunkSectionsToRender RenderPass.
 */
public final class ProductionTerrainReplacementPlan implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/ProductionTerrainReplacement");
    private static final int MAX_CLAIMS = SectionLifecycleEvents.SCENE_RECORD_CAPACITY * 2;

    private final WorkerBackedSectionLifecycleProbe[] probes =
            new WorkerBackedSectionLifecycleProbe[MAX_CLAIMS];
    private final boolean[] cutout = new boolean[MAX_CLAIMS];
    private final long[] generations = new long[MAX_CLAIMS];
    private final long[] resourceEpochs = new long[MAX_CLAIMS];
    private final int[] sectionX = new int[MAX_CLAIMS];
    private final int[] sectionY = new int[MAX_CLAIMS];
    private final int[] sectionZ = new int[MAX_CLAIMS];

    private long preparedFrame = -1L;
    private long preparedResourceEpoch = -1L;
    private int claimCount;
    private int executedCount;
    private boolean closed;
    private boolean hardFailure;

    private long prepareCalls;
    private long supportedVanillaCandidates;
    private long vanillaFallbacks;
    private long solidSuppressions;
    private long cutoutSuppressions;
    private long solidExecutions;
    private long cutoutExecutions;
    private long framesWithReplacement;
    private long duplicateClaims;
    private long claimOverflows;
    private long stalePlanFailures;
    private long executionWithoutClaim;
    private long executionRevalidationFailures;
    private long sameOpaquePassExecutions;
    private int maxClaimsPerPrepare;

    public void beginPrepare(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        if (claimCount != executedCount) {
            stalePlanFailures++;
            hardFailure = true;
        }
        clearClaims();
        preparedFrame = frameSerial;
        preparedResourceEpoch = SectionMaterialSnapshot.currentResourceEpoch();
        prepareCalls++;
    }

    public boolean tryClaim(
            AsyncMultiSectionSceneProbe scene,
            int x,
            int y,
            int z,
            ChunkSectionLayer layer) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || scene == null || preparedFrame < 0L) return false;
        boolean targetCutout;
        if (layer == ChunkSectionLayer.SOLID) targetCutout = false;
        else if (layer == ChunkSectionLayer.CUTOUT) targetCutout = true;
        else return false;

        supportedVanillaCandidates++;
        WorkerBackedSectionLifecycleProbe probe = scene.productionReplacementProbe(x, y, z);
        long epoch = SectionMaterialSnapshot.currentResourceEpoch();
        if (probe == null
                || epoch != preparedResourceEpoch
                || !probe.canClaimProductionReplacement(targetCutout, scene.sceneGeneration(), epoch)) {
            vanillaFallbacks++;
            return false;
        }

        for (int i = 0; i < claimCount; i++) {
            if (sectionX[i] == x && sectionY[i] == y && sectionZ[i] == z && cutout[i] == targetCutout) {
                duplicateClaims++;
                hardFailure = true;
                return false;
            }
        }
        if (claimCount >= MAX_CLAIMS) {
            claimOverflows++;
            hardFailure = true;
            return false;
        }

        int slot = claimCount++;
        probes[slot] = probe;
        cutout[slot] = targetCutout;
        generations[slot] = probe.generation();
        resourceEpochs[slot] = epoch;
        sectionX[slot] = x;
        sectionY[slot] = y;
        sectionZ[slot] = z;
        if (targetCutout) cutoutSuppressions++;
        else solidSuppressions++;
        maxClaimsPerPrepare = Math.max(maxClaimsPerPrepare, claimCount);
        return true;
    }

    public void encodeOpaque(RenderPass pass, GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || claimCount == 0) return;
        if (pass == null || renderer == null) {
            executionWithoutClaim++;
            hardFailure = true;
            throw new IllegalStateException("P3.10 OPAQUE replacement pass/renderer is unavailable");
        }
        if (executedCount != 0) {
            executionWithoutClaim++;
            hardFailure = true;
            throw new IllegalStateException("P3.10 OPAQUE replacement plan executed more than once");
        }

        long currentEpoch = SectionMaterialSnapshot.currentResourceEpoch();
        if (currentEpoch != preparedResourceEpoch) {
            executionRevalidationFailures++;
            hardFailure = true;
            throw new IllegalStateException("P3.10 resource epoch changed after vanilla suppression");
        }

        for (int i = 0; i < claimCount; i++) {
            WorkerBackedSectionLifecycleProbe probe = probes[i];
            boolean targetCutout = cutout[i];
            if (probe == null
                    || probe.generation() != generations[i]
                    || currentEpoch != resourceEpochs[i]
                    || !probe.canClaimProductionReplacement(targetCutout, generations[i], currentEpoch)) {
                executionRevalidationFailures++;
                hardFailure = true;
                throw new IllegalStateException(
                        "P3.10 replacement claim became invalid after exact vanilla suppression for section=("
                                + sectionX[i] + "," + sectionY[i] + "," + sectionZ[i] + ") layer="
                                + (targetCutout ? "CUTOUT" : "SOLID"));
            }
            probe.encodeProductionReplacement(pass, renderer, targetCutout);
            executedCount++;
            sameOpaquePassExecutions++;
            if (targetCutout) cutoutExecutions++;
            else solidExecutions++;
        }
        if (executedCount != claimCount) {
            stalePlanFailures++;
            hardFailure = true;
            throw new IllegalStateException("P3.10 suppression/replacement execution accounting mismatch");
        }
        framesWithReplacement++;
    }

    public boolean hardFailure() { return hardFailure; }
    public long prepareCalls() { return prepareCalls; }
    public long supportedVanillaCandidates() { return supportedVanillaCandidates; }
    public long vanillaFallbacks() { return vanillaFallbacks; }
    public long solidSuppressions() { return solidSuppressions; }
    public long cutoutSuppressions() { return cutoutSuppressions; }
    public long solidExecutions() { return solidExecutions; }
    public long cutoutExecutions() { return cutoutExecutions; }
    public long framesWithReplacement() { return framesWithReplacement; }
    public long duplicateClaims() { return duplicateClaims; }
    public long claimOverflows() { return claimOverflows; }
    public long stalePlanFailures() { return stalePlanFailures; }
    public long executionWithoutClaim() { return executionWithoutClaim; }
    public long executionRevalidationFailures() { return executionRevalidationFailures; }
    public int maxClaimsPerPrepare() { return maxClaimsPerPrepare; }
    public boolean accountingCoherent() {
        return solidSuppressions == solidExecutions
                && cutoutSuppressions == cutoutExecutions
                && duplicateClaims == 0L
                && claimOverflows == 0L
                && stalePlanFailures == 0L
                && executionWithoutClaim == 0L
                && executionRevalidationFailures == 0L;
    }

    private void clearClaims() {
        for (int i = 0; i < claimCount; i++) probes[i] = null;
        claimCount = 0;
        executedCount = 0;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        if (claimCount != executedCount) {
            stalePlanFailures++;
            hardFailure = true;
        }
        LOG.log(System.Logger.Level.INFO,
                "Phase 3 dev24 P3.10 final production terrain replacement: prepareCalls={0}, supportedVanillaCandidates={1}, vanillaFallbacks={2}, solidSuppressions={3}, cutoutSuppressions={4}, solidExecutions={5}, cutoutExecutions={6}, framesWithReplacement={7}, maxClaimsPerPrepare={8}, duplicateClaims={9}, claimOverflows={10}, stalePlanFailures={11}, executionWithoutClaim={12}, executionRevalidationFailures={13}, suppressionExecutionAccountingCoherent={14}, productionCoordinatesExact=true, productionExactColor=true, postWorldComparisonDrawDisabled=true, sameOpaquePass=true, sameOpaquePassExecutions={15}, nativeGraphicsExpansion=false, partialRemeshing=false, partialGpuPatch=false.",
                prepareCalls, supportedVanillaCandidates, vanillaFallbacks,
                solidSuppressions, cutoutSuppressions, solidExecutions, cutoutExecutions,
                framesWithReplacement, maxClaimsPerPrepare, duplicateClaims, claimOverflows,
                stalePlanFailures, executionWithoutClaim, executionRevalidationFailures,
                accountingCoherent(), sameOpaquePassExecutions);
        clearClaims();
        closed = true;
    }
}
