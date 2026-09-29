# Obsidian Current State

Last updated: 2026-09-29

## Canonical repository

- Repository: `TrissTheBoss/Obsidian`
- Default branch: `main`
- Synchronized Phase 3 merge: `01547b55f68690a5d0aac8405fc0fe91cdf440f9`
- Active branch: `phase4/p4.1-persistent-scene-visibility`
- Active draft PR: #57 `Phase 4 P4.1: persistent large-scene GPU visibility`
- Product phase: **Phase 4 — GPU-driven visibility at real-world scale**.


## Repository hygiene / public surface — ACTIVE AND PROVEN

- Pre-policy audit on 2026-09-28 found **56 branches** and **57 PRs**, with only PR #57 open.
- D-0028 / `ai/REPOSITORY_HYGIENE.md`: `main` is the only permanent branch; active PR heads are temporary work state; closed/merged branches are deleted after evidence is preserved.
- PR #58 exact final head `1044a8846a5e602665e717e7cbeea63de47b4e2e` passed Build #762 and merged `[no-release]` as `e4f00867ea2df5b70eb21e2f0003ad36950fb9d8`; post-merge Build #763 passed.
- Repository Hygiene run `36400552449` / #1 passed and reduced the repository from **56 branches to 2**: `main` plus the open P4.1 branch. The normal `<=5` target is therefore satisfied.
- Root `README.md` is now the maintained public landing page with Phase 4 status, compatibility, release channels, quick-start guidance, architecture and roadmap links.
- Root `TIMELINE.md` is the maintained public visual project-history page: phase rail, detailed milestone timeline, evidence links, current P4.1 position, and planned Phase 5-12 horizon. D-0030 plus `REPOSITORY_HYGIENE.md` now require future agents to keep it synchronized when material phase/milestone/history triggers fire; it remains non-authoritative. The README and contributing guide link it directly.
- Tester-facing CI-green development JARs use GitHub **Prereleases / Preview**; validated public checkpoints use normal Releases; Actions artifacts remain short-lived engineering evidence.
- Publish Preview run `36400684893` / #1 passed from audited `/publish-preview` on PR #57 and published `v0.4.0-phase4-dev1` as a GitHub prerelease targeting `6af5174f054b272c65174fbf0c37d981a66aff31`.
- Preview runtime JAR: 493,355 bytes, SHA-256 `8bf25f3aa6bfe6fb4392044ba0973692fc6a24fd9948f3fea51cf55b12e157f3`. Its target differs from A-0205 package authority only by `/ai` continuity files; no renderer/source/resource file changed. Keep this Preview binary identity separate from the original A-0205 Actions-handoff hash.
- A-0207 records final cleanup/build/release evidence. P4.1 runtime validation remains pending and PR #57 stays draft.
## Modrinth distribution mirror — ACTIVE AND PROVEN

- D-0031 keeps GitHub CI/releases authoritative while allowing the same versioned builds to be mirrored to Modrinth for ordinary users.
- New reusable/manual workflow: `.github/workflows/publish-modrinth.yml`.
- Main versioned releases and explicit Preview publication are wired to call the Modrinth mirror after a successful release/preview flow, including idempotent backfill when the GitHub release already exists.
- Channel mapping: `dev`/`alpha`/`preview` -> Modrinth `alpha`; `beta`/`rc`/`pre` -> `beta`; validated versions without a prerelease marker -> `release`.
- The runtime JAR is primary; the sources JAR is supplementary; metadata is Fabric / exact Minecraft version / client-only.
- External publisher is pinned to `cloudnode-pro/modrinth-publish` commit `203bc72a51ae39fba1194fc4097d9f0750c49bd3` (v2.5.1).
- The PAT value is never stored in repository content. Workflows consume repository Actions secret `MODRINTH_PAT` only at runtime.
- Automatic mirroring resolves `MODRINTH_PROJECT_ID` from an explicit input, repository variable, or repository secret. The PAT remains repository secret `MODRINTH_PAT`; no credential value is persisted in the repository.
- Publication performs an authenticated existing-version lookup and leaves an already-published matching version number untouched.
- PR #67 merged `[no-release]` as `7323626f7d8f556f5ae81735ecc0e6a4da9bc85f`; post-merge Context Governor #14, Repository Hygiene #15, and Build #783 all passed.
- A-0212 records the reviewed action pin, workflow behavior, security boundary, release-policy synchronization, and exact hosted evidence.
- PR #68 `Allow idempotent Modrinth backfill` merged `[no-release]` as `67e5a19d92e9e4f746ee5632b95e6aef6fb12c0b`; PR #69 added secret-backed project-ID support and merged as `dc4fdec81cc389fb0cee300d982d7da61015ad1b`; A-0214/A-0215 record those first-live integration steps. Publish Preview run `36433438631` / #4 then reached Modrinth but its list-versions preflight returned HTTP 404; A-0216 records that failure and the safe 404 fallback. PR #70 `Allow Modrinth create after hidden-project 404` merged `[no-release]` as `87c51328ab1f1587bc544a186ee5fd55bf688977` after exact-head Build #794, Context Governor #24 and Repository Hygiene #22 passed; merged `main` then passed Build #795, Context Governor #25 and Repository Hygiene #21. Publish Preview run `36434577563` / #5 completed SUCCESS and downstream Modrinth job `108969491205` completed SUCCESS. The list-versions request again returned HTTP 404, the workflow correctly continued to VERSION_CREATE, and the pinned publisher reported a successful upload for `0.4.0-phase4-dev1`, Modrinth version ID `OhTxTs7Z`, source `6af5174f054b272c65174fbf0c37d981a66aff31`, Minecraft 26.2, Fabric, client-only, `alpha`. A-0217 records the closing evidence. The first-live-publication obligation is closed; P4.1 runtime validation remains the active engineering mission.

