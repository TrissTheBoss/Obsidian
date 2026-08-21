package dev.obsidian.render.terrain;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Render-thread P2.5 correctness capture of the exact quads emitted by vanilla
 * {@link ModelBlockRenderer} for one immutable {@link SectionSnapshot}.
 *
 * <p>The P2.1 {@link ReferenceFaceMesh} intentionally remains the independent
 * canonical cube-face oracle. This companion representation broadens semantic
 * coverage by freezing vanilla's already culled, offset, lit, AO/shaded and
 * tinted baked quads into primitive renderer-owned arrays. No live Minecraft
 * world/model/light/resource object is retained after capture.</p>
 */
public final class SectionBakedQuadSnapshot {
    public static final byte LAYER_SOLID = 0;
    public static final byte LAYER_CUTOUT = 1;
    public static final int VERTICES_PER_QUAD = 4;
    public static final int MAX_QUADS = 24_000;

    private static final int REJECT_NONE = 0;
    private static final int REJECT_MATERIAL = 1;
    private static final int REJECT_TRANSLUCENT = 2;
    private static final int REJECT_ATLAS = 3;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Immutable renderer-owned per-quad material identity. */
    public record MaterialIdentity(
            String atlas,
            String sprite,
            byte layer,
            int materialFlags,
            int tintIndex,
            boolean shade,
            int lightEmission,
            boolean animated) {
    }

    private record PendingQuad(
            float[] positions,
            float[] uvs,
            int[] colors,
            int[] lights,
            byte direction,
            MaterialIdentity material) {
    }

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final float[] positions;
    private final float[] uvs;
    private final int[] exactArgbColors;
    private final int[] packedLights;
    private final int[] materialIds;
    private final int[] sourceBlocks;
    private final int[] stateIds;
    private final byte[] directions;
    private final byte[] layers;
    private final MaterialIdentity[] materials;
    private final long sourceSnapshotFingerprint;
    private final long resourceEpoch;
    private final long fingerprint;
    private final long captureTimeNs;

    private final int modelBlocksScanned;
    private final int acceptedBlocks;
    private final int noVisibleQuadBlocks;
    private final int rejectedLeavesBlocks;
    private final int rejectedFluidBlocks;
    private final int rejectedBlockEntityBlocks;
    private final int rejectedMissingModelBlocks;
    private final int rejectedMaterialBlocks;
    private final int rejectedTranslucentBlocks;
    private final int rejectedAtlasBlocks;
    private final int solidQuads;
    private final int cutoutQuads;
    private final int tintedQuads;
    private final int minBlockLight;
    private final int maxBlockLight;
    private final int minSkyLight;
    private final int maxSkyLight;

