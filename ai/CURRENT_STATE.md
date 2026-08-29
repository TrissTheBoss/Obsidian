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
- **Active milestone: P3.7 — Differential correctness framework.**
- P3.7 implementation branch is not yet created; create it from this synchronized `main` before source changes.
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

## ACTIVE: P3.7 — Differential correctness framework

P3.7 is now the active Phase 3 milestone.

Roadmap direction:

- run the permanent independent reference oracle and optimized mesher against representative immutable snapshots;
- expand optimized greedy output conceptually back to source-face coverage where necessary;
- compare topology coverage and render-affecting material/light/AO truth without making optimized output its own oracle;
- preserve deterministic failing fixtures for every discovered mismatch;
- keep worker/world ownership, bounded scratch, generation safety and existing runtime lifetime rules intact;
- do not consume P3.8 benchmark work or P3.9 partial-remesh experimentation.

Source findings already established for the contract freeze:

- `ReferenceFaceMesh` is deliberately independent, primitive-only, one-face-per-exposed-supported-full-cube truth carrying packed local face identity plus original state ID;
- `SectionBakedQuadSnapshot` independently freezes the supported generalized vanilla SOLID/CUTOUT render truth including source block/state, positions, UVs, exact ARGB, packed light, direction, layer and material identity;
- `RepeatAwareGreedyMesh` retains exact passthrough source-quad identities and exact merged candidate identities after suppression, which provides a bounded route to conceptually expand optimized output back to source truth without treating optimized bytes as their own oracle;
- P3.7 must use those independent/captured sources as authority and only use optimized output as the system under test.

The first P3.7 action is to create a dedicated branch from this synchronized `main`, freeze the exact first differential-correctness slice as the next immutable attempt, then implement only that slice.

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

1. Create `phase3/differential-correctness` from this synchronized `main`.
2. Inspect the exact reference/optimized identity APIs needed for conceptual expansion and fixture capture.
3. Freeze the first P3.7 contract in the next monotonic immutable attempt before source changes.
4. Implement that bounded differential proof without consuming P3.8/P3.9 scope.

## Continuity order

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning. Attempts are immutable.
