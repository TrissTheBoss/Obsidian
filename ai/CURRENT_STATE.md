# Obsidian Current State

Last updated: 2026-08-22

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- `main` remains at the P2.5-complete Class-A state until the remaining P2.6 runtime evidence gap closes.
- The user has standing merge authorization for the validated dependency chain; technical gates still control when merges may execute.
- For runtime handoff, provide the direct versioned `.jar`, not a GitHub Actions ZIP wrapper.

## Merged baseline

- Phase 1: COMPLETE — closing merge `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`.
- P2.1: COMPLETE — `a714e19ce871bf73136d52f85a1780109aa851dd`.
- P2.2: COMPLETE — `f9c64267c5becb3bd80897efdb09ed65a6ce8697`.
- P2.3: COMPLETE — `667230f51222746083efe89c72265d80ac9d3929`.
- P2.4: COMPLETE — `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`.
- P2.5: COMPLETE — `c17f7c6146678e18cacabc44d85c67413a040f73`.
- P2.5 Class-A synchronization: `306d74fdf2428af93feac2ce5e49296d508d9d2d`.

## Dependency gate still blocking promotion to `main`

### P2.6 / dev6 — code/correctness proven; one runtime event class still missing

- Branch: `phase2/section-lifecycle-rebuild`.
- PR #25: open/draft against `main`; contains validated P2.7 stacked into it.
- Version at the historical dev6 proof: `0.2.0-phase2-dev6`.
- Evidence: A-0080 through A-0084.

Corrected P2.6 already proved edit rebuilds, resource reload, zero dropped events, zero stale installs, deterministic capture/build invariants, full staging/arena/deferred-resource reclamation and exit 0. A-0084 did not travel far enough to exercise the fixed-target chunk lifecycle class:

- `chunkUnloadEvents=0`;
- `chunkLoadEvents=0`;
- `lifecycleGateReady=false`.

The exact Minecraft 26.2 hooks were already grounded and implemented (`ClientLevel.onChunkLoaded` / `ClientLevel.unload`). The missing fact is a real reference run proving a fixed first-scene neighborhood actually unloads and subsequently loads again while renderer correctness/lifetimes remain valid.

A-0099/A-0100 implement a downstream diagnostic-only fixed lifecycle anchor in the current production async scene so this missing class can be proven without reverting to an obsolete runtime binary. P2.6 remains formally open until that downstream run actually passes; implementation alone does not close the gate.

### P2.7 / dev7 — runtime + human validated; already incorporated into PR #25

- Version: `0.2.0-phase2-dev7`.
- Original PR #27 was runtime + human visual validated and explicitly merge-authorized.
- Merge into P2.6 base: `91eb704e769fff5d872c628a710cd8128a3314ee`.
- Combined evidence-synchronized head: `2bc4ece2b88d85c2e49e957e93a2d5f076271fd0`.
- Exact CI run: `32512405528`.
- Evidence: A-0085 through A-0089.

Validated runtime included `sceneGateReady=true`, 16 READY transitions, 15 rebuilds, 144 record installs, 9 max live records, 12 adjacent pairs, two camera recenters, zero dropped lifecycle events, zero stale installs, full reclamation and exit 0. Human visual validation reported no persistent duplicate/missing borders or stale old-window geometry.

P2.7 reaches `main` as part of PR #25 only after the P2.6 missing lifecycle evidence closes.

## Forward stacked Phase 3 P3.1

Phase 3 has been developed forward on top of validated P2.7 while P2.6 waits for its specific runtime coverage. It remains non-promotable to `main` until the P2.6 -> P2.7 dependency reaches `main`. Separate Phase 3 merge authorization already exists.

### P3.1 dev1 — worker/job boundary runtime validated

- Branch: `phase3/worker-job-architecture`.
- PR #29, stacked.
- Version: `0.3.0-phase3-dev1`.
- Evidence: A-0090 through A-0094.

