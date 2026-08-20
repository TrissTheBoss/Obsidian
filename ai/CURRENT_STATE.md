# Obsidian Current State

Last updated: 2026-08-21

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Phase 1: **COMPLETE / runtime validated through dev9**
- Phase 1 closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
- Phase 2 P2.1: **COMPLETE / runtime validated and merged**
- P2.1 closing merge: `a714e19ce871bf73136d52f85a1780109aa851dd`
- Active branch: `phase2/drawable-real-section`
- Active PR: #14, `Phase 2 dev2: first drawable real section`
- Current version: `0.2.0-phase2-dev2`
- Active roadmap item: **P2.2 - first drawable real section**
- P2.2 status: **RUNTIME + HUMAN VISUAL VALIDATED on the reference RX 6800 XT; final exact-head CI and merge pending**

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Roles:

- `CURRENT_STATE.md` = current truth and immediate next action;
- `MASTER_ROADMAP.md` = canonical long-range product/phase/feature plan and roadmap governance;
- `OPERATING_MANUAL.md` = engineering and handoff procedure;
- `DECISIONS.md` = durable architectural policy;
- attempts = immutable evidence/history;
- source, CI and runtime logs = actual implementation/proof.

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

## Proven Phase 1 foundation

Phase 1 established:

`bounded persistent staging -> generation-safe device arena -> fixed frame graph -> narrow native compute/storage seam -> GPU scene visibility -> atomic indirect compaction + visible count -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`

This remains the infrastructure foundation for real terrain.

## P2.1 closing truth

Evidence: `ai/attempts/A-0058-phase2-dev1-runtime-success.md`.

P2.1 permanently established:

- immutable real 16^3 section snapshot;
- one-block halo / 18^3 = 5832 sampled cells;
- primitive-only post-capture data;
- conservative supported-full-cube subset;
- deterministic canonical `ReferenceFaceMesh`;
- no post-snapshot live-world reads during meshing;
- bounded staging / generation-safe arena / completion-gated lifetime validation;
- vanilla terrain remains active.

`ReferenceFaceMesh` is permanent and must remain independent of the future optimized/greedy mesher.

## P2.2 / dev2 implementation truth

Planning/API/package evidence:

- `ai/attempts/A-0059-phase2-dev2-drawable-section-plan.md`
- `ai/attempts/A-0060-phase2-dev2-render-api-inspection.md`
- `ai/attempts/A-0061-phase2-dev2-implementation-and-package.md`
- `ai/attempts/A-0062-phase2-dev2-runtime-inconclusive-visual.md`
- `ai/attempts/A-0063-phase2-dev2-visual-window-fix.md`
- `ai/attempts/A-0064-roadmap-canonical-restoration.md`
- `ai/attempts/A-0066-phase2-dev2-runtime-success.md`

P2.2 adds a drawable representation without replacing the oracle:

- `DrawableSectionMesh` consumes only `SectionSnapshot` + `ReferenceFaceMesh`;
- one reference face -> one drawable quad;
- 4 vertices + 6 indices per face;
- section-local positions;
- 32-bit indices;
- orientation/debug RGBA colors only;
- duplicate drawable builds must be deterministic;
- exact coverage/state/index validation remains explicit.

Live draw path:

- hook: immediately after vanilla `LevelRenderer.render(...)` inside `GameRenderer.renderLevel`, before HUD projection/depth reset;
- transform: `viewRotation * translate(sectionOrigin - cameraPosition)`;
- public Blaze3D graphics only (`nativeGraphicsSeam=false`);
- Minecraft `core/position_color` shader contract;
- live main color + depth target;
- reversed-depth comparison semantics;
- indexed-indirect drawing with `IndexType.INT`;
- vanilla terrain remains active as visual oracle;
- arena/indirect resources retire only behind real GPU completion.

Validation-only capacities remain bounded at 4 MiB staging + 4 MiB device arena. They are not production memory-budget decisions.

## P2.2 runtime result - SUCCESS

Evidence: `ai/attempts/A-0066-phase2-dev2-runtime-success.md`.

The corrected reference-machine run passed the missing human-visible gate. The tester explicitly reported that the orientation-colored Obsidian geometry appeared and was **perfectly aligned** with the corresponding vanilla terrain.

Logged final-run invariants include:

- corrected harness delayed comparison until world entry was observable;
- all 6 sustained comparison passes completed;
- final sample: 5832 cells, interior 4014 air + 82 supported + 0 unsupported = 4096;
- reference faces=139;
- drawable faces=139;
- drawable vertices=556 = faces * 4;
- drawable indices=834 = faces * 6;
- deterministic reference builds=2;
- deterministic drawable builds=2;
- `worldReadsAfterSnapshot=0`;
- `pipelineValid=true`;
- `nativeGraphicsSeam=false`;
- indexed-indirect graphics executed;
- `profilerOnlySubmissions=0`;
- staging submitted/reclaimed=59256/59256 bytes;
- arena allocations/retired/reclaimed=12/12/12 across six sequential passes;
- final arena used=0, one 4194304-byte free span, fragmentation=0;
- indirect resources retired/released=6/6;
- all pending upload/arena/resource retirement counts=0;
- normal shutdown and process exit code 0.

Terrain-dependent coordinates, fingerprints and counts are not hard-coded success values.

## Deliberate P2.2 boundary

Dev2 does **not** claim:

- correct Minecraft material/sprite/texture/UV/tint/render-layer semantics - P2.3;
- block/sky light or ambient occlusion - P2.4;
- broad cutout/model semantics - P2.5+;
- production greedy meshing - Phase 3;
- global vanilla terrain replacement;
- production-scale visibility/performance - Phase 4+.

## Immediate next action

1. Run exact-head Java 25 / Gradle 9.5.1 CI including A-0066 and this status synchronization.
2. If green, promote/merge PR #14 with `[no-release]` and record the resulting main merge SHA.
3. Perform Class-A post-merge synchronization: P2.2 -> COMPLETE; P2.3 becomes the next milestone.
4. Start P2.3 by inspecting and implementing exact Minecraft 26.2 material/sprite/UV/tint/render-layer identity for the supported terrain subset.

## Relevant durable decisions

D-0014 through D-0027 remain active. Especially relevant:

- D-0016 completion-gated lifetime;
- D-0017 bounded/backpressured staging;
- D-0020 generation-safe arena identity;
- D-0023 public Blaze3D graphics first;
- D-0024 permanent reference oracle before production binary/bitmask greedy meshing;
- D-0025 narrow native Vulkan seam only for missing compute/storage capability;
- D-0027 public indexed-indirect graphics baseline unless profiling later justifies native indirect-count integration.
