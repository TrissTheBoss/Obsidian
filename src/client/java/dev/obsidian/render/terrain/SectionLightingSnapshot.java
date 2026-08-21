package dev.obsidian.render.terrain;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Render-thread-only P2.4 capture of exact Minecraft 26.2 light/AO results.
 *
 * <p>The capture deliberately calls the public vanilla {@link BlockModelLighter}
 * against the live {@link ClientLevel}. It then freezes only primitive
 * per-reference-face/per-corner results. {@link LitSectionMesh} performs no
 * world, light-engine, model or resource reads.</p>
 */
public final class SectionLightingSnapshot {
    public static final byte MODE_UNSUPPORTED = 0;
    public static final byte MODE_FLAT = 1;
    public static final byte MODE_AMBIENT_OCCLUSION = 2;

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

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final int[] packedLights;
    private final int[] exactArgbColors;
    private final byte[] modes;
    private final long referenceFingerprint;
    private final long materialFingerprint;
    private final long resourceEpoch;
    private final long fingerprint;
    private final long captureTimeNs;
    private final int supportedFaces;
    private final int ambientOcclusionFaces;
    private final int flatFaces;
    private final int minBlockLight;
    private final int maxBlockLight;
    private final int minSkyLight;
    private final int maxSkyLight;

    private SectionLightingSnapshot(
            int sectionX,
            int sectionY,
            int sectionZ,
            int[] packedLights,
            int[] exactArgbColors,
            byte[] modes,
            long referenceFingerprint,
            long materialFingerprint,
            long resourceEpoch,
            long fingerprint,
            long captureTimeNs,
            int supportedFaces,
            int ambientOcclusionFaces,
            int flatFaces,
            int minBlockLight,
            int maxBlockLight,
            int minSkyLight,
            int maxSkyLight) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.packedLights = packedLights;
        this.exactArgbColors = exactArgbColors;
        this.modes = modes;
        this.referenceFingerprint = referenceFingerprint;
        this.materialFingerprint = materialFingerprint;
        this.resourceEpoch = resourceEpoch;
        this.fingerprint = fingerprint;
        this.captureTimeNs = captureTimeNs;
        this.supportedFaces = supportedFaces;
        this.ambientOcclusionFaces = ambientOcclusionFaces;
        this.flatFaces = flatFaces;
        this.minBlockLight = minBlockLight;
        this.maxBlockLight = maxBlockLight;
        this.minSkyLight = minSkyLight;
        this.maxSkyLight = maxSkyLight;
    }

    public static SectionLightingSnapshot capture(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionMaterialSnapshot materials) {
        RenderSystem.assertOnRenderThread();
        if (snapshot == null || reference == null || materials == null) {
            throw new NullPointerException("snapshot, reference and materials are required");
        }
        reference.validateAgainst(snapshot);
        if (materials.referenceFingerprint() != reference.fingerprint()
                || materials.referenceFaceCount() != reference.faceCount()
                || materials.sectionX() != snapshot.sectionX()
                || materials.sectionY() != snapshot.sectionY()
                || materials.sectionZ() != snapshot.sectionZ()) {
            throw new IllegalStateException("P2.4 material snapshot does not match section/reference");
        }
        if (SectionMaterialSnapshot.currentResourceEpoch() != materials.resourceEpoch()) {
            throw new IllegalStateException("Minecraft model/atlas resource epoch changed before P2.4 lighting capture");
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            throw new IllegalStateException("P2.4 lighting capture requires a live client level");
        }
        BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
        if (modelSet == null) {
            throw new IllegalStateException("Minecraft block-state model set is unavailable");
        }

        long startNs = System.nanoTime();
        int faceCount = reference.faceCount();
        int[] packedLights = new int[faceCount * DrawableSectionMesh.VERTICES_PER_FACE];
        int[] colors = new int[faceCount * DrawableSectionMesh.VERTICES_PER_FACE];
        byte[] modes = new byte[faceCount];
        Arrays.fill(colors, 0xFFFFFFFF);

        BlockModelLighter lighter = new BlockModelLighter();
        ArrayList<BlockStateModelPart> parts = new ArrayList<>(4);
        RandomSource random = RandomSource.create(0L);
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        int originX = snapshot.sectionX() * SectionSnapshot.INTERIOR_SIZE;
        int originY = snapshot.sectionY() * SectionSnapshot.INTERIOR_SIZE;
        int originZ = snapshot.sectionZ() * SectionSnapshot.INTERIOR_SIZE;
        boolean ambientOcclusionEnabled = Boolean.TRUE.equals(minecraft.options.ambientOcclusion().get());

        int supported = 0;
        int aoFaces = 0;
        int flatFaces = 0;
        int minBlock = Integer.MAX_VALUE;
        int maxBlock = Integer.MIN_VALUE;
        int minSky = Integer.MAX_VALUE;
        int maxSky = Integer.MIN_VALUE;
        long hash = FNV_OFFSET_BASIS;

        for (int face = 0; face < faceCount; face++) {
            int materialId = materials.materialId(face);
            if (materialId == SectionMaterialSnapshot.UNSUPPORTED_MATERIAL) {
                continue;
            }

            int packedFace = reference.packedFace(face);
            int localX = packedFace & 0xF;
            int localY = (packedFace >>> 4) & 0xF;
            int localZ = (packedFace >>> 8) & 0xF;
            int directionIndex = (packedFace >>> 12) & 0x7;
            if (directionIndex >= DIRECTIONS.length) {
                throw new IllegalStateException("Invalid P2.4 reference direction: " + directionIndex);
            }
            Direction direction = DIRECTIONS[directionIndex];

            int stateId = reference.stateId(face);
            BlockState state = Block.stateById(stateId);
            if (state == null || Block.getId(state) != stateId) {
                throw new IllegalStateException("P2.4 could not reconstruct materialized BlockState");
            }

            worldPos.set(originX + localX, originY + localY, originZ + localZ);
            BlockStateModel model = modelSet.get(state);
            if (model == null || model == modelSet.missingModel()) {
                throw new IllegalStateException("P2.4 materialized face lost its baked model");
            }

            parts.clear();
            random.setSeed(state.getSeed(worldPos));
            model.collectParts(random, parts);
            if (parts.isEmpty()) {
                throw new IllegalStateException("P2.4 materialized face selected no model parts");
            }

            BakedQuad directionalQuad = null;
            int directionalCount = 0;
            for (BlockStateModelPart part : parts) {
                List<BakedQuad> quads = part.getQuads(direction);
                directionalCount += quads.size();
                if (!quads.isEmpty() && directionalQuad == null) {
                    directionalQuad = quads.getFirst();
                }
            }
            if (directionalCount != 1 || directionalQuad == null || directionalQuad.direction() != direction) {
                throw new IllegalStateException("P2.4 could not reselect the exact P2.3 directional quad");
            }

            BakedQuad.MaterialInfo info = directionalQuad.materialInfo();
            SectionMaterialSnapshot.MaterialIdentity identity = materials.material(materialId);
            if (info == null
                    || info.sprite() == null
                    || info.layer() == null
                    || !identity.sprite().equals(info.sprite().contents().name().toString())
                    || identity.layerOrdinal() != info.layer().ordinal()
                    || identity.shade() != info.shade()
                    || identity.lightEmission() != info.lightEmission()) {
                throw new IllegalStateException("P2.4 reselected baked material identity differs from P2.3 capture");
            }

            boolean useAo = ambientOcclusionEnabled
                    && state.getLightEmission() == 0
                    && parts.getFirst().useAmbientOcclusion();
            QuadInstance quadInstance = new QuadInstance();
            if (useAo) {
                lighter.prepareQuadAmbientOcclusion(level, state, worldPos, directionalQuad, quadInstance);
                modes[face] = MODE_AMBIENT_OCCLUSION;
                aoFaces++;
            } else {
                BlockPos adjacent = worldPos.relative(direction);
                int flatLight = lighter.getLightCoords(state, level, adjacent);
                lighter.prepareQuadFlat(level, state, worldPos, flatLight, directionalQuad, quadInstance);
                modes[face] = MODE_FLAT;
                flatFaces++;
            }

            quadInstance.multiplyColor(materials.tintColor(face));
            boolean[] canonicalSeen = new boolean[DrawableSectionMesh.VERTICES_PER_FACE];
            for (int vertex = 0; vertex < DrawableSectionMesh.VERTICES_PER_FACE; vertex++) {
                Vector3fc position = directionalQuad.position(vertex);
                int canonicalCorner = findCanonicalCorner(directionIndex, position);
                if (canonicalCorner < 0 || canonicalSeen[canonicalCorner]) {
                    throw new IllegalStateException("P2.4 could not map lighter result to canonical face corner");
                }
                canonicalSeen[canonicalCorner] = true;
                int finalLight = quadInstance.getLightCoordsWithEmission(vertex, info.lightEmission());
                int finalColor = quadInstance.getColor(vertex);
                int offset = face * DrawableSectionMesh.VERTICES_PER_FACE + canonicalCorner;
                packedLights[offset] = finalLight;
                colors[offset] = finalColor;

                int block = LightCoordsUtil.block(finalLight);
                int sky = LightCoordsUtil.sky(finalLight);
                minBlock = Math.min(minBlock, block);
                maxBlock = Math.max(maxBlock, block);
                minSky = Math.min(minSky, sky);
                maxSky = Math.max(maxSky, sky);
            }
            for (boolean seen : canonicalSeen) {
                if (!seen) {
                    throw new IllegalStateException("P2.4 canonical corner mapping is incomplete");
                }
            }

            supported++;
            hash = hashInt(hash, packedFace);
            hash = hashInt(hash, stateId);
            hash = hashInt(hash, materialId);
            hash = hashInt(hash, modes[face]);
            int base = face * DrawableSectionMesh.VERTICES_PER_FACE;
            for (int corner = 0; corner < DrawableSectionMesh.VERTICES_PER_FACE; corner++) {
                hash = hashInt(hash, packedLights[base + corner]);
                hash = hashInt(hash, colors[base + corner]);
            }
        }

        if (supported != materials.supportedFaces()) {
            throw new IllegalStateException("P2.4 lighting supported-face accounting mismatch");
        }
        if (supported == 0) {
            throw new IllegalStateException("P2.4 found no materialized faces to light");
        }

        hash = hashLong(hash, reference.fingerprint());
        hash = hashLong(hash, materials.fingerprint());
        hash = hashLong(hash, materials.resourceEpoch());
        hash = hashInt(hash, ambientOcclusionEnabled ? 1 : 0);
        hash = hashInt(hash, supported);
        hash = hashInt(hash, aoFaces);
        hash = hashInt(hash, flatFaces);

        SectionLightingSnapshot result = new SectionLightingSnapshot(
                snapshot.sectionX(), snapshot.sectionY(), snapshot.sectionZ(),
                packedLights, colors, modes,
                reference.fingerprint(), materials.fingerprint(), materials.resourceEpoch(),
                hash, System.nanoTime() - startNs,
                supported, aoFaces, flatFaces,
                minBlock == Integer.MAX_VALUE ? 0 : minBlock,
                maxBlock == Integer.MIN_VALUE ? 0 : maxBlock,
                minSky == Integer.MAX_VALUE ? 0 : minSky,
                maxSky == Integer.MIN_VALUE ? 0 : maxSky);
        result.validateAgainst(snapshot, reference, materials);
        return result;
    }

    public void validateAgainst(
            SectionSnapshot snapshot,
            ReferenceFaceMesh reference,
            SectionMaterialSnapshot materials) {
        if (snapshot.sectionX() != sectionX || snapshot.sectionY() != sectionY || snapshot.sectionZ() != sectionZ) {
            throw new IllegalStateException("P2.4 lighting section coordinates do not match snapshot");
        }
        if (reference.fingerprint() != referenceFingerprint || reference.faceCount() != modes.length) {
            throw new IllegalStateException("P2.4 lighting reference identity mismatch");
        }
        if (materials.fingerprint() != materialFingerprint
                || materials.resourceEpoch() != resourceEpoch
                || materials.supportedFaces() != supportedFaces) {
            throw new IllegalStateException("P2.4 lighting material identity mismatch");
        }
        if (packedLights.length != modes.length * DrawableSectionMesh.VERTICES_PER_FACE
                || exactArgbColors.length != packedLights.length
                || ambientOcclusionFaces + flatFaces != supportedFaces) {
            throw new IllegalStateException("P2.4 lighting array/accounting mismatch");
        }
        int counted = 0;
        for (int face = 0; face < modes.length; face++) {
            boolean materialized = materials.materialId(face) != SectionMaterialSnapshot.UNSUPPORTED_MATERIAL;
            if (materialized != (modes[face] != MODE_UNSUPPORTED)) {
                throw new IllegalStateException("P2.4 lighting/material support mismatch at reference face " + face);
            }
            if (materialized) {
                counted++;
            }
        }
        if (counted != supportedFaces) {
            throw new IllegalStateException("P2.4 lighting counted-face mismatch");
        }
    }

    public boolean contentEquals(SectionLightingSnapshot other) {
        return other != null
                && sectionX == other.sectionX
                && sectionY == other.sectionY
                && sectionZ == other.sectionZ
                && referenceFingerprint == other.referenceFingerprint
                && materialFingerprint == other.materialFingerprint
                && resourceEpoch == other.resourceEpoch
                && fingerprint == other.fingerprint
                && supportedFaces == other.supportedFaces
                && ambientOcclusionFaces == other.ambientOcclusionFaces
                && flatFaces == other.flatFaces
                && minBlockLight == other.minBlockLight
                && maxBlockLight == other.maxBlockLight
                && minSkyLight == other.minSkyLight
                && maxSkyLight == other.maxSkyLight
                && Arrays.equals(packedLights, other.packedLights)
                && Arrays.equals(exactArgbColors, other.exactArgbColors)
                && Arrays.equals(modes, other.modes);
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
            case 0 -> { expectedX = 0.0f; expectedY = corner >= 2 ? 1.0f : 0.0f; expectedZ = (corner == 1 || corner == 2) ? 1.0f : 0.0f; }
            case 1 -> { expectedX = 1.0f; expectedY = corner >= 2 ? 1.0f : 0.0f; expectedZ = (corner == 0 || corner == 3) ? 1.0f : 0.0f; }
            case 2 -> { expectedY = 0.0f; expectedX = corner >= 2 ? 1.0f : 0.0f; expectedZ = (corner == 0 || corner == 3) ? 1.0f : 0.0f; }
            case 3 -> { expectedY = 1.0f; expectedX = corner >= 2 ? 1.0f : 0.0f; expectedZ = (corner == 1 || corner == 2) ? 1.0f : 0.0f; }
            case 4 -> { expectedZ = 0.0f; expectedY = corner >= 2 ? 1.0f : 0.0f; expectedX = (corner == 0 || corner == 3) ? 1.0f : 0.0f; }
            case 5 -> { expectedZ = 1.0f; expectedY = corner >= 2 ? 1.0f : 0.0f; expectedX = (corner == 1 || corner == 2) ? 1.0f : 0.0f; }
            default -> { return false; }
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

    public int packedLight(int referenceFace, int canonicalCorner) {
        return packedLights[referenceFace * DrawableSectionMesh.VERTICES_PER_FACE + canonicalCorner];
    }

    public int exactArgbColor(int referenceFace, int canonicalCorner) {
        return exactArgbColors[referenceFace * DrawableSectionMesh.VERTICES_PER_FACE + canonicalCorner];
    }

    public byte mode(int referenceFace) { return modes[referenceFace]; }
    public int sectionX() { return sectionX; }
    public int sectionY() { return sectionY; }
    public int sectionZ() { return sectionZ; }
    public int referenceFaceCount() { return modes.length; }
    public long referenceFingerprint() { return referenceFingerprint; }
    public long materialFingerprint() { return materialFingerprint; }
    public long resourceEpoch() { return resourceEpoch; }
    public long fingerprint() { return fingerprint; }
    public long captureTimeNs() { return captureTimeNs; }
    public int supportedFaces() { return supportedFaces; }
    public int ambientOcclusionFaces() { return ambientOcclusionFaces; }
    public int flatFaces() { return flatFaces; }
    public int minBlockLight() { return minBlockLight; }
    public int maxBlockLight() { return maxBlockLight; }
    public int minSkyLight() { return minSkyLight; }
    public int maxSkyLight() { return maxSkyLight; }
}