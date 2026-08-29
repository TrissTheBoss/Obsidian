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
- P3.6 contract: A-0147.
- P3.6 package checkpoint: A-0148.
- P3.6 reference runtime + targeted visual PASS: A-0149.
- **P3.7 — Differential correctness framework: COMPLETE through `0.3.0-phase3-dev14`.**
- P3.7 promotion: PR #50, `[no-release]` merge `e1e0c583160bd2a36a2fd42a969bf35e5697591b` from exact validated head `a63dce386cbee215007f127e7ba801dc3218eb91`.
- Frozen P3.7 contract: A-0150.
- Implementation/package checkpoint: A-0151.
- First runtime: A-0152 PARTIAL only because required scene recenter was not exercised.
- Successful reference-runtime closure: A-0153.
- **P3.8 — Meshing benchmarks: COMPLETE through `0.3.0-phase3-dev15`.**
- Frozen P3.8 contract: **A-0154**; implementation review A-0155; package/runtime handoff A-0156; first runtime A-0157 PARTIAL only for missing measured reload; successful reference baseline A-0158.
- Canonical dev15 JAR: `Obsidian-0.3.0-phase3-dev15.jar`, 456,609 bytes, SHA-256 `eaad8132665e5f662ac30f5e71abbaff3d604f010e09ffd7aa82379c79a9ed65`.
- Exact package-validation head `e6f4b81903ddcdcb859d70a1a01c002a3f550e12` passed Build `33270995728`.
- Exact synchronized P3.8 promotion head `144875e71069f7377a97c78947883592e5c88913` passed Build `33271895037`.
- Draft PR #51 was closed only because the connected ready-for-review mutation failed on obsolete `Repository.fullDatabaseId`; non-draft same-head PR #52 merged `[no-release]` as `49385aedff74f2382fcd9a9bb44e59cf559e63c4`.
- **Active milestone: P3.9 — Partial remeshing (EXPERIMENTAL).**
- P3.9 source work remains blocked until a new immutable contract freeze is recorded from synchronized P3.8-complete `main`.
- Public release intent: keep the existing public checkpoint; internal milestone merges use `[no-release]`.
- Runtime handoff: direct versioned `.jar`, never an Actions ZIP wrapper.

## P3.5 closure — COMPLETE

A-0142 froze the border/halo correctness contract. A-0143 recorded dev12 implementation/package. A-0144 correctly remained PARTIAL after one legitimate stage-boundary worker cancellation exposed a promotion-evidence accounting defect. A-0145 fixed the evidence model with exact cancellation-attributable residual accounting rather than a gate waiver. A-0146 then closed the corrected reference runtime.

Canonical corrected dev12.1 package:

- source/package head `9d52a0d71b73f1f148a0f672555a98d6c97fe83f`
- `Obsidian-0.3.0-phase3-dev12.1.jar`
- size `410,243` bytes
- SHA-256 `2a11b6aff62f671e53b48b37db73f38c6e8ba2749294e2fa946267aec533a13b`
- workflow `33261260933`: Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release SKIPPED

Reference closure facts from A-0146:

- every inherited gate through `repeatAwareGreedyEmissionEvidenceReady=true` true;
- `borderHaloCorrectnessEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `unsafeStaleSceneInstalls=0`;
- border proof records `248`, determinism `248/248`;
- outward / visibility / independent-reference checks `380,928 / 380,928 / 380,928`;
- shared-border comparisons/matches `167,936 / 167,936`;
- workers/staging/arena/resources clean;
- process exit code `0`.

Exact synchronized P3.5 evidence head `d139f8229318109f146003aa186b6d4a46cbdad6` passed hosted Build workflow `33262044878`. The ready-for-review connector mutation failed on the known obsolete `Repository.fullDatabaseId` GraphQL field. Draft PR #45 was closed as superseded, non-draft PR #46 was opened from the exact same head, and merged without source/evidence change as `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.

Historical fixed-anchor Phase 2 flags remain irrelevant: A-0101 permanently closed that far-travel obligation.

## P3.6 — T-junction policy — COMPLETE

A-0147 froze dev13 as a **non-geometry-changing evidence slice**. The purpose was to determine whether real T-junctions in the actual emitted greedy path require mitigation before changing topology.

Source truth retained by the contract:

