# A-0147 - P3.5 promotion and P3.6 T-junction policy contract

**Date:** 2026-08-29  
**Result:** SUCCESS for P3.5 promotion; P3.6 contract FROZEN, implementation/runtime evidence pending.

## P3.5 promotion

A-0146 closed the corrected dev12.1 reference runtime with every frozen P3.5 gate true. Exact synchronized evidence head `d139f8229318109f146003aa186b6d4a46cbdad6` then passed hosted Build workflow `33262044878`.

The connected ready-for-review GraphQL mutation failed on the already-known obsolete `Repository.fullDatabaseId` field. This was the same connector defect previously encountered during dev11 promotion. Draft PR #45 was therefore closed as superseded without changing its branch/head, and non-draft promotion PR #46 was opened from the exact same green head.

PR #46 merged the exact evidence head with `[no-release]` as:

- `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`

No source/runtime/evidence change occurred between final validation and merge.

Therefore **P3.5 — Border/halo correctness is COMPLETE**.

## P3.6 milestone identity

- milestone: **P3.6 — T-junction policy**
- branch: `phase3/t-junction-policy`
- base: P3.5 promotion merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`
- first implementation version: `0.3.0-phase3-dev13`

## Source findings before contract freeze

P3.6 begins from source truth rather than assuming greedy T-junctions are automatically defective.

Current dev11/P3.5 geometry path establishes:

1. `RepeatAwareGreedyMesh.writeMergedQuad()` emits merged positions from integer `plane/u/v/width/height` values. Candidate edge endpoints therefore lie on the section-local voxel lattice.
2. The fixed face coordinate uses the same `BakedSectionMesh.COMPARISON_FACE_OFFSET = 1/512` outward displacement already used by passthrough baked faces for canonical directions.
3. Greedy admission remains limited by the dev6-dev10 render-equivalence chain. Generalized/noncanonical/unsafe geometry is not converted into merged rectangles.
4. The record draw transform computes `sectionOrigin - cameraPosition` in double precision, then casts the already-camera-relative translation to float for the model-view matrix. World-coordinate magnitude is therefore not directly baked into terrain vertex positions.
5. The repeat-aware vertex shader uses `Position + ModelOffset` and the existing `ModelViewMat`/`ProjMat`; it does not introduce a second geometry-warp or snapping transform.
6. P3.4/dev11 and P3.5 reference runs both produced explicit human observations with no visible cracks/seams on the RX 6800 XT, but those observations were not broad P3.6 evidence because they did not prove that inspected frames contained known T-junctions.

These findings support the standing D-0024 policy: **do not globally split greedy quads preemptively**. First prove the actual topology and deliberately exercise known T-junctions on hardware.

## Frozen P3.6 dev13 scope

Dev13 is a **non-geometry-changing T-junction evidence slice**.

It must not change:

- greedy candidate eligibility;
- source suppression/replacement;
- merged or passthrough vertex positions;
- vertex/index formats;
- shaders;
- render pipelines;
- atlas/lightmap behavior;
- draw classes;
- native Vulkan graphics scope;
- worker/live-world ownership;
- staging/arena/resource lifetime.

Because dev13 does not change emitted geometry, its purpose is to decide whether a mitigation is justified at all.

## Pure topology proof

Add a worker-side immutable summary-only `TJunctionTopologyProof` (name may vary only if implementation clarity requires it) built from existing immutable render-correct merge data.

The proof must operate on the **actual emitted merged candidate set**, not raw P3.3 topology rectangles.

For every emitted merged candidate edge, classify coplanar same-facing neighboring emitted edges and identify strict T-junctions where an endpoint of one emitted edge lies strictly inside another emitted edge.

For each detected junction prove:

- both participating candidates are on the same direction and exact fixed face plane;
- the junction coordinate is an exact integer section-local lattice coordinate before the common directional `1/512` comparison offset;
- the long edge endpoints and terminating endpoint are representable from the existing integer candidate fields without epsilon comparisons;
- the terminating point lies strictly inside the long edge, not at an ordinary shared corner;
- all candidate bounds remain within the legal section-local `0..16` edge domain;
- no generalized/passthrough-only source geometry is falsely classified as a merged/merged T-junction.

The implementation should be bounded and allocation-conscious, using reusable worker-local primitive scratch rather than pairwise object creation.

## Cross-section / transform precision proof

P3.6 must retain evidence that section transforms are camera-relative before float conversion. Add diagnostics/proof sufficient to establish:

- every LIVE record uses `origin - camera` rather than absolute world coordinates in float;
- adjacent section origins differ by exactly one section (`16` blocks) in integer source identity;
- current model-view translation values remain finite;
- the tested T-junction sections are rendered through the same camera-relative transform path as ordinary dev11 geometry.

Do not introduce eye-relative vertex rewriting merely to satisfy this proof; current source already performs camera-relative section translation. Change it only if evidence demonstrates an actual precision failure.

## Runtime evidence contract

Add explicit P3.6 diagnostics and final gate, e.g.:

- `tJunctionPolicyEvidenceReady=true`

The gate requires all prior P3.5/dev11 gates plus:

- topology proof builds > 0;
- actual emitted merged candidates > 0;
- **actual strict T-junction count > 0** on the tested scene; a zero-junction scene cannot validate the policy;
- all detected junctions satisfy exact plane/lattice/bounds identities;
- deterministic proof audits pass;
- camera-relative transform proof passes for LIVE records;
- no geometry/shader/pipeline change flag remains true for dev13;
- `workerWorldReadsAfterCapture=0`;
- zero unsafe stale installs;
- workers/staging/arena/resources clean;
- normal process exit code 0.

## Required visual/raster exercise

Once automated evidence confirms the runtime contains real detected T-junctions, the reference-hardware test must deliberately inspect those rendered surfaces while:

1. standing still near the scene;
2. strafing/forward movement across the surface;
3. rotating the camera rapidly and slowly;
4. viewing at shallow/grazing angles and ordinary angles;
5. crossing a section boundary/recentering so independently transformed neighboring sections are exercised;
6. performing an ordinary block rebuild and allowing READY again;
7. performing F3+T and allowing READY again.

The user must report explicit PASS/FAIL for visible cracks, pinholes, flickering seams, z-fighting/double edges, or camera-motion-dependent gaps at greedy rectangle boundaries/T-junctions.

## Decision rule

If automated proof sees real T-junctions and the targeted reference Vulkan visual test passes with no artifacts, P3.6 should record **NO BASELINE MITIGATION REQUIRED on the proven path** and complete without adding global splitting. Preserve a diagnostic/revisit hook for later cross-vendor or larger-scale evidence.

If artifacts are observed, do not abandon greedy meshing globally. Freeze a new mitigation slice and prefer, in order:

1. targeted raster-safe mitigation local to affected edges/classes;
2. selective splitting of only proven-problematic candidate boundaries;
3. broader topology changes only if the narrower approaches fail with evidence.

Any mitigation that changes emitted geometry requires a new explicit visual/runtime gate and must preserve D-0024's independent oracle/render-equivalence rules.

## P3.7 boundary

P3.6 does not consume P3.7 differential correctness. The permanent reference oracle remains active, but the broader optimized-vs-reference representative-fixture framework stays P3.7.

## Next action

Implement the bounded immutable T-junction topology/transform evidence for `0.3.0-phase3-dev13`, integrate it into the existing worker/scene/final coordinator evidence chain without changing geometry, compile/package in exact CI, then run the targeted reference-hardware exercise only after automated logs prove real T-junctions were present.
