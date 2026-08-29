# A-0171 — Phase 3 P3.9 dev19 provenance diagnostic runtime

Date: 2026-08-29
Status: **SUCCESS for diagnostic objective / P3.9 remains experimental**
Version: `0.3.0-phase3-dev19`
Parent contract: A-0159
Diagnostic contract: A-0169
Package checkpoint: A-0170

## Runtime environment

Reference Windows 11 runtime through Prism Launcher 10.0.5, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.158.0+26.2, Java 25.0.1, Vulkan on AMD Radeon RX 6800 XT, driver 26.8.1 / Vulkan 1.4.315.

## Diagnostic result

The dev19 diagnostic objective closed decisively.

Final provenance line:

- `provenanceFallbacksObserved=40`
- `missingOrEmpty=40`
- `offRenderThread=0`
- `overflowFlag=0`
- `overflowEvents=0`
- `other=0`
- `firstRetained=true`
- `firstFallbackIndex=1`
- `firstDrainCount=0`
- `firstFallbackFlags=0`
- `firstOverflowEvents=0`
- `firstSceneStateOrdinal=1`
- `firstSceneStateName=SCANNING`
- `firstCenterKnown=true`
- `firstPendingEpisode=true`
- `firstContextCaptured=true`
- `firstPendingProbeAvailable=true`
- `selfTest=true`
- `boundedPrimitiveState=true`
- `productionRendererChanged=false`
- `admissionPolicyChanged=false`
- `thresholdsChanged=false`

All observed high-level provenance fallbacks were therefore empty exact-provenance drains. None were caused by off-render-thread capture or bounded bridge overflow.

## A-0159 status during this short diagnostic

This run was intentionally not an evidence-volume closure attempt.

Final P3.9 aggregate:

- `partialRemeshExperimentEvidenceReady=false`
- completed episodes `0`
- fallback episodes `76`
- global lifecycle `16`
- provenance `40`
- multi-section `0`
- halo/XZ boundary `10`
- all-slices `0`
- pending episode `0`
- not-LIVE `10`
- fallback accounting coherent `true`
- correctness / unselected / determinism failures `0/0/0`
- no retained localized samples, as expected after every exact pending episode was later invalidated by a missing/empty provenance drain.

## Inherited correctness / lifetime evidence

The production renderer and permanent proofs stayed clean:

- P3.5 border/halo evidence ready `true`;
- P3.6 T-junction evidence ready `true`;
- P3.7 differential evidence ready `true`;
- P3.7 proof records `389`, determinism `389/389`;
- differential missing `0`, duplicate `0`, optimized-without-reference `0`, real mismatches `0`;
- worker world reads after capture `0`;
- synchronous scene mesh builds `0`;
- unsafe stale scene installs `0`;
- scene worker queue rejections `0`;
- workers/staging/arena/resources clean;
- staging submitted bytes equal reclaimed bytes;
- arena used bytes `0` at close;
- no pending retirements;
- process exit code `0`.

## Source diagnosis

The two relevant event surfaces are separate:

1. exact block-local provenance is captured from `ClientLevel.setBlocksDirty(BlockPos, BlockState, BlockState)`;
2. production validity is driven from `LevelExtractor.setSectionDirty(int,int,int,boolean)` into `SectionLifecycleEvents`.

`SectionLifecycleEvents` retains only sticky aggregate dirty counters/deltas; it does not retain per-dirty section identity or causal linkage to a block-local provenance record.

Current `preparePartialRemeshEpisode` drains exact provenance on every lifecycle invalidation. If the drain is empty, it records `FALLBACK_PROVENANCE` and clears `pendingPartialEpisode`, even when the scene is already rebuilding an exact pending localized episode.

The first retained dev19 fixture proves that this happened while:

- scene state was `SCANNING`;
- center was known;
- a pending exact episode already existed;
- the exact-provenance drain was empty and had no flags.

This strongly localizes the failure to lifecycle/provenance causal alignment rather than capacity, thread ownership, shadow correctness, GPU behavior, or user workload.

## Decision

Do not rerun unchanged dev19.

Do not simply ignore empty provenance drains: without causal correlation that could misattribute an unrelated unprovenanced section change to an earlier localized episode.

Freeze a separate dev20 causal-correlation investigation/correction contract before changing admission behavior.

## Promotion

No P3.9 promotion. PR #53 remains draft / DO NOT MERGE. No partial GPU patching.