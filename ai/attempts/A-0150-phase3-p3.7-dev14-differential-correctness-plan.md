# A-0150 - P3.7 dev14 differential correctness contract freeze

**Date:** 2026-08-29  
**Objective:** Freeze the first P3.7 differential-correctness slice from merged P3.6 `main` before source changes.  
**Status:** `PLAN FROZEN`  
**Planned version:** `0.3.0-phase3-dev14`  
**Branch:** `phase3/differential-correctness`

## Context

P3.6 is complete through dev13 and promotion PR #48 merge `602c53abb76dff0e27cf314abc308ff5b7ac0cae`. A-0149 proved real strict T-junctions in the actual emitted greedy path and an explicit targeted visual PASS, so no baseline T-junction mitigation is required on the proven reference Vulkan path. P3.7 is now active.

The roadmap requires a permanent differential correctness framework in which optimized output is the system under test, never its own oracle. The framework must run representative immutable snapshots through independent/reference truth and optimized output, conceptually expand greedy output back to source coverage, compare coverage plus render-affecting material/light/AO truth, and preserve deterministic failure fixtures.

## Source findings before freeze

1. `ReferenceFaceMesh` is deliberately simple and independent. It consumes only `SectionSnapshot` and emits one packed canonical face plus original state ID for each exposed supported full-cube face. Its own class documentation explicitly reserves it for Phase 3 differential testing.
2. `SectionBakedQuadSnapshot` is renderer-owned frozen vanilla render truth for supported generalized SOLID/CUTOUT geometry. It retains source block/state identity, exact positions, raw UVs, exact ARGB, packed light, direction, layer and immutable material identity after render-thread capture.
3. `BakedSectionMesh` is the exact non-greedy drawable/oracle generated from `SectionBakedQuadSnapshot` and retains exact source-quad ordering and render payload.
4. `RepeatAwareGreedyMesh` is the final dev11 optimized drawable. It already retains exact passthrough source-quad identities and exact merged candidate identities, but those identity arrays are private and currently have no read-only element accessors.
5. `RenderMergeCandidates` exposes packed candidate rectangles and representative source quads. `CanonicalFaceRenderKeys` exposes the source baked quad mapped to each canonical cell/direction.
6. `WorkerBackedSectionLifecycleProbe` already captures `ReferenceFaceMesh` before worker submission, but `SectionMeshWorkerPool.Ticket` currently receives only `SectionSnapshot` plus `SectionBakedQuadSnapshot`; therefore the independent reference mesh can be passed immutably into the worker rather than rebuilt allocation-heavily on every job.
7. The worker pipeline has a clean insertion point after the final `RepeatAwareGreedyMesh` exists and after the dev13 T-junction sidecar. Dev14 must remain a pure proof stage before publication/install.

## Frozen dev14 scope

Implement a pure immutable `DifferentialCorrectnessProof` over the actual final optimized output. Dev14 is a **non-geometry-changing correctness slice**.

### Inputs

The proof must consume only renderer-owned immutable inputs/results:

- `SectionSnapshot`;
- the independently captured `ReferenceFaceMesh`;
- `SectionBakedQuadSnapshot`;
- exact `BakedSectionMesh` oracle;
- `BinarySectionVisibility`;
- `CanonicalFaceRenderKeys`;
- `RenderMergeCandidates`;
- `RepeatAwareTransportProof`;
- final `RepeatAwareGreedyMesh`.

No live world/model/material/light/resource/GPU object may be touched by the worker proof.

Pass the already captured immutable `ReferenceFaceMesh` through `SectionMeshWorkerPool.Ticket`; do not rebuild it on every worker job merely to satisfy P3.7.

### Minimal optimized identity exposure

Add only read-only bounds-checked identity access required for differential expansion:

- `RepeatAwareGreedyMesh.passthroughSourceQuad(int)`;
- `RepeatAwareGreedyMesh.mergedCandidateIndex(int)`.

If exact oracle output ordering is needed for proof accounting, expose equivalent read-only identity access from `BakedSectionMesh`; do not expose mutable arrays or add a second mesh representation.

