package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.resource.GpuResourceLifetimeProbe;

/**
 * Render-thread Phase 1 lifecycle root.
 *
 * <p>This owns preallocated CPU frame metadata/timing plus the first explicit
 * GPU resource-retirement path. Future uploads, render-graph work, profiler
 * collection, and renderer-owned resources should attach here rather than
 * creating unrelated lifecycle hooks.</p>
 */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final FrameContextRing frameContexts = new FrameContextRing();
    private final DeferredReleaseQueue deferredReleases = new DeferredReleaseQueue();
    private final GpuResourceLifetimeProbe resourceLifetimeProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        resourceLifetimeProbe = new GpuResourceLifetimeProbe(device, deferredReleases);
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
                    "Phase 1 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}; frame slots are bookkeeping only and GPU safety is fence-gated.",
                    frameContexts.size(), cpuFrameTimings.capacity());
        }

        resourceLifetimeProbe.submit(frameIndex);
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
        resourceLifetimeProbe.poll(frameIndex);
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

    public GpuResourceLifetimeProbe.State resourceLifetimeProbeState() {
        return resourceLifetimeProbe.state();
    }

    public int pendingRetirements() {
        return deferredReleases.pendingCount();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        deferredReleases.close();
        resourceLifetimeProbe.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 frame coordinator closed after {0} frame(s): retiredResources={1}, releasedResources={2}, pending={3}.",
                frameIndex,
                deferredReleases.retiredCount(),
                deferredReleases.releasedCount(),
                deferredReleases.pendingCount());
    }
}
