# Obsidian Current State

Last updated: 2026-08-21

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Current public release: `v0.0.2-phase0`
- Canonical long-range plan: `ai/MASTER_ROADMAP.md`
- Phase 1: **COMPLETE / runtime validated through dev9**
- Phase 1 closing merge: `61df7b8e2abc09ce387e09c9e4d6811a9ef6c40f`
- Phase 2 P2.1: **COMPLETE / runtime validated and merged** — closing merge `a714e19ce871bf73136d52f85a1780109aa851dd`
- Phase 2 P2.2: **COMPLETE / runtime + human visual validated and merged** — closing merge `f9c64267c5becb3bd80897efdb09ed65a6ce8697`
- Phase 2 P2.3: **COMPLETE / runtime + human visual validated and merged** — closing merge `667230f51222746083efe89c72265d80ac9d3929`
- Phase 2 P2.4: **COMPLETE / runtime + human visual validated and merged** — closing merge `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`
- P2.4 Class-A sync merge: `a6d7d2ff96948910e22b2ab4e3e5212408ef97c2`
- Phase 2 P2.5: **COMPLETE / runtime + human visual validated and merged** — closing merge `c17f7c6146678e18cacabc44d85c67413a040f73`
- P2.5 Class-A sync merge: `306d74fdf2428af93feac2ce5e49296d508d9d2d`
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Active milestone: **P2.6 - section lifecycle and rebuild correctness**
- Active branch: `phase2/section-lifecycle-rebuild`
- Current development version: `0.2.0-phase2-dev6`
- P2.6 status: **ACTIVE / exact Minecraft 26.2 lifecycle-event API grounding first**
- Last runtime-validated development version: `0.2.0-phase2-dev5`

## Continuity model

Read in this order before changing architecture or milestone status:

1. `ai/CURRENT_STATE.md`
2. `ai/MASTER_ROADMAP.md`
3. `ai/OPERATING_MANUAL.md`
4. `ai/DECISIONS.md`
5. `ai/ATTEMPT_LOG.md`
6. newest relevant `ai/attempts/`

Source/runtime evidence overrides stale planning text. Attempts remain immutable.

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

## Proven Phase 2 foundation

P2.1 established immutable 16^3 section + one-block-halo capture and the permanent deterministic cube-face `ReferenceFaceMesh` oracle.

P2.2 established deterministic real indexed geometry, exact section/camera placement and public depth-tested indexed-indirect drawing.

P2.3 established exact baked sprite/UV/material/tint identity for the conservative SOLID full-cube subset.

P2.4 established exact Minecraft 26.2 block/sky light, directional shade and ambient-occlusion semantics through immutable capture + exact BLOCK/lightmap drawing.

P2.5 established exact generalized vanilla MODEL semantics for the accepted SOLID/CUTOUT domain through `ModelBlockRenderer.tesselateBlock(...) -> BlockQuadOutput`, immutable arbitrary quad capture and deterministic layered BLOCK-format public indexed-indirect drawing. Runtime evidence: `ai/attempts/A-0079-phase2-dev5-runtime-success.md`.

## P2.5 / dev5 - COMPLETE

Closing merge: `c17f7c6146678e18cacabc44d85c67413a040f73`.
Class-A sync merge: `306d74fdf2428af93feac2ce5e49296d508d9d2d`.
Validated version: `0.2.0-phase2-dev5`.

Reference validation completed six sustained SOLID+CUTOUT passes on the RX 6800 XT with deterministic generalized capture/builds, exact public BLOCK/lightmap pipelines, full completion-gated reclamation and process exit 0. The user reported everything looked fine.

## P2.6 / dev6 - ACTIVE

Plan: `ai/attempts/A-0080-phase2-dev6-section-lifecycle-plan.md`.

Canonical goals:

- section load;
- section unload;
- block update invalidation;
- neighbor-border invalidation;
- resource reload invalidation;
- stale async/result rejection;
- safe replacement of live GPU allocations;
- multiple rebuilds of the same section;
- generation/version identity carried end to end.

Concrete defect being eliminated: P2.3/dev3 proved that a pass-based validation overlay could remain stale briefly after a block break until the next recapture. P2.6 replaces that validation-only behavior with event-driven dirtying, versioned rebuild/install and safe completion-gated replacement/unload.

### Required architecture

- renderer-owned section generation increments on relevant invalidation;
- immutable capture/build/upload/install carries generation identity;
- stale generations are rejected before becoming live;
- block/light changes on a border conservatively invalidate every section whose one-block halo can contain the changed cell;
- live replacement is atomic at the renderer record level;
- old GPU geometry/resource ownership retires only after completion;
- unload/world teardown prevents stale reinstall and retires live ownership;
- model/atlas resource epoch remains part of validity and resource reload forces rebuild;
- the lifecycle identity must later be usable across asynchronous Phase 3 worker boundaries without redesign.

### Exact Minecraft 26.2 grounding required before implementation

Inspect the exact client paths/APIs for client chunk/section load and unload, block-state updates, light notifications, vanilla render-section dirty propagation, resource/model reload, world replacement/disconnect and thread affinity. Prefer exact Fabric events only when they cover the required semantics; otherwise use narrow version-grounded mixins. Do not rely on older-version API memory.

## Immediate next action

1. Open the dev6 draft PR.
2. Mark P2.6 ACTIVE in the roadmap on the dev6 branch.
3. Run hosted exact Minecraft 26.2 lifecycle API/bytecode inspection before choosing event/mixin seams.
4. Record exact findings immutably before implementation.
5. Implement the smallest one-section generation-safe event-driven lifecycle proof.
6. Keep dev6 draft/unmerged until exact CI and required runtime validation pass. Fresh explicit merge authorization is required for dev6.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016 completion-gated reclamation, D-0017 bounded/backpressured staging, D-0020 generation-safe arena identity, D-0023 public Blaze3D graphics first, D-0024 permanent independent reference oracle, D-0025 narrow native seam, and D-0027 public fixed-count indirect baseline.