### Differential domain A - independent canonical topology

Build reusable primitive reference coverage from `ReferenceFaceMesh`, not from optimized visibility/candidates.

For every reference face:

- packed local cell/direction must be valid;
- state ID must equal the immutable `SectionSnapshot` state ID;
- the corresponding `BinarySectionVisibility` bit must exist exactly once;
- optimized conceptual coverage for a uniquely mapped canonical baked source face must cover that source face exactly once.

Reference faces that are intentionally unmapped or ambiguous in `CanonicalFaceRenderKeys` must be counted separately and must never be fabricated into a greedy candidate claim. They remain governed by exact generalized/passthrough behavior.

The proof must also reject optimized canonical coverage that has no corresponding independent reference face.

### Differential domain B - complete baked source-quad coverage

Conceptually expand the **actual retained final optimized identities**:

1. each retained passthrough identity contributes exactly its referenced source baked quad once;
2. each retained merged candidate is expanded across its exact integer `plane/u/v/width/height` rectangle;
3. each expanded candidate cell is resolved through `CanonicalFaceRenderKeys.sourceQuad(...)` to the source baked quad that the optimized path claims to replace.

Across the complete `SectionBakedQuadSnapshot` source domain:

- every source baked quad must have conceptual optimized coverage count exactly `1`;
- missing source quads must be `0` at promotion;
- duplicate source coverage must be `0` at promotion;
- no passthrough or merged identity may reference an out-of-range/non-source record;
- expanded merged coverage must equal `RepeatAwareTransportProof.coveredFaces()`;
- expanded merged record count must equal transport record count;
- conceptual optimized source coverage total must equal the source baked quad count.

This proof is independent of the final vertex/index byte counts; optimized output is tested by retained identities against frozen source truth.

### Differential domain C - independent merged render semantics

For every source baked quad covered by a merged candidate, compare the candidate representative and covered source quad directly from `SectionBakedQuadSnapshot`, without calling `CanonicalFaceRenderKeys.renderEquivalent(...)` as the oracle.

The independent comparator must verify:

- same supported layer;
- same immutable `MaterialIdentity`;
- same face direction;
- both quads are exact canonical unit-face geometry for the candidate direction/cell;
- same canonical corner/winding mapping;
- exact per-corner raw U/V bits;
- exact per-corner ARGB;
- exact per-corner packed light.

Exact ARGB and packed light are the frozen AO/shade/light result for this supported path, so equality closes the P3.7 material/light/AO comparison requirement for merged coverage. Raw UV equality plus the already required dev9/dev10 transport identity ensures the differential proof does not silently discard texture semantics.

State IDs are recorded for independent/reference diagnostics but are **not** required to be equal between two merged cells when their complete render output is independently proven equivalent; render equivalence, not block-state identity, is the merge rule.

### Differential domain D - passthrough truth

Every retained passthrough identity must reference exactly the same source baked quad that remains unsuppressed by conceptual merged coverage. The exact `BakedSectionMesh` oracle/source snapshot remains the render truth for that record.

Dev14 must not create a weaker synthetic passthrough representation. If byte-level read-only oracle access is necessary to prove an existing passthrough encoding field, expose only the minimum immutable accessor and compare against `BakedSectionMesh`; do not duplicate encoding logic unless required by evidence.

### Deterministic failing fixtures

The proof must preserve a compact deterministic first-failure fixture rather than emitting only a generic mismatch count.

A fixture must contain enough primitive identity to reproduce/classify the mismatch, at minimum:

- section coordinates;
- snapshot/reference/baked/optimized source fingerprints;
- mismatch domain/type;
- packed canonical face when applicable;
- source baked quad index when applicable;
- merged candidate index when applicable;
- expected and actual integer values/fingerprints needed to identify the disagreement.

Keep fixture retention bounded. A single first mismatch plus aggregate mismatch counts is sufficient for dev14 if deterministic. No allocation-heavy per-face mismatch object graph is allowed.

Add a deterministic synthetic fixture-codec/self-test that proves the mismatch-fixture path itself works even when real runtime correctness is perfect. Do **not** perturb real geometry or real proof inputs to generate runtime failures.

