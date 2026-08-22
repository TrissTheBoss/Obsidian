# A-0132 — Phase 3 P3.4 dev9 promotion and dev10 activation

**Date:** 2026-08-23  
**Result:** SUCCESS

## Objective
Promote the fully validated P3.4 dev9 repeat-aware UV descriptor slice and activate the next correctness slice without jumping to P3.5 or changing GPU terrain geometry prematurely.

## Promotion evidence
Dev9 reference runtime is recorded in A-0131 and passed every frozen gate, including `repeatAwareUvEvidenceReady=true`, exact descriptor accounting/determinism, clean worker/staging/arena/resource closure, and Prism exit code 0.

A-0131 evidence head `378677a08f71c6b783750d47cfc3bac818705e60` passed workflow `32605212651`:
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- versioned release publishing SKIPPED.

PR #41 was marked ready and merged with `[no-release]` as:
- merge commit `59471127162aaf02c9c87e679e1c4c361f968fac`.

## Dev9 architectural conclusion
Observed reference runtime:
- 5,267 dev7 multi-face candidates;
- repeat-aware UV representable: 5,267 / 5,267 = 100%;
- repeat-aware four-vertex safe after color/light constraints: 5,266 / 5,267;
- light-safe: 5,267 / 5,267;
- color-safe: 5,266 / 5,267;
- ordinary atlas UV-safe: 0 / 5,267.

Therefore the dev8 blocker was representation, and the frozen dev9 descriptor solves that representation problem for every observed multi-face candidate. The sole remaining four-vertex exclusion in the observed set is color interpolation, not UV or light.

## Next active slice
Activate **P3.4 dev10 — repeat-aware transport/sampling correctness proof**.

Dev10 remains correctness/proof-first and must not replace the authoritative `BakedSectionMesh` drawable.

Dev10 must freeze and prove, before any geometry-changing slice:
1. the exact runtime representation used to transport candidate-local repeat coordinates and the dev9 source-atlas rectangle/orientation;
2. deterministic reconstruction/remapping from candidate-local coordinates into the source sprite atlas rectangle;
3. exact behavior at integer repeat boundaries and candidate edges;
4. source-atlas filtering/padding/inset assumptions required to prevent sampling adjacent sprites or introducing seams;
5. how orientation flips/rotations from dev9 map through transport without normalization loss;
6. whether the proposed representation can preserve existing material/sprite/layer/tint/shade/emission/animation identity without widening the native/backend seam unnecessarily;
7. the raster/T-junction obligations relevant to the eventual large-quad path, following D-0024: do not globally conform/split by default, but identify any selective split/mitigation conditions that must be enforced before emission;
8. bounded primitive metadata, deterministic validation and worker/lifetime ownership consistent with prior P3.4 slices.

Dev10 must keep:
- `greedyRectangleGpuEmission=false`;
- `renderCorrectMergeKeyComplete=false`;
- `BakedSectionMesh` authoritative;
- no worker live-world reads after capture;
- no broadened native Vulkan takeover merely for convenience.

A later geometry-changing P3.4 slice may consume dev10 proof only after its own frozen emission contract is established. That later slice requires renewed explicit human visual validation before promotion.

## Roadmap status
- P3.4 remains ACTIVE.
- dev9 becomes COMPLETE.
- dev10 becomes ACTIVE.
- P3.5 remains PLANNED and is not active.

## Next action
Synchronize `CURRENT_STATE.md` and `MASTER_ROADMAP.md`, branch dev10 from fresh `main`, then create an immutable dev10 scope/API proof plan before implementation.
