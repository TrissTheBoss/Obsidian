# A-0118 - Phase 3 P3.4 dev7 pure merge-candidate core and first CI

**Date:** 2026-08-23  
**Branch:** `phase3/render-merge-candidate-sidecar`  
**Canonical PR:** #39 against `main`  
**Version:** `0.3.0-phase3-dev7`  
**Result:** `SUCCESS` for the pure sidecar core/API compile checkpoint; production worker integration remains next.

## Objective

Implement and compile the frozen A-0117 merge-candidate algorithm in isolation before changing worker/coordinator integration, so candidate algorithm/API failures are distinguishable from integration transcription errors.

## Implementation

Added `RenderMergeCandidates` as a pure terrain-sidecar class consuming:

- proven `BinarySectionVisibility`;
- proven `GreedySectionRectangles` source topology identity;
- proven `CanonicalFaceRenderKeys` eligibility/equivalence truth;
- immutable `SectionBakedQuadSnapshot`.

The extractor scans eligible masks directly by direction and plane in deterministic direction/plane/v/u order. Each seed extends horizontally and then vertically only while all faces remain unconsumed, dev6-eligible and exactly render-equivalent to the same seed representative.

P3.3 topology rectangle boundaries are intentionally not inherited as mandatory merge boundaries. The topology sidecar is checked for matching visibility identity/coverage, while dev7 computes the render-key-aware partition directly from the eligible canonical face set.

## Representation

Each retained candidate uses:

- one packed `int` geometry record with the proven P3.3 coordinate/extent layout;
- one unsigned `short` representative source baked-quad index.

Logical retained payload:

- `6` bytes/candidate;
- maximum `24,576` candidates;
- maximum `147,456` logical retained bytes/build.

Worker-targeted `BuildScratch` is fixed primitive storage containing bounded record/representative arrays, 16 eligible rows, 16 consumed rows, exact coverage words and small directional counters.

## Self-validation

Every build expands all candidates and requires:

- source visibility/topology/render-key/baked fingerprints agree;
- no candidate overlap;
- every candidate face is present in P3.2 visibility;
- every candidate face is dev6-eligible;
- every candidate face is exactly render-equivalent to its seed representative;
- expanded coverage equals the complete eligible canonical face set exactly;
- exact directional eligible/candidate accounting;
- canonical passthrough equals visible minus eligible;
- singleton + multi-face counts equal total candidates;
- faces saved equals eligible faces minus candidate count;
- retained bytes equal candidate count * 6.

## CI evidence

Pure-core/version head before this evidence record:

- `25f4bb5927ae4ded24f3bce02e844f4684d35413`.

Exact PR workflow:

- run `32601691543`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release publishing SKIPPED.

This proves the new class compiles against the real Minecraft/Fabric project and its referenced P3.2/P3.3/dev6 APIs.

## Deliberate boundary

This checkpoint does not yet prove production-worker execution or runtime candidate metrics. `BakedSectionMesh` remains the drawable; `greedyRectangleGpuEmission=false`; `renderCorrectMergeKeyComplete=false`.

The next dev7 implementation step is worker integration, candidate metrics/audits, and a new coordinator runtime gate. No runtime package should be handed off until that exact integrated head is green.