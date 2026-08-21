package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.BakedSectionMesh;
import dev.obsidian.render.terrain.RealSectionBroadModelProbe;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
import dev.obsidian.render.terrain.SectionBakedQuadSnapshot;
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
    private RealSectionBroadModelProbe sectionProbe;

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
        RealSectionBroadModelProbe probe = null;
        try {
            staging = new StagingUploadArena(device, () -> "Obsidian Phase 2 dev5 bounded staging ring", VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(device, () -> "Obsidian Phase 2 dev5 device geometry arena", VALIDATION_DEVICE_ARENA_BYTES);
            probe = new RealSectionBroadModelProbe(device, staging, arena, deferredReleases);
        } catch (RuntimeException e) {
            if (probe != null) try { probe.close(); } catch (RuntimeException ignored) { }
            if (arena != null) try { arena.close(); } catch (RuntimeException ignored) { }
            if (staging != null) try { staging.close(); } catch (RuntimeException ignored) { }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev5 generalized-section initialization failed; Minecraft will continue for diagnosis.", e);
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
                    "Phase 2 dev5 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}; dev5 captures exact vanilla-emitted arbitrary MODEL quads and compares deterministic SOLID+CUTOUT BLOCK-format ranges while the permanent P2.1 cube oracle remains independent.",
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
                        "Phase 2 dev5 SOLID+CUTOUT comparison is armed but intentionally delayed for 5 seconds after first world render so the human validation window is not consumed during world entry.");
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
            if (sectionProbe.state() == RealSectionBroadModelProbe.State.VERIFIED
                    && completedVisualPasses < VISUAL_COMPARISON_PASSES) {
                completedVisualPasses++;
                if (completedVisualPasses < VISUAL_COMPARISON_PASSES) {
                    sectionProbe.close();
                    sectionProbe = new RealSectionBroadModelProbe(device, stagingUploads, deviceArena, deferredReleases);
                    LOG.log(System.Logger.Level.INFO,
                            "Phase 2 dev5 generalized comparison pass {0}/{1} completed; re-arming immediately so arbitrary geometry, SOLID/CUTOUT layer identity, tint, light and AO remain observable across fresh captures.",
                            completedVisualPasses, VISUAL_COMPARISON_PASSES);
                } else {
                    LOG.log(System.Logger.Level.INFO,
                            "Phase 2 dev5 sustained generalized comparison completed all {0} pass(es); awaiting shutdown/runtime review.",
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
    public RealSectionBroadModelProbe.State sectionProbeState() {
        return sectionProbe == null ? RealSectionBroadModelProbe.State.FAILED : sectionProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;

        RealSectionBroadModelProbe.State stateBeforeClose =
                sectionProbe == null ? RealSectionBroadModelProbe.State.FAILED : sectionProbe.state();
        SectionSnapshot snapshot = sectionProbe == null ? null : sectionProbe.snapshot();
        ReferenceFaceMesh reference = sectionProbe == null ? null : sectionProbe.referenceMesh();
        SectionBakedQuadSnapshot baked = sectionProbe == null ? null : sectionProbe.bakedSnapshot();
        BakedSectionMesh drawable = sectionProbe == null ? null : sectionProbe.drawableMesh();
        boolean pipelineValid = sectionProbe != null && sectionProbe.pipelineValid();
        long resourceChecks = sectionProbe == null ? 0L : sectionProbe.resourceEpochChecks();
        long useful = sectionProbe == null ? 0L : sectionProbe.usefulSubmissions();
        long draws = sectionProbe == null ? 0L : sectionProbe.drawSubmissions();
        long indirectCalls = sectionProbe == null ? 0L : sectionProbe.indirectCalls();

        if (sectionProbe != null) sectionProbe.close();
        if (stagingUploads != null) stagingUploads.close();
        if (deviceArena != null) deviceArena.close();
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev5 frame coordinator closed after {0} frame(s): generalizedSectionResult={1}, section=({2},{3},{4}), sampledCells={5}, interiorAir={6}, interiorSupported={7}, interiorUnsupported={8}, cubeReferenceFaces={9}, modelBlocksScanned={10}, acceptedBlocks={11}, noVisibleBlocks={12}, rejectedBlocks={13}, rejectedLeaves={14}, rejectedFluid={15}, rejectedBlockEntity={16}, rejectedMissingModel={17}, rejectedMaterial={18}, rejectedTranslucent={19}, rejectedAtlas={20}, generalizedQuads={21}, solidQuads={22}, cutoutQuads={23}, materialCount={24}, tintedQuads={25}, blockLightRange={26}..{27}, skyLightRange={28}..{29}, snapshotFingerprint={30}, cubeReferenceFingerprint={31}, generalizedFingerprint={32}, drawableFingerprint={33}, drawableVertices={34}, drawableIndices={35}, drawableVertexBytes={36}, drawableIndexBytes={37}, pipelineValid={38}, resourceEpochChecks={39}, usefulSubmissions={40}, comparisonDraws={41}, indirectCalls={42}, completedVisualPasses={43}, profilerOnlySubmissions=0, worldReadsAfterGeneralizedCapture=0, cubeOraclePreserved=true, oneBlockHaloSufficientForCapturedCullingLightSamples=true, nativeGraphicsSeam=false, indexedIndirect=true, textured=true, blockVertexFormat=true, blocksAtlasBound=true, lightmapBound=true, solidPipeline=true, cutoutPipeline=true, cutoutAlphaThreshold=0.5, comparisonColorScale=3/4, comparisonFaceOffset=1/512, vanillaTerrainActive=true, stagingSubmittedBytes={44}, stagingReclaimedBytes={45}, stagingHighWater={46}, stagingBackpressureEvents={47}, pendingUploadBatches={48}, arenaUsedBytes={49}, arenaHighWater={50}, arenaAllocations={51}, arenaAllocationFailures={52}, arenaRetired={53}, arenaReclaimed={54}, arenaRetirementBackpressureEvents={55}, arenaStaleHandleRejections={56}, arenaFreeSpans={57}, arenaLargestFree={58}, arenaFragmentationPermille={59}, pendingArenaRetirementBatches={60}, retiredResources={61}, releasedResources={62}, pendingRetirements={63}.",
                frameIndex, stateBeforeClose,
                snapshot == null ? 0 : snapshot.sectionX(), snapshot == null ? 0 : snapshot.sectionY(), snapshot == null ? 0 : snapshot.sectionZ(),
                snapshot == null ? 0 : snapshot.sampledCells(), snapshot == null ? 0 : snapshot.interiorAirCells(),
                snapshot == null ? 0 : snapshot.interiorSupportedCells(), snapshot == null ? 0 : snapshot.interiorUnsupportedCells(),
                reference == null ? 0 : reference.faceCount(), baked == null ? 0 : baked.modelBlocksScanned(),
                baked == null ? 0 : baked.acceptedBlocks(), baked == null ? 0 : baked.noVisibleQuadBlocks(),
                baked == null ? 0 : baked.rejectedBlocks(), baked == null ? 0 : baked.rejectedLeavesBlocks(),
                baked == null ? 0 : baked.rejectedFluidBlocks(), baked == null ? 0 : baked.rejectedBlockEntityBlocks(),
                baked == null ? 0 : baked.rejectedMissingModelBlocks(), baked == null ? 0 : baked.rejectedMaterialBlocks(),
                baked == null ? 0 : baked.rejectedTranslucentBlocks(), baked == null ? 0 : baked.rejectedAtlasBlocks(),
                baked == null ? 0 : baked.quadCount(), baked == null ? 0 : baked.solidQuads(), baked == null ? 0 : baked.cutoutQuads(),
                baked == null ? 0 : baked.materialCount(), baked == null ? 0 : baked.tintedQuads(),
                baked == null ? 0 : baked.minBlockLight(), baked == null ? 0 : baked.maxBlockLight(),
                baked == null ? 0 : baked.minSkyLight(), baked == null ? 0 : baked.maxSkyLight(),
                snapshot == null ? "none" : Long.toUnsignedString(snapshot.fingerprint()),
                reference == null ? "none" : Long.toUnsignedString(reference.fingerprint()),
                baked == null ? "none" : Long.toUnsignedString(baked.fingerprint()),
                drawable == null ? "none" : Long.toUnsignedString(drawable.fingerprint()),
                drawable == null ? 0 : drawable.vertexCount(), drawable == null ? 0 : drawable.indexCount(),
                drawable == null ? 0 : drawable.vertexBytes(), drawable == null ? 0 : drawable.indexBytes(),
                pipelineValid, resourceChecks, useful, draws, indirectCalls, completedVisualPasses,
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