- actual merged quads are `RepeatAwareGreedyMesh` dev10-safe render-correct candidates, not raw P3.3 topology rectangles;
- merged candidate positions derive from integer section-local `plane/u/v/width/height` values;
- canonical merged and passthrough face planes share `BakedSectionMesh.COMPARISON_FACE_OFFSET = 1/512`;
- section draw translation computes `sectionOrigin - cameraPosition` in double precision before conversion of the camera-relative translation to float;
- the repeat-aware vertex shader applies `Position + ModelOffset` through the existing model-view/projection path and adds no independent geometry snapping/warping.

### A-0148 implementation/package checkpoint

Dev13 implementation head `1504c87c3ed42dc4b4c49a1cdbdb61c4b5d8c6fc` passed Build workflow `33262626441`.

Class-A continuity synchronization then completed without source/runtime change. Synchronized package head `505a84b76854cd4e2d3e629be204876da3ef3ff1` passed Build workflow `33262729983`: Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release SKIPPED.

Canonical dev13 package:

- artifact id `9717721369`
- wrapper `obsidian-e9a95d52469c7229689cfab55f2930fe9675c04c`
- wrapper size `611,209` bytes
- wrapper digest `sha256:bb99157db044ea3a86e55a6584f92f964b7f6573d1d5c2348c8580261fe41a7b`
- direct JAR `Obsidian-0.3.0-phase3-dev13.jar`
- direct JAR size **419,659 bytes**
- direct JAR SHA-256 **`44f7d9bec8979ddad8eb741b7024ed7ff1cb921d70cb6baff98e2a147956adc7`**
- sources JAR size `217,731` bytes
- sources SHA-256 `013aa35a35b349ef00aaedbb117c0de9ab5031788b6f5ca7d995fe486d59ea8b`

Later continuity-only head `909fc8741c79b39e0f7695b8e3fadefbf0f876e2` also passed Build workflow `33262810375`; packaged source bytes were unchanged.

Implemented dev13 evidence path:

- `TJunctionTopologyProof` consumes actual dev10 transport/emitted candidate identities;
- fixed primitive direction/plane/17x17 lattice scratch detects strict merged/merged endpoint-on-edge intersections exactly, with no epsilon comparisons;
- bounds, direction/plane and integer-lattice identities are explicit;
- each completed worker builds the proof twice and requires deterministic equality before publication;
- cancellation is checked around the pure sidecar stage;
- stale/cancelled output cannot become scene evidence;
- scene aggregation happens only after generation-safe LIVE install;
- a junction-bearing LIVE record must execute the existing camera-relative draw transform before the runtime gate can arm;
- no geometry, candidate eligibility, suppression/replacement, vertex/index format, shader, pipeline, atlas/lightmap, draw-class, native graphics, staging, arena, resource lifetime or ownership semantics changed.

### A-0149 reference runtime closure

Reference environment: Windows 11 / RX 6800 XT / Vulkan / AMD proprietary driver 26.8.1 / Java 25.0.1 / Minecraft 26.2 / Fabric Loader 0.19.3 / Prism Launcher 10.0.5.

Final automated gate:

