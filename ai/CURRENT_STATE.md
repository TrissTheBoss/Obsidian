# Obsidian Current State

Last updated: 2026-08-20

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range product/feature plan: `ai/MASTER_ROADMAP.md`
- Phase 1 status: **COMPLETE / runtime validated through dev9**
- Phase 1 closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
- Active development branch: `phase2/real-section-reference`
- Active draft PR: #12, `Phase 2: immutable real-section snapshot and reference oracle`
- Current development version: `0.2.0-phase2-dev1`
- Phase 2 dev1 status: **implementation + exact CI package verified; RX 6800 XT runtime validation pending**

## Continuity model

The continuity files now have explicit non-overlapping roles:

- `ai/CURRENT_STATE.md` = current truth and immediate next action;
- `ai/MASTER_ROADMAP.md` = canonical full future roadmap, phases, planned features, experiments, gates, release/compatibility direction, and roadmap-alteration procedure;
- `ai/OPERATING_MANUAL.md` = engineering and handoff procedure;
- `ai/DECISIONS.md` = durable reasoning/policy;
- `ai/ATTEMPT_LOG.md` + `ai/attempts/` = immutable evidence/history.

Roadmap creation/governance evidence: `ai/attempts/A-0057-master-roadmap-and-governance.md`.

Future agents must read the roadmap before making architectural/product planning changes and must follow its Class A-E change protocol rather than silently rewriting planned scope.

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

### Exact package verification

Evidence: `ai/attempts/A-0056-phase2-dev1-final-package.md`.

- documented code head: `80a518653e034200532eb0c75033b0726ef8edd5`;
- package CI run: `32418949171`;
- artifact ID: `9424977392`;
- packaged version: `0.2.0-phase2-dev1`;
- main JAR SHA-256: `3ab8c5437b73789a00ff90d0a87d0af87921bcc85a2498db96b39b3f4d7b2a23`;
- sources SHA-256: `69f6fb34b700ee965897569afb028a8e531e1228e6368f9ff554f972fbfe90f0`;
- required Phase 2 classes present;
- completed dev9 one-shot probe absent.

The commits after this package verification are documentation/continuity changes only; no Java/source behavior has changed since the verified dev1 JAR.

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

Phase 2 dev2 / roadmap item P2.2 should produce the first **drawable** real section for the same conservative supported subset:

- preserve immutable snapshot/reference semantics;
- inspect exact material/model/sprite/light APIs needed for correct drawable vertices;
- generate real vertex/index data;
- upload through staging/device arena;
- create a real GPU scene record;
- render through the already-proven visibility/indirect path;
- retain vanilla terrain as a visual comparison oracle until Obsidian output is validated.

Broader block classes, light/AO, cutout/translucent and resource/model compatibility expand during Phase 2 before production greedy meshing.

## Greedy meshing roadmap

The complete plan is now in `ai/MASTER_ROADMAP.md`; D-0024 remains the durable greedy-meshing decision.

- Phase 2: immutable snapshot + halo + simple reference oracle + correct drawable semantics;
- Phase 3: worker-local binary/bitmask greedy mesher with reusable scratch and render-correct merge keys for material/sprite, layer, tint, light, AO, UV and special/fluid state;
- greedy output must be differential-tested against the Phase 2 reference oracle rather than serving as its own correctness source;
- Phase 4+: scale the Phase 1 GPU visibility/compaction architecture over the reduced greedy meshes.

## Immediate next action

1. Runtime-test the already package-verified `0.2.0-phase2-dev1` JAR on the reference RX 6800 XT.
2. Record the real-section snapshot/reference result in a new immutable attempt.
3. Keep PR #12 draft/unmerged until runtime validation passes.
4. If dev1 passes, promote/merge it with `[no-release]` when explicitly authorized and start P2.2 from the resulting `main`.

## Relevant durable decisions

D-0014 through D-0027 remain active. D-0024 governs the production greedy mesher. The canonical long-range feature/phase plan and the procedure for changing it now live in `ai/MASTER_ROADMAP.md` and are mandatory continuity reading.