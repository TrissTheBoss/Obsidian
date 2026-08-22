# A-0113 - Phase 3 P3.4 dev6 implementation and package evidence

**Date:** 2026-08-22  
**Branch:** `phase3/render-correct-merge-key`  
**Canonical PR:** #38 against `main`  
**Version:** `0.3.0-phase3-dev6`  
**Result:** `SUCCESS` — implementation/package ready for reference runtime; runtime closure still required.

## Frozen contract

A-0112 defines dev6 as a correctness-first **render-key sidecar only**. Dev6 must not replace GPU geometry. `BakedSectionMesh` remains authoritative, `greedyRectangleGpuEmission=false`, and `renderCorrectMergeKeyComplete=false`.

## Implementation

Added `CanonicalFaceRenderKeys` as a pure worker-side mapping from the proven P3.2 canonical visible-face set to exact renderer-owned baked output captured by `SectionBakedQuadSnapshot`.

A canonical face is eligible only when exactly one baked SOLID/CUTOUT quad from the same source block can be proven to be the exact full unit-cube face for that direction. The implementation:

- explicitly maps Minecraft `Direction` ordinals to P3.2 WEST/EAST/DOWN/UP/NORTH/SOUTH order;
- requires the corresponding P3.2 visible-face bit;
- requires exact integer-boundary full-unit face positions and all four distinct geometric corners;
- rejects offset, inset, partial, rotated/non-axis-aligned, duplicate-corner and ambiguous generalized geometry;
- uses a fixed 24,576-entry `short` retained face map (`6 * 4096`) = exactly **49,152 retained bytes** per build;
- stores `0` for unmapped, positive `sourceQuad+1` for unique mapping, and `-1` for ambiguous mapping;
- exhaustively self-validates unique/unmapped/ambiguous accounting and canonical recognition.

Exact render equivalence between uniquely mapped neighboring canonical faces requires:

- same canonical direction;
- same render layer;
- exact `SectionBakedQuadSnapshot.MaterialIdentity` equality (atlas, sprite, layer, material flags, tint index, shade, light emission, animation);
- same geometric corner-order/winding signature;
- exact raw UV float bits per geometric corner;
- exact per-corner ARGB color;
- exact per-corner packed light.

Block/state ID equality alone is never used as proof of mergeability.

## Production worker integration

Every real `SectionMeshWorkerPool` job now builds in order:

1. `BinarySectionVisibility`;
2. `GreedySectionRectangles`;
3. `CanonicalFaceRenderKeys`;
4. existing generalized `BakedSectionMesh` drawable.

Workers retain all previous P3.2/P3.3/reference/baked audits. On the existing first/every-64-local-completions audit cadence, dev6 rebuilds the render-key sidecar and requires exact `contentEquals` determinism.

New metrics include render-key builds, visible/eligible/unmapped/ambiguous face counts, recognized canonical/ignored noncanonical baked quads, same-key/different-key/ineligible adjacency pairs, retained bytes, build time, scratch use/high-water and determinism audits/matches.

## Coordinator gate

`FrameCoordinator` now computes `renderMergeKeyEvidenceReady`. It requires all existing Phase 3/P3.2/P3.3 gates plus:

- render-key builds cover completed production jobs;
- render-key visible faces exactly equal P3.2 visibility faces;
- eligible + unmapped + ambiguous exactly equals visible faces;
- nonzero uniquely mapped canonical faces;
- nonzero recognized canonical baked quads;
- at least one same-key adjacency;
- at least one different-key adjacency;
- retained bytes exactly equal builds * 49,152;
- scratch use covers builds;
- nonzero matching determinism audits;
- clean worker/staging/arena/resource lifetime shutdown.

Final diagnostics explicitly report `renderMergeKeySidecarIntegrated=true`, `greedyRectangleGpuEmission=false`, and `renderCorrectMergeKeyComplete=false`.

## CI chain

### Pure classifier CI

Head `41624bab6e442d9f228a2d1c25702d9064ce384c` passed exact PR run `32600453166`:

- Java 25 / Gradle 9.5.1;
- build SUCCESS;
- artifact upload SUCCESS;
- release SKIPPED.

This isolated and proved the classifier's Minecraft/Java APIs before worker integration.

### Caught integration compiler error

First combined integration run `32600633306` failed at compile time after the worker rewrite because the existing `AtomicLongArray` max helper was accidentally written with the two-argument `compareAndSet(previous, value)` form instead of `compareAndSet(index, previous, value)`.

This was a local integration transcription error, not a render-key algorithm failure. No runtime artifact from that run is valid. The helper was corrected immediately.

### Canonical dev6 source/package CI

Canonical runtime source/package head:

- `6bb999d8adbcb4abf5c23d6766f06305666974c5`.

Exact PR run:

- `32600719722`;
- Java 25 / Gradle 9.5.1;
- build SUCCESS;
- artifact upload SUCCESS;
- release SKIPPED;
- artifact id `9482807581`;
- artifact wrapper digest `sha256:1880587a2f6519dfc0ef51a891e0c5f7b14c48514754a7a8bf223e836486ea54`.

## Canonical runtime package

Direct JAR:

- `Obsidian-0.3.0-phase3-dev6.jar`;
- size `308,439` bytes;
- SHA-256 `2d2664d1eb6fc844cf70cefabb11400752da20866f4e1f1a79ca3873ea55019a`.

Sources JAR:

- SHA-256 `520abfdc1e8c4f611ef60e04dc217684a71ee9c8e9c1c7ff11cf44f74ff3fbb4`.

Packaged `fabric.mod.json` verifies:

- version `0.3.0-phase3-dev6`;
- Minecraft `~26.2`;
- Java `>=25`;
- Fabric Loader `>=0.19.3`.

The runtime JAR contains `CanonicalFaceRenderKeys`, updated `SectionMeshWorkerPool`, updated `FrameCoordinator`, and updated `ObsidianBootstrap` classes.

## Runtime closure required

Dev6 is **not merge-eligible yet**. The user must run the canonical JAR on the reference Vulkan system and provide the final log. Required new flag: `renderMergeKeyEvidenceReady=true`, together with `phase3GateReady=true`, `schedulerEvidenceReady=true`, `binaryVisibilityEvidenceReady=true`, and `greedyRectangleEvidenceReady=true`.

The run should exercise initial READY, ordinary dirty rebuild, F3+T resource-reload rebuild, optional normal recentering, and normal exit. The historical fixed-anchor far-travel test remains unnecessary unless lifecycle semantics change.

Because dev6 does not change emitted GPU geometry, the human visual check is a regression guard. Do not claim P3.4 production greedy emission from this package.