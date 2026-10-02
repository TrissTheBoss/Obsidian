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
 * P4.4 validation-only GPU-resident coarse-column -> fine-section classifier.
 *
 * <p>The coarse output buffer is read directly by this compute stage in the
 * same sampled submission. Graphics never consumes this output.</p>
 */
public final class VulkanGpuResidentHierarchicalFineProbe implements AutoCloseable {
    public static final int SECTION_RECORD_BYTES = 16;
    public static final int OUTPUT_HEADER_WORDS = 4;
    public static final int WORKGROUP_SIZE = 128;
    public static final int PUSH_CONSTANT_BYTES = 128;

    private static final String COMPUTE_SHADER = """
            #version 450
            layout(local_size_x = 128, local_size_y = 1, local_size_z = 1) in;

            struct SectionRecord {
                ivec4 sectionAndIdentity;
            };

            layout(std430, set = 0, binding = 0) readonly buffer CoarseOutput {
                uint words[];
            } coarseOutput;

            layout(std430, set = 0, binding = 1) readonly buffer SectionTable {
                SectionRecord records[];
            } sectionTable;

            layout(std430, set = 0, binding = 2) buffer FineOutput {
                uint words[];
            } fineOutput;

            layout(push_constant) uniform Push {
                vec4 planes[6];
                ivec4 cameraSectionAndPackedCounts;
                vec4 cameraLocalAndEpsilon;
            } pc;

            float maxAabbDistance(vec4 plane, vec3 minP, vec3 maxP) {
                vec3 support = vec3(
                    plane.x >= 0.0 ? maxP.x : minP.x,
                    plane.y >= 0.0 ? maxP.y : minP.y,
                    plane.z >= 0.0 ? maxP.z : minP.z);
                return dot(plane.xyz, support) + plane.w;
            }

            void main() {
                uint packedCounts = uint(pc.cameraSectionAndPackedCounts.w);
                uint snapshotCandidateCount = packedCounts & 0x00ffffffu;
                uint sectionCount = packedCounts >> 24;
                if (sectionCount == 0u) return;

                uint flatIndex = gl_GlobalInvocationID.x;
                uint visibleListIndex = flatIndex / sectionCount;
                uint sectionOffset = flatIndex - visibleListIndex * sectionCount;
                uint visibleColumnCount = coarseOutput.words[0];
                if (visibleListIndex >= visibleColumnCount) return;

                uint snapshotIndex = coarseOutput.words[1u + visibleListIndex];
                if (snapshotIndex >= snapshotCandidateCount) {
                    atomicAdd(fineOutput.words[2], 1u);
                    return;
                }

                uint tableIndex = snapshotIndex * sectionCount + sectionOffset;
                ivec4 data = sectionTable.records[tableIndex].sectionAndIdentity;
                if (data.w == 0) return;

                atomicAdd(fineOutput.words[0], 1u);

                ivec3 sectionDelta = data.xyz - pc.cameraSectionAndPackedCounts.xyz;
                vec3 minP = vec3(sectionDelta) * 16.0 - pc.cameraLocalAndEpsilon.xyz;
                vec3 maxP = minP + vec3(16.0);
                float epsilon = pc.cameraLocalAndEpsilon.w;

                for (int i = 0; i < 6; i++) {
                    if (maxAabbDistance(pc.planes[i], minP, maxP) < -epsilon) {
                        return;
                    }
                }

                uint visibleSlot = atomicAdd(fineOutput.words[1], 1u);
                fineOutput.words[4u + visibleSlot] = uint(data.w);
            }
            """;

    private final VulkanDevice device;
    private final int columnCapacity;
    private final int sectionCount;
    private final int sectionCapacity;
    private final VulkanInteropBuffer sectionTable;
    private final VulkanInteropBuffer output;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long shaderModule;
    private final long pipeline;
    private boolean closed;