## LLM Context Governor — ACTIVE AND PROVEN

- Reusable skill: `ai/skills/context-governor/SKILL.md`; compact bootstrap: `ai/context/ACTIVE_CONTEXT.json`, which remains a validated cache/index and **not** source authority.
- D-0029 makes tiered/recoverable context, fail-closed compaction, exact-source rehydration, least-data external calls, and least-privilege OAuth durable process policy.
- Deterministic local regression suite: **7/7 PASS**; active-capsule validation PASS; standalone Skill validation PASS. A-0208 records design/research/local evidence.
- Exact PR #61 integration head `16cde38d8a96f377687f448b11d7f2d05033df90` passed normal Build run `36404017997` / **#768** and Context Governor run `36404018090` / **#1**.
- PR #61 merged `[no-release]` as `623dc5904fae5e3d0be7d90d13236ee99be32b37`.
- Exact merged `main` passed Context Governor run `36404117650` / **#2**, normal Build run `36404117683` / **#769**, and Repository Hygiene run `36404117664` / **#8**.
- A-0209 records hosted validation, activation, post-merge validation, and package evidence.
- Automatic compaction remains two-phase: preserve critical knowledge + recovery pointers, validate, then discard only re-fetchable/transient bulk. Critical-over-budget cases fail closed.
- External API/tool/MCP calls use call-specific required identifiers/fields/records/ranges only. OAuth uses least-privilege action/resource scopes and incremental authorization where supported; credentials stay out of durable/model context.
- This process work does **not** change the P4.1 renderer, runtime package, draft status, or frozen validation gates.

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

## Phase 4 P4.1 — ACTIVE / DEV1 FOLLOW-UP RUNTIME REQUIRED

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

## First P4.1 dev1 reference runtime — PARTIAL / AUTOMATED PATH CLEAN

A-0219 records the first public-Preview reference runtime from 2026-09-29.

Observed clean evidence from the exact published Preview includes:

- final live/high-water persistent scene: **10,642** sections at effective render distance 16;
- **2,779** GPU dispatches over **25,985,145** candidates;
- **2,644 / 2,644** exact/conservative sampled visibility comparisons passed;
- `missingVisible=0`, `unexpectedVisible=0`, `duplicateVisible=0`, `gpuFalseCullCount=0`;
- `capacityFailures=0`, `lifecycleOverflow=0`, `hardFailure=false`;
- `cameraOnlyFullSceneScan=false`, `productionDrawOwnershipChanged=false`, `nativeGraphicsExpansion=false`;
- real horizontal/vertical traversal and scene churn occurred;
- inherited P3.10/P3.5/P3.6/P3.7 correctness evidence remained clean;
- worker/staging/arena/resource lifetime closed cleanly;
- process exit code 0.

This run is **not promotion-complete**. The only explicit ResourceManager reload and `resource-reload` invalidation occur during startup before world entry/P4.1 configuration, while the inherited measured window reports `benchmarkResourceReloadDelta=0`. Therefore the required **post-startup in-world F3+T + recovery** is not proven. The uploaded log also did not include an explicit human visual PASS. A world leave/re-entry cycle was not demonstrated either, though A-0203 makes that item conditional on practicality.

No renderer-source change is authorized from this result.

## Current handoff — exact same dev1 package, missing evidence only

Use the **same published Preview**:

- `Obsidian-0.4.0-phase4-dev1.jar`;
- Preview source `6af5174f054b272c65174fbf0c37d981a66aff31`;
- Preview JAR SHA-256 `8bf25f3aa6bfe6fb4392044ba0973692fc6a24fd9948f3fea51cf55b12e157f3`.

Follow-up exercise:

1. enter the world and wait for `P4.1 bounded scene resync complete` plus subsequent `P4.1 shadow visibility sample PASS` evidence;
2. press **F3+T** after the world is fully active;
3. wait for resource reload/recovery and new P4.1 PASS samples afterward;
4. leave/re-enter the world if practical and again allow P4.1 to recover;
5. exit normally;
6. provide the full Prism Launcher log and an explicit human visual verdict.

The visual verdict remains strict: **the world must look the same as the promoted P3.10 baseline**. Any new holes, missing terrain, duplicate terrain, texture/light/cutout/depth regressions, stale popping or other visual difference is failure.

Promotion remains blocked until the missing F3+T/recovery evidence and explicit human visual PASS are supplied. PR #57 stays **DRAFT / DO NOT MERGE**.

## After the follow-up dev1 runtime

If the exact same package closes the remaining gates, record the runtime promotion immutably, synchronize current state/context, run exact synchronized-head CI, and decide the next Phase 4 slice from measured evidence. Do not connect GPU-visible records to production draw submission inside P4.1 itself and do not weaken A-0203 after seeing runtime data.