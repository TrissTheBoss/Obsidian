package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Render-thread Phase 1 lifecycle root.
 *
 * <p>For now this owns only fixed-allocation CPU frame timing and the one-shot
 * GPU submission probe. Future frame contexts, deferred destruction, upload
 * retirement, render-graph scheduling, and profiler collection should attach
 * here rather than creating unrelated lifecycle hooks.</p>
 */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final GpuSubmissionProbe submissionProbe;

    private long frameIndex;
    private long frameStartNs;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        submissionProbe = new GpuSubmissionProbe(device);
    }

    public void beginFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }

        frameIndex++;
        frameStartNs = System.nanoTime();

        if (!firstFrameLogged) {
            firstFrameLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 frame coordinator active. CPU timing ring capacity={0}; GPU probe is one-shot only.",
                    cpuFrameTimings.capacity());
        }

        submissionProbe.submit(frameIndex);
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }

        long start = frameStartNs;
        if (start != 0L) {
            cpuFrameTimings.record(System.nanoTime() - start);
            frameStartNs = 0L;
        }

        submissionProbe.poll(frameIndex);
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

    public GpuSubmissionProbe.State gpuProbeState() {
        return submissionProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;
        submissionProbe.close();
        LOG.log(System.Logger.Level.INFO,
                "Phase 1 frame coordinator closed after {0} frame(s).",
                frameIndex);
    }
}