Any real mismatch must fail the worker/promotion path and include the deterministic fixture in the failure evidence. Do not install a result known to fail differential correctness.

### Scratch / allocation contract

Use reusable worker-local primitive scratch for source coverage and canonical coverage. Suggested bounded forms are primitive count/bit arrays sized by existing `MAX_QUADS`, `INTERIOR_CELL_COUNT` and six directions. Clear only the used bounded ranges.

The retained `DifferentialCorrectnessProof` must be summary/fingerprint/fixture data, not a retained full per-face coverage array.

Do not introduce routine allocation-heavy collections or per-face Java objects in the worker hot path.

### Worker integration / cancellation

- Build the differential proof twice before a completed ticket is published and require deterministic equality.
- Add cancellation checks around the new pure proof boundary.
- Publish the proof on the ticket only with the exact matching final greedy mesh and source fingerprints.
- Stale/cancelled tickets must never become installed differential evidence.
- Scene evidence counts only after the result survives generation/resource-epoch validation and reaches LIVE.

### Runtime evidence

Add a final layered gate, tentatively `differentialCorrectnessEvidenceReady=true`.

It must require every inherited gate through P3.6 plus all of the following:

- installed differential proof records > 0 and equal installed optimized records;
- deterministic proof audits/matches exact;
- independent reference canonical faces checked > 0;
- complete baked source quads checked > 0;
- passthrough source identities checked > 0;
- merged candidates checked > 0;
- merged expanded source faces checked > 0;
- material identity checks/matches exact;
- canonical geometry/corner checks/matches exact;
- raw UV checks/matches exact;
- exact ARGB checks/matches exact;
- packed-light checks/matches exact;
- source coverage missing = 0;
- source coverage duplicate = 0;
- optimized canonical-without-reference = 0;
- real mismatch count = 0;
- deterministic fixture self-test = PASS;
- `workerWorldReadsAfterCapture=0`;
- zero unsafe stale installs;
- zero dropped lifecycle evidence;
- clean worker/staging/arena/resource lifetime;
- normal process exit code `0`.

The test scene must include actual merged candidates/covered faces; a passthrough-only run cannot promote P3.7.

Exercise at least initial READY, an ordinary block rebuild, resource reload, and scene recenter/boundary movement so multiple immutable snapshots/resource epochs/generations are covered.

### Visual gate

Dev14 changes no emitted geometry, candidate eligibility, suppression/replacement policy, vertex/index format, shader, pipeline, atlas/lightmap behavior, draw class or native Vulkan graphics behavior. Therefore **no new human visual verdict is required** for this slice unless implementation review discovers an accidental renderer-semantic change. Existing dev11 and A-0149 visual evidence remains valid for unchanged rendering.

## Explicit non-scope

Do not consume:

- P3.8 meshing benchmark/percentile work;
- P3.9 partial remeshing;
- new greedy eligibility;
- broader model/resource compatibility;
- fluids/translucency;
- GPU visibility architecture;
- geometry/T-junction mitigation;
- native Vulkan graphics expansion.

## Promotion rule

If dev14 package CI and reference runtime satisfy the complete frozen gate with zero real mismatches and clean lifetime, P3.7 may be promoted COMPLETE under the standing Phase 3 authorization.

If a real mismatch appears, do not weaken the oracle or redefine the gate. Preserve the deterministic fixture, record a new immutable attempt, classify whether the defect is optimized coverage, source identity, material/UV/color/light/AO semantics, or framework plumbing, then make the narrow correction and rerun the exact gate.

## Immediate implementation order

1. Add the minimal immutable identity accessors.
2. Pass captured `ReferenceFaceMesh` immutably into worker tickets.
3. Implement bounded `DifferentialCorrectnessProof` + primitive scratch + fixture self-test.
4. Build twice per completed worker ticket before publication.
5. Add installed-scene aggregation and final coordinator evidence.
6. Bump to `0.3.0-phase3-dev14`.
7. Open a draft P3.7 PR and require exact Java 25 / Gradle 9.5.1 package CI before runtime handoff.
