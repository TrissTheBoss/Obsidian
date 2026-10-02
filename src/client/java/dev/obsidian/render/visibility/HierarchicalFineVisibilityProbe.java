package dev.obsidian.render.visibility;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.vulkan.VulkanLargeSceneVisibilityProbe;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.nio.ByteBuffer;

/**
 * P4.3 shadow-only sampled handoff from proven coarse column visibility to
 * proven fine section visibility.
 *
 * <p>This validator deliberately consumes only completed P4.2 samples. It is
 * not a same-frame production GPU-to-GPU dependency and no graphics pass
 * consumes its output.</p>
 */
public final class HierarchicalFineVisibilityProbe implements AutoCloseable {
    private static final System.Logger LOG =
            System.getLogger("Obsidian/HierarchicalFineVisibility");

    private static final int BUILD_SECTION_BUDGET = 8_192;
    private static final long SHUTDOWN_WAIT_NS = 2_000_000_000L;
    private static final float FRUSTUM_EPSILON = 1.0e-3f;
    private static final int NO_Y = Integer.MIN_VALUE;

    private final GpuDevice device;
    private final PersistentColumnHierarchy hierarchy;
    private final PersistentSectionScene sections;
    private final VulkanLargeSceneVisibilityProbe gpu;
    private final GpuBuffer uploadBuffer;
    private final GpuBufferSlice.MappedView uploadView;
    private final ByteBuffer uploadData;
    private final GpuBuffer readbackBuffer;
    private final GpuBufferSlice.MappedView readbackView;
    private final ByteBuffer readbackData;
    private final int[] gpuVisibleColumnSlots;
    private final IntOpenHashSet cpuCoarseVisibleIds = new IntOpenHashSet();
    private final IntOpenHashSet expectedFineVisibleIds = new IntOpenHashSet();
    private final IntOpenHashSet gpuFineVisibleIds = new IntOpenHashSet();

    private GpuFence inFlightFence;
    private boolean sampleActive;
    private boolean buildActive;
    private long sampleHierarchySerial;
    private long sampleSectionSerial;
    private int gpuVisibleColumnCount;
    private int cpuVisibleColumnCount;
    private int buildColumnCursor;
    private int buildSectionY = NO_Y;
    private int candidateCount;
    private int flatCandidateCount;
    private int cpuFineVisible;
    private int cpuFineAmbiguous;
    private int cpuFineCulled;
    private int gpuFineVisibleCount = -1;
    private int gpuFineDuplicateIds;
    private int cameraSectionX;
    private int cameraSectionY;
    private int cameraSectionZ;
    private float cameraLocalX;
    private float cameraLocalY;
    private float cameraLocalZ;
    private final float[] planes = new float[24];

    private long samplesStarted;
    private long samplesCompleted;
    private long samplesAbortedStale;
    private long samplesDeferred;
    private long candidateBuildProbes;
    private long candidateBuildFrames;
    private long snapshotLookupFailures;
    private long uploads;
    private long uploadBytes;
    private long missingFineIdentities;
    private long unexpectedFineIdentities;
    private long duplicateFineIdentities;
    private long gpuHierarchicalFineFalseCullCount;
    private long readbackPendingHighWater;
    private int lastGpuCoarseVisibleColumns;
    private int lastCpuCoarseVisibleColumns;
    private int lastFineCandidateCount;
    private int lastFlatCandidateCount;
    private int lastCandidateReductionPermille;
    private int lastFineVisibleCount;
    private boolean hardFailure;
    private boolean abandonedForDeviceShutdown;
    private boolean closed;

    public HierarchicalFineVisibilityProbe(
            GpuDevice device,
            PersistentColumnHierarchy hierarchy,
            PersistentSectionScene sections) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.hierarchy = hierarchy;
        this.sections = sections;
        this.gpu = new VulkanLargeSceneVisibilityProbe(device, sections.capacity());
        this.gpuVisibleColumnSlots = new int[hierarchy.capacity()];

