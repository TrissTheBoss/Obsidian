# A-0136 — Phase 3 P3.4 dev10 promotion and dev11 activation

**Date:** 2026-08-28  
**Result:** SUCCESS — dev10 promoted; P3.4 dev11 activated.

## Dev10 closure

P3.4 dev10 (`0.3.0-phase3-dev10`) is COMPLETE.

Reference runtime evidence is recorded in A-0135. The final reference shutdown reported every required gate through `repeatAwareTransportEvidenceReady=true`, all worker/staging/arena/resource lifetime gates clean, and Prism exited with code 0.

Exact evidence head:
- `b126b3ac6621ec510005581ecaf570018f9dfee4`

Evidence-head workflow:
- `33211921903`
- Java 25 / Gradle 9.5.1 build SUCCESS
- artifact upload SUCCESS
- versioned release publishing SKIPPED

PR #42 was promoted under the standing Phase 3 authorization and merged with `[no-release]` as:
- `3f75cf4d7e4a65aa6b12053fd75507d1cd292b34`

No emitted terrain geometry changed in dev10, so no new human visual verdict was required for dev10 promotion.

## Dev10 measured closure carried forward

The reference dev10 run measured:
- dev7 multi-face candidates: `2,229`;
- dev9 repeat-aware representable: `2,229 / 2,229`;
- dev9 repeat-aware four-vertex-safe: `2,219 / 2,229`;
- dev10 transport records: `2,219`, exactly equal to the dev9 safe set;
- dev10 covered faces: `5,460`;
- dev10 faces saved: `3,241`;
- explicit-gradient / outer-edge / same-atlas-sampler / raster-review obligations: `2,219 / 2,219 / 2,219 / 2,219`;
- `repeatAwareTransportBoundaryRasterObligationOpen=true`.

The ten excluded candidates are inherited color-interpolation failures. Light and repeat-aware UV representation are not blockers in that runtime set.

The open raster flag is intentionally **not** a failure and is also **not** closed by dev10. It identifies the exact obligation that must be exercised once merged geometry is actually drawn.

## ACTIVE: P3.4 dev11 — repeat-aware greedy GPU emission canary

P3.5 is **not** active. Dev11 remains inside P3.4 and is the first geometry-changing slice in the dev6-dev10 chain.

Dev11 objective:

> Replace only the dev10-proven transport-safe canonical source-face groups with one repeat-aware large quad per admitted candidate in a bounded canary/render-validation path, while keeping every unsafe, ambiguous, noncanonical and generalized face on the existing exact `BakedSectionMesh` path.

Dev11 must consume the frozen dev10 proof rather than widening eligibility.

### Required source/API inspection before the emission contract is frozen

Inspect the exact 26.2 repository/dependency path for:
- `RepeatAwareTransportProof` and `RepeatAwareUvDescriptors`;
- `BakedSectionMesh` vertex/index layout and source-quad identity;
- worker-ticket install and scene upload ownership;
- current terrain comparison pipeline/shaders;
- live blocks-atlas texture-view/sampler binding;
- public Blaze3D vertex/pipeline/shader facilities needed to transport candidate-local repeat coordinates and explicit gradients.

D-0023 remains the default: public Blaze3D graphics first. D-0025 must not be widened merely for convenience.

### Frozen invariants that dev11 may not weaken

- dev11 eligibility is a subset of the exact dev10 transport records; no block/state-ID shortcut;
- source material/sprite/layer/tint/shade/emission/animation identity remains inherited from dev6/dev7;
- exact color/light safety remains inherited from dev8/dev9;
- repeat-aware UV bounds/orientation remain inherited from dev9;
- candidate-local repeat/remap, positive outer-edge policy and explicit-gradient requirement remain inherited from dev10;
- source baked vertex order/diagonal must be preserved;
- unsupported/generalized geometry remains exact passthrough;
- render-thread live-state capture and GPU ownership remain render-thread-only;
- worker live-world reads after capture remain zero;
- staging/arena/resource lifetimes remain bounded and completion-gated;
- no routine global GPU waits;
- the permanent reference/oracle path remains independent.

### Geometry accounting requirement

Before promotion, dev11 must prove exact source-to-output accounting for every emitted candidate:
- one emitted merged quad per admitted dev10 transport record (or a separately frozen conservative subset if exact API inspection proves a narrower representation is necessary);
- every covered source unit face is removed/replaced exactly once in the canary output;
- no eligible face is both source-emitted and merged-emitted;
- no covered source face disappears without a replacement;
- unsafe/noncanonical/generalized source quads remain present;
- direction counts, covered faces and faces saved reconcile exactly with the admitted transport set;
- deterministic rebuilds and existing lifecycle/lifetime gates remain clean.

Do not reuse the old `greedyRectangleGpuEmission` name to imply raw P3.3 topology rectangles are drawn. Dev11 should use a new explicit emission flag such as `repeatAwareGreedyGpuEmission=true` / equivalent, and leave `renderCorrectMergeKeyComplete=false` unless the slice truly proves the broader P3.4 completion contract.

### Raster / T-junction obligation becomes a hard dev11 validation requirement

Dev11 is geometry-changing. A-0133/A-0135's open raster obligation now becomes mandatory validation rather than a non-emitting note.

The runtime/visual exercise must include:
- close and oblique inspection of repeated planar textures across internal integer repeat-reset lines;
- rectangle-to-rectangle boundaries, including long edge meeting shorter edges;
- section boundaries where available in the canary scene;
- camera rotation/motion to expose cracks or mip shimmer;
- block break/place rebuild;
- F3+T resource reload/rebuild;
- normal shutdown with all existing lifetime gates clean.

The user must provide an **explicit human visual verdict** for dev11. Absence of a complaint in a log is not a verdict.

Inspect specifically for:
- texture stretching, atlas bleed or wrong mip/filter footprint;
- one-pixel seams at repeat reset lines;
- cracks or z-fighting at T-junctions/section borders;
- wrong winding/diagonal/culling;
- color/light mismatch;
- double-drawn source faces or missing faces.

If artifacts appear, follow D-0024: prefer stable positions and targeted selective splitting/mitigation. Do not globally abandon greedy meshing or globally conform the mesh without evidence.

### Promotion boundary

Dev11 cannot be promoted from CI/runtime counters alone.

Required before merge:
1. frozen dev11 emission contract;
2. exact GitHub CI/package success;
3. reference-machine runtime gates and exact geometry/lifetime accounting;
4. **explicit human visual validation PASS** on the geometry-changing path;
5. any observed raster issue either fixed with targeted mitigation and revalidated, or the affected candidate class excluded by a frozen conservative rule.

P3.6 remains the broader T-junction policy milestone; dev11 may validate/mitigate its immediate emitted geometry without falsely marking P3.6 COMPLETE.

## Next action

Synchronize `CURRENT_STATE.md` and the P3.4 roadmap status, cut a fresh dev11 feature branch from synchronized `main`, inspect the exact graphics/upload/shader path, then freeze the dev11 emission contract before implementation.