A-0092 proved bounded worker queues, work stealing, cancellation, deterministic immutable-input worker meshes, zero worker world reads after capture, and clean shutdown. Dev1 deliberately kept production scene install synchronous.

### P3.1 dev2 — production async scene runtime validated

- Branch: `phase3/async-scene-integration`.
- PR #32, stacked on dev1, open/draft.
- Version: `0.3.0-phase3-dev2`.
- Evidence: A-0093 through A-0098.

A-0098 reference runtime passed the production worker-backed 3x3 scene:

- `phase3GateReady=true`;
- `productionWorkerIntegrationReady=true`;
- `productionSceneInstallStillSynchronous=false`;
- `productionWorkerSceneIntegration=true`;
- worker submitted/started/completed `131/131/131`;
- zero worker queue-full rejection/failure;
- 97 stolen jobs;
- scene worker installs / renderer record installs `131/131`;
- 25 READY transitions / 24 rebuilds;
- two camera recenters / two resource reload events;
- zero dropped lifecycle events / unsafe stale scene installs;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- exit code 0.

The same run did not observe chunk load/unload events, so it did not close P2.6.

## ACTIVE: P3.1 dev3 — scheduler/backpressure + combined lifecycle closure evidence

- Branch: `phase3/scheduler-backpressure-tuning`.
- Canonical stacked PR #34, base `phase3/async-scene-integration`, open/draft and mergeable.
- Version: `0.3.0-phase3-dev3`.
- A-0099: frozen implementation/runtime plan.
- A-0100: implementation + exact CI/package evidence.
- Runtime: **NEXT / REQUIRED**.

### Implemented dev3 behavior

`SectionMeshWorkerPool` now performs global relevance-aware selection:

`HIGH anywhere -> NORMAL anywhere -> LOW anywhere`

The 3x3 production scene assigns:

- center: HIGH;
- cardinal neighbors: NORMAL;
- diagonal neighbors: LOW.

Scene admission selects highest relevance first, admits at most two new records per render frame, and defers when bounded outstanding-work pressure reaches the configured target. Queue-full rejection remains abnormal rather than a normal backpressure mechanism.

Workers now own reusable primitive `BakedSectionMesh.BuildScratch` storage for deterministic source-quad ordering. Per-quad `Direction.values()` allocation is removed. Exact output arrays remain per-result because ownership transfers to the render thread. Full duplicate-mesh determinism builds are retained at worker warm-up / periodic audit rather than performed unconditionally for every production job. Every accepted result still executes `validateAgainst(...)`.

Raw production metrics now include priority-specific submission/completion/wait data, total/max queue and execution latency, mesh output quads/bytes, scratch use/high-water, determinism audits/matches, admission deferrals/burst size, cancellation, steals, queue rejection/failure and shutdown join failures.

### Fixed lifecycle anchor for the old P2.6 gap

The first successfully bound production scene center is frozen as a **diagnostic-only** fixed anchor. Its 3x3 chunk halo counts the same exact `ClientLevel.onChunkLoaded` and `ClientLevel.unload` events even after the active renderer scene recenters.

Anchor-only events do not advance active scene validity, do not cause current-scene invalidation and do not alter rendering ownership. They exist only to prove the missing fixed-target event class.

The final coordinator exposes:

- `phase3GateReady` — production async correctness/cleanup;
- `schedulerEvidenceReady` — dev3 scheduler/scratch/output evidence;
- `phase2ChunkLifecycleEvidenceReady` — downstream closure evidence for the old P2.6 gap;
- `fixedAnchorReturnSceneReady` — proves the async scene became LIVE after both fixed-anchor unload and load were observed.

`phase2ChunkLifecycleEvidenceReady=true` requires the Phase 3 correctness gate, nonzero fixed-anchor unload and load counts, `fixedAnchorReturnSceneReady=true`, zero dropped/stale-unsafe events, and clean worker/staging/arena/resource shutdown.

