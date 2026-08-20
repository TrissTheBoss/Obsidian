# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Phase 1 status: **COMPLETE / runtime validated through dev9**
- Phase 1 closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
- Active development branch: `phase2/real-section-reference`
- Active draft PR: #12, `Phase 2: real section snapshot and reference mesher`
- Current development version: `0.2.0-phase2-dev1`
- Phase 2 dev1 status: **implementation compile-clean; final exact-head CI/package + RX 6800 XT runtime pending**

## Reference runtime

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666

## Phase 1 closing result

Dev9 runtime evidence: `ai/attempts/A-0052-dev9-runtime-success.md`.

Phase 1 proved the small-scale GPU-driven renderer skeleton end-to-end:

`bounded persistent staging -> generation-safe device arena -> frame graph -> narrow native compute/storage seam -> GPU scene visibility -> atomic indirect compaction + visible count -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`.

The reference RX 6800 XT run produced visibleCount=2 / culledCount=2, verified two visible and two culled pixels, verified all four compacted command slots including the zero tail, entered a world normally, and exited with no pending GPU work.

## Phase 2 dev1 - immutable real-section snapshot/reference oracle

Phase 2 intentionally begins with correctness before visible terrain replacement.

### Exact Minecraft 26.2 API grounding

Evidence: `ai/attempts/A-0054-phase2-section-api-inspection.md`.

Key findings:

- `ClientChunkCache.getChunk(x,z,ChunkStatus.FULL,false)` is the non-loading lookup; exact bytecode returns null when the chunk is unavailable.
- `ChunkAccess.getSection(s)` and `LevelChunkSection.getBlockState(x,y,z)` expose direct loaded section state.
- `LevelHeightAccessor` exposes build-height and section-index helpers.
- `Block.getId(BlockState)` / `Block.stateById(int)` provide primitive state identity.
- cached state predicates expose the conservative first support subset without model/world shape calls.

The temporary inspection workflow has been removed from the clean branch.

### `SectionSnapshot`

- target interior: 16x16x16 = 4096 cells;
- one-block halo in every direction: 18x18x18 = 5832 sampled cells;
- primitive-only post-capture state: `int[] stateIds`, `byte[] classifications`, coordinates/counts/timing/fingerprint;
- no retained ClientLevel, LevelChunk, LevelChunkSection, BlockPos, palette or other mutable live-world object;
- capture occurs on render thread only;
- complete 3x3 loaded chunk neighborhood is required before capture, using FULL,false lookups only;
- target vertical section is nearest to the player's section containing both air and at least one conservative supported full cube, avoiding an empty validation stream.

Dev1 classifications:

- AIR;
- SUPPORTED_FULL_CUBE: MODEL + solidRender + canOcclude + empty fluid + no block entity + no offset function;
- UNSUPPORTED: everything else.

Unsupported content is counted and conservatively omitted rather than approximated.

### `ReferenceFaceMesh`

- consumes only immutable primitive snapshot data;
- deterministic one-face-per-exposed-face oracle;
- emits only SUPPORTED_FULL_CUBE -> AIR faces;
- supported neighbor hides face;
- unsupported neighbor blocks emission and increments `blockedByUnsupportedFaces`;
- canonical record = packed local xyz+direction + original BlockState ID = 8 bytes;
- max possible stream 24576 faces / 196608 bytes, within the validated 256 KiB staging arena;
- every emitted record is revalidated against the snapshot;
- the same snapshot is meshed twice and both results must be content-identical.

This canonical stream is intentionally independent of the eventual terrain vertex format. It is the long-lived differential correctness oracle for Phase 3 greedy meshing.

### `RealSectionReferenceProbe`

After the world/chunks are available:

1. capture one real immutable snapshot;
2. build/validate the reference stream twice;
3. allocate one generation-safe DeviceGeometryArena span;
4. stage the real canonical face bytes through the bounded persistent upload ring;
5. copy the arena span to a readback buffer in the same useful GPU submission;
6. retire the arena allocation behind a real completion timeline handle;
7. nonblockingly poll normal staging/arena completion;
8. map readback and verify every `(packedFace,stateId)` record byte-for-byte;
9. log `worldReadsAfterSnapshot=0` as an architectural invariant.

Vanilla terrain stays active and visible. Dev1 does not yet render a replacement section.

Implementation evidence: `ai/attempts/A-0055-phase2-reference-snapshot-implementation.md`.

Clean source-only tree after removing dev9 one-shot code and the temporary inspector passed GitHub Actions run `32418735722` with build + artifact upload SUCCESS and release SKIPPED.

## Runtime success criteria for dev1

Expected invariants on the reference RX 6800 XT:

- `obsidian 0.2.0-phase2-dev1` loads on Vulkan;
- probe waits quietly until player/world/required chunks exist;
- sampledCells=5832;
- interiorAir + interiorSupported + interiorUnsupported = 4096;
- supportedCells > 0;
- faceCount > 0;
- deterministicBuilds=2;
- `worldReadsAfterSnapshot=0`;
- referenceBytes = faceCount * 8;
- usefulSubmissions=1;
- profilerOnlySubmissions=0;
- staging submitted/reclaimed bytes = referenceBytes;
- gpuVerifiedBytes = referenceBytes;
- arena allocation retires/reclaims and fully coalesces;
- world entry stays normal with vanilla terrain active;
- no pending work at shutdown;
- process exits code 0.

Exact face count, fingerprints and timings are world-dependent and must not be hard-coded.

## Next after dev1 runtime success

Phase 2 dev2 should produce the first **drawable** real section for the same conservative supported subset:

- preserve immutable snapshot/reference semantics;
- inspect exact material/model/sprite/light APIs needed for correct drawable vertices;
- generate real vertex/index data;
- upload through staging/device arena;
- create a real GPU scene record;
- render through the already-proven visibility/indirect path;
- retain vanilla terrain as a visual comparison oracle until Obsidian output is validated.

Broader block classes, light/AO, cutout/translucent and resource/model compatibility expand during Phase 2 before global terrain replacement.

## Greedy meshing roadmap

D-0024 remains active:

- Phase 2: immutable snapshot + halo + simple reference oracle + correct drawable semantics;
- Phase 3: worker-local binary/bitmask greedy mesher with reusable scratch and render-correct merge keys for material/sprite, layer, tint, light, AO, UV and special/fluid state;
- greedy output must be differential-tested against the Phase 2 reference oracle rather than serving as its own correctness source;
- Phase 4+: scale the Phase 1 GPU visibility/compaction architecture over the reduced greedy meshes.

## Immediate next action

1. Update PR #12 with exact dev1 implementation and runtime invariants.
2. Run final CI on the documented exact head.
3. Inspect/package the exact CI-built `0.2.0-phase2-dev1` JAR and checksums.
4. Runtime-test on the reference RX 6800 XT.
5. Keep PR #12 draft/unmerged until runtime validation passes.

## Relevant durable decisions

D-0014 through D-0027 remain active. D-0024 governs the production greedy mesher; Phase 2 dev1 additionally establishes the primitive-only snapshot + conservative canonical reference-face oracle as the correctness baseline for later optimized meshing.