# A-0082 - Phase 2 dev6 lifecycle implementation and package

Status: **IMPLEMENTED / CI + PACKAGE SUCCESS / REFERENCE RUNTIME PENDING**

Date: 2026-08-21
Branch: `phase2/section-lifecycle-rebuild`
Draft PR: #25
Version: `0.2.0-phase2-dev6`

## Scope

Implement the A-0081 exact Minecraft 26.2 lifecycle hook architecture as a one-section generation-safe correctness proof while preserving all P2.1-P2.5 geometry/material/light/cutout semantics.

## Exact hook implementation

`LevelExtractorMixin` observes, without cancelling or changing vanilla behavior:

- private central `setSectionDirty(IIIZ)` at TAIL, after vanilla has already chosen exact affected section coordinates;
- `setLevel(ClientLevel)` at TAIL for renderer-extraction world replacement/teardown.

`ClientLevelMixin` observes:

- `onChunkLoaded(ChunkPos)` at TAIL;
- `unload(LevelChunk)` at TAIL.

Minecraft 26.2 `ChunkPos` coordinates are read through exact public `x()` / `z()` accessors.

`ModelManagerMixin` observes the public `reload(...)` returned `CompletableFuture<Void>` and emits the resource/model invalidation signal only after successful future completion.

## Renderer-owned lifecycle implementation

`SectionLifecycleEvents`:

- bounded 256-entry primitive ring;
- no retained mutable Minecraft objects;
- monotonically sequenced events;
- section dirty, nearby 3x3-halo chunk load/unload, world change, resource reload and overflow reasons;
- per-frame relevant-event coalescing and counters.

`SectionGenerationGate`:

- monotonically increasing renderer generation;
- install accepted only when token equals current generation;
- deterministic stale-token self-test permanently exercises stale rejection.

`SectionSnapshot.tryCaptureSection(...)`:

- recaptures the exact tracked section identity;
- preserves the existing immutable 18^3 one-block-halo primitive snapshot;
- requires the complete 3x3 `ChunkStatus.FULL,false` neighborhood already loaded;
- never requests generation/loading.

`RealSectionLifecycleProbe`:

- owns exactly one renderer generation and lifecycle-event sequence;
- preserves duplicate deterministic `ReferenceFaceMesh`, `SectionBakedQuadSnapshot` and `BakedSectionMesh` validation;
- preserves public SOLID/CUTOUT BLOCK-format atlas/lightmap indexed-indirect comparison;
- checks for intervening lifecycle events before capture admission, GPU allocation and final upload submission;
- stale generation/event-sequence work is rejected before installation;
- draws only in LIVE state;
- invalidation immediately leaves LIVE so stale geometry is not submitted again;
- replacement/unload geometry and indirect ownership retire only through completion fences;
- resource-epoch mismatch is an invalidation, not a stale draw.

`FrameCoordinator`:

- drains exact lifecycle events at frame start;
- relevant events in one drain batch advance generation once and are coalesced;
- tracked section becomes bound after first valid install;
- stale install tokens are rejected through `SectionGenerationGate`;
- completion-retires the old generation before admitting the replacement generation;
- records rebuild installs, invalidation/coalescing counts, lifecycle reasons and rebuild latency;
- runtime gate requires repeated rebuilds plus observed section-dirty, model/resource-reload and chunk unload/load evidence with fully reclaimed resources.

The deliberate no-stale-geometry policy may produce a short blank comparison interval while an invalidated generation completion-retires and the next generation rebuilds. P2.6 prioritizes lifecycle correctness; production scheduling/latency belongs to later phases.

## CI correction

First complete implementation build run `32489999086` reached `compileClientJava` and found only four errors: Minecraft 26.2 `ChunkPos.x` / `z` fields are private. The exact inspection bytecode showed public `ChunkPos.x()` and `ChunkPos.z()` accessors. `ClientLevelMixin` was corrected accordingly; no lifecycle architecture or renderer behavior was weakened.

