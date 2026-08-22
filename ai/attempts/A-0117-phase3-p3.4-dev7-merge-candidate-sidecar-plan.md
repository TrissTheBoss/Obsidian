# A-0117 - Phase 3 P3.4 dev7 render-key-aware merge-candidate sidecar plan

**Date:** 2026-08-23  
**Branch:** `phase3/render-merge-candidate-sidecar`  
**Planned version:** `0.3.0-phase3-dev7`  
**Result:** `SUCCESS` — correctness-first dev7 scope frozen before worker integration.

## Objective

Advance P3.4 from per-face render-key classification to deterministic key-aware rectangle candidates without changing GPU-emitted geometry.

Dev7 must answer a narrower question than final greedy emission: given the proven P3.2 visible canonical face set and dev6 exact canonical render-key mapping, what is the deterministic bounded rectangle partition when faces may coalesce only if every covered face is dev6-eligible and exactly render-equivalent?

`BakedSectionMesh` remains the authoritative production drawable. Dev7 must continue to report `greedyRectangleGpuEmission=false` and `renderCorrectMergeKeyComplete=false`.

## Proven inputs

- `BinarySectionVisibility` — exact conservative canonical visible face set.
- `GreedySectionRectangles` — proven topology-only no-gap/no-overlap partition; retained as source identity/differential evidence, but **not** a partition constraint for dev7.
- `CanonicalFaceRenderKeys` — unique canonical-face-to-baked mapping plus exact output-equivalence predicate.
- `SectionBakedQuadSnapshot` — immutable baked render truth used by the dev6 comparator.

## Why dev7 does not simply split P3.3 rectangles

P3.3 topology rectangles are deterministic but intentionally ignore render keys. Treating their existing boundaries as mandatory would make candidate quality depend on arbitrary topology partition choices and could prevent a larger valid same-key rectangle spanning two P3.3 records.

Dev7 therefore scans each direction/plane directly over the **eligible canonical face mask**, using P3.3 only to verify that the source topology identity/coverage is the proven one. Candidate merging is constrained by dev6 render equivalence, not by previous topology rectangle boundaries.

## Frozen candidate extraction

For each canonical direction in WEST/EAST/DOWN/UP/NORTH/SOUTH order and each plane `0..15`:

1. Construct 16 rows of eligible bits from `CanonicalFaceRenderKeys` using the same coordinate mapping as P3.3:
   - WEST/EAST: `plane=x`, `u=z`, `v=y`;
   - DOWN/UP: `plane=y`, `u=x`, `v=z`;
   - NORTH/SOUTH: `plane=z`, `u=x`, `v=y`.
2. Scan rows by increasing `v`; choose the least-significant remaining `u` as the seed.
3. Use the seed face's unique source baked quad as the candidate representative.
4. Extend horizontal width only while each adjacent face is eligible, not already consumed, and `CanonicalFaceRenderKeys.renderEquivalent(seed, face, direction, baked)` is true.
5. Extend vertical height only while the complete horizontal run in the next row is eligible, unconsumed and exactly render-equivalent to the same representative seed.
6. Emit the rectangle, mark its cells consumed, and continue deterministically.

This is a correctness-first greedy partition, not a claim of globally minimum rectangle count.

## Bounded representation

Use one packed `int` geometry record per candidate with the proven P3.3 bit layout:

- bits 0-3: `u`;
- bits 4-7: `v`;
- bits 8-11: `widthMinus1`;
- bits 12-15: `heightMinus1`;
- bits 16-19: `plane`;
- bits 20-22: `direction`.

Retain one `short` representative source-quad index per candidate. `SectionBakedQuadSnapshot.MAX_QUADS=24,000`, so the unsigned index fits safely in 16 bits.

- `BYTES_PER_CANDIDATE = 6` logical retained bytes;
- `MAX_CANDIDATES = ReferenceFaceMesh.MAX_FACES = 24,576`;
- maximum logical retained payload = `147,456` bytes/build.

Worker scratch remains fixed primitive storage: candidate record array, representative array, 16 eligibility rows, 16 consumed rows, exact-coverage words and small direction counters. No per-face object graph.

## Exact partition validation

Every primary build must self-validate independently of the extraction loop:

