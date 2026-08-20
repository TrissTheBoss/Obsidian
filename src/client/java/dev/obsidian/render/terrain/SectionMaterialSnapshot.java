package dev.obsidian.render.terrain;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Render-thread P2.3 extraction of Minecraft model/material state into an
 * immutable renderer-owned representation.
 *
 * <p>The permanent {@link SectionSnapshot}/{@link ReferenceFaceMesh} oracle
 * remains primitive and model-manager independent. This companion capture is
 * allowed to touch the live client model/tint/resource state exactly once on
 * the render thread, then stores only immutable IDs, UVs, colors and diagnostic
 * material keys. Future mesh construction consumes this object without world,
 * model-manager, baked-quad or sprite reads.</p>
 */
public final class SectionMaterialSnapshot {
    public static final int UNSUPPORTED_MATERIAL = -1;

    private static final Direction[] DIRECTIONS = {
            Direction.WEST,
            Direction.EAST,
            Direction.DOWN,
            Direction.UP,
            Direction.NORTH,
            Direction.SOUTH
    };
    private static final float GEOMETRY_EPSILON = 1.0e-5f;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    /** Immutable renderer-owned material identity; no live Minecraft object is retained. */
    public record MaterialIdentity(
            String atlas,
            String sprite,
            int layerOrdinal,
            int materialFlags,
            int tintIndex,
            boolean shade,
            int lightEmission,
            boolean animated) {
    }

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final int[] materialIds;
    private final float[] canonicalUvs;
    private final int[] tintColors;
    private final MaterialIdentity[] materials;
    private final long referenceFingerprint;
    private final long resourceEpoch;
    private final long fingerprint;
    private final long captureTimeNs;

    private final int supportedFaces;
    private final int rejectedMissingModelFaces;
    private final int rejectedGeneralQuadFaces;
    private final int rejectedDirectionalQuadFaces;
    private final int rejectedLayerFaces;
    private final int rejectedAtlasFaces;
    private final int rejectedGeometryFaces;
    private final int rejectedTintFaces;
    private final int cutoutFaces;
    private final int translucentFaces;
    private final int tintedFaces;
    private final int tintWorldQueries;

