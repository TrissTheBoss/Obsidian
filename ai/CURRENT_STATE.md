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
- Frozen first P3.6 slice: A-0147, planned version `0.3.0-phase3-dev13`.
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

A-0146 records corrected reference runtime SUCCESS on Windows 11 / RX 6800 XT / Vulkan / Java 25.0.1 / Minecraft 26.2:

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
- workers submitted/started/completed/cancelled `248/248/248/0`, queue-full/failure/join failure `0/0/0`;
- unchanged dev11 greedy installed records `248`, draws `43,083`, indirect calls `172,332 / 172,332`;
- workers/staging/arena/resources clean;
- staging submitted/reclaimed `24,900,504 / 24,900,504`;
- arena allocations/retired/reclaimed `744/744/744`, used bytes `0`;
- resources retired/released `248/248`, pending `0`;
- process exit code `0`.

The user reported everything looked visually fine. This is supporting evidence only because P3.5 changed no emitted geometry/shader/pipeline semantics.

Exact synchronized P3.5 evidence head `d139f8229318109f146003aa186b6d4a46cbdad6` passed hosted Build workflow `33262044878`. The ready-for-review connector mutation failed on the known obsolete `Repository.fullDatabaseId` GraphQL field. Draft PR #45 was closed as superseded, non-draft PR #46 was opened from the exact same head, and merged without source/evidence change as `1f34b3e4819b4eaa3a8fa474b09570a2e049b15a`.

Historical fixed-anchor Phase 2 flags remain irrelevant: A-0101 permanently closed that far-travel obligation.

## ACTIVE: P3.6 — T-junction policy

A-0147 freezes dev13 as a **non-geometry-changing evidence slice**. It must determine whether a mitigation is justified before altering greedy topology.

Current source truth:

- actual merged quads are `RepeatAwareGreedyMesh` dev10-safe render-correct candidates, not raw P3.3 topology rectangles;
- merged candidate positions derive from integer section-local `plane/u/v/width/height` values;
- canonical merged and passthrough face planes share `BakedSectionMesh.COMPARISON_FACE_OFFSET = 1/512`;
- section draw translation computes `sectionOrigin - cameraPosition` in double precision before conversion of the camera-relative translation to float;
- the repeat-aware vertex shader applies `Position + ModelOffset` through the existing model-view/projection path and adds no independent geometry snapping/warping;
- dev11/P3.5 visual observations found no cracks on the reference RX 6800 XT, but they did not prove that inspected frames contained known strict T-junctions.

### Frozen dev13 contract

Implement a bounded immutable worker-side T-junction topology proof over the **actual emitted merged candidate set**. Detect strict coplanar same-facing merged/merged T-junctions where an endpoint of one emitted edge lies strictly inside another emitted edge.

For every detected junction prove exact integer identities, without epsilon comparison:

- same direction and fixed face plane;
- terminating point lies strictly inside the long edge, not at a shared corner;
- coordinates are integer section-local lattice positions before the common directional `1/512` offset;
- candidate edge bounds stay in legal `0..16` section-local edge coordinates;
- generalized/passthrough-only geometry is not falsely classified.

Use reusable worker-local primitive scratch; do not introduce allocation-heavy pairwise object graphs.

Retain/extend transform evidence proving LIVE records use camera-relative section origins before float conversion, adjacent section origins preserve exact 16-block integer identity, transforms remain finite, and detected-junction sections use the same draw path.

The dev13 final gate (e.g. `tJunctionPolicyEvidenceReady=true`) requires all prior P3.5 gates plus:

- topology proof builds > 0;
- emitted merged candidates > 0;
- **strict detected T-junctions > 0** on the tested scene;
- all plane/lattice/bounds identities exact;
- determinism audits pass;
- camera-relative transform proof passes;
- explicit no-geometry/shader/pipeline-change evidence remains true;
- `workerWorldReadsAfterCapture=0`;
- zero unsafe stale installs;
- clean worker/staging/arena/resources;
- process exit code `0`.

Do not modify candidate eligibility, source suppression, merged/passthrough vertex positions, vertex/index formats, shaders, pipelines, atlas/lightmap behavior, draw classes, native Vulkan graphics scope, ownership, or lifetime in dev13.

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

Implement the A-0147 dev13 topology/transform evidence on `phase3/t-junction-policy`, integrate it into the worker/scene/final coordinator evidence chain without changing geometry, bump the development version to `0.3.0-phase3-dev13`, then obtain exact hosted CI/package evidence before runtime validation.

## Continuity order

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning. Attempts are immutable.
