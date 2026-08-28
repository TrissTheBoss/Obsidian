# A-0141 — Phase 3 P3.4 dev11 promotion and P3.5 activation

**Date:** 2026-08-29  
**Result:** SUCCESS — P3.4 complete; P3.5 border/halo correctness activated.

## Inputs
Dev11 closure evidence is immutable in:
- A-0139 — cleaned canonical package;
- A-0140 — reference automated runtime SUCCESS plus explicit human visual PASS.

Exact dev11 evidence head:
- `66c38250426cd6d35629fda088ade768420dee0f`

Evidence-head workflow:
- `33218461794` — Java 25 / Gradle 9.5.1 build SUCCESS; artifact upload SUCCESS; versioned release publishing SKIPPED.

## Promotion
The original implementation PR #43 remained draft only because the connected GitHub ready-for-review GraphQL wrapper failed while decoding a now-invalid `Repository.fullDatabaseId` field. GitHub's REST merge endpoint then correctly rejected #43 because it was still draft.

No source, runtime, or CI defect existed. To avoid altering the proven head, #43 was closed as superseded and non-draft promotion PR #44 was opened from the exact same branch/head.

PR #44 merged the exact evidence head with `[no-release]` as:
- **`b01ff98c4dbe6e548550f86784547afc37db2b2d`**

The workaround changed no code or evidence between final validation and merge.

## P3.4 completion
P3.4's roadmap goal was to move from topology-only greedy rectangles to render-correct merge candidates and GPU-emitted greedy geometry without weakening exact material/UV/color/light/model semantics.

The dev6-dev11 chain now provides:
- conservative canonical render-key identification;
- exact render-key-aware merge candidates;
- ordinary four-vertex interpolation safety classification;
- exact repeat-aware UV representability;
- explicit repeat transport/sampling proof;
- real public-Blaze3D repeat-aware greedy GPU emission;
- exact source suppression/replacement accounting;
- exact fallback for unsupported/generalized/ambiguous/unsafe geometry;
- four fixed indexed-indirect draw classes;
- completion-gated upload/arena/resource lifetime;
- real Vulkan runtime validation and explicit human visual PASS.

Therefore **P3.4 — Render-correct merge and emission semantics is COMPLETE**.

`renderCorrectMergeKeyComplete=false` remains a runtime diagnostic inherited from the narrower canary implementation and must not be reinterpreted as evidence that the roadmap item failed. Any later change to that diagnostic's semantics should be frozen separately. P3.4 completion means the frozen dev6-dev11 roadmap chain has satisfied its required correctness/emission proof; it does not mean every future terrain/render class is merged.

## Scope explicitly not consumed
- A-0101 remains the permanent closure of the old fixed-anchor unload/return lifecycle proof.
- P3.6 broader T-junction policy remains PLANNED; dev11's visual PASS closes only the concrete canary promotion gate.
- No native Vulkan graphics seam expansion occurred; D-0023/D-0025 remain intact.
- P3.5+ phase order is unchanged.

## Next canonical milestone
Activate:
- **P3.5 — Border/halo correctness**

Roadmap objective:
> Validate face visibility, light/AO and rebuild invalidation across section boundaries with no worker-thread live-world reads.

Immediate work must begin by freezing a P3.5 correctness/runtime contract against current source truth. The new slice must preserve:
- immutable renderer-owned capture;
- halo/neighbor data sufficient for cross-section visibility and supported light/AO semantics;
- zero worker live-world reads after capture;
- generation-safe stale-result rejection;
- exact fallback/oracle behavior;
- bounded scheduler/staging/arena/resource lifetime;
- dev11 render-correct greedy emission semantics for already-supported faces.

Do not consume P3.6 T-junction policy while implementing P3.5 unless concrete new hardware evidence forces a separately recorded roadmap/decision change.

## Status after this attempt
- Phase 3: ACTIVE
- P3.1: COMPLETE
- P3.2: COMPLETE
- P3.3: COMPLETE
- **P3.4: COMPLETE through dev11**
- **P3.5: ACTIVE — border/halo correctness**
- P3.6+: remain PLANNED/EXPERIMENTAL as already recorded
