# Obsidian Current State

Last updated: 2026-09-02

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Synchronized Phase 3 merge: `01547b55f68690a5d0aac8405fc0fe91cdf440f9`
- Active branch: `phase4/p4.1-persistent-scene-visibility`
- Active draft PR: #57 `Phase 4 P4.1: persistent large-scene GPU visibility`
- Product phase: **Phase 4 — GPU-driven visibility at real-world scale**.

## Phase 3 status — COMPLETE

- P3.1-P3.8: COMPLETE.
- P3.9 fixed four-Y-slice partial remeshing: **REJECTED / DEFERRED** by A-0188 (`807` permille projected-upload P95 vs frozen `<=800`). Do not retune or revive the same experiment as baseline.
- P3.10 production opaque/cutout replacement: **COMPLETE**.

P3.10 final continuity:

- A-0199 — dev24.2 reference runtime closed leaves/kelp visibility and same-column vertical-scene tracking; production/P3.7/lifetime accounting clean; F3+T still pending.
- A-0200 — exact same dev24.2 JAR passed a real post-startup F3+T automated reload/rebuild/replacement cycle with `resourceReloadEvents=2` and clean accounting/lifetime.
- A-0201 — explicit human post-F3+T **visual PASS**; final frozen runtime + visual contract closed.
- final evidence head `f29c0adceb99b572f9d4066342ffdc034ec1e81e` passed hosted Build #736.
- connector draft-to-ready mutation for PR #55 failed internally on an unsupported GitHub GraphQL field; no repository/source gate was weakened. PR #55 was closed, replacement non-draft PR #56 used the exact unchanged tested head, promotion Build #739 passed, and PR #56 merged `[no-release]`.
- merge commit `01547b55f68690a5d0aac8405fc0fe91cdf440f9` passed post-merge Build #741 / run `33650990847`; versioned release was intentionally skipped.
- A-0202 records the promotion/tooling transition and Phase 4 branch activation.

### Canonical P3.10 runtime package

Renderer/package source authority remains:

`debe41eb3b6fdc7e975e904ae913f1a0f18ebb28`

Canonical direct runtime JAR:

- `Obsidian-0.3.0-phase3-dev24.2.jar`
- size `466,654` bytes
- SHA-256 `7146efd6be8faf5f926eee094a65a149a6187764631abbe4fb8926f2dedbdba4`

Do not treat later continuity/Phase 4 commits as the source authority for that package.

## Phase 4 P4.1 — ACTIVE / CONTRACT FROZEN

Immutable contract:

`ai/attempts/A-0203-phase4-p4.1-persistent-scene-gpu-visibility-contract.md`

P4.1 is a correctness-first **shadow large-scene visibility** milestone. It deliberately does not change P3.10 production draw ownership yet.

Purpose:

1. maintain a bounded renderer-owned persistent section metadata database at real render-distance scale using incremental lifecycle events rather than per-camera-frame reconstruction;
2. classify real 16x16x16 section AABBs against the authoritative Minecraft world camera/frustum on GPU;
3. compact visible identities/templates and retain a GPU visible count using the proven Phase 1 native-compute/public-graphics ownership model;
4. compare sampled GPU results against an independent conservative CPU oracle;
5. prove scale/correctness/lifetime before a later Phase 4 slice connects GPU-visible production records to the P3.10 same-OPAQUE-pass draw seam.

Inherited constraints:

- D-0004: camera-only frames must not Java-walk/rebuild all loaded sections.
- D-0008: long-term scene path is async CPU mesh + GPU scene + visibility/compaction + indirect rendering.
- D-0025: native Vulkan interop remains narrow; no separate device/graphics takeover.
- D-0026: producer/consumer synchronization is explicit Synchronization2.
- D-0027: public fixed-count indexed indirect + zero unused tail remains the graphics baseline; no native indirect-count graphics expansion without later measured justification.
- P3.10 replacement/fallback semantics, P3.7 oracle, zero worker live-world reads, zero synchronous scene builds and completion-gated lifetime remain mandatory.

P4.1 explicit non-goals: production GPU-driven terrain draw ownership, native graphics expansion, indirect-count consumption, Hi-Z, temporal occlusion, async-compute queue ownership, mesh shaders, LOD, translucency/fluids, entities/block entities/particles, mesher changes and P3.9 partial remeshing.

## Proven Phase 1 visibility foundation being reused

A-0048/A-0049/A-0050/A-0052 proved the small-scale architecture on the RX 6800 XT:

`GPU scene records -> compute visibility -> atomic front compaction -> GPU visible count -> zero unused indirect tail -> public Blaze3D fixed-count indexed-indirect -> deterministic readback`

The existing `VulkanVisibilityCompactor` is deliberately fixed to four validation candidates and one workgroup. P4.1 must generalize its ownership/synchronization model rather than simply increasing constants. In particular, the dev9 workgroup-local reset/barrier cannot serve as a global N-candidate reset mechanism.

## Current exact API-inspection step

A-0203 requires exact Minecraft 26.2 grounding before renderer-source implementation.

Already-known exact evidence from prior attempts:

- A-0054: `ClientLevel.getChunkSource()`, non-loading `ClientChunkCache.getChunk(..., ChunkStatus.FULL, false)`, `ChunkAccess.getSections()`, `LevelHeightAccessor.getMinSectionY/getMaxSectionY/...` and exact section access.
- A-0060: authoritative world `CameraRenderState` exposes `pos`, `projectionMatrix`, `viewRotationMatrix`; `GameRenderer.gameRenderState().levelRenderState.cameraRenderState` supplies the live world camera state.

Temporary workflow `Phase 4 P4.1 API Inspect` was added only to close remaining exact seams: active view distance, loaded chunk/section enumeration suitable for incremental population, Frustum API/plane behavior and relevant LevelRenderer/GameRenderer bytecode.

Current inspection workflow:

- branch head containing temporary workflow: `5e3b080967b677b02de2adf69b96a4a5c1bba25f`;
- run `33651565582` / #1 — in progress at the time of this state update.

The temporary workflow must be removed after evidence is recorded and before any runtime package handoff.

## Immediate handoff

1. finish run `33651565582` and inspect its artifact;
2. record the exact Minecraft 26.2 large-scene/frustum seam in a new immutable attempt;
3. remove the temporary inspection workflow;
4. only then implement P4.1 persistent metadata + scalable GPU shadow visibility under A-0203;
5. run exact hosted CI and package a dedicated Phase 4 dev build only after the source implementation is statically reviewed against the frozen contract;
6. keep PR #57 DRAFT / DO NOT MERGE until real render-distance-scale runtime correctness, lifecycle, performance-baseline and human visual gates close.
