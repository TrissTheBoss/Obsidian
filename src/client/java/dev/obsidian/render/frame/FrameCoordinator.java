package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.MaterializedSectionMesh;
import dev.obsidian.render.terrain.RealSectionMaterialProbe;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
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
    private RealSectionMaterialProbe sectionProbe;

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
        RealSectionMaterialProbe probe = null;
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 2 dev3 bounded staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 2 dev3 device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            probe = new RealSectionMaterialProbe(device, staging, arena, deferredReleases);
        } catch (RuntimeException e) {
            if (probe != null) {
                try {
                    probe.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure.
                }
            }
            if (arena != null) {
                try {
                    arena.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure.
                }
            }
            if (staging != null) {
                try {
                    staging.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure.
                }
            }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev3 material-section initialization failed; Minecraft will continue for diagnosis.", e);
        }
        stagingUploads = staging;
        deviceArena = arena;
        sectionProbe = probe;
    }

    public void beginFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }

        frameIndex++;
        activeFrame = frameContexts.begin(frameIndex, System.nanoTime());

        if (!firstFrameLogged) {
            firstFrameLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 dev3 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}; P2.1/P2.2 snapshot/reference/placement semantics remain the oracle while dev3 captures exact baked material/sprite/UV/tint identity and prepares a live textured comparison.",
                    frameContexts.size(),
                    cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes());
        }
    }

    /** Called from the exact GameRenderer world-render hook before HUD projection/depth reset. */
    public void afterWorldRender(GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (closed || sectionProbe == null) {
            return;
        }

        long nowNs = System.nanoTime();
        if (firstWorldRenderNs == 0L) {
            firstWorldRenderNs = nowNs;
        }
        if (completedVisualPasses == 0 && nowNs - firstWorldRenderNs < VISUAL_ARM_DELAY_NS) {
            if (!visualDelayLogged) {
                visualDelayLogged = true;
                LOG.log(System.Logger.Level.INFO,
                        "Phase 2 dev3 textured comparison is armed but intentionally delayed for 5 seconds after the first world render so the human validation window is not consumed during initial world entry.");
            }
            return;
        }

        sectionProbe.afterWorldRender(renderer, frameIndex);
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }

        FrameContext context = activeFrame;
        activeFrame = null;
        if (context != null) {
            long duration = context.finish(System.nanoTime());
            if (duration > 0L) {
                cpuFrameTimings.record(duration);
            }
        }

        deferredReleases.poll();
        if (stagingUploads != null) {
            stagingUploads.pollReclaims();
        }
        if (deviceArena != null) {
            deviceArena.pollRetirements();
        }
        if (sectionProbe != null) {
            sectionProbe.poll(frameIndex);
            if (sectionProbe.state() == RealSectionMaterialProbe.State.VERIFIED
                    && completedVisualPasses < VISUAL_COMPARISON_PASSES) {
                completedVisualPasses++;
                if (completedVisualPasses < VISUAL_COMPARISON_PASSES) {
                    sectionProbe.close();
                    sectionProbe = new RealSectionMaterialProbe(device, stagingUploads, deviceArena, deferredReleases);
                    LOG.log(System.Logger.Level.INFO,
                            "Phase 2 dev3 textured comparison pass {0}/{1} completed; re-arming immediately so texture/UV/tint alignment remains observable for a sustained human-validation interval.",
                            completedVisualPasses,
                            VISUAL_COMPARISON_PASSES);
                } else {
                    LOG.log(System.Logger.Level.INFO,
                            "Phase 2 dev3 sustained textured comparison completed all {0} pass(es); awaiting shutdown/runtime review.",
                            VISUAL_COMPARISON_PASSES);
                }
            }
        }
    }

    public long frameIndex() {
        return frameIndex;
    }

    public long latestCpuFrameTimeNs() {
        return cpuFrameTimings.latestNs();
    }

    public FrameTimings cpuFrameTimings() {
        return cpuFrameTimings;
    }

    public int pendingRetirements() {
        return deferredReleases.pendingCount();
    }

    public int pendingUploadBatches() {
        return stagingUploads == null ? 0 : stagingUploads.pendingBatches();
    }

    public int pendingArenaRetirementBatches() {
        return deviceArena == null ? 0 : deviceArena.pendingRetirementBatches();
    }

    public RealSectionMaterialProbe.State sectionProbeState() {
        return sectionProbe == null ? RealSectionMaterialProbe.State.FAILED : sectionProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        RealSectionMaterialProbe.State sectionStateBeforeClose =
                sectionProbe == null ? RealSectionMaterialProbe.State.FAILED : sectionProbe.state();
        SectionSnapshot snapshot = sectionProbe == null ? null : sectionProbe.snapshot();
        ReferenceFaceMesh reference = sectionProbe == null ? null : sectionProbe.referenceMesh();
        SectionMaterialSnapshot materials = sectionProbe == null ? null : sectionProbe.materialSnapshot();
        MaterializedSectionMesh drawable = sectionProbe == null ? null : sectionProbe.drawableMesh();

        if (sectionProbe != null) {
            sectionProbe.close();
        }
        if (stagingUploads != null) {
            stagingUploads.close();
        }
        if (deviceArena != null) {
            deviceArena.close();
        }
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 dev3 frame coordinator closed after {0} frame(s): materialSectionResult={1}, section=({2},{3},{4}), sampledCells={5}, interiorAir={6}, interiorSupported={7}, interiorUnsupported={8}, referenceFaces={9}, materializedFaces={10}, rejectedMaterialFaces={11}, materialCount={12}, tintedFaces={13}, tintWorldQueries={14}, rejectedMissing={15}, rejectedGeneralQuads={16}, rejectedDirectionalQuads={17}, rejectedLayer={18}, cutoutFaces={19}, translucentFaces={20}, rejectedAtlas={21}, rejectedGeometry={22}, rejectedTint={23}, snapshotFingerprint={24}, referenceFingerprint={25}, materialFingerprint={26}, drawableFingerprint={27}, resourceEpoch={28}, drawableVertices={29}, drawableIndices={30}, drawableVertexBytes={31}, drawableIndexBytes={32}, pipelineValid={33}, resourceEpochChecks={34}, usefulSubmissions={35}, comparisonDraws={36}, completedVisualPasses={37}, profilerOnlySubmissions=0, worldReadsAfterMaterialCapture=0, nativeGraphicsSeam=false, textured=true, blocksAtlasBound=true, p2_4LightingAo=false, comparisonColorScale=3/4, vanillaTerrainActive=true, stagingSubmittedBytes={38}, stagingReclaimedBytes={39}, stagingHighWater={40}, stagingBackpressureEvents={41}, pendingUploadBatches={42}, arenaUsedBytes={43}, arenaHighWater={44}, arenaAllocations={45}, arenaAllocationFailures={46}, arenaRetired={47}, arenaReclaimed={48}, arenaRetirementBackpressureEvents={49}, arenaStaleHandleRejections={50}, arenaFreeSpans={51}, arenaLargestFree={52}, arenaFragmentationPermille={53}, pendingArenaRetirementBatches={54}, retiredResources={55}, releasedResources={56}, pendingRetirements={57}.",
                frameIndex,
                sectionStateBeforeClose,
                snapshot == null ? 0 : snapshot.sectionX(),
                snapshot == null ? 0 : snapshot.sectionY(),
                snapshot == null ? 0 : snapshot.sectionZ(),
                snapshot == null ? 0 : snapshot.sampledCells(),
                snapshot == null ? 0 : snapshot.interiorAirCells(),
                snapshot == null ? 0 : snapshot.interiorSupportedCells(),
                snapshot == null ? 0 : snapshot.interiorUnsupportedCells(),
                reference == null ? 0 : reference.faceCount(),
                drawable == null ? 0 : drawable.faceCount(),
                drawable == null ? 0 : drawable.rejectedReferenceFaces(),
                materials == null ? 0 : materials.materialCount(),
                materials == null ? 0 : materials.tintedFaces(),
                materials == null ? 0 : materials.tintWorldQueries(),
                materials == null ? 0 : materials.rejectedMissingModelFaces(),
                materials == null ? 0 : materials.rejectedGeneralQuadFaces(),
                materials == null ? 0 : materials.rejectedDirectionalQuadFaces(),
                materials == null ? 0 : materials.rejectedLayerFaces(),
                materials == null ? 0 : materials.cutoutFaces(),
                materials == null ? 0 : materials.translucentFaces(),
                materials == null ? 0 : materials.rejectedAtlasFaces(),
                materials == null ? 0 : materials.rejectedGeometryFaces(),
                materials == null ? 0 : materials.rejectedTintFaces(),
                snapshot == null ? "none" : Long.toUnsignedString(snapshot.fingerprint()),
                reference == null ? "none" : Long.toUnsignedString(reference.fingerprint()),
                materials == null ? "none" : Long.toUnsignedString(materials.fingerprint()),
                drawable == null ? "none" : Long.toUnsignedString(drawable.fingerprint()),
                materials == null ? "none" : Long.toUnsignedString(materials.resourceEpoch()),
                drawable == null ? 0 : drawable.vertexCount(),
                drawable == null ? 0 : drawable.indexCount(),
                drawable == null ? 0 : drawable.vertexBytes(),
                drawable == null ? 0 : drawable.indexBytes(),
                sectionProbe != null && sectionProbe.pipelineValid(),
                sectionProbe == null ? 0L : sectionProbe.resourceEpochChecks(),
                sectionProbe == null ? 0L : sectionProbe.usefulSubmissions(),
                sectionProbe == null ? 0L : sectionProbe.drawSubmissions(),
                completedVisualPasses,
                stagingUploads == null ? 0L : stagingUploads.submittedBytes(),
                stagingUploads == null ? 0L : stagingUploads.reclaimedBytes(),
                stagingUploads == null ? 0L : stagingUploads.highWaterBytes(),
                stagingUploads == null ? 0L : stagingUploads.backpressureEvents(),
                stagingUploads == null ? 0 : stagingUploads.pendingBatches(),
                deviceArena == null ? 0L : deviceArena.usedBytes(),
                deviceArena == null ? 0L : deviceArena.highWaterBytes(),
                deviceArena == null ? 0L : deviceArena.successfulAllocations(),
                deviceArena == null ? 0L : deviceArena.allocationFailures(),
                deviceArena == null ? 0L : deviceArena.retiredAllocations(),
                deviceArena == null ? 0L : deviceArena.reclaimedAllocations(),
                deviceArena == null ? 0L : deviceArena.retirementBackpressureEvents(),
                deviceArena == null ? 0L : deviceArena.staleHandleRejections(),
                deviceArena == null ? 0 : deviceArena.freeSpanCount(),
                deviceArena == null ? 0L : deviceArena.largestFreeBlockBytes(),
                deviceArena == null ? 0 : deviceArena.fragmentationPermille(),
                deviceArena == null ? 0 : deviceArena.pendingRetirementBatches(),
                deferredReleases.retiredCount(),
                deferredReleases.releasedCount(),
                deferredReleases.pendingCount());
    }
}