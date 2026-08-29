# A-0168 — Phase 3 P3.9 dev18 second reference runtime

Date: 2026-08-29
Status: **PARTIAL — repeated evidence-volume failure exposes unresolved provenance observability gap**
Version: `0.3.0-phase3-dev18`
Parent contract: A-0159
Correction contract: A-0165
Package checkpoint: A-0166
Prior runtime: A-0167

## Purpose

Evaluate the second exact dev18 reference run after A-0167 requested a controlled interior-X/Z workload. This run is still shadow-only; production full-section capture/mesh/upload/install/draw remains authoritative and no partial GPU patching is active.

## Runtime identity

The supplied log loaded `obsidian 0.3.0-phase3-dev18` on Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25.0.1 on Windows 11 using Vulkan on AMD Radeon RX 6800 XT. Both the P3.8 benchmark window and P3.9 pending-coalescing shadow window armed after settled READY.

Canonical binary authority remains A-0166:

- `Obsidian-0.3.0-phase3-dev18.jar`
- 496,542 bytes
- SHA-256 `cb3065a172489f197ee3f3b988fe3f202a8079ee6bafb87516f24d65d7fdf8a1`

## Final P3.9 closure

`partialRemeshExperimentEvidenceReady=false`.

Correctness and determinism remain green:

- completed localized episodes `10`
- exact `10/10`
- correctness failures `0`
- unselected-change failures `0`
- determinism failures `0`
- retained first correctness failure: none
- one-slice `7`
- two-slice `3`
- three-slice `0`
- coalesced `1`
- selected-cell P50 `250` permille
- observed/retained/overflow samples `10/10/0`

Benefit telemetry on the insufficient 10-sample population:

- CPU P50/P95 `158/802` permille
- projected upload P50/P95 `210/865` permille
- inflation mean/max `4/8` permille
- metadata `96` bytes/section
- retained identities exactly `4`
- GPU install changed `false`
- rendered geometry changed `false`

The P95 CPU and upload values are above the frozen thresholds, but the mandatory >=32 localized minimum is not met, so these P95 values are not a strategy-closure result.

## Fallback diagnostics

Exact final accounting:

- total fallbacks `144`
- global lifecycle `5`
- provenance `80`
- multi-section `0`
- halo/XZ-boundary `42`
- all-slices `0`
- pending episode `0`
- not-LIVE `17`
- accounting coherent `true`

This materially changes the diagnosis from A-0167. Pending coalescing remains solved (`0` pending fallbacks, `1` coalesced completion), but provenance is now the dominant rejection bucket.

Current source records a provenance fallback whenever either:

1. the bounded dirty-provenance drain has no exact entries (`count == 0`), or
2. the drain carries any fallback flag (currently off-render-thread and/or capacity overflow).

The runtime telemetry exposes only the aggregate provenance count. Therefore this second controlled run cannot distinguish an incomplete/misaligned `ClientLevel.setBlocksDirty` provenance surface from an actual bounded-bridge off-thread/overflow condition.

A third unchanged dev18 run would not resolve that ambiguity and is not justified.

## Inherited gates and lifetime

The measured P3.8 gate armed successfully with samples `171`, READY delta `47`, core-dirty delta `2179`, reload delta `1`, recenter delta `1`.

Final inherited closure remained green:

- P3.5 border/halo ready
- P3.6 T-junction ready
- P3.7 differential ready, determinism `377/377`
- differential missing `0`
- differential duplicate `0`
- differential optimized-without-reference `0`
- differential real mismatches `0`
- `workerWorldReadsAfterCapture=0`
- `synchronousSceneMeshBuilds=0`
- `unsafeStaleSceneInstalls=0`
- worker queue rejections `0`
- worker failed jobs `0`
- worker shutdown join failures `0`
- workers/staging/arena/resources clean
- staging submitted == reclaimed
- arena used bytes `0`
- pending retirements `0`
- process exit code `0`

The single worker cancellation/request is lifecycle-attributable and did not produce an unsafe stale install or inherited-gate failure.

## Classification

**PARTIAL.**

The four-slice shadow path remains exact on all retained episodes and dev18 pending coalescing remains validated, but A-0159 still cannot close because only `10/32`, `7/16`, and `3/8` localized/one-slice/two-slice evidence was retained. The dominant `80` provenance fallbacks are not diagnostically decomposed by dev18.

## Decision

Do **not** ask for another unchanged dev18 runtime. Do **not** weaken A-0159 thresholds. Do **not** implement partial GPU patching.

Freeze a diagnostic-only dev19 attempt that preserves all production behavior, all A-0159 thresholds, the four-slice layout, provenance hook surface, and admission policy, while adding bounded primitive telemetry that distinguishes:

- provenance drain missing/empty;
- off-render-thread fallback flag;
- capacity-overflow fallback flag;
- first bounded provenance-fallback fixture (drain count, flags, overflow event count, scene state/center-known status).

No admission behavior may change until this runtime evidence identifies the dominant provenance subreason.