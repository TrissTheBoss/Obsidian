package dev.obsidian.render.visibility;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.vulkan.VulkanGpuResidentHierarchicalFineProbe;
import dev.obsidian.render.vulkan.VulkanLargeSceneColumnVisibilityProbe;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.nio.ByteBuffer;

/**
 * P4.4 shadow-only GPU-resident coarse -> fine visibility validation.
 *
 * <p>The coarse GPU output feeds the fine compute stage directly. CPU work is
 * retained only after final completion as an independent correctness oracle.</p>
 */
public final class GpuResidentHierarchicalFineVisibilityProbe {
    private static final System.Logger LOG =
            System.getLogger("Obsidian/GpuResidentHierarchicalFineVisibility");

    private static final float FRUSTUM_EPSILON = 1.0e-3f;

    private final GpuDevice device;
    private final PersistentColumnHierarchy hierarchy;
    private final PersistentSectionScene sections;
    private final int sectionCount;
    private final int minSectionY;
    private final VulkanGpuResidentHierarchicalFineProbe gpu;
    private final GpuBuffer uploadBuffer;
    private final GpuBufferSlice.MappedView uploadView;
    private final ByteBuffer uploadData;
    private final GpuBuffer readbackBuffer;
    private final GpuBufferSlice.MappedView readbackView;
    private final ByteBuffer readbackData;
    private final int[] snapshotSlotByCandidateIndex;
    private final int[] snapshotIdentityByCandidateIndex;
    private final IntOpenHashSet expectedFineVisibleIds = new IntOpenHashSet();
    private final IntOpenHashSet baselineFineVisibleIds = new IntOpenHashSet();
    private final IntOpenHashSet gpuFineVisibleIds = new IntOpenHashSet();
    private final IntOpenHashSet seenCoarseSnapshotIndices = new IntOpenHashSet();

    private boolean snapshotBuilding;
    private boolean snapshotReady;
    private long snapshotHierarchySerial;
    private long snapshotSectionSerial;
    private int snapshotCandidateCount;
    private boolean sampleActive;
    private long sampleHierarchySerial;
    private long sampleSectionSerial;

    private long samplesStarted;
    private long samplesCompleted;
    private long samplesAbortedStale;
    private long samplesDeferred;
    private long sectionTableSnapshotBuilds;
    private long sectionTableSnapshotRestarts;
    private long sectionTableUploads;
    private long sectionTableUploadBytes;
    private long snapshotLookupFailures;
    private long invalidCoarseOutputIndices;
    private long duplicateCoarseOutputIndices;
    private long candidateCountMismatches;
    private long missingFineIdentities;
    private long unexpectedFineIdentities;
    private long duplicateFineIdentities;
    private long gpuResidentFineFalseCullCount;
    private long safeExtraCoarseColumns;
    private long safeExtraLineageFineIdentities;
    private long readbackPendingHighWater;
    private int lastGpuFineCandidateCount;
    private int lastCpuFineCandidateCount;
    private int lastFlatFineCandidateCount;
    private int lastCandidatePermille;
    private int lastBaselineFineVisible;
    private int lastSafeExtraCoarseColumns;
    private int lastSafeExtraLineageFine;
    private int lastGpuFineVisible;
    private boolean hardFailure;
    private boolean abandonedForDeviceShutdown;
    private boolean closed;

    public GpuResidentHierarchicalFineVisibilityProbe(
            GpuDevice device,
            PersistentColumnHierarchy hierarchy,
            PersistentSectionScene sections,
            VulkanLargeSceneColumnVisibilityProbe coarseGpu) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.hierarchy = hierarchy;
        this.sections = sections;
        this.sectionCount = hierarchy.sectionCount();
        this.minSectionY = hierarchy.minSectionY();
        this.gpu = new VulkanGpuResidentHierarchicalFineProbe(
                device,
                hierarchy.capacity(),
                sectionCount,
                sections.capacity(),
                coarseGpu.outputVkBuffer(),
                coarseGpu.outputBytes());

        this.snapshotSlotByCandidateIndex = new int[hierarchy.capacity()];
        this.snapshotIdentityByCandidateIndex = new int[hierarchy.capacity()];

        long tableBytes = gpu.sectionTableBytes();
        this.uploadBuffer = device.createBuffer(
                () -> "Obsidian P4.4 GPU-resident section table upload",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC,
                tableBytes);
        this.uploadView = uploadBuffer.map(false, true);
        this.uploadData = uploadView.data();

