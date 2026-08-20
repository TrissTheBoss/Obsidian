package dev.obsidian.render.vulkan;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
 * Dev9 GPU visibility/compaction bridge.
 *
 * <p>Four fixed candidate scene records are uploaded through the normal bounded
 * staging system. Compute evaluates their centers against a deliberately small
 * validation frustum, atomically compacts visible draw commands to the front of
 * a storage+indirect buffer, writes a visible count and leaves the unused tail
 * zeroed. Graphics remains public Blaze3D.</p>
 */
public final class VulkanVisibilityCompactor implements AutoCloseable {
    public static final int CANDIDATE_COUNT = 4;
    public static final int VISIBLE_COUNT_EXPECTED = 2;
    public static final int CANDIDATE_BYTES = 16;
    public static final int COMMAND_BYTES = 20;
    public static final int INDIRECT_BYTES = CANDIDATE_COUNT * COMMAND_BYTES;
    public static final int COUNT_OFFSET = INDIRECT_BYTES;
    public static final int OUTPUT_BYTES = INDIRECT_BYTES + Integer.BYTES;
    public static final int CANDIDATE_BUFFER_BYTES = CANDIDATE_COUNT * CANDIDATE_BYTES;

    private static final String COMPUTE_SHADER = """
            #version 450
            layout(local_size_x = 4, local_size_y = 1, local_size_z = 1) in;

            struct Candidate {
                uint firstIndex;
                float centerX;
                float centerY;
                uint reserved;
            };

            struct DrawCommand {
                uint indexCount;
                uint instanceCount;
                uint firstIndex;
                int vertexOffset;
                uint firstInstance;
            };

            layout(std430, set = 0, binding = 0) readonly buffer Candidates {
                Candidate candidates[];
            } scene;

            layout(std430, set = 0, binding = 1) buffer Output {
                DrawCommand commands[4];
                uint visibleCount;
            } outputData;

            void clearCommand(uint slot) {
                outputData.commands[slot].indexCount = 0u;
                outputData.commands[slot].instanceCount = 0u;
                outputData.commands[slot].firstIndex = 0u;
                outputData.commands[slot].vertexOffset = 0;
                outputData.commands[slot].firstInstance = 0u;
            }

            void main() {
                uint id = gl_LocalInvocationID.x;

                if (id == 0u) {
                    outputData.visibleCount = 0u;
                    clearCommand(0u);
                    clearCommand(1u);
                    clearCommand(2u);
                    clearCommand(3u);
                }

                memoryBarrierBuffer();
                barrier();

                Candidate candidate = scene.candidates[id];
                bool visible = abs(candidate.centerX) <= 0.50
                        && abs(candidate.centerY) <= 0.80;
                if (!visible) {
                    return;
                }

                uint slot = atomicAdd(outputData.visibleCount, 1u);
                outputData.commands[slot].indexCount = 3u;
                outputData.commands[slot].instanceCount = 1u;
                outputData.commands[slot].firstIndex = candidate.firstIndex;
                outputData.commands[slot].vertexOffset = 0;
                outputData.commands[slot].firstInstance = 0u;
            }
            """;

    private final VulkanDevice device;
    private final VulkanInteropBuffer candidates;
    private final VulkanInteropBuffer output;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long shaderModule;
    private final long pipeline;
    private boolean closed;

