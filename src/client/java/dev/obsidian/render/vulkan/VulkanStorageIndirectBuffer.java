package dev.obsidian.render.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

/**
 * Narrow Vulkan-only buffer for the capability public Blaze3D 26.2 cannot express:
 * one device-preferred buffer that is both compute-storage writable and indirect-readable.
 *
 * <p>The wrapper remains a {@link VulkanGpuBuffer}, so normal public
 * {@code RenderPass.drawIndexedIndirect} can consume it. It is never mapped.</p>
 */
public final class VulkanStorageIndirectBuffer extends VulkanGpuBuffer {
    private final VulkanDevice device;
    private final long allocation;
    private boolean closed;

    public VulkanStorageIndirectBuffer(VulkanDevice device, long sizeBytes) {
        this(device, sizeBytes, allocate(device, sizeBytes));
    }

    private VulkanStorageIndirectBuffer(VulkanDevice device, long sizeBytes, Allocation created) {
        super(created.buffer(), GpuBuffer.USAGE_INDIRECT_PARAMETERS, sizeBytes);
        this.device = device;
        this.allocation = created.allocation();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public GpuBufferSlice.MappedView map(long offset, long length, boolean read, boolean write) {
        throw new UnsupportedOperationException("Obsidian storage/indirect buffer is device-preferred and non-mappable");
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

    private static Allocation allocate(VulkanDevice device, long sizeBytes) {
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("Storage/indirect buffer size must be positive");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(sizeBytes)
                    .usage(VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT)
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
                throw new IllegalStateException("vmaCreateBuffer(storage+indirect) failed with VkResult " + result);
            }
            return new Allocation(pBuffer.get(0), pAllocation.get(0));
        }
    }

    private record Allocation(long buffer, long allocation) {}
}
