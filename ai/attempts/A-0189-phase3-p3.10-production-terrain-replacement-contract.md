# A-0189 — Phase 3 P3.10 production opaque/cutout terrain replacement contract

Date: 2026-08-30
Status: **PLAN FROZEN**
Target version family: `0.3.0-phase3-dev24+`
Base: synchronized P3.8-complete `main` at `169274b468d2a278d39043938efff19844bec9ba`
Predecessor evidence: P3.1-P3.8 COMPLETE; A-0188 formally REJECTED/DEFERRED the experimental fixed four-slice P3.9 strategy.

## Objective

Make Obsidian perform the first real production replacement of Minecraft 26.2's supported opaque/cutout terrain draw path using the already-proven full-section asynchronous repeat-aware greedy output.

A successful P3.10 canary must prove **replacement**, not comparison rendering: when an exact vanilla terrain draw is suppressed, a generation/resource-epoch-matched Obsidian draw must render the same supported section/layer at the correct world-render stage. A second copy drawn after vanilla does not satisfy this contract.

P3.9 partial remeshing and partial GPU patching are not prerequisites and remain out of scope.

## Non-negotiable architecture

1. Minecraft retains Vulkan device, graphics submission and presentation ownership unless exact evidence requires a separately frozen change.
2. Prefer the public Blaze3D graphics path. Do not expand native Vulkan graphics ownership in this slice.
3. Render-thread capture and GPU ownership remain authoritative. Workers never read live world state after capture.
4. The permanent P3.7 independent reference/differential oracle remains unchanged and must stay green.
5. Existing full-section generation-safe invalidation, worker build, bounded staging, device-arena allocation, completion-gated reclamation and stale-result rejection remain authoritative.
6. No routine `vkDeviceWaitIdle`, no frame-count lifetime guesses and no unbounded queue/staging fallback.
7. Unsupported or unproven terrain must remain on Minecraft's path. Fail-open means **keep vanilla**, never render a hole.

## Stage 0 — exact Minecraft 26.2 render-seam proof (mandatory before renderer source changes)

Inspect the exact resolved Minecraft 26.2 client bytecode used by the build and identify:

- the concrete method(s) that submit SOLID terrain;
- the concrete method(s) that submit CUTOUT terrain;
- the data structure containing the exact visible/renderable section draw set;
- where section/layer identity is still available;
- ordering relative to depth consumers, entities/block entities, translucent terrain, particles/weather and post-world work;
- whether a public-Blaze3D interception can suppress an individual section/layer draw or whether the narrowest safe seam is a whole-layer/list operation;
- the exact resource/atlas/lightmap state required at that point.

No production source may be changed until this call-shape/seam result is recorded immutably. Do not infer the seam from older Minecraft versions or mappings alone.

## First replacement canary scope

The first P3.10 implementation may replace only Obsidian-managed sections for which all of the following are true at draw time:

- scene record is LIVE;
- record generation equals current scene generation;
- resource epoch/atlas identity is current;
- production worker result was installed through the proven generation-safe path;
- the section has complete Obsidian SOLID/CUTOUT output for the class being suppressed, including exact passthrough geometry plus admitted repeat-aware greedy replacements;
- the corresponding P3.7 differential result for the installed record is exact;
- the vanilla draw being suppressed can be identified exactly enough to prove one-for-one replacement accounting.

If any requirement is unavailable or ambiguous, vanilla rendering remains enabled for that draw.

## Draw-stage semantics

Obsidian replacement terrain must execute at the same semantic stage as the vanilla class it replaces, before later rendering that depends on opaque/cutout depth. The existing `GameRendererMixin` post-`LevelRenderer.render` comparison hook is **not** a valid production replacement location.

SOLID and CUTOUT keep their intended depth/cull/blend/atlas/lightmap semantics. No translucent/fluid, entity, block-entity, particle/weather, sky/cloud or UI replacement is allowed in this slice.

## Replacement identity / no-double-draw contract

For each suppressed vanilla terrain unit, retain bounded primitive evidence identifying at least:

- frame serial;
- section XYZ;
- render class (SOLID or CUTOUT);
- scene generation;
- installed record generation;
- resource epoch;
- whether replacement submission occurred;
- replacement draw/index counts sufficient to prove non-empty output when vanilla work was non-empty.

