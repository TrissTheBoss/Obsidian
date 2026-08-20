# A-0055 - Phase 2 dev1 immutable section snapshot/reference oracle implementation

Date: 2026-08-20
Status: COMPILE SUCCESS / RUNTIME PENDING
Milestone: Phase 2 dev1
Version: `0.2.0-phase2-dev1`
Baseline: Phase 1 closing merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
PR: #12

## Intent

Cross from synthetic Phase 1 workloads to real Minecraft terrain data without yet replacing vanilla terrain. Prove that Obsidian can snapshot a real loaded section into immutable primitive data, derive a deterministic conservative face oracle without live-world reads, move that real face stream through the validated staging/device-arena path, and verify it after GPU copy/readback.

## Implementation

### `SectionSnapshot`

- captures on the render thread only;
- waits for non-null `Minecraft.level` and `Minecraft.player`;
- selects the player's chunk and nearest vertical section containing both air and at least one conservative supported-full-cube state;
- all chunk access uses `ClientChunkCache.getChunk(..., ChunkStatus.FULL, false)`;
- requires all 3x3 loaded neighbor chunks before capture so the one-block halo is complete without loading/generation side effects;
- captures 18x18x18 = 5832 cells around one 16x16x16 section;
- stores only `int[] stateIds` plus `byte[] classifications` and primitive metadata after capture;
- uses `Block.getId(BlockState)` for stable in-process state identity;
- computes a deterministic FNV-1a-style fingerprint over state IDs/classification + section coordinates;
- records interior air/supported/unsupported counts and capture time.

Conservative dev1 supported class:

- MODEL render shape;
- solid render;
- can occlude;
- empty fluid state;
- no block entity;
- no offset function.

Air and unsupported are separate classifications. Unsupported cases are never silently treated as supported geometry.

### `ReferenceFaceMesh`

- depends only on primitive `SectionSnapshot` data;
- no Minecraft world/chunk reads;
- deterministic loop and fixed direction order;
- emits one face only for supported-full-cube -> air;
- supported neighbor hides the face;
- unsupported neighbor suppresses the face and increments `blockedByUnsupportedFaces`;
- emits 8 bytes per canonical face: packed local xyz+direction plus original state ID;
- maximum possible stream is 24576 faces / 196608 bytes, within the validated 256 KiB staging ring;
- validates every emitted record back against the immutable snapshot;
- second independent build must be byte/content-identical to the first;
- reports face/quad/vertex/index counts, byte size, fingerprint and mesh time.

### `RealSectionReferenceProbe`

- remains WAITING_WORLD until the required real client world/chunks exist;
- captures one snapshot and builds the reference oracle twice;
- requires deterministic equality and non-empty face stream;
- allocates one generation-safe `DeviceGeometryArena` span;
- stages the canonical face stream through the existing bounded persistent upload ring;
- copies arena bytes to a MAP_READ readback buffer in the same useful command submission;
- creates an independent lightweight timeline completion handle for arena retirement;
- uses existing completion-gated staging reclamation and arena retirement;
- after completion, maps the readback and verifies every `(packedFace,stateId)` record;
- logs `worldReadsAfterSnapshot=0` as an architectural invariant;
- vanilla terrain remains active and visually unchanged.

### `FrameCoordinator`

- completed dev9 one-shot visibility probe removed;
- reusable Phase 1 visibility/compute infrastructure remains in the tree for later real-section rendering;
- Phase 2 probe is retried each frame only until a valid loaded-world snapshot can be captured;
- normal end-frame polling still reclaims staging/arena work nonblockingly;
- shutdown reports section coordinates, snapshot/mesh fingerprints, timing/counts, bytes, submission count and all staging/arena safety metrics.

### Bootstrap/version

- version advanced to `0.2.0-phase2-dev1`;
- startup explicitly states vanilla terrain remains active;
- Phase 2 dev1 is a correctness/reference milestone, not terrain replacement.

## Compile gates

The full implementation compiled successfully against exact Minecraft 26.2 / Java 25 / Gradle 9.5.1.

After compile success:

- completed dev9 `VisibilityCompactionProbe` was removed;
- temporary Phase 2 API inspection workflow was removed;
- the clean source-only tree passed GitHub Actions run `32418735722` with build + artifact upload SUCCESS; versioned release was correctly skipped.

## Runtime success criteria

On the reference RX 6800 XT test:

- correct `0.2.0-phase2-dev1` loads on Vulkan;
- probe initially waits until world/player/chunks exist, then captures exactly one real section;
- sampledCells=5832;
- interior cells=4096 and air+supported+unsupported=4096;
- supportedCells > 0 and faceCount > 0;
- deterministicBuilds=2;
- snapshot/mesh fingerprints are logged;
- every canonical face validates against the snapshot;
- one useful GPU submission, zero profiler-only submissions;
- staging submitted/reclaimed equals `faceCount*8` bytes;
- GPU readback verifies the same number of bytes;
- arena allocation retires/reclaims safely and returns to one 524288-byte free span;
- no pending upload/retirement work at shutdown;
- vanilla world enters normally and process exits code 0.

## Next milestone if runtime succeeds

Phase 2 dev2 should turn the immutable snapshot/reference semantics into the first **drawable** real section for the same conservative supported subset: material/model-aware vertex/index generation, arena upload, a real GPU scene record, and rendering through the already-proven visibility/indirect path while vanilla remains available for visual comparison.

Only after the reference/drawable semantics are stable should Phase 3 introduce binary/bitmask greedy meshing.