## Successful exact-dependency build

Behavior head: `5ba0dc66157f0140ae394e389f27a450857aea11`.

GitHub Actions run `32490137172`:

- Java 25 / Gradle 9.5.1: SUCCESS;
- Build: SUCCESS;
- Upload build artifacts: SUCCESS;
- Publish versioned release: SKIPPED.

Artifact ID: `9449425262`.

Package:

- `Obsidian-0.2.0-phase2-dev6.jar`;
- SHA-256 `3a0fb21a8c39d82d7f9a779523921213b198bc296711ba7b83a81fbcfd368c8e`;
- sources SHA-256 `b7e05afa49d6bab70387fce5b9d894e900057088201df60ec2afa5ff08dab87a`;
- metadata exactly `obsidian 0.2.0-phase2-dev6`.

## Packaged-bytecode verification

The successful behavior artifact contains:

- `ClientLevelMixin`;
- `LevelExtractorMixin`;
- `ModelManagerMixin`;
- `SectionLifecycleEvents` + `Cursor`;
- `SectionGenerationGate`;
- `RealSectionLifecycleProbe`;
- active `FrameCoordinator`.

`obsidian.mixins.json` registers all lifecycle mixins.

`javap` of packaged classes confirms:

- `FrameCoordinator -> SectionLifecycleEvents`, `SectionGenerationGate`, `RealSectionLifecycleProbe`;
- generation `advance()` and `tryInstall(...)` calls;
- lifecycle `drain(...)`, `latestSequence()` and `requestInvalidate(...)` calls;
- lifecycle probe build-event-sequence checks around admission/submission;
- `RenderPipelines.SOLID_BLOCK` and `RenderPipelines.CUTOUT_BLOCK`;
- `ALPHA_CUTOUT`;
- blocks atlas `Sampler0` and lightmap `Sampler2`;
- public `RenderPass.drawIndexedIndirect(...)`;
- exact Mixin selectors for `setSectionDirty(IIIZ)V`, `setLevel`, `onChunkLoaded`, `unload` and `reload` RETURN.

No native graphics seam was introduced.

## Required reference runtime/human lifecycle gate

Run the exact dev6 lifecycle-test package on the reference Windows 11 / Prism / RX 6800 XT Vulkan session and prove:

1. initial tracked section installs with correct P2.5 SOLID/CUTOUT visuals;
2. multiple block break/place edits inside the logged tracked-section bounds emit section-dirty invalidation and install fresh generations without stale overlay remaining visible;
3. at least three rebuild installations complete;
4. F3+T successful resource/model reload emits resource-reload invalidation and installs a fresh generation afterward;
5. travel far enough that the tracked section's 3x3 halo chunk neighborhood unloads, then return so chunk-unload and chunk-load signals both occur and the section rebuilds correctly;
6. no older generation installs after a newer invalidation;
7. cube/generalized/drawable duplicate checks remain deterministic;
8. `worldReadsAfterGeneralizedCapture=0` and `cubeOraclePreserved=true` remain true;
9. public SOLID/CUTOUT atlas/lightmap indexed-indirect comparison remains valid;
10. all staging/arena/indirect ownership fully reclaims and process exits 0;
11. shutdown reports `lifecycleGateReady=true`.

Human oracle: after edits/reload/unload-return, no stale block geometry should persist. A brief missing-overlay interval while a generation retires/rebuilds is allowed for this correctness milestone.

## Merge authorization

The user explicitly authorized dev6 merge on 2026-08-21. This is standing authorization conditional on the runtime + human lifecycle gate passing and final exact-head CI remaining green. No further authorization is required unless behavior/scope changes materially.

## Deliberate boundary

Not claimed in dev6:

- persistent multi-section scene database (P2.7);
- production async worker meshing/greedy meshing (Phase 3);
- partial remeshing;
- translucent/fluid terrain (Phase 6);
- global vanilla terrain replacement;
- production rebuild-latency/performance claims.

This attempt is immutable once committed.
