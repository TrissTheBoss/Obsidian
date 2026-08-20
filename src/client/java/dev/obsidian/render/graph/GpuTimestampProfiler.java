package dev.obsidian.render.graph;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.OptionalLong;

/**
 * GPU timestamp ranges embedded in an Obsidian-owned command stream.
 *
 * <p>Dev5 intentionally records one validation sample without reusing query
 * slots. This proves integrated timestamping without requiring profiler-only
 * submissions. Query result polling is nonblocking in Minecraft's Vulkan
 * backend because availability bits are requested instead of the Vulkan WAIT
 * flag.</p>
 */
public final class GpuTimestampProfiler implements AutoCloseable {
    private final GpuQueryPool queryPool;
    private final float timestampPeriodNs;
    private final long[] gpuPassNs;

    private int recordedPassCount;
    private long pollCount;
    private long unavailablePolls;
    private boolean submitted;
    private boolean resolved;
    private boolean closed;
    private long totalGpuNs;

    public GpuTimestampProfiler(GpuDevice device, int passCapacity) {
        RenderSystem.assertOnRenderThread();
        if (passCapacity <= 0 || passCapacity > FixedFrameGraph.MAX_PASSES) {
            throw new IllegalArgumentException("Invalid timestamp pass capacity: " + passCapacity);
        }
        this.queryPool = device.createTimestampQueryPool(passCapacity * 2);
        this.timestampPeriodNs = device.getDeviceInfo().timestampPeriod();
        this.gpuPassNs = new long[passCapacity];
    }

    public void writePassStart(CommandEncoder encoder, int passIndex) {
        RenderSystem.assertOnRenderThread();
        ensureRecordable(passIndex);
        encoder.writeTimestamp(queryPool, passIndex * 2);
    }

    public void writePassEnd(CommandEncoder encoder, int passIndex) {
        RenderSystem.assertOnRenderThread();
        ensureRecordable(passIndex);
        encoder.writeTimestamp(queryPool, passIndex * 2 + 1);
    }

    public void markSubmitted(int passCount) {
        RenderSystem.assertOnRenderThread();
        if (submitted) {
            throw new IllegalStateException("Dev5 timestamp sample has already been submitted");
        }
        if (passCount <= 0 || passCount > gpuPassNs.length) {
            throw new IllegalArgumentException("Invalid submitted pass count: " + passCount);
        }
        recordedPassCount = passCount;
        submitted = true;
    }

    /**
     * Polls query availability without waiting. Returns true only once every
     * timestamp in the submitted sample is available and converted to ns.
     */
    public boolean poll() {
        RenderSystem.assertOnRenderThread();
        if (closed || !submitted || resolved) {
            return resolved;
        }

        pollCount++;
        OptionalLong[] values = queryPool.getValues(0, recordedPassCount * 2);
        for (OptionalLong value : values) {
            if (value.isEmpty()) {
                unavailablePolls++;
                return false;
            }
        }

        long firstTick = values[0].getAsLong();
        long lastTick = values[recordedPassCount * 2 - 1].getAsLong();
        totalGpuNs = ticksToNs(lastTick - firstTick);

        for (int pass = 0; pass < recordedPassCount; pass++) {
            long begin = values[pass * 2].getAsLong();
            long end = values[pass * 2 + 1].getAsLong();
            gpuPassNs[pass] = ticksToNs(end - begin);
        }
        resolved = true;
        return true;
    }

    public long passGpuNs(int passIndex) {
        if (!resolved) {
            return 0L;
        }
        if (passIndex < 0 || passIndex >= recordedPassCount) {
            throw new IndexOutOfBoundsException("GPU profiler pass index " + passIndex + " is out of range");
        }
        return gpuPassNs[passIndex];
    }

    public long totalGpuNs() {
        return resolved ? totalGpuNs : 0L;
    }

    public long pollCount() {
        return pollCount;
    }

    public long unavailablePolls() {
        return unavailablePolls;
    }

    public boolean isResolved() {
        return resolved;
    }

    public float timestampPeriodNs() {
        return timestampPeriodNs;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;
        queryPool.close();
    }

    private long ticksToNs(long ticks) {
        if (ticks <= 0L) {
            return 0L;
        }
        double ns = ticks * (double) timestampPeriodNs;
        if (ns >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) ns);
    }

    private void ensureRecordable(int passIndex) {
        if (closed) {
            throw new IllegalStateException("GPU timestamp profiler is closed");
        }
        if (submitted) {
            throw new IllegalStateException("Dev5 timestamp query slots are one-shot and cannot be reused");
        }
        if (passIndex < 0 || passIndex >= gpuPassNs.length) {
            throw new IndexOutOfBoundsException("GPU profiler pass index " + passIndex + " is out of range");
        }
    }
}
