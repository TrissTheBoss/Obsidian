# A-0112 - Phase 3 P3.4 dev6 render-key sidecar plan

**Date:** 2026-08-22  
**Branch:** `phase3/render-correct-merge-key`  
**Planned version:** `0.3.0-phase3-dev6`  
**Result:** `SUCCESS` — scope frozen before implementation.

## Objective

Start P3.4 — render-correct merge key — with a correctness-first sidecar that proves which conservative P3.2/P3.3 canonical faces map uniquely to exact vanilla-baked SOLID/CUTOUT quads and whether neighboring topology faces are genuinely render-equivalent.

Dev6 does **not** change GPU-emitted geometry. Existing generalized `BakedSectionMesh` remains the authoritative production drawable.

## Inputs already proven

- `SectionSnapshot` — immutable conservative section + halo topology/state identity;
- `BinarySectionVisibility` — exact conservative visible canonical face set;
- `GreedySectionRectangles` — deterministic topology-only rectangle partition;
- `SectionBakedQuadSnapshot` — render-thread capture of accepted vanilla-baked SOLID/CUTOUT quad positions, UVs, exact colors, packed light, material/sprite/layer/tint/shade/emission/animation identity, source block/state and direction.

## Frozen dev6 scope

Implement a pure worker-side render-key sidecar (working name `CanonicalFaceRenderKeys`) that:

1. consumes only immutable renderer-owned `SectionSnapshot`, `BinarySectionVisibility` and `SectionBakedQuadSnapshot`;
2. maps a P3.2 canonical face to a baked quad only when exactly one source baked quad can be proven to be the same full unit-cube face;
3. requires exact source-block identity, exact face direction mapping, exact axis-aligned full-face geometry and all four expected geometric corners;
4. marks zero-match and multiple-match canonical faces as passthrough / not greedy-render-eligible;
5. never maps arbitrary noncanonical/model geometry merely because it shares a block/state ID;
6. exposes exact render-equivalence only by comparing all output-affecting captured properties of the mapped quads;
7. measures how many topology faces and adjacency edges are render-key compatible in real production jobs;
8. remains a sidecar/differential product with no greedy GPU emission.

## Exact render-equivalence contract for dev6

Two uniquely mapped canonical faces are render-equivalent only when all relevant captured output truth agrees, including:

- same canonical direction/orientation;
- same render layer;
- exact `MaterialIdentity` equality: atlas, sprite, material flags, tint index, shade flag, light emission, animation flag;
- same geometric corner ordering/winding/diagonal representation;
- exact per-geometric-corner raw UV float bits;
- exact per-geometric-corner ARGB color;
- exact per-geometric-corner packed light.

The comparison is based on geometric corner identity rather than incidental baked-quad vertex array order, while separately requiring the same corner-order signature so winding/triangulation semantics are not silently changed.

State/block ID equality is **not** treated as proof of render equivalence. Conversely, visually/output-identical faces need not be rejected solely because state IDs differ, provided every captured output-affecting property above agrees.

## Conservative geometry recognition

For each candidate baked quad:

- decode `sourceBlock` to local x/y/z;
- map Minecraft `Direction.ordinal()` to P3.2 direction order explicitly (never assume ordinal equality);
- require the corresponding P3.2 face bit to exist;
- require all four positions to lie exactly on the expected source-cell unit face boundaries using exact float-bit equality to integer local coordinates;
- require exactly one vertex for each of the four geometric corners;
- reject offset, inset, partial, rotated/non-axis-aligned, duplicate-corner or otherwise noncanonical geometry from greedy eligibility.

This intentionally leaves many generalized vanilla model quads on passthrough rather than approximating them.

## Representation / boundedness

Correctness-first retained mapping may use one `short` slot per possible canonical face (`6 * 4096 = 24,576`), with `0` = unmapped, positive `sourceQuad+1` = unique mapping, and `-1` = ambiguous mapping. Since `SectionBakedQuadSnapshot.MAX_QUADS=24,000`, source-quad indices fit safely.

Expected retained mapping bound: `24,576 * 2 = 49,152` bytes per sidecar plus small aggregate metadata. Worker-local scratch may use bounded primitive counters/temporary arrays only.

No allocation-heavy per-face object graph is allowed.

## Validation

Every primary build must self-validate:

- mapped faces are present in `BinarySectionVisibility`;
- mapped source block and direction exactly match the canonical face;
- mapped quad is exact full-unit canonical geometry;
- every visible face is accounted as unique / unmapped / ambiguous;
- no non-visible canonical face is marked unique;
- unique mapping points to one and only one source quad.

On the existing first/every-64-local-completions audit cadence:

- rebuild the render-key sidecar and require exact deterministic equality;
- retain P3.2 visibility and P3.3 rectangle/reference audits;
- keep existing `BakedSectionMesh` determinism audit.

## Metrics / runtime gate

Add production metrics for:

- render-key builds;
- visible canonical faces;
- uniquely mapped/render-key-eligible faces;
- unmapped faces;
- ambiguous faces;
- eligible percentage;
- canonical baked quads recognized;
- noncanonical baked quads ignored;
- same-key adjacent face pairs;
- different-key adjacent face pairs;
- retained bytes;
- build time;
- deterministic audits/matches.

New coordinator gate: `renderMergeKeyEvidenceReady=true` (name may vary only for clarity), requiring:

- existing Phase 3, scheduler, P3.2 and P3.3 gates remain true;
- render-key builds cover completed production jobs;
- visible-face accounting is exact;
- nonzero uniquely mapped faces are observed on real terrain;
- at least one same-key adjacency and at least one different-key adjacency are observed, proving the comparator is exercised rather than trivially accepting/rejecting everything;
- deterministic audits are nonzero and all match;
- zero worker failure/rejection/join failure;
- clean worker/staging/arena/resource lifetime shutdown.

Final diagnostics must state:

- `greedyRectangleSidecarIntegrated=true`;
- `renderMergeKeySidecarIntegrated=true`;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false` for dev6 unless later work in the same milestone explicitly proves production geometry readiness.

## Non-goals

Dev6 does not:

- emit merged greedy quads to GPU;
- replace `BakedSectionMesh`;
- merge arbitrary generalized model quads;
- solve section-border/halo light/AO correctness (P3.5 remains planned);
- change T-junction policy (P3.6);
- claim final performance/benchmark closure (P3.8);
- publish a public release.

## Promotion rule

Compilation is not sufficient. Dev6 requires exact CI/package plus a real reference runtime demonstrating `renderMergeKeyEvidenceReady=true` and clean lifetime behavior. Because GPU geometry remains unchanged, human visual inspection is a regression guard; if a later P3.4 step begins emitting merged geometry, renewed explicit human visual validation becomes mandatory.

All internal commits/merges use `[no-release]`.