Required accounting:

- `vanillaTerrainSuppressions == obsidianReplacementClaims` for accepted replacement units;
- zero replacement claims without a matching suppression;
- zero suppressions without a matching valid Obsidian record;
- zero duplicate suppression of the same section/layer in one frame;
- zero Obsidian production comparison draw for a section/layer already counted as replacement;
- fallback-to-vanilla events are counted separately and may be nonzero.

Collectors must be fixed-capacity/bounded primitive state. Overflow is a gate failure, not permission to guess.

## Correctness gates

A runtime canary may be called successful only if:

- at least one real SOLID terrain suppression/replacement is observed;
- at least one real CUTOUT terrain suppression/replacement is observed before P3.10 opaque/cutout is considered closed; if the first canary scene lacks CUTOUT, that run may close only the SOLID sub-gate;
- suppressed/replaced section/layer accounting is exact and coherent;
- replacement occurs at the proven in-world stage, not after `LevelRenderer.render`;
- P3.7 differential proof remains exact: missing/duplicate/optimized-without-reference/real-mismatch all zero;
- worker world reads after capture = 0;
- synchronous scene mesh builds = 0;
- unsafe stale installs = 0;
- queue-full rejection/failure/join-failure remain zero in the required canary workload;
- staging/arena/deferred resources close cleanly;
- no Mixin application/injection error;
- normal process exit = 0.

## Lifecycle workload gates

The replacement canary must exercise, while replacement is actually active:

1. stable READY rendering;
2. ordinary block edits that rebuild at least one replaced section and return to replacement;
3. at least one real scene recenter and recovery;
4. F3+T resource reload and recovery;
5. at least one interval in which a candidate is intentionally not safe/LIVE and vanilla fallback remains visible rather than disappearing.

Generation/resource transitions must never suppress vanilla based on stale Obsidian ownership.

## Visual gate

P3.10 changes production rendering semantics, so an explicit human visual verdict is mandatory on the reference Vulkan path.

Inspect at minimum:

- opaque terrain surfaces;
- leaves/grass or another CUTOUT fixture;
- chunk/section boundaries;
- camera motion and rapid turns;
- edits/rebuild transition;
- resource reload recovery;
- recenter/traversal recovery;
- absence of double-rendering/z-fighting, holes, popping, wrong UV repeat, tint/light/AO regression, cracks or depth-order artifacts.

A visual FAIL blocks promotion even when numerical accounting is green.

## Performance policy

This first production-replacement canary is correctness/ownership-first. Do not claim average-FPS uplift from a tiny managed scene. However, the implementation must not introduce render-thread synchronous mesh construction, unbounded draw loops, new per-frame object graphs or blocking GPU waits.

Record replacement draw counts and CPU frame telemetry for regression diagnosis, but no post-hoc performance threshold will be invented for this canary.

## Promotion / failure rules

- **SUCCESS / SOLID sub-gate:** exact SOLID replacement proven but CUTOUT not yet exercised; continue P3.10 on the same architecture to CUTOUT.
- **SUCCESS / P3.10 canary:** exact SOLID + CUTOUT replacement, lifecycle/reload/recenter/fallback safety, P3.7 exactness, clean lifetime and explicit visual PASS.
- **SEAM BLOCKED:** exact Minecraft 26.2 bytecode shows the currently intended public seam cannot suppress terrain safely at section/layer granularity. Freeze a narrower architecture decision before expanding graphics interop; do not hack around ordering.
- **CORRECTNESS FAIL:** any hole, double draw, stale suppression, oracle mismatch, resource-epoch mismatch or visual regression blocks replacement and must be fixed before scale work.

Do not weaken P3.7, broaden eligibility, silently skip unsupported source geometry, or disable vanilla fallback to make the gate pass.

## Out of scope

- P3.9 slice partial remeshing or partial GPU patching;
- translucent terrain / fluids;
- entities / block entities;
- particles / weather;
- Phase 4 large-scale GPU visibility policy;
- Hi-Z/occlusion redesign;
- shader-pack compatibility;
- vendor-specific fast paths.

## Expected next action

Run Stage 0 exact Minecraft 26.2 bytecode/call-shape inspection and record the exact terrain draw seam. Only after that result authorizes a safe public-Blaze3D replacement location may source implementation for dev24 begin.