    public VulkanGpuResidentHierarchicalFineProbe(
            GpuDevice publicDevice,
            int columnCapacity,
            int sectionCount,
            int sectionCapacity,
            long coarseOutputVkBuffer,
            long coarseOutputBytes) {
        if (columnCapacity <= 0 || sectionCount <= 0 || sectionCount > 255 || sectionCapacity <= 0) {
            throw new IllegalArgumentException("P4.4 invalid hierarchy dimensions");
        }
        long tableEntries = Math.multiplyExact((long) columnCapacity, sectionCount);
        if (tableEntries > sectionCapacity) {
            throw new IllegalArgumentException("P4.4 section table exceeds P4.1 section capacity");
        }
        GpuDeviceBackend backend = ((GpuDeviceAccessor) (Object) publicDevice).obsidian$getBackend();
        if (!(backend instanceof VulkanDevice vulkanDevice)) {
            throw new IllegalStateException("P4.4 GPU-resident hierarchy fine visibility requires Vulkan backend");
        }
        this.device = vulkanDevice;
        this.columnCapacity = columnCapacity;
        this.sectionCount = sectionCount;
        this.sectionCapacity = sectionCapacity;

        long tableBytes = Math.multiplyExact(tableEntries, SECTION_RECORD_BYTES);
        long outputBytes = Math.multiplyExact(
                (long) sectionCapacity + OUTPUT_HEADER_WORDS, Integer.BYTES);

        this.sectionTable = new VulkanInteropBuffer(
                device,
                tableBytes,
                GpuBuffer.USAGE_COPY_DST,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        this.output = new VulkanInteropBuffer(
                device,
                outputBytes,
                GpuBuffer.USAGE_COPY_SRC,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                        | VK_BUFFER_USAGE_TRANSFER_SRC_BIT
                        | VK_BUFFER_USAGE_TRANSFER_DST_BIT);

        long createdDescriptorSetLayout = 0L;
        long createdDescriptorPool = 0L;
        long createdDescriptorSet = 0L;
        long createdPipelineLayout = 0L;
        long createdShaderModule = 0L;
        long createdPipeline = 0L;

        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(3, stack);
                for (int i = 0; i < 3; i++) {
                    bindings.get(i).binding(i)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .descriptorCount(1)
                            .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
                }
                VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType$Default().pBindings(bindings);
                LongBuffer pLayout = stack.callocLong(1);
                requireSuccess(vkCreateDescriptorSetLayout(device.vkDevice(), layoutInfo, null, pLayout),
                        "vkCreateDescriptorSetLayout(P4.4)");
                createdDescriptorSetLayout = pLayout.get(0);

                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
                poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(3);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType$Default().maxSets(1).pPoolSizes(poolSizes);
                LongBuffer pPool = stack.callocLong(1);
                requireSuccess(vkCreateDescriptorPool(device.vkDevice(), poolInfo, null, pPool),
                        "vkCreateDescriptorPool(P4.4)");
                createdDescriptorPool = pPool.get(0);

                VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default().descriptorPool(createdDescriptorPool)
                        .pSetLayouts(stack.longs(createdDescriptorSetLayout));
                LongBuffer pSet = stack.callocLong(1);
                requireSuccess(vkAllocateDescriptorSets(device.vkDevice(), setInfo, pSet),
                        "vkAllocateDescriptorSets(P4.4)");
                createdDescriptorSet = pSet.get(0);

                VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(3, stack);
                infos.get(0).buffer(coarseOutputVkBuffer).offset(0L).range(coarseOutputBytes);
                infos.get(1).buffer(sectionTable.vkBuffer()).offset(0L).range(tableBytes);
                infos.get(2).buffer(output.vkBuffer()).offset(0L).range(outputBytes);
                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
                for (int i = 0; i < 3; i++) {
                    writes.get(i).sType$Default().dstSet(createdDescriptorSet).dstBinding(i)
                            .descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .pBufferInfo(VkDescriptorBufferInfo.create(infos.get(i).address(), 1));
                }
                vkUpdateDescriptorSets(device.vkDevice(), writes, null);

                VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
                pushRange.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT)
                        .offset(0).size(PUSH_CONSTANT_BYTES);
                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pSetLayouts(stack.longs(createdDescriptorSetLayout))
                        .pPushConstantRanges(pushRange);
                LongBuffer pPipelineLayout = stack.callocLong(1);
                requireSuccess(vkCreatePipelineLayout(
                                device.vkDevice(), pipelineLayoutInfo, null, pPipelineLayout),
                        "vkCreatePipelineLayout(P4.4)");
                createdPipelineLayout = pPipelineLayout.get(0);
            }

