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
 * P4.1 scalable shadow visibility producer.
 *
 * <p>Graphics does not consume this output. The class exists only to prove
 * large-scene GPU frustum classification/identity compaction while preserving
 * the promoted P3.10 production renderer as the visual authority.</p>
 */
public final class VulkanLargeSceneVisibilityProbe implements AutoCloseable {
    public static final int CANDIDATE_BYTES = 16;
    public static final int WORKGROUP_SIZE = 128;
    public static final int PUSH_CONSTANT_BYTES = 128;

    private static final String COMPUTE_SHADER = """
            #version 450
            layout(local_size_x = 128, local_size_y = 1, local_size_z = 1) in;

            struct Candidate {
                ivec4 sectionAndIdentity;
            };

            layout(std430, set = 0, binding = 0) readonly buffer Candidates {
                Candidate candidates[];
            } scene;

            layout(std430, set = 0, binding = 1) buffer Output {
                uint words[];
            } outputData;

            layout(push_constant) uniform Push {
                vec4 planes[6];
                ivec4 cameraSectionAndCount;
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
                uint packedIndex = gl_GlobalInvocationID.x;
                uint candidateCount = uint(pc.cameraSectionAndCount.w);
                if (packedIndex >= candidateCount) return;

                ivec4 data = scene.candidates[packedIndex].sectionAndIdentity;
                ivec3 sectionDelta = data.xyz - pc.cameraSectionAndCount.xyz;
                vec3 minP = vec3(sectionDelta) * 16.0 - pc.cameraLocalAndEpsilon.xyz;
                vec3 maxP = minP + vec3(16.0);
                float epsilon = pc.cameraLocalAndEpsilon.w;

                for (int i = 0; i < 6; i++) {
                    if (maxAabbDistance(pc.planes[i], minP, maxP) < -epsilon) {
                        return;
                    }
                }

                uint visibleSlot = atomicAdd(outputData.words[0], 1u);
                outputData.words[1u + visibleSlot] = uint(data.w);
            }
            """;

    private final VulkanDevice device;
    private final int capacity;
    private final VulkanInteropBuffer candidates;
    private final VulkanInteropBuffer output;
    private final long descriptorSetLayout;
    private final long descriptorPool;
    private final long descriptorSet;
    private final long pipelineLayout;
    private final long shaderModule;
    private final long pipeline;
    private boolean closed;

