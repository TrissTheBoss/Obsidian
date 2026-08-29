# A-0186 — Phase 3 P3.9 dev23 unselected-truth fallback contract

**Date:** 2026-08-30  
**Status:** PLAN FROZEN  
**Target version:** `0.3.0-phase3-dev23`  
**Parent evidence:** A-0185

## Intent

Perform the single final safety correction permitted by the frozen A-0159 conclusion rule. A-0185 proved that dev22 has excellent full-volume benefit but six deterministic episodes in which an unselected slice's exact render-truth fingerprint changed. Those episodes are not safe partial-remesh candidates and must fail closed to the authoritative full-section path.

## Frozen correction

1. Preserve the dev22 exact-section light-update admission logic unchanged.
2. Preserve the existing `PartialRemeshShadowResult` unselected-slice fingerprint comparison unchanged. `FAILURE_UNSELECTED_CHANGED` remains a real oracle result; it is not suppressed or redefined.
3. At matched shadow-result accounting, before an episode is counted as completed localized evidence:
   - if and only if `result.failureCode() == PartialRemeshShadowResult.FAILURE_UNSELECTED_CHANGED`, record a mandatory full-section fallback and do not count that episode as completed/exact/performance evidence;
   - use an explicit bounded fallback reason `FALLBACK_UNSELECTED_TRUTH_CHANGED` so accounting remains truthful and inspectable.
4. Every other shadow failure code remains a correctness failure under the existing `recordCompleted` path.
5. The fallback must not mutate or widen the request. Episode id, original per-slice fingerprints, slice mask and edit count remain immutable.
6. Production full-section invalidation/capture/worker mesh/upload/install/draw remains authoritative and unchanged. No partial GPU patch is introduced.
7. Add/update bounded self-test coverage so the new fallback reason participates in exact one-bit validation and fallback accounting.
8. Final shutdown telemetry must expose the new fallback count explicitly and keep `fallbackAccountingCoherent=true`.

## Explicitly forbidden

- changing any A-0159 threshold;
- changing the four fixed Y-slice layout;
- ignoring packed light, exact color, material, geometry, UV or reference truth in slice fingerprints;
- changing the permanent P3.7 reference oracle or differential semantics;
- widening a request in response to an unselected change;
- counting an unselected-truth fallback as a completed localized episode;
- changing greedy merge eligibility or slice-boundary splitting rules;
- changing worker count, queue/backpressure, staging, arena, retirement or resource lifetime behavior;
- changing shaders, pipelines, atlas/lightmap semantics, Vulkan ownership/submission or presentation;
- partial GPU patching;
- reopening broad provenance/caller research.

## Required implementation shape

Prefer the smallest source change:

- `PartialRemeshExperimentTelemetry`: add the explicit fallback bit/counter and fail-closed classification at the front of `recordCompleted` for `FAILURE_UNSELECTED_CHANGED` only.
- `FrameCoordinator`: append the new fallback counter to final closure telemetry.
- version/bootstrap wording may identify dev23 and the exact safety correction.

Core scene admission and shadow oracle classes should remain byte-for-byte unchanged unless compilation proves a narrowly required accessor change.

## Self-test / build gates

Hosted CI must compile on Java 25 / Gradle 9.5.1 and the package must contain version `0.3.0-phase3-dev23`.

Static review must prove:

- all prior fallback bits remain unchanged;
- new fallback reason is one unique power-of-two bit;
- `ALL_FALLBACK_REASONS` includes it;
- `fallbackAccountingCoherent()` includes it;
- `recordCompleted` routes only `FAILURE_UNSELECTED_CHANGED` to fallback before incrementing completed/observed percentile evidence;
- other failures still increment correctness failure telemetry;
- `thresholdsPassed()` numeric thresholds are unchanged;
- dev22 preservation, `PartialRemeshSliceTruth`, `PartialRemeshShadowResult`, P3.7, greedy mesh, worker and GPU paths are unchanged.

## Final runtime gate

Run the same full unchanged A-0159 workload after a valid P3.9 arm:

- >=32 completed safe localized episodes;
- >=16 one-slice;
- >=8 two-slice;
- >=1 coalesced;
- >=1 fallback;
- zero correctness, unselected-change and determinism failures among completed localized episodes;
- selected cells P50 <=50%;
- >=75% completed localized episodes <=2 slices;
- CPU P50<=60%, P95<=80%;
- projected upload P50<=60%, P95<=80%;
- metadata <=1024 bytes/section, exactly 4 identities, mean/max inflation <=5%/10%;
- inherited P3.5-P3.8/P3.7/lifetime gates green.

The new `FALLBACK_UNSELECTED_TRUTH_CHANGED` count may be nonzero; that is the intended conservative outcome for episodes whose full captured render truth proves the selected mask insufficient.

## Final conclusion rule

This is the last permitted P3.9 correctness correction.

- If the runtime closes all unchanged gates with zero completed-episode correctness failures: **P3.9 experimental SUCCESS**.
- If any correctness failure remains: **REJECT/DEFER fixed four-slice P3.9**.
- If safe completed volume or frozen benefit thresholds fail at full evidence: **REJECT/DEFER fixed four-slice P3.9**.

After either SUCCESS or REJECT/DEFER, proceed directly to **production opaque/cutout terrain rendering replacement**. Partial GPU patching is not a prerequisite.
