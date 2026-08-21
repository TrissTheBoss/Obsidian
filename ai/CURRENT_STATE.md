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
- Phase 2 P2.4: **COMPLETE / runtime + human visual validated and merged**
- P2.4 closing merge: `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`
- Current product phase: **Phase 2 - real-section correctness and renderer semantics**
- Next milestone: **P2.5 - broader opaque/cutout block semantics**
- No P2.5 implementation branch/version has been started at this status-sync commit.

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

P2.1 established immutable 16^3 section + one-block halo capture and the permanent deterministic `ReferenceFaceMesh` geometry oracle.

P2.2 established deterministic real indexed geometry, exact section/camera placement and public depth-tested indexed-indirect world drawing.

P2.3 established exact Minecraft 26.2 baked sprite/UV/tint/material identity for the conservative SOLID full-cube path through immutable renderer-owned capture and a pure materialized mesh.

P2.4 established exact Minecraft 26.2 block/sky light, directional shade and ambient-occlusion corner semantics for that supported subset through `SectionLightingSnapshot` + `LitSectionMesh` and the public BLOCK/lightmap pipeline.

P2.4 final evidence: `ai/attempts/A-0075-phase2-dev4-runtime-success.md`.

The permanent P2.1 reference oracle remains independent of all later optimized/materialized representations.

## P2.4 closure facts

- validated milestone: `0.2.0-phase2-dev4`;
- closing merge: `fa0d40182cd0bc29a526b28a8b2b3b43fc8fc8ba`;
- exact API inspection: A-0072;
- implementation/package: A-0073;
- first machine-clean/coplanar visual ambiguity: A-0074;
- final runtime + human visual success: A-0075;
- six sustained visual passes complete;
- exact vanilla `BlockModelLighter` used as the lighting/AO correctness oracle;
- deterministic reference/material/lighting/drawable duplicates;
- `worldReadsAfterLightingCapture=0`;
- `oneBlockHaloSufficient=true`, including a validated section-border transition;
- `pipelineValid=true`;
- `nativeGraphicsSeam=false`;
- public indexed-indirect drawing with blocks atlas + live level lightmap;
- `profilerOnlySubmissions=0`;
- bounded staging and full completion-gated arena/indirect reclamation;
- process exit code 0;
- human visual gate accepted after a validation-only `1/512` outward face offset removed coplanar depth fighting.

The `1/512` comparison offset is presentation-only. It is not part of the canonical geometry oracle and must not silently become a production geometry rule.

## P2.5 - NEXT

Canonical roadmap scope:

- ordinary full cubes remain supported;
- expand axis-aligned simple model cases;
- add cutout vegetation/model classes where architecture allows;
- preserve tinted blocks and biome-dependent color inputs;
- add selected non-full model cases only after exact semantics are understood;
- keep unsupported cases explicit and measurable.

P2.5 must begin with exact Minecraft 26.2 API/bytecode inspection for render-layer selection, general/unculled quads, multi-quad model parts, non-full baked geometry, cutout pipeline/state requirements, culling semantics, per-quad material identity, tint/light/AO mapping for arbitrary quad positions, and any halo implications beyond the P2.4 full-cube proof.

Do not guess older-version model APIs and do not broaden support by silently approximating unsupported geometry.

## P2.6 retained lifecycle work

The earlier bounded validation probes can remain stale until the next recapture after block/light changes. Event-driven section/block/light dirtying, neighbor invalidation, resource reload invalidation, stale-result rejection and rebuild scheduling remain P2.6 scope.

## Immediate next action

1. Start P2.5/dev5 from this synchronized `main`.
2. Bump the development version to `0.2.0-phase2-dev5`.
3. Record an immutable P2.5 plan.
4. Run exact Minecraft 26.2 hosted API/bytecode inspection before selecting the first broader semantic subset.
5. Keep the dev5 PR draft until CI and reference-runtime validation pass.

## Relevant durable decisions

D-0014 through D-0027 remain active, especially D-0016, D-0017, D-0020, D-0023, D-0024, D-0025 and D-0027.
