package dev.obsidian.render.draw;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.function.Supplier;

/** Fixed-capacity, device-preferred storage for VkDrawIndexedIndirectCommand records. */
public final class IndexedIndirectCommandBuffer implements AutoCloseable {
    public static final int COMMAND_BYTES = 20;

    private final GpuBuffer buffer;
    private final int commandCapacity;
    private boolean closed;

    public IndexedIndirectCommandBuffer(GpuDevice device, Supplier<String> label, int commandCapacity) {
        RenderSystem.assertOnRenderThread();
        if (commandCapacity <= 0) {
            throw new IllegalArgumentException("Indirect command capacity must be positive");
        }
        this.commandCapacity = commandCapacity;
        this.buffer = device.createBuffer(
                label,
                GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_INDIRECT_PARAMETERS,
                (long) commandCapacity * COMMAND_BYTES);
    }

    public GpuBuffer buffer() {
        ensureOpen();
        return buffer;
    }

    public GpuBufferSlice slice(int commandCount) {
        ensureOpen();
        if (commandCount <= 0 || commandCount > commandCapacity) {
            throw new IllegalArgumentException("Invalid indirect command count: " + commandCount);
        }
        return buffer.slice(0L, (long) commandCount * COMMAND_BYTES);
    }

    public int commandCapacity() {
        return commandCapacity;
    }

    public long capacityBytes() {
        return (long) commandCapacity * COMMAND_BYTES;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;
        buffer.close();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Indirect command buffer is closed");
        }
    }
}
