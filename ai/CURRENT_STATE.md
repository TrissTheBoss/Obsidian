# Obsidian Current State

Last updated: 2026-08-29

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- **P3.5 — border/halo correctness: COMPLETE through `0.3.0-phase3-dev12.1`.**
- P3.5 promotion merge: PR #46, `[no-release]` commit `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.
- **P3.6 — T-junction policy: COMPLETE through `0.3.0-phase3-dev13`.**
- P3.6 promotion merge: PR #48, `[no-release]` commit `602c53abb76dff0e27cf314abc308ff5b7ac0cae`.
- P3.6 contract: A-0147; package A-0148; runtime/visual PASS A-0149.
- **P3.7 — Differential correctness framework: COMPLETE through `0.3.0-phase3-dev14`.**
- P3.7 promotion: PR #50, merge `e1e0c583160bd2a36a2fd42a969bf35e5697591b`; frozen A-0150; package A-0151; closure A-0153.
- **P3.8 — Meshing benchmarks: COMPLETE through `0.3.0-phase3-dev15`.**
- Frozen A-0154; package A-0156; successful baseline A-0158; promotion PR #52 merge `49385aedff74f2382fcd9a9bb44e59cf559e63c4`.
- Canonical dev15 JAR SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`.
- Synchronized P3.8-complete main: `169274b468d2a278d39043938efff19844bec9ba`, Build `33272073819` SUCCESS.

## Active milestone — P3.9 partial remeshing (EXPERIMENTAL)

- Active branch: `phase3/partial-remeshing`.
- Frozen parent contract: **A-0159** — shadow-only four fixed Y slices, exact block-local dirty provenance, mandatory full fallback, matched production full-section control, permanent P3.7 correctness, and pre-frozen benefit/complexity thresholds. **Every A-0159 threshold remains unchanged.**
- Production full-section invalidation/capture/worker mesh/upload/install/draw remains authoritative. Partial-remesh output is never uploaded or drawn. No partial GPU patch exists.
- Dev16 package A-0160 SUCCESS; reference runtime A-0161 FAILED on one shadow correctness mismatch plus insufficient evidence.
- Dev17 diagnostic/correction A-0162/A-0163 corrected the two proven shadow proof defects. Runtime A-0164 closed correctness at `19/19` but remained PARTIAL for evidence volume; diagnostics showed pending/not-LIVE admission losses.
- Dev18 pending-coalescing contract A-0165; package A-0166 SUCCESS. Canonical dev18 JAR SHA-256 `cb3065a172489f197ee3f3b988fe3f202a8079ee6bafb87516f24d65d7fdf8a1`.
- First dev18 runtime A-0167 PARTIAL: exact `14/14`, pending fallbacks `0`, coalesced `1`, evidence `14/32`, one-slice `10/16`, two-slice `4/8`; upload P50/P95 `51/1000` permille. Dominant rejected population was X/Z halo/boundary `74` plus provenance `30`.
- **Second dev18 runtime A-0168 PARTIAL:** exact `10/10`, zero correctness/unselected/determinism failures, coalesced `1`, pending fallbacks `0`, but only `10/32`, `7/16`, `3/8` retained. Fallbacks `144`: global `5`, **provenance `80`**, multi-section `0`, halo/boundary `42`, all-slices `0`, pending `0`, not-LIVE `17`. CPU P50/P95 `158/802`, upload P50/P95 `210/865`, selected-cell P50 `250`, inflation mean/max `4/8` permille. P95 misses are not closure-grade at only ten samples.
- A third unchanged dev18 run is **not justified** because the aggregate provenance bucket cannot distinguish missing/empty exact provenance from off-render-thread or bounded-overflow conditions.
- **A-0169 — dev19 provenance diagnostic contract: PLAN FROZEN.** Diagnostic-only; no admission relaxation, no provenance hook change, no threshold change, no renderer/GPU change.
- **A-0170 — dev19 implementation/package: SUCCESS; short diagnostic runtime required.**

## Dev19 canonical runtime handoff

Implementation/package authority:

- exact implementation head `510ce9b7986c84b5c2a951c681f3f1783b99518c`
- tree `39542c3972772df5984b60d5f91ce8835ffe1b37`
- hosted Build `33276352301`: Java 25 / Gradle 9.5.1 SUCCESS; Build SUCCESS; artifact upload SUCCESS; release SKIPPED
- artifact id `9721614518`, wrapper digest `sha256:f26b3c68cf2637ee88761b7e93a92620232e5cf199673f1df0f5ca8c0b4d04e2`
- canonical JAR `Obsidian-0.3.0-phase3-dev19.jar`
- size `503,422` bytes
- SHA-256 `3af4c0773627f1a74bc3c5f25746885b2051f535c68a079157aa9b549d747637`
- sources JAR `257,321` bytes; SHA-256 `e826833e142c0a10de63a6cd89a118c64ae3dcd88a5ee801d61e98573f0bbe94`

Dev19 implementation safety:

- `AsyncMultiSectionSceneProbe.java` unchanged;
- `PartialRemeshDirtyProvenance.java` unchanged;
- `PartialRemeshExperimentTelemetry.java` unchanged;
- `FrameCoordinator.java` unchanged;
- therefore the actual P3.9 classifier/admission state machine and A-0159 `thresholdsPassed()` are byte-for-byte inherited from dev18;
- dev19 adds only observational mixins plus fixed primitive provenance counters/one first fixture;
- final diagnostic distinguishes missing/empty, off-render-thread, overflow flag/events, and other existing provenance fallback paths;
- no mutable world/snapshot/block object is retained by diagnostics.

Draft P3.9 PR: **#53**, keep DRAFT / DO NOT MERGE.

## Exact next action

Run the exact canonical dev19 JAR. This is a **short diagnostic runtime**, not another 32-episode attempt:

1. wait for P3.9 windows to arm;
2. perform about 8 safe-interior ordinary one-slice edits with READY recovery;
3. perform about 4 safe-interior two-slice Y-boundary edits;
4. perform one quick same-section 3-5 edit burst;
5. perform F3+T and recover READY;
6. cause one real scene recenter and recover READY;
7. quit normally and return the complete log.

The decisive new line is `Phase 3 dev19 P3.9 final provenance diagnostics`. Classify the next correction from its subreason counts; do not alter A-0159 until that runtime evidence exists.

## Public release / handoff policy

- Keep the existing public checkpoint; internal milestone commits remain `[no-release]`.
- Runtime handoff is always the direct versioned `.jar`, never an Actions ZIP wrapper.
