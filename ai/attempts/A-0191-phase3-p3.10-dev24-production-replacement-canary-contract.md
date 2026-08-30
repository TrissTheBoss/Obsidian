# A-0191 — Phase 3 P3.10 dev24 production replacement canary contract

Date: 2026-08-30
Status: **PLAN FROZEN**
Parent: A-0189
Stage 0 authority: A-0190
Target: `0.3.0-phase3-dev24`

## Purpose

Implement the first real SOLID/CUTOUT production terrain replacement canary at the exact Minecraft 26.2 public-Blaze3D seam proven by A-0190.

This contract exists because Stage 0 source review found two dev11 comparison-only presentation transforms that must not enter production replacement:

- `BakedSectionMesh.COMPARISON_FACE_OFFSET = 1/512` applied outward to comparison faces;
- comparison RGB multiplied by `3/4` so comparison geometry could be visually distinguished from vanilla.

Those transforms were valid only while vanilla geometry remained underneath. A true replacement must use the exact frozen source positions and exact frozen ARGB while retaining all proven merge/material/UV/light identities.

## Frozen dev24 changes

### 1. Production-coordinate greedy emission

`RepeatAwareGreedyMesh` remains the same bounded worker-built identity set:

- same passthrough source-quad ordering;
- same dev10 transport-safe merged candidates;
- same SOLID/CUTOUT partition;
- same indices/winding;
- same repeat-aware UV descriptor/explicit-gradient transport;
- same packed light;
- same material/sprite identity.

Only the two comparison-only presentation transforms are removed:

- passthrough positions are emitted from exact `SectionBakedQuadSnapshot.position(...)` with no normal offset;
- merged canonical planes use exact integer face planes with no +/- comparison offset;
- emitted RGBA is the exact captured ARGB channel value, with no 3/4 RGB multiplier.

No source face becomes newly mergeable. P3.7 coverage/material/direction/geometry/UV/color/light oracle rules are unchanged.

### 2. No post-world comparison draw in production mode

The worker install path still allocates/uploads the exact full-section GPU buffers and indirect commands, but does not draw them as a comparison copy after `LevelRenderer.render`.

The install path must still capture camera-relative transform evidence without requiring a comparison draw, so inherited P3.6 transform proof remains available.

A LIVE record is drawn only when claimed through the P3.10 production replacement plan.

### 3. Fixed-capacity replacement plan

A render-thread-owned plan retains at most `sceneRecordCapacity * 2` claims (one SOLID and one CUTOUT claim per managed section). It stores only primitive identity plus existing record references; it does not retain Minecraft world/chunk objects.

At `LevelRenderer.prepareChunkRenders`:

- reset/validate previous-frame plan completion;
- observe each exact `RenderSection` + `ChunkSectionLayer` at `SectionMesh.getSectionDraw(layer)`;
- only SOLID/CUTOUT may be claimed;
- claim only if the matching Obsidian record is LIVE, scene-generation matched, resource-epoch matched, differential-exact, GPU-installed, and has non-empty output for that layer;
- a successful claim returns `null` for that exact vanilla `SectionDraw`, suppressing only that unit;
- failed/ambiguous claims leave the original vanilla draw unchanged.

Duplicate claim, plan overflow, or stale plan accounting is a hard canary failure.

### 4. Same OPAQUE RenderPass replacement

Immediately before the normal-path `RenderPass.close()` inside `ChunkSectionsToRender.renderGroup(...)`:

- act only for `ChunkSectionLayerGroup.OPAQUE`;
- encode every reserved SOLID/CUTOUT Obsidian claim into the already-active Minecraft OPAQUE `RenderPass`;
- use Obsidian's passthrough/repeat-aware pipelines and existing GPU arena slices/indirect commands;
- bind the same live blocks atlas and lightmap surfaces used by the proven path;
- no new graphics submission and no native Vulkan graphics seam;
- mark each claim executed exactly once.

Because suppression and execution occur on the same render thread within one `LevelRenderer.render`/frame-graph build+execution interval, lifecycle invalidation is only drained at the next Obsidian frame boundary. Any unexpected generation/resource mismatch at execution is a hard correctness failure rather than a silent hole.

### 5. Telemetry

Bounded counters must expose at final shutdown at least:

- prepare calls;
- supported vanilla draw candidates;
- vanilla fallbacks;
- SOLID suppressions;
- CUTOUT suppressions;
- replacement executions by layer;
- duplicate claims;
- claim overflow;
- stale-plan/nonexecuted claims;
- execution-without-claim;
- execution revalidation failures;
- exact suppression==execution accounting;
- production-coordinate flag true;
- exact-color flag true;
- post-world comparison draw disabled true;
- same-OPAQUE-pass flag true;
- native graphics expansion false.

## Runtime canary gates

Before P3.10 canary success:

- >=1 SOLID suppression and matching replacement execution;
- >=1 CUTOUT suppression and matching replacement execution;
- suppression/execution accounting exact;
- no duplicate/overflow/stale/unclaimed/revalidation failures;
- at least one vanilla fallback while the managed record is unavailable/not-LIVE;
- ordinary edit -> rebuild -> replacement recovery;
- >=1 real scene recenter -> replacement recovery;
- F3+T -> vanilla fallback during invalidation -> replacement recovery;
- permanent P3.7 missing/duplicate/optimized-without-reference/real mismatch all zero;
- worker world reads after capture 0;
- synchronous scene mesh builds 0;
- unsafe stale installs 0;
- queue/staging/arena/deferred lifetime clean;
- normal exit 0.

## Human visual gate

Explicit visual PASS is required. Inspect opaque terrain and cutout vegetation, boundaries, camera motion, edits, reload and recenter. There must be no:

- holes or missing terrain;
- duplicate/z-fighting terrain;
- 1/512 shifted faces;
- 75% dim comparison tint;
- UV-repeat regression;
- tint/light/AO regression;
- cracks/pinholes;
- cutout alpha regression;
- depth-order regression or stale popping.

## Failure policy

Do not broaden eligibility or disable fallback to rescue the canary. Any real hole, duplicate, stale suppression, incorrect production coordinates/color, P3.7 mismatch or visual regression blocks promotion and requires a separately recorded correction.
