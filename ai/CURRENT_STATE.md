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
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Active milestone: **P2.3 - correct textures/material identity**
- Active branch: `phase2/material-texture-identity`
- Active draft PR: #16, `Phase 2 dev3: texture and material identity`
- Current development version: `0.2.0-phase2-dev3`
- P2.3 status: **exact Minecraft 26.2 API grounded + implementation complete + CI/package verified; reference RX 6800 XT runtime/visual validation pending**

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Roles:

- `CURRENT_STATE.md` = current truth and immediate next action;
- `MASTER_ROADMAP.md` = canonical long-range product/phase/feature plan and roadmap governance;
- `OPERATING_MANUAL.md` = engineering and handoff procedure;
- `DECISIONS.md` = durable architectural policy;
- attempts = immutable evidence/history;
- source, CI and runtime logs = actual implementation/proof.

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

## Proven foundations

Phase 1 established:

`bounded persistent staging -> generation-safe device arena -> fixed frame graph -> narrow native compute/storage seam -> GPU scene visibility -> atomic indirect compaction + visible count -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`

P2.1 permanently established immutable real-section + halo capture and the deterministic `ReferenceFaceMesh` correctness oracle with no post-snapshot live-world reads during meshing.

P2.2 established deterministic real indexed geometry, exact section/camera placement and public depth-tested indexed-indirect world drawing. Runtime evidence is `ai/attempts/A-0066-phase2-dev2-runtime-success.md`; closing merge is `f9c64267c5becb3bd80897efdb09ed65a6ce8697`. The tester explicitly reported the orientation-colored geometry was perfectly aligned with vanilla.

The permanent P2.1 reference oracle remains independent of all later optimized/materialized representations.

## P2.3 / dev3 - ACTIVE

Planning: `ai/attempts/A-0067-phase2-dev3-material-identity-plan.md`.

Exact Minecraft 26.2 API evidence: `ai/attempts/A-0068-phase2-dev3-material-api-inspection.md`.

Implementation/package evidence: `ai/attempts/A-0069-phase2-dev3-implementation-and-package.md`.

### Exact 26.2 model/material contract now used

Hosted Loom inspection proved the active path is:

`BlockStateModelSet -> BlockStateModel -> BlockStateModelPart -> net.minecraft.client.resources.model.geometry.BakedQuad`.

Dev3 follows vanilla's deterministic model selection seed `BlockState.getSeed(worldPos)`, decodes packed quad UVs through exact `UVPair.unpackU/V`, reads `BakedQuad.MaterialInfo` for sprite/layer/tint/material flags, resolves world-context tint through `BlockTintSource.colorInWorld(...)`, and binds the live blocks atlas through public `RenderPass.bindTexture`.

Temporary inspection PR #17 was closed without merge after A-0068 recorded the findings.

### Immutable material extraction

New `SectionMaterialSnapshot` is the only P2.3 stage allowed to query live model/tint/resource state.

For each permanent reference face it:

- reconstructs the captured BlockState from its immutable state ID;
- performs vanilla-seeded baked-model part selection;
- conservatively requires no general/null-direction quads and exactly one compatible directional quad;
- currently accepts only `ChunkSectionLayer.SOLID` for the first dev3 proof;
- explicitly counts CUTOUT/TRANSLUCENT/complex/missing/wrong-atlas/geometry/tint rejections;
- verifies the baked quad covers the same canonical unit face as the P2.1 oracle;
- maps exact baked UVs into Obsidian's canonical four-corner order;
- captures block-atlas and sprite identity, layer, flags, tint index, shade, light emission and animation identity;
- captures exact world-context tint while live world access is legal;
- assigns deterministic renderer-owned material IDs;
- retains only immutable values after capture, never live model/quad/sprite/world objects;
- captures a model-set/blocks-atlas resource epoch so stale materialized geometry is rejected before draw.

Two material captures must be content-identical in the validation probe.

### Pure textured mesh

New `MaterializedSectionMesh` consumes only `SectionSnapshot`, `ReferenceFaceMesh` and `SectionMaterialSnapshot`.

It performs no live Minecraft world/model/resource reads and emits:

- `DefaultVertexFormat.POSITION_TEX_COLOR`;
- float3 section-local position;
- float2 exact baked UV;
- RGBA8 captured tint with a deliberate 3/4 RGB comparison modulation;
- 4 vertices / 6 32-bit indices per accepted materialized face.

The exact tint remains stored independently; the 3/4 modulation exists only so the texture overlay is human-visible against the already-rendered vanilla surface. Brightness/AO parity is intentionally not claimed until P2.4.

### Live textured comparison

New `RealSectionMaterialProbe` is now the active `FrameCoordinator` path.

It preserves the proven P2.2 placement/lifetime system and adds:

- duplicate deterministic material captures;
- duplicate deterministic pure materialized mesh builds;
- public `GUI_TEXTURED` position/tex/color shader contract;
- proven reversed-depth comparison state;
- live blocks atlas bound as `Sampler0`;
- public indexed-indirect draw;
- model/atlas resource-epoch check before every comparison draw;
- five-second world-entry delay plus six sequential fully reclaimed visual passes;
- completion-gated vertex/index/indirect retirement;
- `nativeGraphicsSeam=false`;
- zero profiler-only submissions.

