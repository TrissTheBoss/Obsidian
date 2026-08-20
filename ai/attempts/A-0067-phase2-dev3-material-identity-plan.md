# A-0067 - Phase 2 dev3 material/texture identity plan

Date: 2026-08-21
Status: ACTIVE
Milestone: Phase 2 P2.3 / 0.2.0-phase2-dev3
Branch: `phase2/material-texture-identity`

## Objective

Advance the already runtime-validated P2.2 real-section drawable path from orientation/debug colors to correct Minecraft texture/material identity for the deliberately conservative supported full-cube subset, without pulling P2.4 lighting/AO or Phase 3 greedy meshing forward.

## Required grounding before implementation

Inspect the exact Loom-resolved Minecraft 26.2 client API/bytecode for:

- `BlockStateModelSet`, `BlockStateModel`, `BlockStateModelPart` and deterministic part selection;
- `BakedQuad` position, UV, direction, tint and material fields/accessors;
- `Material.Baked` and `TextureAtlasSprite` identity/lifetime/UV APIs;
- block tint/color resolution and whether it requires world/position context;
- render-layer/material classification for the supported opaque/cutout subset;
- the public textured terrain/debug pipeline shader and bind-group contracts available through Blaze3D;
- resource reload/model-manager lifetime so renderer-owned material IDs can be invalidated safely.

Do not rely on remembered APIs from older Minecraft versions.

## Dev3 architecture boundary

P2.3 should preserve these P2.1/P2.2 truths:

- `SectionSnapshot` and `ReferenceFaceMesh` remain the independent correctness oracle;
- post-snapshot meshing must not perform live world reads;
- section-local geometry and the validated world/camera transform remain unchanged;
- public Blaze3D graphics remains the baseline; do not widen the native Vulkan seam;
- staging and geometry allocation remain bounded and completion-gated;
- unsupported model/material cases must be explicit rather than approximated silently.

The implementation may introduce an immutable renderer-owned material/sprite capture/table keyed by resource-generation identity so asynchronous future meshes never retain mutable model-manager objects.

## Intended validation

The reference RX 6800 XT runtime gate should prove at minimum:

- exact dev3 version and mixin/world hook load normally;
- snapshot/reference determinism remains intact;
- materialized drawable coverage still equals the reference oracle for supported faces;
- every emitted face has deterministic sprite/material identity and UVs derived from exact baked-model data;
- no post-snapshot world reads occur during mesh construction;
- the live comparison visibly uses Minecraft textures rather than diagnostic orientation colors;
- textured faces remain perfectly aligned with vanilla while the camera moves;
- tint/material-layer behavior for the deliberately supported sample is correct or explicitly reported unsupported;
- staging/arena/resource lifetime still fully reclaims behind real GPU completion;
- `profilerOnlySubmissions=0` and normal shutdown exits 0.

Lighting and AO are intentionally not success criteria for dev3.

## Next action

Run exact Minecraft 26.2 API inspection, record the result immutably, then implement the narrow materialized drawable path on this branch and keep the PR draft until exact CI plus the required reference-hardware visual/runtime gate pass.