        long candidateBytes = Math.multiplyExact(
                (long) sections.capacity(), VulkanLargeSceneVisibilityProbe.CANDIDATE_BYTES);
        long outputBytes = Math.multiplyExact((long) sections.capacity() + 1L, Integer.BYTES);
        this.uploadBuffer = device.createBuffer(
                () -> "Obsidian P4.3 hierarchy-fed fine upload",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC,
                candidateBytes);
        this.uploadView = uploadBuffer.map(false, true);
        this.uploadData = uploadView.data();
        this.readbackBuffer = device.createBuffer(
                () -> "Obsidian P4.3 hierarchy-fed fine readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                outputBytes);
        this.readbackView = readbackBuffer.map(true, false);
        this.readbackData = readbackView.data();

        LOG.log(System.Logger.Level.INFO,
                "P4.3 hierarchy-fed fine visibility configured: sectionCapacity={0}, columnCapacity={1}, candidateBytes={2}, outputBytes={3}, buildBudget={4}, cameraOnlyFullSectionScan=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false.",
                sections.capacity(), hierarchy.capacity(), candidateBytes, outputBytes,
                BUILD_SECTION_BUDGET);
    }

    public void afterWorldRender() {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;

        pollReadback();
        if (hardFailure || !sampleActive || !buildActive) return;

        if (hierarchy.serial() != sampleHierarchySerial || sections.serial() != sampleSectionSerial) {
            abortStaleSample();
            return;
        }
        processBuildBudget();
    }

