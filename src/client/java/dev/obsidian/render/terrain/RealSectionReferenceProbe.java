package dev.obsidian.render.terrain;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.upload.StagingUploadArena;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Phase 2 dev1 runtime oracle.
 *
 * <p>Captures one real loaded Minecraft section into primitive-only immutable
 * data, builds the deliberately simple canonical exposed-face stream twice,
 * validates deterministic equality, uploads that stream through the real
 * staging/geometry-arena path, copies it back, and verifies every record. No
 * vanilla terrain is replaced by this milestone.</p>
 */
public final class RealSectionReferenceProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/RealSectionReferenceProbe");

    private final GpuDevice device;
    private final StagingUploadArena staging;
    private final DeviceGeometryArena arena;
    private final long[] retirementHandles = new long[1];

    private State state = State.WAITING_WORLD;
    private SectionSnapshot snapshot;
    private ReferenceFaceMesh mesh;
    private GpuBuffer readback;
    private GpuFence pendingArenaFence;
    private long faceHandle = DeviceGeometryArena.INVALID_HANDLE;
    private long submittedBatchOrdinal;
    private long submittedFrame = -1L;
    private long verifiedFrame = -1L;
    private long usefulSubmissions;
    private long retirementBackpressureEvents;
    private long retirementRegistrationFailures;
    private long gpuVerifiedBytes;

    public enum State {
        WAITING_WORLD,
        SUBMITTED,
        VERIFIED,
        FAILED,
        CLOSED
    }

    public RealSectionReferenceProbe(GpuDevice device, StagingUploadArena staging, DeviceGeometryArena arena) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.staging = staging;
        this.arena = arena;
    }

    public void tryCaptureAndSubmit(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.WAITING_WORLD) {
            return;
        }

        SectionSnapshot captured;
        try {
            captured = SectionSnapshot.tryCaptureNearPlayer();
        } catch (RuntimeException e) {
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 real-section snapshot capture failed; Minecraft will continue for diagnosis.", e);
            return;
        }
        if (captured == null) {
            return;
        }

        GpuFence arenaFence = null;
        boolean batchOpen = false;
        try {
            ReferenceFaceMesh first = ReferenceFaceMesh.build(captured);
            first.validateAgainst(captured);
            ReferenceFaceMesh second = ReferenceFaceMesh.build(captured);
            second.validateAgainst(captured);
            if (!first.contentEquals(second)) {
                throw new IllegalStateException("Reference mesher is not deterministic for an immutable snapshot");
            }
            if (first.faceCount() <= 0) {
                throw new IllegalStateException("Selected real section produced no conservative exposed faces");
            }
            if (first.byteSize() > StagingUploadArenaCapacity.MAX_REFERENCE_PAYLOAD_BYTES) {
                throw new IllegalStateException("Reference face stream exceeds validated staging capacity: " + first.byteSize());
            }

            if (!staging.beginBatch()) {
                return;
            }
            batchOpen = true;

            long handle = arena.allocate(first.byteSize(), 4);
            if (handle == DeviceGeometryArena.INVALID_HANDLE) {
                throw new IllegalStateException("Device geometry arena could not fit Phase 2 reference face stream");
            }
            faceHandle = handle;
            GpuBufferSlice arenaSlice = arena.slice(handle);

            readback = device.createBuffer(
                    () -> "Obsidian Phase 2 real-section reference readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    first.byteSize());

            CommandEncoder encoder = device.createCommandEncoder();
            ByteBuffer payload = first.toByteBuffer();
            if (!staging.stageCopy(encoder, payload, arenaSlice)) {
                throw new IllegalStateException("Phase 2 reference upload hit bounded staging backpressure");
            }
            encoder.copyToBuffer(arenaSlice, readback.slice(0L, first.byteSize()));

            arenaFence = encoder.createFence();
            staging.submitBatch(encoder);
            batchOpen = false;
            submittedBatchOrdinal = staging.submittedBatches();
            usefulSubmissions++;

            snapshot = captured;
            mesh = first;
            submittedFrame = frameSerial;
            state = State.SUBMITTED;

            retirementHandles[0] = faceHandle;
            pendingArenaFence = arenaFence;
            arenaFence = null;
            tryRegisterArenaRetirement();

            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 real section captured and reference stream submitted on frame {0}: section=({1},{2},{3}), sampledCells={4}, interiorCells={5}, airCells={6}, supportedCells={7}, unsupportedCells={8}, snapshotFingerprint={9}, snapshotNs={10}, faceCount={11}, quadCount={12}, vertexCount={13}, indexCount={14}, blockedByUnsupportedFaces={15}, meshFingerprint={16}, meshNs={17}, faceBytes={18}, usefulSubmissions={19}, profilerOnlySubmissions=0, arenaUsedBytes={20}.",
                    frameSerial,
                    captured.sectionX(),
                    captured.sectionY(),
                    captured.sectionZ(),
                    captured.sampledCells(),
                    SectionSnapshot.INTERIOR_CELL_COUNT,
                    captured.interiorAirCells(),
                    captured.interiorSupportedCells(),
                    captured.interiorUnsupportedCells(),
                    Long.toUnsignedString(captured.fingerprint()),
                    captured.captureTimeNs(),
                    first.faceCount(),
                    first.quadCount(),
                    first.vertexCount(),
                    first.indexCount(),
                    first.blockedByUnsupportedFaces(),
                    Long.toUnsignedString(first.fingerprint()),
                    first.meshTimeNs(),
                    first.byteSize(),
                    usefulSubmissions,
                    arena.usedBytes());
        } catch (RuntimeException e) {
            if (batchOpen) {
                staging.abortBatch();
            }
            if (arenaFence != null) {
                try {
                    arenaFence.close();
                } catch (RuntimeException ignored) {
                    // Preserve the useful failure.
                }
            }
            cancelUnsubmittedAllocation();
            closeReadback();
            state = State.FAILED;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 reference snapshot/mesh/upload validation failed; Minecraft will continue for diagnosis.", e);
        }
    }

    public void poll(long frameSerial) {
        RenderSystem.assertOnRenderThread();
        if (state != State.SUBMITTED) {
            return;
        }
        if (pendingArenaFence != null && !tryRegisterArenaRetirement()) {
            return;
        }
        if (staging.reclaimedBatches() < submittedBatchOrdinal) {
            return;
        }
        if (arena.isAllocated(faceHandle)) {
            return;
        }

        try {
            verifyReadback();
            verifiedFrame = frameSerial;
            state = State.VERIFIED;
            closeReadback();

            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 real-section reference verified on frame {0} after {1} frame(s): section=({2},{3},{4}), snapshotFingerprint={5}, meshFingerprint={6}, faceCount={7}, gpuVerifiedBytes={8}, deterministicBuilds=2, worldReadsAfterSnapshot=0, usefulSubmissions={9}, profilerOnlySubmissions=0, arenaRetired={10}, arenaReclaimed={11}, arenaUsedBytes={12}, arenaFreeSpans={13}, arenaFragmentationPermille={14}.",
                    verifiedFrame,
                    verifiedFrame - submittedFrame,
                    snapshot.sectionX(),
                    snapshot.sectionY(),
                    snapshot.sectionZ(),
                    Long.toUnsignedString(snapshot.fingerprint()),
                    Long.toUnsignedString(mesh.fingerprint()),
                    mesh.faceCount(),
                    gpuVerifiedBytes,
                    usefulSubmissions,
                    arena.retiredAllocations(),
                    arena.reclaimedAllocations(),
                    arena.usedBytes(),
                    arena.freeSpanCount(),
                    arena.fragmentationPermille());
        } catch (RuntimeException e) {
            state = State.FAILED;
            closeReadback();
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 GPU readback of the real-section reference stream failed verification.", e);
        }
    }

    private void verifyReadback() {
        if (readback == null || mesh == null) {
            throw new IllegalStateException("Reference readback state is incomplete");
        }
        try (GpuBufferSlice.MappedView mapped = readback.map(true, false)) {
            ByteBuffer data = mapped.data().order(ByteOrder.nativeOrder());
            for (int i = 0; i < mesh.faceCount(); i++) {
                int offset = i * ReferenceFaceMesh.BYTES_PER_FACE;
                int packed = data.getInt(offset);
                int stateId = data.getInt(offset + Integer.BYTES);
                if (packed != mesh.packedFace(i) || stateId != mesh.stateId(i)) {
                    throw new IllegalStateException("Reference GPU readback mismatch at face " + i);
                }
            }
        }
        gpuVerifiedBytes = mesh.byteSize();
    }

    private boolean tryRegisterArenaRetirement() {
        if (pendingArenaFence == null) {
            return true;
        }
        try {
            if (!arena.retireBatch(pendingArenaFence, retirementHandles, 1)) {
                retirementBackpressureEvents++;
                return false;
            }
            pendingArenaFence = null;
            return true;
        } catch (RuntimeException e) {
            retirementRegistrationFailures++;
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 arena retirement registration failed after submission; preserving the timeline handle for retry.", e);
            return false;
        }
    }

    private void cancelUnsubmittedAllocation() {
        if (faceHandle != DeviceGeometryArena.INVALID_HANDLE && arena.isLive(faceHandle)) {
            try {
                arena.cancelUnsubmitted(faceHandle);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to cancel unsubmitted Phase 2 face allocation.", e);
            }
        }
        faceHandle = DeviceGeometryArena.INVALID_HANDLE;
    }

    private void closeReadback() {
        if (readback != null) {
            try {
                readback.close();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to close Phase 2 reference readback buffer.", e);
            }
            readback = null;
        }
    }

    public State state() {
        return state;
    }

    public SectionSnapshot snapshot() {
        return snapshot;
    }

    public ReferenceFaceMesh mesh() {
        return mesh;
    }

    public long usefulSubmissions() {
        return usefulSubmissions;
    }

    public long gpuVerifiedBytes() {
        return gpuVerifiedBytes;
    }

    public long retirementBackpressureEvents() {
        return retirementBackpressureEvents;
    }

    public long retirementRegistrationFailures() {
        return retirementRegistrationFailures;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (state == State.CLOSED) {
            return;
        }

        if (state == State.SUBMITTED && staging.reclaimedBatches() < submittedBatchOrdinal) {
            LOG.log(System.Logger.Level.WARNING,
                    "Phase 2 reference resources are still in flight; leaving readback/arena backing for Minecraft device shutdown rather than destroying them unsafely.");
            readback = null;
            if (pendingArenaFence != null) {
                pendingArenaFence.close();
                pendingArenaFence = null;
            }
            state = State.CLOSED;
            return;
        }

        if (pendingArenaFence != null) {
            if (tryRegisterArenaRetirement()) {
                arena.pollRetirements();
            } else {
                pendingArenaFence.close();
                pendingArenaFence = null;
            }
        }

        if (state == State.WAITING_WORLD) {
            cancelUnsubmittedAllocation();
        }
        closeReadback();
        state = State.CLOSED;
    }

    /** Keeps the dev1 staging-capacity assertion explicit and independent of implementation internals. */
    private static final class StagingUploadArenaCapacity {
        private static final int MAX_REFERENCE_PAYLOAD_BYTES = 256 * 1024;
    }
}
