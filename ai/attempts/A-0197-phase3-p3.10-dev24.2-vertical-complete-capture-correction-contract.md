# A-0197 — P3.10 dev24.2 vertical + complete-capture correction contract

Date: 2026-09-02
Status: FROZEN BEFORE SOURCE CHANGE
Branch: `phase3/p3.10-production-terrain-replacement`
Parent runtime failure: A-0196

## Objective

Correct only the two demonstrated dev24.1 production-canary defects:

1. stale 3x3x1 scene Y when the player crosses a vertical section boundary without leaving the current X/Z window;
2. production suppression of a section/layer even though the generalized capture omitted unsupported block categories such as leaves or fluid-bearing blocks.

## Frozen source changes

### 1. Vertical recenter

`AsyncMultiSectionSceneProbe.tryRecenterIfPlayerLeftWindow(...)` must trigger the existing recenter/invalidate path when either:

- the player leaves the existing horizontal 3x3 X/Z window, **or**
- `playerSection.y() != centerSectionY`.

The scene footprint remains 3x3x1. The existing generation/event invalidation and completion-gated retirement path remains authoritative.

### 2. Production capture completeness gate

Do **not** broaden `SectionBakedQuadSnapshot.capture(...)` geometry support in this attempt.

A `WorkerBackedSectionLifecycleProbe` may claim production replacement only when its immutable `SectionBakedQuadSnapshot` is complete with respect to the current capture policy:

- `bakedSnapshot.rejectedBlocks() == 0` must be true before either SOLID or CUTOUT production suppression is authorized;
- the existing per-layer non-empty output condition remains mandatory;
- if any block was rejected for leaves, fluid, block-entity, missing-model, material, translucent, or non-block-atlas reasons, the production claim returns false and vanilla remains authoritative for that section/layer.

This is intentionally conservative. Proof/benchmark records may still exist for incomplete captures, but they cannot suppress vanilla terrain in P3.10 production mode.

## Version / observability

- bump runtime version to `0.3.0-phase3-dev24.2`;
- bootstrap banner must state that vertical section recenter is active and production suppression requires zero rejected capture blocks;
- no comparison overlay is reintroduced.

## Explicit non-changes

This attempt must not:

- remove or relax the existing leaves/fluid capture exclusions;
- add leaf, fluid, translucent, block-entity, or other new rendering support;
- change the P3.7 differential proof or its mismatch gates;
- change the exact Minecraft 26.2 prepare/same-OPAQUE-pass production seam;
- change passthrough/merged shaders or pipeline semantics;
- change the 3x3x1 footprint;
- add partial remeshing or partial GPU patching;
- expand native Vulkan graphics ownership;
- change known thin/coplanar 2D grass/leaf-litter behavior.

## Acceptance gates before runtime handoff

- static diff contains only the frozen behavior plus version/banner metadata;
- hosted Java 25 / Gradle build passes on the exact source head;
- direct versioned JAR identity is recorded with size and SHA-256;
- PR #55 remains DRAFT / DO NOT MERGE.

## Runtime retest requirements

Reference runtime must explicitly verify:

- leaves remain visible while near/in a managed scene;
- kelp remains visible while near/in a managed scene;
- crossing Y section boundaries within the same X/Z chunk column causes the managed scene center Y to follow the player and replacement recovers;
- horizontal recenter still works;
- SOLID/CUTOUT replacement still occurs in clean supported sections;
- unsupported/incomplete sections fall back to vanilla without holes;
- suppression/execution accounting remains exact;
- P3.7/lifetime/worker-world-read/stale-install gates remain clean;
- normal exit succeeds.