    private SectionMaterialSnapshot(
            int sectionX,
            int sectionY,
            int sectionZ,
            int[] materialIds,
            float[] canonicalUvs,
            int[] tintColors,
            MaterialIdentity[] materials,
            long referenceFingerprint,
            long resourceEpoch,
            long fingerprint,
            long captureTimeNs,
            int supportedFaces,
            int rejectedMissingModelFaces,
            int rejectedGeneralQuadFaces,
            int rejectedDirectionalQuadFaces,
            int rejectedLayerFaces,
            int rejectedAtlasFaces,
            int rejectedGeometryFaces,
            int rejectedTintFaces,
            int cutoutFaces,
            int translucentFaces,
            int tintedFaces,
            int tintWorldQueries) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.materialIds = materialIds;
        this.canonicalUvs = canonicalUvs;
        this.tintColors = tintColors;
        this.materials = materials;
        this.referenceFingerprint = referenceFingerprint;
        this.resourceEpoch = resourceEpoch;
        this.fingerprint = fingerprint;
        this.captureTimeNs = captureTimeNs;
        this.supportedFaces = supportedFaces;
        this.rejectedMissingModelFaces = rejectedMissingModelFaces;
        this.rejectedGeneralQuadFaces = rejectedGeneralQuadFaces;
        this.rejectedDirectionalQuadFaces = rejectedDirectionalQuadFaces;
        this.rejectedLayerFaces = rejectedLayerFaces;
        this.rejectedAtlasFaces = rejectedAtlasFaces;
        this.rejectedGeometryFaces = rejectedGeometryFaces;
        this.rejectedTintFaces = rejectedTintFaces;
        this.cutoutFaces = cutoutFaces;
        this.translucentFaces = translucentFaces;
        this.tintedFaces = tintedFaces;
        this.tintWorldQueries = tintWorldQueries;
    }

    public static SectionMaterialSnapshot capture(SectionSnapshot snapshot, ReferenceFaceMesh reference) {
        RenderSystem.assertOnRenderThread();
        if (snapshot == null || reference == null) {
            throw new NullPointerException("snapshot and reference are required");
        }
        reference.validateAgainst(snapshot);

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            throw new IllegalStateException("P2.3 material capture requires a live client level");
        }

        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        if (modelSet == null) {
            throw new IllegalStateException("Minecraft block-state model set is unavailable");
        }
        AbstractTexture blocksAtlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (blocksAtlas == null || blocksAtlas.getTextureView() == null || blocksAtlas.getSampler() == null) {
            throw new IllegalStateException("Minecraft blocks atlas is unavailable");
        }

        long startNs = System.nanoTime();
        long resourceEpoch = resourceEpoch(modelSet, blocksAtlas);
        int faceCount = reference.faceCount();
        int[] materialIds = new int[faceCount];
        Arrays.fill(materialIds, UNSUPPORTED_MATERIAL);
        float[] uvs = new float[faceCount * DrawableSectionMesh.VERTICES_PER_FACE * 2];
        int[] tintColors = new int[faceCount];
        Arrays.fill(tintColors, 0xFFFFFFFF);

        Map<MaterialIdentity, Integer> materialTable = new LinkedHashMap<>();
        ArrayList<BlockStateModelPart> parts = new ArrayList<>(4);
        RandomSource random = RandomSource.create(0L);
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        int supported = 0;
        int rejectedMissing = 0;
        int rejectedGeneral = 0;
        int rejectedDirectional = 0;
        int rejectedLayer = 0;
        int rejectedAtlas = 0;
        int rejectedGeometry = 0;
        int rejectedTint = 0;
        int cutout = 0;
        int translucent = 0;
        int tinted = 0;
        int tintQueries = 0;
        long hash = FNV_OFFSET_BASIS;

        int originX = snapshot.sectionX() * SectionSnapshot.INTERIOR_SIZE;
        int originY = snapshot.sectionY() * SectionSnapshot.INTERIOR_SIZE;
        int originZ = snapshot.sectionZ() * SectionSnapshot.INTERIOR_SIZE;

        for (int face = 0; face < faceCount; face++) {
            int packed = reference.packedFace(face);
            int localX = packed & 0xF;
            int localY = (packed >>> 4) & 0xF;
            int localZ = (packed >>> 8) & 0xF;
            int directionIndex = (packed >>> 12) & 0x7;
            if (directionIndex >= DIRECTIONS.length) {
                throw new IllegalStateException("Invalid P2.3 reference direction: " + directionIndex);
            }
            Direction direction = DIRECTIONS[directionIndex];

            int stateId = reference.stateId(face);
            BlockState state = Block.stateById(stateId);
            if (state == null || Block.getId(state) != stateId) {
                rejectedMissing++;
                continue;
            }

            worldPos.set(originX + localX, originY + localY, originZ + localZ);
            BlockStateModel model = modelSet.get(state);
            if (model == null || model == modelSet.missingModel()) {
                rejectedMissing++;
                continue;
            }

            parts.clear();
            random.setSeed(state.getSeed(worldPos));
            model.collectParts(random, parts);
            if (parts.isEmpty()) {
                rejectedMissing++;
                continue;
            }

            boolean hasGeneralQuads = false;
            BakedQuad directionalQuad = null;
            int directionalCount = 0;
            for (BlockStateModelPart part : parts) {
                if (!part.getQuads(null).isEmpty()) {
                    hasGeneralQuads = true;
                }
                List<BakedQuad> quads = part.getQuads(direction);
                directionalCount += quads.size();
                if (!quads.isEmpty() && directionalQuad == null) {
                    directionalQuad = quads.getFirst();
                }
            }
            if (hasGeneralQuads) {
                rejectedGeneral++;
                continue;
            }
            if (directionalCount != 1 || directionalQuad == null || directionalQuad.direction() != direction) {
                rejectedDirectional++;
                continue;
            }

            BakedQuad.MaterialInfo info = directionalQuad.materialInfo();
            if (info == null || info.sprite() == null || info.layer() == null) {
                rejectedDirectional++;
                continue;
            }
            if (info.layer() == ChunkSectionLayer.CUTOUT) {
                cutout++;
                rejectedLayer++;
                continue;
            }
            if (info.layer() == ChunkSectionLayer.TRANSLUCENT) {
                translucent++;
                rejectedLayer++;
                continue;
            }
            if (info.layer() != ChunkSectionLayer.SOLID) {
                rejectedLayer++;
                continue;
            }

            TextureAtlasSprite sprite = info.sprite();
            if (!TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation())) {
                rejectedAtlas++;
                continue;
            }

            float[] faceUvs = new float[DrawableSectionMesh.VERTICES_PER_FACE * 2];
            boolean[] cornerSeen = new boolean[DrawableSectionMesh.VERTICES_PER_FACE];
            boolean geometryOk = true;
            for (int vertex = 0; vertex < DrawableSectionMesh.VERTICES_PER_FACE; vertex++) {
                Vector3fc position = directionalQuad.position(vertex);
                int canonicalCorner = findCanonicalCorner(directionIndex, position);
                if (canonicalCorner < 0 || cornerSeen[canonicalCorner]) {
                    geometryOk = false;
                    break;
                }
                cornerSeen[canonicalCorner] = true;
                long packedUv = directionalQuad.packedUV(vertex);
                float u = net.minecraft.client.model.geom.builders.UVPair.unpackU(packedUv);
                float v = net.minecraft.client.model.geom.builders.UVPair.unpackV(packedUv);
                if (!Float.isFinite(u) || !Float.isFinite(v)) {
                    geometryOk = false;
                    break;
                }
                faceUvs[canonicalCorner * 2] = u;
                faceUvs[canonicalCorner * 2 + 1] = v;
            }
            for (boolean seen : cornerSeen) {
                geometryOk &= seen;
            }
            if (!geometryOk) {
                rejectedGeometry++;
                continue;
            }

            int tintColor = 0xFFFFFFFF;
            if (info.isTinted()) {
                BlockTintSource tintSource = minecraft.getBlockColors().getTintSource(state, info.tintIndex());
                if (tintSource == null) {
                    rejectedTint++;
                    continue;
                }
                tintColor = tintSource.colorInWorld(state, level, worldPos);
                tintQueries++;
                tinted++;
            }

            MaterialIdentity identity = new MaterialIdentity(
                    sprite.atlasLocation().toString(),
                    sprite.contents().name().toString(),
                    info.layer().ordinal(),
                    info.flags(),
                    info.tintIndex(),
                    info.shade(),
                    info.lightEmission(),
                    sprite.isAnimated());
            int materialId = materialTable.computeIfAbsent(identity, ignored -> materialTable.size());
            materialIds[face] = materialId;
            tintColors[face] = tintColor;
            System.arraycopy(faceUvs, 0, uvs, face * DrawableSectionMesh.VERTICES_PER_FACE * 2, faceUvs.length);
            supported++;

            hash = hashInt(hash, packed);
            hash = hashInt(hash, stateId);
            hash = hashInt(hash, materialId);
            hash = hashInt(hash, tintColor);
            hash = hashString(hash, identity.atlas());
            hash = hashString(hash, identity.sprite());
            hash = hashInt(hash, identity.layerOrdinal());
            hash = hashInt(hash, identity.materialFlags());
            hash = hashInt(hash, identity.tintIndex());
            hash = hashInt(hash, identity.shade() ? 1 : 0);
            hash = hashInt(hash, identity.lightEmission());
            hash = hashInt(hash, identity.animated() ? 1 : 0);
            for (float value : faceUvs) {
                hash = hashInt(hash, Float.floatToRawIntBits(value));
            }
        }

        hash = hashLong(hash, reference.fingerprint());
        hash = hashLong(hash, resourceEpoch);
        hash = hashInt(hash, supported);
        hash = hashInt(hash, rejectedMissing);
        hash = hashInt(hash, rejectedGeneral);
        hash = hashInt(hash, rejectedDirectional);
        hash = hashInt(hash, rejectedLayer);
        hash = hashInt(hash, rejectedAtlas);
        hash = hashInt(hash, rejectedGeometry);
        hash = hashInt(hash, rejectedTint);

        return new SectionMaterialSnapshot(
                snapshot.sectionX(),
                snapshot.sectionY(),
                snapshot.sectionZ(),
                materialIds,
                uvs,
                tintColors,
                materialTable.keySet().toArray(MaterialIdentity[]::new),
                reference.fingerprint(),
                resourceEpoch,
                hash,
                System.nanoTime() - startNs,
                supported,
                rejectedMissing,
                rejectedGeneral,
                rejectedDirectional,
                rejectedLayer,
                rejectedAtlas,
                rejectedGeometry,
                rejectedTint,
                cutout,
                translucent,
                tinted,
                tintQueries);
    }

    public static long currentResourceEpoch() {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        AbstractTexture atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        if (modelSet == null || atlas == null) {
            return Long.MIN_VALUE;
        }
        return resourceEpoch(modelSet, atlas);
    }

    private static long resourceEpoch(BlockStateModelSet modelSet, AbstractTexture atlas) {
        return (Integer.toUnsignedLong(System.identityHashCode(modelSet)) << 32)
                ^ Integer.toUnsignedLong(System.identityHashCode(atlas));
    }

    private static int findCanonicalCorner(int direction, Vector3fc position) {
        if (position == null) {
            return -1;
        }
        for (int corner = 0; corner < DrawableSectionMesh.VERTICES_PER_FACE; corner++) {
            if (matchesCanonicalCorner(direction, corner, position.x(), position.y(), position.z())) {
                return corner;
            }
        }
        return -1;
    }

    private static boolean matchesCanonicalCorner(int direction, int corner, float x, float y, float z) {
        float expectedX;
        float expectedY;
        float expectedZ;
        switch (direction) {
            case 0 -> { // -X
                expectedX = 0.0f;
                expectedY = corner >= 2 ? 1.0f : 0.0f;
                expectedZ = (corner == 1 || corner == 2) ? 1.0f : 0.0f;
            }
            case 1 -> { // +X
                expectedX = 1.0f;
                expectedY = corner >= 2 ? 1.0f : 0.0f;
                expectedZ = (corner == 0 || corner == 3) ? 1.0f : 0.0f;
            }
            case 2 -> { // -Y
                expectedY = 0.0f;
                expectedX = corner >= 2 ? 1.0f : 0.0f;
                expectedZ = (corner == 0 || corner == 3) ? 1.0f : 0.0f;
            }
            case 3 -> { // +Y
                expectedY = 1.0f;
                expectedX = corner >= 2 ? 1.0f : 0.0f;
                expectedZ = (corner == 1 || corner == 2) ? 1.0f : 0.0f;
            }
            case 4 -> { // -Z
                expectedZ = 0.0f;
                expectedY = corner >= 2 ? 1.0f : 0.0f;
                expectedX = (corner == 0 || corner == 3) ? 1.0f : 0.0f;
            }
            case 5 -> { // +Z
                expectedZ = 1.0f;
                expectedY = corner >= 2 ? 1.0f : 0.0f;
                expectedX = (corner == 1 || corner == 2) ? 1.0f : 0.0f;
            }
            default -> {
                return false;
            }
        }
        return nearlyEqual(x, expectedX) && nearlyEqual(y, expectedY) && nearlyEqual(z, expectedZ);
    }

    private static boolean nearlyEqual(float a, float b) {
        return Math.abs(a - b) <= GEOMETRY_EPSILON;
    }

    private static long hashInt(long hash, int value) {
        hash ^= Integer.toUnsignedLong(value);
        return hash * FNV_PRIME;
    }

    private static long hashLong(long hash, long value) {
        hash = hashInt(hash, (int) value);
        return hashInt(hash, (int) (value >>> 32));
    }

    private static long hashString(long hash, String value) {
        for (int i = 0; i < value.length(); i++) {
            hash = hashInt(hash, value.charAt(i));
        }
        return hash;
    }

    public boolean contentEquals(SectionMaterialSnapshot other) {
        return other != null
                && sectionX == other.sectionX
                && sectionY == other.sectionY
                && sectionZ == other.sectionZ
                && referenceFingerprint == other.referenceFingerprint
                && resourceEpoch == other.resourceEpoch
                && fingerprint == other.fingerprint
                && supportedFaces == other.supportedFaces
                && rejectedMissingModelFaces == other.rejectedMissingModelFaces
                && rejectedGeneralQuadFaces == other.rejectedGeneralQuadFaces
                && rejectedDirectionalQuadFaces == other.rejectedDirectionalQuadFaces
                && rejectedLayerFaces == other.rejectedLayerFaces
                && rejectedAtlasFaces == other.rejectedAtlasFaces
                && rejectedGeometryFaces == other.rejectedGeometryFaces
                && rejectedTintFaces == other.rejectedTintFaces
                && cutoutFaces == other.cutoutFaces
                && translucentFaces == other.translucentFaces
                && tintedFaces == other.tintedFaces
                && tintWorldQueries == other.tintWorldQueries
                && Arrays.equals(materialIds, other.materialIds)
                && Arrays.equals(canonicalUvs, other.canonicalUvs)
                && Arrays.equals(tintColors, other.tintColors)
                && Arrays.equals(materials, other.materials);
    }

    public int materialId(int referenceFace) {
        return materialIds[referenceFace];
    }

    public float u(int referenceFace, int canonicalCorner) {
        return canonicalUvs[(referenceFace * DrawableSectionMesh.VERTICES_PER_FACE + canonicalCorner) * 2];
    }

    public float v(int referenceFace, int canonicalCorner) {
        return canonicalUvs[(referenceFace * DrawableSectionMesh.VERTICES_PER_FACE + canonicalCorner) * 2 + 1];
    }

    public int tintColor(int referenceFace) {
        return tintColors[referenceFace];
    }

    public MaterialIdentity material(int materialId) {
        return materials[materialId];
    }

    public int sectionX() { return sectionX; }
    public int sectionY() { return sectionY; }
    public int sectionZ() { return sectionZ; }
    public int referenceFaceCount() { return materialIds.length; }
    public int materialCount() { return materials.length; }
    public long referenceFingerprint() { return referenceFingerprint; }
    public long resourceEpoch() { return resourceEpoch; }
    public long fingerprint() { return fingerprint; }
    public long captureTimeNs() { return captureTimeNs; }
    public int supportedFaces() { return supportedFaces; }
    public int rejectedFaces() { return materialIds.length - supportedFaces; }
    public int rejectedMissingModelFaces() { return rejectedMissingModelFaces; }
    public int rejectedGeneralQuadFaces() { return rejectedGeneralQuadFaces; }
    public int rejectedDirectionalQuadFaces() { return rejectedDirectionalQuadFaces; }
    public int rejectedLayerFaces() { return rejectedLayerFaces; }
    public int rejectedAtlasFaces() { return rejectedAtlasFaces; }
    public int rejectedGeometryFaces() { return rejectedGeometryFaces; }
    public int rejectedTintFaces() { return rejectedTintFaces; }
    public int cutoutFaces() { return cutoutFaces; }
    public int translucentFaces() { return translucentFaces; }
    public int tintedFaces() { return tintedFaces; }
    public int tintWorldQueries() { return tintWorldQueries; }
}