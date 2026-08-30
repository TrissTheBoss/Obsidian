# A-0188 — Phase 3 P3.9 dev23 final runtime decision

Date: 2026-08-30
Status: **SUCCESS for final decision objective / P3.9 FIXED FOUR-SLICE STRATEGY REJECTED-DEFERRED**
Version under test: `0.3.0-phase3-dev23`
Frozen parent contract: A-0159
Final safety contract: A-0186
Canonical package authority: A-0187 / commit `fa779604924a6e6f7d6b845b9a3c8522bfa222b6`

## Objective

Run the final permitted P3.9 full-volume closure after the A-0186 safety correction. Do not weaken or retune any A-0159 threshold. A deterministic unselected-truth change is allowed only as a mandatory full-section fallback; every completed localized episode must remain exact. The result must terminate P3.9 with either experimental SUCCESS or formal REJECT/DEFER.

## Runtime environment

- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- Windows 11
- AMD Radeon RX 6800 XT
- Vulkan 1.4.315 AMD proprietary driver 26.8.1
- Obsidian `0.3.0-phase3-dev23`

The log shows the expected dev23 bootstrap banner and both P3.9 arming lines before the measured workload.

## Dev23 safety-correction result

Final dev23 diagnostic:

- `fallbackUnselectedTruthChanged=5`
- fallback episodes `150`
- completed episodes `32`
- exact episodes `32`
- correctness failures `0`
- unselected-change failures `0`
- determinism failures `0`
- fallback accounting coherent `true`
- collector self-test `true`
- oracle changed `false`
- slice-mask mutation `false`
- production renderer changed `false`
- partial GPU patch `false`
- thresholds changed `false`

This proves A-0186 performed exactly the intended conservative safety action: five episodes whose full captured truth invalidated the selected mask were removed from localized completion accounting and routed to authoritative full-section fallback. No completed localized episode retained the prior dev22 correctness defect.

Dev22 exact-section light-update preservation also remained bounded and unchanged:

- eligible checks `75`
- preserved `32`
- rejected `43`
- reflection failures `0`
- cross-thread events `0`
- scope overflow events `0`
- accounting coherent `true`
- pending request mutated `false`
- production invalidation changed `false`
- partial GPU patch `false`
- thresholds changed `false`.

## Frozen A-0159 local closure

Volume/correctness:

- completed localized episodes: **32** — PASS (`>=32`)
- one-slice episodes: **19** — PASS (`>=16`)
- two-slice episodes: **13** — PASS (`>=8`)
- three-slice episodes: `0`
- coalesced episodes: **3** — PASS (`>=1`)
- fallback episodes: **150** — PASS (`>=1`)
- exact episodes: **32/32** — PASS
- correctness failures: **0** — PASS
- unselected-change failures among completed episodes: **0** — PASS
- determinism failures: **0** — PASS
- observed/retained/overflow: `32/32/0` — PASS

Benefit:

- selected-cell P50: **250 permille** — PASS (`<=500`)
- all completed episodes used at most two slices — PASS (`>=75%` required)
- CPU P50: **24 permille** — PASS (`<=600`)
- CPU P95: **256 permille** — PASS (`<=800`)
- projected-upload P50: **300 permille** — PASS (`<=600`)
- projected-upload P95: **807 permille** — **FAIL** (`<=800` frozen threshold)

Complexity:

- metadata: **96 bytes/section** — PASS (`<=1024`)
- retained slice identities: **4** — PASS
- mean inflation: **0 permille** — PASS (`<=50`)
- max inflation: **0 permille** — PASS (`<=100`)

The dev23 diagnostic therefore reports `thresholdsPassed=false`. This is a local P3.9 frozen-threshold failure, not a classification artifact.

## Inherited correctness/lifetime evidence

The final shutdown occurs after a world/scene transition, so the high-level `phase3GateReady` / P3.5-P3.8 readiness booleans are false at the instant of close because `localSceneReady=false`. That does not rescue P3.9 and is not needed for the terminal decision: the local frozen upload-P95 gate already failed.

Underlying proof and safety contents remained clean:

- P3.5 proof records `865`; border visibility/reference counts match exactly; lifecycle classifier self-test true
- P3.6 determinism `865/865`; strict T-junction points `4,077`; junction-bearing transform proofs `606`; transform failures `0`
- P3.7 determinism `865/865`; missing `0`; duplicate `0`; optimized-without-reference `0`; real mismatches `0`; fixture self-tests `865/865`
- worker world reads after capture `0`
- synchronous scene mesh builds `0`
- unsafe stale scene installs `0`
- worker queue rejections `0`; worker failures `0`; shutdown join failures `0`
- workers clean `true`
- staging clean `true`; submitted bytes equal reclaimed bytes `57,699,168`
- arena clean `true`; used bytes `0`; allocations/retired/reclaimed `2,595/2,595/2,595`
- resources clean `true`; retired/released `865/865`; pending retirements `0`
- camera recenter events `2`
- resource reload events `2`
- process exit code `0`.

## Decision

**FORMAL REJECT/DEFER for the fixed four-slice P3.9 partial-remeshing strategy.**

Reason: after the one final evidence-required safety correction, correctness and volume closed exactly, but the frozen projected-upload P95 requirement missed by **7 permille**: `807 > 800`. A-0159 explicitly forbids retuning thresholds after measurement. The final conclusion rule requires REJECT/DEFER when safe full-volume benefit fails.

Do not build dev24 for this strategy. Do not change the slice count, widen masks, relax the oracle, or rerun in search of a favorable percentile. Retain the P3.9 evidence as a future design reference only.

## Product implication

P3.9 is no longer on the critical path. Partial GPU patching is not required before proceeding.

Next active engineering target: **production opaque/cutout terrain rendering replacement using the already-proven full-section async greedy path**, while preserving the permanent P3.7 differential oracle, bounded workers/uploads/arena lifetime, Vulkan-only ownership boundary, and full-section fallback semantics where applicable.

PR #53 must close without merge after continuity is synchronized; the experimental branch is evidence history, not a promotion path.
