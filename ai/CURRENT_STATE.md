# Obsidian Current State

Last updated: 2026-08-29

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- **P3.5 — border/halo correctness: COMPLETE through `0.3.0-phase3-dev12.1`.**
- P3.5 promotion merge: PR #46, `[no-release]` commit `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.
- **Active milestone: P3.6 — T-junction policy.**
- Active branch: `phase3/t-junction-policy`.
- Active draft PR: **#47**.
- Frozen P3.6 contract: A-0147.
- Current implementation/package checkpoint: **A-0148 / `0.3.0-phase3-dev13`**.
- Public release intent: keep the existing public checkpoint; internal milestone merges use `[no-release]`.
- Runtime handoff: direct versioned `.jar`, never an Actions ZIP wrapper.

## P3.5 closure — COMPLETE

A-0142 froze the border/halo correctness contract. A-0143 recorded dev12 implementation/package. A-0144 correctly remained PARTIAL after one legitimate stage-boundary worker cancellation exposed a promotion-evidence accounting defect. A-0145 fixed that evidence model with exact cancellation-attributable residual accounting rather than a gate waiver.

Canonical corrected dev12.1 package:

- source/package head `9d52a0d71b73f1f148a0f672555a98d6c97fe83f`
- `Obsidian-0.3.0-phase3-dev12.1.jar`
- size `410,243` bytes
- SHA-256 `2a11b6aff62f671e53b48b37db73f38c6e8ba2749294e2fa946267aec533a13b`
- workflow `33261260933`: Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release SKIPPED

A-0146 corrected reference runtime SUCCESS on Windows 11 / RX 6800 XT / Vulkan / Java 25.0.1 / Minecraft 26.2:

- every inherited gate through `repeatAwareGreedyEmissionEvidenceReady=true` true;
- `borderHaloCorrectnessEvidenceReady=true`;
- `productionWorkerIntegrationReady=true`;
- `hardFailure=false`;
- `workerWorldReadsAfterCapture=0`;
- `synchronousSceneMeshBuilds=0`;
- `unsafeStaleSceneInstalls=0`;
- border proof records `248`, determinism `248/248`;
- outward / visibility / independent-reference checks `380,928 / 380,928 / 380,928`;
- shared-border comparisons/matches `167,936 / 167,936` across `328` pair audits;
- border generalized baked quads `46,913`, exact frozen light/color samples `187,652`;
- rendered-core / halo-only / horizontal-halo / vertical-halo dirty events `963 / 588 / 578 / 334`;
- resource reloads `2`, recenters `2`, READY transitions `28`, rebuilds `27`, dropped lifecycle events `0`;
- workers submitted/started/completed/cancelled `248/248/248/0`;
- unchanged dev11 greedy installed records `248`, draws `43,083`, indirect calls `172,332 / 172,332`;
- workers/staging/arena/resources clean; process exit code `0`.

The user reported everything looked visually fine. This is supporting evidence only because P3.5 changed no emitted geometry/shader/pipeline semantics.

Exact synchronized P3.5 evidence head `d139f8229318109f146003aa186b6d4a46cbdad6` passed hosted Build workflow `33262044878`. The ready-for-review connector mutation failed on the known obsolete `Repository.fullDatabaseId` GraphQL field. Draft PR #45 was closed as superseded, non-draft PR #46 was opened from the exact same head, and merged without source/evidence change as `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.

Historical fixed-anchor Phase 2 flags remain irrelevant: A-0101 permanently closed that far-travel obligation.

## ACTIVE: P3.6 — T-junction policy

A-0147 freezes dev13 as a **non-geometry-changing evidence slice**. It must determine whether a mitigation is justified before altering greedy topology.

Source truth retained by the contract:

- actual merged quads are `RepeatAwareGreedyMesh` dev10-safe render-correct candidates, not raw P3.3 topology rectangles;
- merged candidate positions derive from integer section-local `plane/u/v/width/height` values;
- canonical merged and passthrough face planes share `BakedSectionMesh.COMPARISON_FACE_OFFSET = 1/512`;
- section draw translation computes `sectionOrigin - cameraPosition` in double precision before conversion of the camera-relative translation to float;
- the repeat-aware vertex shader applies `Position + ModelOffset` through the existing model-view/projection path and adds no independent geometry snapping/warping;
- dev11/P3.5 visual observations found no cracks on the reference RX 6800 XT, but they did not prove that inspected frames contained known strict T-junctions.

