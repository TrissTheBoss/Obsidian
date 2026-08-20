# A-0054 - Exact Minecraft 26.2 real-section API inspection

Date: 2026-08-20
Status: SUCCESS
Milestone: Phase 2 dev1 API grounding

## Purpose

Ground the first real Minecraft section snapshot/reference-mesher path in the exact Minecraft 26.2 client/common bytecode instead of remembered mappings.

## Inspection method

A temporary GitHub Actions workflow resolved the Loom Minecraft 26.2 artifacts and ran `javap` against the combined client + common classpath. The first inspection used only the client JAR and usefully revealed that common world classes are packaged separately; the workflow was corrected rather than treating missing common classes as missing APIs.

Final targeted inspection run: `32417997403`.
Final inspection artifact: `9424645592`.
The temporary workflow was removed before the clean testable branch head.

## Exact useful APIs

### Client world and non-loading chunk lookup

- `Minecraft.getInstance()` is public.
- `Minecraft.level` is the current `ClientLevel`.
- `Minecraft.player` is the current local player.
- `ClientLevel.getChunkSource()` returns `ClientChunkCache`.
- `ClientChunkCache.getChunk(int x, int z, ChunkStatus status, boolean create)` returns `LevelChunk`.
- Exact bytecode shows `getChunk(..., ChunkStatus.FULL, false)` returns `null` when the requested loaded chunk is unavailable rather than triggering generation/loading.

This is the required safe halo-neighborhood lookup for a renderer snapshot.

### Chunk/section access

- `ChunkAccess.getSections()` and `getSection(int)` are public.
- `LevelChunkSection.getBlockState(int,int,int)` is public direct section-state access.
- `LevelChunkSection.hasOnlyAir()` is public.
- `LevelHeightAccessor` exposes `getMinY`, `getMaxY`, `getMinSectionY`, `getMaxSectionY`, `getSectionIndex`, `getSectionIndexFromSectionY`, and `getSectionYFromSectionIndex`.
- `SectionPos` exposes block/section coordinate conversion and player-section construction.

### Primitive BlockState identity

- `Block.BLOCK_STATE_REGISTRY` is a public state ID mapper.
- `Block.getId(BlockState)` is public.
- `Block.stateById(int)` is public.

This permits the immutable snapshot to store primitive integer state IDs rather than retaining live world/chunk/palette objects.

### Conservative first supported render class

`BlockBehaviour.BlockStateBase` exposes cached/pure state queries needed for a deliberately narrow first classification:

- `isAir()`;
- `getRenderShape()`;
- `isSolidRender()`;
- `canOcclude()`;
- `hasBlockEntity()`;
- `hasOffsetFunction()`;
- `getFluidState()`; and `FluidState.isEmpty()`.

`RenderShape` values are `INVISIBLE` and `MODEL`.

Phase 2 dev1 therefore treats a non-air state as reference-supported only when:

- render shape is MODEL;
- solidRender is true;
- canOcclude is true;
- fluid state is empty;
- no block entity;
- no position offset function.

Everything else is explicitly UNSUPPORTED for dev1 rather than visually approximated.

## Snapshot design consequence

The chosen snapshot is primitive-only after capture:

- one 16x16x16 target section;
- one-block halo in each direction -> 18x18x18 = 5832 sampled cells;
- `int stateId[5832]`;
- `byte classification[5832]`;
- section coordinates, counts, timing and fingerprint only.

No `ClientLevel`, `LevelChunk`, `LevelChunkSection`, `BlockPos`, palette or mutable world object is retained.

The capture prechecks a complete 3x3 already-loaded chunk neighborhood with `ChunkStatus.FULL,false` before sampling, so the one-block halo does not cause generation or synchronous loading.

## Reference-mesher consequence

The mesher consumes only `SectionSnapshot`. It cannot read the live world by type.

Dev1 emits a canonical face only for:

`supported-full-cube cell -> AIR neighbor`.

- supported neighbor: face hidden;
- unsupported neighbor: face conservatively suppressed and counted as blocked-by-unsupported;
- unsupported source cell: no reference face emitted.

This avoids claiming correctness for models/layers/light/AO semantics not yet implemented.

Each canonical face carries:

- packed local x/y/z + face direction;
- original Minecraft BlockState integer ID.

This 8-byte record is intentionally independent of the eventual terrain vertex format and becomes the coverage/material-identity oracle for Phase 3 greedy meshing.

## Result

The exact API surface supports the planned no-world-read reference mesher without Mixins or native Vulkan changes. The first implementation compiled cleanly against Minecraft 26.2.