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

## Phase 4 P4.1 — ACTIVE / DEV1 REFERENCE RUNTIME REQUIRED

Immutable contract:

`ai/attempts/A-0203-phase4-p4.1-persistent-scene-gpu-visibility-contract.md`

Exact Minecraft 26.2 seam:

`ai/attempts/A-0204-phase4-p4.1-exact-mc26.2-large-scene-frustum-seam.md`

Canonical dev1 package/runtime handoff:

`ai/attempts/A-0205-phase4-p4.1-dev1-ci-package-runtime-handoff.md`

P4.1 is a correctness-first **shadow large-scene visibility** milestone. It deliberately does not change P3.10 production draw ownership yet.

### Exact P4.1 seam now proven

A-0204 closed the required exact Minecraft 26.2 API/bytecode inspection before renderer-source implementation. Important grounded facts include:

- authoritative world camera state is `GameRenderer.gameRenderState().levelRenderState.cameraRenderState`;
- frustum construction uses the live world camera view-rotation and culling projection and is prepared at the exact camera position;
- `ClientChunkCache.getChunk(x,z,ChunkStatus.FULL,false)` provides safe non-loading lookup;
- world min/max section Y and section count are available from the exact level height APIs;
- Minecraft 26.2 exposes double-buffered loaded-chunk and empty-section lifecycle changes used during level extraction;
- section-empty transitions are driven from the real `LevelChunkSection.hasOnlyAir()` state, giving P4.1 an incremental membership seam rather than camera-frame polling.

Temporary API-inspection workflow code was removed before the dev1 runtime package.

## P4.1 dev1 implementation

P4.1 dev1 adds a shadow subsystem beside the promoted P3.10 renderer:

- fixed/bounded primitive persistent section metadata database;
- hard capacity ceiling `2,500,000` section slots;
- stable per-record validation identity and bounded free-slot reuse;
- exact chunk load/unload and section empty/non-empty observation hooks;
- lifecycle event ring with explicit overflow detection and conservative bounded resync;
- bounded initial/full resync of `128` chunk columns per frame;
- changed-scene candidate snapshot construction of at most `16,384` slots per frame;
- camera-relative integer-section AABB transport to avoid huge world-coordinate float precision loss;
- scalable native Vulkan compute visibility classification with workgroup size `128` and arbitrary workgroup count;
- transfer reset -> compute and compute -> transfer/readback Synchronization2 edges;
- atomic compacted visible identity list plus GPU visible count;
- asynchronous zero-timeout normal readback polling;
- independent CPU visibility oracle bounded to `8,192` slots per frame;
- conservative `1e-3` plane epsilon where boundary-ambiguous CPU records remain visible;
- exact sampled identity-set accounting for missing/unexpected/duplicate records and GPU false culls;
- explicit final evidence that camera-only frames do not full-scan the Java scene and that production draw ownership/native graphics ownership did not expand.

A P4.1 shadow failure is isolated from the production terrain renderer. P3.10 remains the sole SOLID/CUTOUT production replacement authority for this milestone.

## Canonical P4.1 dev1 package authority

Version:

`0.4.0-phase4-dev1`

Exact source/package authority:

`fd58b9f2e915462f665b7d85f5d993456d5f930e`

The previous fully integrated source head `8c63c478691605dddc577b572b461e83a1384a8c` passed Build #756. The final package head only adds the dev1 version identity.

Canonical package CI:

- Build run `33653778087` / **#757** — SUCCESS;
- Java 25 / Gradle 9.5.1 build — SUCCESS;
- artifact upload — SUCCESS;
- source branch head `fd58b9f2e915462f665b7d85f5d993456d5f930e`;
- PR synthetic merge commit `c6fa00d824beec44d5010103c38478306d2c0d43`;
- branch head and synthetic merge use identical tree SHA `ab82fd3908d174df668754c80ddec633da3bfb00`, so the hosted artifact source tree exactly matches the package-authority tree.

Hosted artifact:

- artifact ID `9855845429`;
- wrapper name `obsidian-c6fa00d824beec44d5010103c38478306d2c0d43`;
- wrapper size `718,756` bytes;
- wrapper digest `sha256:b480c700f6b2b88ab1b0aa57136b43d55f9bd1d6d6fb99295f8abfbfc4f2ef9b`.

Canonical direct runtime JAR:

- `Obsidian-0.4.0-phase4-dev1.jar`;
- size **493,377 bytes**;
- SHA-256 **`39c4bb4932bd6e7c00a4190c3514ef29eb926c337bba488f9a04bbef27120458`**.

Sources JAR from the same hosted artifact:

- `Obsidian-0.4.0-phase4-dev1-sources.jar`;
- size `255,354` bytes;
- SHA-256 `55b9a7ce230db01b74c38d58023f91739ec74a0262344b4d6b40eaab4c17e03d`.

Later continuity-only commits do not change dev1 package authority.

## Current handoff — reference P4.1 dev1 runtime

Use the exact canonical `Obsidian-0.4.0-phase4-dev1.jar` on the reference Windows 11 / RX 6800 XT / Minecraft 26.2 / Fabric Loader 0.19.3 / Java 25 Vulkan setup.

Exercise:

1. enter a world and allow the persistent scene to populate/resync;
2. hold a stable camera long enough for sampled visibility evidence to appear;
3. perform rapid 360-degree camera turns;
4. traverse horizontally enough to cause real chunk load/unload churn;
5. move vertically across section boundaries;
6. break/place ordinary blocks while P3.10 remains active;
7. perform F3+T and allow recovery;
8. leave/re-enter the world if practical;
9. exit normally.

Because P4.1 is shadow-only, the required human visual verdict is simple but strict: **the world must look the same as the promoted P3.10 baseline**. Any new holes, missing terrain, duplicate terrain, texture/light/cutout/depth regressions, stale popping or other visual difference is a failure.

Useful dev1 log anchors:

- `Obsidian 0.4.0-phase4-dev1`;
- `P4.1 shadow large-scene visibility configured`;
- `P4.1 bounded scene resync complete`;
- `P4.1 shadow visibility sample PASS`;
- `P4.1 final shadow visibility evidence`.

Promotion remains blocked until runtime evidence shows real scale, zero missing/unexpected/duplicate visibility identities, `gpuFalseCullCount=0`, no capacity failure, nonblocking readback/lifetime, `cameraOnlyFullSceneScan=false`, `productionDrawOwnershipChanged=false`, `nativeGraphicsExpansion=false`, inherited P3.10/P3.7/worker/lifetime gates clean, normal exit and explicit human visual PASS.

PR #57 stays **DRAFT / DO NOT MERGE** until those gates close.

## After the dev1 runtime

If the reference run passes, record the runtime result immutably and decide the next Phase 4 slice from measured evidence. Do not connect GPU-visible records to production draw submission in P4.1 itself and do not weaken A-0203 after seeing runtime data.