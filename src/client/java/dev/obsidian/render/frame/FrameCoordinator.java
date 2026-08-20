package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.draw.ArenaIndirectDrawProbe;
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
    private final ArenaIndirectDrawProbe indirectDrawProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        ArenaIndirectDrawProbe probe = null;
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 1 bounded staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 1 device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            probe = new ArenaIndirectDrawProbe(device, staging, arena);
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
                    "Phase 1 arena-indirect initialization failed; Minecraft will continue for diagnosis.", e);
        }
        stagingUploads = staging;
        deviceArena = arena;
        indirectDrawProbe = probe;
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
                    "Phase 1 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}, graphPasses={4}; dev7 uses arena-backed multi-draw indirect offscreen, GPU safety/reuse are completion-gated, profiler-only submissions are forbidden.",
                    frameContexts.size(),
                    cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes(),
                    indirectDrawProbe == null ? 0 : indirectDrawProbe.graph().passCount());
        }

        if (indirectDrawProbe != null) {
            indirectDrawProbe.submit(frameIndex);
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
        if (indirectDrawProbe != null) {
            indirectDrawProbe.poll(frameIndex);
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

    public ArenaIndirectDrawProbe.State indirectDrawProbeState() {
        return indirectDrawProbe == null ? ArenaIndirectDrawProbe.State.FAILED : indirectDrawProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        ArenaIndirectDrawProbe.State drawStateBeforeClose =
                indirectDrawProbe == null ? ArenaIndirectDrawProbe.State.FAILED : indirectDrawProbe.state();

        // Staging owns one timeline handle for the useful submission. Closing it
        // first either completes/reclaims safely or explicitly abandons in-flight
        // memory to Minecraft device shutdown. The probe/arena can then consume
        // their independent handle for the same submit index.
        if (stagingUploads != null) {
            stagingUploads.close();
        }
        if (indirectDrawProbe != null) {
            indirectDrawProbe.close();
        }
        if (deviceArena != null) {
            deviceArena.close();
        }
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 frame coordinator closed after {0} frame(s): indirectDrawResult={1}, graphPasses={2}, usefulSubmissions={3}, profilerOnlySubmissions=0, indirectCalls={4}, indirectCommands={5}, triangles={6}, pipelineValid={7}, queryPolls={8}, unavailableQueryPolls={9}, stagingSubmittedBytes={10}, stagingReclaimedBytes={11}, stagingHighWater={12}, stagingBackpressureEvents={13}, pendingUploadBatches={14}, arenaUsedBytes={15}, arenaHighWater={16}, arenaAllocations={17}, arenaAllocationFailures={18}, arenaRetired={19}, arenaReclaimed={20}, arenaRetirementBackpressureEvents={21}, arenaStaleHandleRejections={22}, arenaFreeSpans={23}, arenaLargestFree={24}, arenaFragmentationPermille={25}, pendingArenaRetirementBatches={26}, retiredResources={27}, releasedResources={28}, pendingRetirements={29}.",
                frameIndex,
                drawStateBeforeClose,
                indirectDrawProbe == null ? 0 : indirectDrawProbe.graph().passCount(),
                indirectDrawProbe == null ? 0L : indirectDrawProbe.stream().submissionCount(),
                indirectDrawProbe == null ? 0L : indirectDrawProbe.indirectCalls(),
                indirectDrawProbe == null ? 0L : indirectDrawProbe.indirectCommandsExecuted(),
                indirectDrawProbe == null ? 0L : indirectDrawProbe.triangles(),
                indirectDrawProbe != null && indirectDrawProbe.pipelineValid(),
                indirectDrawProbe == null ? 0L : indirectDrawProbe.profiler().pollCount(),
                indirectDrawProbe == null ? 0L : indirectDrawProbe.profiler().unavailablePolls(),
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