    public VulkanVisibilityCompactor(GpuDevice publicDevice) {
        GpuDeviceBackend backend = ((GpuDeviceAccessor) (Object) publicDevice).obsidian$getBackend();
        if (!(backend instanceof VulkanDevice vulkanDevice)) {
            throw new IllegalStateException("Obsidian visibility compaction requires Minecraft's Vulkan backend");
        }
        this.device = vulkanDevice;
        this.candidates = new VulkanInteropBuffer(
                device,
                CANDIDATE_BUFFER_BYTES,
                GpuBuffer.USAGE_COPY_DST,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        this.output = new VulkanInteropBuffer(
                device,
                OUTPUT_BYTES,
                GpuBuffer.USAGE_INDIRECT_PARAMETERS | GpuBuffer.USAGE_COPY_SRC,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT);

        long createdDescriptorSetLayout = 0L;
        long createdDescriptorPool = 0L;
        long createdDescriptorSet = 0L;
        long createdPipelineLayout = 0L;
        long createdShaderModule = 0L;
        long createdPipeline = 0L;

        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
                bindings.get(0)
                        .binding(0)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
                bindings.get(1)
                        .binding(1)
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
                poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(2);
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

                VkDescriptorBufferInfo.Buffer bufferInfos = VkDescriptorBufferInfo.calloc(2, stack);
                bufferInfos.get(0)
                        .buffer(candidates.vkBuffer())
                        .offset(0L)
                        .range(CANDIDATE_BUFFER_BYTES);
                bufferInfos.get(1)
                        .buffer(output.vkBuffer())
                        .offset(0L)
                        .range(OUTPUT_BYTES);

                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
                writes.get(0)
                        .sType$Default()
                        .dstSet(createdDescriptorSet)
                        .dstBinding(0)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(VkDescriptorBufferInfo.create(bufferInfos.get(0).address(), 1));
                writes.get(1)
                        .sType$Default()
                        .dstSet(createdDescriptorSet)
                        .dstBinding(1)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(VkDescriptorBufferInfo.create(bufferInfos.get(1).address(), 1));
                vkUpdateDescriptorSets(device.vkDevice(), writes, null);

                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pSetLayouts(stack.longs(createdDescriptorSetLayout));
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
            output.close();
            candidates.close();
            throw e;
        }

        descriptorSetLayout = createdDescriptorSetLayout;
        descriptorPool = createdDescriptorPool;
        descriptorSet = createdDescriptorSet;
        pipelineLayout = createdPipelineLayout;
        shaderModule = createdShaderModule;
        pipeline = createdPipeline;
    }

    /** Records upload-read ordering, one visibility dispatch and output visibility barriers. */
    public void dispatch(CommandEncoder publicEncoder) {
        if (closed) {
            throw new IllegalStateException("Visibility compactor is closed");
        }
        CommandEncoderBackend backend = ((CommandEncoderAccessor) (Object) publicEncoder).obsidian$getBackend();
        if (!(backend instanceof VulkanCommandEncoder encoder)) {
            throw new IllegalStateException("Obsidian visibility dispatch requires VulkanCommandEncoder");
        }

        VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer uploadBarrier = VkMemoryBarrier2.calloc(1, stack);
            uploadBarrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                    .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT);
            VkDependencyInfo uploadDependency = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pMemoryBarriers(uploadBarrier);
            vkCmdPipelineBarrier2KHR(commandBuffer, uploadDependency);
        }

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout,
                    0,
                    stack.longs(descriptorSet),
                    null);
        }
        vkCmdDispatch(commandBuffer, 1, 1, 1);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer outputBarrier = VkMemoryBarrier2.calloc(1, stack);
            outputBarrier.get(0)
                    .sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT | VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                    .dstAccessMask(VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT | VK_ACCESS_2_TRANSFER_READ_BIT);
            VkDependencyInfo outputDependency = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pMemoryBarriers(outputBarrier);
            vkCmdPipelineBarrier2KHR(commandBuffer, outputDependency);
        }

        requireSuccess(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer(visibility)");
        encoder.execute(commandBuffer);
    }

    public GpuBuffer candidateBuffer() {
        return candidates;
    }

    public GpuBufferSlice indirectSlice() {
        return output.slice(0L, INDIRECT_BYTES);
    }

    public GpuBufferSlice outputSlice() {
        return output.slice(0L, OUTPUT_BYTES);
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
        output.close();
        candidates.close();
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
                    "obsidian_visibility_compact.comp",
                    "main",
                    0L);
            if (result == 0L) {
                throw new IllegalStateException("shaderc_compile_into_spv returned null");
            }
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != shaderc_compilation_status_success) {
                throw new IllegalStateException(
                        "Visibility compute shader compilation failed: " + Shaderc.shaderc_result_get_error_message(result));
            }
            ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
            if (spirv == null || !spirv.hasRemaining()) {
                throw new IllegalStateException("Visibility compute shader produced no SPIR-V");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default()
                        .pCode(spirv);
                LongBuffer pModule = stack.callocLong(1);
                requireSuccess(vkCreateShaderModule(device.vkDevice(), info, null, pModule),
                        "vkCreateShaderModule(visibility)");
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
