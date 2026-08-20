package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.draw.FirstDrawProbe;
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
    private final FirstDrawProbe firstDrawProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        FirstDrawProbe probe = null;
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 1 bounded staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 1 device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            probe = new FirstDrawProbe(device, staging);
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
                    "Phase 1 first-draw initialization failed; Minecraft will continue for diagnosis.",
                    e);
        }
        stagingUploads = staging;
        deviceArena = arena;
        firstDrawProbe = probe;
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
                    "Phase 1 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}, graphPasses={4}; first draw is offscreen, GPU safety/reuse are completion-gated, profiler-only submissions are forbidden.",
                    frameContexts.size(),
                    cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes(),
                    firstDrawProbe == null ? 0 : firstDrawProbe.graph().passCount());
        }

        if (firstDrawProbe != null) {
            firstDrawProbe.submit(frameIndex);
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
        if (firstDrawProbe != null) {
            firstDrawProbe.poll(frameIndex);
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

    public FirstDrawProbe.State firstDrawProbeState() {
        return firstDrawProbe == null ? FirstDrawProbe.State.FAILED : firstDrawProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        FirstDrawProbe.State drawStateBeforeClose =
                firstDrawProbe == null ? FirstDrawProbe.State.FAILED : firstDrawProbe.state();

        // Staging owns the useful submission fence. Closing it first either
        // completes/reclaims safely or explicitly abandons in-flight memory to
        // Minecraft device shutdown. The probe can then make the same choice
        // for its offscreen target/buffers/query pool.
        if (stagingUploads != null) {
            stagingUploads.close();
        }
        if (firstDrawProbe != null) {
            firstDrawProbe.close();
        }
        if (deviceArena != null) {
            deviceArena.close();
        }
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 frame coordinator closed after {0} frame(s): firstDrawResult={1}, graphPasses={2}, usefulSubmissions={3}, profilerOnlySubmissions=0, drawCalls={4}, triangles={5}, pipelineValid={6}, queryPolls={7}, unavailableQueryPolls={8}, stagingSubmittedBytes={9}, stagingReclaimedBytes={10}, stagingHighWater={11}, stagingBackpressureEvents={12}, pendingUploadBatches={13}, arenaUsedBytes={14}, arenaHighWater={15}, arenaAllocations={16}, arenaAllocationFailures={17}, arenaRetired={18}, arenaReclaimed={19}, arenaStaleHandleRejections={20}, arenaFreeSpans={21}, arenaLargestFree={22}, arenaFragmentationPermille={23}, pendingArenaRetirementBatches={24}, retiredResources={25}, releasedResources={26}, pendingRetirements={27}.",
                frameIndex,
                drawStateBeforeClose,
                firstDrawProbe == null ? 0 : firstDrawProbe.graph().passCount(),
                firstDrawProbe == null ? 0L : firstDrawProbe.stream().submissionCount(),
                firstDrawProbe == null ? 0L : firstDrawProbe.drawCalls(),
                firstDrawProbe == null ? 0L : firstDrawProbe.triangles(),
                firstDrawProbe != null && firstDrawProbe.pipelineValid(),
                firstDrawProbe == null ? 0L : firstDrawProbe.profiler().pollCount(),
                firstDrawProbe == null ? 0L : firstDrawProbe.profiler().unavailablePolls(),
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
