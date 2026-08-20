package dev.obsidian.render.graph;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.obsidian.render.upload.StagingUploadArena;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One Obsidian-owned useful command stream with graph ordering and timestamps.
 *
 * <p>The stream submits through the bounded staging batch owner so uploads,
 * render passes, copies, timestamp writes, and completion tracking can share
 * one deliberate GPU submission. Profiling never creates a submission of its
 * own.</p>
 */
public final class FrameGraphCommandStream {
    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final FixedFrameGraph graph;
    private final GpuTimestampProfiler profiler;

    private CommandEncoder encoder;
    private boolean recording;
    private long submissionCount;
    private long beginBackpressureCount;
    private long submittedBatchOrdinal;

    public FrameGraphCommandStream(
            GpuDevice device,
            StagingUploadArena staging,
            FixedFrameGraph graph,
            GpuTimestampProfiler profiler) {
        this.device = device;
        this.staging = staging;
        this.graph = graph;
        this.profiler = profiler;
    }

    /** Returns false rather than waiting when staging batch metadata is full. */
    public boolean begin() {
        RenderSystem.assertOnRenderThread();
        if (recording) {
            throw new IllegalStateException("Frame-graph command stream is already recording");
        }
        if (!staging.beginBatch()) {
            beginBackpressureCount++;
            return false;
        }
        try {
            encoder = device.createCommandEncoder();
            graph.beginExecution();
            recording = true;
            return true;
        } catch (RuntimeException e) {
            staging.abortBatch();
            encoder = null;
            graph.abortExecution();
            throw e;
        }
    }

    public void beginPass(int passIndex) {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        graph.beginPass(passIndex);
        profiler.writePassStart(encoder, passIndex);
    }

    public void endPass(int passIndex) {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        profiler.writePassEnd(encoder, passIndex);
        graph.endPass(passIndex);
    }

    public boolean stageCopy(ByteBuffer source, GpuBuffer destination, long destinationOffset) {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        return staging.stageCopy(encoder, source, destination, destinationOffset);
    }

    public boolean stageCopy(ByteBuffer source, GpuBufferSlice destination) {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        return staging.stageCopy(encoder, source, destination);
    }

    public void copy(GpuBufferSlice source, GpuBufferSlice destination) {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        encoder.copyToBuffer(source, destination);
    }

    public RenderPass createRenderPass(
            Supplier<String> label,
            GpuTextureView colorAttachment,
            Optional<Vector4fc> clearColor) {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        return encoder.createRenderPass(label, colorAttachment, clearColor);
    }

    public void copyTextureToBuffer(
            GpuTexture source,
            GpuBuffer destination,
            long destinationOffset,
            Runnable completionCallback,
            int mipLevel) {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        encoder.copyTextureToBuffer(source, destination, destinationOffset, completionCallback, mipLevel);
    }

    /**
     * Returns the currently recording public encoder only for an isolated,
     * evidence-justified backend interop seam.
     *
     * <p>Minecraft 26.2 exposes no public compute pipeline/dispatch/storage-buffer
     * API, so dev8 needs to insert one Vulkan compute command buffer into this
     * same owned submission. Callers must not submit, end, replace, or retain the
     * encoder and must return control before the next graph operation.</p>
     */
    public CommandEncoder backendInteropEncoder() {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        return encoder;
    }

    /**
     * Creates another lightweight completion handle for the submission being recorded.
     *
     * <p>Exact Minecraft 26.2 Vulkan inspection shows each handle captures the
     * encoder timeline submit index; it does not allocate a native VkFence.
     * Multiple handles created before one submit therefore observe the same GPU
     * completion point and may be owned independently by staging/arena lifetime code.</p>
     */
    public GpuFence createCompletionFence() {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        return encoder.createFence();
    }

    /**
     * Submits exactly once through the staging arena, which owns its own
     * completion handle and staging-range reclamation.
     *
     * <p>All fallible software bookkeeping is completed before queue submission
     * so a post-submit exception cannot incorrectly route the caller through a
     * cleanup path that assumes resources are no longer in flight.</p>
     */
    public void submit() {
        RenderSystem.assertOnRenderThread();
        ensureRecording();
        try {
            graph.endExecution();
            profiler.markSubmitted(graph.passCount());
            staging.submitBatch(encoder);
            submissionCount++;
            submittedBatchOrdinal = staging.submittedBatches();
            recording = false;
            encoder = null;
        } catch (RuntimeException e) {
            if (recording) {
                staging.abortBatch();
                graph.abortExecution();
                recording = false;
                encoder = null;
            }
            throw e;
        }
    }

    public void abort() {
        RenderSystem.assertOnRenderThread();
        if (!recording) {
            return;
        }
        staging.abortBatch();
        graph.abortExecution();
        encoder = null;
        recording = false;
    }

    /** True only after the same useful submission's staging completion handle resolved. */
    public boolean isSubmissionComplete() {
        return submittedBatchOrdinal != 0L
                && staging.reclaimedBatches() >= submittedBatchOrdinal;
    }

    /** Polls timestamp availability only after the useful submission completed. */
    public boolean pollProfiler() {
        RenderSystem.assertOnRenderThread();
        if (!isSubmissionComplete()) {
            return false;
        }
        return profiler.poll();
    }

    public long submissionCount() {
        return submissionCount;
    }

    public long beginBackpressureCount() {
        return beginBackpressureCount;
    }

    public long submittedBatchOrdinal() {
        return submittedBatchOrdinal;
    }

    private void ensureRecording() {
        if (!recording || encoder == null) {
            throw new IllegalStateException("Frame-graph command stream is not recording");
        }
    }
}
