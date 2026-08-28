# Obsidian Current State

Last updated: 2026-08-29

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Product phase: **Phase 3 — production asynchronous CPU mesher / greedy meshing**.
- **P3.4 — render-correct merge and emission semantics: COMPLETE through dev11.**
- Active milestone: **P3.5 — border/halo correctness**.
- P3.5 implementation/version branch is not frozen yet; freeze the correctness/runtime contract before coding.
- P3.6 broader T-junction policy is **not active**.
- Public release intent: keep the existing public checkpoint until a separate release decision.
- Runtime handoff: direct versioned `.jar`, never an Actions ZIP wrapper.

## Completed foundation

- Phase 0 COMPLETE.
- Phase 1 COMPLETE.
- Phase 2 through P2.7 COMPLETE.
- P3.1 dev1/dev2/dev3 COMPLETE — PRs #29/#32/#34.
- P3.2 dev4 binary visibility COMPLETE — PR #36 merge `54ca3cb2d64eda958579407728e757eb0c98b948`.
- P3.3 dev5 topology rectangles COMPLETE — PR #37 merge `34caa19a9de70ba8e0395a2992180f3a24a3f7aa`.
- P3.4 dev6 canonical render-key sidecar COMPLETE — PR #38 merge `967c4511cd11cd721886feae6d146f4412790a6d`.
- P3.4 dev7 render-key-aware merge candidates COMPLETE — PR #39 merge `cec4ecb2432ec92f17a94a358895de6c2f21257e`.
- P3.4 dev8 ordinary four-vertex emission-safety COMPLETE — PR #40 merge `7a15f857a081fba642fcc28811ce88363b5abb66`.
- P3.4 dev9 repeat-aware UV descriptors COMPLETE — PR #41 merge `59471127162aaf02c9c87e679e1c4c361f968fac`.
- P3.4 dev10 repeat-aware transport/sampling proof COMPLETE — PR #42 merge `3f75cf4d7e4a65aa6b12053fd75507d1cd292b34`.
- **P3.4 dev11 repeat-aware greedy GPU emission canary COMPLETE — promotion PR #44 merge `b01ff98c4dbe6e548550f86784547afc37db2b2d`.**

PR #43 contains the same proven dev11 branch history but was closed unmerged only because the connected GitHub ready-for-review GraphQL wrapper failed on an obsolete response field while the PR remained draft. Promotion PR #44 used the exact same evidence head and introduced no source/evidence change.

A-0101 permanently closes the old fixed-target unload/return lifecycle proof. Later Phase 3 slices do not repeat that far-travel sequence unless lifecycle semantics change.

## Durable P3.2–P3.4 truth

`BinarySectionVisibility` is the six-direction conservative canonical face topology: exactly 3,072 retained bytes/section, deterministic, and checked against the permanent independent `ReferenceFaceMesh` oracle.

`GreedySectionRectangles` partitions proven topology into deterministic packed 4-byte rectangles with exact no-gap/no-overlap coverage. Raw P3.3 topology rectangles are not themselves the render-equivalence contract.

`CanonicalFaceRenderKeys` admits only exact full source-cell canonical baked faces. Render equality includes direction, render layer, material/sprite/tint/shade/emission/animation identity, source corner order/winding/diagonal, raw UV bits, exact ARGB and packed light.

`RenderMergeCandidates` forms deterministic same-render-equivalence rectangles across the complete dev6-eligible set; P3.3 topology rectangle boundaries do not cap render candidates. Exact eligible/passthrough identity is retained.

`OrdinaryQuadEmissionSafety` proves whether one ordinary four-vertex rectangle can preserve repeated captured color/light/raw atlas UV fields. Ordinary atlas-UV reset is the dominant blocker; repeat-aware representation is required for observed multi-face candidates.

`RepeatAwareUvDescriptors` proves an exact two-U by two-V source atlas rectangle and affine geometric-corner-to-UV orientation. Repetition is candidate-local then remapped into the exact source atlas rectangle; full-atlas sampler wrapping is not the correctness model.

`RepeatAwareTransportProof` freezes candidate-local repeat/remap, positive outer-edge endpoint policy, explicit gradients from **unwrapped** repeat coordinates, source baked vertex order/diagonal preservation, and the requirement to bind the same live blocks-atlas view/sampler under the same resource epoch.

`RepeatAwareGreedyMesh` is the proven dev11 hybrid geometry path. Only exact dev10 transport-safe canonical groups are replaced by one merged quad. Unsupported, generalized, ambiguous, noncanonical, color/light-unsafe, or otherwise inadmissible geometry remains exact `BakedSectionMesh` passthrough/oracle geometry.

Dev11 uses public Blaze3D graphics, a 60-byte merged vertex format, namespaced repeat-aware shaders, `textureGrad` from unwrapped repeat coordinates, and four fixed indexed-indirect classes: passthrough/merged × SOLID/CUTOUT. D-0023/D-0025 remain intact; no native Vulkan graphics seam was added.

## P3.4 dev11 closure — COMPLETE

Canonical cleaned runtime package from A-0139:

- `Obsidian-0.3.0-phase3-dev11.jar`
- size **399,361 bytes**
- SHA-256 **`89520af731dbfb48c35071de809d75db1f0c98cdd289e123a9c77f2bacc46418`**
- cleaned source/package head `fe8bf06b1bdb1dbdcc4169ab720fc20be23a5af1`
- package workflow `33216879856`: build SUCCESS, artifact upload SUCCESS, release SKIPPED

A-0140 records reference runtime SUCCESS and the required explicit human visual PASS. Exact evidence head `66c38250426cd6d35629fda088ade768420dee0f` passed workflow `33218461794`: Java 25 / Gradle 9.5.1 build SUCCESS, artifact upload SUCCESS, release SKIPPED.