### Exact dev3 CI/package evidence

Canonical tested **code** head:

- `bd71f9ea1d7bdec02bef656338f0fd270a15dcbf`.

Exact final workflow:

- temporary CI-only PR #35 to `main`, explicitly NEVER MERGE;
- run `32582141208`;
- Java 25 / Gradle 9.5.1;
- build success;
- artifact upload success;
- artifact id `9478074937`;
- wrapper digest `sha256:b07793251fd8b6e7550e2b680df704eeb9cbe7f707221da63fa7c8a217804eeb`.

Canonical reference runtime JAR:

- `Obsidian-0.3.0-phase3-dev3.jar`;
- size `275,994` bytes;
- SHA-256 `182bac20d44de88705d5549ab5c1dd596aeef1aba53571ee7a121d472c3cc131`.

Sources JAR:

- size `143,508` bytes;
- SHA-256 `454ef1373076e327b259312196e1ff1b747198b6d99056132b715e50b34ec48b`.

Packaged metadata is Minecraft `~26.2`, Java `>=25`, version `0.3.0-phase3-dev3`. Bytecode inspection confirmed `BuildScratch`, fixed-anchor state, and all three final closure markers.

### Combined reference runtime — next concrete action

Run the canonical dev3 JAR on the reference Vulkan system:

1. enter ordinary surface terrain with supported SOLID + CUTOUT content;
2. wait for initial async scene READY;
3. visually inspect borders / stale geometry;
4. break/place blocks and allow a READY rebuild;
5. press F3+T once and allow a READY rebuild;
6. travel far enough that the **first tracked scene neighborhood actually unloads**;
7. return to that original area so those chunks load again;
8. wait until the async scene becomes LIVE/READY after returning;
9. visually confirm no stale/duplicate/missing geometry;
10. exit normally and provide the complete Prism log.

Required final evidence for combined closure:

- `phase3GateReady=true`;
- `schedulerEvidenceReady=true`;
- `phase2ChunkLifecycleEvidenceReady=true`;
- `fixedAnchorReturnSceneReady=true`;
- fixed-anchor load and unload counts > 0;
- `productionWorkerIntegrationReady=true`;
- zero synchronous scene mesh builds;
- zero queue-full rejection / worker failure / shutdown join failure;
- determinism audits > 0 and matches == audits;
- worker scratch uses > 0;
- nonzero worker output quads/vertex/index bytes;
- `maxAdmissionBurst>=2`;
- zero dropped lifecycle events;
- zero unsafe stale scene installs;
- `workersClean=true`, `stagingClean=true`, `arenaClean=true`, `resourcesClean=true`;
- no staging/arena abandonment;
- exit code 0.

Safe pre-install cancellation or stale discard may be nonzero during travel and is not itself a failure.

### Merge sequence once combined runtime passes

Do not ask for new merge authorization. The user authorization is already standing.

After a passing combined log:

1. create a new immutable runtime-success attempt explicitly stating that the stronger downstream fixed-anchor proof supersedes A-0084's missing P2.6 observation;
2. synchronize `CURRENT_STATE.md` and any roadmap status that becomes materially complete;
3. merge PR #25 to `main` with a `[no-release]` merge message so validated P2.6 + P2.7 reach the default branch without publishing an internal milestone release;
4. retarget/revalidate PR #29 against the newly advanced `main`, then merge with `[no-release]`;
5. retarget/revalidate PR #32 against `main`, then merge with `[no-release]`;
6. retarget/revalidate PR #34 against `main`, then merge with `[no-release]` if the same dev3 runtime evidence passed;
7. only after P3.1 is formally synchronized/closed may P3.2 binary/bitmask greedy meshing activate.

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning text. Attempts are immutable.

## Reference runtime

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT, 16 GB VRAM
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Vulkan backend

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + later worker-local binary/bitmask greedy meshing, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
