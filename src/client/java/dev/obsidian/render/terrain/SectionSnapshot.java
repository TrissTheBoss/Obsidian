package dev.obsidian.render.terrain;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Immutable, primitive-only snapshot of one real 16^3 Minecraft section plus
 * a one-block halo in every direction.
 *
 * <p>Capture is the only place allowed to touch the live client world. Once
 * constructed, meshing only sees primitive state IDs and conservative render
 * classifications. No ClientLevel, LevelChunk, palette, BlockPos, or other
 * mutable world object is retained.</p>
 */
public final class SectionSnapshot {
    public static final int INTERIOR_SIZE = 16;
    public static final int HALO = 1;
    public static final int SIZE = INTERIOR_SIZE + HALO * 2;
    public static final int CELL_COUNT = SIZE * SIZE * SIZE;
    public static final int INTERIOR_CELL_COUNT = INTERIOR_SIZE * INTERIOR_SIZE * INTERIOR_SIZE;

    public static final byte AIR = 0;
    public static final byte SUPPORTED_FULL_CUBE = 1;
    public static final byte UNSUPPORTED = 2;

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final int[] stateIds;
    private final byte[] classes;
    private final long fingerprint;
    private final int interiorAirCells;
    private final int interiorSupportedCells;
    private final int interiorUnsupportedCells;
    private final int sampledCells;
    private final long captureTimeNs;

    private SectionSnapshot(
            int sectionX,
            int sectionY,
            int sectionZ,
            int[] stateIds,
            byte[] classes,
            long fingerprint,
            int interiorAirCells,
            int interiorSupportedCells,
            int interiorUnsupportedCells,
            int sampledCells,
            long captureTimeNs) {
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.stateIds = stateIds;
        this.classes = classes;
        this.fingerprint = fingerprint;
        this.interiorAirCells = interiorAirCells;
        this.interiorSupportedCells = interiorSupportedCells;
        this.interiorUnsupportedCells = interiorUnsupportedCells;
        this.sampledCells = sampledCells;
        this.captureTimeNs = captureTimeNs;
    }

    /**
     * Attempts to capture a useful real section near the player.
     *
     * <p>Returns {@code null} while the world/player or the complete 3x3 chunk
     * neighborhood needed for the halo is not already loaded. Chunk lookup uses
     * {@code ChunkStatus.FULL, false}, so retrying never asks Minecraft to load
     * or generate missing chunks.</p>
     */
    public static SectionSnapshot tryCaptureNearPlayer() {
        RenderSystem.assertOnRenderThread();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return null;
        }

        SectionPos playerSection = SectionPos.of(minecraft.player.blockPosition());
        int sectionX = playerSection.x();
        int sectionZ = playerSection.z();
        ClientChunkCache source = level.getChunkSource();
        LevelChunk center = source.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
        if (center == null) {
            return null;
        }

        int sectionY = selectInterestingSection(level, center, playerSection.y());
        if (sectionY == Integer.MIN_VALUE) {
            return null;
        }

