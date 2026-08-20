package dev.obsidian.render.resource;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Render-thread queue for GPU resources that may only be destroyed after a
 * fence associated with their last use has completed.
 *
 * <p>Routine polling uses a zero-nanosecond fence timeout and therefore never
 * intentionally waits for the GPU. The backing arrays grow only when capacity
 * is exhausted; polling and steady-state retirement are allocation-free.</p>
 *
 * <p>This queue currently assumes entries are retired from one ordered
 * submission domain. If Obsidian later owns multiple Vulkan queues, each
 * completion domain must use a separate queue or a more general completion
 * model.</p>
 */
public final class DeferredReleaseQueue implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/DeferredRelease");

    private static final int INITIAL_CAPACITY = 64;
    private static final int DEFAULT_POLL_BUDGET = 64;
    private static final long SHUTDOWN_WAIT_NS = 2_000_000_000L;

    private AutoCloseable[] resources = new AutoCloseable[INITIAL_CAPACITY];
    private GpuFence[] fences = new GpuFence[INITIAL_CAPACITY];
    private long[] serials = new long[INITIAL_CAPACITY];

    private int head;
    private int size;
    private long retiredCount;
    private long releasedCount;
    private boolean closed;

    public void retire(AutoCloseable resource, GpuFence fence, long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            throw new IllegalStateException("Deferred release queue is closed");
        }
        if (resource == null || fence == null) {
            throw new NullPointerException("resource and fence are required");
        }

        ensureCapacity();
        int tail = head + size;
        if (tail >= resources.length) {
            tail -= resources.length;
        }

        resources[tail] = resource;
        fences[tail] = fence;
        serials[tail] = frameSerial;
        size++;
        retiredCount++;
    }

    public int poll() {
        return poll(DEFAULT_POLL_BUDGET);
    }

    public int poll(int budget) {
        RenderSystem.assertOnRenderThread();
        if (closed || size == 0 || budget <= 0) {
            return 0;
        }

        int released = 0;
        while (size > 0 && released < budget) {
            GpuFence fence = fences[head];
            boolean complete;
            try {
                complete = fence.awaitCompletion(0L);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR,
                        "Fence polling failed for resource retired on frame {0}; leaving it queued.",
                        serials[head]);
                LOG.log(System.Logger.Level.DEBUG, "Fence polling failure", e);
                break;
            }

            if (!complete) {
                break;
            }

            releaseHead();
            released++;
        }
        return released;
    }

    public int pendingCount() {
        return size;
    }

    public long retiredCount() {
        return retiredCount;
    }

    public long releasedCount() {
        return releasedCount;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        long deadline = System.nanoTime() + SHUTDOWN_WAIT_NS;
        while (size > 0) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                LOG.log(System.Logger.Level.WARNING,
                        "Shutdown deadline reached with {0} GPU retirement(s) still pending; leaving them for device shutdown rather than destroying in-flight resources.",
                        size);
                abandonPending();
                return;
            }

            GpuFence fence = fences[head];
            boolean complete;
            try {
                complete = fence.awaitCompletion(remaining);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Fence wait failed during shutdown with {0} retirement(s) pending; leaving them for device shutdown.",
                        size);
                LOG.log(System.Logger.Level.DEBUG, "Shutdown fence failure", e);
                abandonPending();
                return;
            }

            if (!complete) {
                LOG.log(System.Logger.Level.WARNING,
                        "GPU retirement did not complete within the shutdown budget; leaving {0} resource(s) for device shutdown.",
                        size);
                abandonPending();
                return;
            }

            releaseHead();
        }
    }

    private void releaseHead() {
        AutoCloseable resource = resources[head];
        GpuFence fence = fences[head];
        long serial = serials[head];

        resources[head] = null;
        fences[head] = null;
        serials[head] = 0L;
        head++;
        if (head == resources.length) {
            head = 0;
        }
        size--;

        try {
            resource.close();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR,
                    "Failed to close GPU resource retired on frame {0}.", serial);
            LOG.log(System.Logger.Level.DEBUG, "GPU resource close failure", e);
        }

        try {
            fence.close();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.ERROR,
                    "Failed to close completion fence for resource retired on frame {0}.", serial);
            LOG.log(System.Logger.Level.DEBUG, "GPU fence close failure", e);
        }

        releasedCount++;
    }

    private void ensureCapacity() {
        if (size < resources.length) {
            return;
        }

        int oldCapacity = resources.length;
        int newCapacity = oldCapacity << 1;
        AutoCloseable[] newResources = new AutoCloseable[newCapacity];
        GpuFence[] newFences = new GpuFence[newCapacity];
        long[] newSerials = new long[newCapacity];

        for (int i = 0; i < size; i++) {
            int source = head + i;
            if (source >= oldCapacity) {
                source -= oldCapacity;
            }
            newResources[i] = resources[source];
            newFences[i] = fences[source];
            newSerials[i] = serials[source];
        }

        resources = newResources;
        fences = newFences;
        serials = newSerials;
        head = 0;

        LOG.log(System.Logger.Level.DEBUG,
                "Expanded deferred release queue from {0} to {1} entries.",
                oldCapacity, newCapacity);
    }

    private void abandonPending() {
        for (int i = 0; i < size; i++) {
            int index = head + i;
            if (index >= resources.length) {
                index -= resources.length;
            }
            resources[index] = null;
            fences[index] = null;
            serials[index] = 0L;
        }
        head = 0;
        size = 0;
    }
}
