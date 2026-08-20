package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.DrawableSectionMesh;
import dev.obsidian.render.terrain.RealSectionDrawableProbe;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
import dev.obsidian.render.terrain.SectionSnapshot;
import dev.obsidian.render.upload.StagingUploadArena;
import net.minecraft.client.renderer.GameRenderer;

/** Render-thread lifecycle root for the active Obsidian milestone. */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");
    private static final int VALIDATION_STAGING_BYTES = 4 * 1024 * 1024;
    private static final int VALIDATION_DEVICE_ARENA_BYTES = 4 * 1024 * 1024;

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final FrameContextRing frameContexts = new FrameContextRing();
    private final DeferredReleaseQueue deferredReleases = new DeferredReleaseQueue();
    private final StagingUploadArena stagingUploads;
    private final DeviceGeometryArena deviceArena;
    private final RealSectionDrawableProbe sectionProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        RealSectionDrawableProbe probe = null;
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 2 dev2 bounded staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 2 dev2 device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            probe = new RealSectionDrawableProbe(device, staging, arena, deferredReleases);
        } catch (RuntimeException e) {
            if (probe != null) {
                try {
                    probe.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure as the useful diagnostic.
                }
            }
            if (arena != null) {
                try {
                    arena.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure as the useful diagnostic.
                }
            }
            if (staging != null) {
                try {
                    staging.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure as the useful diagnostic.
                }
            }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 dev2 drawable-section initialization failed; Minecraft will continue for diagnosis.", e);
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
                    "Phase 2 dev2 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}; P2.1 snapshot/reference semantics remain the oracle, while dev2 waits for the live world render hook to build and depth-test one drawable real section against vanilla terrain.",
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

    public RealSectionDrawableProbe.State sectionProbeState() {
        return sectionProbe == null ? RealSectionDrawableProbe.State.FAILED : sectionProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        RealSectionDrawableProbe.State sectionStateBeforeClose =
                sectionProbe == null ? RealSectionDrawableProbe.State.FAILED : sectionProbe.state();
        SectionSnapshot snapshot = sectionProbe == null ? null : sectionProbe.snapshot();
        ReferenceFaceMesh reference = sectionProbe == null ? null : sectionProbe.referenceMesh();
        DrawableSectionMesh drawable = sectionProbe == null ? null : sectionProbe.drawableMesh();

        // The dev2 probe registers completion-gated arena/resource retirement
        // before the shared owners perform their bounded shutdown waits.
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
                "Phase 2 dev2 frame coordinator closed after {0} frame(s): drawableSectionResult={1}, section=({2},{3},{4}), sampledCells={5}, interiorAir={6}, interiorSupported={7}, interiorUnsupported={8}, snapshotFingerprint={9}, referenceFaces={10}, referenceFingerprint={11}, drawableFaces={12}, drawableVertices={13}, drawableIndices={14}, drawableFingerprint={15}, drawableVertexBytes={16}, drawableIndexBytes={17}, pipelineValid={18}, usefulSubmissions={19}, comparisonDraws={20}, profilerOnlySubmissions=0, worldReadsAfterSnapshot=0, vanillaTerrainActive=true, stagingSubmittedBytes={21}, stagingReclaimedBytes={22}, stagingHighWater={23}, stagingBackpressureEvents={24}, pendingUploadBatches={25}, arenaUsedBytes={26}, arenaHighWater={27}, arenaAllocations={28}, arenaAllocationFailures={29}, arenaRetired={30}, arenaReclaimed={31}, arenaRetirementBackpressureEvents={32}, arenaStaleHandleRejections={33}, arenaFreeSpans={34}, arenaLargestFree={35}, arenaFragmentationPermille={36}, pendingArenaRetirementBatches={37}, retiredResources={38}, releasedResources={39}, pendingRetirements={40}.",
                frameIndex,
                sectionStateBeforeClose,
                snapshot == null ? 0 : snapshot.sectionX(),
                snapshot == null ? 0 : snapshot.sectionY(),
                snapshot == null ? 0 : snapshot.sectionZ(),
                snapshot == null ? 0 : snapshot.sampledCells(),
                snapshot == null ? 0 : snapshot.interiorAirCells(),
                snapshot == null ? 0 : snapshot.interiorSupportedCells(),
                snapshot == null ? 0 : snapshot.interiorUnsupportedCells(),
                snapshot == null ? "none" : Long.toUnsignedString(snapshot.fingerprint()),
                reference == null ? 0 : reference.faceCount(),
                reference == null ? "none" : Long.toUnsignedString(reference.fingerprint()),
                drawable == null ? 0 : drawable.faceCount(),
                drawable == null ? 0 : drawable.vertexCount(),
                drawable == null ? 0 : drawable.indexCount(),
                drawable == null ? "none" : Long.toUnsignedString(drawable.fingerprint()),
                drawable == null ? 0 : drawable.vertexBytes(),
                drawable == null ? 0 : drawable.indexBytes(),
                sectionProbe != null && sectionProbe.pipelineValid(),
                sectionProbe == null ? 0L : sectionProbe.usefulSubmissions(),
                sectionProbe == null ? 0L : sectionProbe.drawSubmissions(),
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