        LevelChunk[][] chunks = new LevelChunk[3][3];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = source.getChunk(sectionX + dx, sectionZ + dz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    return null;
                }
                chunks[dx + 1][dz + 1] = chunk;
            }
        }

        long startNs = System.nanoTime();
        int[] stateIds = new int[CELL_COUNT];
        byte[] classes = new byte[CELL_COUNT];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState airState = Blocks.AIR.defaultBlockState();
        int airId = Block.getId(airState);

        int minBlockX = SectionPos.sectionToBlockCoord(sectionX);
        int minBlockY = SectionPos.sectionToBlockCoord(sectionY);
        int minBlockZ = SectionPos.sectionToBlockCoord(sectionZ);

        long hash = FNV_OFFSET_BASIS;
        int interiorAir = 0;
        int interiorSupported = 0;
        int interiorUnsupported = 0;
        int sampled = 0;

        for (int sy = 0; sy < SIZE; sy++) {
            int worldY = minBlockY + sy - HALO;
            for (int sz = 0; sz < SIZE; sz++) {
                int worldZ = minBlockZ + sz - HALO;
                int chunkZ = Math.floorDiv(worldZ, INTERIOR_SIZE);
                int dz = chunkZ - sectionZ;
                for (int sx = 0; sx < SIZE; sx++) {
                    int worldX = minBlockX + sx - HALO;
                    int chunkX = Math.floorDiv(worldX, INTERIOR_SIZE);
                    int dx = chunkX - sectionX;

                    BlockState state;
                    if (level.isOutsideBuildHeight(worldY)) {
                        state = airState;
                    } else {
                        LevelChunk chunk = chunks[dx + 1][dz + 1];
                        state = chunk.getBlockState(pos.set(worldX, worldY, worldZ));
                    }

                    int id = state == airState ? airId : Block.getId(state);
                    byte classification = classify(state);
                    int index = indexRaw(sx, sy, sz);
                    stateIds[index] = id;
                    classes[index] = classification;
                    sampled++;

                    hash ^= Integer.toUnsignedLong(id);
                    hash *= FNV_PRIME;
                    hash ^= classification & 0xffL;
                    hash *= FNV_PRIME;

                    if (isInteriorStorageCoordinate(sx, sy, sz)) {
                        if (classification == AIR) {
                            interiorAir++;
                        } else if (classification == SUPPORTED_FULL_CUBE) {
                            interiorSupported++;
                        } else {
                            interiorUnsupported++;
                        }
                    }
                }
            }
        }

        hash ^= Integer.toUnsignedLong(sectionX);
        hash *= FNV_PRIME;
        hash ^= Integer.toUnsignedLong(sectionY);
        hash *= FNV_PRIME;
        hash ^= Integer.toUnsignedLong(sectionZ);
        hash *= FNV_PRIME;

        return new SectionSnapshot(
                sectionX,
                sectionY,
                sectionZ,
                stateIds,
                classes,
                hash,
                interiorAir,
                interiorSupported,
                interiorUnsupported,
                sampled,
                System.nanoTime() - startNs);
    }

    /**
     * Finds the nearest section containing both air and at least one block in
     * the conservative supported-full-cube class. This prevents the validation
     * oracle from accidentally choosing a completely enclosed solid section
     * whose exposed-face stream would legitimately be empty.
     */
    private static int selectInterestingSection(ClientLevel level, LevelChunk chunk, int preferredSectionY) {
        int minSectionY = level.getMinSectionY();
        int maxSectionY = level.getMaxSectionY();
        int maxRadius = Math.max(preferredSectionY - minSectionY, maxSectionY - 1 - preferredSectionY);

        for (int radius = 0; radius <= maxRadius; radius++) {
            int below = preferredSectionY - radius;
            if (below >= minSectionY && below < maxSectionY && hasAirAndSupported(level, chunk, below)) {
                return below;
            }
            if (radius != 0) {
                int above = preferredSectionY + radius;
                if (above >= minSectionY && above < maxSectionY && hasAirAndSupported(level, chunk, above)) {
                    return above;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean hasAirAndSupported(ClientLevel level, LevelChunk chunk, int sectionY) {
        int index = level.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection section = chunk.getSection(index);
        if (section.hasOnlyAir()) {
            return false;
        }

        boolean air = false;
        boolean supported = false;
        for (int y = 0; y < INTERIOR_SIZE && !(air && supported); y++) {
            for (int z = 0; z < INTERIOR_SIZE && !(air && supported); z++) {
                for (int x = 0; x < INTERIOR_SIZE; x++) {
                    byte classification = classify(section.getBlockState(x, y, z));
                    air |= classification == AIR;
                    supported |= classification == SUPPORTED_FULL_CUBE;
                    if (air && supported) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static byte classify(BlockState state) {
        if (state.isAir()) {
            return AIR;
        }
        if (state.getRenderShape() == RenderShape.MODEL
                && state.isSolidRender()
                && state.canOcclude()
                && state.getFluidState().isEmpty()
                && !state.hasBlockEntity()
                && !state.hasOffsetFunction()) {
            return SUPPORTED_FULL_CUBE;
        }
        return UNSUPPORTED;
    }

    private static boolean isInteriorStorageCoordinate(int x, int y, int z) {
        return x >= HALO && x < HALO + INTERIOR_SIZE
                && y >= HALO && y < HALO + INTERIOR_SIZE
                && z >= HALO && z < HALO + INTERIOR_SIZE;
    }

    private static int indexRaw(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    private static int storageCoord(int local) {
        if (local < -HALO || local >= INTERIOR_SIZE + HALO) {
            throw new IndexOutOfBoundsException("Snapshot local coordinate outside halo: " + local);
        }
        return local + HALO;
    }

    public byte classification(int localX, int localY, int localZ) {
        return classes[indexRaw(storageCoord(localX), storageCoord(localY), storageCoord(localZ))];
    }

    public int stateId(int localX, int localY, int localZ) {
        return stateIds[indexRaw(storageCoord(localX), storageCoord(localY), storageCoord(localZ))];
    }

    public int sectionX() {
        return sectionX;
    }

    public int sectionY() {
        return sectionY;
    }

    public int sectionZ() {
        return sectionZ;
    }

    public long fingerprint() {
        return fingerprint;
    }

    public int interiorAirCells() {
        return interiorAirCells;
    }

    public int interiorSupportedCells() {
        return interiorSupportedCells;
    }

    public int interiorUnsupportedCells() {
        return interiorUnsupportedCells;
    }

    public int sampledCells() {
        return sampledCells;
    }

    public long captureTimeNs() {
        return captureTimeNs;
    }
}
