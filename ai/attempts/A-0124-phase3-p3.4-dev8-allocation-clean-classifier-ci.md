# A-0124 - Phase 3 P3.4 dev8 allocation-clean classifier CI

**Date:** 2026-08-23  
**Branch:** `phase3/rectangle-emission-safety`  
**Version:** `0.3.0-phase3-dev8`  
**Result:** `SUCCESS` — the validation-only 24 KiB scratch allocation identified in A-0123 was removed and the classifier remained compile-clean.

## Change

`OrdinaryQuadEmissionSafety` now separates source identity/accounting validation from the worker scratch-null check. Primary `build(...)` reuses the caller-owned `BuildScratch`; `validateAgainst(...)` no longer constructs a fresh `BuildScratch` merely to validate source identity.

This preserves the frozen A-0122 classifier semantics and removes the large unnecessary temporary allocation before production worker integration.

## CI evidence

Allocation-clean head:

- `d4a761fdaa6836699525ef4b41e01261ea4bc0d3`.

Exact PR workflow:

- run `32603050066`;
- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release publishing SKIPPED.

## Boundary

No GPU geometry changes. `BakedSectionMesh` remains authoritative, `greedyRectangleGpuEmission=false`, and `renderCorrectMergeKeyComplete=false`.

## Next action

Integrate the allocation-clean classifier after dev7 merge-candidate construction in `SectionMeshWorkerPool`, retain it on completed tickets, add exact metrics/classification audits and first/every-64 determinism checks, then wire the frozen `ordinaryQuadEmissionSafetyEvidenceReady` coordinator gate.