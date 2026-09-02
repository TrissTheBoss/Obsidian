package dev.obsidian.render.visibility;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.obsidian.render.vulkan.VulkanLargeSceneVisibilityProbe;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;

/**
 * Phase 4 P4.1 shadow-only persistent large-scene visibility validation.
 *
 * <p>This subsystem cannot suppress or draw terrain. P3.10 remains the visual
 * authority while this class proves event-driven large-scene metadata and GPU
 * frustum compaction at real render-distance scale.</p>
 */
public final class LargeSceneVisibilityProbe implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger("Obsidian/LargeSceneVisibility");

    private static final int EVENT_BUDGET = 8_192;
    private static final int RESYNC_COLUMN_BUDGET = 128;
    private static final int SNAPSHOT_SLOT_BUDGET = 16_384;
    private static final int ORACLE_SLOT_BUDGET = 8_192;
    private static final int SAMPLE_INTERVAL_FRAMES = 60;
    private static final long SHUTDOWN_WAIT_NS = 2_000_000_000L;
    private static final float FRUSTUM_EPSILON = 1.0e-3f;

    private final GpuDevice device;
    private final byte[] eventTypes = new byte[EVENT_BUDGET];
    private final long[] eventKeys = new long[EVENT_BUDGET];
    private final float[] cameraPlanes = new float[24];
    private final Vector4f planeScratch = new Vector4f();
    private final IntOpenHashSet expectedVisibleIds = new IntOpenHashSet();
    private final IntOpenHashSet gpuVisibleIds = new IntOpenHashSet();

    private ClientLevel configuredLevel;
    private PersistentSectionScene scene;
    private VulkanLargeSceneVisibilityProbe gpu;
    private GpuBuffer uploadBuffer;
    private GpuBufferSlice.MappedView uploadView;
    private ByteBuffer uploadData;
    private GpuBuffer readbackBuffer;
    private GpuBufferSlice.MappedView readbackView;
    private ByteBuffer readbackData;
    private GpuFence inFlightFence;

    private int effectiveRenderDistance = -1;
    private int cacheRadius;
    private int minSectionY;
    private int maxSectionY;
    private int sectionCount;
    private int capacity;
    private boolean capacityDisabled;
    private boolean abandonedForDeviceShutdown;

    private boolean fullResync;
    private int resyncCenterX;
    private int resyncCenterZ;
    private int resyncDiameter;
    private int resyncIndex;
    private int resyncTotal;

    private long uploadedSceneSerial;
    private int uploadedCandidateCount;
    private boolean snapshotBuilding;
    private long snapshotBuildSerial;
    private int snapshotSlotCursor;
    private int snapshotCandidateCount;

    private boolean sampleActive;
    private long sampleSceneSerial;
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

    private long frameIndex;
    private long nextSampleFrame;
    private long worldChanges;
    private long resourceReloads;
    private long chunkLoads;
    private long chunkUnloads;
    private long sectionBecameEmpty;
    private long sectionBecameNonempty;
    private long fullResyncs;
    private long resyncColumnsProbed;
    private long snapshotBuildRestarts;
    private long snapshotUploads;
    private long snapshotUploadBytes;
    private long dispatches;
    private long candidatesTested;
    private long samplesCompleted;
    private long samplesAbortedStaleScene;
    private long exactSamples;
    private long missingVisibleIdentities;
    private long unexpectedVisibleIdentities;
    private long duplicateVisibleIdentities;
    private long gpuFalseCullCount;
    private long gpuReadbackPendingHighWater;
    private long sceneMaintenanceNs;
    private long cameraOnlyMaintenanceNs;
    private long sceneUpdateFrames;
    private long cameraOnlyFrames;
    private boolean hardFailure;
    private boolean closed;
    private boolean firstActiveLog;

    public LargeSceneVisibilityProbe(GpuDevice device) {
        RenderSystem.assertOnRenderThread();
        this.device = device;
        if (!device.getDeviceInfo().features().persistentMapping()) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.1 requires persistent mapping for bounded upload/readback validation; shadow visibility disabled.");
        }
    }

    public void afterWorldRender(GameRenderer renderer) {
        RenderSystem.assertOnRenderThread();
        if (closed || hardFailure) return;
        frameIndex++;
        long startNs = System.nanoTime();

        pollReadback();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) return;

        boolean sceneChangedThisFrame = false;
        if (!configurationMatches(level, minecraft.options.getEffectiveRenderDistance())) {
            if (inFlightFence != null) {
                cameraOnlyFrames++;
                cameraOnlyMaintenanceNs += System.nanoTime() - startNs;
                return;
            }
            configure(level, minecraft.options.getEffectiveRenderDistance());
            sceneChangedThisFrame = true;
        }
        if (capacityDisabled || scene == null || gpu == null) return;

        if (LargeSceneLifecycleEvents.consumeOverflowed()) {
            startFullResync(minecraft);
            sceneChangedThisFrame = true;
            LOG.log(System.Logger.Level.WARNING,
                    "P4.1 lifecycle ring overflow observed; forcing bounded full scene resync. overflowEvents={0}",
                    LargeSceneLifecycleEvents.overflowEvents());
        }

        int drained = LargeSceneLifecycleEvents.drainTo(eventTypes, eventKeys, EVENT_BUDGET);
        if (drained > 0) {
            sceneChangedThisFrame |= processEvents(level, minecraft, drained);
        }
        if (fullResync) {
            sceneChangedThisFrame |= processFullResync(level);
        }

        if (scene.serial() != uploadedSceneSerial) {
            buildCandidateSnapshotBudget();
            sceneChangedThisFrame = true;
        }

        processOracleBudget();
        finishSampleIfReady();

        if (inFlightFence == null && !sampleActive) {
            if (uploadedSceneSerial == scene.serial() && uploadedCandidateCount >= 0
                    && (dispatches == 0 || frameIndex >= nextSampleFrame)) {
                dispatchSample(renderer, false);
            } else if (!snapshotBuilding && snapshotBuildSerial == scene.serial()) {
                // Completed snapshot waiting to be uploaded and dispatched.
                dispatchSample(renderer, true);
            }
        }

        long elapsed = System.nanoTime() - startNs;
        sceneMaintenanceNs += elapsed;
        if (sceneChangedThisFrame) {
            sceneUpdateFrames++;
        } else {
            cameraOnlyFrames++;
            cameraOnlyMaintenanceNs += elapsed;
        }
    }

    private boolean configurationMatches(ClientLevel level, int renderDistance) {
        return configuredLevel == level
                && effectiveRenderDistance == renderDistance
                && minSectionY == level.getMinSectionY()
                && maxSectionY == level.getMaxSectionY();
    }

    private void configure(ClientLevel level, int renderDistance) {
        closeGpuResources();
        configuredLevel = level;
        effectiveRenderDistance = renderDistance;
        cacheRadius = Math.max(2, renderDistance) + 3; // exact ClientChunkCache.calculateStorageRange(26.2)
        minSectionY = level.getMinSectionY();
        maxSectionY = level.getMaxSectionY();
        sectionCount = level.getSectionsCount();
        long diameter = Math.addExact(Math.multiplyExact((long) cacheRadius, 2L), 1L);
        long required = Math.multiplyExact(Math.multiplyExact(diameter, diameter), sectionCount);
        if (required <= 0L || required > PersistentSectionScene.HARD_MAX_SLOTS) {
            capacityDisabled = true;
            capacity = required > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) required;
            LOG.log(System.Logger.Level.WARNING,
                    "P4.1 persistent scene disabled by explicit hard bound: effectiveRenderDistance={0}, cacheRadius={1}, sections={2}, requiredSlots={3}, hardMax={4}. P3.10 remains authoritative.",
                    renderDistance, cacheRadius, sectionCount, required, PersistentSectionScene.HARD_MAX_SLOTS);
            return;
        }

        capacityDisabled = false;
        capacity = (int) required;
        scene = new PersistentSectionScene(capacity);
        gpu = new VulkanLargeSceneVisibilityProbe(device, capacity);
        long candidateBytes = Math.multiplyExact((long) capacity, VulkanLargeSceneVisibilityProbe.CANDIDATE_BYTES);
        long outputBytes = Math.multiplyExact((long) capacity + 1L, Integer.BYTES);
        uploadBuffer = device.createBuffer(
                () -> "Obsidian P4.1 persistent scene upload",
                GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_SRC,
                candidateBytes);
        uploadView = uploadBuffer.map(false, true);
        uploadData = uploadView.data();
        readbackBuffer = device.createBuffer(
                () -> "Obsidian P4.1 visibility readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                outputBytes);
        readbackView = readbackBuffer.map(true, false);
        readbackData = readbackView.data();
        uploadedSceneSerial = 0L;
        uploadedCandidateCount = -1;
        snapshotBuilding = false;
        sampleActive = false;
        nextSampleFrame = frameIndex;
        startFullResync(Minecraft.getInstance());

        LOG.log(System.Logger.Level.INFO,
                "P4.1 shadow large-scene visibility configured: effectiveRenderDistance={0}, exactCacheRadius={1}, sectionRange=[{2},{3}), sectionCount={4}, candidateCapacity={5}, primitiveMetadataBytes={6}, gpuCandidateBytes={7}, gpuOutputBytes={8}, hardMaxSlots={9}, nativeGraphicsExpansion=false.",
                effectiveRenderDistance, cacheRadius, minSectionY, maxSectionY, sectionCount,
                capacity, scene.metadataBytes(), candidateBytes, outputBytes, PersistentSectionScene.HARD_MAX_SLOTS);
        firstActiveLog = true;
    }

    private boolean processEvents(ClientLevel level, Minecraft minecraft, int count) {
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            byte type = eventTypes[i];
            long key = eventKeys[i];
            switch (type) {
                case LargeSceneLifecycleEvents.WORLD_CHANGED -> {
                    worldChanges++;
                    scene.clear();
                    startFullResync(minecraft);
                    changed = true;
                }
                case LargeSceneLifecycleEvents.RESOURCE_RELOADED -> resourceReloads++;
                case LargeSceneLifecycleEvents.CHUNK_LOADED -> {
                    chunkLoads++;
                    changed |= scanLoadedColumn(level,
                            LargeSceneLifecycleEvents.chunkX(key), LargeSceneLifecycleEvents.chunkZ(key));
                }
                case LargeSceneLifecycleEvents.CHUNK_UNLOADED -> {
                    chunkUnloads++;
                    int removed = scene.removeColumn(
                            LargeSceneLifecycleEvents.chunkX(key), LargeSceneLifecycleEvents.chunkZ(key),
                            minSectionY, maxSectionY);
                    changed |= removed > 0;
                }
                case LargeSceneLifecycleEvents.SECTION_BECAME_EMPTY -> {
                    sectionBecameEmpty++;
                    net.minecraft.core.SectionPos pos = net.minecraft.core.SectionPos.of(key);
                    changed |= scene.remove(pos.x(), pos.y(), pos.z());
                }
                case LargeSceneLifecycleEvents.SECTION_BECAME_NONEMPTY -> {
                    sectionBecameNonempty++;
                    net.minecraft.core.SectionPos pos = net.minecraft.core.SectionPos.of(key);
                    if (!scene.add(pos.x(), pos.y(), pos.z())) capacityFailure();
                    else changed = true;
                }
                default -> { }
            }
            eventTypes[i] = 0;
            eventKeys[i] = 0L;
        }
        return changed;
    }

    private void startFullResync(Minecraft minecraft) {
        if (scene == null || minecraft.player == null) return;
        int centerX = Math.floorDiv(minecraft.player.blockPosition().getX(), 16);
        int centerZ = Math.floorDiv(minecraft.player.blockPosition().getZ(), 16);
        resyncCenterX = centerX;
        resyncCenterZ = centerZ;
        resyncDiameter = cacheRadius * 2 + 1;
        resyncIndex = 0;
        resyncTotal = resyncDiameter * resyncDiameter;
        fullResync = true;
        fullResyncs++;
    }

    private boolean processFullResync(ClientLevel level) {
        boolean changed = false;
        int budget = RESYNC_COLUMN_BUDGET;
        while (budget-- > 0 && resyncIndex < resyncTotal) {
            int localX = resyncIndex % resyncDiameter;
            int localZ = resyncIndex / resyncDiameter;
            int chunkX = resyncCenterX + localX - cacheRadius;
            int chunkZ = resyncCenterZ + localZ - cacheRadius;
            changed |= scanLoadedColumn(level, chunkX, chunkZ);
            resyncColumnsProbed++;
            resyncIndex++;
        }
        if (resyncIndex >= resyncTotal) {
            fullResync = false;
            LOG.log(System.Logger.Level.INFO,
                    "P4.1 bounded scene resync complete: liveSections={0}, loadedChunks={1}, probedColumns={2}, sceneSerial={3}.",
                    scene.liveCount(), level.getChunkSource().getLoadedChunksCount(), resyncTotal, scene.serial());
        }
        return changed;
    }

    private boolean scanLoadedColumn(ClientLevel level, int chunkX, int chunkZ) {
        ClientChunkCache cache = level.getChunkSource();
        LevelChunk chunk = cache.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) return false;
        boolean changed = false;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            int y = chunk.getSectionYFromSectionIndex(i);
            if (section.hasOnlyAir()) {
                changed |= scene.remove(chunkX, y, chunkZ);
            } else {
                int before = scene.liveCount();
                if (!scene.add(chunkX, y, chunkZ)) capacityFailure();
                changed |= scene.liveCount() != before;
            }
        }
        return changed;
    }

    private void capacityFailure() {
        hardFailure = true;
        LOG.log(System.Logger.Level.ERROR,
                "P4.1 persistent scene capacity exhausted; shadow validation disabled rather than dropping a live section. live={0}, capacity={1}, failures={2}. P3.10 rendering remains unchanged.",
                scene.liveCount(), scene.capacity(), scene.capacityFailures());
    }

    private void buildCandidateSnapshotBudget() {
        if (inFlightFence != null || scene == null || uploadData == null) return;
        long serial = scene.serial();
        if (!snapshotBuilding || snapshotBuildSerial != serial) {
            if (snapshotBuilding) snapshotBuildRestarts++;
            snapshotBuilding = true;
            snapshotBuildSerial = serial;
            snapshotSlotCursor = 0;
            snapshotCandidateCount = 0;
        }

        int budget = SNAPSHOT_SLOT_BUDGET;
        while (budget-- > 0 && snapshotSlotCursor < scene.capacity()) {
            int slot = snapshotSlotCursor++;
            if (!scene.isLive(slot)) continue;
            int byteOffset = snapshotCandidateCount * VulkanLargeSceneVisibilityProbe.CANDIDATE_BYTES;
            uploadData.putInt(byteOffset, scene.sectionX(slot));
            uploadData.putInt(byteOffset + 4, scene.sectionY(slot));
            uploadData.putInt(byteOffset + 8, scene.sectionZ(slot));
            uploadData.putInt(byteOffset + 12, scene.identity(slot));
            snapshotCandidateCount++;
        }

        if (snapshotSlotCursor >= scene.capacity()) {
            if (scene.serial() != snapshotBuildSerial) {
                snapshotBuildRestarts++;
                snapshotBuilding = false;
                return;
            }
            snapshotBuilding = false;
        }
    }

    private void dispatchSample(GameRenderer renderer, boolean uploadChanged) {
        if (gpu == null || readbackBuffer == null || scene == null || inFlightFence != null) return;
        if (uploadChanged) {
            if (snapshotBuilding || snapshotBuildSerial != scene.serial()) return;
        } else if (uploadedSceneSerial != scene.serial()) {
            return;
        }
        if (!captureCamera(renderer)) return;

        int candidateCount = uploadChanged ? snapshotCandidateCount : uploadedCandidateCount;
        if (candidateCount < 0 || candidateCount > capacity) return;

        CommandEncoder encoder = device.createCommandEncoder();
        try {
            if (uploadChanged && candidateCount > 0) {
                int bytes = Math.multiplyExact(candidateCount, VulkanLargeSceneVisibilityProbe.CANDIDATE_BYTES);
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
                    "P4.1 visibility dispatch failed; shadow validation disabled and P3.10 remains authoritative.", e);
            return;
        }

        if (uploadChanged) {
            uploadedSceneSerial = snapshotBuildSerial;
            uploadedCandidateCount = candidateCount;
        }
        dispatches++;
        candidatesTested += candidateCount;
        nextSampleFrame = frameIndex + SAMPLE_INTERVAL_FRAMES;
        beginOracleSample(candidateCount);
        if (gpuReadbackPendingHighWater < 1L) gpuReadbackPendingHighWater = 1L;
    }

    private boolean captureCamera(GameRenderer renderer) {
        CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null
                || camera.projectionMatrix == null || camera.viewRotationMatrix == null) return false;
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
        return true;
    }

    private void beginOracleSample(int candidateCount) {
        sampleActive = true;
        sampleSceneSerial = scene.serial();
        sampleCandidateCount = candidateCount;
        sampleOracleCursor = 0;
        sampleOracleComplete = false;
        sampleGpuComplete = false;
        sampleCpuVisible = 0;
        sampleCpuAmbiguous = 0;
        sampleCpuCulled = 0;
        sampleGpuVisibleCount = -1;
        sampleGpuDuplicateIds = 0;
        expectedVisibleIds.clear();
        gpuVisibleIds.clear();
    }

    private void processOracleBudget() {
        if (!sampleActive || sampleOracleComplete || scene == null) return;
        if (scene.serial() != sampleSceneSerial) {
            abortStaleSample();
            return;
        }
        int budget = ORACLE_SLOT_BUDGET;
        while (budget-- > 0 && sampleOracleCursor < scene.capacity()) {
            int slot = sampleOracleCursor++;
            if (!scene.isLive(slot)) continue;
            int classification = classifyCpu(scene.sectionX(slot), scene.sectionY(slot), scene.sectionZ(slot));
            if (classification == -1) {
                sampleCpuCulled++;
            } else {
                if (classification == 0) sampleCpuAmbiguous++;
                else sampleCpuVisible++;
                expectedVisibleIds.add(scene.identity(slot));
            }
        }
        if (sampleOracleCursor >= scene.capacity()) sampleOracleComplete = true;
    }

    /** -1 culled, 0 boundary-ambiguous/conservatively visible, 1 clearly visible. */
    private int classifyCpu(int sectionX, int sectionY, int sectionZ) {
        float minX = (sectionX - sampleCameraSectionX) * 16.0f - sampleCameraLocalX;
        float minY = (sectionY - sampleCameraSectionY) * 16.0f - sampleCameraLocalY;
        float minZ = (sectionZ - sampleCameraSectionZ) * 16.0f - sampleCameraLocalZ;
        float maxX = minX + 16.0f;
        float maxY = minY + 16.0f;
        float maxZ = minZ + 16.0f;
        boolean ambiguous = false;
        for (int i = 0; i < 6; i++) {
            int p = i * 4;
            float a = samplePlanes[p];
            float b = samplePlanes[p + 1];
            float c = samplePlanes[p + 2];
            float d = samplePlanes[p + 3];
            float x = a >= 0.0f ? maxX : minX;
            float y = b >= 0.0f ? maxY : minY;
            float z = c >= 0.0f ? maxZ : minZ;
            float maxDistance = a * x + b * y + c * z + d;
            if (maxDistance < -FRUSTUM_EPSILON) return -1;
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
            LOG.log(System.Logger.Level.ERROR, "P4.1 readback fence polling failed.", e);
            return;
        }
        if (!complete) return;
        inFlightFence.close();
        inFlightFence = null;
        if (!sampleActive || scene == null || scene.serial() != sampleSceneSerial) {
            if (sampleActive) abortStaleSample();
            return;
        }

        int visibleCount = readbackData.getInt(0);
        if (visibleCount < 0 || visibleCount > sampleCandidateCount) {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.1 GPU visible count outside snapshot bounds: visible={0}, candidates={1}.",
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

        int expectedCount = expectedVisibleIds.size();
        boolean exact = sampleGpuDuplicateIds == 0
                && missing == 0
                && unexpected == 0
                && sampleGpuVisibleCount == expectedCount;
        samplesCompleted++;
        missingVisibleIdentities += missing;
        unexpectedVisibleIdentities += unexpected;
        duplicateVisibleIdentities += sampleGpuDuplicateIds;
        gpuFalseCullCount += missing;
        if (exact) exactSamples++;
        else {
            hardFailure = true;
            LOG.log(System.Logger.Level.ERROR,
                    "P4.1 CPU/GPU visibility mismatch: candidates={0}, cpuVisible={1}, cpuAmbiguous={2}, cpuCulled={3}, expectedGpuVisible={4}, gpuVisible={5}, missing={6}, unexpected={7}, duplicate={8}.",
                    sampleCandidateCount, sampleCpuVisible, sampleCpuAmbiguous, sampleCpuCulled,
                    expectedCount, sampleGpuVisibleCount, missing, unexpected, sampleGpuDuplicateIds);
        }

        if (exact && (samplesCompleted <= 3 || samplesCompleted % 10 == 0)) {
            LOG.log(System.Logger.Level.INFO,
                    "P4.1 shadow visibility sample PASS: sample={0}, candidates={1}, cpuVisible={2}, boundaryAmbiguous={3}, cpuCulled={4}, gpuVisible={5}, liveScene={6}, renderDistance={7}, cameraOnlyFullSceneScan=false, nativeGraphicsExpansion=false.",
                    samplesCompleted, sampleCandidateCount, sampleCpuVisible, sampleCpuAmbiguous,
                    sampleCpuCulled, sampleGpuVisibleCount, scene.liveCount(), effectiveRenderDistance);
        }
        sampleActive = false;
    }

    private void abortStaleSample() {
        samplesAbortedStaleScene++;
        sampleActive = false;
        sampleOracleComplete = false;
        sampleGpuComplete = false;
        expectedVisibleIds.clear();
        gpuVisibleIds.clear();
    }

    private void closeGpuResources() {
        if (inFlightFence != null) return;
        if (readbackView != null) { readbackView.close(); readbackView = null; readbackData = null; }
        if (readbackBuffer != null) { readbackBuffer.close(); readbackBuffer = null; }
        if (uploadView != null) { uploadView.close(); uploadView = null; uploadData = null; }
        if (uploadBuffer != null) { uploadBuffer.close(); uploadBuffer = null; }
        if (gpu != null) { gpu.close(); gpu = null; }
        scene = null;
        uploadedSceneSerial = 0L;
        uploadedCandidateCount = -1;
    }

    public boolean hardFailure() { return hardFailure; }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        closed = true;
        if (inFlightFence != null) {
            long remaining = SHUTDOWN_WAIT_NS;
            long start = System.nanoTime();
            try {
                boolean complete = inFlightFence.awaitCompletion(remaining);
                if (complete) {
                    inFlightFence.close();
                    inFlightFence = null;
                }
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "P4.1 shutdown fence wait failed; leaving in-flight shadow buffers for Minecraft device shutdown.", e);
            }
            if (inFlightFence != null || System.nanoTime() - start >= SHUTDOWN_WAIT_NS) {
                abandonedForDeviceShutdown = true;
            }
        }

        LOG.log(System.Logger.Level.INFO,
                "P4.1 final shadow visibility evidence: configured={0}, capacityDisabled={1}, capacity={2}, live={3}, highWater={4}, primitiveMetadataBytes={5}, installs={6}, removals={7}, slotReuses={8}, capacityFailures={9}, lifecycleRecorded={10}, lifecycleOverflow={11}, worldChanges={12}, resourceReloads={13}, chunkLoads={14}, chunkUnloads={15}, sectionEmpty={16}, sectionNonempty={17}, fullResyncs={18}, resyncColumnsProbed={19}, snapshotBuildRestarts={20}, snapshotUploads={21}, snapshotUploadBytes={22}, dispatches={23}, candidatesTested={24}, samplesCompleted={25}, samplesAbortedStale={26}, exactSamples={27}, missingVisible={28}, unexpectedVisible={29}, duplicateVisible={30}, gpuFalseCullCount={31}, readbackPendingHighWater={32}, sceneUpdateFrames={33}, cameraOnlyFrames={34}, sceneMaintenanceNs={35}, cameraOnlyMaintenanceNs={36}, hardFailure={37}, abandonedForDeviceShutdown={38}, cameraOnlyFullSceneScan=false, productionDrawOwnershipChanged=false, nativeGraphicsExpansion=false.",
                firstActiveLog, capacityDisabled, capacity,
                scene == null ? 0 : scene.liveCount(), scene == null ? 0 : scene.highWater(),
                scene == null ? 0L : scene.metadataBytes(), scene == null ? 0L : scene.installs(),
                scene == null ? 0L : scene.removals(), scene == null ? 0L : scene.slotReuses(),
                scene == null ? 0L : scene.capacityFailures(), LargeSceneLifecycleEvents.recordedEvents(),
                LargeSceneLifecycleEvents.overflowEvents(), worldChanges, resourceReloads, chunkLoads, chunkUnloads,
                sectionBecameEmpty, sectionBecameNonempty, fullResyncs, resyncColumnsProbed,
                snapshotBuildRestarts, snapshotUploads, snapshotUploadBytes, dispatches, candidatesTested,
                samplesCompleted, samplesAbortedStaleScene, exactSamples, missingVisibleIdentities,
                unexpectedVisibleIdentities, duplicateVisibleIdentities, gpuFalseCullCount,
                gpuReadbackPendingHighWater, sceneUpdateFrames, cameraOnlyFrames,
                sceneMaintenanceNs, cameraOnlyMaintenanceNs, hardFailure, abandonedForDeviceShutdown);

        if (!abandonedForDeviceShutdown) closeGpuResources();
    }

    private static int floorSection(double coordinate) {
        return (int) Math.floor(coordinate / 16.0);
    }
}
