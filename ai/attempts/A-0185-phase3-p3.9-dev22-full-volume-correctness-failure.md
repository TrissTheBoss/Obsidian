# A-0185 — Phase 3 P3.9 dev22 full-volume reference runtime

**Date:** 2026-08-30  
**Status:** CORRECTNESS FAIL — benefit/volume gates pass, frozen unselected-truth invariant fails  
**Binary:** `Obsidian-0.3.0-phase3-dev22.jar`  
**Canonical package commit:** `177081d5b8605439f66d70ffca481c0044e62add`  
**Canonical JAR SHA-256:** `ec0574c7d24a521eed3de13b5c7efc23f54d501c6c8915c597a283f9296a3f27`

## Objective

Execute the full unchanged A-0159 closure workload after a valid P3.9 arm and make the frozen PASS / benefit REJECT-DEFER / correctness-failure decision.

## Runtime identity

- Minecraft 26.2
- Fabric Loader 0.19.3
- Java 25.0.1
- Vulkan backend
- AMD Radeon RX 6800 XT
- AMD proprietary driver 26.8.1
- dev22 exact-section light-update preservation enabled
- both P3.9 arming lines appeared after settled READY
- strict T-junction witnesses were present before the measured workload

## Final P3.9 closure

- `partialRemeshExperimentEvidenceReady=false`
- `partialRemeshWindowArmed=true`
- completed localized episodes: **67**
- fallback episodes: **55**
- fallback accounting coherent: **true**
- fallback breakdown:
  - global lifecycle: 12
  - provenance: 17
  - multi-section: 0
  - halo/XZ-boundary: 21
  - all-slices: 0
  - pending episode: 0
  - not-LIVE: 5
- one-slice: **29**
- two-slice: **38**
- three-slice: 0
- coalesced: **6**
- exact episodes: **61**
- correctness failures: **6**
- unselected-change failures: **6**
- determinism failures: **0**

First retained failure fixture:

- episodeId: **11**
- section: **(63,4,-1)**
- sliceMask: **4** (slice 2 only)
- editCount: **1**
- failureCode: **1** (`unselected-changed`)
- failureIndex: **1** (unselected slice 1)
- deterministic: **true**

## Frozen benefit / complexity gates

The strategy is not rejected for benefit. At the measured volume, every frozen benefit and complexity threshold closes:

- localized volume: 67 >= 32 — PASS
- one-slice: 29 >= 16 — PASS
- two-slice: 38 >= 8 — PASS
- coalesced: 6 >= 1 — PASS
- mandatory fallback observed — PASS
- <=2-slice ratio: 67/67 = 100% >= 75% — PASS
- selected cells P50: **500 permille** <= 500 — PASS
- CPU ratio P50/P95: **21/291 permille** <= 600/800 — PASS
- projected upload ratio P50/P95: **597/723 permille** <= 600/800 — PASS
- metadata: **96 bytes/section** <= 1024 — PASS
- retained slice identities: **4** — PASS
- inflation mean/max: **0/0 permille** <= 50/100 — PASS
- observed/retained/overflow: **67/67/0** — bounded collector PASS
- partial GPU install changed: false
- rendered geometry changed: false

## Inherited gates

All inherited production/correctness/lifetime gates remained green:

- P3.5 border/halo ready: true; proof records **900**
- P3.6 T-junction ready: true; proofs 900/900 deterministic; strict T-junction points **3775**; junction-bearing transform proofs **672**; transform failures 0
- P3.7 differential ready: true; **900/900** deterministic proofs; missing/duplicate/optimized-without-reference/real-mismatch all 0
- P3.8 benchmark ready: true; measured samples **933**; reload delta 1; recenter delta **10**; queue rejection delta 0
- worker world reads after capture: 0
- synchronous scene mesh builds: 0
- unsafe stale scene installs: 0
- workers clean: true
- staging clean: true
- arena clean: true
- deferred resources clean: true
- worker submitted/started/completed: **942/942/942**
- worker cancelled: 0
- worker queue rejections: 0
- worker failed jobs: 0
- worker shutdown join failures: 0
- normal process exit: 0

## dev19-dev22 causal diagnostics

- dev19 provenance fallbacks observed: 17; missing/empty: 17; off-thread/overflow/other: 0
- dev20: all 17 provenance fallbacks were outer `SINGLE_SECTION` only
- dev21: all 17 were `LIGHT_UPDATE` only; biome/other/mixed/unavailable/cross-thread/overflow remained 0
- dev22 preservation: eligible checks **78**, preserved **71**, rejected **7**, reflection failures 0, cross-thread 0, overflow 0, accounting coherent true, self-test true, pending request mutated false, production invalidation changed false, partial GPU patch false, thresholds changed false

## Classification

**CORRECTNESS FAIL.** A-0159 requires zero correctness and zero unselected-change failures. Six deterministic `unselected-changed` results therefore block P3.9 despite full-volume benefit closure.

This is not evidence to weaken the slice fingerprint. `PartialRemeshSliceTruth` intentionally includes all render-affecting baked truth, including exact color and packed light. The failure proves that a block-local edit followed by a same-section light update can alter render truth in an unselected Y slice. The permanent oracle is correctly detecting that the selected slice mask is insufficient for those episodes.

## Decision

The frozen conclusion rule permits at most one clearly evidence-required safety correction after a concrete correctness failure. This run supplies that evidence.

Freeze dev23 as a **fail-closed unselected-truth fallback** correction:

- keep the unselected fingerprint comparison unchanged;
- when the matched full immutable capture reports `FAILURE_UNSELECTED_CHANGED`, classify that episode as mandatory full-section fallback instead of a completed localized episode;
- do not widen or mutate the selected slice mask;
- do not ignore packed-light/color differences;
- do not weaken A-0159 thresholds or the P3.7 oracle;
- preserve dev22 same-section light-update admission and all production rendering behavior;
- all other shadow failure codes remain correctness failures.

After this one safety correction, the next full-volume runtime is final: zero correctness failures + all unchanged thresholds => P3.9 SUCCESS; any remaining correctness failure => REJECT/DEFER; benefit/volume failure at full evidence => REJECT/DEFER. Either SUCCESS or REJECT/DEFER then moves directly to production opaque/cutout terrain replacement.
