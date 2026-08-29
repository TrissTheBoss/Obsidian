# A-0187 — Phase 3 P3.9 dev23 unselected-truth fallback implementation/package

**Date:** 2026-08-30  
**Status:** SUCCESS for implementation/package; one final full-volume runtime required  
**Parent contract:** A-0186  
**Version:** `0.3.0-phase3-dev23`

## Implementation

The correction is intentionally narrow and preserves the A-0186 safety contract.

Changed after the frozen A-0186 head:

1. `PartialRemeshExperimentTelemetry`
   - adds unique fallback bit `FALLBACK_UNSELECTED_TRUTH_CHANGED = 1 << 7`;
   - includes the bit in the exact known fallback mask and coherent fallback accounting;
   - exposes `fallbackUnselectedTruthChanged` in the immutable snapshot;
   - before completed/performance accounting, routes a **deterministic** `PartialRemeshShadowResult.FAILURE_UNSELECTED_CHANGED` to that mandatory fallback and returns;
   - nondeterministic unselected results remain on the existing correctness path (stricter fail-closed behavior);
   - all other shadow failure codes remain on the existing correctness path;
   - all A-0159 numeric thresholds remain byte-for-byte unchanged;
   - self-test covers the eighth unique fallback bit and deterministic-only routing.
2. `FrameCoordinatorDiagnosticMixin`
   - adds a dedicated final dev23 line exposing the new fallback count, total fallback/completed/exact/correctness/unselected/determinism counters, accounting coherence, self-test and threshold state;
   - no production frame behavior changes.
3. `gradle.properties`
   - version only: `0.3.0-phase3-dev23`.
4. `ObsidianBootstrap`
   - identifies the exact dev23 safety correction and explicitly states unchanged oracle, thresholds, production rendering, GPU behavior and dev22 admission.

Static compare from A-0186 (`f9dba674ea434be49a67499fb73e075b2884d096`) to implementation/package head shows exactly those four files changed. In particular, these remain unchanged:

- `AsyncMultiSectionSceneProbe`
- `AsyncMultiSectionSceneProbeLightUpdateMixin`
- `PartialRemeshLightUpdatePreservation`
- `PartialRemeshSliceTruth`
- `PartialRemeshShadowResult`
- P3.7 differential oracle/proof
- greedy mesh / worker pool / staging / arena / graphics path.

## Hosted CI / binary authority

Exact implementation/package head:

- commit: `fa779604924a6e6f7d6b845b9a3c8522bfa222b6`
- tree: `ac6d83c1204fdbd519eff391ba927225a9e00db8`
- Build workflow run: **33280453234**
- Java 25 / Gradle 9.5.1: SUCCESS
- Build step: SUCCESS
- artifact upload: SUCCESS
- release job: not applicable to draft PR branch

Artifact:

- id: **9722827474**
- name: `obsidian-3c0a136739f381ac38a03a9ff84647b7431ec07d`
- wrapper size: **763,648 bytes**
- wrapper digest: `sha256:ee97dca85800e6ac9ab8af7321595aa4e8ec047df31167eb4576c55646b49456`

Canonical runtime JAR:

- `Obsidian-0.3.0-phase3-dev23.jar`
- size: **525,855 bytes**
- SHA-256: **`f920cae998a8d27c6419dd05fb50c58cf2d7626c27616e8f7ec6e224dd4368d1`**

Sources JAR:

- `Obsidian-0.3.0-phase3-dev23-sources.jar`
- size: **271,702 bytes**
- SHA-256: `527eab20994c4fc75ef6e63ebf92a090a19e0210ff52e148c8df5cf44ebe7b38`

Package inspection confirms:

- `fabric.mod.json` version `0.3.0-phase3-dev23`;
- `PartialRemeshExperimentTelemetry.class` contains `FALLBACK_UNSELECTED_TRUTH_CHANGED` and `fallbackUnselectedTruthChanged`;
- bytecode routes deterministic failure code 1 through `recordFallback(128)` before the completed counter is incremented;
- `FrameCoordinatorDiagnosticMixin.class` and inherited dev22 light-update mixin are present.

## Runtime requirement

One final full unchanged A-0159 reference runtime is required. It must arm P3.9, produce at least 32 **safe completed** localized episodes, meet the original 16 one-slice / 8 two-slice / coalesced / fallback workload minima, exercise F3+T and a real recenter, and exit normally.

The final dev23 line to inspect is:

`Phase 3 dev23 P3.9 final unselected-truth fallback diagnostics`

Expected safe behavior: `fallbackUnselectedTruthChanged` may be nonzero. Those episodes are mandatory full-section fallbacks and must not appear in completed/performance samples. All completed localized episodes must be exact with zero correctness, unselected-change and determinism failures.

This is the final permitted P3.9 correction. After the next runtime, PASS or formal REJECT/DEFER moves directly to production opaque/cutout terrain replacement.
