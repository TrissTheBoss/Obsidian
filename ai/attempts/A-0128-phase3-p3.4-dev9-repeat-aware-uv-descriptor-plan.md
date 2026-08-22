# A-0128 — Phase 3 P3.4 dev9 repeat-aware UV descriptor plan

Date: 2026-08-23
Result: **SUCCESS — CONTRACT FROZEN**
Milestone: P3.4 dev9

## Why this slice exists

Dev8 proved that none of 6,383 observed multi-face dev7 candidates can be emitted exactly as one ordinary four-vertex quad using the current raw atlas UV0 field:

- color-safe / unsafe: `6,352 / 31`;
- light-safe / unsafe: `6,383 / 0`;
- ordinary atlas UV-safe / unsafe: `0 / 6,383`;
- combined ordinary-safe / unsafe: `0 / 6,383`.

Therefore the next correctness question is not whether to stretch atlas UVs. It is whether exact source UV truth can be represented as **sprite-local repetition** plus a deterministic remap back into the same source atlas rectangle/orientation.

Dev9 is sidecar-only. It does not emit geometry or add a shader.

## Inputs

Dev9 consumes only immutable renderer-owned truth:

1. `RenderMergeCandidates`;
2. `CanonicalFaceRenderKeys`;
3. `OrdinaryQuadEmissionSafety`;
4. `SectionBakedQuadSnapshot`.

No live world/model/resource reads are permitted on workers.

## Candidate scope

Only dev7 **multi-face** candidates need a repeat-aware descriptor. Singletons already have exact ordinary source quads and are not counted as repeat-aware opportunities.

For each multi-face candidate, use its retained representative source baked quad. Independently reconstruct the representative's four vertices into canonical geometric corners:

- corner 0 = `(uLow,vLow)`;
- corner 1 = `(uHigh,vLow)`;
- corner 2 = `(uLow,vHigh)`;
- corner 3 = `(uHigh,vHigh)`

using the existing direction-specific geometric face axes, not source vertex order assumptions.

## Exact UV rectangle proof

A representative is repeat-aware-UV representable only if its four raw atlas UV pairs satisfy all of:

1. exactly two distinct raw U values;
2. exactly two distinct raw V values;
3. all four combinations of those U/V values occur exactly once across the four geometric corners;
4. values are finite and preserve their exact raw float bits;
5. the geometric-corner -> UV-corner mapping is an affine square symmetry, not an arbitrary permutation.

Define each UV corner code as:

- bit 0: low/high U;
- bit 1: low/high V.

For geometric corner codes `c0,c1,c2,c3`, define:

- `dU = c0 XOR c1`;
- `dV = c0 XOR c2`.

A mapping is accepted only when:

- `(dU,dV)` is `(1,2)` or `(2,1)`;
- `c3 == c0 XOR dU XOR dV`.

This admits the eight exact axis flips/rotations of a square and rejects crossed/non-affine corner permutations.

## Descriptor representation

Retain descriptors only for representable multi-face candidates, in original candidate-index order.

Each retained descriptor is logically exactly **19 bytes**:

- unsigned `short` candidate index: 2 bytes;
- raw `float` bits for U low: 4 bytes;
- raw `float` bits for U high: 4 bytes;
- raw `float` bits for V low: 4 bytes;
- raw `float` bits for V high: 4 bytes;
- packed 8-bit geometric-corner -> UV-corner orientation signature: 1 byte.

`BYTES_PER_DESCRIPTOR = 19`.

The orientation signature stores the four 2-bit UV corner codes in geometric-corner order. It is evidence, not a normalized guess.

Worker scratch must use fixed primitive arrays bounded by `RenderMergeCandidates.MAX_CANDIDATES`.

## Future shader semantics being proven, not implemented

A future repeat-aware path may conceptually derive candidate-local cell coordinates `(s,t)`, apply per-cell repetition via `fract`/equivalent exact local periodic coordinates, apply the retained affine orientation, and remap into the retained source atlas U/V bounds.

Important: **full-atlas sampler wrapping is forbidden as the correctness model.** Repetition happens in sprite-local space first, then remaps into the source sprite rectangle. This prevents wrapping into unrelated atlas sprites.

Dev9 does not yet freeze filtering-edge/padding implementation details for the actual shader. That belongs to the geometry/shader implementation slice and must use exact Minecraft atlas sampling semantics.

## Combined four-vertex feasibility metric

For each multi-face candidate:

`repeatAwareFourVertexSafe = repeatAwareUvRepresentable && dev8.colorInterpolationSafe && dev8.lightInterpolationSafe`

Dev8's ordinary UV-safe bit is intentionally ignored for this combined metric because dev9 replaces that field with repeat-aware UV metadata.

Measure:

- multi-face candidates;
- repeat-aware UV representable / unrepresentable;
- repeat-aware four-vertex safe / unsafe;
- safe covered faces;
- safe faces saved;
- safe reduction ratio over dev6 eligible faces;
- descriptor count/bytes;
- per-direction representable and safe counts/faces;
- scratch use/high-water;
- primary exact classification audits;
- determinism audits.

Zero representable or zero repeat-aware-four-vertex-safe is a valid result. The gate must not force a desired outcome.

## Exact validation

Every build must validate:

- candidate/render-key/safety/baked source fingerprints agree;
- candidate index in descriptor range and strictly ascending;
- descriptor belongs to a multi-face candidate;
- representative source quad is valid and canonical for candidate direction;
- raw bounds exactly match recomputation;
- orientation signature exactly matches recomputation and passes affine-square proof;
- no descriptor exists for an unrepresentable candidate;
- every representable multi-face candidate has exactly one descriptor;
- representable + unrepresentable == dev7 multi-face count;
- repeat-aware-safe + repeat-aware-unsafe == dev7 multi-face count;
- safe covered/saved accounting exactly matches candidate areas;
- per-direction sums match globals;
- retained bytes == descriptorCount * 19;
- duplicate builds are content-equal on audit cadence.

## Runtime gate target after integration

Add `repeatAwareUvEvidenceReady=true` after all prior gates. It must require:

- all prior dev8 gates true;
- nonzero dev9 builds covering completed production jobs;
- exact multi-face candidate identity/accounting;
- representable/unrepresentable exact partition;
- repeat-aware-safe/unsafe exact partition;
- descriptor retained bytes exact;
- scratch uses covering builds;
- primary descriptor audits exactly equal builds and all match;
- nonzero determinism audits and all match;
- previous worker/lifecycle/lifetime closure clean.

It must **not** require representable or safe counts to be positive.

## Rendering boundary

Dev9 keeps:

- `repeatAwareUvDescriptorSidecarIntegrated=true` after production integration;
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` authoritative.

Dev9 does not change GPU terrain geometry and therefore does not consume the future geometry-changing slice's required renewed human visual verdict.

A later geometry/shader slice must separately prove exact atlas filtering/padding behavior, actual vertex/shader transport, raster/T-junction policy relevant to emitted rectangles, and explicit human visual validation.

P3.5 remains planned and is not active.
