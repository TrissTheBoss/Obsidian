package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.graph.FrameGraphProbe;
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
    private final FrameGraphProbe frameGraphProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        FrameGraphProbe probe = null;
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 1 bounded staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 1 device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            probe = new FrameGraphProbe(device, staging);
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
                    "Phase 1 frame-graph/profiler initialization failed; Minecraft will continue for diagnosis.",
                    e);
        }
        stagingUploads = staging;
        deviceArena = arena;
        frameGraphProbe = probe;
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
                    "Phase 1 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}, graphPasses={4}; GPU safety/reuse are completion-gated and profiler-only submissions are forbidden.",
                    frameContexts.size(),
                    cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes(),
                    frameGraphProbe == null ? 0 : frameGraphProbe.graph().passCount());
        }

        if (frameGraphProbe != null) {
            frameGraphProbe.submit(frameIndex);
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
        if (frameGraphProbe != null) {
            frameGraphProbe.poll(frameIndex);
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

    public FrameGraphProbe.State frameGraphProbeState() {
        return frameGraphProbe == null ? FrameGraphProbe.State.FAILED : frameGraphProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        // Staging owns the useful submission fence. Closing it first either
        // completes/reclaims safely or explicitly abandons in-flight memory to
        // Minecraft device shutdown. The probe can then make the same choice
        // for its destination/query resources.
        if (stagingUploads != null) {
            stagingUploads.close();
        }
        if (frameGraphProbe != null) {
            frameGraphProbe.close();
        }
        if (deviceArena != null) {
            deviceArena.close();
        }
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 frame coordinator closed after {0} frame(s): graphState={1}, graphPasses={2}, usefulSubmissions={3}, profilerOnlySubmissions=0, queryPolls={4}, unavailableQueryPolls={5}, stagingSubmittedBytes={6}, stagingReclaimedBytes={7}, stagingHighWater={8}, stagingBackpressureEvents={9}, pendingUploadBatches={10}, arenaUsedBytes={11}, arenaHighWater={12}, arenaAllocations={13}, arenaAllocationFailures={14}, arenaRetired={15}, arenaReclaimed={16}, arenaStaleHandleRejections={17}, arenaFreeSpans={18}, arenaLargestFree={19}, arenaFragmentationPermille={20}, pendingArenaRetirementBatches={21}, retiredResources={22}, releasedResources={23}, pendingRetirements={24}.",
                frameIndex,
                frameGraphProbe == null ? FrameGraphProbe.State.FAILED : frameGraphProbe.state(),
                frameGraphProbe == null ? 0 : frameGraphProbe.graph().passCount(),
                frameGraphProbe == null ? 0L : frameGraphProbe.stream().submissionCount(),
                frameGraphProbe == null ? 0L : frameGraphProbe.profiler().pollCount(),
                frameGraphProbe == null ? 0L : frameGraphProbe.profiler().unavailablePolls(),
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
