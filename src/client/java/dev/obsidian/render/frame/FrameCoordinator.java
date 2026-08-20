package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.draw.VisibilityCompactionProbe;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.upload.StagingUploadArena;

/** Render-thread Phase 1 lifecycle root. */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");
    private static final int VALIDATION_STAGING_BYTES = 256 * 1024;
    private static final int VALIDATION_DEVICE_ARENA_BYTES = 512 * 1024;

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final FrameContextRing frameContexts = new FrameContextRing();
    private final DeferredReleaseQueue deferredReleases = new DeferredReleaseQueue();
    private final StagingUploadArena stagingUploads;
    private final DeviceGeometryArena deviceArena;
    private final VisibilityCompactionProbe visibilityProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        VisibilityCompactionProbe probe = null;
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 1 bounded staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 1 device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            probe = new VisibilityCompactionProbe(device, staging, arena);
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
                    "Phase 1 visibility-compaction initialization failed; Minecraft will continue for diagnosis.", e);
        }
        stagingUploads = staging;
        deviceArena = arena;
        visibilityProbe = probe;
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
                    "Phase 1 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}, graphPasses={4}; dev9 uses GPU scene visibility + command compaction, native compute only, public fixed-count Blaze3D indirect graphics with zeroed tail slots, GPU safety/reuse are completion-gated, profiler-only submissions are forbidden.",
                    frameContexts.size(),
                    cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes(),
                    visibilityProbe == null ? 0 : visibilityProbe.graph().passCount());
        }

        if (visibilityProbe != null) {
            visibilityProbe.submit(frameIndex);
        }
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
        if (visibilityProbe != null) {
            visibilityProbe.poll(frameIndex);
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

    public VisibilityCompactionProbe.State visibilityProbeState() {
        return visibilityProbe == null ? VisibilityCompactionProbe.State.FAILED : visibilityProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        VisibilityCompactionProbe.State visibilityStateBeforeClose =
                visibilityProbe == null ? VisibilityCompactionProbe.State.FAILED : visibilityProbe.state();

        if (stagingUploads != null) {
            stagingUploads.close();
        }
        if (visibilityProbe != null) {
            visibilityProbe.close();
        }
        if (deviceArena != null) {
            deviceArena.close();
        }
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 frame coordinator closed after {0} frame(s): visibilityResult={1}, graphPasses={2}, usefulSubmissions={3}, profilerOnlySubmissions=0, computeDispatches={4}, candidates=4, visibleCount={5}, culledCount={6}, indirectCalls={7}, publicIndirectSlots={8}, nativeComputeSeam=true, nativeGraphicsSeam=false, indirectCountConsumed=false, pipelineValid={9}, queryPolls={10}, unavailableQueryPolls={11}, stagingSubmittedBytes={12}, stagingReclaimedBytes={13}, stagingHighWater={14}, stagingBackpressureEvents={15}, pendingUploadBatches={16}, arenaUsedBytes={17}, arenaHighWater={18}, arenaAllocations={19}, arenaAllocationFailures={20}, arenaRetired={21}, arenaReclaimed={22}, arenaRetirementBackpressureEvents={23}, arenaStaleHandleRejections={24}, arenaFreeSpans={25}, arenaLargestFree={26}, arenaFragmentationPermille={27}, pendingArenaRetirementBatches={28}, retiredResources={29}, releasedResources={30}, pendingRetirements={31}.",
                frameIndex,
                visibilityStateBeforeClose,
                visibilityProbe == null ? 0 : visibilityProbe.graph().passCount(),
                visibilityProbe == null ? 0L : visibilityProbe.stream().submissionCount(),
                visibilityProbe == null ? 0L : visibilityProbe.computeDispatches(),
                visibilityProbe == null ? 0 : visibilityProbe.visibleCount(),
                visibilityProbe == null ? 0 : visibilityProbe.culledCount(),
                visibilityProbe == null ? 0L : visibilityProbe.indirectCalls(),
                visibilityProbe == null ? 0L : visibilityProbe.publicIndirectSlots(),
                visibilityProbe != null && visibilityProbe.pipelineValid(),
                visibilityProbe == null ? 0L : visibilityProbe.profiler().pollCount(),
                visibilityProbe == null ? 0L : visibilityProbe.profiler().unavailablePolls(),
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
