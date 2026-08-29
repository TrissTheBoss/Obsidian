# A-0182 — Phase 3 P3.9 dev22 exact-section light-update preservation contract

Date: 2026-08-30
Status: PLAN FROZEN
Target version: `0.3.0-phase3-dev22`

## Why this attempt exists

A-0176 proved every observed missing/empty provenance fallback was outer `SINGLE_SECTION`. A-0178 proved generic single-section preservation is unsafe because both `ClientChunkCache.onLightUpdate(...)` and broad `ClientPacketListener.handleChunksBiomes(...)` feed the same public `LevelExtractor.setSectionDirty(III)` sink. A-0181 then proved the complete observed fallback population on the reference workload was caller-pure `LIGHT_UPDATE`: `43/43` provenance fallbacks light-update-only, with zero biome/other/mixed/unavailable, zero caller-scope cross-thread events and zero caller-scope overflow events.

This contract permits exactly one behavior correction in the P3.9 shadow experiment. It does not authorize production partial GPU patching or generic dirty-event preservation.

## Frozen correction

When `preparePartialRemeshEpisode(...)` is handling exactly `REASON_SECTION_DIRTY`, an existing pending exact partial-remesh episode MAY survive an otherwise-empty provenance drain only when every condition below is true:

1. `pendingPartialEpisode != null`.
2. `PartialRemeshDirtyProvenance` drain has `count == 0` and `fallbackFlags == 0`.
3. The already-accepted lifecycle interval is caller-classified `LIGHT_UPDATE` only.
4. At least one relevant light-update event exists in the interval.
5. The number of relevant lifecycle events equals the number of relevant light-update events used for the classification.
6. Every retained relevant light-update event in that interval refers to one identical section coordinate.
7. That exact section coordinate equals the pending episode section `(x,y,z)`.
8. No biome packet, residual/other caller, mixed caller, unavailable caller classification, scope cross-thread event, or scope overflow event participates.
9. The pending request is preserved byte-for-byte semantically: episode id, original pre-edit fingerprints, slice mask and edit count are unchanged. A light update is not a new block edit and must not widen/coalesce the slice mask.
10. Production full-section invalidation/rebuild continues normally. Only the shadow pending episode survives.

If any condition is false, the existing `FALLBACK_PROVENANCE` behavior remains authoritative and clears the pending episode exactly as dev21.

## Required bounded state

The caller tracker may retain only fixed primitive state needed to prove one lifecycle interval is single-section LIGHT_UPDATE-only:

- per-caller interval counts;
- first/representative section coordinates;
- a boolean proving all retained events for that caller interval share the same section;
- existing fixed thread/overflow counters.

No stack traces, unbounded event histories, world objects or worker-visible mutable game state are allowed.

## Explicitly forbidden

- no generic `SINGLE_SECTION` preservation;
- no biome-packet preservation;
- no cross-section preservation;
- no preservation when provenance flags are nonzero;
- no preservation when there is no pending exact episode;
- no modification to A-0159 slice boundaries, dependency expansion or thresholds;
- no changes to the permanent P3.7 oracle or greedy eligibility;
- no worker/live-world ownership changes;
- no renderer, shader, pipeline, atlas/lightmap, native Vulkan, upload, arena or lifetime behavior changes;
- no partial GPU patching;
- no threshold retuning after runtime evidence.

## Self-tests / compile-time proof obligations

Dev22 must include deterministic pure tests proving:

- light-only + same-section + exact pending-section match is eligible;
- wrong-section light update is rejected;
- two different light-update sections in one interval are rejected;
- biome-only is rejected;
- other-only is rejected;
- mixed light+biome is rejected;
- unavailable/no caller evidence is rejected;
- cross-thread or overflow evidence is rejected;
- lifecycle-count/caller-count mismatch is rejected;
- nonempty or flagged provenance is not routed through this exception;
- preservation does not mutate pending request identity, slice mask, edit count or original fingerprints.

## Runtime gate

The next dev22 runtime is not another short diagnostic. It is the full unchanged A-0159 closure workload, with correction-exercise telemetry included.

Required frozen evidence remains:

- localized completed episodes >=32;
- one-slice >=16;
- two-slice >=8;
- coalesced >=1;
- fallback >=1;
- exact completed episodes equal completed episodes;
- zero correctness, unselected-change and determinism failures;
- selected cells P50 <=500 permille;
- >=75% localized episodes use <=2 slices;
- CPU ratio P50 <=600 and P95 <=800 permille;
- projected upload ratio P50 <=600 and P95 <=800 permille;
- metadata <=1024 bytes/section, exactly four retained slice identities, mean/max inflation <=50/100 permille;
- workerWorldReadsAfterCapture=0, synchronousSceneMeshBuilds=0, unsafe stale installs=0;
- permanent P3.7 exactness and clean bounded worker/staging/arena/resource shutdown.

Dev22 must also report the number of empty-provenance intervals preserved by this exact light-update rule and the number rejected by the rule, without changing the high-level fallback accounting for rejected cases.

## Conclusion rule

This is the final correction pass for the fixed four-slice P3.9 experiment before a strategy decision.

- If the full-volume run closes all frozen A-0159 correctness/complexity/benefit gates, record P3.9 experimental SUCCESS and move on. Do **not** automatically implement partial GPU patching; that requires a separate later contract.
- If full-volume evidence closes but any frozen benefit threshold fails, formally REJECT/DEFER the fixed four-slice strategy without retuning thresholds and move on.
- If this narrow correction causes a correctness failure, use the first failure fixture and either reject/defer P3.9 or freeze one evidence-driven safety fix only if it is clearly required for correctness. Do not reopen broad provenance research.

After P3.9 concludes by PASS or formal REJECT/DEFER, the next terrain-core milestone is full production opaque/cutout terrain rendering replacement, before Phase 4 large-scale GPU visibility tuning.