    public boolean beginSample(
            IntOpenHashSet cpuCoarseVisible,
            IntOpenHashSet gpuCoarseVisible,
            Int2IntOpenHashMap snapshotSlotByIdentity,
            long snapshotLookupSerial,
            long hierarchySerial,
            long sectionSerial,
            int capturedCameraSectionX,
            int capturedCameraSectionY,
            int capturedCameraSectionZ,
            float capturedCameraLocalX,
            float capturedCameraLocalY,
            float capturedCameraLocalZ,
            float[] capturedPlanes,
            int capturedFlatCandidateCount) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return false;
        if (sampleActive || inFlightFence != null) {
            samplesDeferred++;
            return false;
        }
        if (snapshotLookupSerial != hierarchySerial
                || hierarchy.serial() != hierarchySerial
                || sections.serial() != sectionSerial) {
            samplesDeferred++;
            return false;
        }
        if (capturedPlanes == null || capturedPlanes.length != 24) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.3 rejected invalid captured frustum plane array.");
            return false;
        }

        cpuCoarseVisibleIds.clear();
        cpuCoarseVisibleIds.addAll(cpuCoarseVisible);
        expectedFineVisibleIds.clear();
        gpuFineVisibleIds.clear();

        gpuVisibleColumnCount = 0;
        IntIterator gpuColumns = gpuCoarseVisible.iterator();
        while (gpuColumns.hasNext()) {
            int identity = gpuColumns.nextInt();
            int slot = snapshotSlotByIdentity.get(identity);
            if (slot < 0 || !hierarchy.isLive(slot) || hierarchy.identity(slot) != identity) {
                snapshotLookupFailures++;
                hardFailure = true;
                LOG.log(System.Logger.Level.ERROR,
                        "P4.3 coarse identity lookup FAILED: identity={0}, slot={1}, lookupSerial={2}, hierarchySerial={3}.",
                        identity, slot, snapshotLookupSerial, hierarchySerial);
                return false;
            }
            gpuVisibleColumnSlots[gpuVisibleColumnCount++] = slot;
        }

        sampleHierarchySerial = hierarchySerial;
        sampleSectionSerial = sectionSerial;
        cpuVisibleColumnCount = cpuCoarseVisible.size();
        buildColumnCursor = 0;
        buildSectionY = NO_Y;
        candidateCount = 0;
        flatCandidateCount = capturedFlatCandidateCount;
        cpuFineVisible = 0;
        cpuFineAmbiguous = 0;
        cpuFineCulled = 0;
        gpuFineVisibleCount = -1;
        gpuFineDuplicateIds = 0;
        cameraSectionX = capturedCameraSectionX;
        cameraSectionY = capturedCameraSectionY;
        cameraSectionZ = capturedCameraSectionZ;
        cameraLocalX = capturedCameraLocalX;
        cameraLocalY = capturedCameraLocalY;
        cameraLocalZ = capturedCameraLocalZ;
        System.arraycopy(capturedPlanes, 0, planes, 0, planes.length);

        sampleActive = true;
        buildActive = true;
        samplesStarted++;
        return true;
    }

    public boolean hardFailure() {
        return hardFailure;
    }

    private void processBuildBudget() {
        int budget = BUILD_SECTION_BUDGET;
        candidateBuildFrames++;

        while (budget > 0 && buildColumnCursor < gpuVisibleColumnCount) {
            int columnSlot = gpuVisibleColumnSlots[buildColumnCursor];
            if (!hierarchy.isLive(columnSlot)) {
                abortStaleSample();
                return;
            }

            if (buildSectionY == NO_Y) {
                buildSectionY = hierarchy.minLiveSectionY(columnSlot);
            }
            int maxY = hierarchy.maxLiveSectionY(columnSlot);
            int chunkX = hierarchy.chunkX(columnSlot);
            int chunkZ = hierarchy.chunkZ(columnSlot);
            int columnIdentity = hierarchy.identity(columnSlot);
            boolean cpuCoarseVisible = cpuCoarseVisibleIds.contains(columnIdentity);

            while (budget > 0 && buildSectionY <= maxY) {
                int y = buildSectionY++;
                budget--;
                candidateBuildProbes++;

                if (!hierarchy.containsSection(columnSlot, y)) continue;
                int sectionSlot = sections.sectionSlot(chunkX, y, chunkZ);
                if (sectionSlot < 0 || !sections.isLive(sectionSlot)) {
                    snapshotLookupFailures++;
                    hardFailure = true;
                    LOG.log(System.Logger.Level.ERROR,
                            "P4.3 hierarchy/section lookup FAILED: column=({0},{1}), y={2}, columnIdentity={3}, sectionSlot={4}.",
                            chunkX, chunkZ, y, columnIdentity, sectionSlot);
                    return;
                }
                if (candidateCount >= sections.capacity()) {
                    hardFailure = true;
                    LOG.log(System.Logger.Level.ERROR,
                            "P4.3 fine candidate count exceeded persistent section capacity: candidates={0}, capacity={1}.",
                            candidateCount, sections.capacity());
                    return;
                }

                int byteOffset = Math.multiplyExact(
                        candidateCount, VulkanLargeSceneVisibilityProbe.CANDIDATE_BYTES);
                uploadData.putInt(byteOffset, chunkX);
                uploadData.putInt(byteOffset + 4, y);
                uploadData.putInt(byteOffset + 8, chunkZ);
                uploadData.putInt(byteOffset + 12, sections.identity(sectionSlot));
                candidateCount++;

                if (cpuCoarseVisible) {
                    int classification = classifyCpu(chunkX, y, chunkZ);
                    if (classification == -1) {
                        cpuFineCulled++;
                    } else {
                        if (classification == 0) cpuFineAmbiguous++;
                        else cpuFineVisible++;
                        expectedFineVisibleIds.add(sections.identity(sectionSlot));
                    }
                }
            }

            if (buildSectionY > maxY) {
                buildColumnCursor++;
                buildSectionY = NO_Y;
            }
        }

        if (buildColumnCursor >= gpuVisibleColumnCount) {
            buildActive = false;
            dispatch();
        }
    }

    /** -1 culled, 0 boundary-ambiguous/conservatively visible, 1 clearly visible. */
    private int classifyCpu(int sectionX, int sectionY, int sectionZ) {
        float minX = (sectionX - cameraSectionX) * 16.0f - cameraLocalX;
        float minY = (sectionY - cameraSectionY) * 16.0f - cameraLocalY;
        float minZ = (sectionZ - cameraSectionZ) * 16.0f - cameraLocalZ;
        float maxX = minX + 16.0f;
        float maxY = minY + 16.0f;
        float maxZ = minZ + 16.0f;
        boolean ambiguous = false;

        for (int i = 0; i < 6; i++) {
            int p = i * 4;
            float a = planes[p];
            float b = planes[p + 1];
            float c = planes[p + 2];
            float d = planes[p + 3];
            float x = a >= 0.0f ? maxX : minX;
            float y = b >= 0.0f ? maxY : minY;
            float z = c >= 0.0f ? maxZ : minZ;
            float maxDistance = a * x + b * y + c * z + d;
            if (maxDistance < -FRUSTUM_EPSILON) return -1;
            if (maxDistance <= FRUSTUM_EPSILON) ambiguous = true;
        }
        return ambiguous ? 0 : 1;
    }

    private void dispatch() {
        if (!sampleActive || inFlightFence != null) return;
        if (hierarchy.serial() != sampleHierarchySerial || sections.serial() != sampleSectionSerial) {
            abortStaleSample();
            return;
        }

        CommandEncoder encoder = device.createCommandEncoder();
        try {
            if (candidateCount > 0) {
                int bytes = Math.multiplyExact(
                        candidateCount, VulkanLargeSceneVisibilityProbe.CANDIDATE_BYTES);
                encoder.copyToBuffer(
                        uploadBuffer.slice(0L, bytes),
                        gpu.candidateBuffer().slice(0L, bytes));
                uploads++;
                uploadBytes += bytes;
            }

            gpu.dispatch(
                    encoder,
                    candidateCount,
                    cameraSectionX,
                    cameraSectionY,
                    cameraSectionZ,
                    cameraLocalX,
                    cameraLocalY,
                    cameraLocalZ,
                    FRUSTUM_EPSILON,
                    planes);
            encoder.copyToBuffer(gpu.outputSlice(), readbackBuffer.slice(0L, gpu.outputBytes()));
            GpuFence fence = encoder.createFence();
            encoder.submit();
            inFlightFence = fence;
            if (readbackPendingHighWater < 1L) readbackPendingHighWater = 1L;
        } catch (RuntimeException e) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.3 hierarchy-fed fine visibility dispatch failed; P4.3 shadow validation disabled.",
                    e);
        }
    }

    private void pollReadback() {
        if (inFlightFence == null) return;

        boolean complete;
        try {
            complete = inFlightFence.awaitCompletion(0L);
        } catch (RuntimeException e) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR, "P4.3 readback fence polling failed.", e);
            return;
        }
        if (!complete) return;

        inFlightFence.close();
        inFlightFence = null;

        if (!sampleActive
                || hierarchy.serial() != sampleHierarchySerial
                || sections.serial() != sampleSectionSerial) {
            if (sampleActive) abortStaleSample();
            return;
        }

        int visibleCount = readbackData.getInt(0);
        if (visibleCount < 0 || visibleCount > candidateCount) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.3 GPU fine-visible count outside candidate bounds: visible={0}, candidates={1}.",
                    visibleCount, candidateCount);
            return;
        }

        gpuFineVisibleCount = visibleCount;
        gpuFineVisibleIds.clear();
        gpuFineDuplicateIds = 0;
        for (int i = 0; i < visibleCount; i++) {
            int identity = readbackData.getInt((i + 1) * Integer.BYTES);
            if (!gpuFineVisibleIds.add(identity)) gpuFineDuplicateIds++;
        }

        finishSample();
    }

    private void finishSample() {
        int missing = 0;
        IntIterator expected = expectedFineVisibleIds.iterator();
        while (expected.hasNext()) {
            if (!gpuFineVisibleIds.contains(expected.nextInt())) missing++;
        }

        int unexpected = 0;
        IntIterator actual = gpuFineVisibleIds.iterator();
        while (actual.hasNext()) {
            if (!expectedFineVisibleIds.contains(actual.nextInt())) unexpected++;
        }

        samplesCompleted++;
        missingFineIdentities += missing;
        unexpectedFineIdentities += unexpected;
        duplicateFineIdentities += gpuFineDuplicateIds;
        gpuHierarchicalFineFalseCullCount += missing;

        lastGpuCoarseVisibleColumns = gpuVisibleColumnCount;
        lastCpuCoarseVisibleColumns = cpuVisibleColumnCount;
        lastFineCandidateCount = candidateCount;
        lastFlatCandidateCount = flatCandidateCount;
        lastCandidateReductionPermille = flatCandidateCount == 0
                ? 0
                : (int) Math.min(1000L, (long) candidateCount * 1000L / flatCandidateCount);
        lastFineVisibleCount = gpuFineVisibleCount;

        boolean exact = missing == 0
                && unexpected == 0
                && gpuFineDuplicateIds == 0
                && gpuFineVisibleCount == expectedFineVisibleIds.size();

        if (!exact) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.3 hierarchy-fed fine visibility mismatch: gpuCoarseColumns={0}, cpuCoarseColumns={1}, flatSections={2}, fineCandidates={3}, cpuFineVisible={4}, cpuFineAmbiguous={5}, cpuFineCulled={6}, gpuFineVisible={7}, missing={8}, unexpected={9}, duplicate={10}.",
                    gpuVisibleColumnCount, cpuVisibleColumnCount, flatCandidateCount,
                    candidateCount, cpuFineVisible, cpuFineAmbiguous, cpuFineCulled,
                    gpuFineVisibleCount, missing, unexpected, gpuFineDuplicateIds);
        } else if (samplesCompleted <= 3 || samplesCompleted % 10 == 0) {
            LOG.log(System.Logger.Level.INFO,
                    "P4.3 hierarchy-fed fine visibility sample PASS: sample={0}, gpuCoarseColumns={1}, cpuCoarseColumns={2}, flatSections={3}, fineCandidates={4}, candidatePermille={5}, cpuFineVisible={6}, boundaryAmbiguous={7}, gpuFineVisible={8}, missing=0, unexpected=0, duplicate=0, gpuHierarchicalFineFalseCullCount=0, cameraOnlyFullSectionScan=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false.",
                    samplesCompleted, gpuVisibleColumnCount, cpuVisibleColumnCount,
                    flatCandidateCount, candidateCount, lastCandidateReductionPermille,
                    expectedFineVisibleIds.size(), cpuFineAmbiguous, gpuFineVisibleCount);
        }

        sampleActive = false;
        buildActive = false;
    }

    private void abortStaleSample() {
        samplesAbortedStale++;
        sampleActive = false;
        buildActive = false;
        buildSectionY = NO_Y;
        cpuCoarseVisibleIds.clear();
        expectedFineVisibleIds.clear();
        gpuFineVisibleIds.clear();
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;

        if (inFlightFence != null) {
            long start = System.nanoTime();
            try {
                if (inFlightFence.awaitCompletion(SHUTDOWN_WAIT_NS)) {
                    inFlightFence.close();
                    inFlightFence = null;
                }
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "P4.3 shutdown fence wait failed; leaving P4.3 buffers for device shutdown.",
                        e);
            }
            if (inFlightFence != null || System.nanoTime() - start >= SHUTDOWN_WAIT_NS) {
                abandonedForDeviceShutdown = true;
            }
        }

        LOG.log(System.Logger.Level.INFO,
                "P4.3 final hierarchy-fed fine visibility evidence: configured=true, samplesStarted={0}, samplesCompleted={1}, samplesAbortedStale={2}, samplesDeferred={3}, candidateBuildProbes={4}, candidateBuildFrames={5}, snapshotLookupFailures={6}, uploads={7}, uploadBytes={8}, missingFine={9}, unexpectedFine={10}, duplicateFine={11}, gpuHierarchicalFineFalseCullCount={12}, readbackPendingHighWater={13}, lastGpuCoarseVisibleColumns={14}, lastCpuCoarseVisibleColumns={15}, lastFineCandidateCount={16}, lastFlatFineCandidateCount={17}, lastCandidatePermille={18}, lastFineVisibleCount={19}, candidateBytes={20}, outputBytes={21}, hardFailure={22}, abandonedForDeviceShutdown={23}, cameraOnlyFullSectionScan=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false, commandCompactionEnabled=false, temporalVisibilityEnabled=false, hizEnabled=false.",
                samplesStarted, samplesCompleted, samplesAbortedStale, samplesDeferred,
                candidateBuildProbes, candidateBuildFrames, snapshotLookupFailures,
                uploads, uploadBytes, missingFineIdentities, unexpectedFineIdentities,
                duplicateFineIdentities, gpuHierarchicalFineFalseCullCount,
                readbackPendingHighWater, lastGpuCoarseVisibleColumns,
                lastCpuCoarseVisibleColumns, lastFineCandidateCount,
                lastFlatCandidateCount, lastCandidateReductionPermille,
                lastFineVisibleCount, gpu.candidateBytes(), gpu.outputBytes(),
                hardFailure, abandonedForDeviceShutdown);

        if (!abandonedForDeviceShutdown) {
            readbackView.close();
            readbackBuffer.close();
            uploadView.close();
            uploadBuffer.close();
            gpu.close();
        }
    }
}
