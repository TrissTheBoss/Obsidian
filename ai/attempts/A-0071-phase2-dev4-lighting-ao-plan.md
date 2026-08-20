# A-0071 - Phase 2 dev4 lighting and ambient-occlusion plan

Date: 2026-08-21
Status: ACTIVE
Milestone: Phase 2 P2.4 / 0.2.0-phase2-dev4
Branch: `phase2/lighting-ao-correctness`

## Objective

Advance the runtime-validated P2.3 textured real-section path to correct Minecraft 26.2 block light, sky light, directional shade and ambient-occlusion semantics for the same deliberately conservative supported SOLID full-cube subset.

P2.4 must establish immutable reference lighting data that a later Phase 3 production greedy mesher can be judged against. It must not broaden model/material coverage merely to increase face counts.

## Required grounding before implementation

Inspect the exact Loom-resolved Minecraft 26.2 client API and bytecode for:

- vanilla block/sky light lookup used by chunk/block rendering;
- packed light-coordinate representation and any emission adjustment;
- exact terrain vertex format and shader/pipeline lightmap binding contract;
- `ModelBlockRenderer` flat-light and smooth/AO paths;
- AO neighbor sampling, shade-brightness inputs, occlusion/view-blocking rules and per-corner weights;
- quad vertex/corner ordering used when AO/light values are written;
- AO diagonal/triangle choice, if topology or interpolation changes based on corner values;
- directional face shade and `BakedQuad.MaterialInfo.shade()` semantics;
- whether the existing one-block section halo is sufficient for all supported-face light/AO samples, including section borders;
- light-engine/resource lifetime facts needed to prevent future async workers from reading live world state.

Do not rely on remembered APIs or algorithms from older Minecraft versions.

## Architectural boundary

Preserve the proven P2.1-P2.3 truths:

- `SectionSnapshot` + `ReferenceFaceMesh` remain the independent geometry oracle;
- `SectionMaterialSnapshot` remains the exact immutable material/UV/tint capture for supported faces;
- all live light/world queries must be confined to an explicit render-thread capture stage;
- pure mesh construction after that capture must perform zero live world/model/light-engine reads;
- section-local geometry, UV mapping, camera transform and material resource epoch remain unchanged unless exact 26.2 evidence requires a narrow extension;
- public Blaze3D graphics remains the baseline; do not widen native Vulkan graphics ownership;
- staging and geometry resources remain bounded and completion-gated;
- unsupported cases remain explicit and measurable.

A likely shape, subject to exact API inspection, is a new immutable `SectionLightingSnapshot` keyed to the same section/reference/material identities. It may capture per-cell block/sky light and AO inputs, or directly capture deterministic per-supported-face/per-corner results, whichever preserves a reusable independent reference contract with no post-capture world reads.

## Halo requirement

The existing snapshot contains an 18^3 region: the 16^3 interior plus a one-block halo in every direction. P2.4 must prove from exact vanilla sampling that this is sufficient for supported full-cube face lighting/AO at section borders. If exact vanilla sampling reaches beyond one block, the snapshot contract must be expanded deliberately and revalidated rather than silently reading the live world from the mesh builder.

## Intended vertex/render path

Do not choose the dev4 vertex format until exact inspection identifies the active Minecraft 26.2 terrain pipeline contract.

The desired result is:

- exact baked texture UV + tint from P2.3;
- exact packed block/sky light values or exact equivalent terrain-light input;
- exact per-corner AO/shade color modulation for the supported subset;
- a public Blaze3D terrain-compatible pipeline/lightmap binding path;
- 4 vertices / 6 32-bit indices per accepted face unless exact AO diagonal semantics require a deterministic alternate index diagonal.

If vanilla changes the quad diagonal based on AO corner values, dev4 must reproduce and record that choice explicitly so Phase 3 merge keys can include it.

## Validation requirements

The reference RX 6800 XT gate should prove at minimum:

- exact `0.2.0-phase2-dev4` loads on the Vulkan backend;
- snapshot/reference/material determinism remains intact;
- duplicate lighting captures/reference evaluations are content-identical;
- supported materialized-face accounting remains exact;
- each emitted face has deterministic four-corner block light, sky light, AO/shade and any diagonal identity required by vanilla;
- border faces need no post-capture live-world/light reads;
- pure drawable construction reports zero world/light-engine reads after lighting capture;
- public terrain-compatible textured/lightmapped pipeline is valid;
- `nativeGraphicsSeam=false` and indexed-indirect live drawing remains active;
- visual comparison shows the same light gradients, face shading and AO corner darkening pattern as vanilla for the supported sample, allowing only an explicitly documented uniform comparison modulation if needed to distinguish the overlay;
- geometry/UV alignment remains perfect while moving/turning;
- all validation passes complete;
- staging/arena/indirect resources fully reclaim behind real GPU completion;
- `profilerOnlySubmissions=0`;
- process exits 0.

World-dependent light values, face counts, fingerprints and AO distributions must never be hard-coded as success values.

## Deliberate exclusions

P2.4 does not include:

- CUTOUT/non-full/custom model expansion - P2.5+;
- event-driven block/light update invalidation or section rebuild scheduling - P2.6;
- production greedy meshing - Phase 3;
- global vanilla terrain replacement;
- production-scale visibility/performance tuning - Phase 4+.

The dev3 block-break stale-overlay observation remains P2.6 lifecycle work.

## Next action

Run exact Minecraft 26.2 lighting/AO API and bytecode inspection, record the result immutably, then implement the narrow dev4 reference/capture/drawable path on this branch. Keep the production PR draft until exact CI plus reference-hardware runtime and human visual validation pass.