Reference dev11 runtime:

- all gates through `repeatAwareGreedyEmissionEvidenceReady=true` were true;
- `productionWorkerIntegrationReady=true`, `hardFailure=false`;
- render-thread capture/GPU ownership true; worker live-world reads after capture `0`;
- `repeatAwareGreedyMeshIntegrated=true`, `repeatAwareGreedyGpuEmission=true`;
- workers submitted/started/completed `284/284/284`, steals `208`, queue-full rejection/failure/shutdown-join failure `0/0/0`;
- worker determinism `6/6`;
- visibility: `284` builds / `123,047` faces, retained `872,448 = 284*3,072`, determinism/reference `6/6`;
- topology rectangles: `50,836` covering all `123,047` faces, `72,211` saved = `58.6%`, exact coverage/determinism/reference;
- render-key unmapped faces `0`;
- dev10 transport source multi-face/representable/four-vertex-safe `6,565 / 6,565 / 6,541`;
- transport records `6,541`, unsafe `24`, covered faces `15,800`, faces saved `9,259`;
- explicit-gradient / outer-edge / same-atlas-sampler / raster-review obligations all `6,541`;
- transport internal S/T/both/union `2,705 / 4,071 / 235 / 6,541`;
- transport proof audits `284/284`, determinism `6/6`;
- validated dev11 installed records `261`;
- draw submissions `43,044`;
- actual/expected indirect calls `172,176 / 172,176 = 43,044 * 4`;
- resource-epoch checks `43,044`;
- `repeatAwareGreedyInstallValidationPassed=true`;
- `repeatAwareGreedyFixedFourClassDrawContract=true`;
- scene workers submitted/completed `284/284`, installs `261`, stale discards/preinstall invalidations `23/23` with unsafe stale installs `0`;
- READY transitions `29`, rebuilds `28`, camera recenters `11`, resource reloads `1`;
- dropped lifecycle events `0`;
- workers/staging/arena/resources clean;
- staging submitted/reclaimed `28,434,232 / 28,434,232`;
- arena allocations/retired/reclaimed `783/783/783`, used bytes `0`;
- resources retired/released `261/261`, pending `0`;
- Prism exit code `0`.

Human visual gate:

- explicit user verdict: **PASS — “Everything looked visually fine.”**
- no reported texture stretching, atlas bleed, repeat/mip shimmer, seams/cracks, winding/culling error, color/light mismatch, double draw/z-fighting, or holes.

The historical dev10 diagnostic `repeatAwareTransportBoundaryRasterObligationOpen=true` remains a record of the no-emission proof's original raster obligation. Dev11's real geometry plus explicit visual PASS closes the dev11 canary promotion gate. It does **not** claim the broader P3.6 T-junction policy complete.

`renderCorrectMergeKeyComplete=false` remains an implementation diagnostic in the merged dev11 runtime. P3.4 roadmap completion means the frozen dev6-dev11 render-correct merge/emission chain passed its required proof and canary validation; it does not mean every future render class is greedily mergeable. Any semantic change to that diagnostic must be frozen separately.

A-0141 records P3.4 promotion and P3.5 activation.

## ACTIVE: P3.5 — border/halo correctness

Canonical roadmap objective:

> Validate face visibility, light/AO and rebuild invalidation across section boundaries with no worker-thread live-world reads.

Before implementation, freeze a P3.5 correctness/runtime contract against current source truth. Inspect exact current snapshot/halo capture, visibility, supported light/AO capture, cross-section dirty propagation, generation identity, worker inputs, and dev11 hybrid emission ownership.

### P3.5 non-negotiable boundary

- Cross-section face visibility must be proven from immutable renderer-owned captured data.
- Supported light/AO semantics at section borders must remain exact for the supported geometry class.
- Neighbor/halo changes that affect a section must trigger correct generation-safe rebuild/invalidation.
- Worker live-world reads after capture remain exactly zero.
- Stale worker outputs may never install after a relevant border/halo generation change.
- The independent reference/oracle path remains authoritative for differential correctness.
- Existing dev11 render-correct greedy eligibility may only remain equal or narrow when border proof is insufficient; never widen correctness assumptions silently.
- Unsupported/generalized/ambiguous/unsafe geometry remains exact fallback.
- Render-thread capture/GPU ownership remains unchanged.
- Scheduler, staging, arena, and resource lifetime remain bounded and completion-gated.
- A-0101's old far-travel lifecycle test remains closed unless P3.5 explicitly changes lifecycle semantics.
- Do not consume P3.6's broader T-junction policy during P3.5 absent separately recorded concrete evidence.

P3.5 implementation/version naming should be frozen in the first P3.5 attempt rather than guessed from chat history.

## Promotion authorization

Standing merge authorization applies to the Phase 3 chain after each frozen slice's exact gates pass. Internal commits/merges use `[no-release]`.

Any P3.5 geometry-changing behavior beyond the already-validated dev11 emission semantics requires a fresh explicit human visual validation gate. Pure correctness sidecars/invalidation proof that do not alter emitted geometry may use automated/runtime proof without inventing a visual requirement.

## Continuity order

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning. Attempts are immutable.

## Reference machine

- Windows 11
- Prism Launcher 10.0.5
- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.158.0+26.2
- Java 25.0.1
- AMD Radeon RX 6800 XT 16 GB
- Ryzen 5 5600X
- 16 GB DDR4-2666
- Vulkan backend

## Relevant durable decisions

D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle + complete render-equivalence greedy key + targeted T-junction mitigation, D-0025 narrow native compute/storage seam, D-0026 explicit compute-write/indirect-read synchronization, and D-0027 public fixed-count indirect baseline remain active.
