# Obsidian Current State

Last updated: 2026-08-29

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- P3.1-P3.4: COMPLETE.
- **P3.5 — border/halo correctness: PROMOTION-READY after corrected dev12.1 reference runtime.**
- Active promotion PR: **#45**, branch `phase3/border-halo-correctness`.
- P3.6 broader T-junction policy remains inactive until PR #45 is merged.
- Public release intent: keep the existing public checkpoint; internal milestone merges use `[no-release]`.
- Runtime handoff: direct versioned `.jar`, never an Actions ZIP wrapper.

## P3.5 exact implementation/package truth

Frozen contract: A-0142.

Implementation/package checkpoint: A-0143.

First runtime: A-0144 PARTIAL. Scene-local P3.5 border/halo correctness passed, but one valid worker cancellation occurred after merge-candidate telemetry publication and before downstream sidecar stages. The old inherited evidence gates incorrectly required global transactional equality across stages, so promotion was correctly refused.

Correction: A-0145. The dev8/dev9/dev10 evidence gates now accept only exact nonnegative upstream-minus-downstream residuals attributable to cancelled stage prefixes. This is not a waiver: stage-local identities and all clean-lifetime/correctness requirements remain exact, and zero cancellation reduces to the previous equality case.

Canonical corrected package:

- source/package head: `9d52a0d71b73f1f148a0f672555a98d6c97fe83f`
- version: `0.3.0-phase3-dev12.1`
- `Obsidian-0.3.0-phase3-dev12.1.jar`
- size `410,243` bytes
- SHA-256 `2a11b6aff62f671e53b48b37db73f38c6e8ba2749294e2fa946267aec533a13b`
- workflow `33261260933`: Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release SKIPPED

P3.5 changes no emitted geometry, vertex/index formats, shaders, graphics pipelines, atlas/lightmap behavior, or native Vulkan graphics behavior. The validated dev11 repeat-aware greedy GPU emission path remains unchanged.

## A-0146 corrected reference runtime — SUCCESS

Reference machine/runtime:

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT 16 GB
- Vulkan backend / AMD driver 26.8.1

Final coordinator gates:

- `phase3GateReady=true`
- `schedulerEvidenceReady=true`
- `binaryVisibilityEvidenceReady=true`
- `greedyRectangleEvidenceReady=true`
- `renderMergeKeyEvidenceReady=true`
- `renderMergeCandidateEvidenceReady=true`
- `ordinaryQuadEmissionSafetyEvidenceReady=true`
- `repeatAwareUvEvidenceReady=true`
- `repeatAwareTransportEvidenceReady=true`
- `repeatAwareGreedyEmissionEvidenceReady=true`
- `borderHaloCorrectnessEvidenceReady=true`
- `productionWorkerIntegrationReady=true`
- `hardFailure=false`
- `workerWorldReadsAfterCapture=0`
- `synchronousSceneMeshBuilds=0`
- `unsafeStaleSceneInstalls=0`

P3.5 proof totals:

- border proof records `248`
- determinism `248/248`
- outward checks / binary visibility matches / independent reference matches `380,928 / 380,928 / 380,928`
- border generalized baked quads `46,913`
- frozen exact border light/color samples `187,652`
- shared-border pair audits `328`
- shared-border comparisons / matches `167,936 / 167,936`
- rendered-core / halo-only / horizontal-halo / vertical-halo dirty events `963 / 588 / 578 / 334`
- resource reload events `2`
- scene recenter events `2`
- READY transitions `28`, rebuilds `27`
- dropped lifecycle events `0`

Worker/cancellation closure:

- submitted/started/completed/cancelled `248/248/248/0`
- cancellation requests `0`
- queue-full rejections/failures/shutdown-join failures `0/0/0`
- steals `189`

Unchanged dev11 emission continuity:

- installed greedy records `248`
- draw submissions `43,083`
- indirect calls actual/expected `172,332 / 172,332 = 43,083 * 4`
- transport multi-face/representable/four-vertex-safe `4,623 / 4,623 / 4,623`
- transport records `4,623`, covered faces `10,184`, faces saved `5,561`
- install validation PASS; fixed four-class indirect contract PASS

Lifetime closure:

- workers/staging/arena/resources clean
- staging submitted/reclaimed `24,900,504 / 24,900,504`
- arena allocations/retired/reclaimed `744/744/744`, used bytes `0`
- resources retired/released `248/248`, pending `0`
- process exit code `0`

User observation: everything was visually fine. This is supporting evidence only; P3.5 itself does not invent a new visual gate because its implementation did not alter rendered geometry.

Historical `phase2ChunkLifecycleEvidenceReady=false` / `fixedAnchorReturnSceneReady=false` remain expected and non-blocking. A-0101 permanently closed the old fixed-anchor far-travel lifecycle obligation, and A-0142 explicitly excluded repeating it.

## P3.5 promotion boundary

A-0146 proves the frozen A-0142 contract. PR #45 may be marked ready and merged under the standing Phase 3 authorization after the exact synchronized evidence head passes hosted CI.

After merge, update this file and `MASTER_ROADMAP.md` to mark **P3.5 COMPLETE** and activate **P3.6 — T-junction policy**.

## Next milestone after promotion: P3.6 — T-junction policy

Do not infer broad P3.6 completion from dev11/P3.5 visual success. P3.6 must begin with a new immutable attempt that freezes an evidence-driven contract against current source truth.

Standing roadmap direction:

- keep greedy topology by default unless real Vulkan evidence demonstrates cracks/artifacts;
- prefer stable local/eye-relative positions and targeted mitigation/selective splitting over globally abandoning greedy meshing;
- preserve D-0024 greedy/oracle requirements;
- preserve dev11 exact render-equivalence eligibility and fallback semantics;
- do not widen the native graphics seam without a separate evidence-backed decision;
- any geometry-changing mitigation requires an explicit visual/runtime gate.

The first P3.6 action is source/evidence inspection and contract freeze, not speculative geometry modification.

## Durable foundation that remains authoritative

- D-0016 completion-gated reclamation.
- D-0017 bounded/backpressured staging.
- D-0020 generation-safe arena identity.
- D-0023 public Blaze3D graphics first.
- D-0024 binary/bitmask greedy meshing + permanent independent reference oracle + targeted T-junction mitigation policy.
- D-0025 narrow native compute/storage seam only.
- D-0026 explicit compute-write to indirect-read synchronization.
- D-0027 public fixed-count indirect baseline with zeroed tail.
- Unsupported/generalized/ambiguous/unsafe geometry remains exact passthrough.
- Render-thread capture/GPU ownership remains authoritative; worker live-world reads after capture remain zero.

## Continuity order

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning. Attempts are immutable.
