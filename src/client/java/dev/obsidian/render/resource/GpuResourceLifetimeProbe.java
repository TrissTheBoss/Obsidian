package dev.obsidian.render.resource;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Supplier;

/**
 * One-shot Phase 1 validation for fence-gated resource retirement.
 *
 * <p>The probe creates a tiny GPU buffer, records one write, inserts a fence,
 * submits once, and immediately retires the buffer to the deferred release
 * queue. The buffer must remain alive until the fence completes, after which
 * the queue closes it. This changes no rendered pixels.</p>
 */
public final class GpuResourceLifetimeProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/ResourceLifetimeProbe");
    private static final Supplier<String> LABEL = () -> "Obsidian Phase 1 resource lifetime probe";
    private static final int PROBE_BYTES = 64;
    private static final long EMERGENCY_WAIT_NS = 2_000_000_000L;

    public enum State {
        ARMED,
        RETIRED,
        RELEASED,
        FAILED,
        CLOSED
    }

    private final GpuDevice device;
    private final DeferredReleaseQueue releaseQueue;

    private GpuBuffer probeBuffer;
    private ByteBuffer sourceData;
    private GpuBuffer emergencyBuffer;
    private GpuFence emergencyFence;
    private State state = State.ARMED;
    private long retiredFrame = -1L;
    private long releasedFrame = -1L;

    public GpuResourceLifetimeProbe(GpuDevice device, DeferredReleaseQueue releaseQueue) {
        this.device = device;
        this.releaseQueue = releaseQueue;
    }

    public void submit(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.ARMED) {
            return;
        }

        GpuBuffer created = null;
        GpuFence fence = null;
        boolean submitted = false;

        try {
            created = device.createBuffer(LABEL, GpuBuffer.USAGE_COPY_DST, PROBE_BYTES);

            ByteBuffer data = ByteBuffer.allocateDirect(PROBE_BYTES).order(ByteOrder.nativeOrder());
            data.putLong(0x4F4253494449414EL); // "OBSIDIAN"
            data.putLong(frameSerial);
            while (data.hasRemaining()) {
                data.putLong(0L);
            }
            data.flip();

            CommandEncoder encoder = device.createCommandEncoder();
            encoder.writeToBuffer(created.slice(0L, PROBE_BYTES), data);
            fence = encoder.createFence();
            encoder.submit();
            submitted = true;

            releaseQueue.retire(created, fence, frameSerial);
            probeBuffer = created;
            sourceData = data;
            retiredFrame = frameSerial;
            state = State.RETIRED;

            LOG.log(System.Logger.Level.INFO,
                    "Phase 1 resource lifetime probe submitted and retired on frame {0}: bufferBytes={1}, pendingRetirements={2}.",
                    frameSerial, PROBE_BYTES, releaseQueue.pendingCount());
        } catch (RuntimeException e) {
            state = State.FAILED;

            if (submitted && created != null && fence != null) {
                emergencyBuffer = created;
                emergencyFence = fence;
                LOG.log(System.Logger.Level.ERROR,
                        "Resource lifetime probe submitted GPU work but could not enqueue retirement; preserving the resource until bounded shutdown cleanup.");
            } else {
                if (fence != null) {
                    try {
                        fence.close();
                    } catch (RuntimeException ignored) {
                        // Preserve the original failure as the useful diagnostic.
                    }
                }
                if (created != null) {
                    try {
                        created.close();
                    } catch (RuntimeException ignored) {
                        // Preserve the original failure as the useful diagnostic.
                    }
                }
            }

            LOG.log(System.Logger.Level.ERROR,
                    "Phase 1 resource lifetime probe failed; Minecraft will continue for diagnosis.", e);
        }
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.RETIRED || probeBuffer == null) {
            return;
        }

        if (!probeBuffer.isClosed()) {
            return;
        }

        releasedFrame = frameSerial;
        state = State.RELEASED;
        probeBuffer = null;
        sourceData = null;

        LOG.log(System.Logger.Level.INFO,
                "Phase 1 resource lifetime probe released on frame {0} after {1} frame(s); deferred destruction is fence-gated and complete.",
                releasedFrame, releasedFrame - retiredFrame);
    }

    public State state() {
        return state;
    }

    public long retiredFrame() {
        return retiredFrame;
    }

    public long releasedFrame() {
        return releasedFrame;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) {
            return;
        }

        if (emergencyFence != null && emergencyBuffer != null) {
            try {
                if (emergencyFence.awaitCompletion(EMERGENCY_WAIT_NS)) {
                    emergencyBuffer.close();
                    emergencyFence.close();
                } else {
                    LOG.log(System.Logger.Level.WARNING,
                            "Emergency resource-lifetime cleanup timed out; leaving the in-flight resource for Minecraft device shutdown.");
                }
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Emergency resource-lifetime cleanup failed; leaving the resource for Minecraft device shutdown.");
                LOG.log(System.Logger.Level.DEBUG, "Emergency cleanup failure", e);
            }
            emergencyBuffer = null;
            emergencyFence = null;
        }

        probeBuffer = null;
        sourceData = null;
        state = State.CLOSED;
    }
}
