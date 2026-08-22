# A-0122 - Phase 3 P3.4 dev8 ordinary-quad emission-safety plan

**Date:** 2026-08-23  
**Branch:** `phase3/rectangle-emission-safety`  
**Planned version:** `0.3.0-phase3-dev8`  
**Result:** `SUCCESS` — the correctness-first dev8 classifier contract is frozen before implementation.

## Objective

Determine which dev7 merge candidates, if any, can be represented by one ordinary four-vertex `DefaultVertexFormat.BLOCK` rectangle **without changing the current shader/atlas semantics**.

Dev8 remains sidecar-only. It does not replace source quads and must keep:

- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` as the authoritative GPU drawable.

The purpose is to separate valid render-key grouping from actual four-vertex emission feasibility using exact captured source payloads.

## Proven inputs

- `RenderMergeCandidates` — exact deterministic dev7 partition of all uniquely mapped same-render-key canonical faces.
- `CanonicalFaceRenderKeys` — exact per-face render equivalence and unique source-quad mapping.
- `SectionBakedQuadSnapshot` — exact source positions, raw atlas UV floats, exact ARGB colors, packed light, material identity and source-block identity.

Dev8 never reads the live world and does not infer semantics from block/state IDs.

## Current drawable facts

`BakedSectionMesh` emits four vertices per source quad using the block vertex payload:

- float3 position;
- RGBA8 color;
- float2 atlas UV0;
- packed light UV2.

`SectionBakedQuadSnapshot` captures the corresponding exact per-corner source values. Dev6/dev7 already prove that all faces inside one candidate repeat the same geometric-corner payload.

A repeated unit-face payload is not automatically one-large-quad-compatible because source quads restart that four-corner field every cell, while one large quad interpolates only once across the complete rectangle.

## Geometric corner convention

Use the existing canonical corner code from dev6:

- corner `0` = `(uLow, vLow)`;
- corner `1` = `(uHigh, vLow)`;
- corner `2` = `(uLow, vHigh)`;
- corner `3` = `(uHigh, vHigh)`.

Candidate `u/v` axes use the same P3.3/dev7 directional mapping.

The classifier independently maps the representative baked quad's four source vertices to these four geometric corners and rejects any identity mismatch rather than assuming source vertex order.

## Exact repeated-field continuity theorem used by dev8

For a candidate whose source unit faces all repeat the same four geometric-corner values, one ordinary four-corner large rectangle can reproduce that repeated attribute field only when every merged axis has no per-cell reset discontinuity.

For any exact corner payload `P[0..3]`:

### U-axis condition

If `width == 1`, U imposes no extra condition.

If `width > 1`, require:

- `P[0] == P[1]`;
- `P[2] == P[3]`.

Otherwise each source cell reaches a different high-U value and the next source cell restarts at low-U, while one large quad would interpolate continuously across the whole width.

### V-axis condition

If `height == 1`, V imposes no extra condition.

If `height > 1`, require:

- `P[0] == P[2]`;
- `P[1] == P[3]`.

### Two-axis merge

When both `width > 1` and `height > 1`, both conditions apply; therefore the payload must be constant at all four corners for exact ordinary-quad representation.

These tests are applied independently to:

1. exact ARGB color;
2. packed light;
3. the exact raw `(UV0.u, UV0.v)` bit pair.

Raw float bits are used for UV equality, matching the dev6 render-key contract.

## Frozen classification

For every dev7 candidate retain one flag byte:

- bit 0: `COLOR_INTERPOLATION_SAFE`;
- bit 1: `LIGHT_INTERPOLATION_SAFE`;
- bit 2: `UV_FIELD_SAFE`;
- bit 3: `ORDINARY_ATTRIBUTE_SAFE` = bits 0,1,2 all true.

Logical retained bytes: exactly **1 byte/candidate**.

Maximum retained payload: `24,576` bytes/build.

No object graph is retained per candidate. Build scratch uses only bounded primitive arrays/counters.

Singleton candidates are expected to be ordinary-attribute-safe by construction but provide no geometry reduction. The important measured set is multi-face candidates.

## Exact self-validation

Every primary dev8 build must verify:

- dev7 source render-key and baked fingerprints match the supplied inputs;
- flag array length equals dev7 candidate count;
- retained bytes equal candidate count exactly;
- every candidate representative is the same bounded source quad retained by dev7;
- independent geometric-corner reconstruction succeeds for the representative;
- recomputing the U/V continuity equations reproduces every stored flag exactly;
- candidate totals partition exactly into singleton and multi-face classes inherited from dev7;
- multi-face candidates partition exactly into ordinary-safe and ordinary-unsafe;
- ordinary-unsafe candidates partition exactly into:
  - color unsafe;
  - light unsafe;
  - UV unsafe;
  with overlapping reason counters allowed but exact per-reason counts recorded;
- ordinary-safe covered faces and faces-saved sums are recomputed exactly from candidate areas;
- all counters are deterministic.

## Metrics

Record at least:

- builds;
- candidates classified;
- singleton / multi-face candidates;
- color-safe / color-unsafe multi-face candidates;
- light-safe / light-unsafe multi-face candidates;
- UV-safe / UV-unsafe multi-face candidates;
- ordinary-safe / ordinary-unsafe multi-face candidates;
- ordinary-safe covered faces;
- ordinary-safe faces saved (`sum(area - 1)` over safe multi-face candidates);
- ordinary-safe reduction permille over dev7 eligible faces;
- candidates requiring a future repeat-aware path;
- retained bytes and bytes/candidate;
- total/max build time;
- scratch uses/high-water;
- primary classification audits/matches;
- first/every-64 determinism audits/matches after worker integration.

Also retain per-direction ordinary-safe multi-face counts/covered faces so directional accounting can be audited.

## Runtime gate after worker integration

Add `ordinaryQuadEmissionSafetyEvidenceReady=true` requiring all prior dev7 gates plus:

- safety builds are nonzero and cover completed worker jobs;
- classified candidate count exactly equals dev7 candidate count;
- singleton + multi-face exactly equals dev7 candidate count;
- multi-face candidate count exactly equals dev7 multi-face candidate count;
- ordinary-safe + ordinary-unsafe multi-face exactly equals multi-face candidates;
- per-direction candidate accounting is exact;
- retained bytes equal candidate count * 1;
- scratch uses cover builds;
- primary classification audits equal builds and all match;
- determinism audits are nonzero and all match;
- prior worker/lifecycle/lifetime cleanliness remains true;
- normal process exit code 0.

The gate **does not require ordinary-safe multi-face candidates to be nonzero**. A result of zero is a valid and important measurement: it would prove the current ordinary atlas/block vertex representation cannot exploit dev7 grouping directly.

Likewise, no particular unsafe reason is required to be nonzero; the distribution is evidence, not an assumed outcome.

## Scope boundary: what dev8 does not prove

`ORDINARY_ATTRIBUTE_SAFE` means the exact captured color/light/UV fields can be represented by one ordinary four-corner rectangle under the current payload semantics. Dev8 does not yet waive later correctness obligations for:

- T-junction/crack behavior (P3.6 policy remains later);
- raster edge/sample ownership differences;
- a custom repeat-aware shader/metadata design;
- actual GPU geometry replacement;
- human visual validation of a geometry-changing path.

Therefore dev8 itself cannot set `renderCorrectMergeKeyComplete=true` or enable greedy rectangle GPU emission.

## Decision rule after measurement

- If meaningful multi-face ordinary-safe coverage exists, a later P3.4 emission slice may consider a conservative ordinary-quad subset, still behind explicit visual validation.
- If ordinary-safe coverage is negligible/zero because UV repetition dominates, the next slice should design a sprite-local repeat-aware representation rather than stretching atlas UVs or weakening correctness.

## Promotion rule

Dev8 requires exact GitHub CI/package plus real reference runtime classification evidence and clean shutdown. Because dev8 does not change GPU geometry, no new explicit visual verdict is required for promotion. The first slice that changes emitted terrain geometry does require renewed explicit human visual validation.

All internal commits/merges use `[no-release]`.