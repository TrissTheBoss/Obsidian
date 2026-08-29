# Obsidian Current State

Last updated: 2026-08-30

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- **P3.5 border/halo correctness: COMPLETE** through dev12.1; promotion merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.
- **P3.6 T-junction policy: COMPLETE** through dev13; promotion merge `602c53abb76dff0e27cf314abc308ff5b7ac0cae`.
- **P3.7 differential correctness: COMPLETE** through dev14; PR #50 merge `e1e0c583160bd2a36a2fd42a969bf35e5697591b`.
- **P3.8 meshing benchmark: COMPLETE** through dev15; PR #52 merge `49385aedff74f2382fcd9a9bb44e59cf559e63c4`.
- Synchronized P3.8-complete main: `169274b468d2a278d39043938efff19844bec9ba`, Build `33272073819` SUCCESS.

## Active milestone — P3.9 partial remeshing experiment

Frozen parent contract: **A-0159**. Exactly four fixed section-local Y slices, exact block-local dirty provenance, permanent P3.7 oracle, immutable per-slice render-truth fingerprints, mandatory fail-closed fallback, matched full-production control, and unchanged pre-frozen benefit/complexity thresholds.

Production full-section invalidation/capture/worker mesh/upload/install/draw remains authoritative. Shadow partial-remesh output is never uploaded or drawn. No partial GPU patch exists.

Causal history:

- dev16: initial correctness defects found.
- dev17: permanent P3.7 semantics and direction-domain correction.
- dev18: pending same-section coalescing fixed.
- dev19: missing/empty provenance identified.
- dev20: missing/empty correlated to outer `SINGLE_SECTION` lifecycle events.
- dev21: all relevant missing/empty single-section events proved `LIGHT_UPDATE` only.
- dev22: preserved an already-pending episode only across exact same-section LIGHT_UPDATE-only empty-provenance intervals.
- A-0184: first dev22 closure attempt did not arm because the chosen scene lacked the inherited strict T-junction witness.
- **A-0185: second dev22 run armed and reached full evidence volume. Benefit/complexity passed strongly, but correctness failed on 6 deterministic unselected-slice truth changes.**
- **A-0186: final permitted safety correction contract frozen.** Keep the exact unselected fingerprint check; deterministic `FAILURE_UNSELECTED_CHANGED` becomes mandatory full-section fallback before localized completion/performance accounting. Nondeterminism and every other failure remain correctness failures.
- **A-0187: dev23 implementation/package SUCCESS; one final full-volume runtime required.**

## A-0185 decisive dev22 evidence

P3.9 armed correctly. Final shadow evidence:

- completed localized episodes: 67
- fallbacks: 55
- one-slice: 29
- two-slice: 38
- coalesced: 6
- exact: 61
- correctness failures: 6
- unselected-change failures: 6
- determinism failures: 0
- first failure: episode 11, section `(63,4,-1)`, sliceMask 4, editCount 1, code 1 `unselected-changed`, failureIndex 1, deterministic true
- selected cells P50: 500 permille — PASS
- CPU P50/P95: 21/291 permille — PASS
- projected upload P50/P95: 597/723 permille — PASS
- <=2-slice ratio: 100% — PASS
- metadata 96 bytes/section; identities 4; inflation mean/max 0/0 — PASS
- observed/retained/overflow 67/67/0.

Inherited gates all green:

- P3.5 900 proof records, exact border/halo evidence
- P3.6 900/900 deterministic proofs, 3775 strict T-junction points, 672 junction-bearing transform proofs, zero transform failures
- P3.7 900/900 exact deterministic proofs; missing/duplicate/optimized-without-reference/real-mismatch all 0
- P3.8 benchmark ready with 933 samples, reload delta 1, recenter delta 10
- worker world reads after capture 0; synchronous scene mesh builds 0; unsafe stale scene installs 0
- worker submitted/started/completed 942/942/942; cancelled/queue-rejected/failed/join-failed all 0
- staging/arena/deferred resources clean; normal exit 0.

