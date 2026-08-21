# Obsidian Current State

Last updated: 2026-08-21

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Phase 1: **COMPLETE / runtime validated through dev9**
- Phase 1 closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
- Phase 2 P2.1: **COMPLETE / runtime validated and merged**
- P2.1 closing merge: `a714e19ce871bf73136d52f85a1780109aa851dd`
- Phase 2 P2.2: **COMPLETE / runtime + human visual validated and merged**
- P2.2 closing merge: `f9c64267c5becb3bd80897efdb09ed65a6ce8697`
- Phase 2 P2.3: **COMPLETE / runtime + human visual validated and merged**
- P2.3 closing merge: `667230f51222746083efe89c72265d80ac9d3929`
- Phase 2 P2.4: **COMPLETE / runtime + human visual validated and merged**
- P2.4 closing merge: `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`
- P2.4 Class-A sync merge: `a6d7d2ff96948910e22b2ab4e3e5212408ef97c2`
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Active milestone: **P2.5 - broader opaque/cutout block semantics**
- Active branch: `phase2/broader-opaque-cutout-semantics`
- Active draft PR: #22, `Phase 2 dev5: broader opaque and cutout block semantics`
- Current development version: `0.2.0-phase2-dev5`
- P2.5 status: **IMPLEMENTED / CI+PACKAGE VERIFIED / REFERENCE RX 6800 XT RUNTIME + HUMAN VISUAL VALIDATED / MERGE PENDING**

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning text. Attempts remain immutable.

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
- Vulkan backend

## Proven Phase 2 foundation

P2.1 established immutable section + one-block-halo capture and the permanent deterministic cube-face `ReferenceFaceMesh` oracle.

P2.2 established deterministic real indexed geometry, exact section/camera placement and public depth-tested indexed-indirect drawing.

P2.3 established exact baked sprite/UV/material/tint identity for the conservative SOLID full-cube subset.

P2.4 established exact Minecraft 26.2 block/sky light, directional shade and ambient-occlusion semantics for that subset through immutable capture + exact BLOCK/lightmap drawing. Final evidence: `ai/attempts/A-0075-phase2-dev4-runtime-success.md`.

The P2.1 cube oracle remains permanent and independent. P2.5 does not redefine it to mean arbitrary baked geometry.

## P2.5 / dev5 - RUNTIME + HUMAN VISUAL VALIDATED / MERGE PENDING

Evidence:

- plan: `ai/attempts/A-0076-phase2-dev5-broader-block-semantics-plan.md`;
- exact Minecraft 26.2 model/cutout/API inspection: `ai/attempts/A-0077-phase2-dev5-model-cutout-api-inspection.md`;
- implementation/package: `ai/attempts/A-0078-phase2-dev5-implementation-and-package.md`;
- reference runtime + human visual validation: `ai/attempts/A-0079-phase2-dev5-runtime-success.md`.

Temporary inspection PR #23 is closed without merge.

### Exact 26.2 generalized reference seam

Minecraft's public `ModelBlockRenderer.tesselateBlock(...)` is used on the render thread as the correctness seam. Vanilla itself performs seeded model-part selection, block offset, multiple directional quads, `getQuads(null)` general/unculled quads, shape-based directional face culling through `Block.shouldRenderFace`, exact AO-vs-flat `BlockModelLighter` processing, and exact tint multiplication. The custom `BlockQuadOutput` freezes the finished primitive result.

### Immutable generalized capture

`SectionBakedQuadSnapshot` captures exact four-vertex geometry, UVs, final AO/shade/tint colors, packed light, direction, material/layer/sprite identity and source block/state identity.

First-proof accepted block policy:

- MODEL render shape;
- no fluid;
- no block entity;
- no leaves yet;
- valid baked model;
- every emitted quad is blocks-atlas SOLID or CUTOUT.

If any emitted quad is TRANSLUCENT, wrong-atlas or otherwise unsupported, the entire block is rejected rather than partially rendered. Rejection counts remain explicit.

The exact inspected arbitrary-quad culling/light/AO reads remain within one block per axis, so the existing 18^3 halo contains every relevant block-state sample for this proof. The capture uses the live world only on the render thread; pure mesh construction performs zero post-capture live reads.

### Pure layered BLOCK mesh

`BakedSectionMesh` consumes only immutable renderer-owned captures and deterministically groups quads into contiguous SOLID then CUTOUT ranges.

Vertex layout is exact `DefaultVertexFormat.BLOCK`: float3 Position, RGBA8 Color, float2 UV0, signed-short2 packed UV2/light, 28 bytes/vertex, 4 vertices / 6 int32 indices per quad.

Exact captured color/light remains immutable. Emitted RGB is uniformly multiplied by 3/4. The validation overlay uses the already-human-validated `1/512` outward offset when a baked direction exists to avoid coplanar comparison depth fighting; this is not a production geometry rule.

