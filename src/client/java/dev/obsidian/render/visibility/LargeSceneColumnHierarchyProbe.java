package dev.obsidian.render.visibility;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.vulkan.VulkanLargeSceneColumnVisibilityProbe;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

/**
 * P4.2 shadow-only coarse chunk-column hierarchy/GPU visibility validation.
 *
 * <p>P4.1 remains the fine section-level shadow control and P3.10 remains the
 * only production terrain draw owner.</p>
 */
public final class LargeSceneColumnHierarchyProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/LargeSceneColumnHierarchy");

    private static final int SNAPSHOT_COLUMN_BUDGET = 16_384;
    private static final int STRUCTURAL_AUDIT_BUDGET = 16_384;
    private static final int ORACLE_COLUMN_BUDGET = 8_192;
    private static final int SAMPLE_INTERVAL_FRAMES = 60;
    private static final long SHUTDOWN_WAIT_NS = 2_000_000_000L;
    private static final float FRUSTUM_EPSILON = 1.0e-3f;

    private final GpuDevice device;
    private final PersistentColumnHierarchy hierarchy;
    private final PersistentSectionScene sections;
    private final int effectiveRenderDistance;
    private final VulkanLargeSceneColumnVisibilityProbe gpu;
    private final GpuBuffer uploadBuffer;
    private final GpuBufferSlice.MappedView uploadView;
    private final ByteBuffer uploadData;
    private final GpuBuffer readbackBuffer;
    private final GpuBufferSlice.MappedView readbackView;
    private final ByteBuffer readbackData;
    private final float[] cameraPlanes = new float[24];
    private final Vector4f planeScratch = new Vector4f();
    private final IntOpenHashSet expectedVisibleIds = new IntOpenHashSet();
    private final IntOpenHashSet gpuVisibleIds = new IntOpenHashSet();

    private GpuFence inFlightFence;

    private long uploadedHierarchySerial;
    private int uploadedCandidateCount = -1;
    private boolean snapshotBuilding;
    private long snapshotBuildSerial;
    private int snapshotSlotCursor;
    private int snapshotCandidateCount;

    private long auditHierarchySerial;
    private long auditSectionSerial;
    private int auditSectionCursor;
    private int auditColumnCursor;
    private int auditedLiveSections;
    private int auditedColumnSections;
    private boolean auditSectionsComplete;
    private boolean auditComplete;

    private boolean sampleActive;
    private long sampleHierarchySerial;
    private long sampleSectionSerial;
    private int sampleCandidateCount;
    private int sampleOracleCursor;
    private boolean sampleOracleComplete;
    private boolean sampleGpuComplete;
    private int sampleCpuVisible;
    private int sampleCpuAmbiguous;
    private int sampleCpuCulled;
    private int sampleGpuVisibleCount;
    private int sampleGpuDuplicateIds;
    private int sampleCameraSectionX;
    private int sampleCameraSectionY;
    private int sampleCameraSectionZ;
    private float sampleCameraLocalX;
    private float sampleCameraLocalY;
    private float sampleCameraLocalZ;
    private final float[] samplePlanes = new float[24];
    private Frustum sampleFrustum;
    private long sampleEstimatedFineCandidateUpperBound;
    private int sampleFlatFineCandidates;

    private long nextSampleFrame;
    private long hierarchyAuditRuns;
    private long hierarchyAuditFailures;
    private long snapshotBuildRestarts;
    private long snapshotUploads;
    private long snapshotUploadBytes;
    private long dispatches;
    private long columnCandidatesTested;
    private long samplesCompleted;
    private long samplesAbortedStale;
    private long conservativeSamples;
    private long exactSamples;
    private long missingVisibleColumnIdentities;
    private long unexpectedVisibleColumnIdentities;
    private long duplicateVisibleColumnIdentities;
    private long gpuColumnFalseCullCount;
    private long readbackPendingHighWater;
    private long hierarchyUpdateFrames;
    private long cameraOnlyFrames;
    private long hierarchyMaintenanceNs;
    private long cameraOnlyMaintenanceNs;
    private int lastVisibleColumns;
    private int lastCoarseVisiblePermille;
    private long lastEstimatedFineCandidateUpperBound;
    private int lastFlatFineCandidates;
    private boolean hardFailure;
    private boolean abandonedForDeviceShutdown;
    private boolean closed;

    public LargeSceneColumnHierarchyProbe(
            GpuDevice device,
            PersistentColumnHierarchy hierarchy,
            PersistentSectionScene sections,
            int effectiveRenderDistance) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        this.hierarchy = hierarchy;
        this.sections = sections;
        this.effectiveRenderDistance = effectiveRenderDistance;
        this.gpu = new VulkanLargeSceneColumnVisibilityProbe(device, hierarchy.capacity());

        long candidateBytes = Math.multiplyExact(
                (long) hierarchy.capacity(), VulkanLargeSceneColumnVisibilityProbe.CANDIDATE_BYTES);
        long outputBytes = Math.multiplyExact((long) hierarchy.capacity() + 1L, Integer.BYTES);
        this.uploadBuffer = device.createBuffer(
                () -> "Obsidian P4.2 column hierarchy upload",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC,
                candidateBytes);
        this.uploadView = uploadBuffer.map(false, true);
        this.uploadData = uploadView.data();
        this.readbackBuffer = device.createBuffer(
                () -> "Obsidian P4.2 column visibility readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                outputBytes);
        this.readbackView = readbackBuffer.map(true, false);
        this.readbackData = readbackView.data();
        this.nextSampleFrame = 0L;

        LOG.log(System.Logger.Level.INFO,
                "P4.2 shadow column hierarchy configured: renderDistance={0}, columnCapacity={1}, sectionRange=[{2},{3}), wordsPerColumn={4}, columnMetadataBytes={5}, occupancyBytes={6}, gpuCandidateBytes={7}, gpuOutputBytes={8}, hardMaxColumns={9}, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false.",
                effectiveRenderDistance, hierarchy.capacity(), hierarchy.minSectionY(), hierarchy.maxSectionY(),
                hierarchy.wordsPerColumn(), hierarchy.metadataBytes(), hierarchy.occupancyBytes(),
                candidateBytes, outputBytes, PersistentColumnHierarchy.HARD_MAX_COLUMNS);
    }

    public void afterWorldRender(GameRenderer renderer, long frameIndex) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;
        long startNs = System.nanoTime();

        pollReadback();
        if (hardFailure) return;

        boolean hierarchyWork = hierarchy.serial() != uploadedHierarchySerial
                || !auditCurrent()
                || snapshotBuilding;
        if (hierarchy.mutationFailures() != 0L || hierarchy.capacityFailures() != 0L) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.2 hierarchy mutation/capacity failure: mutationFailures={0}, capacityFailures={1}. P4.1/P3.10 ownership remains unchanged.",
                    hierarchy.mutationFailures(), hierarchy.capacityFailures());
            return;
        }

        processStructuralAuditBudget();
        if (hardFailure) return;

        if (hierarchy.serial() != uploadedHierarchySerial) {
            buildCandidateSnapshotBudget();
            hierarchyWork = true;
        }

        processOracleBudget();
        finishSampleIfReady();

        if (inFlightFence == null && !sampleActive && auditCurrent()) {
            if (uploadedHierarchySerial == hierarchy.serial() && uploadedCandidateCount >= 0
                    && (dispatches == 0 || frameIndex >= nextSampleFrame)) {
                dispatchSample(renderer, frameIndex, false);
            } else if (!snapshotBuilding && snapshotBuildSerial == hierarchy.serial()) {
                dispatchSample(renderer, frameIndex, true);
            }
        }

        long elapsed = System.nanoTime() - startNs;
        hierarchyMaintenanceNs += elapsed;
        if (hierarchyWork || hierarchy.serial() != uploadedHierarchySerial || !auditCurrent()) {
            hierarchyUpdateFrames++;
        } else {
            cameraOnlyFrames++;
            cameraOnlyMaintenanceNs += elapsed;
        }
    }

    public boolean hardFailure() {
        return hardFailure;
    }

    private boolean auditCurrent() {
        return auditComplete
                && auditHierarchySerial == hierarchy.serial()
                && auditSectionSerial == sections.serial();
    }

    private void resetStructuralAudit() {
        auditHierarchySerial = hierarchy.serial();
        auditSectionSerial = sections.serial();
        auditSectionCursor = 0;
        auditColumnCursor = 0;
        auditedLiveSections = 0;
        auditedColumnSections = 0;
        auditSectionsComplete = false;
        auditComplete = false;
        hierarchyAuditRuns++;
    }

    private void processStructuralAuditBudget() {
        if (!auditCurrent() && (auditHierarchySerial != hierarchy.serial()
                || auditSectionSerial != sections.serial())) {
            resetStructuralAudit();
        }
        if (auditComplete) return;

        int budget = STRUCTURAL_AUDIT_BUDGET;
        while (budget > 0 && !auditSectionsComplete) {
            if (auditSectionCursor >= sections.capacity()) {
                auditSectionsComplete = true;
                break;
            }
            int slot = auditSectionCursor++;
            budget--;
            if (!sections.isLive(slot)) continue;
            auditedLiveSections++;
            int columnSlot = hierarchy.columnSlot(sections.sectionX(slot), sections.sectionZ(slot));
            if (columnSlot < 0 || !hierarchy.isLive(columnSlot)
                    || !hierarchy.containsSection(columnSlot, sections.sectionY(slot))) {
                hierarchyAuditFailures++;
            }
        }

        while (budget > 0 && auditSectionsComplete && auditColumnCursor < hierarchy.capacity()) {
            int slot = auditColumnCursor++;
            budget--;
            if (!hierarchy.isLive(slot)) continue;
            auditedColumnSections += hierarchy.liveSectionCount(slot);
            if (!hierarchy.auditColumn(slot)) hierarchyAuditFailures++;
        }

        if (auditSectionsComplete && auditColumnCursor >= hierarchy.capacity()) {
            if (auditedLiveSections != sections.liveCount()
                    || auditedColumnSections != hierarchy.liveSectionMembership()
                    || hierarchy.liveSectionMembership() != sections.liveCount()) {
                hierarchyAuditFailures++;
            }
            auditComplete = true;
            if (hierarchyAuditFailures != 0L) {
                hardFailure = true;
                LOG.log(System.Logger.Level.ERROR,
                        "P4.2 structural hierarchy audit FAILED: failures={0}, sectionSceneLive={1}, auditedSections={2}, hierarchyMembership={3}, auditedColumnSections={4}.",
                        hierarchyAuditFailures, sections.liveCount(), auditedLiveSections,
                        hierarchy.liveSectionMembership(), auditedColumnSections);
            } else if (hierarchyAuditRuns <= 3 || hierarchyAuditRuns % 10 == 0) {
                LOG.log(System.Logger.Level.INFO,
                        "P4.2 structural hierarchy audit PASS: audit={0}, liveColumns={1}, liveSections={2}, columnMembership={3}, hierarchySerial={4}, sectionSerial={5}.",
                        hierarchyAuditRuns, hierarchy.liveCount(), sections.liveCount(),
                        hierarchy.liveSectionMembership(), auditHierarchySerial, auditSectionSerial);
            }
        }
    }

    private void buildCandidateSnapshotBudget() {
        if (inFlightFence != null) return;
        long serial = hierarchy.serial();
        if (!snapshotBuilding || snapshotBuildSerial != serial) {
            if (snapshotBuilding) snapshotBuildRestarts++;
            snapshotBuilding = true;
            snapshotBuildSerial = serial;
            snapshotSlotCursor = 0;
            snapshotCandidateCount = 0;
        }

        int budget = SNAPSHOT_COLUMN_BUDGET;
        while (budget-- > 0 && snapshotSlotCursor < hierarchy.capacity()) {
            int slot = snapshotSlotCursor++;
            if (!hierarchy.isLive(slot)) continue;
            if (!hierarchy.auditColumn(slot)) {
                hierarchyAuditFailures++;
                hardFailure = true;
                LOG.log(System.Logger.Level.ERROR,
                        "P4.2 candidate snapshot found invalid column slot {0}.", slot);
                return;
            }

            int byteOffset = Math.multiplyExact(
                    snapshotCandidateCount, VulkanLargeSceneColumnVisibilityProbe.CANDIDATE_BYTES);
            uploadData.putInt(byteOffset, hierarchy.chunkX(slot));
            uploadData.putInt(byteOffset + 4, hierarchy.minLiveSectionY(slot));
            uploadData.putInt(byteOffset + 8, hierarchy.chunkZ(slot));
            uploadData.putInt(byteOffset + 12, hierarchy.maxLiveSectionY(slot));
            uploadData.putInt(byteOffset + 16, hierarchy.identity(slot));
            uploadData.putInt(byteOffset + 20, hierarchy.liveSectionCount(slot));
            uploadData.putInt(byteOffset + 24, 0);
            uploadData.putInt(byteOffset + 28, 0);
            snapshotCandidateCount++;
        }

        if (snapshotSlotCursor >= hierarchy.capacity()) {
            snapshotBuilding = false;
        }
    }

    private void dispatchSample(GameRenderer renderer, long frameIndex, boolean uploadChanged) {
        if (inFlightFence != null || hardFailure) return;
        if (!auditCurrent()) return;
        if (!captureCamera(renderer)) return;

        int candidateCount = uploadChanged ? snapshotCandidateCount : uploadedCandidateCount;
        if (candidateCount < 0 || candidateCount > hierarchy.capacity()) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.2 invalid candidate count before dispatch: {0}", candidateCount);
            return;
        }
        if (uploadChanged && snapshotBuildSerial != hierarchy.serial()) return;

        CommandEncoder encoder = device.createCommandEncoder();
        try {
            if (uploadChanged && candidateCount > 0) {
                int bytes = Math.multiplyExact(
                        candidateCount, VulkanLargeSceneColumnVisibilityProbe.CANDIDATE_BYTES);
                encoder.copyToBuffer(uploadBuffer.slice(0L, bytes), gpu.candidateBuffer().slice(0L, bytes));
                snapshotUploads++;
                snapshotUploadBytes += bytes;
            }

            gpu.dispatch(encoder, candidateCount,
                    sampleCameraSectionX, sampleCameraSectionY, sampleCameraSectionZ,
                    sampleCameraLocalX, sampleCameraLocalY, sampleCameraLocalZ,
                    FRUSTUM_EPSILON, samplePlanes);
            encoder.copyToBuffer(gpu.outputSlice(), readbackBuffer.slice(0L, gpu.outputBytes()));
            GpuFence fence = encoder.createFence();
            encoder.submit();
            inFlightFence = fence;
        } catch (RuntimeException e) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.2 column visibility dispatch failed; P4.2 shadow validation disabled.", e);
            return;
        }

        if (uploadChanged) {
            uploadedHierarchySerial = snapshotBuildSerial;
            uploadedCandidateCount = candidateCount;
        }
        dispatches++;
        columnCandidatesTested += candidateCount;
        nextSampleFrame = frameIndex + SAMPLE_INTERVAL_FRAMES;
        beginOracleSample(candidateCount);
        if (readbackPendingHighWater < 1L) readbackPendingHighWater = 1L;
    }

    private boolean captureCamera(GameRenderer renderer) {
        CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null
                || camera.projectionMatrix == null || camera.viewRotationMatrix == null
                || camera.cullFrustum == null) {
            return false;
        }

        Matrix4f clip = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        int offset = 0;
        for (int i = 0; i < 6; i++) {
            clip.frustumPlane(i, planeScratch);
            cameraPlanes[offset++] = planeScratch.x;
            cameraPlanes[offset++] = planeScratch.y;
            cameraPlanes[offset++] = planeScratch.z;
            cameraPlanes[offset++] = planeScratch.w;
        }
        System.arraycopy(cameraPlanes, 0, samplePlanes, 0, cameraPlanes.length);

        Vec3 pos = camera.pos;
        sampleCameraSectionX = floorSection(pos.x);
        sampleCameraSectionY = floorSection(pos.y);
        sampleCameraSectionZ = floorSection(pos.z);
        sampleCameraLocalX = (float) (pos.x - sampleCameraSectionX * 16.0);
        sampleCameraLocalY = (float) (pos.y - sampleCameraSectionY * 16.0);
        sampleCameraLocalZ = (float) (pos.z - sampleCameraSectionZ * 16.0);

        sampleFrustum = new Frustum(
                new Matrix4f(camera.viewRotationMatrix),
                new Matrix4f(camera.projectionMatrix));
        sampleFrustum.prepare(pos.x, pos.y, pos.z);
        return true;
    }

    private void beginOracleSample(int candidateCount) {
        sampleActive = true;
        sampleHierarchySerial = hierarchy.serial();
        sampleSectionSerial = sections.serial();
        sampleCandidateCount = candidateCount;
        sampleOracleCursor = 0;
        sampleOracleComplete = false;
        sampleGpuComplete = false;
        sampleCpuVisible = 0;
        sampleCpuAmbiguous = 0;
        sampleCpuCulled = 0;
        sampleGpuVisibleCount = -1;
        sampleGpuDuplicateIds = 0;
        sampleEstimatedFineCandidateUpperBound = 0L;
        sampleFlatFineCandidates = sections.liveCount();
        expectedVisibleIds.clear();
        gpuVisibleIds.clear();
    }

    private void processOracleBudget() {
        if (!sampleActive || sampleOracleComplete) return;
        if (hierarchy.serial() != sampleHierarchySerial || sections.serial() != sampleSectionSerial) {
            abortStaleSample();
            return;
        }

        int budget = ORACLE_COLUMN_BUDGET;
        while (budget-- > 0 && sampleOracleCursor < hierarchy.capacity()) {
            int slot = sampleOracleCursor++;
            if (!hierarchy.isLive(slot)) continue;
            int classification = classifyCpu(slot);
            if (classification == -1) {
                sampleCpuCulled++;
            } else {
                if (classification == 0) sampleCpuAmbiguous++;
                else sampleCpuVisible++;
                expectedVisibleIds.add(hierarchy.identity(slot));
                sampleEstimatedFineCandidateUpperBound += hierarchy.liveSectionCount(slot);
            }
        }
        if (sampleOracleCursor >= hierarchy.capacity()) sampleOracleComplete = true;
    }

    /** -1 culled by Minecraft, 0 visible but boundary-ambiguous, 1 clearly visible. */
    private int classifyCpu(int slot) {
        int chunkX = hierarchy.chunkX(slot);
        int chunkZ = hierarchy.chunkZ(slot);
        int minY = hierarchy.minLiveSectionY(slot);
        int maxY = hierarchy.maxLiveSectionY(slot);

        double worldMinX = chunkX * 16.0;
        double worldMinY = minY * 16.0;
        double worldMinZ = chunkZ * 16.0;
        AABB bounds = new AABB(
                worldMinX, worldMinY, worldMinZ,
                worldMinX + 16.0, (maxY + 1) * 16.0, worldMinZ + 16.0);
        if (!sampleFrustum.isVisible(bounds)) return -1;

        float minX = (chunkX - sampleCameraSectionX) * 16.0f - sampleCameraLocalX;
        float minRelativeY = (minY - sampleCameraSectionY) * 16.0f - sampleCameraLocalY;
        float minZ = (chunkZ - sampleCameraSectionZ) * 16.0f - sampleCameraLocalZ;
        float maxX = minX + 16.0f;
        float maxRelativeY = (maxY + 1 - sampleCameraSectionY) * 16.0f - sampleCameraLocalY;
        float maxZ = minZ + 16.0f;

        boolean ambiguous = false;
        for (int i = 0; i < 6; i++) {
            int p = i * 4;
            float a = samplePlanes[p];
            float b = samplePlanes[p + 1];
            float c = samplePlanes[p + 2];
            float d = samplePlanes[p + 3];
            float x = a >= 0.0f ? maxX : minX;
            float y = b >= 0.0f ? maxRelativeY : minRelativeY;
            float z = c >= 0.0f ? maxZ : minZ;
            float maxDistance = a * x + b * y + c * z + d;
            if (maxDistance <= FRUSTUM_EPSILON) ambiguous = true;
        }
        return ambiguous ? 0 : 1;
    }

    private void pollReadback() {
        if (inFlightFence == null) return;
        boolean complete;
        try {
            complete = inFlightFence.awaitCompletion(0L);
        } catch (RuntimeException e) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR, "P4.2 readback fence polling failed.", e);
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
        if (visibleCount < 0 || visibleCount > sampleCandidateCount) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.2 GPU visible-column count outside snapshot bounds: visible={0}, candidates={1}.",
                    visibleCount, sampleCandidateCount);
            return;
        }

        sampleGpuVisibleCount = visibleCount;
        gpuVisibleIds.clear();
        for (int i = 0; i < visibleCount; i++) {
            int id = readbackData.getInt((i + 1) * Integer.BYTES);
            if (!gpuVisibleIds.add(id)) sampleGpuDuplicateIds++;
        }
        sampleGpuComplete = true;
    }

    private void finishSampleIfReady() {
        if (!sampleActive || !sampleOracleComplete || !sampleGpuComplete) return;

        int missing = 0;
        IntIterator expected = expectedVisibleIds.iterator();
        while (expected.hasNext()) {
            if (!gpuVisibleIds.contains(expected.nextInt())) missing++;
        }
        int unexpected = 0;
        IntIterator actual = gpuVisibleIds.iterator();
        while (actual.hasNext()) {
            if (!expectedVisibleIds.contains(actual.nextInt())) unexpected++;
        }

        boolean conservative = sampleGpuDuplicateIds == 0 && missing == 0;
        boolean exact = conservative && unexpected == 0
                && sampleGpuVisibleCount == expectedVisibleIds.size();
        samplesCompleted++;
        missingVisibleColumnIdentities += missing;
        unexpectedVisibleColumnIdentities += unexpected;
        duplicateVisibleColumnIdentities += sampleGpuDuplicateIds;
        gpuColumnFalseCullCount += missing;
        if (conservative) conservativeSamples++;
        if (exact) exactSamples++;

        if (!conservative) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.2 CPU/GPU coarse visibility mismatch: columns={0}, cpuVisible={1}, cpuAmbiguous={2}, cpuCulled={3}, gpuVisible={4}, missing={5}, unexpectedSafeExtras={6}, duplicate={7}.",
                    sampleCandidateCount, sampleCpuVisible, sampleCpuAmbiguous, sampleCpuCulled,
                    sampleGpuVisibleCount, missing, unexpected, sampleGpuDuplicateIds);
        } else {
            lastVisibleColumns = sampleGpuVisibleCount;
            lastCoarseVisiblePermille = sampleCandidateCount == 0
                    ? 0 : (int) Math.min(1000L, (long) sampleGpuVisibleCount * 1000L / sampleCandidateCount);
            lastEstimatedFineCandidateUpperBound = sampleEstimatedFineCandidateUpperBound;
            lastFlatFineCandidates = sampleFlatFineCandidates;
            if (samplesCompleted <= 3 || samplesCompleted % 10 == 0) {
                LOG.log(System.Logger.Level.INFO,
                        "P4.2 coarse column visibility sample PASS: sample={0}, columns={1}, cpuVisible={2}, boundaryAmbiguous={3}, cpuCulled={4}, gpuVisible={5}, safeExtraGpuColumns={6}, liveColumns={7}, liveSections={8}, estimatedFineCandidateUpperBound={9}, flatFineCandidateCount={10}, coarseVisiblePermille={11}, cameraOnlyFullHierarchyScan=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false.",
                        samplesCompleted, sampleCandidateCount, sampleCpuVisible, sampleCpuAmbiguous,
                        sampleCpuCulled, sampleGpuVisibleCount, unexpected, hierarchy.liveCount(),
                        sections.liveCount(), sampleEstimatedFineCandidateUpperBound,
                        sampleFlatFineCandidates, lastCoarseVisiblePermille);
            }
        }
        sampleActive = false;
        sampleFrustum = null;
    }

    private void abortStaleSample() {
        samplesAbortedStale++;
        sampleActive = false;
        sampleOracleComplete = false;
        sampleGpuComplete = false;
        sampleFrustum = null;
        expectedVisibleIds.clear();
        gpuVisibleIds.clear();
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
                        "P4.2 shutdown fence wait failed; leaving P4.2 buffers for device shutdown.", e);
            }
            if (inFlightFence != null || System.nanoTime() - start >= SHUTDOWN_WAIT_NS) {
                abandonedForDeviceShutdown = true;
            }
        }

        LOG.log(System.Logger.Level.INFO,
                "P4.2 final column hierarchy evidence: configured=true, renderDistance={0}, columnCapacity={1}, liveColumns={2}, highWaterColumns={3}, liveSections={4}, columnMembership={5}, sectionsPerLiveColumnPermille={6}, columnMetadataBytes={7}, occupancyBytes={8}, wordsPerColumn={9}, columnInstalls={10}, columnRemovals={11}, slotReuses={12}, membershipAdds={13}, membershipRemovals={14}, capacityFailures={15}, mutationFailures={16}, hierarchyAuditRuns={17}, hierarchyAuditFailures={18}, snapshotBuildRestarts={19}, snapshotUploads={20}, snapshotUploadBytes={21}, dispatches={22}, columnCandidatesTested={23}, samplesCompleted={24}, samplesAbortedStale={25}, conservativeSamples={26}, exactSamples={27}, missingVisibleColumns={28}, unexpectedVisibleColumns={29}, duplicateVisibleColumns={30}, gpuColumnFalseCullCount={31}, readbackPendingHighWater={32}, lastVisibleColumns={33}, lastCoarseVisiblePermille={34}, lastEstimatedFineCandidateUpperBound={35}, lastFlatFineCandidateCount={36}, hierarchyMutationCalls={37}, hierarchyMutationNs={38}, hierarchyUpdateFrames={39}, cameraOnlyFrames={40}, hierarchyMaintenanceNs={41}, cameraOnlyHierarchyMaintenanceNs={42}, hardFailure={43}, abandonedForDeviceShutdown={44}, cameraOnlyFullHierarchyScan=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false, commandCompactionEnabled=false, temporalVisibilityEnabled=false, hizEnabled=false.",
                effectiveRenderDistance, hierarchy.capacity(), hierarchy.liveCount(), hierarchy.highWater(),
                sections.liveCount(), hierarchy.liveSectionMembership(),
                hierarchy.liveCount() == 0 ? 0 : (long) hierarchy.liveSectionMembership() * 1000L / hierarchy.liveCount(),
                hierarchy.metadataBytes(), hierarchy.occupancyBytes(), hierarchy.wordsPerColumn(),
                hierarchy.installs(), hierarchy.removals(), hierarchy.slotReuses(),
                hierarchy.sectionMembershipAdds(), hierarchy.sectionMembershipRemovals(),
                hierarchy.capacityFailures(), hierarchy.mutationFailures(),
                hierarchyAuditRuns, hierarchyAuditFailures, snapshotBuildRestarts,
                snapshotUploads, snapshotUploadBytes, dispatches, columnCandidatesTested,
                samplesCompleted, samplesAbortedStale, conservativeSamples, exactSamples,
                missingVisibleColumnIdentities, unexpectedVisibleColumnIdentities,
                duplicateVisibleColumnIdentities, gpuColumnFalseCullCount, readbackPendingHighWater,
                lastVisibleColumns, lastCoarseVisiblePermille, lastEstimatedFineCandidateUpperBound,
                lastFlatFineCandidates, hierarchy.mutationCalls(), hierarchy.mutationNs(),
                hierarchyUpdateFrames, cameraOnlyFrames, hierarchyMaintenanceNs,
                cameraOnlyMaintenanceNs, hardFailure, abandonedForDeviceShutdown);

        if (!abandonedForDeviceShutdown) {
            readbackView.close();
            readbackBuffer.close();
            uploadView.close();
            uploadBuffer.close();
            gpu.close();
        }
    }

    private static int floorSection(double coordinate) {
        return (int) Math.floor(coordinate / 16.0);
    }
}