        this.readbackBuffer = device.createBuffer(
                () -> "Obsidian P4.4 GPU-resident fine readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                gpu.outputBytes());
        this.readbackView = readbackBuffer.map(true, false);
        this.readbackData = readbackView.data();

        LOG.log(System.Logger.Level.INFO,
                "P4.4 GPU-resident hierarchy fine visibility configured: columnCapacity={0}, sectionCount={1}, sectionCapacity={2}, sectionTableBytes={3}, outputBytes={4}, intermediateCpuReadback=false, intermediateCpuCandidateBuild=false, cameraOnlySectionTableRebuild=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false.",
                hierarchy.capacity(), sectionCount, sections.capacity(),
                tableBytes, gpu.outputBytes());
    }

    public void startSnapshot(long hierarchySerial, long sectionSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;
        if (snapshotBuilding) sectionTableSnapshotRestarts++;
        sectionTableSnapshotBuilds++;
        snapshotBuilding = true;
        snapshotReady = false;
        snapshotHierarchySerial = hierarchySerial;
        snapshotSectionSerial = sectionSerial;
        snapshotCandidateCount = 0;
    }

    public void writeSnapshotColumn(int candidateIndex, int columnSlot) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;
        if (!snapshotBuilding
                || hierarchy.serial() != snapshotHierarchySerial
                || sections.serial() != snapshotSectionSerial) {
            samplesDeferred++;
            return;
        }
        if (candidateIndex < 0 || candidateIndex >= hierarchy.capacity()
                || columnSlot < 0 || !hierarchy.isLive(columnSlot)) {
            snapshotLookupFailures++;
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.4 invalid snapshot column mapping: candidateIndex={0}, columnSlot={1}.",
                    candidateIndex, columnSlot);
            return;
        }

        snapshotSlotByCandidateIndex[candidateIndex] = columnSlot;
        snapshotIdentityByCandidateIndex[candidateIndex] = hierarchy.identity(columnSlot);
        int chunkX = hierarchy.chunkX(columnSlot);
        int chunkZ = hierarchy.chunkZ(columnSlot);

        for (int offset = 0; offset < sectionCount; offset++) {
            int y = minSectionY + offset;
            int tableIndex = Math.addExact(Math.multiplyExact(candidateIndex, sectionCount), offset);
            int byteOffset = Math.multiplyExact(
                    tableIndex, VulkanGpuResidentHierarchicalFineProbe.SECTION_RECORD_BYTES);
            uploadData.putInt(byteOffset, chunkX);
            uploadData.putInt(byteOffset + 4, y);
            uploadData.putInt(byteOffset + 8, chunkZ);

            int identity = 0;
            if (hierarchy.containsSection(columnSlot, y)) {
                int sectionSlot = sections.sectionSlot(chunkX, y, chunkZ);
                if (sectionSlot < 0 || !sections.isLive(sectionSlot)) {
                    snapshotLookupFailures++;
                    hardFailure = true;
                    LOG.log(System.Logger.Level.ERROR,
                            "P4.4 snapshot hierarchy/section lookup FAILED: candidateIndex={0}, column=({1},{2}), y={3}, sectionSlot={4}.",
                            candidateIndex, chunkX, chunkZ, y, sectionSlot);
                    return;
                }
                identity = sections.identity(sectionSlot);
            }
            uploadData.putInt(byteOffset + 12, identity);
        }

        snapshotCandidateCount = Math.max(snapshotCandidateCount, candidateIndex + 1);
    }

    public void finishSnapshot(
            int candidateCount,
            long hierarchySerial,
            long sectionSerial) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;
        if (!snapshotBuilding
                || hierarchySerial != snapshotHierarchySerial
                || sectionSerial != snapshotSectionSerial
                || candidateCount != snapshotCandidateCount
                || hierarchy.serial() != hierarchySerial
                || sections.serial() != sectionSerial) {
            snapshotLookupFailures++;
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.4 snapshot completion mismatch: candidateCount={0}, built={1}, hierarchySerial={2}/{3}, sectionSerial={4}/{5}.",
                    candidateCount, snapshotCandidateCount,
                    hierarchySerial, snapshotHierarchySerial,
                    sectionSerial, snapshotSectionSerial);
            return;
        }
        snapshotBuilding = false;
        snapshotReady = true;
    }

    public boolean prepareAndDispatch(
            CommandEncoder encoder,
            boolean uploadChanged,
            int candidateCount,
            long hierarchySerial,
            long sectionSerial,
            int cameraSectionX,
            int cameraSectionY,
            int cameraSectionZ,
            float cameraLocalX,
            float cameraLocalY,
            float cameraLocalZ,
            float[] planes) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return false;
        if (sampleActive) {
            samplesDeferred++;
            return false;
        }
        if (!snapshotReady
                || snapshotHierarchySerial != hierarchySerial
                || snapshotSectionSerial != sectionSerial
                || snapshotCandidateCount != candidateCount) {
            samplesDeferred++;
            return false;
        }

        if (uploadChanged && candidateCount > 0) {
            long entries = Math.multiplyExact((long) candidateCount, sectionCount);
            long bytes = Math.multiplyExact(
                    entries, VulkanGpuResidentHierarchicalFineProbe.SECTION_RECORD_BYTES);
            encoder.copyToBuffer(
                    uploadBuffer.slice(0L, bytes),
                    gpu.sectionTableBuffer().slice(0L, bytes));
            sectionTableUploads++;
            sectionTableUploadBytes += bytes;
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

        sampleActive = true;
        sampleHierarchySerial = hierarchySerial;
        sampleSectionSerial = sectionSerial;
        samplesStarted++;
        if (readbackPendingHighWater < 1L) readbackPendingHighWater = 1L;
        return true;
    }

    public void abortStaleSample() {
        if (!sampleActive) return;
        samplesAbortedStale++;
        sampleActive = false;
        expectedFineVisibleIds.clear();
        baselineFineVisibleIds.clear();
        gpuFineVisibleIds.clear();
        seenCoarseSnapshotIndices.clear();
    }

    public void validateCompletedSample(
            int[] gpuVisibleSnapshotIndices,
            int visibleColumnCount,
            IntOpenHashSet cpuCoarseVisibleIds,
            long hierarchySerial,
            long sectionSerial,
            int flatFineCandidateCount,
            int cameraSectionX,
            int cameraSectionY,
            int cameraSectionZ,
            float cameraLocalX,
            float cameraLocalY,
            float cameraLocalZ,
            float[] planes) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure || !sampleActive) return;

        if (hierarchySerial != sampleHierarchySerial
                || sectionSerial != sampleSectionSerial
                || hierarchy.serial() != sampleHierarchySerial
                || sections.serial() != sampleSectionSerial) {
            abortStaleSample();
            return;
        }

        int gpuCandidateCount = readbackData.getInt(0);
        int gpuVisibleCount = readbackData.getInt(Integer.BYTES);
        int invalidIndices = readbackData.getInt(Integer.BYTES * 2);
        if (gpuCandidateCount < 0 || gpuCandidateCount > sections.capacity()
                || gpuVisibleCount < 0 || gpuVisibleCount > gpuCandidateCount
                || invalidIndices < 0) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.4 readback header outside bounds: gpuCandidates={0}, gpuVisible={1}, invalidIndices={2}, sectionCapacity={3}.",
                    gpuCandidateCount, gpuVisibleCount, invalidIndices, sections.capacity());
            return;
        }

        invalidCoarseOutputIndices += invalidIndices;
        expectedFineVisibleIds.clear();
        baselineFineVisibleIds.clear();
        gpuFineVisibleIds.clear();
        seenCoarseSnapshotIndices.clear();

        int cpuCandidateCount = 0;
        int safeExtraColumns = 0;
        int safeExtraLineageFine = 0;

        for (int i = 0; i < visibleColumnCount; i++) {
            int snapshotIndex = gpuVisibleSnapshotIndices[i];
            if (snapshotIndex < 0 || snapshotIndex >= snapshotCandidateCount) {
                invalidCoarseOutputIndices++;
                hardFailure = true;
                LOG.log(System.Logger.Level.ERROR,
                        "P4.4 coarse output snapshot index outside captured bounds: index={0}, candidates={1}.",
                        snapshotIndex, snapshotCandidateCount);
                return;
            }
            if (!seenCoarseSnapshotIndices.add(snapshotIndex)) {
                duplicateCoarseOutputIndices++;
                hardFailure = true;
                LOG.log(System.Logger.Level.ERROR,
                        "P4.4 duplicate coarse output snapshot index {0}.", snapshotIndex);
                return;
            }

            int columnSlot = snapshotSlotByCandidateIndex[snapshotIndex];
            int columnIdentity = snapshotIdentityByCandidateIndex[snapshotIndex];
            if (!hierarchy.isLive(columnSlot)
                    || hierarchy.identity(columnSlot) != columnIdentity) {
                snapshotLookupFailures++;
                hardFailure = true;
                LOG.log(System.Logger.Level.ERROR,
                        "P4.4 completed sample column lookup stale/incoherent: snapshotIndex={0}, slot={1}, identity={2}.",
                        snapshotIndex, columnSlot, columnIdentity);
                return;
            }

            boolean cpuCoarseVisible = cpuCoarseVisibleIds.contains(columnIdentity);
            if (!cpuCoarseVisible) safeExtraColumns++;

            int chunkX = hierarchy.chunkX(columnSlot);
            int chunkZ = hierarchy.chunkZ(columnSlot);
            for (int offset = 0; offset < sectionCount; offset++) {
                int y = minSectionY + offset;
                if (!hierarchy.containsSection(columnSlot, y)) continue;
                cpuCandidateCount++;

                int sectionSlot = sections.sectionSlot(chunkX, y, chunkZ);
                if (sectionSlot < 0 || !sections.isLive(sectionSlot)) {
                    snapshotLookupFailures++;
                    hardFailure = true;
                    LOG.log(System.Logger.Level.ERROR,
                            "P4.4 validation hierarchy/section lookup FAILED: snapshotIndex={0}, column=({1},{2}), y={3}.",
                            snapshotIndex, chunkX, chunkZ, y);
                    return;
                }

                int classification = classifyCpu(
                        chunkX, y, chunkZ,
                        cameraSectionX, cameraSectionY, cameraSectionZ,
                        cameraLocalX, cameraLocalY, cameraLocalZ,
                        planes);
                if (classification < 0) continue;

                int sectionIdentity = sections.identity(sectionSlot);
                expectedFineVisibleIds.add(sectionIdentity);
                if (cpuCoarseVisible) {
                    baselineFineVisibleIds.add(sectionIdentity);
                } else {
                    safeExtraLineageFine++;
                }
            }
        }

        int duplicateFine = 0;
        for (int i = 0; i < gpuVisibleCount; i++) {
            int identity = readbackData.getInt(
                    (VulkanGpuResidentHierarchicalFineProbe.OUTPUT_HEADER_WORDS + i)
                            * Integer.BYTES);
            if (!gpuFineVisibleIds.add(identity)) duplicateFine++;
        }

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

        boolean candidateCountExact = gpuCandidateCount == cpuCandidateCount;
        boolean lineageAccountingCoherent =
                expectedFineVisibleIds.size()
                        == baselineFineVisibleIds.size() + safeExtraLineageFine;
        boolean exact = invalidIndices == 0
                && candidateCountExact
                && lineageAccountingCoherent
                && missing == 0
                && unexpected == 0
                && duplicateFine == 0
                && gpuVisibleCount == expectedFineVisibleIds.size();

        samplesCompleted++;
        if (!candidateCountExact) candidateCountMismatches++;
        missingFineIdentities += missing;
        unexpectedFineIdentities += unexpected;
        duplicateFineIdentities += duplicateFine;
        gpuResidentFineFalseCullCount += missing;
        safeExtraCoarseColumns += safeExtraColumns;
        safeExtraLineageFineIdentities += safeExtraLineageFine;

        lastGpuFineCandidateCount = gpuCandidateCount;
        lastCpuFineCandidateCount = cpuCandidateCount;
        lastFlatFineCandidateCount = flatFineCandidateCount;
        lastCandidatePermille = flatFineCandidateCount == 0
                ? 0
                : (int) Math.min(1000L,
                (long) gpuCandidateCount * 1000L / flatFineCandidateCount);
        lastBaselineFineVisible = baselineFineVisibleIds.size();
        lastSafeExtraCoarseColumns = safeExtraColumns;
        lastSafeExtraLineageFine = safeExtraLineageFine;
        lastGpuFineVisible = gpuVisibleCount;

        if (!exact) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.4 GPU-resident hierarchy fine mismatch: gpuCoarseColumns={0}, safeExtraCoarseColumns={1}, gpuFineCandidates={2}, cpuFineCandidates={3}, flatFineCandidates={4}, baselineFineVisible={5}, safeExtraLineageFine={6}, candidateSetExpectedFine={7}, gpuFineVisible={8}, invalidCoarseIndices={9}, missing={10}, unexpected={11}, duplicate={12}, lineageAccountingCoherent={13}.",
                    visibleColumnCount, safeExtraColumns,
                    gpuCandidateCount, cpuCandidateCount, flatFineCandidateCount,
                    baselineFineVisibleIds.size(), safeExtraLineageFine,
                    expectedFineVisibleIds.size(), gpuVisibleCount, invalidIndices,
                    missing, unexpected, duplicateFine, lineageAccountingCoherent);
        } else if (samplesCompleted <= 3 || samplesCompleted % 10 == 0) {
            LOG.log(System.Logger.Level.INFO,
                    "P4.4 GPU-resident hierarchy fine sample PASS: sample={0}, gpuCoarseColumns={1}, safeExtraCoarseColumns={2}, gpuFineCandidates={3}, cpuFineCandidates={4}, flatFineCandidates={5}, candidatePermille={6}, baselineFineVisible={7}, safeExtraLineageFine={8}, candidateSetExpectedFine={9}, gpuFineVisible={10}, missing=0, unexpected=0, duplicate=0, gpuResidentFineFalseCullCount=0, intermediateCpuReadback=false, intermediateCpuCandidateBuild=false, cameraOnlySectionTableRebuild=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false.",
                    samplesCompleted, visibleColumnCount, safeExtraColumns,
                    gpuCandidateCount, cpuCandidateCount, flatFineCandidateCount,
                    lastCandidatePermille, baselineFineVisibleIds.size(),
                    safeExtraLineageFine, expectedFineVisibleIds.size(),
                    gpuVisibleCount);
        }

        sampleActive = false;
    }

    public boolean hardFailure() {
        return hardFailure;
    }

    public void close(boolean parentAbandonedForDeviceShutdown) {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        if (sampleActive) abortStaleSample();
        abandonedForDeviceShutdown = parentAbandonedForDeviceShutdown;

        LOG.log(System.Logger.Level.INFO,
                "P4.4 final GPU-resident hierarchy fine evidence: configured=true, samplesStarted={0}, samplesCompleted={1}, samplesAbortedStale={2}, samplesDeferred={3}, sectionTableSnapshotBuilds={4}, sectionTableSnapshotRestarts={5}, sectionTableUploads={6}, sectionTableUploadBytes={7}, sectionTableBytes={8}, snapshotLookupFailures={9}, invalidCoarseOutputIndices={10}, duplicateCoarseOutputIndices={11}, candidateCountMismatches={12}, missingFine={13}, unexpectedFine={14}, duplicateFine={15}, gpuResidentFineFalseCullCount={16}, safeExtraCoarseColumns={17}, safeExtraLineageFine={18}, readbackPendingHighWater={19}, lastGpuFineCandidateCount={20}, lastCpuFineCandidateCount={21}, lastFlatFineCandidateCount={22}, lastCandidatePermille={23}, lastBaselineFineVisible={24}, lastSafeExtraCoarseColumns={25}, lastSafeExtraLineageFine={26}, lastGpuFineVisible={27}, hardFailure={28}, abandonedForDeviceShutdown={29}, intermediateCpuReadback=false, intermediateCpuCandidateBuild=false, cameraOnlySectionTableRebuild=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false, commandCompactionEnabled=false, temporalVisibilityEnabled=false, hizEnabled=false.",
                samplesStarted, samplesCompleted, samplesAbortedStale, samplesDeferred,
                sectionTableSnapshotBuilds, sectionTableSnapshotRestarts,
                sectionTableUploads, sectionTableUploadBytes, gpu.sectionTableBytes(),
                snapshotLookupFailures, invalidCoarseOutputIndices,
                duplicateCoarseOutputIndices, candidateCountMismatches,
                missingFineIdentities, unexpectedFineIdentities,
                duplicateFineIdentities, gpuResidentFineFalseCullCount,
                safeExtraCoarseColumns, safeExtraLineageFineIdentities,
                readbackPendingHighWater, lastGpuFineCandidateCount,
                lastCpuFineCandidateCount, lastFlatFineCandidateCount,
                lastCandidatePermille, lastBaselineFineVisible,
                lastSafeExtraCoarseColumns, lastSafeExtraLineageFine,
                lastGpuFineVisible, hardFailure, abandonedForDeviceShutdown);

        if (!abandonedForDeviceShutdown) {
            readbackView.close();
            readbackBuffer.close();
            uploadView.close();
            uploadBuffer.close();
            gpu.close();
        }
    }

    private static int classifyCpu(
            int sectionX,
            int sectionY,
            int sectionZ,
            int cameraSectionX,
            int cameraSectionY,
            int cameraSectionZ,
            float cameraLocalX,
            float cameraLocalY,
            float cameraLocalZ,
            float[] planes) {
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
}