    private SectionBakedQuadSnapshot(
            int sectionX,
            int sectionY,
            int sectionZ,
            float[] positions,
            float[] uvs,
            int[] exactArgbColors,
            int[] packedLights,
            int[] materialIds,
            int[] sourceBlocks,
            int[] stateIds,
            byte[] directions,
            byte[] layers,
            MaterialIdentity[] materials,
            long sourceSnapshotFingerprint,
            long resourceEpoch,
            long fingerprint,
            long captureTimeNs,
            int modelBlocksScanned,
            int acceptedBlocks,
            int noVisibleQuadBlocks,
            int rejectedLeavesBlocks,
            int rejectedFluidBlocks,
            int rejectedBlockEntityBlocks,
            int rejectedMissingModelBlocks,
            int rejectedMaterialBlocks,
            int rejectedTranslucentBlocks,
            int rejectedAtlasBlocks,
            int solidQuads,
            int cutoutQuads,
            int tintedQuads,
            int minBlockLight,
            int maxBlockLight,
            int minSkyLight,
            int maxSkyLight) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.positions = positions;
        this.uvs = uvs;
        this.exactArgbColors = exactArgbColors;
        this.packedLights = packedLights;
        this.materialIds = materialIds;
        this.sourceBlocks = sourceBlocks;
        this.stateIds = stateIds;
        this.directions = directions;
        this.layers = layers;
        this.materials = materials;
        this.sourceSnapshotFingerprint = sourceSnapshotFingerprint;
        this.resourceEpoch = resourceEpoch;
        this.fingerprint = fingerprint;
        this.captureTimeNs = captureTimeNs;
        this.modelBlocksScanned = modelBlocksScanned;
        this.acceptedBlocks = acceptedBlocks;
        this.noVisibleQuadBlocks = noVisibleQuadBlocks;
        this.rejectedLeavesBlocks = rejectedLeavesBlocks;
        this.rejectedFluidBlocks = rejectedFluidBlocks;
        this.rejectedBlockEntityBlocks = rejectedBlockEntityBlocks;
        this.rejectedMissingModelBlocks = rejectedMissingModelBlocks;
        this.rejectedMaterialBlocks = rejectedMaterialBlocks;
        this.rejectedTranslucentBlocks = rejectedTranslucentBlocks;
        this.rejectedAtlasBlocks = rejectedAtlasBlocks;
        this.solidQuads = solidQuads;
        this.cutoutQuads = cutoutQuads;
        this.tintedQuads = tintedQuads;
        this.minBlockLight = minBlockLight;
        this.maxBlockLight = maxBlockLight;
        this.minSkyLight = minSkyLight;
        this.maxSkyLight = maxSkyLight;
    }

    public static SectionBakedQuadSnapshot capture(SectionSnapshot snapshot) {
        RenderSystem.assertOnRenderThread();
        if (snapshot == null) {
            throw new NullPointerException("snapshot is required");
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            throw new IllegalStateException("P2.5 generalized quad capture requires a live client level");
        }
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        if (modelSet == null) {
            throw new IllegalStateException("Minecraft block-state model set is unavailable");
        }

        long epochBefore = SectionMaterialSnapshot.currentResourceEpoch();
        if (epochBefore == Long.MIN_VALUE) {
            throw new IllegalStateException("Minecraft model/blocks-atlas resource epoch is unavailable");
        }

        long startNs = System.nanoTime();
        float[] positions = new float[MAX_QUADS * VERTICES_PER_QUAD * 3];
        float[] uvs = new float[MAX_QUADS * VERTICES_PER_QUAD * 2];
        int[] colors = new int[MAX_QUADS * VERTICES_PER_QUAD];
        int[] lights = new int[MAX_QUADS * VERTICES_PER_QUAD];
        int[] materialIds = new int[MAX_QUADS];
        int[] sourceBlocks = new int[MAX_QUADS];
        int[] stateIds = new int[MAX_QUADS];
        byte[] directions = new byte[MAX_QUADS];
        byte[] layers = new byte[MAX_QUADS];
        Map<MaterialIdentity, Integer> materialTable = new LinkedHashMap<>();

        boolean ambientOcclusion = Boolean.TRUE.equals(minecraft.options.ambientOcclusion().get());
        ModelBlockRenderer renderer = new ModelBlockRenderer(ambientOcclusion, true, minecraft.getBlockColors());
        CaptureOutput output = new CaptureOutput();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        int originX = snapshot.sectionX() * SectionSnapshot.INTERIOR_SIZE;
        int originY = snapshot.sectionY() * SectionSnapshot.INTERIOR_SIZE;
        int originZ = snapshot.sectionZ() * SectionSnapshot.INTERIOR_SIZE;
        int quadCount = 0;
        int scanned = 0;
        int acceptedBlocks = 0;
        int noVisible = 0;
        int rejectedLeaves = 0;
        int rejectedFluid = 0;
        int rejectedBlockEntity = 0;
        int rejectedMissingModel = 0;
        int rejectedMaterial = 0;
        int rejectedTranslucent = 0;
        int rejectedAtlas = 0;
        int solid = 0;
        int cutout = 0;
        int tinted = 0;
        int minBlock = Integer.MAX_VALUE;
        int maxBlock = Integer.MIN_VALUE;
        int minSky = Integer.MAX_VALUE;
        int maxSky = Integer.MIN_VALUE;
        long hash = FNV_OFFSET_BASIS;

        BlockModelLighter.enableCaching();
        try {
            for (int localY = 0; localY < SectionSnapshot.INTERIOR_SIZE; localY++) {
                for (int localZ = 0; localZ < SectionSnapshot.INTERIOR_SIZE; localZ++) {
                    for (int localX = 0; localX < SectionSnapshot.INTERIOR_SIZE; localX++) {
                        int stateId = snapshot.stateId(localX, localY, localZ);
                        BlockState state = Block.stateById(stateId);
                        if (state == null || Block.getId(state) != stateId || state.isAir()) {
                            continue;
                        }
                        if (state.getRenderShape() != RenderShape.MODEL) {
                            continue;
                        }
                        scanned++;
                        if (!state.getFluidState().isEmpty()) {
                            rejectedFluid++;
                            continue;
                        }
                        if (state.hasBlockEntity()) {
                            rejectedBlockEntity++;
                            continue;
                        }
                        if (state.getBlock() instanceof LeavesBlock) {
                            rejectedLeaves++;
                            continue;
                        }

                        worldPos.set(originX + localX, originY + localY, originZ + localZ);
                        BlockStateModel model = modelSet.get(state);
                        if (model == null || model == modelSet.missingModel()) {
                            rejectedMissingModel++;
                            continue;
                        }

                        output.reset(packLocal(localX, localY, localZ), stateId);
                        renderer.tesselateBlock(
                                output,
                                localX,
                                localY,
                                localZ,
                                level,
                                worldPos,
                                state,
                                model,
                                state.getSeed(worldPos));

                        if (output.rejectReason != REJECT_NONE) {
                            switch (output.rejectReason) {
                                case REJECT_TRANSLUCENT -> rejectedTranslucent++;
                                case REJECT_ATLAS -> rejectedAtlas++;
                                default -> rejectedMaterial++;
                            }
                            continue;
                        }
                        if (output.pending.isEmpty()) {
                            noVisible++;
                            continue;
                        }
                        if (quadCount + output.pending.size() > MAX_QUADS) {
                            throw new IllegalStateException(
                                    "P2.5 generalized quad capture exceeded bounded MAX_QUADS=" + MAX_QUADS);
                        }

                        acceptedBlocks++;
                        for (PendingQuad pending : output.pending) {
                            int quad = quadCount++;
                            int positionBase = quad * VERTICES_PER_QUAD * 3;
                            int uvBase = quad * VERTICES_PER_QUAD * 2;
                            int vertexBase = quad * VERTICES_PER_QUAD;
                            System.arraycopy(pending.positions, 0, positions, positionBase, pending.positions.length);
                            System.arraycopy(pending.uvs, 0, uvs, uvBase, pending.uvs.length);
                            System.arraycopy(pending.colors, 0, colors, vertexBase, pending.colors.length);
                            System.arraycopy(pending.lights, 0, lights, vertexBase, pending.lights.length);
                            int materialId = materialTable.computeIfAbsent(pending.material, ignored -> materialTable.size());
                            materialIds[quad] = materialId;
                            sourceBlocks[quad] = output.sourceBlock;
                            stateIds[quad] = output.stateId;
                            directions[quad] = pending.direction;
                            layers[quad] = pending.material.layer();
                            if (pending.material.layer() == LAYER_SOLID) {
                                solid++;
                            } else {
                                cutout++;
                            }
                            if (pending.material.tintIndex() >= 0) {
                                tinted++;
                            }

                            hash = hashInt(hash, output.sourceBlock);
                            hash = hashInt(hash, output.stateId);
                            hash = hashInt(hash, materialId);
                            hash = hashInt(hash, Byte.toUnsignedInt(pending.direction));
                            hash = hashInt(hash, Byte.toUnsignedInt(pending.material.layer()));
                            for (float value : pending.positions) {
                                hash = hashInt(hash, Float.floatToRawIntBits(value));
                            }
                            for (float value : pending.uvs) {
                                hash = hashInt(hash, Float.floatToRawIntBits(value));
                            }
                            for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
                                int light = pending.lights[vertex];
                                hash = hashInt(hash, pending.colors[vertex]);
                                hash = hashInt(hash, light);
                                int block = LightCoordsUtil.block(light);
                                int sky = LightCoordsUtil.sky(light);
                                minBlock = Math.min(minBlock, block);
                                maxBlock = Math.max(maxBlock, block);
                                minSky = Math.min(minSky, sky);
                                maxSky = Math.max(maxSky, sky);
                            }
                        }
                    }
                }
            }
        } finally {
            BlockModelLighter.clearCache();
        }

        long epochAfter = SectionMaterialSnapshot.currentResourceEpoch();
        if (epochAfter != epochBefore) {
            throw new IllegalStateException(
                    "Minecraft model/blocks-atlas resource epoch changed during P2.5 generalized capture");
        }
        if (quadCount <= 0) {
            throw new IllegalStateException("P2.5 generalized capture produced no supported SOLID/CUTOUT quads");
        }

        hash = hashLong(hash, snapshot.fingerprint());
        hash = hashLong(hash, epochBefore);
        hash = hashInt(hash, quadCount);
        hash = hashInt(hash, solid);
        hash = hashInt(hash, cutout);
        hash = hashInt(hash, materialTable.size());
        hash = hashInt(hash, acceptedBlocks);
        hash = hashInt(hash, rejectedTranslucent);

        return new SectionBakedQuadSnapshot(
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(),
                Arrays.copyOf(positions, quadCount * VERTICES_PER_QUAD * 3),
                Arrays.copyOf(uvs, quadCount * VERTICES_PER_QUAD * 2),
                Arrays.copyOf(colors, quadCount * VERTICES_PER_QUAD),
                Arrays.copyOf(lights, quadCount * VERTICES_PER_QUAD),
                Arrays.copyOf(materialIds, quadCount),
                Arrays.copyOf(sourceBlocks, quadCount),
                Arrays.copyOf(stateIds, quadCount),
                Arrays.copyOf(directions, quadCount),
                Arrays.copyOf(layers, quadCount),
                materialTable.keySet().toArray(MaterialIdentity[]::new),
                snapshot.fingerprint(), epochBefore, hash, System.nanoTime() - startNs,
                scanned, acceptedBlocks, noVisible, rejectedLeaves, rejectedFluid, rejectedBlockEntity,
                rejectedMissingModel, rejectedMaterial, rejectedTranslucent, rejectedAtlas,
                solid, cutout, tinted,
                minBlock == Integer.MAX_VALUE ? 0 : minBlock,
                maxBlock == Integer.MIN_VALUE ? 0 : maxBlock,
                minSky == Integer.MAX_VALUE ? 0 : minSky,
                maxSky == Integer.MIN_VALUE ? 0 : maxSky);
    }

    private static int packLocal(int x, int y, int z) {
        return x | (y << 4) | (z << 8);
    }

    private static byte layerCode(ChunkSectionLayer layer) {
        if (layer == ChunkSectionLayer.SOLID) {
            return LAYER_SOLID;
        }
        if (layer == ChunkSectionLayer.CUTOUT) {
            return LAYER_CUTOUT;
        }
        return -1;
    }

    private static long hashInt(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value);
        return hash * FNV_PRIME;
    }

    private static long hashLong(long hash, long value) {
        hash = hashInt(hash, (int) value);
        return hashInt(hash, (int) (value >>> 32));
    }

    private static final class CaptureOutput implements BlockQuadOutput {
        private final List<PendingQuad> pending = new ArrayList<>(12);
        private int sourceBlock;
        private int stateId;
        private int rejectReason;

        void reset(int sourceBlock, int stateId) {
            this.sourceBlock = sourceBlock;
            this.stateId = stateId;
            this.rejectReason = REJECT_NONE;
            pending.clear();
        }

        @Override
        public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            if (rejectReason != REJECT_NONE) {
                return;
            }
            BakedQuad.MaterialInfo info = quad == null ? null : quad.materialInfo();
            if (info == null || info.sprite() == null || info.layer() == null || instance == null) {
                rejectReason = REJECT_MATERIAL;
                return;
            }
            if (info.layer() == ChunkSectionLayer.TRANSLUCENT) {
                rejectReason = REJECT_TRANSLUCENT;
                return;
            }
            byte layer = layerCode(info.layer());
            if (layer < 0) {
                rejectReason = REJECT_MATERIAL;
                return;
            }
            TextureAtlasSprite sprite = info.sprite();
            if (!TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation())) {
                rejectReason = REJECT_ATLAS;
                return;
            }

            float[] positions = new float[VERTICES_PER_QUAD * 3];
            float[] uvs = new float[VERTICES_PER_QUAD * 2];
            int[] colors = new int[VERTICES_PER_QUAD];
            int[] lights = new int[VERTICES_PER_QUAD];
            for (int vertex = 0; vertex < VERTICES_PER_QUAD; vertex++) {
                Vector3fc position = quad.position(vertex);
                if (position == null) {
                    rejectReason = REJECT_MATERIAL;
                    return;
                }
                int p = vertex * 3;
                positions[p] = x + position.x();
                positions[p + 1] = y + position.y();
                positions[p + 2] = z + position.z();
                long packedUv = quad.packedUV(vertex);
                float u = UVPair.unpackU(packedUv);
                float v = UVPair.unpackV(packedUv);
                if (!Float.isFinite(positions[p]) || !Float.isFinite(positions[p + 1])
                        || !Float.isFinite(positions[p + 2]) || !Float.isFinite(u) || !Float.isFinite(v)) {
                    rejectReason = REJECT_MATERIAL;
                    return;
                }
                uvs[vertex * 2] = u;
                uvs[vertex * 2 + 1] = v;
                colors[vertex] = instance.getColor(vertex);
                lights[vertex] = instance.getLightCoordsWithEmission(vertex, info.lightEmission());
            }

            Direction direction = quad.direction();
            byte directionOrdinal = direction == null ? (byte) -1 : (byte) direction.ordinal();
            MaterialIdentity material = new MaterialIdentity(
                    sprite.atlasLocation().toString(),
                    sprite.contents().name().toString(),
                    layer,
                    info.flags(),
                    info.tintIndex(),
                    info.shade(),
                    info.lightEmission(),
                    sprite.isAnimated());
            pending.add(new PendingQuad(positions, uvs, colors, lights, directionOrdinal, material));
        }
    }

    public boolean contentEquals(SectionBakedQuadSnapshot other) {
        return other != null
                && sectionX == other.sectionX && sectionY == other.sectionY && sectionZ == other.sectionZ
                && sourceSnapshotFingerprint == other.sourceSnapshotFingerprint
                && resourceEpoch == other.resourceEpoch
                && fingerprint == other.fingerprint
                && modelBlocksScanned == other.modelBlocksScanned
                && acceptedBlocks == other.acceptedBlocks
                && noVisibleQuadBlocks == other.noVisibleQuadBlocks
                && rejectedLeavesBlocks == other.rejectedLeavesBlocks
                && rejectedFluidBlocks == other.rejectedFluidBlocks
                && rejectedBlockEntityBlocks == other.rejectedBlockEntityBlocks
                && rejectedMissingModelBlocks == other.rejectedMissingModelBlocks
                && rejectedMaterialBlocks == other.rejectedMaterialBlocks
                && rejectedTranslucentBlocks == other.rejectedTranslucentBlocks
                && rejectedAtlasBlocks == other.rejectedAtlasBlocks
                && solidQuads == other.solidQuads && cutoutQuads == other.cutoutQuads
                && tintedQuads == other.tintedQuads
                && minBlockLight == other.minBlockLight && maxBlockLight == other.maxBlockLight
                && minSkyLight == other.minSkyLight && maxSkyLight == other.maxSkyLight
                && Arrays.equals(positions, other.positions)
                && Arrays.equals(uvs, other.uvs)
                && Arrays.equals(exactArgbColors, other.exactArgbColors)
                && Arrays.equals(packedLights, other.packedLights)
                && Arrays.equals(materialIds, other.materialIds)
                && Arrays.equals(sourceBlocks, other.sourceBlocks)
                && Arrays.equals(stateIds, other.stateIds)
                && Arrays.equals(directions, other.directions)
                && Arrays.equals(layers, other.layers)
                && Arrays.equals(materials, other.materials);
    }

    public float position(int quad, int vertex, int axis) {
        return positions[(quad * VERTICES_PER_QUAD + vertex) * 3 + axis];
    }

    public float u(int quad, int vertex) { return uvs[(quad * VERTICES_PER_QUAD + vertex) * 2]; }
    public float v(int quad, int vertex) { return uvs[(quad * VERTICES_PER_QUAD + vertex) * 2 + 1]; }
    public int exactArgbColor(int quad, int vertex) { return exactArgbColors[quad * VERTICES_PER_QUAD + vertex]; }
    public int packedLight(int quad, int vertex) { return packedLights[quad * VERTICES_PER_QUAD + vertex]; }
    public int materialId(int quad) { return materialIds[quad]; }
    public MaterialIdentity material(int materialId) { return materials[materialId]; }
    public int sourceBlock(int quad) { return sourceBlocks[quad]; }
    public int stateId(int quad) { return stateIds[quad]; }
    public byte direction(int quad) { return directions[quad]; }
    public byte layer(int quad) { return layers[quad]; }

    public int sectionX() { return sectionX; }
    public int sectionY() { return sectionY; }
    public int sectionZ() { return sectionZ; }
    public int quadCount() { return materialIds.length; }
    public int materialCount() { return materials.length; }
    public long sourceSnapshotFingerprint() { return sourceSnapshotFingerprint; }
    public long resourceEpoch() { return resourceEpoch; }
    public long fingerprint() { return fingerprint; }
    public long captureTimeNs() { return captureTimeNs; }
    public int modelBlocksScanned() { return modelBlocksScanned; }
    public int acceptedBlocks() { return acceptedBlocks; }
    public int noVisibleQuadBlocks() { return noVisibleQuadBlocks; }
    public int rejectedLeavesBlocks() { return rejectedLeavesBlocks; }
    public int rejectedFluidBlocks() { return rejectedFluidBlocks; }
    public int rejectedBlockEntityBlocks() { return rejectedBlockEntityBlocks; }
    public int rejectedMissingModelBlocks() { return rejectedMissingModelBlocks; }
    public int rejectedMaterialBlocks() { return rejectedMaterialBlocks; }
    public int rejectedTranslucentBlocks() { return rejectedTranslucentBlocks; }
    public int rejectedAtlasBlocks() { return rejectedAtlasBlocks; }
    public int rejectedBlocks() {
        return rejectedLeavesBlocks + rejectedFluidBlocks + rejectedBlockEntityBlocks
                + rejectedMissingModelBlocks + rejectedMaterialBlocks + rejectedTranslucentBlocks + rejectedAtlasBlocks;
    }
    public int solidQuads() { return solidQuads; }
    public int cutoutQuads() { return cutoutQuads; }
    public int tintedQuads() { return tintedQuads; }
    public int minBlockLight() { return minBlockLight; }
    public int maxBlockLight() { return maxBlockLight; }
    public int minSkyLight() { return minSkyLight; }
    public int maxSkyLight() { return maxSkyLight; }
}
