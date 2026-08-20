package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.draw.ComputeIndirectDrawProbe;
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
    private final ComputeIndirectDrawProbe computeIndirectProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        ComputeIndirectDrawProbe probe = null;
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 1 bounded staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 1 device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            probe = new ComputeIndirectDrawProbe(device, staging, arena);
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
                    "Phase 1 compute-indirect initialization failed; Minecraft will continue for diagnosis.", e);
        }
        stagingUploads = staging;
        deviceArena = arena;
        computeIndirectProbe = probe;
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
                    "Phase 1 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}, graphPasses={4}; dev8 uses native compute only for GPU indirect generation, graphics remain public Blaze3D, GPU safety/reuse are completion-gated, profiler-only submissions are forbidden.",
                    frameContexts.size(),
                    cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes(),
                    computeIndirectProbe == null ? 0 : computeIndirectProbe.graph().passCount());
        }

        if (computeIndirectProbe != null) {
            computeIndirectProbe.submit(frameIndex);
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
        if (computeIndirectProbe != null) {
            computeIndirectProbe.poll(frameIndex);
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

    public ComputeIndirectDrawProbe.State computeIndirectProbeState() {
        return computeIndirectProbe == null ? ComputeIndirectDrawProbe.State.FAILED : computeIndirectProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        ComputeIndirectDrawProbe.State drawStateBeforeClose =
                computeIndirectProbe == null ? ComputeIndirectDrawProbe.State.FAILED : computeIndirectProbe.state();

        if (stagingUploads != null) {
            stagingUploads.close();
        }
        if (computeIndirectProbe != null) {
            computeIndirectProbe.close();
        }
        if (deviceArena != null) {
            deviceArena.close();
        }
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 frame coordinator closed after {0} frame(s): computeIndirectResult={1}, graphPasses={2}, usefulSubmissions={3}, profilerOnlySubmissions=0, computeDispatches={4}, indirectCalls={5}, indirectCommands={6}, triangles={7}, nativeComputeSeam=true, nativeGraphicsSeam=false, pipelineValid={8}, queryPolls={9}, unavailableQueryPolls={10}, stagingSubmittedBytes={11}, stagingReclaimedBytes={12}, stagingHighWater={13}, stagingBackpressureEvents={14}, pendingUploadBatches={15}, arenaUsedBytes={16}, arenaHighWater={17}, arenaAllocations={18}, arenaAllocationFailures={19}, arenaRetired={20}, arenaReclaimed={21}, arenaRetirementBackpressureEvents={22}, arenaStaleHandleRejections={23}, arenaFreeSpans={24}, arenaLargestFree={25}, arenaFragmentationPermille={26}, pendingArenaRetirementBatches={27}, retiredResources={28}, releasedResources={29}, pendingRetirements={30}.",
                frameIndex,
                drawStateBeforeClose,
                computeIndirectProbe == null ? 0 : computeIndirectProbe.graph().passCount(),
                computeIndirectProbe == null ? 0L : computeIndirectProbe.stream().submissionCount(),
                computeIndirectProbe == null ? 0L : computeIndirectProbe.computeDispatches(),
                computeIndirectProbe == null ? 0L : computeIndirectProbe.indirectCalls(),
                computeIndirectProbe == null ? 0L : computeIndirectProbe.indirectCommandsExecuted(),
                computeIndirectProbe == null ? 0L : computeIndirectProbe.triangles(),
                computeIndirectProbe != null && computeIndirectProbe.pipelineValid(),
                computeIndirectProbe == null ? 0L : computeIndirectProbe.profiler().pollCount(),
                computeIndirectProbe == null ? 0L : computeIndirectProbe.profiler().unavailablePolls(),
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