The six failures do **not** justify weakening the oracle. `PartialRemeshSliceTruth` includes render-affecting baked truth including packed light and exact color. The full immutable control proved some same-section light propagation changed an unselected slice. Those episodes are therefore unsafe partial candidates and must remain full-section fallbacks.

## Dev23 exact correction

Target/version: `0.3.0-phase3-dev23`.

Only deterministic `PartialRemeshShadowResult.FAILURE_UNSELECTED_CHANGED` is routed to the new explicit `FALLBACK_UNSELECTED_TRUTH_CHANGED` before completed/performance accounting. This keeps the failure oracle intact while conservatively excluding unsafe episodes from the partial candidate set.

Nondeterministic unselected results and every other failure code remain correctness failures. The request is never widened or mutated. Dev22 same-section light-update admission is unchanged. `AsyncMultiSectionSceneProbe`, `PartialRemeshLightUpdatePreservation`, `PartialRemeshSliceTruth`, `PartialRemeshShadowResult`, P3.7, greedy mesh, worker pool, staging/arena and production GPU/render paths are unchanged.

All A-0159 numeric thresholds remain unchanged.

## Dev23 canonical runtime handoff

Binary authority is the exact A-0187 implementation/package head:

- commit `fa779604924a6e6f7d6b845b9a3c8522bfa222b6`
- tree `ac6d83c1204fdbd519eff391ba927225a9e00db8`
- hosted Build `33280453234`: Java 25 / Gradle 9.5.1 SUCCESS; build SUCCESS; artifact upload SUCCESS
- artifact id `9722827474`
- wrapper digest `sha256:ee97dca85800e6ac9ab8af7321595aa4e8ec047df31167eb4576c55646b49456`
- canonical JAR `Obsidian-0.3.0-phase3-dev23.jar`
- size 525,855 bytes
- SHA-256 `f920cae998a8d27c6419dd05fb50c58cf2d7626c27616e8f7ec6e224dd4368d1`
- sources JAR 271,702 bytes; SHA-256 `527eab20994c4fc75ef6e63ebf92a090a19e0210ff52e148c8df5cf44ebe7b38`.

Package inspection confirms version dev23 and the new fallback field/constant; bytecode routes deterministic failure code 1 to `recordFallback(128)` before the completed counter increments.

## Exact next action — final P3.9 runtime

Use the exact canonical dev23 JAR. This is the final permitted P3.9 correction/run decision.

1. Use geometrically rich terrain and wait until both P3.9 arming lines appear before beginning measured edits.
2. Produce >=16 separate safe-interior one-slice completed episodes with READY recovery.
3. Produce >=8 separate safe-interior two-slice boundary completed episodes with READY recovery.
4. Produce >=1 same-section coalesced burst.
5. F3+T and recover READY.
6. Trigger at least one real scene recenter and recover READY.
7. Continue safe localized edits until **safe completed episodes >=32**; fallback episodes do not count toward this minimum.
8. Quit normally and return the complete log.

Inspect both the normal P3.9 closure and:

`Phase 3 dev23 P3.9 final unselected-truth fallback diagnostics`

`fallbackUnselectedTruthChanged` may be nonzero and is expected when full captured truth proves the selected mask insufficient. It must be counted as full fallback, not completed localized evidence.

Final decision:

- zero completed-episode correctness/unselected/determinism failures + all unchanged volume/benefit/complexity/inherited gates => **P3.9 experimental SUCCESS**;
- any correctness failure remains => **REJECT/DEFER fixed four-slice P3.9**;
- safe completed volume or frozen benefit fails at full evidence => **REJECT/DEFER fixed four-slice P3.9**.

After either SUCCESS or REJECT/DEFER, proceed directly to **production opaque/cutout terrain rendering replacement**. Partial GPU patching is not a prerequisite.

## PR / release policy

- Canonical P3.9 PR: #53, keep DRAFT / DO NOT MERGE until the final dev23 runtime decision is recorded.
- PR #54 is CI-isolation only for dev23 and is not a promotion path.
- Keep the existing public checkpoint; internal milestone commits remain `[no-release]`.
- Runtime handoff is always the direct versioned JAR, never the Actions ZIP wrapper.
