package dev.obsidian.render.frame;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.memory.DeviceGeometryArena;
import dev.obsidian.render.resource.DeferredReleaseQueue;
import dev.obsidian.render.terrain.RealSectionReferenceProbe;
import dev.obsidian.render.terrain.ReferenceFaceMesh;
import dev.obsidian.render.terrain.SectionSnapshot;
import dev.obsidian.render.upload.StagingUploadArena;

/** Render-thread lifecycle root for the active Obsidian milestone. */
public final class FrameCoordinator implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/FrameCoordinator");
    private static final int VALIDATION_STAGING_BYTES = 256 * 1024;
    private static final int VALIDATION_DEVICE_ARENA_BYTES = 512 * 1024;

    private final FrameTimings cpuFrameTimings = new FrameTimings();
    private final FrameContextRing frameContexts = new FrameContextRing();
    private final DeferredReleaseQueue deferredReleases = new DeferredReleaseQueue();
    private final StagingUploadArena stagingUploads;
    private final DeviceGeometryArena deviceArena;
    private final RealSectionReferenceProbe sectionProbe;

    private FrameContext activeFrame;
    private long frameIndex;
    private boolean firstFrameLogged;
    private boolean closed;

    public FrameCoordinator(GpuDevice device) {
        StagingUploadArena staging = null;
        DeviceGeometryArena arena = null;
        RealSectionReferenceProbe probe = null;
        try {
            staging = new StagingUploadArena(
                    device,
                    () -> "Obsidian Phase 2 bounded staging ring",
                    VALIDATION_STAGING_BYTES);
            arena = new DeviceGeometryArena(
                    device,
                    () -> "Obsidian Phase 2 device geometry arena",
                    VALIDATION_DEVICE_ARENA_BYTES);
            probe = new RealSectionReferenceProbe(device, staging, arena);
        } catch (RuntimeException e) {
            if (probe != null) {
                try {
                    probe.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure as the useful diagnostic.
                }
            }
            if (arena != null) {
                try {
                    arena.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure as the useful diagnostic.
                }
            }
            if (staging != null) {
                try {
                    staging.close();
                } catch (RuntimeException ignored) {
                    // Preserve the creation failure as the useful diagnostic.
                }
            }
            LOG.log(System.Logger.Level.ERROR,
                    "Phase 2 real-section reference initialization failed; Minecraft will continue for diagnosis.", e);
        }
        stagingUploads = staging;
        deviceArena = arena;
        sectionProbe = probe;
    }

    public void beginFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }

        frameIndex++;
        activeFrame = frameContexts.begin(frameIndex, System.nanoTime());

        if (!firstFrameLogged) {
            firstFrameLogged = true;
            LOG.log(System.Logger.Level.INFO,
                    "Phase 2 frame coordinator active. contextSlots={0}, CPU timing ring capacity={1}, stagingCapacity={2}, deviceArenaCapacity={3}; dev1 waits for a real loaded section, captures an immutable 18^3 snapshot, builds the simple reference face oracle, and validates it through the existing GPU arena. Vanilla terrain remains active.",
                    frameContexts.size(),
                    cpuFrameTimings.capacity(),
                    stagingUploads == null ? 0 : stagingUploads.capacityBytes(),
                    deviceArena == null ? 0L : deviceArena.capacityBytes());
        }

        if (sectionProbe != null) {
            sectionProbe.tryCaptureAndSubmit(frameIndex);
        }
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }

        FrameContext context = activeFrame;
        activeFrame = null;
        if (context != null) {
            long duration = context.finish(System.nanoTime());
            if (duration > 0L) {
                cpuFrameTimings.record(duration);
            }
        }

        deferredReleases.poll();
        if (stagingUploads != null) {
            stagingUploads.pollReclaims();
        }
        if (deviceArena != null) {
            deviceArena.pollRetirements();
        }
        if (sectionProbe != null) {
            sectionProbe.poll(frameIndex);
        }
    }

    public long frameIndex() {
        return frameIndex;
    }

    public long latestCpuFrameTimeNs() {
        return cpuFrameTimings.latestNs();
    }

    public FrameTimings cpuFrameTimings() {
        return cpuFrameTimings;
    }

    public int pendingRetirements() {
        return deferredReleases.pendingCount();
    }

    public int pendingUploadBatches() {
        return stagingUploads == null ? 0 : stagingUploads.pendingBatches();
    }

    public int pendingArenaRetirementBatches() {
        return deviceArena == null ? 0 : deviceArena.pendingRetirementBatches();
    }

    public RealSectionReferenceProbe.State sectionProbeState() {
        return sectionProbe == null ? RealSectionReferenceProbe.State.FAILED : sectionProbe.state();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) {
            return;
        }
        closed = true;

        RealSectionReferenceProbe.State sectionStateBeforeClose =
                sectionProbe == null ? RealSectionReferenceProbe.State.FAILED : sectionProbe.state();
        SectionSnapshot snapshot = sectionProbe == null ? null : sectionProbe.snapshot();
        ReferenceFaceMesh mesh = sectionProbe == null ? null : sectionProbe.mesh();

        // The probe owns temporary readback/completion handles. Let it relinquish
        // those before the shared staging/arena owners perform their bounded shutdown.
        if (sectionProbe != null) {
            sectionProbe.close();
        }
        if (stagingUploads != null) {
            stagingUploads.close();
        }
        if (deviceArena != null) {
            deviceArena.close();
        }
        deferredReleases.close();

        LOG.log(System.Logger.Level.INFO,
                "Phase 2 frame coordinator closed after {0} frame(s): sectionReferenceResult={1}, section=({2},{3},{4}), sampledCells={5}, interiorAir={6}, interiorSupported={7}, interiorUnsupported={8}, snapshotFingerprint={9}, snapshotNs={10}, faceCount={11}, blockedByUnsupportedFaces={12}, meshFingerprint={13}, meshNs={14}, referenceBytes={15}, gpuVerifiedBytes={16}, usefulSubmissions={17}, profilerOnlySubmissions=0, stagingSubmittedBytes={18}, stagingReclaimedBytes={19}, stagingHighWater={20}, stagingBackpressureEvents={21}, pendingUploadBatches={22}, arenaUsedBytes={23}, arenaHighWater={24}, arenaAllocations={25}, arenaAllocationFailures={26}, arenaRetired={27}, arenaReclaimed={28}, arenaRetirementBackpressureEvents={29}, arenaStaleHandleRejections={30}, arenaFreeSpans={31}, arenaLargestFree={32}, arenaFragmentationPermille={33}, pendingArenaRetirementBatches={34}, retiredResources={35}, releasedResources={36}, pendingRetirements={37}.",
                frameIndex,
                sectionStateBeforeClose,
                snapshot == null ? 0 : snapshot.sectionX(),
                snapshot == null ? 0 : snapshot.sectionY(),
                snapshot == null ? 0 : snapshot.sectionZ(),
                snapshot == null ? 0 : snapshot.sampledCells(),
                snapshot == null ? 0 : snapshot.interiorAirCells(),
                snapshot == null ? 0 : snapshot.interiorSupportedCells(),
                snapshot == null ? 0 : snapshot.interiorUnsupportedCells(),
                snapshot == null ? "none" : Long.toUnsignedString(snapshot.fingerprint()),
                snapshot == null ? 0L : snapshot.captureTimeNs(),
                mesh == null ? 0 : mesh.faceCount(),
                mesh == null ? 0 : mesh.blockedByUnsupportedFaces(),
                mesh == null ? "none" : Long.toUnsignedString(mesh.fingerprint()),
                mesh == null ? 0L : mesh.meshTimeNs(),
                mesh == null ? 0 : mesh.byteSize(),
                sectionProbe == null ? 0L : sectionProbe.gpuVerifiedBytes(),
                sectionProbe == null ? 0L : sectionProbe.usefulSubmissions(),
                stagingUploads == null ? 0L : stagingUploads.submittedBytes(),
                stagingUploads == null ? 0L : stagingUploads.reclaimedBytes(),
                stagingUploads == null ? 0L : stagingUploads.highWaterBytes(),
                stagingUploads == null ? 0L : stagingUploads.backpressureEvents(),
                stagingUploads == null ? 0 : stagingUploads.pendingBatches(),
                deviceArena == null ? 0L : deviceArena.usedBytes(),
                deviceArena == null ? 0L : deviceArena.highWaterBytes(),
                deviceArena == null ? 0L : deviceArena.successfulAllocations(),
                deviceArena == null ? 0L : deviceArena.allocationFailures(),
                deviceArena == null ? 0L : deviceArena.retiredAllocations(),
                deviceArena == null ? 0L : deviceArena.reclaimedAllocations(),
                deviceArena == null ? 0L : deviceArena.retirementBackpressureEvents(),
                deviceArena == null ? 0L : deviceArena.staleHandleRejections(),
                deviceArena == null ? 0 : deviceArena.freeSpanCount(),
                deviceArena == null ? 0L : deviceArena.largestFreeBlockBytes(),
                deviceArena == null ? 0 : deviceArena.fragmentationPermille(),
                deviceArena == null ? 0 : deviceArena.pendingRetirementBatches(),
                deferredReleases.retiredCount(),
                deferredReleases.releasedCount(),
                deferredReleases.pendingCount());
    }
}
