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
- First dev18 runtime A-0167 PARTIAL: exact `14/14`, pending fallbacks `0`, coalesced `1`, evidence `14/32`, one-slice `10/16`, two-slice `4/8`; upload P50/P95 `51/1000` permille.
- Second dev18 runtime A-0168 PARTIAL: exact `10/10`, zero correctness/unselected/determinism failures, coalesced `1`, pending fallbacks `0`, but only `10/32`, `7/16`, `3/8`. Fallbacks `144`: global `5`, provenance `80`, multi `0`, halo/boundary `42`, all-slices `0`, pending `0`, not-LIVE `17`.
- A-0169 froze dev19 provenance diagnostics; A-0170 packaged dev19.
- **A-0171 dev19 runtime: SUCCESS for diagnostic objective.** All `40/40` provenance fallbacks were `missingOrEmpty`; off-render-thread `0`, overflow flag/events `0/0`, other `0`. First fixture: scene `SCANNING`, center known, pending exact episode present, drain count/flags `0/0`. Inherited P3.5/P3.6/P3.7 and lifetime gates stayed green; P3.7 `389/389`, zero real mismatches, zero sync scene mesh builds, zero unsafe stale installs, clean shutdown/exit `0`.
- **Do not rerun unchanged dev19.** The problem is lifecycle/provenance causal alignment, not capacity or thread ownership.
- **A-0172 dev20 causal-correlation investigation: PLAN FROZEN.** No behavior change unless exact evidence supports a fail-closed rule.
- **A-0173 exact Minecraft 26.2 call-shape: SUCCESS for Stage 1.** Hosted exact bytecode proves `ClientLevel.setBlocksDirty` synchronously delegates to `LevelExtractor.setBlockDirty`; one render-relevant block dirty expands to a 3x3x3 block neighborhood and emits exactly `27` section-dirty calls. Thus common `54` batches are exactly two synchronous callbacks before a frame drain. The later next-frame `+1` is a distinct section-dirty path and cannot be assumed correlated.
- **A-0174 dev20 section-dirty origin tracer: PLAN FROZEN.** Diagnostic-only caller-origin classification; empty provenance still falls back exactly as before.
- **A-0175 dev20 implementation/package: SUCCESS; short reference runtime required.**

## Dev20 canonical runtime handoff

Implementation/package authority:

- exact implementation head `ffbe60535607a242ef6b2c03c8c44066e69a63ac`
- tree `9131bd8d1edf5440ae8a52817b820b17866fa2e4`
- hosted Build `33277303655`: Java 25 / Gradle 9.5.1 SUCCESS; Build SUCCESS; artifact upload SUCCESS; release SKIPPED
- artifact id `9721898429`, wrapper digest `sha256:7d3c87000818b13f9ebc30265b865583f66864ebc882e19d75a8f2064f5bf647`
- canonical JAR `Obsidian-0.3.0-phase3-dev20.jar`
- size `511,074` bytes
- SHA-256 `690b24cd6bb34e47b3b85159eda365da3e1f76f95d41b51f0b1298fc093ed2f3`
- sources JAR `262,188` bytes; SHA-256 `fcec81638fd5ee20ed03d968fcec0665bac96887a13de3b24778306972cda71a`

Dev20 safety:

- `AsyncMultiSectionSceneProbe.java` unchanged;
- `PartialRemeshDirtyProvenance.java` unchanged;
- `PartialRemeshExperimentTelemetry.java` unchanged;
- `FrameCoordinator.java` unchanged;
- no worker/mesher/upload/arena/shader/pipeline/native Vulkan changes;
- no A-0159 threshold changes;
- an empty provenance drain still records `FALLBACK_PROVENANCE` and clears pending exactly as before;
- dev20 only classifies tracked-scene-relevant `LevelExtractor.setSectionDirty(IIIZ)` calls by outermost origin: `EXACT_BLOCK`, `BLOCK_RANGE`, `NEIGHBOR_RANGE`, `SECTION_RANGE`, `SINGLE_SECTION`, or `UNCLASSIFIED`;
- only fixed primitive counters/deltas/first fixtures are retained; no stack traces or unbounded history.

Draft P3.9 PR: **#53**, keep DRAFT / DO NOT MERGE.

## Exact next action

Run the exact canonical dev20 JAR. This is a **short caller-origin diagnostic**, not a 32-episode A-0159 closure attempt:

1. wait for P3.9 windows to arm;
2. perform about 6 safe-interior ordinary edits with READY recovery;
3. perform about 3 safe-interior Y-slice-boundary edits;
4. perform one quick same-section 3-5 edit burst;
5. perform F3+T and recover READY;
6. cause one real scene recenter and recover READY;
7. quit normally and return the complete log.

Decisive lines:

- `Phase 3 dev20 P3.9 final section-dirty origin totals`;
- `Phase 3 dev20 P3.9 first section-dirty origin fixtures`;
- `Phase 3 dev20 P3.9 final provenance-origin correlation`.

Do not alter A-0159 admission behavior until those lines identify a fail-closed causal rule for the missing/empty next-frame dirty path.

## Public release / handoff policy

- Keep the existing public checkpoint; internal milestone commits remain `[no-release]`.
- Runtime handoff is always the direct versioned `.jar`, never an Actions ZIP wrapper.