- all inherited gates through `repeatAwareGreedyEmissionEvidenceReady=true` true;
- `borderHaloCorrectnessEvidenceReady=true`;
- `tJunctionPolicyEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `unsafeStaleSceneInstalls=0`.

Exact T-junction evidence:

- proof records/determinism `329 / 329`;
- emitted merged candidates `7,391`;
- emitted edges `29,564`;
- strict interior lattice incidences `18,260`;
- **strict T-junction points `3,231`**;
- bounds matches `29,564 / 29,564`;
- plane matches `7,391 / 7,391`;
- integer-lattice matches `29,564 / 29,564`;
- camera-relative transform proof records `329`;
- junction-bearing transform proof records `283`;
- camera-relative transform failures `0`;
- `geometryChanged=false`, `shaderChanged=false`, `pipelineChanged=false`.

Runtime exercise/lifecycle evidence:

- READY transitions `38`;
- scene rebuilds `37`;
- record installs `329`;
- camera recenter events `5`;
- resource reload events `2`;
- world-change events `3`;
- rendered-core / halo-only / horizontal-halo / vertical-halo dirty events `1,371 / 576 / 468 / 360`;
- dropped lifecycle events `0`;
- scene workers submitted/completed `333 / 333`;
- worker cancellations/requests/queue rejections/failures/join failures all `0`;
- stale result discards `4`, preinstall invalidations `4`, unsafe stale installs `0`.

Unchanged greedy GPU path remained green:

- installed records `329`;
- draw submissions `56,068`;
- actual/expected indirect calls `224,272 / 224,272`;
- transport records `7,461`;
- transport covered faces `17,209`;
- faces saved `9,748`;
- install validation PASS;
- fixed four-class indirect contract PASS.

Lifetime closure:

- workers/staging/arena/resources clean;
- staging submitted/reclaimed `28,188,008 / 28,188,008`;
- pending upload batches `0`;
- arena allocations/retired/reclaimed `987 / 987 / 987`;
- arena used bytes `0`;
- arena allocation failures `0`;
- retired/released resources `329 / 329`;
- pending retirements `0`;
- process exit code `0`.

The targeted visual gate armed only after real strict junctions were proven in a drawn LIVE section. The user then reported **“visually everything looked fine.”** This is the required explicit visual PASS for the frozen P3.6 contract.

### P3.6 decision

**No baseline T-junction mitigation is required on the proven reference Vulkan path.**

Do not add global edge splitting or otherwise weaken greedy meshing based only on theoretical T-junction risk. Retain the D-0024 cross-vendor/larger-scale revisit hook. If future evidence shows cracks/pinholes/flicker on another vendor, driver, scale or renderer change, prefer targeted raster-safe mitigation or selective splitting of proven-problematic boundaries before broader topology changes.

Class-A roadmap synchronization commit `4bff1cb4c1b1a31b2bae5c70a1a79e440cb91609` recorded P3.6 COMPLETE / P3.7 ACTIVE. Final fully synchronized promotion head `3e2a6c751ad0a77a87f2e60e4de9b80757dc75fc` passed hosted Build workflow `33263393349`: Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release SKIPPED. Draft PR #47 was closed as superseded only because the connected ready-for-review mutation is known broken; non-draft PR #48 promoted the exact same validated head and merged `[no-release]` as `602c53abb76dff0e27cf314abc308ff5b7ac0cae`.

## P3.7 — Differential correctness framework — COMPLETE

A-0150 freezes `0.3.0-phase3-dev14` as the first bounded differential-correctness slice. The optimized output is the system under test; it never becomes its own oracle.

Authoritative truth surfaces:

- `ReferenceFaceMesh` remains the deliberately independent primitive canonical topology oracle;
- `SectionBakedQuadSnapshot` independently freezes supported vanilla SOLID/CUTOUT render truth including source block/state identity, positions, UVs, exact ARGB, packed light, direction, layer and immutable material identity;
- exact `BakedSectionMesh` remains the non-greedy drawable/oracle derived from that frozen render truth;
- `RepeatAwareGreedyMesh` retains the actual final optimized passthrough source identities and merged-candidate identities that must expand back to authoritative source coverage.

### A-0151 implementation/package checkpoint

Dev14 implementation head: `83388481b7a0fd566daf5cc39fd8713945df912c`.

Draft PR: #49.

Hosted pull-request Build workflow `33264171457` passed:

- Java 25 / Gradle 9.5.1 build SUCCESS;
- artifact upload SUCCESS;
- release SKIPPED.

Canonical dev14 package:

- artifact id `9718131242`;
- wrapper `obsidian-99409bc5317779124d619e6c191ce23f4593aa67`;
- wrapper size `642,620` bytes;
- wrapper digest `sha256:41686a431bf9e7699be06de197d85ba72eb0543e5d0ad70e1f8fadb1a4aa0c4c`;
- direct JAR `Obsidian-0.3.0-phase3-dev14.jar`;
- direct JAR size **441,563 bytes**;
- direct JAR SHA-256 **`9d79b1de179768d5b872178564f708b42dab0d9cc8e99a0dd8f80bf10336bc39`**;
- sources JAR size `228,167` bytes;
- sources SHA-256 `2b6b98611885bd264db5280cc866b6dddae3895e03e8389dd81bd29484d9ba30`.

Implemented proof contract:

- already captured immutable `ReferenceFaceMesh` is passed into worker tickets; workers do not rebuild it from live state;
- only minimal read-only optimized identity accessors were added;
- `DifferentialCorrectnessProof` uses reusable bounded primitive scratch and retains summary/fingerprint/first-fixture data only;
- actual final passthrough identities and merged candidates are expanded conceptually back to source baked-quad coverage;
- complete source coverage must be exactly once: missing and duplicate coverage are promotion failures;
- independent canonical topology is checked against binary visibility and optimized canonical mapping;
- optimized canonical mappings without an independent reference face are rejected;
- merged expansion accounting must match the dev10 transport proof;
- merged covered source quads are independently compared against their representative for immutable material/layer, direction, canonical geometry/corner order, raw UV bits, exact ARGB and packed-light/AO result;
- a bounded deterministic first-failure fixture is preserved for any real mismatch;
- a deterministic synthetic fixture self-test verifies the diagnostic path without perturbing production proof inputs;
- every completed worker builds the proof twice and requires deterministic equality before publication;
- real differential mismatch results fail before install;
- scene evidence counts only after generation/resource-epoch validation and LIVE installation.

Dev14 remains explicitly non-render-changing: no greedy eligibility, suppression/replacement policy, emitted positions, vertex/index formats, shaders, pipelines, atlas/lightmap behavior, draw classes, native graphics scope or resource-lifetime semantics changed. No new human visual verdict is required unless runtime discovers an accidental renderer-semantic change.

### A-0152 first reference runtime — PARTIAL, no code defect

The first dev14 reference run passed the complete automated differential/lifetime gate but correctly remained PARTIAL because `cameraRecenterEvents=0` and `scene-recenter` was absent. A-0150 required actual section-boundary/recenter movement followed by READY, so no waiver was taken and no source/package change was made.

### A-0153 reference runtime closure — SUCCESS

The exact same canonical dev14 JAR was rerun and closes the complete frozen contract:

- all inherited gates through P3.6 plus `differentialCorrectnessEvidenceReady=true`;
- proof records/determinism `238 / 238`;
- reference faces `79,754`, mapped/unmapped/ambiguous `59,933 / 0 / 19,821`;
- source baked quads `165,638`;
- passthrough identities `154,306`;
- real merged candidates / expanded merged source faces `4,964 / 11,332`;
- material/direction/canonical-geometry checks exact `11,332 / 11,332` each;
- UV/ARGB/light checks exact `45,328 / 45,328` each;
- missing/duplicate/optimized-without-reference/real-mismatch all `0`;
- fixture self-tests `238 / 238`;
- `cameraRecenterEvents=5` and `scene-recenter` observed, with READY after recenter activity;
- resource reload events `2` and ordinary dirty/rebuild activity present;
- scene workers `244 / 244`, no cancellation/failure/rejection/join failure;
- unsafe stale installs and dropped lifecycle evidence `0`;
- staging `22,315,152 / 22,315,152` bytes reclaimed;
- arena allocations/retired/reclaimed `714 / 714 / 714`, used bytes `0`;
- resources retired/released `238 / 238`, pending `0`;
- process exit code `0`;
- differential geometry/shader/pipeline change flags remain `false`.

No new visual verdict was required because dev14 changes no renderer semantics. Exact synchronized evidence head `a63dce386cbee215007f127e7ba801dc3218eb91` passed hosted Build workflow `33265069030`, and non-draft workaround PR #50 merged it `[no-release]` as `e1e0c583160bd2a36a2fd42a969bf35e5697591b` after the known ready-for-review connector failure on draft PR #49.

### Required dev14 reference runtime gate — CLOSED by A-0153

A-0153 satisfies the frozen A-0150 contract. The required final evidence included:

Required final evidence includes every prior P3.6 gate plus:

- `differentialCorrectnessEvidenceReady=true`;
- installed differential proof records > 0 and equal installed optimized records;
- deterministic proof audits exact;
- independent reference canonical faces checked > 0;
- complete source baked quads checked > 0;
- passthrough identities checked > 0;
- merged candidates > 0 and expanded merged source faces > 0;
- material checks/matches exact;
- canonical geometry/corner checks/matches exact;
- raw UV checks/matches exact;
- exact ARGB checks/matches exact;
- packed-light checks/matches exact;
- source coverage missing `0`;
- source coverage duplicate `0`;
- optimized canonical-without-reference `0`;
- real mismatch count `0`;
- deterministic fixture self-test PASS;
- `workerWorldReadsAfterCapture=0`;
- zero unsafe stale installs and dropped lifecycle evidence;
- clean worker/staging/arena/resources;
- normal process exit code `0`.

A-0153 contained actual merged candidates/covered faces and exercised initial READY, ordinary block rebuild/READY, F3+T/resource reload/READY, section-boundary scene recenter/READY, then normal exit.

Future differential regressions must not weaken the independent/captured oracle. Preserve the deterministic fixture, record a new immutable attempt, classify the exact disagreement and make only the narrow correction.

P3.7 closure remains authoritative. P3.8 is now complete; P3.9 may activate only under a new immutable experimental contract freeze.

## P3.8 — Meshing benchmarks — COMPLETE

A-0154 freezes the first P3.8 slice as a non-render-changing measurement baseline for the actual full-section production worker path. Planned version: `0.3.0-phase3-dev15`.

Source truth before implementation:

- `SectionMeshWorkerPool.Ticket` already records enqueue/start/end timestamps and exposes queue wait plus full execution time;
- the pool already retains totals/maxima for queue wait/execution, per-stage build time, output bytes, queue pressure, priority, steals/cancellations and reusable scratch high-water;
- the missing baseline capability is bounded distribution/percentile evidence plus explicit workload/window identity, not a second benchmark-only mesher;
- P3.7 differential correctness remains part of the measured production cost and must stay green during benchmarking.

Frozen dev15 requirements:

- bounded primitive P50/P95/P99/max queue-wait and full-ticket execution telemetry;
- labeled warm-up versus measured benchmark windows armed only after settled READY;
- representative ordinary rebuild, resource reload and real scene-recenter/traversal activity;
- workload identity including source quads/reference faces/rectangles/merge candidates/passthrough+merged identities/faces saved/output vertex+index bytes;
- scratch high-water, GC deltas where portable, worker utilization/queue pressure and exact retained/overflow sample accounting;
- deterministic synthetic collector self-test without injecting fake runtime evidence;
- every inherited gate through P3.7 remains mandatory;
- no performance pass/fail threshold is invented before the first trustworthy baseline exists;
- no P3.9 partial remeshing, merge-policy tuning, worker-count tuning, graphics change or benchmark-only simplification.

A valid but slower-than-desired baseline is still valid P3.8 evidence; future optimization targets must be derived from recorded measurements rather than changing the workload after seeing results.

### A-0158 reference benchmark closure — SUCCESS / PROMOTION-READY

The exact same canonical dev15 JAR from A-0156 was rerun after A-0157. This time the measured window included the missing F3+T/resource-reload exercise and the complete frozen A-0154 runtime contract closed:

- `meshingBenchmarkEvidenceReady=true`;
- benchmark duration about `47.121 s`;
- completed / retained / overflow samples `305 / 305 / 0`;
- queue wait P50/P95/P99/max `25.7 / 50.5 / 80.0 / 3,683.9 us`;
- full production-ticket execution mean/P50/P95/P99/max `1.313 / 1.001 / 2.664 / 4.432 / 14.408 ms`;
- measured workload source quads/reference faces `178,238 / 71,606`;
- merge candidates `43,239`, merged identities `3,305`, merged covered source faces `7,310`, faces saved `4,005`;
- output quads `174,233`, vertex/index bytes `19,937,136 / 4,181,592`;
- max queued/running jobs `1 / 2`, measured worker queue rejections `0`;
- measured READY/core-dirty/resource-reload/recenter deltas `32 / 1,929 / 1 / 2`;
- JVM GC count/time deltas `24 / 278 ms`; exact allocation bytes remain intentionally `not-portably-measured`;
- inherited P3.7 differential proof `308/308` deterministic with real merged coverage and zero missing/duplicate/optimized-without-reference/real mismatches;
- workers/staging/arena/resources clean; process exit code `0`.

This is the first trustworthy P3.8 reference baseline. No numerical threshold is retrofitted after seeing it. Exact synchronized promotion head `144875e71069f7377a97c78947883592e5c88913` passed hosted Build `33271895037`; non-draft same-head PR #52 merged `[no-release]` as `49385aedff74f2382fcd9a9bb44e59cf559e63c4`. P3.8 is COMPLETE.

## Durable foundation that remains authoritative

- D-0016 completion-gated reclamation.
- D-0017 bounded/backpressured staging.
- D-0020 generation-safe arena identity.
- D-0023 public Blaze3D graphics first.
- D-0024 binary/bitmask greedy meshing + permanent independent reference oracle + evidence-driven targeted T-junction mitigation.
- D-0025 narrow native compute/storage seam only.
- D-0026 explicit compute-write to indirect-read synchronization.
- D-0027 public fixed-count indirect baseline with zeroed tail.
- Unsupported/generalized/ambiguous/unsafe geometry remains exact passthrough.
- Render-thread capture/GPU ownership remains authoritative; worker live-world reads after capture remain zero.

## Immediate next action

P3.8 is merged and COMPLETE. From the exact synchronized P3.8-complete `main`, create the P3.9 experimental branch and freeze partial-remeshing in a new immutable attempt before any source change. The experiment must prove benefit against the recorded A-0158 full-section baseline while preserving every inherited correctness/lifetime gate and explicitly accounting for metadata, GPU-allocation and fragmentation complexity.

Do not implement P3.9 before its contract freeze.

## Continuity order

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning. Attempts are immutable.
