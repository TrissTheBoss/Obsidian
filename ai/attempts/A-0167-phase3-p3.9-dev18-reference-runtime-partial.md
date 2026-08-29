# A-0167 — Phase 3 P3.9 dev18 reference runtime

Date: 2026-08-29
Status: **PARTIAL — correctness/coalescing correction validated; A-0159 evidence volume not closed**
Version: `0.3.0-phase3-dev18`
Parent contract: A-0159
Correction contract: A-0165
Package checkpoint: A-0166

## Purpose

Evaluate the exact canonical dev18 runtime under the unchanged frozen A-0159 four-slice shadow partial-remesh contract. Dev18 changes only pending same-section episode coalescing; production full-section capture/mesh/upload/install/draw remains authoritative and shadow output is never rendered.

## Runtime identity

The submitted runtime log explicitly loaded `obsidian 0.3.0-phase3-dev18` on Minecraft 26.2 / Fabric Loader 0.19.3 with Vulkan active on an AMD Radeon RX 6800 XT. The dev18 P3.9 measured benchmark and pending-coalescing shadow windows both armed after settled READY.

Canonical package authority remains A-0166:

- package-validation head `cfff336b1cb8ab18214d48af3521f65c4182acb3`
- tree `90f0a44bf811ac1c3dc6e0965ad7de8d01894693`
- Build `33275004099` SUCCESS
- `Obsidian-0.3.0-phase3-dev18.jar`
- 496,542 bytes
- SHA-256 `cb3065a172489f197ee3f3b988fe3f202a8079ee6bafb87516f24d65d7fdf8a1`

## Frozen A-0159 result

Final closure reported `partialRemeshExperimentEvidenceReady=false`.

### Correctness and determinism — PASS

- completed localized episodes: `14`
- exact episodes: `14/14`
- correctness failures: `0`
- unselected-change failures: `0`
- determinism failures: `0`
- retained first failure: none
- P3.7 final differential evidence: ready, determinism `572/572`, missing `0`, duplicate `0`, optimized-without-reference `0`, real mismatches `0`
- selected-cell P50: `250` permille
- <=2-slice localized fraction: `14/14 = 100%`

The dev17 correctness correction remains closed. No oracle weakening or threshold movement is justified.

### Dev18 pending-coalescing correction — VALIDATED

- pending-episode fallbacks: `0` (dev17: `9`)
- coalesced completed episodes: `1` (dev17: `0`)
- no correctness, unselected-change, or determinism failure appeared in the coalesced run

Therefore A-0165 solved the specific pending-episode loss it was intended to solve. No dev19 source correction is justified from this run.

### Mandatory evidence volume — FAIL / INSUFFICIENT

Frozen minimums vs observed:

- localized completed: `14 / 32`
- one-slice: `10 / 16`
- two-slice: `4 / 8`
- coalesced: `1 / 1` PASS
- fallback: `121 / >=1` PASS

This is an evidence-volume PARTIAL, not a correctness failure and not a strategy rejection.

### Fallback diagnostics

Final exact accounting:

- total fallbacks: `121`
- global lifecycle: `7`
- provenance: `30`
- multi-section: `0`
- X/Z halo or boundary: `74`
- all-slices: `0`
- pending episode: `0`
- not-LIVE: `10`
- accounting coherent: true

The dominant loss is now the test population itself: `74` X/Z halo/boundary rejections plus `30` provenance fallbacks. Pending coalescing is no longer the dominant admission defect.

The next reference run must therefore keep ordinary and boundary-Y edits well inside section-local X/Z (`localX/localZ` preferably 2..13) and avoid large/ambiguous edit patterns except for the single intentional same-section 3-5 edit coalescing burst.

### Benefit thresholds

Observed:

- selected-cell P50: `250` permille — PASS (`<=500`)
- CPU ratio P50: `174` permille — PASS (`<=600`)
- CPU ratio P95: `739` permille — PASS (`<=800`)
- projected upload ratio P50: `51` permille — PASS (`<=600`)
- projected upload ratio P95: `1000` permille — FAIL (`<=800`)

The upload evidence materially changed from dev17: P50 improved from `1000` to `51` permille, proving the projected upload path is not intrinsically fixed at full-section size. However, with only 14 retained samples, nearest-rank P95 is effectively the maximum sample (`ceil(0.95*14)=14`). A single full-size outlier can therefore force P95 to `1000`.

Because the frozen minimum requires >=32 localized episodes and that volume was not reached, the P95 miss is not sufficient to reject the four-slice strategy yet. The metric and threshold remain unchanged. A full-volume reference run must decide it.

### Complexity thresholds — PASS on observed evidence

- metadata: `96` bytes/section (`<=1024`)
- retained slice identities: exactly `4`
- inflation mean: `0` permille (`<=50`)
- inflation max: `3` permille (`<=100`)
- observed/retained/overflow: `14/14/0`
- GC delta: `67` collections / `719` ms
- allocation bytes: `not-portably-measured`
- GPU install changed: false
- rendered geometry changed: false

### Inherited benchmark/lifetime gates — PASS

The measured P3.8 gate armed with:

- `meshingBenchmarkEvidenceReady=true`
- samples `353`
- READY delta `36`
- rendered-core dirty delta `1116`
- reload delta `1`
- recenter delta `1`

Final inherited closure remained green through Phase 3/P3.7/P3.8. Final safety/lifetime evidence included:

- `workerWorldReadsAfterCapture=0`
- `synchronousSceneMeshBuilds=0`
- `unsafeStaleSceneInstalls=0`
- worker queue rejections `0`
- worker failed jobs `0`
- worker shutdown join failures `0`
- workers clean
- staging clean
- arena clean
- resources clean
- staging submitted bytes == reclaimed bytes
- arena used bytes `0`
- pending retirements `0`
- process exit code `0`

## Classification

**PARTIAL.**

Dev18 successfully validates the pending same-section coalescing correction and preserves exact correctness, but A-0159 cannot close because the run retained only 14/32 localized episodes, 10/16 one-slice and 4/8 two-slice. The workload produced 74 X/Z halo/boundary and 30 provenance fallbacks, so the valid experiment population is too small for the frozen P95 upload decision.

No code change, threshold change, oracle change, GPU patching, or strategy rejection is authorized from A-0167.

## Required next action

Repeat **the exact same dev18 binary**; do not build dev19.

After the P3.9 window arms:

1. perform at least 20 ordinary edits at section-local X/Z safely inside the section, preferably local X/Z `2..13`;
2. for one-slice samples use local Y rows `1/2`, `5/6`, `9/10`, `13/14`;
3. perform at least 10 two-slice samples at local Y `3/4`, `7/8`, or `11/12`, also with local X/Z `2..13`;
4. perform one intentional quick 3-5 edit burst in the **same section**, same safe X/Z interior, before READY returns;
5. perform F3+T after the window is armed and recover READY;
6. trigger one real scene recenter and recover READY;
7. continue safe interior localized edits until the final closure has at least `32` completed, `16` one-slice and `8` two-slice episodes;
8. exit normally and return the complete log.

If that full-volume run remains exact and projected upload P95 is still >800 permille, reject/redesign the fixed four-slice strategy under A-0159 rather than retuning the threshold.