            createdShaderModule = compileShaderModule(COMPUTE_SHADER);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default()
                        .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(createdShaderModule)
                        .pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer infos = VkComputePipelineCreateInfo.calloc(1, stack);
                infos.get(0).sType$Default().stage(stage).layout(createdPipelineLayout);
                LongBuffer pPipeline = stack.callocLong(1);
                requireSuccess(vkCreateComputePipelines(
                                device.vkDevice(), VK_NULL_HANDLE, infos, null, pPipeline),
                        "vkCreateComputePipelines(P4.4)");
                createdPipeline = pPipeline.get(0);
            }
        } catch (RuntimeException e) {
            destroyPartial(createdPipeline, createdShaderModule, createdPipelineLayout,
                    createdDescriptorPool, createdDescriptorSetLayout);
            output.close();
            sectionTable.close();
            throw e;
        }

        descriptorSetLayout = createdDescriptorSetLayout;
        descriptorPool = createdDescriptorPool;
        descriptorSet = createdDescriptorSet;
        pipelineLayout = createdPipelineLayout;
        shaderModule = createdShaderModule;
        pipeline = createdPipeline;
    }

    public void dispatch(
            CommandEncoder publicEncoder,
            int snapshotCandidateCount,
            int cameraSectionX,
            int cameraSectionY,
            int cameraSectionZ,
            float cameraLocalX,
            float cameraLocalY,
            float cameraLocalZ,
            float epsilon,
            float[] planes) {
        if (closed) throw new IllegalStateException("P4.4 probe is closed");
        if (snapshotCandidateCount < 0 || snapshotCandidateCount > columnCapacity) {
            throw new IllegalArgumentException("P4.4 snapshot candidate count exceeds capacity");
        }
        if (snapshotCandidateCount > 0x00ff_ffff) {
            throw new IllegalArgumentException("P4.4 snapshot candidate count exceeds packed bound");
        }
        if (planes == null || planes.length != 24) {
            throw new IllegalArgumentException("P4.4 requires exactly six vec4 frustum planes");
        }

        CommandEncoderBackend backend = ((CommandEncoderAccessor) (Object) publicEncoder).obsidian$getBackend();
        if (!(backend instanceof VulkanCommandEncoder encoder)) {
            throw new IllegalStateException("P4.4 requires VulkanCommandEncoder");
        }

        VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer coarseToFine = VkMemoryBarrier2.calloc(1, stack);
            coarseToFine.get(0).sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT);
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                    .sType$Default().pMemoryBarriers(coarseToFine);
            vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
        }

        vkCmdFillBuffer(commandBuffer, output.vkBuffer(), 0L,
                (long) OUTPUT_HEADER_WORDS * Integer.BYTES, 0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer transferToCompute = VkMemoryBarrier2.calloc(1, stack);
            transferToCompute.get(0).sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                    .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT
                            | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                    .sType$Default().pMemoryBarriers(transferToCompute);
            vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
        }

        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE,
                    pipelineLayout, 0, stack.longs(descriptorSet), null);
            ByteBuffer push = stack.malloc(PUSH_CONSTANT_BYTES);
            int offset = 0;
            for (float plane : planes) {
                push.putFloat(offset, plane);
                offset += Float.BYTES;
            }
            push.putInt(96, cameraSectionX);
            push.putInt(100, cameraSectionY);
            push.putInt(104, cameraSectionZ);
            int packedCounts = (sectionCount << 24) | snapshotCandidateCount;
            push.putInt(108, packedCounts);
            push.putFloat(112, cameraLocalX);
            push.putFloat(116, cameraLocalY);
            push.putFloat(120, cameraLocalZ);
            push.putFloat(124, epsilon);
            vkCmdPushConstants(commandBuffer, pipelineLayout,
                    VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
        }

        long threadCount = Math.multiplyExact((long) snapshotCandidateCount, sectionCount);
        if (threadCount > 0L) {
            long groupCount = (threadCount + WORKGROUP_SIZE - 1L) / WORKGROUP_SIZE;
            if (groupCount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("P4.4 dispatch group count exceeds int bound");
            }
            vkCmdDispatch(commandBuffer, (int) groupCount, 1, 1);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer computeToTransfer = VkMemoryBarrier2.calloc(1, stack);
            computeToTransfer.get(0).sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                    .dstAccessMask(VK_ACCESS_2_TRANSFER_READ_BIT);
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                    .sType$Default().pMemoryBarriers(computeToTransfer);
            vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
        }

        requireSuccess(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer(P4.4)");
        encoder.execute(commandBuffer);
    }

    public GpuBuffer sectionTableBuffer() { return sectionTable; }
    public GpuBufferSlice outputSlice() { return output.slice(0L, output.size()); }
    public long sectionTableBytes() { return sectionTable.size(); }
    public long outputBytes() { return output.size(); }
    public int sectionCapacity() { return sectionCapacity; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        vkDestroyPipeline(device.vkDevice(), pipeline, null);
        vkDestroyShaderModule(device.vkDevice(), shaderModule, null);
        vkDestroyPipelineLayout(device.vkDevice(), pipelineLayout, null);
        vkDestroyDescriptorPool(device.vkDevice(), descriptorPool, null);
        vkDestroyDescriptorSetLayout(device.vkDevice(), descriptorSetLayout, null);
        output.close();
        sectionTable.close();
    }

    private long compileShaderModule(String source) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == 0L) throw new IllegalStateException("shaderc_compiler_initialize failed");
        long result = 0L;
        try {
            result = Shaderc.shaderc_compile_into_spv(compiler, source, shaderc_compute_shader,
                    "obsidian_p4_gpu_resident_hierarchical_fine.comp", "main", 0L);
            if (result == 0L) throw new IllegalStateException("shaderc_compile_into_spv returned null");
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != shaderc_compilation_status_success) {
                throw new IllegalStateException("P4.4 compute shader compilation failed: "
                        + Shaderc.shaderc_result_get_error_message(result));
            }
            ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
            if (spirv == null || !spirv.hasRemaining()) {
                throw new IllegalStateException("P4.4 compute shader produced no SPIR-V");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default().pCode(spirv);
                LongBuffer pModule = stack.callocLong(1);
                requireSuccess(vkCreateShaderModule(device.vkDevice(), info, null, pModule),
                        "vkCreateShaderModule(P4.4)");
                return pModule.get(0);
            }
        } finally {
            if (result != 0L) Shaderc.shaderc_result_release(result);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    private void destroyPartial(long createdPipeline, long createdShaderModule,
                                long createdPipelineLayout, long createdDescriptorPool,
                                long createdDescriptorSetLayout) {
        if (createdPipeline != 0L) vkDestroyPipeline(device.vkDevice(), createdPipeline, null);
        if (createdShaderModule != 0L) vkDestroyShaderModule(device.vkDevice(), createdShaderModule, null);
        if (createdPipelineLayout != 0L) vkDestroyPipelineLayout(device.vkDevice(), createdPipelineLayout, null);
        if (createdDescriptorPool != 0L) vkDestroyDescriptorPool(device.vkDevice(), createdDescriptorPool, null);
        if (createdDescriptorSetLayout != 0L) vkDestroyDescriptorSetLayout(device.vkDevice(), createdDescriptorSetLayout, null);
    }

    private static void requireSuccess(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result);
        }
    }
}