    public VulkanLargeSceneVisibilityProbe(GpuDevice publicDevice, int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Visibility capacity must be positive");
        GpuDeviceBackend backend = ((GpuDeviceAccessor) (Object) publicDevice).obsidian$getBackend();
        if (!(backend instanceof VulkanDevice vulkanDevice)) {
            throw new IllegalStateException("P4.1 visibility requires Minecraft's Vulkan backend");
        }
        this.device = vulkanDevice;
        this.capacity = capacity;
        long candidateBytes = Math.multiplyExact((long) capacity, CANDIDATE_BYTES);
        long outputBytes = Math.multiplyExact((long) capacity + 1L, Integer.BYTES);
        this.candidates = new VulkanInteropBuffer(
                device,
                candidateBytes,
                GpuBuffer.USAGE_COPY_DST,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        this.output = new VulkanInteropBuffer(
                device,
                outputBytes,
                GpuBuffer.USAGE_COPY_SRC,
                VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT);

        long createdDescriptorSetLayout = 0L;
        long createdDescriptorPool = 0L;
        long createdDescriptorSet = 0L;
        long createdPipelineLayout = 0L;
        long createdShaderModule = 0L;
        long createdPipeline = 0L;

        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
                bindings.get(0).binding(0).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
                bindings.get(1).binding(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
                VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType$Default().pBindings(bindings);
                LongBuffer pLayout = stack.callocLong(1);
                requireSuccess(vkCreateDescriptorSetLayout(device.vkDevice(), layoutInfo, null, pLayout),
                        "vkCreateDescriptorSetLayout(P4.1)");
                createdDescriptorSetLayout = pLayout.get(0);

                VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
                poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(2);
                VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                        .sType$Default().maxSets(1).pPoolSizes(poolSizes);
                LongBuffer pPool = stack.callocLong(1);
                requireSuccess(vkCreateDescriptorPool(device.vkDevice(), poolInfo, null, pPool),
                        "vkCreateDescriptorPool(P4.1)");
                createdDescriptorPool = pPool.get(0);

                VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default().descriptorPool(createdDescriptorPool)
                        .pSetLayouts(stack.longs(createdDescriptorSetLayout));
                LongBuffer pSet = stack.callocLong(1);
                requireSuccess(vkAllocateDescriptorSets(device.vkDevice(), setInfo, pSet),
                        "vkAllocateDescriptorSets(P4.1)");
                createdDescriptorSet = pSet.get(0);

                VkDescriptorBufferInfo.Buffer infos = VkDescriptorBufferInfo.calloc(2, stack);
                infos.get(0).buffer(candidates.vkBuffer()).offset(0L).range(candidateBytes);
                infos.get(1).buffer(output.vkBuffer()).offset(0L).range(outputBytes);
                VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(2, stack);
                writes.get(0).sType$Default().dstSet(createdDescriptorSet).dstBinding(0)
                        .descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(VkDescriptorBufferInfo.create(infos.get(0).address(), 1));
                writes.get(1).sType$Default().dstSet(createdDescriptorSet).dstBinding(1)
                        .descriptorCount(1).descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(VkDescriptorBufferInfo.create(infos.get(1).address(), 1));
                vkUpdateDescriptorSets(device.vkDevice(), writes, null);

                VkPushConstantRange.Buffer pushRange = VkPushConstantRange.calloc(1, stack);
                pushRange.get(0).stageFlags(VK_SHADER_STAGE_COMPUTE_BIT).offset(0).size(PUSH_CONSTANT_BYTES);
                VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                        .sType$Default().pSetLayouts(stack.longs(createdDescriptorSetLayout))
                        .pPushConstantRanges(pushRange);
                LongBuffer pPipelineLayout = stack.callocLong(1);
                requireSuccess(vkCreatePipelineLayout(device.vkDevice(), pipelineLayoutInfo, null, pPipelineLayout),
                        "vkCreatePipelineLayout(P4.1)");
                createdPipelineLayout = pPipelineLayout.get(0);
            }

            createdShaderModule = compileShaderModule(COMPUTE_SHADER);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType$Default().stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(createdShaderModule).pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer infos = VkComputePipelineCreateInfo.calloc(1, stack);
                infos.get(0).sType$Default().stage(stage).layout(createdPipelineLayout);
                LongBuffer pPipeline = stack.callocLong(1);
                requireSuccess(vkCreateComputePipelines(device.vkDevice(), VK_NULL_HANDLE, infos, null, pPipeline),
                        "vkCreateComputePipelines(P4.1)");
                createdPipeline = pPipeline.get(0);
            }
        } catch (RuntimeException e) {
            destroyPartial(createdPipeline, createdShaderModule, createdPipelineLayout,
                    createdDescriptorPool, createdDescriptorSetLayout);
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

    public void dispatch(
            CommandEncoder publicEncoder,
            int candidateCount,
            int cameraSectionX,
            int cameraSectionY,
            int cameraSectionZ,
            float cameraLocalX,
            float cameraLocalY,
            float cameraLocalZ,
            float epsilon,
            float[] planes) {
        if (closed) throw new IllegalStateException("P4.1 visibility probe is closed");
        if (candidateCount < 0 || candidateCount > capacity) {
            throw new IllegalArgumentException("P4.1 candidate count exceeds capacity");
        }
        if (planes == null || planes.length != 24) {
            throw new IllegalArgumentException("P4.1 requires exactly six vec4 frustum planes");
        }
        CommandEncoderBackend backend = ((CommandEncoderAccessor) (Object) publicEncoder).obsidian$getBackend();
        if (!(backend instanceof VulkanCommandEncoder encoder)) {
            throw new IllegalStateException("P4.1 visibility requires VulkanCommandEncoder");
        }

        VkCommandBuffer commandBuffer = encoder.allocateAndBeginTransientCommandBuffer();
        vkCmdFillBuffer(commandBuffer, output.vkBuffer(), 0L, Integer.BYTES, 0);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryBarrier2.Buffer transferToCompute = VkMemoryBarrier2.calloc(1, stack);
            transferToCompute.get(0).sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_TRANSFER_BIT)
                    .srcAccessMask(VK_ACCESS_2_TRANSFER_WRITE_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT);
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
            push.putInt(108, candidateCount);
            push.putFloat(112, cameraLocalX);
            push.putFloat(116, cameraLocalY);
            push.putFloat(120, cameraLocalZ);
            push.putFloat(124, epsilon);
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT, 0, push);
        }

        if (candidateCount > 0) {
            int groups = (candidateCount + WORKGROUP_SIZE - 1) / WORKGROUP_SIZE;
            vkCmdDispatch(commandBuffer, groups, 1, 1);
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

        requireSuccess(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer(P4.1 visibility)");
        encoder.execute(commandBuffer);
    }

    public int capacity() { return capacity; }
    public long candidateBytes() { return candidates.size(); }
    public long outputBytes() { return output.size(); }
    public GpuBuffer candidateBuffer() { return candidates; }
    public GpuBufferSlice outputSlice() { return output.slice(0L, output.size()); }

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
        candidates.close();
    }

    private long compileShaderModule(String source) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == 0L) throw new IllegalStateException("shaderc_compiler_initialize failed");
        long result = 0L;
        try {
            result = Shaderc.shaderc_compile_into_spv(compiler, source, shaderc_compute_shader,
                    "obsidian_p4_large_scene_visibility.comp", "main", 0L);
            if (result == 0L) throw new IllegalStateException("shaderc_compile_into_spv returned null");
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if (status != shaderc_compilation_status_success) {
                throw new IllegalStateException("P4.1 compute shader compilation failed: "
                        + Shaderc.shaderc_result_get_error_message(result));
            }
            ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
            if (spirv == null || !spirv.hasRemaining()) {
                throw new IllegalStateException("P4.1 compute shader produced no SPIR-V");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                        .sType$Default().pCode(spirv);
                LongBuffer pModule = stack.callocLong(1);
                requireSuccess(vkCreateShaderModule(device.vkDevice(), info, null, pModule),
                        "vkCreateShaderModule(P4.1)");
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