Bounded maximum geometry is `3,264,000` bytes, below the existing 4 MiB validation capacity.

### Active live comparison

`RealSectionBroadModelProbe` is the active `FrameCoordinator` path. It performs duplicate deterministic cube-reference builds, generalized captures and layered mesh builds; bounded 4 MiB staging + 4 MiB geometry arena; public `RenderPipelines.SOLID_BLOCK` and `RenderPipelines.CUTOUT_BLOCK` with exact `ALPHA_CUTOUT=0.5`; blocks atlas `Sampler0`; live level lightmap `Sampler2`; separate public indexed-indirect SOLID/CUTOUT draws; resource-epoch validation before every draw; a five-second world-entry arm delay + six fully reclaimed visual passes; completion-gated geometry/indirect lifetime; `nativeGraphicsSeam=false`; and zero profiler-only submissions.

Older Phase 2 probes remain in source as inactive diagnostics.

## Dev5 CI/package evidence

Exact active-path behavior head: `ad417532ef939df8ef0826eac1aab100b2c26faa`.

Behavior-package GitHub Actions run `32484610552`: SUCCESS, artifact `9447364592`.

Documentation-synchronized head `4895df09a1757d21e1878e5a09566f8775bd98d7` passed run `32484898259`, artifact `9447477287`; its JAR and sources JAR are byte-for-byte identical to the behavior package.

Pre-runtime final head `e3a6717f42bcefe083af3b8bb79dfb88a2a5a86a` passed run `32485137568`, artifact `9447552792`, also byte-for-byte identical.

Package:

- `Obsidian-0.2.0-phase2-dev5.jar`;
- SHA-256 `68e393636e0ca216c99b3253033f701ac38aad6ba373538430a910cce238d42e`;
- sources SHA-256 `84b3e4c7846160b771202cd2f09a9453a8077da2dc8417437ad40aa99cf57ad0`;
- metadata exactly `obsidian 0.2.0-phase2-dev5`.

Packaged bytecode confirms active `FrameCoordinator -> RealSectionBroadModelProbe`, public SOLID/CUTOUT BLOCK pipelines, `ALPHA_CUTOUT`, `Sampler0`, `Sampler2`, and `drawIndexedIndirect`.

## Dev5 reference runtime result

The reference run loaded `obsidian 0.2.0-phase2-dev5` under Vulkan on the RX 6800 XT and completed all six passes. The user reported **everything looks fine**, closing the human visual gate.

Stable sampled section `(64,4,8)`:

- cube reference faces `247`;
- model blocks scanned `2440`;
- accepted blocks `321`;
- no-visible blocks `2119`;
- rejected blocks `0`;
- generalized quads `626 = 321 SOLID + 305 CUTOUT`;
- materials `6`;
- tinted quads `561`;
- block light `0..0`;
- sky light `11..15`;
- vertices `2504 = 626 * 4`;
- indices `3756 = 626 * 6`.

Every pass reported deterministic duplicate capture/build checks, `worldReadsAfterGeneralizedCapture=0`, `cubeOraclePreserved=true`, one-block-halo sufficiency, `pipelineValid=true`, `nativeGraphicsSeam=false`, public indexed-indirect drawing, blocks atlas + lightmap binding, SOLID + CUTOUT pipelines, `cutoutAlphaThreshold=0.5`, and `profilerOnlySubmissions=0`.

Final shutdown reported `generalizedSectionResult=VERIFIED`, `completedVisualPasses=6`, staging submitted/reclaimed `511056/511056`, zero upload backpressure/pending batches, arena allocations/retired/reclaimed `12/12/12`, arena used `0`, one full `4194304`-byte free span, fragmentation `0`, indirect resources retired/released `6/6`, zero pending retirements, and process exit code `0`.

## Merge authorization

The user explicitly authorized merging dev5 and reaffirmed that authorization after the successful runtime/human validation. No further merge authorization is required for PR #22 unless behavior/scope changes materially before merge.

## Deliberate P2.5 boundary

Dev5 does **not** claim leaves force-opaque option support; translucent/fluid terrain (Phase 6); event-driven section/block/light/resource update lifecycle (P2.6); persistent multi-section scene ownership (P2.7); production greedy meshing (Phase 3); global vanilla terrain replacement; or production-scale performance claims.

## Immediate next action

1. Run exact-head CI containing A-0079 and this continuity update.
2. If green, mark PR #22 ready and merge under standing authorization.
3. Record the P2.5 closing merge in a Class-A `main` synchronization while preserving all future roadmap detail.
4. Start P2.6/dev6 from synchronized `main`.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016, D-0017, D-0020, D-0023, D-0024, D-0025 and D-0027.
