# A-0123 - Phase 3 P3.4 dev8 pure emission-safety classifier CI

**Date:** 2026-08-23  
**Branch:** `phase3/rectangle-emission-safety`  
**Canonical PR:** #40 against `main`  
**Version:** `0.3.0-phase3-dev8`  
**Result:** `SUCCESS` for the isolated classifier/API compile checkpoint; production worker integration remains next.

## Objective

Compile the A-0122 ordinary four-vertex emission-safety classifier against the exact Minecraft/Fabric project before changing worker/coordinator integration.

## Implementation checkpoint

Added `OrdinaryQuadEmissionSafety`, consuming only immutable/proven dev7 inputs:

- `RenderMergeCandidates`;
- `CanonicalFaceRenderKeys`;
- `SectionBakedQuadSnapshot`.

For each candidate it independently reconstructs the representative baked quad's geometric corners and applies the frozen repeated-field continuity equations independently to exact ARGB, packed light and raw UV bit pairs.

Retained representation is exactly one flag byte/candidate:

- color interpolation safe;
- light interpolation safe;
- UV field safe;
- ordinary attribute safe = all three.

The class recomputes and validates classification/accounting, exposes safe/unsafe multi-face reason counts, ordinary-safe covered faces/faces saved, per-direction safe counts, exact retained bytes and deterministic content equality.

## CI evidence

Pure classifier/version head:

- `58b857112e9cdad877ad43fe01189a14e1ffcbff`.

Exact PR workflow:

- run `32602966803`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release publishing SKIPPED.

This proves the new classifier compiles against the real project and the referenced dev6/dev7 source APIs.

## Review finding before production integration

The first implementation's `validateAgainst()` reuses the common input validation helper by constructing a new `BuildScratch`. That allocation is not needed for correctness and would add a fixed ~24 KiB temporary allocation per primary classifier build once integrated into workers.

This is not a CI/runtime correctness failure, but it conflicts with the worker-local reusable-scratch discipline. It must be removed before production integration by separating source identity/accounting validation from the scratch-null check.

## Deliberate boundary

No GPU geometry changes. `greedyRectangleGpuEmission=false`, `renderCorrectMergeKeyComplete=false`, and `BakedSectionMesh` remains authoritative.

## Next action

Remove the validation-only scratch allocation, rerun exact CI, then integrate the allocation-clean classifier into production workers and add the `ordinaryQuadEmissionSafetyEvidenceReady` coordinator gate.