### A-0148 implementation/package checkpoint

Dev13 implementation head `1504c87c3ed42dc4b4c49a1cdbdb61c4b5d8c6fc` passed the normal pull-request Build workflow `33262626441` against the exact Minecraft 26.2 dependency set.

Package artifact:

- artifact id `9717691386`
- wrapper `obsidian-5ccc041bcabe45408c9051749aa75ea9c7dde9d2`
- wrapper size `611,209` bytes
- wrapper digest `sha256:2654a9e94b5b183ed3ff302f758ab566e3b4ee09a72ed3bcf58c9a7c30185067`
- direct JAR `Obsidian-0.3.0-phase3-dev13.jar`
- direct JAR size **419,659 bytes**
- direct JAR SHA-256 **`44f7d9bec8979ddad8eb741b7024ed7ff1cb921d70cb6baff98e2a147956adc7`**
- sources JAR size `217,731` bytes
- sources SHA-256 `013aa35a35b349ef00aaedbb117c0de9ab5031788b6f5ca7d995fe486d59ea8b`

Implemented evidence path:

- `TJunctionTopologyProof` consumes the actual dev10 transport/emitted candidate identities;
- fixed primitive 6-direction × 16-plane × 17×17 lattice scratch detects strict merged/merged endpoint-on-edge intersections exactly, with no epsilon comparisons;
- bounds, direction/plane and integer-lattice identities are explicit;
- each completed worker builds the proof twice and requires deterministic `contentEquals` before publication;
- cancellation is checked around the new pure sidecar stage;
- stale/cancelled output cannot become scene evidence;
- scene aggregation happens only after generation-safe LIVE install;
- a junction-bearing LIVE record must also execute the existing camera-relative draw transform before the runtime gate can arm;
- no geometry, candidate eligibility, suppression/replacement, vertex/index format, shader, pipeline, atlas/lightmap, draw-class, native graphics, staging, arena, resource lifetime or ownership semantics changed.

Class-A roadmap synchronization is complete: `MASTER_ROADMAP.md` marks P3.5 COMPLETE, P3.6 ACTIVE, records A-0147/dev13 direction, and leaves P3.7+ ordering unchanged.

### Dev13 runtime gate

The final gate is `tJunctionPolicyEvidenceReady=true` and requires every prior P3.5/dev11 gate plus:

- installed topology proof records > 0 and equal installed records;
- proof determinism exact;
- emitted merged candidates > 0;
- strict edge-interior lattice points > 0;
- **strict detected T-junction points > 0**;
- all plane/lattice/bounds checks match exactly;
- camera-relative transform evidence on LIVE drawn records;
- at least one junction-bearing record reaches the real transform/draw path;
- explicit geometry/shader/pipeline change flags remain false;
- `workerWorldReadsAfterCapture=0`;
- zero unsafe stale installs and dropped lifecycle events;
- clean worker/staging/arena/resources;
- normal process exit code `0`.

### P3.6 runtime decision rule

Only after automated logs prove real strict T-junctions are present, perform targeted reference-hardware visual inspection while stationary/moving, rotating camera slowly/rapidly, using grazing/normal angles, crossing/recentering a section boundary, rebuilding an ordinary block, and performing F3+T.

If real T-junctions are present and the targeted Vulkan visual test passes, complete P3.6 as **no baseline mitigation required on the proven path**, retaining a revisit hook for cross-vendor/larger-scale evidence.

If artifacts appear, freeze a separate geometry-changing mitigation slice. Prefer targeted raster-safe mitigation, then selective splitting of proven-problematic boundaries; do not globally abandon greedy meshing without evidence. Any geometry change requires a new explicit visual/runtime gate.

P3.7 differential correctness remains separate and must not be consumed here.

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

Wait only on the normal hosted Build triggered by this synchronized continuity head. If green, use the exact direct `Obsidian-0.3.0-phase3-dev13.jar` runtime package on the reference machine and follow the targeted P3.6 exercise. Keep PR #47 draft until automated runtime evidence and the explicit targeted visual verdict close.

## Continuity order

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning. Attempts are immutable.