- source visibility/topology/render-key/baked fingerprints agree;
- P3.3 covered faces equal P3.2 visible faces;
- dev6 visible faces equal P3.2 visible faces;
- each candidate rectangle is in range and has a valid representative source quad;
- every expanded candidate face exists in P3.2 visibility;
- every expanded candidate face is dev6-eligible;
- every expanded candidate face is exactly render-equivalent to the candidate seed/representative;
- candidate rectangles never overlap;
- expanded candidate coverage equals the complete dev6 eligible canonical face set exactly, globally and by direction;
- `candidateCoveredFaces == renderKeys.eligibleFaces()`;
- `passthroughCanonicalFaces == visibility.visibleFaceCount() - renderKeys.eligibleFaces()`;
- singleton + multi-face candidate counts equal total candidates;
- sum(candidate areas - 1) equals eligible faces saved by candidate merging;
- retained bytes equal candidate count * 6.

Ambiguous/unmapped canonical faces are never swallowed into candidates. Arbitrary generalized baked geometry remains outside this canonical candidate sidecar and stays on the existing exact `BakedSectionMesh` path.

## Determinism / differential validation

On the existing first/every-64-local-completions audit cadence after worker integration:

- rebuild visibility and keep the permanent `ReferenceFaceMesh` audit;
- rebuild P3.3 rectangles and keep exact topology/reference audits;
- rebuild dev6 render keys and require exact deterministic equality;
- rebuild dev7 candidates and require exact `contentEquals` equality;
- keep existing `BakedSectionMesh` determinism audit.

The dev7 self-validation is the exact eligible-face partition oracle. The independent P2/P3.2 reference oracle remains authoritative for canonical topology rather than making dev7 its own topology oracle.

## Production metrics after integration

Record at least:

- candidate builds;
- candidate count;
- candidate-covered eligible faces;
- passthrough canonical visible faces;
- singleton candidates;
- multi-face candidates;
- eligible faces saved by candidate merging;
- candidate reduction permille over eligible faces;
- candidate counts and covered faces by direction;
- logical retained bytes and bytes/candidate;
- total/max build time;
- max candidates/build;
- scratch uses/high-water;
- exact coverage audits/matches;
- determinism audits/matches.

## Runtime gate

Add `renderMergeCandidateEvidenceReady=true`, requiring all prior Phase 3/P3.2/P3.3/dev6 gates plus:

- candidate builds are nonzero and cover completed production jobs;
- candidate-covered faces exactly equal dev6 render-key eligible faces;
- passthrough canonical faces exactly equal visible minus eligible;
- candidate count is positive and `<=` eligible faces;
- at least one multi-face candidate is observed;
- faces saved by candidate merging is positive;
- direction coverage sums exactly to candidate-covered faces;
- retained bytes equal candidate count * 6;
- scratch uses cover builds;
- primary exact coverage audits match builds;
- determinism audits are nonzero and all match;
- zero worker queue-full rejection/failure/shutdown-join failure;
- zero dropped lifecycle events / unsafe stale installs;
- workers/staging/arena/resources clean;
- normal process exit code 0.

## Critical non-goal: no GPU emission yet

Dev7 does **not** prove that a candidate rectangle can be emitted as one large quad with exact visual equivalence.

Two unresolved rectangle-level output problems remain intentionally outside this slice:

1. **Interpolation:** one large quad can interpolate per-corner light/color/AO across the entire rectangle differently from repeated cell quads, even when adjacent source faces pass the dev6 per-face equality test.
2. **UV repetition:** identical per-cell atlas UVs do not automatically imply that stretching one large atlas quad reproduces cell-local texture repetition/animation semantics.

Those semantics must be frozen and proven in a later P3.4 emission-readiness slice before `renderCorrectMergeKeyComplete=true` or `greedyRectangleGpuEmission=true` is allowed.

## Promotion rule

Dev7 is not merge-eligible from compile evidence alone. It needs exact GitHub CI/package plus real reference runtime evidence for the new candidate gate and clean shutdown. Because emitted GPU geometry remains unchanged, visual inspection is a regression guard for dev7; a later geometry-changing slice requires renewed explicit human visual validation.

All internal commits/merges use `[no-release]`.