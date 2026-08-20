package dev.obsidian.render.vulkan;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import dev.obsidian.mixin.CommandEncoderAccessor;
import dev.obsidian.mixin.GpuDeviceAccessor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkMemoryBarrier2;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compute_shader;
import static org.lwjgl.vulkan.KHRSynchronization2.vkCmdPipelineBarrier2KHR;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Minimal Vulkan seam used because Minecraft 26.2 public Blaze3D exposes no
 * compute pipeline, dispatch, or storage-buffer API.
 *
 * <p>It deliberately reuses Minecraft's VulkanDevice, graphics command pool and
 * VulkanCommandEncoder submission. Graphics drawing remains on public Blaze3D.</p>
 */
public final class VulkanComputeIndirectGenerator implements AutoCloseable {
    public static final int COMMAND_BYTES = 20;
    public static final int COMMAND_COUNT = 2;
    public static final int BUFFER_BYTES = COMMAND_BYTES * COMMAND_COUNT;

    private static final String COMPUTE_SHADER = """
            #version 450
            layout(local_size_x = 1, local_size_y = 1, local_size_z = 1) in;

            struct DrawCommand {
                uint indexCount;
                uint instanceCount;
                uint firstIndex;
                int vertexOffset;
                uint firstInstance;
            };

            layout(std430, set = 0, binding = 0) buffer Commands {
                DrawCommand commands[];
            };

            layout(push_constant) uniform Params {
                uint firstIndex;
            } params;

            void main() {
                commands[0].indexCount = 3u;
                commands[0].instanceCount = 1u;
                commands[0].firstIndex = params.firstIndex;
                commands[0].vertexOffset = 0;
                commands[0].firstInstance = 0u;

                commands[1].indexCount = 3u;
                commands[1].instanceCount = 1u;
                commands[1].firstIndex = params.firstIndex + 3u;
                commands[1].vertexOffset = 0;
                commands[1].firstInstance = 0u;
            }
            """;

    private final VulkanDevice device;
    private final VulkanStorageIndirectBuffer commands;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long shaderModule;
    private final long pipeline;
    private boolean closed;

    public VulkanComputeIndirectGenerator(GpuDevice publicDevice) {
        GpuDeviceBackend backend = ((GpuDeviceAccessor) (Object) publicDevice).obsidian$getBackend();
        if (!(backend instanceof VulkanDevice vulkanDevice)) {
            throw new IllegalStateException("Obsidian compute-indirect seam requires Minecraft's Vulkan backend");
        }
        this.device = vulkanDevice;
        this.commands = new VulkanStorageIndirectBuffer(device, BUFFER_BYTES);

        long createdDescriptorSetLayout = 0L;
        long createdDescriptorPool = 0L;
        long createdDescriptorSet = 0L;
        long createdPipelineLayout = 0L;
        long createdShaderModule = 0L;
        long createdPipeline = 0L;
        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
                bindings.get(0)
                        .binding(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);

                VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pBindings(bindings);
                LongBuffer pLayout = stack.callocLong(1);
                requireSuccess(vkCreateDescriptorSetLayout(device.vkDevice(), layoutInfo, null, pLayout),
                        "vkCreateDescriptorSetLayout");
                createdDescriptorSetLayout = pLayout.get(0);

                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
                poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType$Default()
                        .maxSets(1)
                        .pPoolSizes(poolSizes);
                LongBuffer pPool = stack.callocLong(1);
                requireSuccess(vkCreateDescriptorPool(device.vkDevice(), poolInfo, null, pPool),
                        "vkCreateDescriptorPool");
                createdDescriptorPool = pPool.get(0);

                VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default()
                        .descriptorPool(createdDescriptorPool)
                        .pSetLayouts(stack.longs(createdDescriptorSetLayout));
                LongBuffer pSet = stack.callocLong(1);
                requireSuccess(vkAllocateDescriptorSets(device.vkDevice(), setInfo, pSet),
                        "vkAllocateDescriptorSets");
                createdDescriptorSet = pSet.get(0);

                VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(1, stack);
                bufferInfos.get(0)
                        .buffer(commands.vkBuffer())
                        .offset(0L)
                        .range(BUFFER_BYTES);
                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack);
                writes.get(0)
                        .sType$Default()
                        .dstSet(createdDescriptorSet)
                        .dstBinding(0)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(bufferInfos);
                vkUpdateDescriptorSets(device.vkDevice(), writes, null);

