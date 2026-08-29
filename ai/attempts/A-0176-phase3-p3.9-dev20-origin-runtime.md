# A-0176 — Phase 3 P3.9 dev20 section-dirty origin runtime

Date: 2026-08-30
Status: **SUCCESS for diagnostic objective / P3.9 remains experimental**
Version: `0.3.0-phase3-dev20`
Parent contract: A-0159
Tracer contract: A-0174
Package: A-0175

## Purpose

Identify the exact `LevelExtractor` origin paired with the missing/empty provenance fallbacks proven by dev19, without changing any P3.9 admission behavior or A-0159 threshold.

## Reference runtime

Environment:

- Windows 11 / Prism Launcher 10.0.5;
- Java 25.0.1;
- Minecraft 26.2;
- Fabric Loader 0.19.3;
- Fabric API 0.158.0+26.2;
- AMD Radeon RX 6800 XT;
- Vulkan 1.4.315 AMD proprietary driver 26.8.1;
- exact canonical dev20 JAR from A-0175, SHA-256 `690b24cd6bb34e47b3b85159eda365da3e1f76f95d41b51f0b1298fc093ed2f3`.

The required short workload included ordinary edits, Y-boundary edits, a bounded burst, F3+T/resource reload, real scene recenter activity, and normal exit.

## Decisive dev20 diagnostics

Final provenance diagnostics:

- provenance fallbacks observed: `21`;
- missing/empty: `21`;
- off-render-thread: `0`;
- overflow flag/events: `0/0`;
- other: `0`;
- first fallback: `SCANNING`, center known, pending exact episode present, lifecycle relevant events `1`, provenance drain count/flags `0/0`;
- self-test true;
- production renderer/admission/threshold changes all false.

Final tracked-scene-relevant section-dirty origin totals:

- total relevant: `1224`;
- exact block: `594`;
- block range: `0`;
- neighbor range: `0`;
- section range: `0`;
- single section: `36`;
- unclassified: `594`;
- dirtyFromPlayer: `540`;
- player exact/block-range/neighbor/section-range/single-section: all `0`;
- player unclassified: `540`;
- accounting/self-test/lifecycle-relevance invariants true.

First fixtures:

- exact block: `(58,4,-4, player=false)`;
- single section: `(58,4,-4, player=false)`;
- unclassified: `(58,4,-4, player=true)`;
- no block-range, neighbor-range, or section-range fixture observed.

## Provenance-origin correlation

All `21` provenance fallbacks were classified coherently:

- exact-block-only: `0`;
- **single-section-only: `21`**;
- range/neighbor-only: `0`;
- mixed: `0`;
- no relevant section dirty: `0`;
- unclassified involved: `0`.

The first fallback fixture is exact:

- origin `SINGLE_SECTION`;
- one lifecycle-relevant section-dirty event;
- section `(58,4,-4)`;
- `dirtyFromPlayer=false`;
- provenance drain count/flags `0/0`;
- scene state `SCANNING`;
- center known;
- pending exact episode present.

This closes A-0174's decision rule: the missing/empty next-frame dirty path is consistently the public `LevelExtractor.setSectionDirty(III)` origin, not exact-block fan-out, range/neighbor fan-out, unclassified player dirties, thread misuse, or bounded bridge overflow.

## Inherited safety/correctness

- P3.5 border/halo evidence ready; `194` proof records; worker world reads after capture `0`; synchronous scene mesh builds `0`; unsafe stale scene installs `0`.
- P3.6 T-junction evidence ready; determinism `194/194`; geometry/shader/pipeline unchanged.
- P3.7 differential evidence ready; `194/194` deterministic fixtures; missing `0`, duplicate `0`, optimized-without-reference `0`, real mismatches `0`.
- P3.8 meshing benchmark evidence ready.
- worker queue full rejections `0`; worker failed jobs `0`; worker shutdown join failures `0`.
- workers/staging/arena/resources all clean; submitted staging bytes fully reclaimed; no pending upload or retirement batches; arena used bytes `0` at close.
- normal process exit code `0`.

## P3.9 result

This run intentionally cannot satisfy A-0159 evidence volume because dev20 remains diagnostic-only and preserves the existing provenance fallback. The final P3.9 shadow counters therefore show:

- completed episodes `0`;
- fallback episodes `40`;
- global lifecycle `12`;
- provenance `21`;
- multi-section `0`;
- halo/boundary `0`;
- all-slices `0`;
- pending `0`;
- not-LIVE `7`;
- correctness/unselected/determinism failures all `0`;
- observed/retained partial samples `0/0`.

This is not an A-0159 strategy failure and is not a promotion attempt.

## Conclusion

A-0176 is a successful diagnostic closure. Do not rerun unchanged dev20.

The next allowed action is exact caller inspection of public `LevelExtractor.setSectionDirty(III)` under a new immutable contract. A preservation rule is allowed only if the caller semantics establish a fail-closed causal relationship to the already-retained pending exact episode. Otherwise the provenance source must be redesigned.

PR #53 remains draft / DO NOT MERGE. Partial GPU patching remains blocked.
