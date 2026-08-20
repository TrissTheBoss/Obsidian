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
- Phase 2 P2.2: **COMPLETE / runtime + human visual validated and merged**
- P2.2 closing merge: `f9c64267c5becb3bd80897efdb09ed65a6ce8697`
- Phase 2 P2.3: **COMPLETE / runtime + human visual validated and merged**
- P2.3 closing merge: `667230f51222746083efe89c72265d80ac9d3929`
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Active milestone: **P2.4 - block/sky lighting and ambient occlusion correctness**
- Active branch: `phase2/lighting-ao-correctness`
- Current development version: `0.2.0-phase2-dev4`
- P2.4 status: **ACTIVE / exact Minecraft 26.2 lighting-AO API grounding in progress**

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

## Proven foundations

Phase 1 established:

`bounded persistent staging -> generation-safe device arena -> fixed frame graph -> narrow native compute/storage seam -> GPU scene visibility -> atomic indirect compaction + visible count -> public Blaze3D indexed-indirect graphics -> deterministic readback -> completion-gated reclamation`

P2.1 permanently established immutable real-section + one-block halo capture and the deterministic `ReferenceFaceMesh` geometry oracle with no post-snapshot live-world reads during meshing.

P2.2 established deterministic real indexed geometry, exact section/camera placement and public depth-tested indexed-indirect world drawing.

P2.3 established exact Minecraft 26.2 baked sprite/UV/tint/material identity for the conservative supported SOLID full-cube path through immutable `SectionMaterialSnapshot` + pure `MaterializedSectionMesh`. Reference RX 6800 XT validation reported no visible texture/UV/alignment/tint issues. Runtime evidence: `ai/attempts/A-0070-phase2-dev3-runtime-success.md`.

The permanent P2.1 reference oracle remains independent of all later optimized/materialized representations.

## P2.4 / dev4 - ACTIVE

Planning: `ai/attempts/A-0071-phase2-dev4-lighting-ao-plan.md`.

### Objective

Add correct block light, sky light, directional shade and ambient-occlusion semantics to the already validated P2.3 textured faces without broadening P2.5 model/material scope or pulling P2.6 lifecycle work forward.

P2.4 must leave behind a deterministic immutable lighting/AO reference representation suitable for later Phase 3 differential testing.

### Required exact Minecraft 26.2 grounding

Before implementation, inspect the Loom-resolved client API/bytecode for:

- block/sky light lookup and packed light coordinates;
- terrain vertex format and lightmap bindings;
- emission adjustment;
- `ModelBlockRenderer` flat and smooth/AO paths;
- AO neighbor sampling, shade brightness and occlusion rules;
- exact per-corner ordering/weights;
- directional shade and baked `shade` semantics;
- AO diagonal/triangle choice if present;
- whether the existing 18^3 one-block-halo snapshot contains every sample needed at section borders.

Do not rely on remembered pre-26.2 rendering APIs.

### Architectural boundary

P2.4 must preserve:

- immutable snapshot/reference/material contracts;
- render-thread-only live world/light capture;
- zero live world/model/light-engine reads during pure drawable construction after lighting capture;
- existing section-local geometry, UVs and validated camera/world transform;
- public Blaze3D graphics first;
- bounded staging/arena resources and completion-gated reclamation;
- explicit unsupported cases rather than silent approximation.

A new immutable `SectionLightingSnapshot` is the expected direction unless exact 26.2 inspection proves a better narrow representation.

### Halo rule

The current section snapshot covers local coordinates `-1..16` on each axis (18^3). P2.4 must prove this one-block halo is sufficient for all vanilla-intent light/AO samples of the supported full-cube faces. If exact sampling reaches farther, expand the capture contract deliberately; never compensate with post-capture live-world reads.

### Runtime gate

Reference RX 6800 XT validation must prove, at minimum:

- exact `0.2.0-phase2-dev4` on Vulkan;
- deterministic reference/material/lighting/drawable construction;
- exact supported-face accounting;
- deterministic four-corner block/sky light and AO/shade identity;
- border correctness without post-capture live reads;
- public textured/lightmapped indexed-indirect drawing;
- `nativeGraphicsSeam=false`;
- visual light gradients, directional shading and AO corner pattern agree with vanilla for the supported sample, aside from any explicitly documented uniform comparison modulation;
- geometry/UV alignment remains correct while moving;
- all sustained visual passes complete;
- `profilerOnlySubmissions=0`;
- staging/arena/indirect resources fully reclaim;
- process exits 0.

World-dependent values and fingerprints must not be hard-coded.

## P2.3 block-edit observation retained for P2.6

The dev3 validation overlay could remain stale for part of one bounded comparison pass after a block edit. The log proved the next recapture incorporated the edit. This is acceptable for the probe but is not the production latency target.

Event-driven block/light update invalidation, neighbor invalidation and rebuild scheduling remain P2.6 scope. Do not add ad-hoc P2.4 lifecycle logic merely to shorten the validation overlay interval.

## Deliberate P2.4 boundary

P2.4 does **not** claim:

- broad CUTOUT/non-full/custom-model support - P2.5+;
- event-driven section/block/light update lifecycle - P2.6;
- production greedy meshing - Phase 3;
- global vanilla terrain replacement;
- production-scale visibility/performance - Phase 4+.

## Immediate next action

1. Run exact Minecraft 26.2 hosted API/bytecode inspection for lighting, AO, terrain vertex format and lightmap binding.
2. Record the result immutably as the next attempt.
3. Implement the narrow immutable lighting/AO reference/capture path only after those contracts are grounded.
4. Keep the dev4 PR draft until exact CI and reference RX 6800 XT runtime + human visual validation pass.

## Relevant durable decisions

D-0014 through D-0027 remain active. Especially relevant:

- D-0016 completion-gated lifetime;
- D-0017 bounded/backpressured staging;
- D-0020 generation-safe arena identity;
- D-0023 public Blaze3D graphics first;
- D-0024 permanent reference oracle before production binary/bitmask greedy meshing;
- D-0025 narrow native Vulkan seam only for missing compute/storage capability;
- D-0027 public indexed-indirect graphics baseline unless profiling later justifies native indirect-count integration.
