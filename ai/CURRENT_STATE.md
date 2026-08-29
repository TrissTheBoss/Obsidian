# Obsidian Current State

Last updated: 2026-08-30

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- **P3.5 — border/halo correctness: COMPLETE through `0.3.0-phase3-dev12.1`.** Promotion merge `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.
- **P3.6 — T-junction policy: COMPLETE through `0.3.0-phase3-dev13`.** Promotion merge `602c53abb76dff0e27cf314abc308ff5b7ac0cae`.
- **P3.7 — differential correctness: COMPLETE through `0.3.0-phase3-dev14`.** PR #50 merge `e1e0c583160bd2a36a2fd42a969bf35e5697591b`.
- **P3.8 — meshing benchmark: COMPLETE through `0.3.0-phase3-dev15`.** PR #52 merge `49385aedff74f2382fcd9a9bb44e59cf559e63c4`; canonical dev15 SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`.
- Synchronized P3.8-complete main: `169274b468d2a278d39043938efff19844bec9ba`, Build `33272073819` SUCCESS.

## Active milestone — P3.9 partial remeshing (EXPERIMENTAL)

- Active branch: `phase3/partial-remeshing`.
- Frozen parent contract: **A-0159** — shadow-only four fixed Y slices, exact block-local dirty provenance, mandatory full fallback, matched production full-section control, permanent P3.7 correctness, and pre-frozen benefit/complexity thresholds. **Every A-0159 threshold remains unchanged.**
- Production full-section invalidation/capture/worker mesh/upload/install/draw remains authoritative. Partial-remesh output is never uploaded or drawn. No partial GPU patch exists.
- Dev16 correctness defect was corrected by dev17; dev18 closed pending same-section coalescing but evidence remained partial because provenance/halo losses dominated.
- Dev19 A-0171 proved the provenance bucket was `40/40` missing/empty, not off-thread or overflow.
- Dev20 A-0176 proved `21/21` missing/empty fallbacks were outer `SINGLE_SECTION` only.
- A-0178 exact Minecraft 26.2 bytecode proved public `LevelExtractor.setSectionDirty(III)` is shared by single-section `ClientChunkCache.onLightUpdate(...)` and broad `ClientPacketListener.handleChunksBiomes(...)`, so generic single-section preservation is forbidden.
- Dev21 A-0181 runtime **closed the caller question**: all `43/43` missing/empty provenance fallbacks remained outer `SINGLE_SECTION` and all `43/43` were `LIGHT_UPDATE` only. Caller totals were `117` relevant, `117` light update, `0` biome, `0` other; mixed/unavailable/cross-thread/overflow all `0`. Permanent P3.7 proof closed `312/312` with zero real mismatches and lifetime shutdown was clean. Do not rerun dev21.
- **A-0182 dev22 exact-section light-update preservation contract: PLAN FROZEN.** This is the final correction pass for the fixed four-slice experiment before a PASS or formal REJECT/DEFER decision.
- **A-0183 dev22 implementation/package: SUCCESS.**
- **A-0184 dev22 reference runtime: PARTIAL / NOT ARMED / NON-DECISIVE.** The run was clean but the P3.9 window never armed because the selected scene produced no strict T-junction witness: `strictTJunctionPoints=0`, `junctionBearingTransformProofRecords=0`. P3.5 remained exact; all 385 P3.7 differential proof contents were exact with zero missing/duplicate/optimized-without-reference/real mismatches, but the high-level P3.7 readiness inherited the unsatisfied P3.6 gate. `partialRemeshWindowArmed=false`, completed/observed/retained `0/0/0`; no A-0159 benefit decision is permitted from this run. No source change or new JAR is justified.

## Dev22 canonical runtime handoff

Implementation/package authority:

- exact implementation/package head `177081d5b8605439f66d70ffca481c0044e62add`
- tree `9fadf0e62b7833f7676dc067e7b4cab40ae19805`
- hosted Build `33279229989`: Java 25 / Gradle 9.5.1 SUCCESS; Build SUCCESS; artifact upload SUCCESS; release SKIPPED
- artifact id `9722466081`
- wrapper digest `sha256:d2d9b720562a86d8b3d2972e25d187bb76e8e74732fe282889038d825cc31227`
- canonical JAR `Obsidian-0.3.0-phase3-dev22.jar`
- size `524452` bytes
- SHA-256 `ec0574c7d24a521eed3de13b5c7efc23f54d501c6c8915c597a283f9296a3f27`
- sources JAR `271008` bytes; SHA-256 `da1499574481812db91ab2df1e5e9b02e3a7619e18ff8abd5013895c420655ad`

Dev22 correction scope:

- only an **already-pending shadow** partial-remesh episode can be preserved;
- only after the existing provenance drain reports `count=0` and `flags=0`;
- every accepted lifecycle event in that interval must be a `ClientChunkCache.onLightUpdate` event;
- relevant light count must equal lifecycle relevant count and be >0;
- all relevant light events must identify one identical section exactly equal to the pending episode section;
- biome/other/mixed/unavailable/wrong-section/multi-section/count-mismatch/cross-thread/overflow cases fail closed to the existing provenance fallback;
- the pending request is not widened or mutated: episode id, original fingerprints, slice mask and edit count remain unchanged;
- production full-section invalidation/rebuild continues normally;
- no A-0159 threshold, slice rule, greedy rule, P3.7 oracle, worker, upload, staging, arena, shader, pipeline, atlas/lightmap or native Vulkan behavior changed;
- partial GPU patching remains disabled.

Draft P3.9 PR: **#53**, keep DRAFT / DO NOT MERGE until the full closure decision is recorded.

## Exact next action — final P3.9 decision run, same dev22 binary

A-0184 proved scene selection must establish the inherited P3.6 witness **before** the closure workload. Use the exact canonical dev22 JAR; do not build dev23.

1. Enter the world and move to a geometrically richer area before doing the measured edit workload. A previously successful reference scene was near section `(69,4,1)`, corresponding approximately to world X `1104..1119`, Y `64..79`, Z `16..31` if the same world still contains that terrain.
2. Wait for READY and watch the log. **Do not begin the 32-episode closure workload until both arming lines have appeared:**
   - `Phase 3 dev18 P3.9 measured benchmark window armed...`
   - `Phase 3 dev18 P3.9 pending-coalescing shadow partial-remesh window armed...`
3. After arming, perform >=16 separate safe-interior one-slice edits, allowing READY recovery between episodes.
4. Perform >=8 separate safe-interior two-slice boundary edits, allowing READY recovery.
5. Perform >=1 quick same-section 3-5 edit burst for coalescing.
6. F3+T and READY recovery to exercise mandatory global fallback.
7. Move more than the 3x3 scene radius so `cameraRecenterEvents` increases by at least one, then recover READY.
8. Continue safe-interior localized edits until completed localized episodes >=32.
9. Quit normally and return the complete log.

Frozen conclusion rule:

- **PASS:** if volume, exactness, complexity, CPU and projected-upload thresholds all close, record P3.9 experimental SUCCESS and move to full production opaque/cutout terrain replacement. Partial GPU patching is not required before moving on.
- **BENEFIT FAIL at full volume:** formally REJECT/DEFER the fixed four-slice strategy without retuning thresholds and move to full production opaque/cutout terrain replacement.
- **CORRECTNESS FAIL:** use the first fixture; allow at most one clearly evidence-required safety correction, otherwise REJECT/DEFER P3.9. Do not reopen broad provenance research.

The roadmap dependency remains: real production opaque/cutout terrain replacement comes before Phase 4 large-scale GPU visibility tuning.

## Public release / handoff policy

- Keep the existing public checkpoint; internal milestone commits remain `[no-release]`.
- Runtime handoff is always the direct versioned `.jar`, never an Actions ZIP wrapper.