                VkPushConstantRange.Buffer pushRanges = VkPushConstantRange.calloc(1, stack);
                pushRanges.get(0)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                        .offset(0)
                        .size(Integer.BYTES);
                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pSetLayouts(stack.longs(createdDescriptorSetLayout))
                        .pPushConstantRanges(pushRanges);
                LongBuffer pPipelineLayout = stack.callocLong(1);
                requireSuccess(vkCreatePipelineLayout(device.vkDevice(), pipelineLayoutInfo, null, pPipelineLayout),
                        "vkCreatePipelineLayout");
                createdPipelineLayout = pPipelineLayout.get(0);
            }

            createdShaderModule = compileShaderModule(COMPUTE_SHADER);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default()
                        .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(createdShaderModule)
                        .pName(stack.UTF8("main"));

                VkComputePipelineCreateInfo.Buffer pipelineInfos = VkComputePipelineCreateInfo.calloc(1, stack);
                pipelineInfos.get(0)
                        .sType$Default()
                        .stage(stage)
                        .layout(createdPipelineLayout);
                LongBuffer pPipeline = stack.callocLong(1);
                requireSuccess(vkCreateComputePipelines(
                                device.vkDevice(),
                                VK_NULL_HANDLE,
                                pipelineInfos,
                                null,
                                pPipeline),
                        "vkCreateComputePipelines");
                createdPipeline = pPipeline.get(0);
            }
        } catch (RuntimeException e) {
            destroyPartial(
                    createdPipeline,
                    createdShaderModule,
                    createdPipelineLayout,
                    createdDescriptorPool,
                    createdDescriptorSetLayout);
            commands.close();
            throw e;
        }

        descriptorSetLayout = createdDescriptorSetLayout;
        descriptorPool = createdDescriptorPool;
        descriptorSet = createdDescriptorSet;
        pipelineLayout = createdPipelineLayout;
        shaderModule = createdShaderModule;
        pipeline = createdPipeline;
    }

    /** Records one compute dispatch and an explicit compute-write -> indirect-read Sync2 barrier. */
    public void dispatch(CommandEncoder publicEncoder, int firstIndex) {
        if (closed) {
            throw new IllegalStateException("Compute indirect generator is closed");
        }
        CommandEncoderBackend backend = ((CommandEncoderAccessor) (Object) publicEncoder).obsidian$getBackend();
        if (!(backend instanceof VulkanCommandEncoder encoder)) {
            throw new IllegalStateException("Obsidian compute-indirect dispatch requires VulkanCommandEncoder");
        }

        VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
        boolean ended = false;
        try {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                vkCmdBindDescriptorSets(
                        commandBuffer,
                        VK_PIPELINE_BIND_POINT_COMPUTE,
                        pipelineLayout,
                        0,
                        stack.longs(descriptorSet),
                        null);
                ByteBuffer push = stack.malloc(Integer.BYTES);
                push.putInt(0, firstIndex);
                vkCmdPushConstants(
                        commandBuffer,
                        pipelineLayout,
                        VK_SHADER_STAGE_COMPUTE_BIT,
                        0,
                        push);
            }
            vkCmdDispatch(commandBuffer, 1, 1, 1);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkMemoryBarrier2.Buffer barrier = VkMemoryBarrier2.calloc(1, stack);
                barrier.get(0)
                        .sType$Default()
                        .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                        .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                        .dstStageMask(VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT)
                        .dstAccessMask(VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT);
                VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                        .sType$Default()
                        .pMemoryBarriers(barrier);
                vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
            }

            requireSuccess(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer(compute)");
            ended = true;
            encoder.execute(commandBuffer);
        } catch (RuntimeException e) {
            if (!ended) {
                // Transient command memory is reclaimed with Minecraft's command pool reset.
            }
            throw e;
        }
    }

    public GpuBufferSlice indirectSlice() {
        return commands.slice(0L, BUFFER_BYTES);
    }

    public VulkanStorageIndirectBuffer buffer() {
        return commands;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        vkDestroyPipeline(device.vkDevice(), pipeline, null);
        vkDestroyShaderModule(device.vkDevice(), shaderModule, null);
        vkDestroyPipelineLayout(device.vkDevice(), pipelineLayout, null);
        vkDestroyDescriptorPool(device.vkDevice(), descriptorPool, null);
        vkDestroyDescriptorSetLayout(device.vkDevice(), descriptorSetLayout, null);
        commands.close();
    }

    private long compileShaderModule(String source) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == 0L) {
            throw new IllegalStateException("shaderc_compiler_initialize failed");
        }
        long result = 0L;
        try {
            result = Shaderc.shaderc_compile_into_spv(
                    compiler,
                    source,
                    shaderc_compute_shader,
                    "obsidian_compute_indirect.comp",
                    "main",
                    0L);
            if (result == 0L) {
                throw new IllegalStateException("shaderc_compile_into_spv returned null");
            }
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != shaderc_compilation_status_success) {
                throw new IllegalStateException(
                        "Compute shader compilation failed: " + Shaderc.shaderc_result_get_error_message(result));
            }
            ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
            if (spirv == null || !spirv.hasRemaining()) {
                throw new IllegalStateException("Compute shader compilation produced no SPIR-V");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default()
                        .pCode(spirv);
                LongBuffer pModule = stack.callocLong(1);
                requireSuccess(vkCreateShaderModule(device.vkDevice(), info, null, pModule),
                        "vkCreateShaderModule(compute)");
                return pModule.get(0);
            }
        } finally {
            if (result != 0L) {
                Shaderc.shaderc_result_release(result);
            }
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    private void destroyPartial(
            long createdPipeline,
            long createdShaderModule,
            long createdPipelineLayout,
            long createdDescriptorPool,
            long createdDescriptorSetLayout) {
        if (createdPipeline != 0L) {
            vkDestroyPipeline(device.vkDevice(), createdPipeline, null);
        }
        if (createdShaderModule != 0L) {
            vkDestroyShaderModule(device.vkDevice(), createdShaderModule, null);
        }
        if (createdPipelineLayout != 0L) {
            vkDestroyPipelineLayout(device.vkDevice(), createdPipelineLayout, null);
        }
        if (createdDescriptorPool != 0L) {
            vkDestroyDescriptorPool(device.vkDevice(), createdDescriptorPool, null);
        }
        if (createdDescriptorSetLayout != 0L) {
            vkDestroyDescriptorSetLayout(device.vkDevice(), createdDescriptorSetLayout, null);
        }
    }

    private static void requireSuccess(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result);
        }
    }
}
