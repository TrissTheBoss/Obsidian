package dev.obsidian.render.vulkan;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

/**
 * Small generic wrapper for the isolated Vulkan interop seam.
 *
 * <p>The native usage flags describe the real Vulkan consumers/producers while
 * the public usage flags let already-validated Blaze3D copy/draw operations use
 * the same buffer. The storage remains device-preferred and non-mappable.</p>
 */
public final class VulkanInteropBuffer extends VulkanGpuBuffer {
    private final VulkanDevice device;
    private final long allocation;
    private boolean closed;

    public VulkanInteropBuffer(
            VulkanDevice device,
            long sizeBytes,
            int publicUsage,
            int nativeUsage) {
        this(device, sizeBytes, publicUsage, allocate(device, sizeBytes, nativeUsage));
    }

    private VulkanInteropBuffer(
            VulkanDevice device,
            long sizeBytes,
            int publicUsage,
            Allocation created) {
        super(created.buffer(), publicUsage, sizeBytes);
        this.device = device;
        this.allocation = created.allocation();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        throw new UnsupportedOperationException("Obsidian Vulkan interop buffers are device-preferred and non-mappable");
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        destroy();
    }

    @Override
    public void destroy() {
        Vma.vmaDestroyBuffer(device.vma(), vkBuffer(), allocation);
    }

    private static Allocation allocate(VulkanDevice device, long sizeBytes, int nativeUsage) {
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("Interop buffer size must be positive");
        }
        if (nativeUsage == 0) {
            throw new IllegalArgumentException("Interop buffer native usage must be non-zero");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(sizeBytes)
                    .usage(nativeUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);

            LongBuffer pBuffer = stack.callocLong(1);
            PointerBuffer pAllocation = stack.callocPointer(1);
            int result = Vma.vmaCreateBuffer(
                    device.vma(),
                    bufferInfo,
                    allocationInfo,
                    pBuffer,
                    pAllocation,
                    null);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException("vmaCreateBuffer(interop) failed with VkResult " + result);
            }
            return new Allocation(pBuffer.get(0), pAllocation.get(0));
        }
    }

    private record Allocation(long buffer, long allocation) {}
}
