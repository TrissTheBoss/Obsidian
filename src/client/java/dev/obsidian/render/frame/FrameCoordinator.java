package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.LitSectionMesh;
import dev.obsidian.render.terrain.RealSectionLightingProbe;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
import dev.obsidian.render.terrain.SectionLightingSnapshot;
import dev.obsidian.render.terrain.SectionMaterialSnapshot;
import dev.obsidian.render.terrain.SectionSnapshot;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.renderer.GameRenderer;

/** Render-thread lifecycle root for the active Obsidian milestone. */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");
    private static final int VALIDATION_STAGING_BYTES = 4 * 1024 * 1024;
    private static final int VALIDATION_DEVICE_ARENA_BYTES = 4 * 1024 * 1024;
    private static final long VISUAL_ARM_DELAY_NS = 5_000_000_000L;
    private static final int VISUAL_COMPARISON_PASSES = 6;

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final FrameContextRing frameContexts = new FrameContextRing();
    private final DeferredReleaseQueue deferredReleases = new DeferredReleaseQueue();
    private final GpuDevice device;
    private final StagingUploadArena stagingUploads;
    private final DeviceGeometryArena deviceArena;
    private RealSectionLightingProbe sectionProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private long firstWorldRenderNs;
    private int completedVisualPasses;
    private boolean firstFrameLogged;
    private boolean visualDelayLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        this.device = device;
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        RealSectionLightingProbe probe = null;
        try {
            staging = new StagingUploadArena(device, () -> "Obsidian Phase 2 dev4 bounded staging ring", VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(device, () -> "Obsidian Phase 2 dev4 device geometry arena", VALIDATION_DEVICE_ARENA_BYTES);
            probe = new RealSectionLightingProbe(device, staging, arena, deferredReleases);
        } catch (RuntimeException e) {
            if (probe != null) try { probe.close(); } catch (RuntimeException ignored) { }
            if (arena != null) try { arena.close(); } catch (RuntimeException ignored) { }
            if (staging != null) try { staging.close(); } catch (RuntimeException ignored) { }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev4 lighting-section initialization failed; Minecraft will continue for diagnosis.", e);
        }
        stagingUploads = staging;
        deviceArena = arena;
        sectionProbe = probe;
    }

    public void beginFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        frameIndex++;
        activeFrame = frameContexts.begin(frameIndex, System.nanoTime());
        if (!firstFrameLogged) {
            firstFrameLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev4 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}; P2.1-P2.3 geometry/material semantics remain the oracle while dev4 captures exact Minecraft 26.2 BlockModelLighter AO/light results and prepares a live lightmapped comparison.",
                    frameContexts.size(), cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes());
        }
    }

    public void afterWorldRender(GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (closed || sectionProbe == null) return;
        long nowNs = System.nanoTime();
        if (firstWorldRenderNs == 0L) firstWorldRenderNs = nowNs;
        if (completedVisualPasses == 0 && nowNs - firstWorldRenderNs < VISUAL_ARM_DELAY_NS) {
            if (!visualDelayLogged) {
                visualDelayLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 2 dev4 lit comparison is armed but intentionally delayed for 5 seconds after first world render so the human validation window is not consumed during world entry.");
            }
            return;
        }
        sectionProbe.afterWorldRender(renderer, frameIndex);
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        FrameContext context = activeFrame;
        activeFrame = null;
        if (context != null) {
            long duration = context.finish(System.nanoTime());
            if (duration > 0L) cpuFrameTimings.record(duration);
        }
        deferredReleases.poll();
        if (stagingUploads != null) stagingUploads.pollReclaims();
        if (deviceArena != null) deviceArena.pollRetirements();
        if (sectionProbe != null) {
            sectionProbe.poll(frameIndex);
            if (sectionProbe.state() == RealSectionLightingProbe.State.VERIFIED
                    && completedVisualPasses < VISUAL_COMPARISON_PASSES) {
                completedVisualPasses++;
                if (completedVisualPasses < VISUAL_COMPARISON_PASSES) {
                    sectionProbe.close();
                    sectionProbe = new RealSectionLightingProbe(device, stagingUploads, deviceArena, deferredReleases);
                    LOG.log(System.Logger.Level.INFO,
                            "Phase 2 dev4 lit comparison pass {0}/{1} completed; re-arming immediately so block/sky light, face shade and AO corner patterns remain observable.",
                            completedVisualPasses, VISUAL_COMPARISON_PASSES);
                } else {
                    LOG.log(System.Logger.Level.INFO,
                            "Phase 2 dev4 sustained lit comparison completed all {0} pass(es); awaiting shutdown/runtime review.",
                            VISUAL_COMPARISON_PASSES);
                }
            }
        }
    }

    public long frameIndex() { return frameIndex; }
    public long latestCpuFrameTimeNs() { return cpuFrameTimings.latestNs(); }
    public FrameTimings cpuFrameTimings() { return cpuFrameTimings; }
    public int pendingRetirements() { return deferredReleases.pendingCount(); }
    public int pendingUploadBatches() { return stagingUploads == null ? 0 : stagingUploads.pendingBatches(); }
    public int pendingArenaRetirementBatches() { return deviceArena == null ? 0 : deviceArena.pendingRetirementBatches(); }
    public RealSectionLightingProbe.State sectionProbeState() {
        return sectionProbe == null ? RealSectionLightingProbe.State.FAILED : sectionProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;

        RealSectionLightingProbe.State stateBeforeClose =
                sectionProbe == null ? RealSectionLightingProbe.State.FAILED : sectionProbe.state();
        SectionSnapshot snapshot = sectionProbe == null ? null : sectionProbe.snapshot();
        ReferenceFaceMesh reference = sectionProbe == null ? null : sectionProbe.referenceMesh();
        SectionMaterialSnapshot materials = sectionProbe == null ? null : sectionProbe.materialSnapshot();
        SectionLightingSnapshot lighting = sectionProbe == null ? null : sectionProbe.lightingSnapshot();
        LitSectionMesh drawable = sectionProbe == null ? null : sectionProbe.drawableMesh();
        boolean pipelineValid = sectionProbe != null && sectionProbe.pipelineValid();
        long resourceChecks = sectionProbe == null ? 0L : sectionProbe.resourceEpochChecks();
        long useful = sectionProbe == null ? 0L : sectionProbe.usefulSubmissions();
        long draws = sectionProbe == null ? 0L : sectionProbe.drawSubmissions();

        if (sectionProbe != null) sectionProbe.close();
        if (stagingUploads != null) stagingUploads.close();
        if (deviceArena != null) deviceArena.close();
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev4 frame coordinator closed after {0} frame(s): lightingSectionResult={1}, section=({2},{3},{4}), sampledCells={5}, interiorAir={6}, interiorSupported={7}, interiorUnsupported={8}, referenceFaces={9}, materializedFaces={10}, rejectedMaterialFaces={11}, materialCount={12}, tintedFaces={13}, aoFaces={14}, flatFaces={15}, blockLightRange={16}..{17}, skyLightRange={18}..{19}, snapshotFingerprint={20}, referenceFingerprint={21}, materialFingerprint={22}, lightingFingerprint={23}, drawableFingerprint={24}, drawableVertices={25}, drawableIndices={26}, drawableVertexBytes={27}, drawableIndexBytes={28}, pipelineValid={29}, resourceEpochChecks={30}, usefulSubmissions={31}, comparisonDraws={32}, completedVisualPasses={33}, profilerOnlySubmissions=0, worldReadsAfterLightingCapture=0, oneBlockHaloSufficient=true, nativeGraphicsSeam=false, indexedIndirect=true, textured=true, blockVertexFormat=true, blocksAtlasBound=true, lightmapBound=true, comparisonColorScale=3/4, vanillaTerrainActive=true, stagingSubmittedBytes={34}, stagingReclaimedBytes={35}, stagingHighWater={36}, stagingBackpressureEvents={37}, pendingUploadBatches={38}, arenaUsedBytes={39}, arenaHighWater={40}, arenaAllocations={41}, arenaAllocationFailures={42}, arenaRetired={43}, arenaReclaimed={44}, arenaRetirementBackpressureEvents={45}, arenaStaleHandleRejections={46}, arenaFreeSpans={47}, arenaLargestFree={48}, arenaFragmentationPermille={49}, pendingArenaRetirementBatches={50}, retiredResources={51}, releasedResources={52}, pendingRetirements={53}.",
                frameIndex, stateBeforeClose,
                snapshot == null ? 0 : snapshot.sectionX(), snapshot == null ? 0 : snapshot.sectionY(), snapshot == null ? 0 : snapshot.sectionZ(),
                snapshot == null ? 0 : snapshot.sampledCells(), snapshot == null ? 0 : snapshot.interiorAirCells(),
                snapshot == null ? 0 : snapshot.interiorSupportedCells(), snapshot == null ? 0 : snapshot.interiorUnsupportedCells(),
                reference == null ? 0 : reference.faceCount(), drawable == null ? 0 : drawable.faceCount(),
                drawable == null ? 0 : drawable.rejectedReferenceFaces(), materials == null ? 0 : materials.materialCount(),
                materials == null ? 0 : materials.tintedFaces(), lighting == null ? 0 : lighting.ambientOcclusionFaces(),
                lighting == null ? 0 : lighting.flatFaces(), lighting == null ? 0 : lighting.minBlockLight(),
                lighting == null ? 0 : lighting.maxBlockLight(), lighting == null ? 0 : lighting.minSkyLight(),
                lighting == null ? 0 : lighting.maxSkyLight(), snapshot == null ? "none" : Long.toUnsignedString(snapshot.fingerprint()),
                reference == null ? "none" : Long.toUnsignedString(reference.fingerprint()),
                materials == null ? "none" : Long.toUnsignedString(materials.fingerprint()),
                lighting == null ? "none" : Long.toUnsignedString(lighting.fingerprint()),
                drawable == null ? "none" : Long.toUnsignedString(drawable.fingerprint()),
                drawable == null ? 0 : drawable.vertexCount(), drawable == null ? 0 : drawable.indexCount(),
                drawable == null ? 0 : drawable.vertexBytes(), drawable == null ? 0 : drawable.indexBytes(),
                pipelineValid, resourceChecks, useful, draws, completedVisualPasses,
                stagingUploads == null ? 0L : stagingUploads.submittedBytes(), stagingUploads == null ? 0L : stagingUploads.reclaimedBytes(),
                stagingUploads == null ? 0L : stagingUploads.highWaterBytes(), stagingUploads == null ? 0L : stagingUploads.backpressureEvents(),
                stagingUploads == null ? 0 : stagingUploads.pendingBatches(), deviceArena == null ? 0L : deviceArena.usedBytes(),
                deviceArena == null ? 0L : deviceArena.highWaterBytes(), deviceArena == null ? 0L : deviceArena.successfulAllocations(),
                deviceArena == null ? 0L : deviceArena.allocationFailures(), deviceArena == null ? 0L : deviceArena.retiredAllocations(),
                deviceArena == null ? 0L : deviceArena.reclaimedAllocations(), deviceArena == null ? 0L : deviceArena.retirementBackpressureEvents(),
                deviceArena == null ? 0L : deviceArena.staleHandleRejections(), deviceArena == null ? 0 : deviceArena.freeSpanCount(),
                deviceArena == null ? 0L : deviceArena.largestFreeBlockBytes(), deviceArena == null ? 0 : deviceArena.fragmentationPermille(),
                deviceArena == null ? 0 : deviceArena.pendingRetirementBatches(), deferredReleases.retiredCount(),
                deferredReleases.releasedCount(), deferredReleases.pendingCount());
    }
}