The completed P2.2 `RealSectionDrawableProbe` remains in source as historical/proven diagnostic code but is no longer the active coordinator path.

### Validation-only capacity

Dev3 remains within the explicit validation capacities:

- staging ring: 4 MiB;
- device geometry arena: 4 MiB.

Worst-case POSITION_TEX_COLOR + 32-bit index data for the permanent reference maximum remains below 3 MiB. These values are validation capacities, not production renderer memory budgets.

## Dev3 CI/package evidence

Exact behavior/code head before documentation-only synchronization: `a7ae05ba8da391c052804d08b5a9ed4546c3311c`.

GitHub Actions run `32428077000`:

- Java 25 / Gradle 9.5.1 build: SUCCESS;
- artifact upload: SUCCESS;
- public release: SKIPPED.

Artifact ID: `9428132378`.

Package:

- `Obsidian-0.2.0-phase2-dev3.jar`;
- main JAR SHA-256: `7eb4cc26ad48ba10e986cd2069720a56f6f4079ec724dec6223ee6dd25376e58`;
- sources SHA-256: `03d6ab63ce81fe96d88148d5f3b6b078d87f0bef252957a5d1a42ecca9d4e818`;
- metadata: exactly `obsidian 0.2.0-phase2-dev3`.

Package inspection confirmed `SectionMaterialSnapshot`, `MaterializedSectionMesh` and `RealSectionMaterialProbe` are present and that active `FrameCoordinator` constructs the new dev3 probe. Bytecode also confirms `GUI_TEXTURED`, `POSITION_TEX_COLOR`, blocks-atlas identity and `Sampler0` are present in the active path.

## Dev3 runtime success criteria

The reference RX 6800 XT run must prove:

- exact `obsidian 0.2.0-phase2-dev3` loads on Vulkan;
- existing GameRenderer world hook applies normally;
- P2.1 snapshot/reference duplicate determinism remains intact;
- `deterministicMaterialCaptures=2`;
- at least one conservative SOLID reference face materializes;
- every rejected reference face is explicitly accounted for by the material rejection counters;
- materialized face count = accepted material faces;
- drawable vertices = materialized faces * 4;
- drawable indices = materialized faces * 6;
- `deterministicDrawableBuilds=2`;
- `worldReadsAfterMaterialCapture=0` for pure mesh construction;
- public textured pipeline is valid;
- `nativeGraphicsSeam=false`;
- indexed-indirect live drawing executes;
- blocks atlas remains bound and resource epoch remains stable through each comparison pass;
- the visible overlay uses recognizable Minecraft block textures rather than orientation colors;
- supported-face UVs are not mirrored, rotated incorrectly or stretched relative to vanilla;
- geometry remains perfectly world/camera aligned while moving;
- supported tinted faces, when encountered, have sensible tint identity;
- brightness/AO differences are ignored for this gate because P2.4 remains intentionally absent;
- all 6 visual passes complete;
- `profilerOnlySubmissions=0`;
- staging fully reclaims;
- all arena allocations retire/reclaim behind completion;
- indirect resources retire/release behind completion;
- final arena used=0, one full 4 MiB free span, fragmentation=0;
- no pending upload/arena/resource retirements at clean shutdown;
- process exits code 0.

Exact world coordinates, terrain counts, material counts, rejection counts and fingerprints are world/resource-pack dependent and must not be hard-coded.

## Deliberate P2.3 boundary

Dev3 does **not** claim:

- block/sky lighting or ambient occlusion - P2.4;
- broad cutout/non-full/custom-model support - P2.5+;
- production greedy meshing - Phase 3;
- global vanilla terrain replacement;
- production-scale visibility/performance - Phase 4+.

Unsupported cases remain explicit rather than silently approximated.

## Immediate next action

1. Run final exact-head Java 25 / Gradle 9.5.1 CI after continuity/PR synchronization.
2. Runtime-test the exact `0.2.0-phase2-dev3` artifact on the reference RX 6800 XT.
3. Enter a normal world, allow the five-second arm delay, then inspect the six-pass textured overlay while moving/turning the camera.
4. Explicitly report texture visibility, UV orientation/mirroring/stretch, geometry alignment and any obviously wrong tint. Ignore lighting/AO brightness differences for dev3.
5. Save the complete Prism log and record the result immutably.
6. Keep PR #16 draft/unmerged until this gate passes.

## Relevant durable decisions

D-0014 through D-0027 remain active. Especially relevant:

- D-0016 completion-gated lifetime;
- D-0017 bounded/backpressured staging;
- D-0020 generation-safe arena identity;
- D-0023 public Blaze3D graphics first;
- D-0024 permanent reference oracle before production binary/bitmask greedy meshing;
- D-0025 narrow native Vulkan seam only for missing compute/storage capability;
- D-0027 public indexed-indirect graphics baseline unless profiling later justifies native indirect-count integration.
